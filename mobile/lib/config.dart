import 'package:flutter/foundation.dart';

/// Build-time settings, passed with `--dart-define-from-file=.env` (see `.env.example`).
///
/// They are compile-time constants rather than something read at runtime because a phone
/// has no `.env` file to read -- the values are baked into the build, the same way
/// Next.js inlines `NEXT_PUBLIC_*` variables into the web bundle.
class AppConfig {
  AppConfig._();

  static const String _apiBaseUrl = String.fromEnvironment('API_BASE_URL');

  /// The OAuth 2.0 *Web* client ID -- the same value as the backend's `GOOGLE_CLIENT_ID`.
  static const String googleServerClientId = String.fromEnvironment('GOOGLE_SERVER_CLIENT_ID');

  /// iOS only. Android identifies the app by package name and signing certificate instead.
  static const String googleIosClientId = String.fromEnvironment('GOOGLE_IOS_CLIENT_ID');

  /// Where the backend is listening. `localhost` on the Android emulator is the emulator
  /// itself, so the default there is 10.0.2.2, which the emulator maps to the host machine.
  static String get apiBaseUrl {
    if (_apiBaseUrl.isNotEmpty) {
      return _apiBaseUrl.endsWith('/') ? _apiBaseUrl.substring(0, _apiBaseUrl.length - 1) : _apiBaseUrl;
    }
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
      return 'http://10.0.2.2:8080';
    }
    return 'http://localhost:8080';
  }
}
