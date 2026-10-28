import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:resume_tailor/util/pdf_picker.dart';

import '../support/harness.dart';

Future<Harness> openRun(WidgetTester tester, {bool pdfEnabled = true}) async {
  final h = Harness();
  h.backend
    ..addResume()
    ..addProfile()
    ..addRun()
    ..pdfEnabled = pdfEnabled;
  await h.pump(tester);
  await tapTab(tester, 'History');
  await tester.tap(find.textContaining('Senior Backend Engineer', findRichText: true));
  await settle(tester);
  return h;
}

PickedPdf pdf(String name, [String content = '%PDF-1.7 mine']) =>
    PickedPdf(name, Uint8List.fromList(utf8.encode(content)));

void main() {
  testWidgets('nothing saved yet: explains both slots', (tester) async {
    final h = await openRun(tester);
    expect(find.text('Saved PDFs'), findsOneWidget);
    expect(find.text('COMPILED HERE'), findsOneWidget);
    expect(find.text('None yet. The PDF button above compiles and keeps one.'), findsOneWidget);
    expect(find.text('YOUR OWN PDF'), findsOneWidget);
    expect(find.text('Upload PDF'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('compiling keeps a copy, which downloads without recompiling', (tester) async {
    final h = await openRun(tester);
    await tapText(tester, 'PDF');
    expect(find.text('Last compiled PDF'), findsOneWidget);
    expect(find.textContaining('17 B ·'), findsOneWidget);

    final compiles = h.backend.log.where((l) => l == 'POST /api/runs/run3/pdf').length;
    await tester.tap(find.bySemanticsLabel('Download Last compiled PDF'));
    await settle(tester);
    expect(h.backend.log.last, 'GET /api/runs/run3/pdf');
    expect(h.backend.log.where((l) => l == 'POST /api/runs/run3/pdf'), hasLength(compiles));
    expect(h.sharer.shared.last.filename, 'resume-acme.pdf');
    await h.dispose(tester);
  });

  testWidgets('upload, download, replace and remove your own PDF', (tester) async {
    final h = await openRun(tester);

    h.pdfPicker.next = pdf('my resume.pdf');
    await tapText(tester, 'Upload PDF');
    expect(find.text('PDF saved with this run'), findsOneWidget);
    expect(find.text('my resume.pdf'), findsOneWidget);
    expect(h.backend.log, contains('PUT /api/runs/run3/pdfs/uploaded?filename=my+resume.pdf'));

    await tester.tap(find.bySemanticsLabel('Download my resume.pdf'));
    await settle(tester);
    expect(h.sharer.shared.last.filename, 'my resume.pdf');

    h.pdfPicker.next = pdf('final.pdf');
    await tapText(tester, 'Replace');
    expect(find.text('final.pdf'), findsOneWidget);

    await tapText(tester, 'Remove');
    expect(find.textContaining('Remove the PDF you uploaded'), findsOneWidget);
    await tapText(tester, 'Remove');
    expect(find.text('final.pdf'), findsNothing);
    expect(find.text('Upload PDF'), findsOneWidget);
    expect(h.backend.storedPdfs['run3']?['uploaded'], isNull);
    await h.dispose(tester);
  });

  testWidgets('a non-PDF is refused, locally by name and by the server by content', (tester) async {
    final h = await openRun(tester);

    h.pdfPicker.next = pdf('resume.docx');
    await tapText(tester, 'Upload PDF');
    expect(find.text('Choose a PDF file.'), findsOneWidget);
    expect(h.backend.log.where((l) => l.startsWith('PUT')), isEmpty);

    h.pdfPicker.next = pdf('fake.pdf', 'not really a pdf');
    await tapText(tester, 'Upload PDF');
    expect(find.text('That file is not a PDF.'), findsOneWidget);
    expect(find.text('Upload PDF'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('backing out of the picker does nothing', (tester) async {
    final h = await openRun(tester);
    await tapText(tester, 'Upload PDF');
    expect(h.backend.log.where((l) => l.startsWith('PUT')), isEmpty);
    await h.dispose(tester);
  });

  testWidgets('with compiling switched off, only the upload slot shows', (tester) async {
    final h = await openRun(tester, pdfEnabled: false);
    expect(find.text('COMPILED HERE'), findsNothing);
    expect(find.text('YOUR OWN PDF'), findsOneWidget);
    await h.dispose(tester);
  });
}
