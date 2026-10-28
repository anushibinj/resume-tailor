import 'package:flutter/material.dart';

import '../api/models.dart';
import '../theme/app_theme.dart';
import 'ui.dart';

const _groups = [
  (KeywordImportance.required, 'Required'),
  (KeywordImportance.preferred, 'Preferred'),
  (KeywordImportance.nice, 'Nice to have'),
];

typedef DecideCallback = void Function(String suggestionId, SuggestionStatus status);

/// What the posting asks for, and one-click additions for whatever the resume does not
/// show. Coverage is judged by the user's model rather than by matching strings, so each
/// covered requirement carries the line of the resume the judgment rests on.
class KeywordMargin extends StatelessWidget {
  const KeywordMargin({
    super.key,
    required this.keywords,
    required this.otherSuggestions,
    required this.onDecide,
    required this.pendingId,
    required this.modelUsed,
    this.onAddAllMissing,
    this.addingAll = false,
    this.isAddingSkill = false,
  });

  final List<KeywordResult> keywords;
  final List<Suggestion> otherSuggestions;
  final DecideCallback onDecide;
  final String? pendingId;
  final String? modelUsed;
  final ValueChanged<List<String>>? onAddAllMissing;
  final bool addingAll;
  final bool isAddingSkill;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    if (keywords.isEmpty && otherSuggestions.isEmpty) {
      return Text(
        'No requirements were extracted from this posting.',
        style: TextStyle(fontSize: 14, color: p.inkSoft),
      );
    }

    final missingIds = [
      for (final k in keywords)
        if (!k.isCovered && k.addition?.status == SuggestionStatus.proposed) k.addition!.id,
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 24,
      children: [
        if (onAddAllMissing != null && missingIds.isNotEmpty)
          AppButton(
            variant: ButtonVariant.primary,
            compact: true,
            expand: true,
            busy: addingAll,
            icon: Icons.add,
            label: 'Add all missing skills to resume',
            onPressed: addingAll || isAddingSkill ? null : () => onAddAllMissing!(missingIds),
          ),
        for (final (importance, label) in _groups) ?_group(context, importance, label),
        if (otherSuggestions.isNotEmpty)
          Container(
            padding: const EdgeInsets.only(top: 16),
            decoration: BoxDecoration(
              border: Border(top: BorderSide(color: p.rule)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              spacing: 8,
              children: [
                Text(
                  'Other additions',
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: p.ink),
                ),
                for (final suggestion in otherSuggestions)
                  _SuggestionRow(
                    suggestion: suggestion,
                    onDecide: onDecide,
                    busy: pendingId == suggestion.id || addingAll,
                    isAddingSkill: isAddingSkill,
                  ),
              ],
            ),
          ),
        Container(
          padding: const EdgeInsets.only(top: 12),
          decoration: BoxDecoration(
            border: Border(top: BorderSide(color: p.rule)),
          ),
          child: Text(
            'Checked by ${modelUsed ?? 'your model'}. Tap anything covered to see the line of your '
            'resume it read that from, or a missing skill\'s ⓘ to see what it means.',
            style: TextStyle(fontSize: 12, height: 1.5, color: p.inkSoft),
          ),
        ),
      ],
    );
  }

  Widget? _group(BuildContext context, KeywordImportance importance, String label) {
    final p = context.palette;
    final items = keywords.where((k) => k.importance == importance).toList();
    if (items.isEmpty) return null;
    final covered = items.where((k) => k.covered).toList();
    final gaps = items.where((k) => !k.covered).toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              label,
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: p.ink),
            ),
            Text(
              gaps.isEmpty ? 'all covered' : '${gaps.length} to add',
              style: TextStyle(fontSize: 12, color: p.inkFaint),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (covered.isNotEmpty) ...[
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: [
              for (final keyword in covered)
                Tooltip(
                  triggerMode: TooltipTriggerMode.tap,
                  message: keyword.evidence != null ? 'Your resume: "${keyword.evidence}"' : 'Covered',
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: p.pencilSoft,
                      borderRadius: BorderRadius.circular(2),
                      border: Border.all(color: p.pencil.withValues(alpha: 0.35)),
                    ),
                    child: Text(keyword.keyword, style: TextStyle(fontSize: 12, color: p.pencil)),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
        ],
        for (final keyword in gaps)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: _GapRow(
              keyword: keyword,
              onDecide: onDecide,
              busy: (keyword.addition != null && pendingId == keyword.addition!.id) || addingAll,
              isAddingSkill: isAddingSkill,
            ),
          ),
      ],
    );
  }
}

class _GapRow extends StatelessWidget {
  const _GapRow({required this.keyword, required this.onDecide, required this.busy, required this.isAddingSkill});

  final KeywordResult keyword;
  final DecideCallback onDecide;
  final bool busy;
  final bool isAddingSkill;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final addition = keyword.addition;
    final added = keyword.isCovered;
    final dismissed = addition?.status == SuggestionStatus.rejected;

