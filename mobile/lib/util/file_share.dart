import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../api/api_client.dart';

/// What "download" means on a phone: the file is written to the app's cache and handed to
/// the system share sheet, from which the user saves it to Files / Drive or opens it in a
/// PDF viewer. The web client triggers a browser download instead.
abstract class FileSharer {
  Future<void> share(DownloadedFile file);
}

class SystemFileSharer implements FileSharer {
  @override
  Future<void> share(DownloadedFile file) async {
    final directory = await getTemporaryDirectory();
    final safeName = file.filename.replaceAll(RegExp(r'[/\\]'), '_');
    final path = '${directory.path}/$safeName';
    await File(path).writeAsBytes(file.bytes, flush: true);
    await SharePlus.instance.share(
      ShareParams(files: [XFile(path, mimeType: file.contentType, name: safeName)], title: safeName),
    );
  }
}

final fileSharerProvider = Provider<FileSharer>((ref) => SystemFileSharer());
