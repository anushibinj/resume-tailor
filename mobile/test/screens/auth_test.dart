import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/harness.dart';

void main() {
  testWidgets('signed out: shows the sign-in gate, and Google sign-in opens the app', (tester) async {
    final h = Harness(signedIn: false);
    await h.pump(tester);

    expect(find.text('Sign in with Google to continue.'), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);

    await tapText(tester, 'Sign in with Google');
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(h.tokens.token, 'app-token');
    expect(h.backend.log, contains('POST /api/auth/google'));
    await h.dispose(tester);
  });

  testWidgets('cancelling the Google picker is not an error', (tester) async {
    final h = Harness(signedIn: false)..google.cancel = true;
    await h.pump(tester);
    await tapText(tester, 'Sign in with Google');
    expect(find.text('Sign in with Google to continue.'), findsOneWidget);
    expect(find.textContaining("Couldn't sign you in"), findsNothing);
    await h.dispose(tester);
  });

  testWidgets('a stored token restores the session via /api/auth/me', (tester) async {
    final h = Harness();
    await h.pump(tester);
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(h.backend.log.first, 'GET /api/auth/me');
    await h.dispose(tester);
  });

  testWidgets('a 401 anywhere drops the token and returns to sign-in', (tester) async {
    final h = Harness();
    await h.pump(tester);
    h.backend.expireSession = true;
    await tapTab(tester, 'Resumes');
    // Pull to refresh: the refresh comes back 401.
    await tester.drag(find.text('Your resumes'), const Offset(0, 2500));
    await settle(tester, frames: 20);
    expect(find.text('Sign in with Google to continue.'), findsOneWidget);
    expect(h.tokens.token, isNull);
    await h.dispose(tester);
  });

  testWidgets('sign out from the account menu', (tester) async {
    final h = Harness();
    await h.pump(tester);
    await tester.tap(find.byType(CircleAvatar));
    await settle(tester);
    expect(find.text('ada@example.com'), findsOneWidget);
    await tapText(tester, 'Sign out');
    expect(find.text('Sign in with Google to continue.'), findsOneWidget);
    expect(h.tokens.token, isNull);
    expect(h.google.signOuts, 1);
    await h.dispose(tester);
  });

  testWidgets('theme toggle flips between light and dark', (tester) async {
    final h = Harness();
    await h.pump(tester);
    final before = Theme.of(tester.element(find.byType(NavigationBar))).brightness;
    await tester.tap(find.byTooltip(before == Brightness.dark ? 'Switch to light theme' : 'Switch to dark theme'));
    await settle(tester, frames: 5);
    final after = Theme.of(tester.element(find.byType(NavigationBar))).brightness;
    expect(after, isNot(before));
    await h.dispose(tester);
  });
}
