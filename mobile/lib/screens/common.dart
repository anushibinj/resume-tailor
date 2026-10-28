import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'navigation.dart';

/// Screen padding: the web's `px-6 py-10` main area, scaled for a phone.
const screenPadding = EdgeInsets.fromLTRB(16, 20, 16, 32);

/// For `RefreshIndicator.onRefresh`: a failed refresh is already on screen as the
/// provider's error state, so the future itself must not rethrow.
Future<void> quietly(Future<Object?> future) => future.then<void>((_) {}, onError: (Object _) {});

/// "Add a resume first — go to Resumes. Connect a model first — go to Settings."
class SetupNotice extends ConsumerWidget {
  const SetupNotice({super.key, required this.noResumes, required this.noModel});

  final bool noResumes;
  final bool noModel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.palette;
    final style = TextStyle(fontSize: 14, color: p.ink);
    return Panel(
      tone: PanelTone.gap,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: 6,
        children: [
          if (noResumes)
            Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text('Add a resume first — ', style: style),
                InlineLink('go to Resumes', onTap: () => goToTab(context, ref, ShellTab.resumes)),
                Text('.', style: style),
              ],
            ),
          if (noModel)
            Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text('Connect a model first — ', style: style),
                InlineLink('go to Settings', onTap: () => openSettings(context)),
                Text('.', style: style),
              ],
            ),
        ],
      ),
    );
  }
}

/// The "Start from" / "Resume" picker shared by Tailor and Ask.
class ResumePicker extends StatelessWidget {
  const ResumePicker({
    super.key,
    required this.resumes,
    required this.value,
    required this.onChanged,
    required this.label,
  });

  final List<ResumeSummary> resumes;
  final String? value;
  final ValueChanged<String> onChanged;
  final String label;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Field(
      label: label,
      child: DropdownButtonFormField<String>(
        initialValue: resumes.any((r) => r.id == value) ? value : null,
        key: ValueKey(value),
        isExpanded: true,
        dropdownColor: p.surface,
        style: TextStyle(fontSize: 14, color: p.ink),
        items: [
          for (final resume in resumes)
            DropdownMenuItem(
              value: resume.id,
              child: Text('${resume.name}${resume.isDefault ? ' (default)' : ''}', overflow: TextOverflow.ellipsis),
            ),
        ],
        onChanged: (id) {
          if (id != null) onChanged(id);
        },
      ),
    );
  }
}

/// The default resume, else the first -- what both pickers preselect once the list arrives.
String? defaultResumeId(List<ResumeSummary>? resumes) {
  if (resumes == null || resumes.isEmpty) return null;
  return resumes.firstWhere((r) => r.isDefault, orElse: () => resumes.first).id;
}

/// A query that failed: the reason, and a way to try again.
class ErrorBlock extends StatelessWidget {
  const ErrorBlock({super.key, required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Panel(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: 12,
        children: [
          Text(describeError(error), style: TextStyle(fontSize: 14, color: p.strike)),
          AppButton(onPressed: onRetry, icon: Icons.refresh, label: 'Try again', compact: true),
        ],
      ),
    );
  }
}

/// "Senior Backend Engineer at Acme", with the company softened.
class RoleTitle extends StatelessWidget {
  const RoleTitle({super.key, required this.role, required this.company, this.fontSize = 16, this.maxLines});

  final String? role;
  final String? company;
  final double fontSize;
  final int? maxLines;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(text: role ?? 'Untitled role'),
          if (company != null)
            TextSpan(
              text: ' at $company',
              style: TextStyle(color: p.inkSoft),
            ),
        ],
      ),
      maxLines: maxLines,
      overflow: maxLines == null ? null : TextOverflow.ellipsis,
      style: AppFonts.serif(TextStyle(fontSize: fontSize, height: 1.25, color: p.ink)),
    );
  }
}
