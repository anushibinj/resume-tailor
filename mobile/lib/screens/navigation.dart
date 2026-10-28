import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/providers.dart';
import 'run_detail_screen.dart';
import 'settings_screen.dart';

/// Switches the bottom tab from anywhere, including a pushed screen (which is closed so
/// the tab is actually visible) -- the mobile equivalent of a `<Link href="/tailor">`.
void goToTab(BuildContext context, WidgetRef ref, ShellTab tab) {
  Navigator.of(context).popUntil((route) => route.isFirst);
  ref.read(shellTabProvider.notifier).select(tab);
}

Future<void> openSettings(BuildContext context) =>
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettingsScreen()));

Future<void> openRun(BuildContext context, String id) =>
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => RunDetailScreen(id: id)));
