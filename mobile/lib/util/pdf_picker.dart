import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// A PDF chosen on the device, read into memory for upload.
class PickedPdf {
  const PickedPdf(this.name, this.bytes);

  final String name;
  final Uint8List bytes;
}

/// Opens the system file picker for a PDF; null when the user backs out. Behind a
/// provider so widget tests can stand in a fake, like [FileSharer].
abstract class PdfPicker {
  Future<PickedPdf?> pick();
}

class SystemPdfPicker implements PdfPicker {
  @override
  Future<PickedPdf?> pick() async {
    final files = await FilePicker.pickFiles(
      dialogTitle: 'Choose a PDF',
      type: FileType.custom,
      allowedExtensions: ['pdf'],
    );
    if (files.isEmpty) return null;
    final file = files.first;
    return PickedPdf(file.name, await file.readAsBytes());
  }
}

final pdfPickerProvider = Provider<PdfPicker>((ref) => SystemPdfPicker());
