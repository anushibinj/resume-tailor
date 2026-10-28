import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../util/file_share.dart';
import '../util/format.dart';
import '../widgets/coverage_meter.dart';
import '../widgets/job_listing_link.dart';
import '../widgets/keyword_margin.dart';
import '../widgets/section_diff_view.dart';
import '../widgets/summary_length.dart';
import '../widgets/toast.dart';
import '../widgets/ui.dart';
import 'common.dart';
import 'navigation.dart';
import 'runs_screen.dart';

/// The web shows the requirements pane beside the document; a phone has no room beside
/// anything, so it becomes a tab of its own next to the three the web already has.
enum _Tab { review, requirements, source, posting }

const _tabLabels = {
  _Tab.review: 'Review edits',
  _Tab.requirements: 'Requirements',
  _Tab.source: 'Final source',
  _Tab.posting: 'Job description',
};

class RunDetailScreen extends ConsumerStatefulWidget {
  const RunDetailScreen({super.key, required this.id});

  final String id;

  @override
  ConsumerState<RunDetailScreen> createState() => _RunDetailScreenState();
}

class _RunDetailScreenState extends ConsumerState<RunDetailScreen> {
  _Tab _tab = _Tab.review;

  String? _pendingSuggestion;
  bool _isAddingSkill = false;
  // Set synchronously, before any await, so a second tap in the same frame is refused even
  // though the rebuild that disables the button hasn't happened yet.
  bool _addingSkillLock = false;
  bool _addingAll = false;
  bool _rechecking = false;
  bool _generating = false;
  bool _exportingSource = false;
  bool _exportingPdf = false;
  bool _removing = false;

  // Summary length moves are sent one after another so they reach the server in the order
  // they were made; the run is refetched once the last one lands.
  Future<void> _summaryQueue = Future.value();
  int _summaryInFlight = 0;

  String get _id => widget.id;
  ApiClient get _api => ref.read(apiClientProvider);
  RunNotifier get _run => ref.read(runProvider(_id).notifier);

  Future<void> _decide(String suggestionId, SuggestionStatus status) async {
    if (status == SuggestionStatus.accepted) {
      if (_addingSkillLock || _isAddingSkill || _addingAll) return;
      _addingSkillLock = true;
      setState(() {
        _isAddingSkill = true;
        _pendingSuggestion = suggestionId;
      });
    } else {
      if (_addingSkillLock || _isAddingSkill) return;
      setState(() => _pendingSuggestion = suggestionId);
    }
    try {
      _run.replace(await _api.updateSuggestion(_id, suggestionId, status));
    } catch (error) {
      toastError(describeError(error));
    } finally {
      _addingSkillLock = false;
      if (mounted) {
        setState(() {
          _isAddingSkill = false;
          _pendingSuggestion = null;
        });
      }
    }
  }

  // Applied one at a time (not in parallel) because each accept recomputes the whole
  // tailored document server-side; overlapping requests could race on that rebuild.
  Future<void> _addAllMissing(List<String> suggestionIds) async {
    if (_addingSkillLock || _isAddingSkill || _addingAll) return;
    setState(() => _addingAll = true);
    try {
      RunDetail? updated;
      for (final suggestionId in suggestionIds) {
        updated = await _api.updateSuggestion(_id, suggestionId, SuggestionStatus.accepted);
      }
      if (updated != null) _run.replace(updated);
    } catch (error) {
      toastError(describeError(error));
      // Some may have landed before the failure; show what the server now holds.
      await _run.refresh();
    } finally {
      if (mounted) setState(() => _addingAll = false);
    }
  }

