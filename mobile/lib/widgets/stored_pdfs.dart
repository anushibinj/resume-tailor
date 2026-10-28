import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../state/providers.dart';
import '../theme/app_theme.dart';
import '../util/file_share.dart';
import '../util/format.dart';
import '../util/pdf_picker.dart';
import 'confirm.dart';
import 'toast.dart';
import 'ui.dart';

/// The PDFs kept with a run: the last one compiled here, and one the user made elsewhere
/// (say, the exported Markdown run through their own converter) and uploaded. Both live in
/// the backend's configured storage, so they survive an app restart and follow the run.
///
/// Mirrors `frontend/src/components/stored-pdfs.tsx`.
class StoredPdfsPanel extends ConsumerStatefulWidget {
  const StoredPdfsPanel({super.key, required this.runId, required this.pdfEnabled});

  final String runId;
  final bool pdfEnabled;

  @override
  ConsumerState<StoredPdfsPanel> createState() => _StoredPdfsPanelState();
}

class _StoredPdfsPanelState extends ConsumerState<StoredPdfsPanel> {
  bool _uploading = false;
  bool _removing = false;
  bool _downloadingCompiled = false;
  bool _downloadingUploaded = false;

  ApiClient get _api => ref.read(apiClientProvider);

  Future<void> _guard(void Function(bool) set, Future<void> Function() action) async {
    setState(() => set(true));
    try {
      await action();
    } catch (error) {
      toastError(describeError(error));
    } finally {
      if (mounted) setState(() => set(false));
    }
  }

  Future<void> _upload() async {
    final PickedPdf? picked;
    try {
      picked = await ref.read(pdfPickerProvider).pick();
    } catch (_) {
      toastError("Couldn't open that file.");
      return;
    }
    if (picked == null) return;
    if (!picked.name.toLowerCase().endsWith('.pdf')) {
      toastError('Choose a PDF file.');
      return;
    }
    await _guard((v) => _uploading = v, () async {
      await _api.uploadPdf(widget.runId, picked!.bytes, picked.name);
      ref.invalidate(storedPdfsProvider(widget.runId));
      toastSuccess('PDF saved with this run');
    });
  }

  Future<void> _remove() async {
    if (!await confirmDestructive(
      context,
      "Remove the PDF you uploaded for this run? This can't be undone.",
      confirmLabel: 'Remove',
    )) {
      return;
    }
    await _guard((v) => _removing = v, () async {
      await _api.removeUploadedPdf(widget.runId);
      ref.invalidate(storedPdfsProvider(widget.runId));
    });
  }

  Future<void> _downloadCompiled() => _guard(
    (v) => _downloadingCompiled = v,
    () async => ref.read(fileSharerProvider).share(await _api.downloadCompiledPdf(widget.runId)),
  );

  Future<void> _downloadUploaded() => _guard(
    (v) => _downloadingUploaded = v,
    () async => ref.read(fileSharerProvider).share(await _api.downloadUploadedPdf(widget.runId)),
  );

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    final stored = ref.watch(storedPdfsProvider(widget.runId));
    final soft = TextStyle(fontSize: 14, color: p.inkSoft);

    if (stored.isLoading && !stored.hasValue) return const LoadingBlock();
    if (stored.hasError && !stored.hasValue) return Text(describeError(stored.error!), style: soft);

    final compiled = stored.value!.compiled;
    final uploaded = stored.value!.uploaded;
    final busy = _uploading || _removing;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 16,
      children: [
        if (widget.pdfEnabled || compiled != null)
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Eyebrow('Compiled here'),
              if (compiled != null)
                _PdfRow(
                  pdf: compiled,
                  label: 'Last compiled PDF',
                  onDownload: _downloadCompiled,
                  downloading: _downloadingCompiled,
                )
              else
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text('None yet. The PDF button above compiles and keeps one.', style: soft),
                ),
            ],
          ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Eyebrow('Your own PDF'),
            if (uploaded != null) ...[
              _PdfRow(
                pdf: uploaded,
                label: uploaded.originalFilename ?? 'Uploaded PDF',
                onDownload: _downloadUploaded,
                downloading: _downloadingUploaded,
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  AppButton(
                    variant: ButtonVariant.ghost,
                    compact: true,
                    icon: Icons.upload,
                    busy: _uploading,
                    label: 'Replace',
                    onPressed: busy ? null : _upload,
                  ),
                  AppButton(
                    variant: ButtonVariant.danger,
                    compact: true,
                    icon: Icons.delete_outline,
                    busy: _removing,
                    label: 'Remove',
                    onPressed: busy ? null : _remove,
                  ),
                ],
              ),
            ] else ...[
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(
                  'Made the PDF yourself, from the exported source? Keep it here with this run.',
                  style: soft,
                ),
              ),
              const SizedBox(height: 8),
              AppButton(icon: Icons.upload, busy: _uploading, label: 'Upload PDF', onPressed: busy ? null : _upload),
            ],
          ],
        ),
      ],
    );
  }
}

class _PdfRow extends StatelessWidget {
  const _PdfRow({required this.pdf, required this.label, required this.onDownload, required this.downloading});

  final StoredPdf pdf;
  final String label;
  final VoidCallback onDownload;
  final bool downloading;

  @override
  Widget build(BuildContext context) {
    final p = context.palette;
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Row(
        spacing: 12,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 14, color: p.ink),
                ),
                Text(
                  '${formatBytes(pdf.sizeBytes)} · ${formatDate(pdf.storedAt)}',
                  style: TextStyle(fontSize: 12, color: p.inkFaint),
                ),
              ],
            ),
          ),
          AppButton(
            variant: ButtonVariant.ghost,
            compact: true,
            icon: Icons.download,
            busy: downloading,
            semanticLabel: 'Download $label',
            onPressed: downloading ? null : onDownload,
          ),
        ],
      ),
    );
  }
}
