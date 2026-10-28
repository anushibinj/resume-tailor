import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:resume_tailor/api/api_client.dart';
import 'package:resume_tailor/api/token_store.dart';
import 'package:resume_tailor/app.dart';
import 'package:resume_tailor/state/google_auth.dart';
import 'package:resume_tailor/state/providers.dart';
import 'package:resume_tailor/theme/app_theme.dart';
import 'package:resume_tailor/util/file_share.dart';

import 'fake_backend.dart';

class FakeGoogleAuth implements GoogleAuth {
  bool cancel = false;
  int signOuts = 0;

  @override
  bool get configured => true;

  @override
  Future<String> signIn() async {
    if (cancel) throw const SignInCancelled();
    return 'google-id-token';
  }

  @override
  Future<void> signOut() async => signOuts++;
}

class FakeFileSharer implements FileSharer {
  final List<DownloadedFile> shared = [];

  @override
  Future<void> share(DownloadedFile file) async => shared.add(file);
}

class Harness {
  Harness({this.signedIn = true, this.tall = true}) : tokens = MemoryTokenStore(signedIn ? FakeBackend.token : null);

  final bool signedIn;
  final bool tall;
  final FakeBackend backend = FakeBackend();
  final MemoryTokenStore tokens;
  final FakeGoogleAuth google = FakeGoogleAuth();
  final FakeFileSharer sharer = FakeFileSharer();

  Future<void> pump(WidgetTester tester) async {
    AppFonts.useGoogleFonts = false;
    // A phone's width (390 logical pixels), but tall enough that the lazily built lists
    // hold every row the tests look for.
    tester.view.physicalSize = Size(1170, tall ? 9000 : 2532);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          tokenStoreProvider.overrideWithValue(tokens),
          apiClientProvider.overrideWith(
            (ref) =>
                ApiClient(baseUrl: 'http://test', tokens: ref.watch(tokenStoreProvider), httpClient: backend.client),
          ),
          googleAuthProvider.overrideWithValue(google),
          fileSharerProvider.overrideWithValue(sharer),
        ],
        retry: (_, _) => null,
        child: const ResumeTailorApp(),
      ),
    );
    await settle(tester);
  }

  /// Unmounts the app so the run poller's timer is cancelled before the test ends.
  Future<void> dispose(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
  }
}

/// pumpAndSettle never settles while a spinner is spinning, so pump a fixed number of
/// short frames instead: enough for fake HTTP round trips and route transitions.
Future<void> settle(WidgetTester tester, {int frames = 12}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> tapText(WidgetTester tester, String text) async {
  final finder = find.text(text).last;
  await tester.ensureVisible(finder);
  await tester.pump();
  await tester.tap(finder);
  await settle(tester);
}

Future<void> tapTab(WidgetTester tester, String label) async {
  await tester.tap(find.descendant(of: find.byType(NavigationBar), matching: find.text(label)));
  await settle(tester);
}
