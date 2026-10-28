import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../util/format.dart';
import '../widgets/toast.dart';
import '../widgets/ui.dart';
import 'common.dart';

/// A resume file read on the device. Read here, not uploaded, so the API stays plain
/// JSON -- no multipart handling needed (the web client reads files in the browser for
/// the same reason).
class PickedResume {
  const PickedResume(this.name, this.text);

  final String name;
  final String text;
}

final _extension = RegExp(r'\.(tex|md|markdown|txt)$', caseSensitive: false);

Future<PickedResume?> pickResumeFile() async {
  // FileType.any: Android has no registered MIME type for .tex, so an extension filter
  // there would grey out the very files this is for.
  final files = await FilePicker.pickFiles(dialogTitle: 'Load a .tex or .md file');
  if (files.isEmpty) return null;
  final file = files.first;
  final text = utf8.decode(await file.readAsBytes(), allowMalformed: true);
  toastSuccess('Loaded ${file.name}');
  return PickedResume(file.name.replaceAll(_extension, ''), text);
}

class ResumesScreen extends ConsumerStatefulWidget {
  const ResumesScreen({super.key});

  @override
  ConsumerState<ResumesScreen> createState() => _ResumesScreenState();
}

class _ResumesScreenState extends ConsumerState<ResumesScreen> {
  final Set<String> _busy = {};

  void _invalidate() => ref.invalidate(resumesProvider);

