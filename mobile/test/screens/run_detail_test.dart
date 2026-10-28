import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:resume_tailor/screens/navigation.dart';

import '../support/fake_backend.dart';
import '../support/harness.dart';

Future<Harness> openSeededRun(WidgetTester tester, {void Function(FakeBackend backend, Json run)? tweak}) async {
  final h = Harness();
  h.backend
    ..addResume()
    ..addProfile();
  final run = h.backend.addRun();
  tweak?.call(h.backend, run);
  await h.pump(tester);
  await tapTab(tester, 'History');
  await tester.tap(find.textContaining('Senior Backend Engineer', findRichText: true));
  await settle(tester);
  return h;
}

void main() {
  testWidgets('header: title, link, meta, coverage and export buttons', (tester) async {
    final h = await openSeededRun(tester);
    expect(find.textContaining('Senior Backend Engineer'), findsWidgets);
    expect(find.text('https://linkedin.com/jobapplic.../123456'), findsOneWidget);
    expect(find.textContaining('From Backend-leaning · Markdown · started'), findsOneWidget);
    expect(find.textContaining('gpt-4o-mini'), findsWidgets);
    expect(find.text('1 of 3 covered'), findsOneWidget);
    expect(find.text('.md'), findsOneWidget);
    expect(find.text('PDF'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('review tab: changed sections open with marks, unchanged ones closed', (tester) async {
    final h = await openSeededRun(tester);
    expect(find.text('Reworded'), findsOneWidget);
    expect(find.text('Unchanged'), findsOneWidget);
    expect(find.text('Leads with the stack the posting names.'), findsOneWidget);
    expect(find.text('Yours'), findsOneWidget);
    expect(find.text('Tailored'), findsOneWidget);

    await tapText(tester, 'Education');
    expect(find.text('Yours'), findsNWidgets(2));
    await h.dispose(tester);
  });

  testWidgets('requirements: covered evidence, one addition, then add all', (tester) async {
    final h = await openSeededRun(tester);
    await tapText(tester, 'Requirements');

    expect(find.text('What this job asks for'), findsOneWidget);
    expect(find.text('Java'), findsOneWidget);
    expect(find.text('1 to add'), findsNWidgets(2));
    expect(find.text('Goes in Skills'), findsNWidgets(2));
    expect(find.text('Terraform (infrastructure as code)'), findsOneWidget);
    expect(find.text('1,200 prompt + 800 completion tokens'), findsOneWidget);

    await tester.tap(find.text('Java'));
    await settle(tester, frames: 3);
    expect(find.text('Your resume: "Managed a team of five Java engineers"'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('What is Kubernetes?'));
    await settle(tester, frames: 3);
    expect(find.text('A container orchestration system.'), findsOneWidget);

    await tapText(tester, 'Add to resume');
    expect(find.text('Added'), findsOneWidget);
    expect(find.text('In Skills'), findsOneWidget);
    expect(find.text('Remove'), findsOneWidget);
    expect(find.text('2 of 3 covered'), findsOneWidget);

    await tapText(tester, 'Add all missing skills to resume');
    expect(find.text('Added'), findsNWidgets(2));
    expect(find.text('Add all missing skills to resume'), findsNothing);
    expect(find.text('3 of 3 covered'), findsOneWidget);

    await tester.tap(find.text('Remove').first);
    await settle(tester);
    expect(find.text('Added'), findsOneWidget);
    expect(h.backend.log.where((l) => l.startsWith('PATCH')), hasLength(3));
    await h.dispose(tester);
  });

  testWidgets('skip, then undo, an addition', (tester) async {
    final h = await openSeededRun(tester);
    await tapText(tester, 'Requirements');
    await tester.tap(find.text('Skip').first);
    await settle(tester);
    expect(find.text('Undo'), findsOneWidget);
    expect(find.text('Goes in Skills'), findsOneWidget);
    await tapText(tester, 'Undo');
    expect(find.text('Skip'), findsNWidgets(2));
    await h.dispose(tester);
  });

  testWidgets('re-check runs the gap analysis again and polls it', (tester) async {
    final h = await openSeededRun(tester);
    await tapText(tester, 'Requirements');
    await tapText(tester, 'Re-check');
    expect(find.text('Checking your resume against the posting…'), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    await settle(tester);
    expect(find.text('Add all missing skills to resume'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('summary length: the slider switches length and writes new options', (tester) async {
    final h = await openSeededRun(tester);
    expect(find.text('≈ 5 lines'), findsOneWidget);

    final slider = find.byType(Slider);
    await tester.tapAt(tester.getTopRight(slider) + const Offset(-24, 20));
    await settle(tester);
    expect(find.text('≈ 7 lines'), findsOneWidget);
    expect(h.backend.log, contains('PUT /api/runs/run3/summary'));
    expect((h.backend.runs['run3']!['summary'] as Json)['selectedLines'], 7);

    await tapText(tester, 'Write new options');
    expect(find.text('Writing summary options at 4 to 7 lines…'), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    await settle(tester);
    expect(find.text('≈ 7 lines'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('source and posting tabs, copy and export', (tester) async {
    final h = await openSeededRun(tester);
    final copied = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String);
      return null;
    });

    await tapText(tester, 'Final source');
    expect(find.text('Includes every suggestion you accepted. Your original preamble is untouched.'), findsOneWidget);
    expect(find.textContaining('Managed a team of five Java engineers.'), findsOneWidget);
    await tapText(tester, 'Copy');
    expect(copied.single, contains('Managed a team'));
    expect(find.text('Copied to clipboard'), findsOneWidget);

    await tapText(tester, 'Job description');
    expect(find.text('We want Java, Kubernetes and Terraform.'), findsOneWidget);

    await tapText(tester, '.md');
    expect(h.sharer.shared.single.filename, 'resume-acme.md');
    await tapText(tester, 'PDF');
    expect(find.text('PDF compiled'), findsOneWidget);
    expect(h.sharer.shared.last.filename, 'resume-acme.pdf');
    expect(h.sharer.shared.last.contentType, 'application/pdf');
    await h.dispose(tester);
  });

  testWidgets('PDF button is hidden when export is switched off', (tester) async {
    final h = await openSeededRun(tester, tweak: (backend, _) => backend.pdfEnabled = false);
    expect(find.text('.md'), findsOneWidget);
    expect(find.text('PDF'), findsNothing);
    await h.dispose(tester);
  });

  testWidgets('delete asks first, then returns to history', (tester) async {
    final h = await openSeededRun(tester);
    await tester.tap(find.byTooltip('Delete run'));
    await settle(tester);
    expect(find.textContaining("This can't be undone."), findsOneWidget);
    await tapText(tester, 'Cancel');
    expect(h.backend.runs, hasLength(1));

    await tester.tap(find.byTooltip('Delete run'));
    await settle(tester);
    await tapText(tester, 'Delete');
    expect(find.text('Run deleted'), findsOneWidget);
    expect(find.text('No runs yet'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('a failed run explains itself and links to settings and tailor', (tester) async {
    final h = Harness();
    h.backend.addResume();
    h.backend.addProfile();
    final run = h.backend.addRun(status: 'FAILED');
    await h.pump(tester);
    openRun(tester.element(find.byType(NavigationBar)), run['id']);
    await settle(tester);

    expect(find.text("This run didn't finish"), findsOneWidget);
    expect(find.text('The model returned no content'), findsOneWidget);
    expect(find.text('.md'), findsNothing);
    await tapText(tester, 'run it again');
    expect(find.text('Tailor to a posting'), findsWidgets);
    await h.dispose(tester);
  });

  testWidgets('gap check states: outdated, failed, and no summary yet', (tester) async {
    final h = await openSeededRun(
      tester,
      tweak: (_, run) {
        run['gapsStatus'] = 'OUTDATED';
        run['summary'] = {'status': 'NONE', 'error': null, 'selectedLines': null, 'variants': []};
      },
    );
    expect(find.text('This run was made before summary lengths existed.'), findsOneWidget);
    expect(find.text('1 of 3 covered'), findsNothing);
    await tapText(tester, 'Requirements');
    expect(find.textContaining('old keyword matcher'), findsOneWidget);
    expect(find.text('Re-check with your model'), findsOneWidget);
    await h.dispose(tester);

    final h2 = await openSeededRun(
      tester,
      tweak: (_, run) {
        run['gapsStatus'] = 'FAILED';
        run['gapsError'] = 'Fewer than half the requirements came back';
      },
    );
    await tapText(tester, 'Requirements');
    expect(find.text("The check didn't finish."), findsOneWidget);
    expect(find.text('Fewer than half the requirements came back'), findsOneWidget);
    await tapText(tester, 'Try again');
    expect(h2.backend.log, contains('POST /api/runs/run3/gaps'));
    await h2.dispose(tester);
  });

  testWidgets('an unknown run says so', (tester) async {
    final h = Harness();
    await h.pump(tester);
    openRun(tester.element(find.byType(NavigationBar)), 'missing');
    await settle(tester);
    expect(find.text("That run doesn't exist."), findsOneWidget);
    await tapText(tester, 'Back to history');
    expect(find.text('History'), findsWidgets);
    await h.dispose(tester);
  });
}
