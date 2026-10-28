import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/providers.dart';
import '../theme/app_theme.dart';
import 'ask_screen.dart';
import 'home_screen.dart';
import 'navigation.dart';
import 'resumes_screen.dart';
import 'runs_screen.dart';
import 'tailor_screen.dart';

/// The signed-in frame: the web's sticky header becomes an app bar (brand, theme toggle,
/// settings, account) and its nav links become the bottom navigation.
class HomeShell extends ConsumerWidget {
  const HomeShell({super.key});

  static const _destinations = [
    (ShellTab.home, 'Home', Icons.home_outlined, Icons.home),
    (ShellTab.tailor, 'Tailor', Icons.auto_fix_high_outlined, Icons.auto_fix_high),
    (ShellTab.resumes, 'Resumes', Icons.description_outlined, Icons.description),
    (ShellTab.ask, 'Ask', Icons.chat_bubble_outline, Icons.chat_bubble),
    (ShellTab.history, 'History', Icons.history, Icons.history),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tab = ref.watch(shellTabProvider);

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: GestureDetector(
          onTap: () => ref.read(shellTabProvider.notifier).select(ShellTab.home),
          child: const Text('Resume Tailor'),
        ),
        actions: const [_ThemeToggle(), _SettingsButton(), _UserMenu(), SizedBox(width: 8)],
      ),
      // Kept alive so switching tabs doesn't lose a half-typed job description.
      body: IndexedStack(
        index: tab.index,
        children: const [HomeScreen(), TailorScreen(), ResumesScreen(), AskScreen(), RunsScreen()],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab.index,
        onDestinationSelected: (index) => ref.read(shellTabProvider.notifier).select(ShellTab.values[index]),
        destinations: [
          for (final (_, label, icon, selectedIcon) in _destinations)
            NavigationDestination(icon: Icon(icon), selectedIcon: Icon(selectedIcon), label: label),
        ],
      ),
    );
  }
}

class _ThemeToggle extends ConsumerWidget {
  const _ThemeToggle();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return IconButton(
      tooltip: isDark ? 'Switch to light theme' : 'Switch to dark theme',
      color: context.palette.inkSoft,
      icon: Icon(isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined, size: 20),
      onPressed: () => ref.read(themeModeProvider.notifier).toggle(Theme.of(context).brightness),
    );
  }
}

class _SettingsButton extends StatelessWidget {
  const _SettingsButton();

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: 'Settings',
    color: context.palette.inkSoft,
    icon: const Icon(Icons.settings_outlined, size: 20),
    onPressed: () => openSettings(context),
  );
}

class _UserMenu extends ConsumerWidget {
  const _UserMenu();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authProvider);
    if (auth is! Authenticated) return const SizedBox.shrink();
    final user = auth.user;
    final p = context.palette;

    return PopupMenuButton<String>(
      tooltip: user.email,
      onSelected: (_) => ref.read(authProvider.notifier).signOut(),
      itemBuilder: (context) => [
        PopupMenuItem<String>(
          enabled: false,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(user.displayName, style: TextStyle(color: p.ink, fontSize: 14)),
              Text(user.email, style: TextStyle(color: p.inkSoft, fontSize: 12)),
            ],
          ),
        ),
        const PopupMenuDivider(),
        PopupMenuItem<String>(
          value: 'sign-out',
          child: Row(
            spacing: 10,
            children: [
              Icon(Icons.logout, size: 16, color: p.inkSoft),
              Text('Sign out', style: TextStyle(color: p.ink)),
            ],
          ),
        ),
      ],
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: CircleAvatar(
          radius: 13,
          backgroundColor: p.pencilSoft,
          foregroundImage: user.pictureUrl != null ? NetworkImage(user.pictureUrl!) : null,
          child: Text(
            user.displayName.isNotEmpty ? user.displayName[0].toUpperCase() : '?',
            style: TextStyle(fontSize: 12, color: p.pencil),
          ),
        ),
      ),
    );
  }
}
