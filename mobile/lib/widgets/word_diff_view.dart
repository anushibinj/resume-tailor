import 'package:flutter/material.dart';

import '../api/models.dart';
import '../theme/app_theme.dart';
import '../util/word_diff.dart';

enum DiffSide { original, tailored }

/// Side-by-side proofreading marks computed from a single diff pass.
///
/// The "Yours" column shows what you had, with cut text struck through; "Tailored" shows
/// what the model produced, with new text underlined in pencil. Deliberately not a
/// red/green block diff -- this is a document being edited, not a code review.
///
/// Markdown is set in the serif document face because it reads as prose. LaTeX is set in
/// monospace: what's on screen is markup like \begin{itemize}, and a serif makes that
/// harder to scan, not easier.
class WordDiffView extends StatelessWidget {
  const WordDiffView({super.key, required this.parts, required this.side, required this.format});

  final List<DiffPart> parts;
  final DiffSide side;
  final ResumeFormat format;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final base = format == ResumeFormat.latex
        ? AppFonts.mono(TextStyle(fontSize: 12, height: 1.6, color: p.ink))
        : AppFonts.serif(TextStyle(fontSize: 15, height: 1.7, color: p.ink));

    final cut = TextStyle(
      backgroundColor: p.strikeSoft,
      color: p.strike,
      decoration: TextDecoration.lineThrough,
      decorationColor: p.strike,
    );
    final add = TextStyle(
      backgroundColor: p.pencilSoft,
      color: p.ink,
      decoration: TextDecoration.underline,
      decorationColor: p.pencil,
      decorationThickness: 2,
    );

    final spans = <TextSpan>[];
    for (final part in parts) {
      if (side == DiffSide.original && part.added) continue;
      if (side == DiffSide.tailored && part.removed) continue;
      final marked = (side == DiffSide.original && part.removed) || (side == DiffSide.tailored && part.added);
      spans.add(TextSpan(text: part.value, style: marked ? (side == DiffSide.original ? cut : add) : null));
    }

    return SelectableText.rich(TextSpan(style: base, children: spans));
  }
}
