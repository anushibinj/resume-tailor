import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../util/format.dart';
import '../widgets/ui.dart';
import 'common.dart';
import 'navigation.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.palette;
    final runs = ref.watch(runsProvider(5));
    final resumes = ref.watch(resumesProvider);
    final profiles = ref.watch(profilesProvider);

    final needsResume = resumes.hasValue && resumes.value!.isEmpty;
    final needsModel = profiles.hasValue && profiles.value!.isEmpty;
    final ready = !needsResume && !needsModel;

    return RefreshIndicator(
      onRefresh: () => quietly(
        Future.wait([
          ref.refresh(runsProvider(5).future),
          ref.refresh(resumesProvider.future),
          ref.refresh(profilesProvider.future),
        ]),
      ),
      child: ListView(
        // Pull-to-refresh needs an overscroll even when the list is shorter than the screen.
        physics: const AlwaysScrollableScrollPhysics(),
        padding: screenPadding,
        children: [
          Text(
            'One resume. A different emphasis for every job.',
            style: AppFonts.serif(TextStyle(fontSize: 34, height: 1.1, color: p.ink)),
          ),
          const SizedBox(height: 16),
          Text(
            "Paste a posting and your resume gets rewritten to speak to it — reordered, reworded, "
            "trimmed. Nothing is invented. Anything the job wants that you can't back up is listed "
            "separately, for you to decide on.",
            style: TextStyle(fontSize: 16, height: 1.6, color: p.inkSoft),
          ),
          if (ready) ...[
            const SizedBox(height: 24),
            Align(
              alignment: Alignment.centerLeft,
              child: AppButton(
                variant: ButtonVariant.primary,
                label: 'Tailor to a posting',
                icon: Icons.arrow_forward,
                onPressed: () => goToTab(context, ref, ShellTab.tailor),
              ),
            ),
          ],
          const SizedBox(height: 40),
          if (!ready && (resumes.hasValue || profiles.hasValue)) ...[
            _SetupStep(
              done: !needsModel,
              title: 'Connect your model',
              body: 'Any OpenAI-compatible endpoint — OpenAI, Groq, or something running on your own machine.',
              onTap: () => openSettings(context),
            ),
            const SizedBox(height: 12),
            _SetupStep(
              done: !needsResume,
              title: 'Add your resume',
              body: 'The .tex or .md file you already send to employers.',
              onTap: () => goToTab(context, ref, ShellTab.resumes),
            ),
            const SizedBox(height: 40),
          ],
          Container(
            padding: const EdgeInsets.only(bottom: 12),
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(
              border: Border(bottom: BorderSide(color: p.rule)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text('Recent runs', style: AppFonts.serif(TextStyle(fontSize: 20, color: p.ink))),
                if (runs.value?.content.isNotEmpty ?? false)
                  InlineLink('See all', onTap: () => goToTab(context, ref, ShellTab.history)),
              ],
            ),
          ),
          if (runs.isLoading && !runs.hasValue) const LoadingBlock(),
          if (runs.hasError && !runs.hasValue)
            ErrorBlock(error: runs.error!, onRetry: () => ref.invalidate(runsProvider(5))),
          if (runs.value?.content.isEmpty ?? false)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Text('Nothing yet.', style: TextStyle(fontSize: 14, color: p.inkSoft)),
            ),
          for (final run in runs.value?.content ?? const <RunSummary>[])
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Panel(
                padding: const EdgeInsets.all(16),
                onTap: () => openRun(context, run.id),
                child: Row(
                  spacing: 16,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          RoleTitle(role: run.role, company: run.company, maxLines: 1),
                          const SizedBox(height: 2),
                          Text(formatRelative(run.createdAt), style: TextStyle(fontSize: 14, color: p.inkSoft)),
                        ],
                      ),
                    ),
                    if (run.status != RunStatus.completed)
                      Tag(
                        run.status == RunStatus.failed ? 'Failed' : 'Running',
                        tone: run.status == RunStatus.failed ? TagTone.strike : TagTone.neutral,
                      )
                    else
                      _Score(score: run.matchScore, fontSize: 20),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _Score extends StatelessWidget {
  const _Score({required this.score, required this.fontSize});

  final int? score;
  final double fontSize;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: '${score ?? '—'}',
            style: AppFonts.serif(TextStyle(fontSize: fontSize, color: p.ink)),
          ),
          TextSpan(
            text: '%',
            style: TextStyle(fontSize: 14, color: p.inkFaint),
          ),
        ],
      ),
    );
  }
}

class _SetupStep extends StatelessWidget {
  const _SetupStep({required this.done, required this.title, required this.body, required this.onTap});

  final bool done;
  final String title;
  final String body;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Panel(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            spacing: 8,
            children: [
              Flexible(
                child: Text(title, style: AppFonts.serif(TextStyle(fontSize: 18, color: p.ink))),
              ),
              if (done) const Tag('Done', tone: TagTone.pencil),
            ],
          ),
          const SizedBox(height: 6),
          Text(body, style: TextStyle(fontSize: 14, color: p.inkSoft)),
        ],
      ),
    );
  }
}
