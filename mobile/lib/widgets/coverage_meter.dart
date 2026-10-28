import 'package:flutter/material.dart';

import '../api/models.dart';
import '../theme/app_theme.dart';
import 'ui.dart';

/// Required requirements are drawn tallest, so a missing one is visible at a glance.
const _tickHeight = {KeywordImportance.required: 28.0, KeywordImportance.preferred: 20.0, KeywordImportance.nice: 14.0};

/// The match score, drawn as the data rather than as a summary of it: one tick per
/// keyword the posting asks for, filled when the tailored resume covers it. Reading left
/// to right you can see *which* requirements are missing, not just how many.
class CoverageMeter extends StatelessWidget {
  const CoverageMeter({super.key, required this.keywords, required this.score});

  final List<KeywordResult> keywords;
  final int? score;

  @override
  Widget build(BuildContext context) {
    if (keywords.isEmpty) return const SizedBox.shrink();
    final p = context.palette;
    final covered = keywords.where((k) => k.isCovered).length;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      spacing: 16,
      children: [
        Flexible(
          child: Semantics(
            label: '$covered of ${keywords.length} job requirements covered',
            child: Wrap(
              spacing: 3,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.end,
              children: [
                for (final keyword in keywords)
                  Tooltip(
                    triggerMode: TooltipTriggerMode.tap,
                    message:
                        '${keyword.keyword} — ${keyword.importance.name}, '
                        '${keyword.isCovered ? 'covered' : 'not in your resume'}',
                    // A wider hit box than the 5px tick, so it can be tapped.
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 1),
                      child: keyword.isCovered
                          ? Container(
                              width: 5,
                              height: _tickHeight[keyword.importance],
                              decoration: BoxDecoration(color: p.pencil, borderRadius: BorderRadius.circular(1)),
                            )
                          : SizedBox(
                              width: 5,
                              height: _tickHeight[keyword.importance],
                              child: DashedBorder(color: p.gap, child: const SizedBox.expand()),
                            ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: '${score ?? '—'}',
                    style: AppFonts.serif(TextStyle(fontSize: 24, height: 1, color: p.ink)),
                  ),
                  TextSpan(
                    text: '%',
                    style: TextStyle(fontSize: 16, color: p.inkFaint),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 4),
            Text('$covered of ${keywords.length} covered', style: TextStyle(fontSize: 12, color: p.inkSoft)),
          ],
        ),
      ],
    );
  }
}
