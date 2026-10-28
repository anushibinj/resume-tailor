import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

/// The mobile stand-in for `window.confirm` -- same question, same wording as the web client.
Future<bool> confirmDestructive(BuildContext context, String message, {String confirmLabel = 'Delete'}) async {
  final p = context.palette;
  final result = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      content: Text(message, style: TextStyle(color: p.ink, fontSize: 15, height: 1.5)),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text('Cancel', style: TextStyle(color: p.inkSoft)),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: Text(confirmLabel, style: TextStyle(color: p.strike, fontWeight: FontWeight.w600)),
        ),
      ],
    ),
  );
  return result ?? false;
}
