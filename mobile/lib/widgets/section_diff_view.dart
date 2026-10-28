import 'package:flutter/material.dart';

import '../api/models.dart';
import '../theme/app_theme.dart';
import '../util/word_diff.dart';
import 'ui.dart';
import 'word_diff_view.dart';

const _changeLabel = {'REWORDED': 'Reworded', 'REORDERED': 'Moved', 'TRIMMED': 'Trimmed', 'EMPHASISED': 'Emphasised'};

class SectionDiffView extends StatelessWidget {
  const SectionDiffView({super.key, required this.sections, required this.format});

  final List<SectionDiff> sections;
  final ResumeFormat format;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    spacing: 12,
    children: [
      for (var i = 0; i < sections.length; i++)
        _SectionRow(key: ValueKey('${sections[i].title}-$i'), section: sections[i], format: format),
    ],
  );
}

class _SectionRow extends StatefulWidget {
  const _SectionRow({super.key, required this.section, required this.format});

  final SectionDiff section;
  final ResumeFormat format;

  @override
  State<_SectionRow> createState() => _SectionRowState();
}

class _SectionRowState extends State<_SectionRow> {
  late bool _open = !_unchanged;
  List<DiffPart>? _parts;

  SectionDiff get _s => widget.section;
  bool get _unchanged => _s.originalContent.trim() == _s.tailoredContent.trim();

  @override
  void didUpdateWidget(_SectionRow old) {
    super.didUpdateWidget(old);
    if (old.section.originalContent != _s.originalContent || old.section.tailoredContent != _s.tailoredContent) {
      _parts = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final dropped = _s.tailoredContent.trim().isEmpty && _s.originalContent.trim().isNotEmpty;
    final added = _s.originalContent.trim().isEmpty && _s.tailoredContent.trim().isNotEmpty;

    Widget? tag;
    if (dropped) {
      tag = const Tag('Removed', tone: TagTone.strike);
    } else if (added) {
      tag = const Tag('New section', tone: TagTone.pencil);
    } else if (_unchanged) {
      tag = const Tag('Unchanged');
    } else if (_s.changeType != null) {
      tag = Tag(_changeLabel[_s.changeType] ?? 'Edited', tone: TagTone.pencil);
    }

    return Container(
      decoration: BoxDecoration(
        color: p.surface,
        border: Border.all(color: p.rule),
        borderRadius: BorderRadius.circular(2),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            onTap: () => setState(() => _open = !_open),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                spacing: 12,
                children: [
                  AnimatedRotation(
                    turns: _open ? 0.5 : 0,
                    duration: const Duration(milliseconds: 150),
                    child: Icon(Icons.expand_more, size: 18, color: p.inkFaint),
                  ),
                  Flexible(
                    child: Text(_s.title, style: AppFonts.serif(TextStyle(fontSize: 16, color: p.ink))),
                  ),
                  ?tag,
                ],
              ),
            ),
          ),
          if (_open) ...[
            Divider(color: p.rule, height: 1),
            if (_s.rationale != null)
              Container(
                color: p.surfaceSunk,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                child: Text(_s.rationale!, style: TextStyle(fontSize: 14, color: p.inkSoft)),
              ),
            if (_s.rationale != null) Divider(color: p.rule, height: 1),
            _column('Yours', DiffSide.original),
            Divider(color: p.rule, height: 1),
            _column('Tailored', DiffSide.tailored),
          ],
        ],
      ),
    );
  }

  Widget _column(String heading, DiffSide side) {
    final p = context.palette;
    // Computed once per section and shared by both columns, like the web's useMemo.
    final parts = _parts ??= diffWords(_s.originalContent, _s.tailoredContent);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            heading,
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: p.inkFaint),
          ),
          const SizedBox(height: 8),
          WordDiffView(parts: parts, side: side, format: widget.format),
        ],
      ),
    );
  }
}
