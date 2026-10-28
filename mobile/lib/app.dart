import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'screens/home_shell.dart';
import 'screens/sign_in_screen.dart';
import 'state/providers.dart';
import 'theme/app_theme.dart';
import 'widgets/toast.dart';
import 'widgets/ui.dart';

class ResumeTailorApp extends ConsumerWidget {
  const ResumeTailorApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp(
      title: 'Resume Tailor',
      debugShowCheckedModeBanner: false,
      scaffoldMessengerKey: scaffoldMessengerKey,
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      themeMode: ref.watch(themeModeProvider),
      home: const AuthGate(),
    );
  }
}

/// Only the signed-in branch ever builds the app proper, so nothing below it needs to
/// handle a missing user.
class AuthGate extends ConsumerWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return switch (ref.watch(authProvider)) {
      AuthLoading() => const Scaffold(body: Center(child: Spinner())),
      Unauthenticated() => const SignInScreen(),
      Authenticated() => const HomeShell(),
    };
  }
}