  // Switching length only swaps in text that was already written, so it is cheap and each
  // move is applied to the run on screen straight away.
  void _selectSummary(RunDetail run, int lines) {
    _run.pausePolling();
    _run.replace(run.withSummary(run.summary.withSelectedLines(lines)));
    _summaryInFlight++;
    _summaryQueue = _summaryQueue.then((_) async {
      try {
        await _api.selectSummary(_id, lines);
      } catch (error) {
        toastError(describeError(error));
      } finally {
        _summaryInFlight--;
        // Brings the diff and source in line with the final choice, and snaps the slider
        // back if a move was refused.
        if (_summaryInFlight == 0 && mounted) await _run.refresh();
      }
    });
  }

  Future<void> _busy(void Function(bool) set, Future<void> Function() action) async {
    setState(() => set(true));
    try {
      await action();
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => set(false));
    }
  }

  Future<void> _generateSummary() =>
      _busy((v) => _generating = v, () async => _run.replace(await _api.generateSummary(_id)));

  Future<void> _recheck() => _busy((v) => _rechecking = v, () async => _run.replace(await _api.recheckGaps(_id)));

  Future<void> _exportSource() => _busy(
    (v) => _exportingSource = v,
    () async => ref.read(fileSharerProvider).share(await _api.downloadSource(_id)),
  );

  Future<void> _exportPdf() => _busy((v) => _exportingPdf = v, () async {
    final file = await _api.compilePdf(_id);
    toastSuccess('PDF compiled');
    await ref.read(fileSharerProvider).share(file);
  });

  Future<void> _delete(RunDetail run) async {
    setState(() => _removing = true);
    final deleted = await confirmAndDeleteRun(context, ref, run.id);
    if (!mounted) return;
    setState(() => _removing = false);
    if (deleted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final run = ref.watch(runProvider(_id));
    final data = run.value;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Run'),
        actions: [
          if (data != null)
            IconButton(
              tooltip: 'Delete run',
              color: context.palette.strike,
              onPressed: _removing ? null : () => _delete(data),
              icon: _removing ? const Spinner() : const Icon(Icons.delete_outline),
            ),
        ],
      ),
      body: switch (run) {
        AsyncValue(:final value?) => RefreshIndicator(onRefresh: _run.refresh, child: _body(value)),
        AsyncValue(hasError: true) => ListView(
          padding: screenPadding,
          children: [
            Panel(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                spacing: 8,
                children: [
                  Text("That run doesn't exist.", style: TextStyle(color: context.palette.ink)),
                  InlineLink('Back to history', onTap: () => goToTab(context, ref, ShellTab.history)),
                ],
              ),
            ),
          ],
        ),
        _ => ListView(
          padding: screenPadding,
          children: const [LoadingBlock(label: 'Loading run…')],
        ),
      },
    );
  }

  Widget _body(RunDetail data) {
    final p = context.palette;
    return ListView(
      // Pull-to-refresh needs an overscroll even when the list is shorter than the screen.
      physics: const AlwaysScrollableScrollPhysics(),
      padding: screenPadding,
      children: [
        _header(data),
        if (data.isRewriting) _WorkingNotice(status: data.status),
        if (data.status == RunStatus.failed) _FailureNotice(message: data.errorMessage),
        if (data.status == RunStatus.completed) ...[
          SummaryLength(
            summary: data.summary,
            onSelect: (lines) => _selectSummary(data, lines),
            onGenerate: _generateSummary,
            generating: _generating,
          ),
          const SizedBox(height: 20),
          _tabs(),
          const SizedBox(height: 16),
          switch (_tab) {
            _Tab.review => SectionDiffView(sections: data.sections, format: data.format),
            _Tab.requirements => _requirements(data),
            _Tab.source => _SourceView(source: data.tailoredSource ?? ''),
            _Tab.posting => Panel(
              child: SelectableText(
                data.jobDescriptionText ?? '',
                style: TextStyle(fontSize: 14, height: 1.6, color: p.inkSoft),
              ),
            ),
          },
        ],
      ],
    );
  }

  Widget _header(RunDetail data) {
    final p = context.palette;
    final pdfEnabled = ref.watch(pdfAvailableProvider(_id)).value;
    final meta = [
      'From ${data.resumeName ?? 'a deleted resume'}',
      data.format.label,
      'started ${formatDate(data.createdAt)}',
      ?data.modelUsed,
    ].join(' · ');

    return Container(
      margin: const EdgeInsets.only(bottom: 24),
      padding: const EdgeInsets.only(bottom: 20),
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: p.rule)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          RoleTitle(role: data.role, company: data.company, fontSize: 26),
          if (data.jobUrl != null)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: JobListingLink(url: data.jobUrl),
            ),
          const SizedBox(height: 8),
          Text(meta, style: TextStyle(fontSize: 14, color: p.inkSoft)),
          if (data.status == RunStatus.completed) ...[
            if (data.gapsStatus == GapsStatus.completed && data.keywords.isNotEmpty) ...[
              const SizedBox(height: 20),
              CoverageMeter(keywords: data.keywords, score: data.matchScore),
            ],
            const SizedBox(height: 20),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                AppButton(
                  icon: Icons.download,
                  busy: _exportingSource,
                  label: '.${data.format.extension}',
                  onPressed: _exportingSource ? null : _exportSource,
                ),
                // Hidden only once the server says PDF export is off -- while asking, the
                // button shows, like the web client.
                if (pdfEnabled != false)
                  AppButton(
                    variant: ButtonVariant.primary,
                    icon: Icons.picture_as_pdf_outlined,
                    busy: _exportingPdf,
                    label: 'PDF',
                    onPressed: _exportingPdf ? null : _exportPdf,
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _tabs() {
    final p = context.palette;
    return Container(
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: p.rule)),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (final tab in _Tab.values)
              InkWell(
                onTap: () => setState(() => _tab = tab),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  decoration: BoxDecoration(
                    border: Border(bottom: BorderSide(color: _tab == tab ? p.pencil : Colors.transparent, width: 2)),
                  ),
                  child: Text(_tabLabels[tab]!, style: TextStyle(fontSize: 14, color: _tab == tab ? p.ink : p.inkSoft)),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _requirements(RunDetail data) {
    final p = context.palette;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text('What this job asks for', style: AppFonts.serif(TextStyle(fontSize: 18, color: p.ink))),
                  ),
                  if (data.gapsStatus == GapsStatus.completed)
                    TextButton.icon(
                      onPressed: _rechecking ? null : _recheck,
                      style: TextButton.styleFrom(foregroundColor: p.inkSoft, textStyle: const TextStyle(fontSize: 12)),
                      icon: const Icon(Icons.refresh, size: 14),
                      label: const Text('Re-check'),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              _gapsPane(data),
            ],
          ),
        ),
        if (data.promptTokens != null && data.promptTokens! > 0)
          Padding(
            padding: const EdgeInsets.only(top: 16),
            child: Text(
              '${formatCount(data.promptTokens!)} prompt + ${formatCount(data.completionTokens ?? 0)} completion tokens',
              style: TextStyle(fontSize: 12, color: p.inkFaint),
            ),
          ),
      ],
    );
  }

  /// The requirements pane across every state the gap check can be in. Coverage from the
  /// retired keyword matcher is deliberately not shown: those numbers were wrong often
  /// enough that displaying them is worse than offering a re-check.
  Widget _gapsPane(RunDetail data) {
    final p = context.palette;
    final soft = TextStyle(fontSize: 14, color: p.inkSoft);

    switch (data.gapsStatus) {
      case GapsStatus.pending || GapsStatus.running:
        return Row(
          spacing: 12,
          children: [
            const Spinner(),
            Expanded(child: Text('Checking your resume against the posting…', style: soft)),
          ],
        );
      case GapsStatus.outdated:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          spacing: 12,
          children: [
            Text(
              "This run was checked by the old keyword matcher, which compared text literally and got it "
              "wrong often. Re-check it with your model to see what's really covered.",
              style: soft,
            ),
            AppButton(
              variant: ButtonVariant.primary,
              icon: Icons.refresh,
              busy: _rechecking,
              label: 'Re-check with your model',
              onPressed: _rechecking ? null : _recheck,
            ),
            if (data.otherSuggestions.isNotEmpty)
              KeywordMargin(
                keywords: const [],
                otherSuggestions: data.otherSuggestions,
                onDecide: _decide,
                pendingId: _pendingSuggestion,
                modelUsed: data.modelUsed,
                isAddingSkill: _isAddingSkill,
              ),
          ],
        );
      case GapsStatus.failed:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          spacing: 12,
          children: [
            Text("The check didn't finish.", style: TextStyle(fontSize: 14, color: p.ink)),
            if (data.gapsError != null) Text(data.gapsError!, style: soft),
            AppButton(
              icon: Icons.refresh,
              busy: _rechecking,
              label: 'Try again',
              onPressed: _rechecking ? null : _recheck,
            ),
          ],
        );
      case GapsStatus.completed:
        return KeywordMargin(
          keywords: data.keywords,
          otherSuggestions: data.otherSuggestions,
          onDecide: _decide,
          pendingId: _pendingSuggestion,
          modelUsed: data.modelUsed,
          onAddAllMissing: (ids) {
            if (_addingSkillLock || _isAddingSkill) return;
            _addAllMissing(ids);
          },
          addingAll: _addingAll,
          isAddingSkill: _isAddingSkill,
        );
    }
  }
}

