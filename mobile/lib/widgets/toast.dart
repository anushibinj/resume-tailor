import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// App-wide, so a toast raised by a screen that is being popped (e.g. "Run deleted")
/// still shows on the screen underneath -- the web client's `sonner` Toaster sits at the
/// layout root for the same reason.
final scaffoldMessengerKey = GlobalKey<ScaffoldMessengerState>();

enum ToastTone { success, error, info }

void showToast(String message, {ToastTone tone = ToastTone.info}) {
  final messenger = scaffoldMessengerKey.currentState;
  final context = scaffoldMessengerKey.currentContext;
  if (messenger == null || context == null) return;
  final p = context.palette;
  final (icon, color, background) = switch (tone) {
    ToastTone.success => (Icons.check_circle_outline, p.pencil, p.pencilSoft),
    ToastTone.error => (Icons.error_outline, p.strike, p.strikeSoft),
    ToastTone.info => (Icons.info_outline, p.inkSoft, p.surface),
  };
  messenger
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        backgroundColor: background,
        duration: Duration(seconds: tone == ToastTone.error ? 6 : 4),
        content: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          spacing: 10,
          children: [
            Icon(icon, size: 18, color: color),
            Expanded(child: Text(message, style: TextStyle(color: p.ink))),
          ],
        ),
      ),
    );
}

void toastSuccess(String message) => showToast(message, tone: ToastTone.success);
void toastError(String message) => showToast(message, tone: ToastTone.error);
