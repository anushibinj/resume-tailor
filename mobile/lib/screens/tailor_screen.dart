import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../widgets/toast.dart';
import '../widgets/ui.dart';
import 'common.dart';
import 'navigation.dart';

class TailorScreen extends ConsumerStatefulWidget {
  const TailorScreen({super.key});

  @override
  ConsumerState<TailorScreen> createState() => _TailorScreenState();
}

class _TailorScreenState extends ConsumerState<TailorScreen> {
  String? _resumeId;
  final _jd = TextEditingController();
  final _company = TextEditingController();
  final _role = TextEditingController();
  final _jobUrl = TextEditingController();
  bool _starting = false;

  @override
  void initState() {
    super.initState();
    _jd.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    for (final c in [_jd, _company, _role, _jobUrl]) {
      c.dispose();
    }
    super.dispose();
  }

  String? _trimmed(TextEditingController c) => c.text.trim().isEmpty ? null : c.text.trim();

  Future<void> _start() async {
    setState(() => _starting = true);
    try {
      final run = await ref.read(apiClientProvider).createRun(CreateRunRequest(
            resumeId: _resumeId,
            jdText: _jd.text,
            company: _trimmed(_company),
            role: _trimmed(_role),
            jobUrl: _trimmed(_jobUrl),
          ));
      ref.invalidate(runsProvider);
      // The web client leaves this page for the run; here the tab stays, so it is cleared
      // for the next posting instead.
      for (final c in [_jd, _company, _role, _jobUrl]) {
        c.clear();
      }
      if (mounted) openRun(context, run.id);
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final resumes = ref.watch(resumesProvider);
    final profiles = ref.watch(profilesProvider);
    // Preselect the default resume once the list arrives.
    _resumeId ??= defaultResumeId(resumes.value);
    if (_resumeId != null && resumes.hasValue && !resumes.value!.any((r) => r.id == _resumeId)) {
      _resumeId = defaultResumeId(resumes.value);
    }

    final noResumes = resumes.hasValue && resumes.value!.isEmpty;
    final noModel = profiles.hasValue && profiles.value!.isEmpty;
    final blocked = noResumes || noModel;

    return ListView(
      padding: screenPadding,
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      children: [
        const PageHeading(
          title: 'Tailor to a posting',
          lede: "Paste the job description. You'll get a rewritten copy of your resume, every edit "
              "marked up for review, and a list of what the posting wants that you can't currently back up.",
        ),
        if (blocked) ...[
          SetupNotice(noResumes: noResumes, noModel: noModel),
          const SizedBox(height: 24),
        ],
        Panel(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            spacing: 16,
            children: [
              ResumePicker(
                label: 'Start from',
                resumes: resumes.value ?? const [],
                value: _resumeId,
                onChanged: (id) => setState(() => _resumeId = id),
              ),
              Field(
                label: 'Company',
                hint: 'Optional — read from the posting if left blank.',
                child: TextField(controller: _company, decoration: const InputDecoration(hintText: 'Acme')),
              ),
              Field(
                label: 'Role',
                hint: 'Optional.',
                child: TextField(controller: _role, decoration: const InputDecoration(hintText: 'Senior Backend Engineer')),
              ),
              Field(
                label: 'Job URL',
                hint: 'Optional — link to the job posting.',
                child: TextField(
                  controller: _jobUrl,
                  keyboardType: TextInputType.url,
                  autocorrect: false,
                  decoration: const InputDecoration(hintText: 'https://example.com/careers/job-posting'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        Panel(
          child: Field(
            label: 'Job description',
            hint: 'The whole posting works better than a summary of it.',
            child: TextField(
              controller: _jd,
              minLines: 12,
              maxLines: 18,
              keyboardType: TextInputType.multiline,
              style: TextStyle(fontSize: 14, height: 1.6, color: p.ink),
              decoration: const InputDecoration(hintText: 'Paste the full posting here…'),
            ),
          ),
        ),
        const SizedBox(height: 20),
        Wrap(
          spacing: 12,
          runSpacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            AppButton(
              variant: ButtonVariant.primary,
              icon: Icons.auto_fix_high,
              busy: _starting,
              label: 'Tailor my resume',
              onPressed: _starting || blocked || _jd.text.trim().isEmpty ? null : _start,
            ),
            Text('Usually takes 20–60 seconds.', style: TextStyle(fontSize: 14, color: p.inkSoft)),
          ],
        ),
      ],
    );
  }
}