class _SourceView extends StatelessWidget {
  const _SourceView({required this.source});

  final String source;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Panel(
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    'Includes every suggestion you accepted. Your original preamble is untouched.',
                    style: TextStyle(fontSize: 14, color: p.inkSoft),
                  ),
                ),
                AppButton(
                  variant: ButtonVariant.ghost,
                  compact: true,
                  icon: Icons.copy,
                  label: 'Copy',
                  onPressed: () async {
                    await Clipboard.setData(ClipboardData(text: source));
                    toastSuccess('Copied to clipboard');
                  },
                ),
              ],
            ),
          ),
          Divider(color: p.rule, height: 1),
          Container(
            color: p.surfaceSunk,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            // Source lines keep their own breaks, like a <pre>; long ones scroll sideways.
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SelectableText(source, style: AppFonts.mono(TextStyle(fontSize: 12, height: 1.6, color: p.ink))),
            ),
          ),
        ],
      ),
    );
  }
}

class _WorkingNotice extends StatelessWidget {
  const _WorkingNotice({required this.status});

  final RunStatus status;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Panel(
      child: Row(
        spacing: 12,
        children: [
          const Spinner(),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  status == RunStatus.pending ? 'Queued…' : 'Reading the posting and rewriting your resume…',
                  style: TextStyle(color: p.ink),
                ),
                const SizedBox(height: 2),
                Text(
                  'This screen updates itself. Usually 20–60 seconds.',
                  style: TextStyle(fontSize: 14, color: p.inkSoft),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _FailureNotice extends ConsumerWidget {
  const _FailureNotice({required this.message});

  final String? message;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final p = context.palette;
    final style = TextStyle(fontSize: 14, color: p.inkSoft);
    return Panel(
      tone: PanelTone.strike,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: 12,
        children: [
          Icon(Icons.warning_amber_rounded, color: p.strike, size: 20),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text("This run didn't finish", style: AppFonts.serif(TextStyle(fontSize: 18, color: p.ink))),
                    const Tag('Failed', tone: TagTone.strike),
                  ],
                ),
                const SizedBox(height: 6),
                Text(message ?? 'No error was recorded.', style: TextStyle(fontSize: 14, color: p.ink)),
                const SizedBox(height: 12),
                Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text('Your resume and the posting are both saved — check your model in ', style: style),
                    InlineLink('Settings', onTap: () => openSettings(context)),
                    Text(' and ', style: style),
                    InlineLink('run it again', onTap: () => goToTab(context, ref, ShellTab.tailor)),
                    Text('.', style: style),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