    final Color background = dismissed ? p.surface : (added ? p.pencilSoft : p.gapSoft);
    final Color titleColor = dismissed ? p.inkSoft : (added ? p.pencil : p.gap);

    final content = Container(
      color: background,
      padding: const EdgeInsets.all(10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Flexible(
                child: Text(keyword.keyword, style: TextStyle(fontSize: 14, color: titleColor)),
              ),
              if (keyword.description != null)
                // On a phone there is no hover, so the explanation sits behind a tap target.
                Tooltip(
                  triggerMode: TooltipTriggerMode.tap,
                  message: keyword.description!,
                  child: Semantics(
                    label: 'What is ${keyword.keyword}?',
                    button: true,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      child: Icon(Icons.help_outline, size: 15, color: p.inkFaint),
                    ),
                  ),
                ),
              const Spacer(),
              if (added) const Tag('Added', tone: TagTone.pencil),
            ],
          ),
          if (addition != null && addition.content.toLowerCase() != keyword.keyword.toLowerCase())
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(addition.content, style: TextStyle(fontSize: 12, color: p.inkSoft)),
            ),
          if (addition?.targetSection != null && !dismissed)
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Text(
                '${added ? 'In' : 'Goes in'} ${addition!.targetSection}',
                style: TextStyle(fontSize: 12, color: p.inkFaint),
              ),
            ),
          const SizedBox(height: 8),
          if (addition != null)
            _DecisionButtons(
              suggestion: addition,
              onDecide: onDecide,
              busy: busy,
              isAddingSkill: isAddingSkill,
              addIcon: Icons.add,
            )
          else
            Text('Nothing offered for this one.', style: TextStyle(fontSize: 12, color: p.inkFaint)),
        ],
      ),
    );

    final framed = dismissed || added
        ? Container(
            decoration: BoxDecoration(
              border: Border.all(color: dismissed ? p.rule : p.pencil.withValues(alpha: 0.4)),
              borderRadius: BorderRadius.circular(2),
            ),
            child: content,
          )
        : DashedBorder(color: p.gap.withValues(alpha: 0.5), child: content);

    return Opacity(opacity: dismissed ? 0.6 : 1, child: framed);
  }
}

/// An addition kept from an earlier check, no longer tied to a listed requirement.
class _SuggestionRow extends StatelessWidget {
  const _SuggestionRow({
    required this.suggestion,
    required this.onDecide,
    required this.busy,
    required this.isAddingSkill,
  });

  final Suggestion suggestion;
  final DecideCallback onDecide;
  final bool busy;
  final bool isAddingSkill;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final accepted = suggestion.status == SuggestionStatus.accepted;
    return Opacity(
      opacity: suggestion.status == SuggestionStatus.rejected ? 0.6 : 1,
      child: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: accepted ? p.pencilSoft : p.surface,
          borderRadius: BorderRadius.circular(2),
          border: Border.all(color: accepted ? p.pencil.withValues(alpha: 0.4) : p.rule),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(suggestion.content, style: TextStyle(fontSize: 14, color: p.ink)),
            if (suggestion.rationale != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(suggestion.rationale!, style: TextStyle(fontSize: 12, color: p.inkSoft)),
              ),
            const SizedBox(height: 8),
            _DecisionButtons(
              suggestion: suggestion,
              onDecide: onDecide,
              busy: busy,
              isAddingSkill: isAddingSkill,
              addIcon: Icons.check,
            ),
          ],
        ),
      ),
    );
  }
}

/// "Add to resume" / "Skip" while proposed; "Remove" / "Undo" once decided.
class _DecisionButtons extends StatelessWidget {
  const _DecisionButtons({
    required this.suggestion,
    required this.onDecide,
    required this.busy,
    required this.isAddingSkill,
    required this.addIcon,
  });

  final Suggestion suggestion;
  final DecideCallback onDecide;
  final bool busy;
  final bool isAddingSkill;
  final IconData addIcon;

  @override
  Widget build(BuildContext context) {
    if (suggestion.status == SuggestionStatus.proposed) {
      return Wrap(
        spacing: 6,
        runSpacing: 6,
        children: [
          AppButton(
            variant: ButtonVariant.primary,
            compact: true,
            icon: addIcon,
            label: 'Add to resume',
            onPressed: busy || isAddingSkill ? null : () => onDecide(suggestion.id, SuggestionStatus.accepted),
          ),
          AppButton(
            variant: ButtonVariant.ghost,
            compact: true,
            icon: Icons.close,
            label: 'Skip',
            onPressed: busy ? null : () => onDecide(suggestion.id, SuggestionStatus.rejected),
          ),
        ],
      );
    }
    return AppButton(
      variant: ButtonVariant.ghost,
      compact: true,
      icon: Icons.undo,
      label: suggestion.status == SuggestionStatus.accepted ? 'Remove' : 'Undo',
      onPressed: busy ? null : () => onDecide(suggestion.id, SuggestionStatus.proposed),
    );
  }
}
