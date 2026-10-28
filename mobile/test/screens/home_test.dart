import 'package:flutter_test/flutter_test.dart';

import '../support/harness.dart';

void main() {
  testWidgets('new account: setup steps instead of the tailor button', (tester) async {
    final h = Harness();
    await h.pump(tester);
    expect(find.text('One resume. A different emphasis for every job.'), findsOneWidget);
    expect(find.text('Connect your model'), findsOneWidget);
    expect(find.text('Add your resume'), findsOneWidget);
    expect(find.text('Tailor to a posting'), findsNothing);
    expect(find.text('Nothing yet.'), findsOneWidget);

    await tapText(tester, 'Connect your model');
    expect(find.text('Your model'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('ready account: tailor button and recent runs with scores', (tester) async {
    final h = Harness();
    h.backend
      ..addResume()
      ..addProfile()
      ..addRun()
      ..addRun(status: 'FAILED', role: 'Staff Engineer');
    await h.pump(tester);

    expect(find.text('Connect your model'), findsNothing);
    expect(find.text('Tailor to a posting'), findsOneWidget);
    expect(find.textContaining('Senior Backend Engineer'), findsOneWidget);
    expect(find.text('Failed'), findsOneWidget);
    expect(find.text('See all'), findsOneWidget);
    expect(h.backend.log, contains('GET /api/runs?page=0&size=5'));

    await tapText(tester, 'See all');
    expect(find.text('History'), findsWidgets);
    expect(h.backend.log, contains('GET /api/runs?page=0&size=50'));
    await h.dispose(tester);
  });
}
