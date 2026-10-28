import 'package:flutter/foundation.dart';
import 'package:google_sign_in/google_sign_in.dart';

import '../config.dart';

/// Thrown when the user backs out of the Google account picker -- not an error worth
/// showing, unlike everything else that can go wrong signing in.
class SignInCancelled implements Exception {
  const SignInCancelled();
}

/// The native Google Sign-In flow, behind an interface so widget tests can stand in a fake.
abstract class GoogleAuth {
  /// Whether a client ID was compiled in. Without it there is nothing to sign in with.
  bool get configured;

  /// Returns a Google ID token for the backend to verify.
  Future<String> signIn();

  Future<void> signOut();
}

/// The phone's own Google account picker (Credential Manager on Android, the GoogleSignIn
/// SDK on iOS). `serverClientId` is the backend's *Web* client ID, which is what makes the
/// ID token's audience the one `GoogleTokenVerifier` checks -- no backend change is needed
/// for the mobile client.
class PlatformGoogleAuth implements GoogleAuth {
  Future<void>? _initialized;

  @override
  bool get configured => AppConfig.googleServerClientId.isNotEmpty;

  Future<void> _initialize() => _initialized ??= GoogleSignIn.instance.initialize(
        serverClientId: AppConfig.googleServerClientId,
        clientId: defaultTargetPlatform == TargetPlatform.iOS && AppConfig.googleIosClientId.isNotEmpty
            ? AppConfig.googleIosClientId
            : null,
      );

  @override
  Future<String> signIn() async {
    await _initialize();
    try {
      final account = await GoogleSignIn.instance.authenticate();
      final idToken = account.authentication.idToken;
      if (idToken == null) {
        throw StateError('Google did not return an ID token');
      }
      return idToken;
    } on GoogleSignInException catch (e) {
      if (e.code == GoogleSignInExceptionCode.canceled || e.code == GoogleSignInExceptionCode.interrupted) {
        throw const SignInCancelled();
      }
      rethrow;
    }
  }

  @override
  Future<void> signOut() async {
    if (_initialized == null) return;
    try {
      await GoogleSignIn.instance.signOut();
    } catch (_) {
      // The app's own session is already gone; Google's is only a convenience.
    }
  }
}
