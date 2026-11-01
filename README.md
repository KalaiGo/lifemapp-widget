# LIFE MAPP Widgets (Android)

One home-screen widget for LIFE MAPP, the **Dashboard**: the hero (greeting, date, weather, counters, Journal /
Budget) fixed at the top, then events, tasks and shortcuts in one scrolling list. The app itself only
handles sign-in, encryption unlock and widget settings; everything else opens the LIFE MAPP web app in a Chrome
Custom Tab, already signed in. Plan: [docs/androidWidgetsApp.md](docs/androidWidgetsApp.md).

- Application ID `com.kalai73.lifemapp.widgets`, minSdk 26, compile/target SDK 36
- Kotlin 2.2, Jetpack Glance 1.1, Compose Material 3, WorkManager, Ktor, DataStore

## Modules

| Module | Contents |
|---|---|
| `app` | `MainActivity` (sign-in → unlock → settings, Google OAuth redirect), `WidgetConfigActivity` (Dashboard events: days / calendars) |
| `core:model` | Snapshot DTOs (mirror of `lifeMappWeb/frontend/src/lib/widget/types.ts`), cache state |
| `core:crypto` | AES-256-GCM, PBKDF2-SHA256 (100k), key envelope — port of the web's `lib/encryption` |
| `core:data` | Supabase auth (password, Google PKCE, refresh), web API client, Keystore secret store, DataStore cache, repositories, `WidgetPresenter` |
| `core:designsystem` | The 7 web color schemes, Classic / Predator styles, Lucide icons as vector drawables |
| `core:weblauncher` | Custom Tabs launcher with the one-time web sign-in handoff |
| `widget` | Glance Dashboard widget, actions, `SnapshotWorker`, `CompleteTaskWorker`, scheduling |

## Setup

`local.properties` (not committed):

```properties
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
lifemapp.supabaseUrl=https://<project>.supabase.co
lifemapp.supabaseAnonKey=<anon key, as in the web app's NEXT_PUBLIC_SUPABASE_ANON_KEY>
lifemapp.webUrl=https://life-mapp.click
```

CI reads the same values from the `LIFEMAPP_SUPABASE_URL`, `LIFEMAPP_SUPABASE_ANON_KEY` and `LIFEMAPP_WEB_URL`
repository variables.

**Google sign-in** needs `lifemappwidgets://auth/callback` in the Supabase project's
*Authentication → URL Configuration → Redirect URLs*.

## Build and test

```sh
./gradlew test                 # crypto parity, data layer, widget rendering (Robolectric + glance-appwidget-testing)
./gradlew lintDebug
./gradlew :app:assembleDebug   # or :app:installDebug with a device attached
```

The crypto parity fixture (`core/crypto/src/test/resources/android-parity.json`) is produced by the web app's own
Web Crypto code; see `lifeMappWeb/frontend/src/lib/encryption/__tests__/android-parity.test.ts` to regenerate it.

## Web API used

| Route | Purpose |
|---|---|
| `GET /api/widget/snapshot?tz=…&daysAhead=…` | Everything the widgets show, one request; failed sections are listed and kept from cache |
| `POST /api/widget/session-handoff` | One-time magic-link token → `/auth/exchange` signs the browser in |
| `PUT /api/tasks/{id}` `{completed:true}` | Task checkbox; the server creates the next recurring instance |
| `POST /api/auth/check-whitelist` | Beta access check at sign-in |
| Supabase REST `lm_user_settings` | Passphrase salt and key envelope, for the encryption unlock |

## Notes

- Encrypted fields stay encrypted in the cache and are decrypted at render time with the data key, which is
  stored encrypted under an Android Keystore key (no user authentication, so background syncs can use it).
- Nothing is backed up (`allowBackup=false`, data-extraction rules exclude everything).
- Device unlock refresh (`ACTION_USER_PRESENT`) only works while the process is alive: Android 8+ doesn't deliver it to
  manifest receivers.
- v1 lists and completes LIFE MAPP tasks only; tasks from Todoist / Google Tasks / Microsoft To Do / TickTick aren't in the
  snapshot yet.
