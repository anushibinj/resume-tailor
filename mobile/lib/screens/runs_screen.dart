import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../util/format.dart';
import '../widgets/confirm.dart';
import '../widgets/job_listing_link.dart';
import '../widgets/toast.dart';
import '../widgets/ui.dart';
import 'common.dart';
import 'navigation.dart';

const _statusTone = {
  RunStatus.pending: TagTone.neutral,
  RunStatus.running: TagTone.neutral,
  RunStatus.completed: TagTone.pencil,
  RunStatus.failed: TagTone.strike,
};

const _statusLabel = {
  RunStatus.pending: 'Queued',
  RunStatus.running: 'Running',
  RunStatus.completed: 'Done',
  RunStatus.failed: 'Failed',
};

/// Deletes a run after the same confirmation the web client asks for. Returns whether it
/// was deleted.
Future<bool> confirmAndDeleteRun(BuildContext context, WidgetRef ref, String id, {String? role}) async {
  final label = role != null ? 'the run for $role' : 'this run';
  final confirmed = await confirmDestructive(
    context,
    "Delete $label? This removes its history, diff and any compiled PDF. This can't be undone.",
  );
  if (!confirmed) return false;
  try {
    await ref.read(apiClientProvider).deleteRun(id);
    toastSuccess('Run deleted');
    ref.invalidate(runsProvider);
    return true;
  } catch (error) {
    toastError(describeError(error));
    return false;
  }
}

class RunsScreen extends ConsumerStatefulWidget {
  const RunsScreen({super.key});

  @override
  ConsumerState<RunsScreen> createState() => _RunsScreenState();
}

class _RunsScreenState extends ConsumerState<RunsScreen> {
  bool _removing = false;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final runs = ref.watch(runsProvider(50));
    final tailorButton = AppButton(
      variant: ButtonVariant.primary,
      label: 'Tailor to a posting',
      onPressed: () => goToTab(context, ref, ShellTab.tailor),
    );

    return RefreshIndicator(
      onRefresh: () => ref.refresh(runsProvider(50).future),
      child: ListView(
        padding: screenPadding,
        children: [
          PageHeading(
            title: 'History',
            lede: "Every tailoring run you've made, with the copy you actually sent.",
            actions: [tailorButton],
          ),
          if (runs.isLoading && !runs.hasValue) const LoadingBlock(),
          if (runs.hasError && !runs.hasValue)
            ErrorBlock(error: runs.error!, onRetry: () => ref.invalidate(runsProvider(50))),
          if (runs.value?.content.isEmpty ?? false)
            EmptyState(
              title: 'No runs yet',
              body: "Once you tailor a resume to a posting it'll show up here, so you can always see what you sent to whom.",
              action: tailorButton,
            ),
          for (final run in runs.value?.content ?? const <RunSummary>[])
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Panel(
                padding: const EdgeInsets.all(16),
                onTap: () => openRun(context, run.id),
                child: Row(
                  spacing: 12,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Wrap(
                            spacing: 8,
                            runSpacing: 4,
                            crossAxisAlignment: WrapCrossAlignment.center,
                            children: [
                              RoleTitle(role: run.role, company: run.company, fontSize: 18),
                              Tag(_statusLabel[run.status]!, tone: _statusTone[run.status]!),
                            ],
                          ),
                          if (run.jobUrl != null) JobListingLink(url: run.jobUrl, maxLength: 32),
                          const SizedBox(height: 4),
                          Text(
                            'From ${run.resumeName ?? 'a deleted resume'} · ${formatRelative(run.createdAt)}',
                            style: TextStyle(fontSize: 14, color: p.inkSoft),
                          ),
                        ],
                      ),
                    ),
                    if (run.matchScore != null)
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text.rich(TextSpan(children: [
                            TextSpan(
                              text: '${run.matchScore}',
                              style: AppFonts.serif(TextStyle(fontSize: 24, height: 1, color: p.ink)),
                            ),
                            TextSpan(text: '%', style: TextStyle(fontSize: 14, color: p.inkFaint)),
                          ])),
                          const SizedBox(height: 4),
                          Text('covered', style: TextStyle(fontSize: 12, color: p.inkFaint)),
                        ],
                      ),
                    AppButton(
                      variant: ButtonVariant.danger,
                      compact: true,
                      icon: Icons.delete_outline,
                      semanticLabel: 'Delete run',
                      onPressed: _removing
                          ? null
                          : () async {
                              setState(() => _removing = true);
                              await confirmAndDeleteRun(context, ref, run.id, role: run.role);
                              if (mounted) setState(() => _removing = false);
                            },
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
