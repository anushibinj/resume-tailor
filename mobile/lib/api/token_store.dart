import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// The app's own session token (issued by POST /api/auth/google), kept so reopening the
/// app doesn't force a fresh Google sign-in. The web client keeps it in localStorage; on a
/// phone the platform keystore (Keychain / Android Keystore) is the equivalent, and safer.
///
/// [token] is cached in memory because [ApiClient] needs it on every request and the
/// keystore is asynchronous.
abstract class TokenStore {
  String? get token;
  Future<void> load();
  Future<void> save(String token);
  Future<void> clear();
}

class SecureTokenStore implements TokenStore {
  SecureTokenStore([FlutterSecureStorage? storage]) : _storage = storage ?? const FlutterSecureStorage();

  static const _key = 'resume-tailor.auth.token';
  final FlutterSecureStorage _storage;
  String? _token;

  @override
  String? get token => _token;

  @override
  Future<void> load() async {
    try {
      _token = await _storage.read(key: _key);
    } catch (_) {
      // A keystore that can't be read (e.g. restored onto another device) just means
      // signing in again.
      _token = null;
    }
  }

  @override
  Future<void> save(String token) async {
    _token = token;
    try {
      await _storage.write(key: _key, value: token);
    } catch (_) {
      // Storage refused: the session just won't survive an app restart.
    }
  }

  @override
  Future<void> clear() async {
    _token = null;
    try {
      await _storage.delete(key: _key);
    } catch (_) {
      // See save.
    }
  }
}

/// For tests, and anywhere persistence isn't wanted.
class MemoryTokenStore implements TokenStore {
  MemoryTokenStore([this._token]);

  String? _token;

  @override
  String? get token => _token;

  @override
  Future<void> load() async {}

  @override
  Future<void> save(String token) async => _token = token;

  @override
  Future<void> clear() async => _token = null;
}
