import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:resume_tailor/screens/ask_screen.dart';

import '../support/harness.dart';

/// Scoped to the Ask tab: the other tabs stay mounted (and keep their fields) underneath.
final questionField = find.descendant(of: find.byType(AskScreen), matching: find.byType(TextField));

/// The Ask button, not the bottom-navigation label of the same name.
Future<void> tapAsk(WidgetTester tester) async {
  // A frame first, so the button has rebuilt as enabled for the text just entered.
  await tester.pump();
  await tester.tap(find.widgetWithText(TextButton, 'Ask'));
  await settle(tester);
}

void main() {
  testWidgets('no resumes: points at the resume library', (tester) async {
    final h = Harness();
    await h.pump(tester);
    await tapTab(tester, 'Ask');
    expect(find.text('Nothing to ask about yet'), findsOneWidget);
    await tapText(tester, 'Add a resume');
    expect(find.text('Your resumes'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('ask one question, see the answer, then start a new chat', (tester) async {
    final h = Harness();
    h.backend
      ..addResume()
      ..addProfile();
    await h.pump(tester);
    await tapTab(tester, 'Ask');

    expect(find.text('Nothing asked about this resume yet.'), findsOneWidget);
    await tester.enterText(questionField, '  What is my core experience?  ');
    await tapAsk(tester);

    expect(find.text('YOU ASKED'), findsOneWidget);
    expect(find.text('What is my core experience?'), findsNWidgets(2));
    expect(find.text('ANSWER'), findsOneWidget);
    expect(find.text('My core experience lies in backend systems.'), findsNWidgets(2));
    expect(questionField, findsNothing);

    await tapText(tester, 'Ask another question in a new chat');
    expect(questionField, findsOneWidget);
    expect(find.text('YOU ASKED'), findsNothing);
    // The history keeps the question; the new chat starts empty.
    expect(find.text('What is my core experience?'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('asking needs a model', (tester) async {
    final h = Harness();
    h.backend.addResume();
    await h.pump(tester);
    await tapTab(tester, 'Ask');
    expect(find.text('Connect a model first — '), findsOneWidget);
    await tester.enterText(questionField, 'Anything');
    await tester.pump();
    final button = find.widgetWithText(TextButton, 'Ask');
    expect(tester.widget<TextButton>(button).onPressed, isNull);
    await h.dispose(tester);
  });

  testWidgets('deleting the question on screen clears it too', (tester) async {
    final h = Harness();
    h.backend
      ..addResume()
      ..addProfile();
    await h.pump(tester);
    await tapTab(tester, 'Ask');
    await tester.enterText(questionField, 'Summarise me');
    await tapAsk(tester);

    await tester.tap(find.bySemanticsLabel('Delete question'));
    await settle(tester);
    expect(find.textContaining("This can't be undone."), findsOneWidget);
    await tapText(tester, 'Delete');

    expect(find.text('Question deleted'), findsOneWidget);
    expect(find.text('YOU ASKED'), findsNothing);
    expect(find.text('Nothing asked about this resume yet.'), findsOneWidget);
    expect(h.backend.questions, isEmpty);
    await h.dispose(tester);
  });

  testWidgets('history is per resume', (tester) async {
    final h = Harness();
    h.backend
      ..addResume(name: 'One')
      ..addResume(name: 'Two', isDefault: false)
      ..addProfile();
    await h.pump(tester);
    await tapTab(tester, 'Ask');
    await tester.enterText(questionField, 'About One');
    await tapAsk(tester);

    await tester.tap(find.text('One (default)'));
    await settle(tester);
    await tester.tap(find.text('Two').last);
    await settle(tester);
    expect(find.text('About One'), findsNothing);
    expect(find.text('Nothing asked about this resume yet.'), findsOneWidget);
    expect(h.backend.log, contains('GET /api/resumes/r2/questions?page=0&size=50'));
    await h.dispose(tester);
  });
}
