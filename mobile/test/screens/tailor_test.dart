import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/harness.dart';

void main() {
  testWidgets('blocked until there is a resume and a model', (tester) async {
    final h = Harness();
    await h.pump(tester);
    await tapTab(tester, 'Tailor');
    expect(find.text('Add a resume first — '), findsOneWidget);
    expect(find.text('Connect a model first — '), findsOneWidget);

    await tester.enterText(find.byType(TextField).last, 'A posting');
    await tester.pump();
    final button = find.ancestor(of: find.text('Tailor my resume'), matching: find.byType(TextButton));
    expect(tester.widget<TextButton>(button).onPressed, isNull);

    await tapText(tester, 'go to Resumes');
    expect(find.text('Your resumes'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('starting a run opens it, and it polls until the rewrite lands', (tester) async {
    final h = Harness();
    h.backend
      ..addResume(name: 'Backend-leaning')
      ..addResume(name: 'Leadership', isDefault: false)
      ..addProfile();
    await h.pump(tester);
    await tapTab(tester, 'Tailor');

    // The default resume is preselected.
    expect(find.text('Backend-leaning (default)'), findsOneWidget);
    await tester.tap(find.text('Backend-leaning (default)'));
    await settle(tester);
    await tester.tap(find.text('Leadership').last);
    await settle(tester);

    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), ' Acme ');
    await tester.enterText(fields.at(1), 'Staff Engineer');
    await tester.enterText(fields.at(2), 'https://acme.com/jobs/1');
    await tester.enterText(fields.at(3), 'We want Java.');
    await tapText(tester, 'Tailor my resume');

    expect(h.backend.log, contains('POST /api/runs'));
    expect(find.text('Reading the posting and rewriting your resume…'), findsOneWidget);
    expect(find.text('This screen updates itself. Usually 20–60 seconds.'), findsOneWidget);

    await tester.pump(const Duration(seconds: 2));
    await settle(tester);
    expect(find.textContaining('Staff Engineer'), findsWidgets);
    expect(find.text('Summary length'), findsOneWidget);
    expect(find.text('Review edits'), findsOneWidget);

    // Back on the tab, the form is clear for the next posting.
    await tester.pageBack();
    await settle(tester);
    expect(find.text('We want Java.'), findsNothing);
    await h.dispose(tester);
  });

  testWidgets('a backend validation error is shown field by field', (tester) async {
    final h = Harness();
    h.backend
      ..addResume()
      ..addProfile();
    await h.pump(tester);
    await tapTab(tester, 'Tailor');
    await tester.enterText(find.byType(TextField).last, 'reject-me');
    await tapText(tester, 'Tailor my resume');
    // "Validation failed" alone says nothing actionable; the field and its message do.
    expect(find.text('jdText: must not be blank'), findsOneWidget);
    expect(find.text('Tailor to a posting'), findsOneWidget);
    await h.dispose(tester);
  });
}
