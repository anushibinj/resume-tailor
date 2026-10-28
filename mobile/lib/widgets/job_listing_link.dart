import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../theme/app_theme.dart';
import '../util/url.dart';

/// Renders a job listing URL with middle-truncation and a compact button to open the
/// original posting in the browser.
///
/// If no URL is provided, renders nothing.
/// If the URL is invalid/non-HTTP, external navigation is disabled.
class JobListingLink extends StatelessWidget {
  const JobListingLink({super.key, required this.url, this.maxLength = 40});

  final String? url;
  final int maxLength;

  Future<void> _open() async {
    await launchUrl(Uri.parse(url!.trim()), mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    if (url == null || url!.trim().isEmpty) return const SizedBox.shrink();
    final p = context.palette;
    final trimmed = url!.trim();
    final display = truncateUrl(trimmed, maxLength);
    final style = AppFonts.mono(TextStyle(fontSize: 12, color: p.inkSoft));

    if (!isValidHttpUrl(trimmed)) {
      return Tooltip(message: trimmed, child: Text(display, style: style, overflow: TextOverflow.ellipsis));
    }

    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Flexible(
          child: Tooltip(
            message: trimmed,
            child: InkWell(
              onTap: _open,
              child: Text(display, style: style, overflow: TextOverflow.ellipsis),
            ),
          ),
        ),
        IconButton(
          onPressed: _open,
          tooltip: 'Open original job listing',
          visualDensity: VisualDensity.compact,
          iconSize: 14,
          color: p.inkSoft,
          icon: const Icon(Icons.open_in_new),
        ),
      ],
    );
  }
}
