import 'package:flutter/material.dart';

import '../api/models.dart';
import '../theme/app_theme.dart';
import 'ui.dart';

/// Picks how long the resume's summary is, from lengths the model already wrote, so moving
/// the slider is instant rather than another wait on a model.
///
/// The stops are the lengths that exist, not a fixed 4-7 range: a reply can be missing
/// one, and a stop with nothing behind it would be a control that does nothing.
class SummaryLength extends StatelessWidget {
  const SummaryLength({
    super.key,
    required this.summary,
    required this.onSelect,
    required this.onGenerate,
    required this.generating,
  });

  final SummaryOptions summary;
  final ValueChanged<int> onSelect;
  final VoidCallback onGenerate;
  final bool generating;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final variants = summary.variants;
    final heading = AppFonts.serif(TextStyle(fontSize: 16, color: p.ink));

    if (summary.status == SummaryStatus.pending || summary.status == SummaryStatus.running) {
      return Panel(
        padding: const EdgeInsets.all(16),
        child: Row(
          spacing: 12,
          children: [
            const Spinner(),
            Expanded(
              child: Text('Writing summary options at 4 to 7 lines…', style: TextStyle(fontSize: 14, color: p.inkSoft)),
            ),
          ],
        ),
      );
    }

    if (variants.isEmpty) {
      final message = switch (summary.status) {
        SummaryStatus.none => 'This run was made before summary lengths existed.',
        SummaryStatus.failed => "The options couldn't be written. ${summary.error ?? ''}",
        _ => 'Your resume has no summary paragraph to resize.',
      };
      return Panel(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Summary length', style: heading),
            const SizedBox(height: 2),
            Text(message, style: TextStyle(fontSize: 14, color: p.inkSoft)),
            if (summary.status != SummaryStatus.completed) ...[
              const SizedBox(height: 12),
              AppButton(
                variant: summary.status == SummaryStatus.none ? ButtonVariant.primary : ButtonVariant.quiet,
                onPressed: generating ? null : onGenerate,
                busy: generating,
                icon: Icons.refresh,
                label: summary.status == SummaryStatus.none ? 'Write summary options' : 'Try again',
              ),
            ],
          ],
        ),
      );
    }

    // The handle is driven by the run itself, which the screen updates the instant a
    // length is picked, so there is no local drag state to fall out of step with the server.
    final found = variants.indexWhere((v) => v.lines == summary.selectedLines);
    final index = found < 0 ? 0 : found;
    final shown = variants[index];

    return Panel(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Summary length', style: heading),
              Semantics(liveRegion: true, child: Text('≈ ${shown.lines} lines', style: TextStyle(fontSize: 14, color: p.ink))),
            ],
          ),
          if (variants.length > 1)
            Semantics(
              label: 'Summary length in lines',
              value: 'about ${shown.lines} lines',
              child: Slider(
                min: 0,
                max: (variants.length - 1).toDouble(),
                divisions: variants.length - 1,
                value: index.toDouble(),
                onChanged: (value) {
                  final lines = variants[value.round()].lines;
                  if (lines != summary.selectedLines) onSelect(lines);
                },
              ),
            ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              mainAxisAlignment: variants.length > 1 ? MainAxisAlignment.spaceBetween : MainAxisAlignment.center,
              children: [
                for (var i = 0; i < variants.length; i++)
                  Text(
                    '${variants[i].lines}',
                    style: TextStyle(
                      fontSize: 12,
                      color: i == index ? p.ink : p.inkFaint,
                      fontWeight: i == index ? FontWeight.w500 : FontWeight.normal,
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Text(
            "Line counts are estimates. Your template's text width decides the real number.",
            style: TextStyle(fontSize: 12, color: p.inkFaint),
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: generating ? null : onGenerate,
              style: TextButton.styleFrom(foregroundColor: p.inkSoft, padding: EdgeInsets.zero, textStyle: const TextStyle(fontSize: 12)),
              icon: const Icon(Icons.refresh, size: 14),
              label: const Text('Write new options'),
            ),
          ),
          if (summary.status == SummaryStatus.failed)
            Text(
              "New options couldn't be written, so these are from earlier. ${summary.error ?? ''}",
              style: TextStyle(fontSize: 12, color: p.strike),
            ),
        ],
      ),
    );
  }
}
