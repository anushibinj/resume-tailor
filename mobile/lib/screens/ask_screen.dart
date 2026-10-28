import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../util/format.dart';
import '../widgets/confirm.dart';
import '../widgets/toast.dart';
import '../widgets/ui.dart';
import 'common.dart';
import 'navigation.dart';

class AskScreen extends ConsumerStatefulWidget {
  const AskScreen({super.key});

  @override
  ConsumerState<AskScreen> createState() => _AskScreenState();
}

class _AskScreenState extends ConsumerState<AskScreen> {
  String? _resumeId;
  final _question = TextEditingController();
  bool _asking = false;
  bool _removing = false;

  // The question and answer currently on screen. Once this is set the form is replaced by
  // the answer and a single "ask another" action -- this is never a thread to append to.
  ResumeQuestion? _asked;

  @override
  void initState() {
    super.initState();
    _question.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _question.dispose();
    super.dispose();
  }

  void _startNewChat() {
    setState(() {
      _asked = null;
      _question.clear();
    });
  }

  Future<void> _ask() async {
    final resumeId = _resumeId;
    if (resumeId == null || _question.text.trim().isEmpty) return;
    FocusScope.of(context).unfocus();
    setState(() => _asking = true);
    try {
      final result = await ref.read(apiClientProvider).askQuestion(resumeId, _question.text.trim());
      setState(() => _asked = result);
      ref.invalidate(questionsProvider(resumeId));
      if (result.status == QuestionStatus.failed) {
        toastError(result.errorMessage ?? 'The question could not be answered');
      }
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _asking = false);
    }
  }

  Future<void> _delete(ResumeQuestion item) async {
    if (!await confirmDestructive(context, "Delete this question and its answer? This can't be undone.")) return;
    setState(() => _removing = true);
    try {
      await ref.read(apiClientProvider).deleteQuestion(item.resumeId, item.id);
      toastSuccess('Question deleted');
      // If the answer on screen is the one just deleted, drop it too rather than leave a
      // question on screen that the history no longer has.
      if (_asked?.id == item.id) _startNewChat();
      ref.invalidate(questionsProvider(item.resumeId));
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _removing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final resumes = ref.watch(resumesProvider);
    final profiles = ref.watch(profilesProvider);
    // Preselect the default resume once the list arrives.
    if (_resumeId == null || (resumes.hasValue && !resumes.value!.any((r) => r.id == _resumeId))) {
      _resumeId = defaultResumeId(resumes.value);
    }

    final noResumes = resumes.hasValue && resumes.value!.isEmpty;
    final noModel = profiles.hasValue && profiles.value!.isEmpty;

    return ListView(
      padding: screenPadding,
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      children: [
        const PageHeading(
          title: 'Ask about a resume',
          lede:
              "Ask one question at a time about a resume you've uploaded -- your core experience, a "
              "specific skill, how a project might be summarised. Each question is answered on its own, "
              "from that resume alone; there's no back-and-forth to keep track of.",
        ),
        if (noResumes)
          EmptyState(
            title: 'Nothing to ask about yet',
            body: 'Upload a resume first, then come back here to ask questions about it.',
            action: AppButton(
              variant: ButtonVariant.primary,
              label: 'Add a resume',
              onPressed: () => goToTab(context, ref, ShellTab.resumes),
            ),
          )
        else ...[
          if (noModel) ...[const SetupNotice(noResumes: false, noModel: true), const SizedBox(height: 24)],
          Panel(
            child: ResumePicker(
              label: 'Resume',
              resumes: resumes.value ?? const [],
              value: _resumeId,
              onChanged: (id) {
                setState(() => _resumeId = id);
                _startNewChat();
              },
            ),
          ),
          const SizedBox(height: 24),
          Panel(child: _asked == null ? _form(noModel) : _answer(_asked!)),
          const SizedBox(height: 24),
          Text('Past questions', style: AppFonts.serif(TextStyle(fontSize: 18, color: p.ink))),
          const SizedBox(height: 12),
          if (_resumeId != null) _history(_resumeId!),
        ],
      ],
    );
  }

  Widget _form(bool noModel) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      spacing: 16,
      children: [
        Field(
          label: 'Your question',
          hint: 'Answered from this resume alone -- a single response, no follow-up.',
          child: TextField(
            controller: _question,
            minLines: 3,
            maxLines: 6,
            textInputAction: TextInputAction.newline,
            decoration: const InputDecoration(
              hintText: 'Briefly tell about my core experience and technical strengths',
            ),
          ),
        ),
        AppButton(
          variant: ButtonVariant.primary,
          icon: Icons.send,
          busy: _asking,
          label: 'Ask',
          onPressed: _asking || _question.text.trim().isEmpty || noModel ? null : _ask,
        ),
      ],
    );
  }

  Widget _answer(ResumeQuestion asked) {
    final p = context.palette;
    final completed = asked.status == QuestionStatus.completed;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Eyebrow('You asked'),
        const SizedBox(height: 4),
        Text(asked.question, style: AppFonts.serif(TextStyle(fontSize: 18, color: p.ink))),
        const SizedBox(height: 16),
        Eyebrow(completed ? 'Answer' : 'Could not answer'),
        const SizedBox(height: 4),
        if (completed)
          SelectableText(asked.answer ?? '', style: TextStyle(fontSize: 14, height: 1.6, color: p.ink))
        else
          Text(asked.errorMessage ?? '', style: TextStyle(fontSize: 14, color: p.strike)),
        const SizedBox(height: 16),
        AppButton(
          variant: ButtonVariant.primary,
          icon: Icons.restart_alt,
          label: 'Ask another question in a new chat',
          onPressed: _startNewChat,
        ),
      ],
    );
  }

  Widget _history(String resumeId) {
    final p = context.palette;
    final history = ref.watch(questionsProvider(resumeId));
    if (history.isLoading && !history.hasValue) return const LoadingBlock();
    if (history.hasError && !history.hasValue) {
      return ErrorBlock(error: history.error!, onRetry: () => ref.invalidate(questionsProvider(resumeId)));
    }
    final items = history.value?.content ?? const <ResumeQuestion>[];
    if (items.isEmpty) {
      return Text('Nothing asked about this resume yet.', style: TextStyle(fontSize: 14, color: p.inkSoft));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 8,
      children: [
        for (final item in items)
          Panel(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(item.question, style: AppFonts.serif(TextStyle(fontSize: 16, color: p.ink))),
                    ),
                    if (item.status == QuestionStatus.failed) const Tag('Failed', tone: TagTone.strike),
                    const SizedBox(width: 8),
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(formatRelative(item.createdAt), style: TextStyle(fontSize: 12, color: p.inkFaint)),
                    ),
                    AppButton(
                      variant: ButtonVariant.danger,
                      compact: true,
                      icon: Icons.delete_outline,
                      semanticLabel: 'Delete question',
                      onPressed: _removing ? null : () => _delete(item),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                SelectableText(
                  (item.status == QuestionStatus.completed ? item.answer : item.errorMessage) ?? '',
                  style: TextStyle(
                    fontSize: 14,
                    height: 1.6,
                    color: item.status == QuestionStatus.failed ? p.strike : p.inkSoft,
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
