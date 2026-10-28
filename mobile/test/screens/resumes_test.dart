import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/harness.dart';

void main() {
  testWidgets('empty library offers a file or pasting, and a pasted resume is added', (tester) async {
    final h = Harness();
    await h.pump(tester);
    await tapTab(tester, 'Resumes');

    expect(find.text('Nothing to tailor from yet'), findsOneWidget);
    expect(find.text('Choose a file'), findsOneWidget);

    await tapText(tester, 'Paste it instead');
    expect(find.text('Add a resume'), findsWidgets);

    // Required fields are checked before anything is sent.
    await tapText(tester, 'Add resume');
    expect(find.text('Required'), findsNWidgets(2));
    expect(h.backend.log.where((l) => l == 'POST /api/resumes'), isEmpty);

    await tester.enterText(find.byType(TextFormField).at(0), 'Leadership');
    await tester.enterText(find.byType(TextFormField).at(1), '# Ada\n## Experience\nLed things.');
    await tapText(tester, 'Add resume');

    expect(find.textContaining('Added “Leadership” — read as Markdown, 1 sections'), findsOneWidget);
    expect(find.text('Leadership'), findsOneWidget);
    expect(find.text('Default'), findsOneWidget);
    expect(find.text('Markdown'), findsOneWidget);
    expect(find.textContaining('3 sections · 1,234 characters'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('edit loads the full source, and saving updates the list', (tester) async {
    final h = Harness();
    h.backend.addResume(name: 'Backend-leaning');
    await h.pump(tester);
    await tapTab(tester, 'Resumes');

    await tapText(tester, 'Edit');
    expect(find.text('Edit resume'), findsOneWidget);
    expect(find.textContaining('Led a team of five.'), findsOneWidget);

    await tester.enterText(find.byType(TextFormField).at(0), 'Backend v2');
    await tapText(tester, 'Save changes');
    expect(find.textContaining('Updated “Backend v2”'), findsOneWidget);
    expect(find.text('Backend v2'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('make default and delete', (tester) async {
    final h = Harness();
    h.backend
      ..addResume(name: 'One')
      ..addResume(name: 'Two', isDefault: false);
    await h.pump(tester);
    await tapTab(tester, 'Resumes');

    await tester.tap(find.bySemanticsLabel('Make Two the default'));
    await settle(tester);
    expect(h.backend.resumes.firstWhere((r) => r['name'] == 'Two')['isDefault'], isTrue);
    expect(find.bySemanticsLabel('Make One the default'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('Delete One'));
    await settle(tester);
    expect(find.text('Resume deleted'), findsOneWidget);
    expect(find.text('One'), findsNothing);
    await h.dispose(tester);
  });

  testWidgets('a LaTeX resume is labelled as such', (tester) async {
    final h = Harness();
    h.backend.addResume(format: 'LATEX');
    await h.pump(tester);
    await tapTab(tester, 'Resumes');
    expect(find.text('LaTeX'), findsOneWidget);
    await h.dispose(tester);
  });
}
