import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class SignInScreen extends ConsumerWidget {
  const SignInScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.palette;
    final auth = ref.watch(authProvider);
    final state = auth is Unauthenticated ? auth : const Unauthenticated();
    final configured = ref.watch(googleAuthProvider).configured;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 384),
              child: Panel(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text('Resume Tailor', style: AppFonts.serif(TextStyle(fontSize: 24, color: p.ink))),
                    const SizedBox(height: 8),
                    Text('Sign in with Google to continue.', style: TextStyle(fontSize: 14, color: p.inkSoft)),
                    const SizedBox(height: 24),
                    if (!configured)
                      Text(
                        'GOOGLE_SERVER_CLIENT_ID is not set -- see mobile/.env.example.',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 14, color: p.strike),
                      )
                    else
                      _GoogleButton(
                        busy: state.signingIn,
                        onPressed: () => ref.read(authProvider.notifier).signInWithGoogle(),
                      ),
                    if (state.error != null) ...[
                      const SizedBox(height: 16),
                      Text(
                        state.error!,
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 14, color: p.strike),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Styled after Google's own outline / pill "Sign in with Google" button, which the web
/// client renders through Google's script.
class _GoogleButton extends StatelessWidget {
  const _GoogleButton({required this.busy, required this.onPressed});

  final bool busy;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return OutlinedButton(
      onPressed: busy ? null : onPressed,
      style: OutlinedButton.styleFrom(
        shape: const StadiumBorder(),
        side: BorderSide(color: p.ruleStrong),
        backgroundColor: p.surface,
        foregroundColor: p.ink,
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        spacing: 12,
        children: [
          if (busy)
            const Spinner(size: 18)
          else
            Text(
              'G',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: p.pencil),
            ),
          const Flexible(
            child: Text('Sign in with Google', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500)),
          ),
        ],
      ),
    );
  }
}
