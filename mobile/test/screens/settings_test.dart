import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/harness.dart';

Future<void> openSettings(WidgetTester tester) async {
  await tester.tap(find.byTooltip('Settings'));
  await settle(tester);
}

void main() {
  testWidgets('add a connection from a preset', (tester) async {
    final h = Harness();
    await h.pump(tester);
    await openSettings(tester);

    expect(find.text('No model configured'), findsOneWidget);
    await tapText(tester, 'Add a connection');

    await tapText(tester, 'Groq');
    expect(find.text('https://api.groq.com/openai/v1'), findsOneWidget);
    expect(find.text('llama-3.3-70b-versatile'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField).at(3), 'gsk-secret');
    await tapText(tester, 'Add connection');

    expect(find.text('Profile added'), findsOneWidget);
    expect(find.text('Groq'), findsOneWidget);
    expect(find.text('Default'), findsOneWidget);
    expect(find.text('••••4f2a'), findsOneWidget);
    expect(find.text('0.2 · max 8000 tokens'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('number fields reject out-of-range values without blocking valid ones', (tester) async {
    final h = Harness();
    await h.pump(tester);
    await openSettings(tester);
    await tapText(tester, 'Add a connection');

    await tester.enterText(find.byType(TextFormField).at(0), 'Mine');
    await tester.enterText(find.byType(TextFormField).at(4), '3');
    await tester.enterText(find.byType(TextFormField).at(5), '100');
    await tapText(tester, 'Add connection');
    expect(find.text('Between 0 and 2'), findsOneWidget);
    expect(find.text('Between 256 and 200000'), findsOneWidget);

    // 0.25 and 8000 are exactly the values a step grid once refused on the web form.
    await tester.enterText(find.byType(TextFormField).at(4), '0.25');
    await tester.enterText(find.byType(TextFormField).at(5), '8000');
    await tapText(tester, 'Add connection');
    expect(find.text('Profile added'), findsOneWidget);
    await h.dispose(tester);
  });

  testWidgets('test, edit, make default and delete a connection', (tester) async {
    final h = Harness();
    h.backend
      ..addProfile(name: 'OpenAI')
      ..addProfile(name: 'Local', isDefault: false);
    await h.pump(tester);
    await openSettings(tester);

    await tapText(tester, 'Test');
    expect(find.text('Connected to gpt-4o-mini (42ms)'), findsOneWidget);

    await tester.tap(find.text('Edit').first);
    await settle(tester);
    expect(find.text('Edit connection'), findsOneWidget);
    expect(find.text('Leave blank to keep the key you already saved.'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField).at(0), 'OpenAI (work)');
    await tapText(tester, 'Save changes');
    expect(find.text('Profile updated'), findsOneWidget);
    expect(find.text('OpenAI (work)'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('Make Local the default'));
    await settle(tester);
    expect(h.backend.profiles.last['isDefault'], isTrue);

    await tester.tap(find.bySemanticsLabel('Delete OpenAI (work)'));
    await settle(tester);
    expect(find.text('Profile deleted'), findsOneWidget);
    expect(find.text('OpenAI (work)'), findsNothing);
    await h.dispose(tester);
  });
}
