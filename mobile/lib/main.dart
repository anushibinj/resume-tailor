import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api/token_store.dart';
import 'app.dart';
import 'state/providers.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Both are read before the first frame so the auth gate and theme start from what was
  // saved, rather than flashing the sign-in screen or the wrong theme.
  final tokens = SecureTokenStore();
  final results = await Future.wait([tokens.load(), SharedPreferences.getInstance()]);

  runApp(
    ProviderScope(
      overrides: [
        tokenStoreProvider.overrideWithValue(tokens),
        sharedPreferencesProvider.overrideWithValue(results[1] as SharedPreferences),
      ],
      // One retry, like the web client's QueryClient (retry: 1).
      retry: (count, error) => count < 1 ? const Duration(seconds: 1) : null,
      child: const ResumeTailorApp(),
    ),
  );
}
