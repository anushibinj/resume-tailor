# Resume Tailor — mobile

A Flutter client for Android and iOS with the same features as the web frontend in
`../frontend`: Google sign-in, the resume library, tailoring a resume to a posting, the
review screen (section diff, requirements and one-tap additions, summary length, final
source, export, saved PDFs, retry), asking about a resume, run history and model settings.

It talks to the same Spring Boot backend, directly, with the same JSON API. The backend
needed no changes.

## Running it

Prerequisites: Flutter (stable, Dart 3.13+), Android Studio or the Android SDK for
Android, Xcode for iOS, and the backend running (see the root `README.md`).

```bash
cd mobile
cp .env.example .env          # then fill in the values, see below
flutter pub get
flutter run --dart-define-from-file=.env
```

`.env` is read at **build** time (`--dart-define-from-file`) — a phone has no file to read
at runtime, the same way Next.js inlines `NEXT_PUBLIC_*` values into the web bundle.

| Variable | What it is |
|---|---|
| `API_BASE_URL` | The backend as the phone sees it. Blank means `http://10.0.2.2:8080` on the Android emulator (the host machine's `localhost`) and `http://localhost:8080` elsewhere. A physical device needs the computer's LAN address, e.g. `http://192.168.1.20:8080`. |
| `GOOGLE_SERVER_CLIENT_ID` | The backend's `GOOGLE_CLIENT_ID` — the **Web** OAuth client ID, same value. |
| `GOOGLE_IOS_CLIENT_ID` | iOS only: an **iOS** OAuth client ID from the same Google Cloud project. |

Plain `http` is allowed for development: `usesCleartextTraffic` on Android and
`NSAllowsLocalNetworking` on iOS. Serve the backend over HTTPS for anything else.

## Google Sign-In

The phone uses the platform's own account picker (Credential Manager on Android, the
Google Sign-In SDK on iOS) and sends the resulting ID token to `POST /api/auth/google`,
exactly like the web client. `GOOGLE_SERVER_CLIENT_ID` is passed as `serverClientId`, which
makes the token's audience the Web client ID — the one `GoogleTokenVerifier` already
checks. So no backend change, and no second audience to configure.

In the same Google Cloud project as the Web client:

- **Android:** create an OAuth client ID of type *Android* with package name
  `com.resumetailor.resume_tailor` and the SHA-1 of your signing key
  (`cd android && ./gradlew signingReport` shows the debug key's). Nothing from it goes
  into the app — Google matches the app by package and signature.
- **iOS:** create an OAuth client ID of type *iOS* with bundle ID
  `com.resumetailor.resumeTailor`. Put its client ID in `GOOGLE_IOS_CLIENT_ID`, and add its
  *reversed* client ID (`com.googleusercontent.apps.…`) as a URL scheme in
  `ios/Runner/Info.plist` under `CFBundleURLTypes`, which the Google SDK needs to return to
  the app.

The app's session token is kept in the platform keystore (`flutter_secure_storage`), so
reopening the app restores the session; any 401 drops it and returns to sign-in.

## How it maps to the web app

| Web | Mobile |
|---|---|
| Header nav: Tailor · Resumes · Ask · History | Bottom navigation, plus Home |
| Settings link, theme toggle, account menu | App-bar gear, theme toggle, avatar menu |
| Side-panel forms (add/edit resume, connection) | Their own screens |
| Run page's requirements sidebar | A *Requirements* tab beside Review edits / Final source / Job description |
| Hover for evidence and skill descriptions | Tap |
| Browser download of `.tex` / `.md` / PDF | The system share sheet (save to Files / Drive, open in a viewer) |
| "Saved PDFs" panel in the run page's sidebar | The same panel above the tabs; *Upload PDF* opens the system file picker |
| Retry / Delete buttons in the run page header | Circular-arrow and bin icons in the run screen's app bar |
| `window.confirm` | A confirmation dialog, same wording |
| TanStack Query cache + polling | Riverpod providers; a run polls every 2s while any work is outstanding |

The design tokens (`lib/theme/app_theme.dart`) are the CSS custom properties from
`frontend/src/app/globals.css`, light and dark, and the fonts are the same three:
Instrument Sans, Newsreader for resume content, IBM Plex Mono for LaTeX source.

## Code layout

```
lib/
  api/      DTOs (mirroring frontend/src/lib/types.ts), ApiClient, token storage
  state/    Riverpod providers: auth, theme, data, the polling run notifier, Google auth
  screens/  one file per web route, plus the shell and shared screen pieces
  widgets/  UI primitives (ui.tsx) and the feature components
  util/     word diff, URL and date helpers, file sharing
test/
  support/  an in-memory fake of the backend API and a widget-test harness
  screens/  every feature driven end to end against the fake
  util/     unit tests, including url.test.ts ported one for one
```

## Checks

```bash
cd mobile && flutter analyze && flutter test
```

The widget tests need no backend and no device: `test/support/fake_backend.dart` answers
every endpoint the app calls with the backend's JSON shapes, and advances runs through
PENDING → RUNNING → COMPLETED as they are polled.
