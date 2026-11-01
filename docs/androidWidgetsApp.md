# LIFE MAPP Widgets: Android App Plan

A small native Android app whose purpose is to bring LIFE MAPP to the home screen as four interactive widgets. For all full-screen views, detailed management, and editing, the widgets redirect directly to the mobile version of the LIFE MAPP web app (rendered seamlessly via Chrome Custom Tabs or the user's default browser):

| Widget | Mirrors (web) | Default size | Content |
|---|---|---|---|
| **Today (Hero)** | `frontend/src/components/layout/hero-section.tsx` | 4×2 | Greeting, date, weather, Events / Tasks / Mail counters, average domain rating, Journal and Budget buttons, reload |
| **Events** | `components/calendar/calendar-events-card.tsx` (`#section-events`) | 4×3, resizable | Today's and upcoming events from every connected calendar |
| **Tasks** | `components/tasks/tasks-panel.tsx` (`#section-tasks`) | 4×3, resizable | Due and overdue tasks, with an interactive checkbox to complete each one |
| **Shortcuts** | `components/quick-links/quick-links-card.tsx` (`#section-quick-links`) | 4×1 to 4×3 | The user's quick links as an icon grid, each opening its configured URL |

The native Android app itself contains only sign-in, encryption unlock, and widget configuration. All deeper interactions (journaling, budgeting, calendar browsing, task creation, domain scoring) take place in the responsive web app. This eliminates maintaining a duplicate React Native mobile codebase (`lifeMappMob`) for Android.

---

## 0. Retirement / Separation from `lifeMappMob`

With the web app serving as the mobile destination for Android, the React Native mobile app (`lifeMappMob`) is no longer required for Android:

- **Android Mobile App Strategy:** Deprecate or archive the Android build of `lifeMappMob`. The native widgets app (`lifeMappWidgets`) becomes the primary LIFE MAPP Android application.
- **Package name options:**
  - Option A (Recommended for greenfield/clean deploy): `com.kalai73.lifemapp.widgets` ("LIFE MAPP Widgets").
  - Option B (Replacement): Reuse `com.kalai73.lifemapp` ("LIFE MAPP") if replacing an existing Play Store listing.
- **iOS Widget:** The iOS widget inside `lifeMappMob` (via `expo-widgets`) can remain if `lifeMappMob` continues to target iOS only.

---

## 1. Technology stack

| Concern | Choice | Why |
|---|---|---|
| Language | **Kotlin 2.x** | Native, first-class for widgets and modern Android |
| Widgets | **Jetpack Glance** (`androidx.glance:glance-appwidget`, Material 3) | Compose-style API compiling to `RemoteViews`; `LazyColumn` for scrollable lists, `SizeMode.Responsive` for resizing, `ActionCallback` for task completion |
| App UI (sign-in, settings) | **Jetpack Compose + Material 3** | Lightweight, modern UI sharing the same state model as Glance |
| Web Launcher | **Chrome Custom Tabs** (`androidx.browser:browser`) | Opens web views with native transition speed, shared browser cookies/sessions, customizable toolbar colors, and clean return to home screen |
| Background refresh | **WorkManager** (periodic + expedited one-off) | Survives reboots and Doze mode; standard 15–30 min sync cycle |
| Networking | **Ktor client** (OkHttp engine) + **kotlinx.serialization** | Lightweight HTTP client for Supabase REST and the Next.js API |
| Auth | **supabase-kt** (`auth-kt`, `postgrest-kt`) | Official Kotlin SDK for Supabase; handles session persistence and token refreshes |
| Local cache | **DataStore** (Proto or JSON) | High-performance snapshot cache; widgets render exclusively from local cache |
| Secrets | **Android Keystore** + EncryptedSharedPreferences | Encrypts refresh tokens and the decrypted master data key |
| Crypto | `javax.crypto` AES/GCM/NoPadding, PBKDF2WithHmacSHA256 | Native port of `frontend/src/lib/encryption/` |
| Images | Bundled Android Vector Drawables (`.xml`) | Low-memory, crisp vector rendering in RemoteViews; avoids arbitrary image loading in IPC |
| Build | Gradle KTS, version catalog, minSdk **26**, compileSdk **34+** | Glance requires SDK 23+; 26 gives adaptive icons and notification channels |
| Tests | JUnit5, Turbine, MockK, `glance-appwidget-testing`, Robolectric | Comprehensive unit and widget testing |

**Repo:** `C:\zzGjo\2025-LifeMapp\lifeMappWidgets\`  
**Application ID:** `com.kalai73.lifemapp.widgets`  
**Display name:** "LIFE MAPP Widgets"

### Module layout

```
lifeMappWidgets/
  app/                      Compose activities: SignInActivity, UnlockActivity, SettingsActivity, WidgetConfigActivity
  core/model/               Snapshot, Event, Task, Shortcut, Weather, Settings DTOs
  core/data/                Supabase + API clients, repositories, DataStore snapshot cache
  core/crypto/              AES-GCM + PBKDF2 + key envelope (port of web lib/encryption)
  core/designsystem/        Color schemes, Predator & default style tokens, vector drawables
  core/weblauncher/         Chrome Custom Tabs helper for launching web app routes & auth exchange
  widget/                   Glance widgets, receivers, actions, WorkManager workers
```

---

## 2. Data sources & API Endpoints

The web API already supports `Authorization: Bearer <supabase access token>` (`frontend/src/proxy.ts:220`) and injects `x-user-id`. The widgets app calls these same Next.js routes.

### Widget snapshot endpoint (Recommended Web Change)

Calling 6–10 separate routes in a background worker is slow and battery-intensive. The web app should provide a single consolidated snapshot route:

`GET /api/widget/snapshot?tz=Europe/Madrid&daysAhead=7`

**Response payload:**
```jsonc
{
  "generatedAt": "2026-10-31T08:00:00Z",
  "settings": { 
    "username": "…", 
    "colorScheme": "default", 
    "uiStyle": "predator", 
    "language": "en", 
    "dateFormat": "…", 
    "timeFormat": "24h", 
    "encryptionEnabled": true 
  },
  "counts": { 
    "events": 3, 
    "tasks": 5, 
    "mail": 12, 
    "mailConnected": true 
  },
  "events": [ 
    { "id": "…", "title": "…", "start": "…", "end": "…", "allDay": false, "calendarColor": "#…", "source": "google", "htmlLink": "…" } 
  ],
  "tasks": [ 
    { "id": "…", "title": "…", "encryptedTitle": "…", "encryptionMetadata": {…}, "due": "…", "priority": 2, "listColor": "#…", "recurring": true, "source": "local" } 
  ],
  "shortcuts": { 
    "plain": [ … ], 
    "encrypted": "…" 
  },
  "domains": { 
    "averageRating": 7.4, 
    "encryptedRatings": [ … ] 
  },
  "weather": { 
    "tempC": 18, 
    "code": 3, 
    "location": "…" 
  },
  "encryptedCalendarConfigs": "…" // present only when encrypted, so the app can proxy ICS/CalDAV directly
}
```

- **Resilience:** The endpoint aggregates services via `Promise.allSettled()` with strict timeouts (e.g. 2.5s per provider) so slow external APIs (Gmail, Microsoft Graph) do not cause the entire snapshot to fail.
- **End-to-End Encryption holds:** The server never decrypts client data. Encrypted fields pass through as-is, and the widget app decrypts them on device.
- **ICS/CalDAV fallback:** When calendar configs are encrypted, the server cannot read external URLs. The widget app decrypts them locally and queries `/api/calendar-proxy` / `/api/caldav-proxy`.
- **Payload size:** Cap events to 25 items and tasks to 25 items to prevent exceeding the Android Binder IPC transaction limit (1 MB).

### Write actions & Task Completion

| Action | Endpoint |
|---|---|
| Complete task | `PUT /api/tasks/{id}` with `{ "completed": true }` or a dedicated `POST /api/widget/tasks/{id}/complete` |

*Note on Recurrence:* The web backend **already implements** server-side recurrence generation in `frontend/src/app/api/tasks/[taskId]/route.ts` via `generateNextRecurringInstance()`. When a task is marked `completed = true`, the next occurrence is automatically created by the server. No client-side recurrence calculation is required in the Android app.

---

## 3. Authentication, Web Session Handoff & Encryption

### Sign-in
1. Email + password or Google OAuth through `supabase-kt`.
2. Google OAuth opens in Chrome Custom Tabs and redirects via `lifemappwidgets://auth/callback`.
3. Calls `/api/auth/check-whitelist` to enforce beta access.
4. Refresh tokens are stored securely in Android Keystore / EncryptedSharedPreferences. `SnapshotWorker` refreshes the token before sync cycles.

### Web Session Continuity (Widget → Web Handoff)
Because the widget app and the mobile browser do not share storage:
1. When opening the web app from a widget, the app checks if an auth handoff is required.
2. The app obtains a one-time session exchange code/token via a lightweight backend route (or Supabase magic link/auth exchange).
3. The Chrome Custom Tab launches: `https://lifemapp.app/auth/exchange?token=...&redirect=/today#section-tasks`.
4. The web app logs in the user, sets session cookies, and displays the target screen without asking for credentials again.

### Encryption unlock (when `encryption_enabled`)
1. Native port of `lib/encryption/` (`aes.ts`, `key-derivation.ts`, `key-envelope.ts`).
2. Unlock screen prompts for the passphrase, derives the master wrapping key with PBKDF2 (matching iteration count and salt format), unwraps the data key, and validates it against `verifyKeyCheck`.
3. The unwrapped data key is encrypted under an Android Keystore AES key (`setUserAuthenticationRequired(false)`) so background WorkManager jobs can decrypt titles and task names.
4. **Direct Boot safety:** Wrap Keystore reads in safe checks; if the device reboots and has not yet undergone the first user unlock, WorkManager jobs defer until `ACTION_USER_PRESENT` / `LOCKED_BOOT_COMPLETED`.
5. **Parity verification:** Unit tests must import exact JSON test vectors from `frontend/src/lib/encryption/__tests__` and assert byte-for-byte decryption equality.
6. When locked, widgets display item counts while displaying titles as `"🔒 Locked"`.

---

## 4. Widgets in detail

All widgets render strictly from the local DataStore snapshot. Network sync updates DataStore, which triggers `GlanceAppWidget.updateAll()`.

### 4.1 Today (Hero)
- **Layouts (`SizeMode.Responsive`):**
  - **2×1:** The three counters (Events, Tasks, Mail).
  - **4×1:** Greeting and counters bar.
  - **4×2 (Default):** Greeting, "Today is \<date\>", weather (icon + temp), Events / Tasks / Mail counters bar, average domain rating badge, and Journal and Budget quick buttons.
  - **5×2+:** Expands with location name and "Updated hh:mm" timestamp.
- **Taps & Web Redirection (via Chrome Custom Tabs):**
  - **Events counter:** Opens `https://lifemapp.app/today#section-events`.
  - **Tasks counter:** Opens `https://lifemapp.app/today#section-tasks`.
  - **Mail counter:** Opens configured webmail URL (`mail_inbox_urls`) or Gmail / Outlook web.
  - **Journal button:** Opens `https://lifemapp.app/journal`.
  - **Budget button:** Opens `https://lifemapp.app/budget`.
  - **Reload button:** Triggers an expedited background sync with an active rotating spinner.

### 4.2 Events
- Header displays "Events", today's date, and a manual reload button.
- Body is a `LazyColumn` grouped by day (Today, Tomorrow, weekday), showing time / "All day", title, and a colored calendar indicator dot.
- Configurable per instance: 1, 3, or 7 days ahead; calendar filter selection.
- **Taps:**
  - Tapping an event with an `htmlLink` (Google/Microsoft event) opens the web event page.
  - Tapping any other event or the widget header opens `https://lifemapp.app/today#section-events`.
- States: Empty ("No events today"), Not connected ("Connect a calendar in LIFE MAPP"), and Error/Stale.

### 4.3 Tasks
- Header shows "Tasks", remaining count, and a "+" button that opens `https://lifemapp.app/today#section-tasks?action=new-task`.
- Body displays overdue tasks (highlighted with red due text) followed by today's tasks, priority badges, and list color pills.
- Item limit: Hard cap at 25 items in the list to preserve smooth IPC rendering.
- **Checkbox completion flow:**
  1. User checks box.
  2. Optimistic update: Glance marks the item completed and updates the local DataStore immediately.
  3. Enqueues a `CompleteTaskWorker` (`PUT /api/tasks/{id}` with `{ completed: true }`).
  4. Backend automatically creates the next recurrence if applicable.
  5. If the network call fails after retries, the task is restored in DataStore and a failure toast is queued.
- Tapping task text opens `https://lifemapp.app/today#section-tasks`.

### 4.4 Shortcuts
- `LazyVerticalGrid` displaying user quick links with position ordering.
- Tiles show an icon, text label, and color border.
- **Icons:** Common Lucide icons mapped to bundled Android Vector Drawables; unknown icons fall back to the first character on a colored tile. Emoji icons render as native text.
- **Taps:** Dispatches `Intent.ACTION_VIEW` on the destination URL (Android automatically routes external app schemes like WhatsApp, Spotify, or websites directly).

### Theming & Visual Identity
- **Color Schemes:** Maps the 7 web schemes (`default`, `teal`, `ocean`, `sunset`, `forest`, `rose`, `midnight`) to Glance Material 3 color providers with light and dark mode support.
- **Predator Style (`ui_style = 'predator'`, the default; null/unknown values are Predator):** follows lifeMappMob `docs/predator-styleguide.md`.
  - Tokens: warm neutrals and the scheme accent (orange for `default`) with fill / ink / on values, precomputed opaque in `PredatorColors`.
  - Shapes: RemoteViews has no clip-path, so chamfers, parallelograms, the arrow banner and hexagons are tinted white layer-list masks (`pd_*` drawables, generated by `gen_predator_shapes.py`) whose corner pieces keep their dp size at any widget size. A frame is the edge mask plus a 1dp-inset fill mask (`PdFrame`); the widget panel adds accent corners.
  - Type: RemoteViews cannot load Chakra Petch or track letters. Labels use `sans-serif-condensed` bold italic uppercase, captions monospace, body text (user content) the default face.
  - Patterns: hex icon well + accent rule in headers, hex rating, arrow hero banner, chamfered counter strip with leaning dividers, parallelogram buttons/tags/day chips, task rows with a −12° list color bar and square checkbox, hex shortcut tiles. Picker previews use the Predator palette.
- **Default Style:** Rounded cards (24dp) with soft shadows and standard Material typography.
- **Brand copy:** Consistently branded as **"LIFE MAPP"**.

---

## 5. Refresh strategy & Battery Optimization

| Trigger | Mechanism | Details |
|---|---|---|
| Periodic | `PeriodicWorkRequest` (30 min) | Constrained to `NetworkType.CONNECTED`, unique work tag `snapshot-sync` |
| Widget event | Expedited `OneTimeWorkRequest` | Triggered when widget is added, resized, or settings change |
| Manual reload | Expedited one-off | Debounced by 10 seconds |
| Midnight rollover | `AlarmManager.setWindow` | Recomputes "today" relative dates at 00:00 local time, then syncs |
| Device unlock | `ACTION_USER_PRESENT` | Triggered if local cache is older than 15 minutes |

### Battery Optimization & App Standby Mitigation
Because the widget app has minimal foreground activity, Android's power manager can relegate it to the **"RARE"** App Standby bucket, throttling WorkManager.
- Provide a clear setting: *"Optimize background refresh"*, directing the user to disable battery optimizations (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` or device settings).
- Display an "Updated hh:mm" timestamp on widgets; display a subtle stale indicator if data is older than 2 hours.

---

## 6. Implementation Phases & Timeline

| # | Phase | Deliverables | Est. |
|---|---|---|---|
| 0 | Web: Snapshot & Session Handoff APIs | `GET /api/widget/snapshot` (with `Promise.allSettled`), task completion verification, auth handoff token exchange | 2.5 d |
| 1 | Project scaffold & Design System | Gradle KTS, version catalog, CI, Vector Drawables, 7 color schemes, Predator / Default Glance styles | 1.5 d |
| 2 | Auth, Web Launcher & Keystore | `supabase-kt` login, Chrome Custom Tabs launcher, Keystore token persistence, Direct Boot safety | 1.5 d |
| 3 | Crypto Port & Unlock Activity | AES-GCM, PBKDF2 port, Unlock screen, parity unit tests using web fixtures | 2 d |
| 4 | Data Layer & Sync Engine | DataStore cache, repository layer, `SnapshotWorker`, ICS/CalDAV proxy fallback | 2 d |
| 5 | Shortcuts Widget | `LazyVerticalGrid`, Lucide vector mapping, URL intent dispatching | 1 d |
| 6 | Today (Hero) Widget | Responsive layouts (2×1 to 5×2), counters, weather, CCT web links | 2 d |
| 7 | Events Widget | Grouped `LazyColumn`, multi-calendar dot colors, CCT web redirection | 1.5 d |
| 8 | Tasks Widget | Due/overdue list, optimistic checkbox action, `CompleteTaskWorker` | 2 d |
| 9 | Theming polish & i18n | en/es/de translations, Predator font fallback, light/dark parity | 1 d |
| 10 | QA, Battery validation & Release | Physical device testing (Pixel, Samsung, Xiaomi), dumpsys wake lock check, Play Store internal track | 2 d |

**Total Estimate:** ~19 dev days.

---

## 7. Testing & Verification

- **Crypto Parity:** Automated unit tests verifying that Kotlin AES-GCM and PBKDF2 produce identical plaintexts from Web Crypto ciphertexts.
- **Snapshot Resilience:** Mock slow/failing external calendar and mail providers; verify snapshot still returns healthy 200 with partial payloads.
- **Widget States:** Test each widget using `glance-appwidget-testing` across states: Initial / Loading, Empty, Populated, Locked, Error, and Stale.
- **OEM Battery Testing:** Validate WorkManager wakeups under Doze mode and restricted App Standby buckets on Samsung One UI and Xiaomi HyperOS.
- **Web Navigation:** Verify Chrome Custom Tabs smoothly transition into the web app at `#section-events`, `#section-tasks`, `/journal`, and `/budget` without requiring a second sign-in.

---

## 8. Out of scope for v1

- Task creation or detailed editing directly within the widget (always delegates to web).
- Lock-screen widgets and Wear OS companion app.
- Full offline write queue beyond optimistic task completion retry.
- Direct recovery-key unlocking (must unlock via web passphrase first).
- Real-time FCM push notifications (WorkManager polling is sufficient for v1).