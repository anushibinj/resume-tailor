import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../api/token_store.dart';
import '../config.dart';
import 'google_auth.dart';

// --- infrastructure, overridden in main() and in tests ---

final tokenStoreProvider = Provider<TokenStore>((ref) => MemoryTokenStore());

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(baseUrl: AppConfig.apiBaseUrl, tokens: ref.watch(tokenStoreProvider)),
);

final googleAuthProvider = Provider<GoogleAuth>((ref) => PlatformGoogleAuth());

final sharedPreferencesProvider = Provider<SharedPreferences?>((ref) => null);

// --- auth ---

sealed class AuthState {
  const AuthState();
}

class AuthLoading extends AuthState {
  const AuthLoading();
}

class Unauthenticated extends AuthState {
  const Unauthenticated({this.error, this.signingIn = false});

  final String? error;
  final bool signingIn;
}

class Authenticated extends AuthState {
  const Authenticated(this.user);

  final AuthUser user;
}

/// Gates the whole app behind a Google sign-in. There is no guest mode and no per-screen
/// guard: every backend endpoint except /api/auth/** requires the bearer token this sets
/// up, so gating here is equivalent to gating every screen individually.
class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    final api = ref.watch(apiClientProvider);
    api.onUnauthorized = signOut;
    Future.microtask(_restore);
    return const AuthLoading();
  }

  Future<void> _restore() async {
    final api = ref.read(apiClientProvider);
    if (api.tokens.token == null) {
      state = const Unauthenticated();
      return;
    }
    try {
      state = Authenticated(await api.me());
    } catch (_) {
      // A 401 already cleared the token inside ApiClient. Anything else (backend down or
      // restarting) leaves a still-valid token in place so the next launch can restore it.
      state = const Unauthenticated();
    }
  }

  Future<void> signInWithGoogle() async {
    state = const Unauthenticated(signingIn: true);
    final String idToken;
    try {
      idToken = await ref.read(googleAuthProvider).signIn();
    } on SignInCancelled {
      state = const Unauthenticated();
      return;
    } catch (_) {
      state = const Unauthenticated(error: "Couldn't sign you in with Google. Try again.");
      return;
    }
    try {
      final api = ref.read(apiClientProvider);
      final response = await api.signInWithGoogle(idToken);
      await api.tokens.save(response.token);
      state = Authenticated(response.user);
    } catch (_) {
      state = const Unauthenticated(error: "Couldn't sign you in. Try again.");
    }
  }

  Future<void> signOut() async {
    // Several requests can come back 401 together; only the first needs to sign out.
    if (state is Unauthenticated && ref.read(apiClientProvider).tokens.token == null) return;
    await ref.read(apiClientProvider).tokens.clear();
    await ref.read(googleAuthProvider).signOut();
    state = const Unauthenticated();
  }
}

final authProvider = NotifierProvider<AuthController, AuthState>(AuthController.new);

/// Every data provider below watches this, so signing out (or in as someone else) drops
/// the previous account's cached resumes, runs and profiles rather than showing them.
final currentUserIdProvider = Provider<String?>((ref) {
  final auth = ref.watch(authProvider);
  return auth is Authenticated ? auth.user.id : null;
});

/// Thrown instead of making a request once there is no signed-in user: the screens that
/// would show the result are about to be replaced by the sign-in gate, and a request
/// without a token would only come back 401.
class SignedOut implements Exception {
  const SignedOut();
}

void _requireUser(Ref ref) {
  if (ref.watch(currentUserIdProvider) == null) throw const SignedOut();
}

// --- theme ---

/// Light / dark / follow the system, remembered across launches. Defaults to the system
/// setting, like the web client.
class ThemeModeController extends Notifier<ThemeMode> {
  static const _key = 'resume-tailor.theme';

  @override
  ThemeMode build() => switch (ref.watch(sharedPreferencesProvider)?.getString(_key)) {
    'light' => ThemeMode.light,
    'dark' => ThemeMode.dark,
    _ => ThemeMode.system,
  };

  /// Flips whatever is showing now, so the first tap always visibly changes something.
  void toggle(Brightness showing) {
    state = showing == Brightness.dark ? ThemeMode.light : ThemeMode.dark;
    ref.read(sharedPreferencesProvider)?.setString(_key, state.name);
  }
}

final themeModeProvider = NotifierProvider<ThemeModeController, ThemeMode>(ThemeModeController.new);

// --- navigation ---

/// The bottom navigation. Settings is not a tab: like most phone apps it opens from the
/// gear in the app bar (see `openSettings`), which keeps the bar at five destinations.
enum ShellTab { home, tailor, resumes, ask, history }

class ShellTabController extends Notifier<ShellTab> {
  @override
  ShellTab build() => ShellTab.home;

  void select(ShellTab tab) => state = tab;
}

final shellTabProvider = NotifierProvider<ShellTabController, ShellTab>(ShellTabController.new);

// --- data ---

final resumesProvider = FutureProvider<List<ResumeSummary>>((ref) {
  _requireUser(ref);
  return ref.watch(apiClientProvider).listResumes();
});

final profilesProvider = FutureProvider<List<LlmProfile>>((ref) {
  _requireUser(ref);
  return ref.watch(apiClientProvider).listProfiles();
});

/// Keyed by page size: Home shows the latest 5, History the latest 50.
final runsProvider = FutureProvider.family<PageOf<RunSummary>, int>((ref, size) {
  _requireUser(ref);
  return ref.watch(apiClientProvider).listRuns(size: size);
});

final questionsProvider = FutureProvider.autoDispose.family<PageOf<ResumeQuestion>, String>((ref, resumeId) {
  _requireUser(ref);
  return ref.watch(apiClientProvider).questionHistory(resumeId);
});

/// PDF export can be switched off entirely (PDF_ENABLED=false); the button is hidden
/// rather than offering something that will only ever return an error.
final pdfAvailableProvider = FutureProvider.family<bool, String>((ref, runId) {
  _requireUser(ref);
  return ref.watch(apiClientProvider).pdfAvailable(runId);
});

/// One run, polled every 2s while any of its work (rewrite, gap check, summary options)
/// is still outstanding -- the same cadence as the web client.
class RunNotifier extends AsyncNotifier<RunDetail> {
  RunNotifier(this.id);

  final String id;
  Timer? _timer;

  static const pollInterval = Duration(seconds: 2);

  @override
  Future<RunDetail> build() async {
    _requireUser(ref);
    ref.onDispose(() => _timer?.cancel());
    final run = await ref.read(apiClientProvider).getRun(id);
    _schedule(run);
    return run;
  }

  /// Puts a server response (or an optimistic edit) on screen and re-arms polling.
  void replace(RunDetail run) {
    state = AsyncData(run);
    _schedule(run);
  }

  void pausePolling() => _timer?.cancel();

  Future<void> refresh() async {
    try {
      final run = await ref.read(apiClientProvider).getRun(id);
      if (ref.mounted) replace(run);
    } catch (_) {
      // Keep what's on screen; the next poll (if any) tries again.
      if (ref.mounted && state.value != null) _schedule(state.value!);
    }
  }

  void _schedule(RunDetail run) {
    _timer?.cancel();
    if (run.isWorking) {
      _timer = Timer(pollInterval, refresh);
    }
  }
}

final runProvider = AsyncNotifierProvider.autoDispose.family<RunNotifier, RunDetail, String>(RunNotifier.new);