  Future<void> _openForm({ResumeDetail? editing, PickedResume? picked}) async {
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ResumeFormScreen(editing: editing, picked: picked),
      ),
    );
  }

  Future<void> _edit(ResumeSummary resume) async {
    setState(() => _busy.add(resume.id));
    try {
      final detail = await ref.read(apiClientProvider).getResume(resume.id);
      if (mounted) await _openForm(editing: detail);
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _busy.remove(resume.id));
    }
  }

  Future<void> _run(String id, Future<void> Function() action, {String? success}) async {
    setState(() => _busy.add(id));
    try {
      await action();
      if (success != null) toastSuccess(success);
      _invalidate();
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _busy.remove(id));
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final resumes = ref.watch(resumesProvider);
    final api = ref.read(apiClientProvider);

    return RefreshIndicator(
      onRefresh: () => quietly(ref.refresh(resumesProvider.future)),
      child: ListView(
        // Pull-to-refresh needs an overscroll even when the list is shorter than the screen.
        physics: const AlwaysScrollableScrollPhysics(),
        padding: screenPadding,
        children: [
          PageHeading(
            title: 'Your resumes',
            lede:
                'Keep a few originals — one leaning backend, one leaning leadership — and pick the '
                'closest starting point for each job. Everything you tailor is built from one of these.',
            actions: [
              if (resumes.value?.isNotEmpty ?? false)
                AppButton(
                  variant: ButtonVariant.primary,
                  icon: Icons.add,
                  label: 'Add a resume',
                  onPressed: () => _openForm(),
                ),
            ],
          ),
          if (resumes.isLoading && !resumes.hasValue) const LoadingBlock(),
          if (resumes.hasError && !resumes.hasValue) ErrorBlock(error: resumes.error!, onRetry: _invalidate),
          if (resumes.value?.isEmpty ?? false)
            EmptyState(
              title: 'Nothing to tailor from yet',
              body:
                  'Upload the .tex or .md file you actually send to employers. The format is detected '
                  'for you, and for LaTeX the preamble is kept aside so no model can touch it.',
              action: Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.center,
                children: [
                  AppButton(
                    variant: ButtonVariant.primary,
                    icon: Icons.upload_file,
                    label: 'Choose a file',
                    onPressed: () async {
                      final picked = await pickResumeFile();
                      if (picked != null && mounted) await _openForm(picked: picked);
                    },
                  ),
                  AppButton(icon: Icons.edit_note, label: 'Paste it instead', onPressed: () => _openForm()),
                ],
              ),
            ),
          for (final resume in resumes.value ?? const <ResumeSummary>[])
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Panel(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Wrap(
                      spacing: 8,
                      runSpacing: 6,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Text(resume.name, style: AppFonts.serif(TextStyle(fontSize: 18, color: p.ink))),
                        if (resume.isDefault) const Tag('Default', tone: TagTone.pencil),
                        Tag(resume.format.label),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${resume.sectionCount} sections · ${formatCount(resume.characterCount)} characters · '
                      'updated ${formatRelative(resume.updatedAt)}',
                      style: TextStyle(fontSize: 14, color: p.inkSoft),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      spacing: 4,
                      children: [
                        AppButton(
                          compact: true,
                          label: 'Edit',
                          busy: _busy.contains(resume.id),
                          onPressed: _busy.contains(resume.id) ? null : () => _edit(resume),
                        ),
                        if (!resume.isDefault)
                          AppButton(
                            variant: ButtonVariant.ghost,
                            compact: true,
                            icon: Icons.star_outline,
                            semanticLabel: 'Make ${resume.name} the default',
                            onPressed: () => _run(resume.id, () => api.setDefaultResume(resume.id)),
                          ),
                        AppButton(
                          variant: ButtonVariant.danger,
                          compact: true,
                          icon: Icons.delete_outline,
                          semanticLabel: 'Delete ${resume.name}',
                          onPressed: () =>
                              _run(resume.id, () => api.deleteResume(resume.id), success: 'Resume deleted'),
                        ),
                      ],
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

/// The web's "Add a resume" / "Edit resume" side panel, as its own screen.
class ResumeFormScreen extends ConsumerStatefulWidget {
  const ResumeFormScreen({super.key, this.editing, this.picked});

  final ResumeDetail? editing;
  final PickedResume? picked;

  @override
  ConsumerState<ResumeFormScreen> createState() => _ResumeFormScreenState();
}

class _ResumeFormScreenState extends ConsumerState<ResumeFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _name = TextEditingController(text: widget.editing?.name ?? widget.picked?.name ?? '');
  late final _source = TextEditingController(text: widget.editing?.sourceText ?? widget.picked?.text ?? '');
  bool _saving = false;

  @override
  void dispose() {
    _name.dispose();
    _source.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final picked = await pickResumeFile();
    if (picked == null) return;
    setState(() {
      if (_name.text.isEmpty) _name.text = picked.name;
      _source.text = picked.text;
    });
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    final api = ref.read(apiClientProvider);
    final body = SaveResumeRequest(name: _name.text, sourceText: _source.text);
    try {
      final editing = widget.editing;
      final resume = editing != null ? await api.updateResume(editing.id, body) : await api.createResume(body);
      toastSuccess(
        '${editing != null ? 'Updated' : 'Added'} “${resume.name}” — read as ${resume.format.label}, '
        '${resume.sections.length} sections',
      );
      ref.invalidate(resumesProvider);
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String? _required(String? value) => value == null || value.isEmpty ? 'Required' : null;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final editing = widget.editing != null;
    return Scaffold(
      appBar: AppBar(title: Text(editing ? 'Edit resume' : 'Add a resume')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: screenPadding,
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          children: [
            Field(
              label: 'Name',
              hint: 'For you, not for employers.',
              child: TextFormField(
                controller: _name,
                validator: _required,
                decoration: const InputDecoration(hintText: 'Backend-leaning'),
              ),
            ),
            const SizedBox(height: 16),
            Align(
              alignment: Alignment.centerLeft,
              child: AppButton(icon: Icons.upload_file, label: 'Load a .tex or .md file', onPressed: _load),
            ),
            const SizedBox(height: 16),
            Field(
              label: 'Source',
              hint: 'Paste it here, or load a file above.',
              child: TextFormField(
                controller: _source,
                validator: _required,
                minLines: 14,
                maxLines: 24,
                autocorrect: false,
                enableSuggestions: false,
                keyboardType: TextInputType.multiline,
                style: AppFonts.mono(TextStyle(fontSize: 12, height: 1.6, color: p.ink)),
                decoration: const InputDecoration(
                  hintText: '\\documentclass{article}\n...\n\nor\n\n# Your Name\n## Experience',
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              spacing: 8,
              children: [
                AppButton(
                  variant: ButtonVariant.primary,
                  busy: _saving,
                  label: editing ? 'Save changes' : 'Add resume',
                  onPressed: _saving ? null : _save,
                ),
                AppButton(variant: ButtonVariant.ghost, label: 'Cancel', onPressed: () => Navigator.of(context).pop()),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
