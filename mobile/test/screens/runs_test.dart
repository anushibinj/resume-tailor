import 'package:flutter_test/flutter_test.dart';

import '../support/harness.dart';

void main() {
  testWidgets('empty history points at tailoring', (tester) async {
    final h = Harness();
    await h.pump(tester);
    await tapTab(tester, 'History');
    expect(find.text('No runs yet'), findsOneWidget);
    await tapText(tester, 'Tailor to a posting');
    expect(find.text('Tailor my resume'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('lists runs with status, link, source resume and score', (tester) async {
    final h = Harness();
    h.backend
      ..addResume()
      ..addRun()
      ..addRun(status: 'RUNNING', role: 'Platform Engineer', company: null)
      ..addRun(status: 'FAILED', role: 'Staff Engineer');
    await h.pump(tester);
    await tapTab(tester, 'History');

    expect(find.text('Done'), findsOneWidget);
    expect(find.text('Running'), findsOneWidget);
    expect(find.text('Failed'), findsOneWidget);
    expect(find.text('covered'), findsOneWidget);
    expect(find.textContaining('From Backend-leaning ·'), findsNWidgets(3));
    expect(find.textContaining('linkedin.com'), findsNWidgets(3));
    await h.dispose(tester);
  });

  testWidgets('delete from the list after confirming', (tester) async {
    final h = Harness();
    h.backend.addRun(role: 'Staff Engineer');
    await h.pump(tester);
    await tapTab(tester, 'History');

    await tester.tap(find.bySemanticsLabel('Delete run'));
    await settle(tester);
    expect(find.textContaining('Delete the run for Staff Engineer?'), findsOneWidget);
    await tapText(tester, 'Delete');
    expect(find.text('Run deleted'), findsOneWidget);
    expect(find.text('No runs yet'), findsOneWidget);
    await h.dispose(tester);
  });
}
