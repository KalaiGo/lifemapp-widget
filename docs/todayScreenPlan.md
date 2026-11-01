# Native Today Screen: Implementation Plan

Bring the content of the web app's Today page (`lifeMappWeb/frontend/src/app/today/page.tsx`) into the Android app as a
native Compose screen. After sign-in it becomes the app's home screen. The current settings content moves to a Settings
screen.

**Scope (decided):** show all Today content natively. These actions are native: complete and undo a task, rate a domain,
expand and collapse sections, open links, mail and events, reload. Deep editing (item editor, image editor, reminders,
task details and creation, quick-link CRUD, calendar / task / weather settings, sharing, score history) opens the web
app at that place through the existing signed-in Custom Tab handoff.

**Data (decided):** the screen reads its content **directly from Supabase** (PostgREST, with the user's access token,
so RLS applies). **No changes to the web app are needed.** Only data that needs server-held secrets comes from
elsewhere: calendar events and mail counts come from the existing widget snapshot, and the weather forecast from the
existing `/api/weather` proxy. Writes that trigger server-side work go through the existing API routes (§2.3).

---

## 1. What the web Today page contains

| # | Web block | Source | Native in v1 |
|---|---|---|---|
| 1 | Greeting "Hello, <name>, today is <date>" | `components/layout/hero-section.tsx` | Yes |
| 2 | Weather card (location, high/low, condition, multi-day forecast); long-press opens settings | `components/weather/weather-card.tsx`, `lib/weather/weather.ts` | Yes; long-press opens web settings |
| 3 | Events / Tasks / Mail counter bar; Events and Tasks scroll to their section, Mail opens the inbox URL | hero-section | Yes (native scroll) |
| 4 | Reload button (events, tasks, mail) | hero-section | Yes (pull-to-refresh as well) |
| 5 | Journal and Budget buttons | hero-section | Open web (`WebRoutes.JOURNAL` / `BUDGET`) |
| 6 | Scoring circles: one per visible life domain (position < 20, not hidden), average rating; tap opens the rating modal (**1–10**, two rows of five), history opens a panel | `components/domains/scoring-circles.tsx`, `domain-rating-modal.tsx` | Circles and rating sheet: yes; history and notes: web |
| 7 | Integrity domain (position 1) with **Warnings** (all items with `status = 'warning'`) and **Reminders** subdomain | `integrity-domain-with-widgets.tsx`, `utils/search-helpers.ts#findWarningItems` | Yes (read); creating a reminder opens web |
| 8 | Calendar Events widget `#section-events` | `domains/integrity-widgets.tsx#CalendarEventsWidget` | Yes |
| 9 | Task list widget `#section-tasks` with complete and undo | `integrity-widgets.tsx#TaskListWidget` | Yes, LIFE MAPP tasks (see §7) |
| 10 | Workspace `#section-workspace`: quick links (2-column grid, sorted by label), mail labels with counts, Google Drive | `domains/workspace-widget.tsx`, `gmail-labels-widget.tsx`, `google-drive-widget.tsx` | Quick links: yes; mail: total + inbox link; Drive: header + "Open" to web |
| 11 | Domain cards for positions 2–4: header (icon, name, rating badge, info box) → subdomains → subsubdomain items (HTML rich text or image, status flag, reminder date) | `domain-card.tsx`, `subdomain-section.tsx`, `subsubdomain-item.tsx` | Yes (read, expand/collapse); edits open web |
| 12 | Tenacity (position 5) with Trayectory (position 10) embedded as a collapsible section | `tenacity-domain-with-trayectory.tsx` | Yes |

Domain filter for Today (`domains-list.tsx`): positions 1–5, minus hidden domains (`isDomainHidden`: positions 1, 5 and 10
and the Integrity / Tenacity / Trayectory names can't be hidden; when `hidden_domain_ids` is null, every other domain is
hidden). Order: by position, with Events, Tasks and Workspace inserted right after Integrity.

---

## 2. Data sources

### 2.1 Read directly from Supabase (PostgREST)

The app already calls Supabase REST with the user's token (`LifeMappApi.keyMaterial` reads `lm_user_settings`). The new
reads follow the same pattern (`apikey` + `Authorization: Bearer`, RLS-scoped, filtered by `user_id`).

| Content | Query |
|---|---|
| Domain tree | `GET /rest/v1/lm_life_domains?select=*,subdomains:lm_life_subdomains(*,subsubdomains:lm_life_subsubdomains(*))&user_id=eq.<id>&order=position` (the same embedded select as `GET /api/domains`) |
| Settings | `lm_user_settings`: `username, color_scheme, ui_style, language, date_format, time_format, timezone, encryption_enabled, preferences, user_settings, weather_settings, quick_links, encrypted_quick_links` |
| Hidden domains | `preferences.hidden_domain_ids`, then legacy `user_settings.calendar_settings.hidden_domain_ids`, then `user_settings.hidden_domain_ids`; null when never set (as in `userSettingsService.getSettings`) |
| Quick links | `quick_links` / `encrypted_quick_links` (decrypted on device, as the Shortcuts widget does) |
| Tasks | `lm_tasks?select=id,title,encrypted_title,due_date,priority,project_id,recurrence_rule,recurrence_parent_id,is_recurrence_template,parent_id,completed,completed_at&user_id=eq.<id>&completed=eq.false` |
| Task lists | `lm_tasklists?select=id,name,encrypted_name,color,created_at&deleted_at=is.null&order=created_at` |
| Weather model | `lm_app_config?constraint_key=eq.weather_model&is_active=eq.true` (optional; falls back to `auto` if RLS hides it) |

Phase 1 starts by checking that RLS allows each of these reads with the user's token. The web client reads the same
tables directly from the browser, so this is expected to pass.

### 2.2 Needs server-held secrets (existing endpoints, unchanged)

| Content | Source | Why not Supabase |
|---|---|---|
| Calendar events + today's count | `GET /api/widget/snapshot` (`events`, `counts.events`, `counts.calendarConnected`), already synced and cached by `SnapshotWorker` | Google / Microsoft events need OAuth refresh tokens and client secrets held by the server |
| Mail total + inbox URL | Snapshot `counts.mail`, `counts.mailConnected`, `mail.inboxUrl` | Gmail / Graph tokens are server-side |
| Weather forecast (multi-day) | `GET /api/weather?location=…&unitGroup=metric\|us&lang=…&model=…`: the public Visual Crossing proxy the web weather card uses, which returns `days[]` with `datetime, tempmax, tempmin, conditions, icon` | The Visual Crossing API key is server-side |

The location label follows `getLocalizedLocationLabel` in `lib/weather/weather.ts` (first part of `resolvedAddress`). The
default location is `Zurich` when `weather_settings.location` is empty.

### 2.3 Writes (existing API routes, unchanged)

| Action | Route | Why not a direct table write |
|---|---|---|
| Complete / undo a task | `PUT /api/tasks/{id}` `{ completed: true \| false }` | The route creates the next recurring instance (`generateNextRecurringInstance`) |
| Rate a domain | `PUT /api/domains/scores/bulk` `{ entries: [{ domainId, rating, encrypted_rating? }] }` | One request updates the ratings, inserts score history and enforces the scoring-runs limit |

Both routes already accept the app's Bearer token: `/api/tasks` and `/api/domains` are on the proxy's
`protectedApiRoutes` list.

Still to verify (no change expected): undo of a completed **recurring** task must not leave the server-created next
instance behind. Check what `handleUndoComplete` in `integrity-widgets.tsx` does, and do the same.

---

## 3. Android architecture

### New module `:feature:today`

```
feature/today/
  TodayScreen.kt            LazyColumn host, pull-to-refresh, scroll-to-section
  TodayViewModel.kt         combines today cache + snapshot cache + encryption key → TodayUiState
  hero/                     Greeting, WeatherCard (+ forecast row), CounterBar, ActionButtons, ScoringCircles, RatingSheet
  domains/                  DomainCard, SubdomainSection, ItemRow, ItemHtml, ItemImage, WarningsSection,
                            RemindersSection, TenacityCard (embeds Trayectory)
  sections/                 EventsSection, TasksSection, WorkspaceSection (QuickLinks, Mail, Drive)
  style/                    Predator and Classic Compose primitives (chamfer shapes, hex, parallelogram, frames)
```

`:app` depends on it. It reuses `:core:data`, `:core:designsystem`, `:core:weblauncher` and `:core:crypto`.

### Data layer additions (`:core:data`, `:core:model`)

| Piece | Purpose |
|---|---|
| `core/model/Today.kt` | `LifeDomain`, `LifeSubdomain`, `LifeItem` (DB column names, all defaulted, encrypted fields kept raw), `TodaySettings`, `TaskRow`, `TaskListRow`, `WeatherForecast` |
| `SupabaseTodayApi` | The PostgREST reads in §2.1, reusing `LifeMappApi`'s `authorized` 401-refresh-retry |
| `LifeMappApi.weather(location, unit, lang, model)` | `GET /api/weather` |
| `LifeMappApi.saveRatings(entries)` / `setTaskCompleted(id, completed)` | §2.3 (the latter generalises `completeTask`) |
| `TodayCacheStore` | Separate DataStore JSON file (`today_cache.json` in `noBackupFilesDir`), holding the tree, settings, tasks, lists and forecast. Encrypted values stay encrypted at rest |
| `TodayRepository` | `refresh()` runs every read in parallel. A failed part keeps its cached copy, the same rule as `SnapshotRepository.merge`. Also: `state: Flow`, optimistic `rate()` with rollback, optimistic task complete/undo |
| `TodayPresenter` (pure Kotlin, unit-tested) | Decryption, Today filter, hidden domains, ordering, section insertion, warnings / reminders lists, average rating, Today task selection, item image parsing, date labels. Mirrors `WidgetPresenter` |

### Decryption (port of `lib/encryption/batch.ts#decryptAllDomains`)

- **Domain:** `encrypted_info_box` is self-contained JSON (`{encrypted, iv, tag}`), falling back to the legacy format
  (raw ciphertext + `encryption_metadata.iv/tag`). `encrypted_name` is used only when `name == "[encrypted]"`.
  `encrypted_rating` is JSON, parsed to an integer.
- **Subdomain `encrypted_name`** and **item `encrypted_name` / `encrypted_content`:** self-contained JSON, else raw
  ciphertext with the row's `encryption_metadata` iv/tag. Item content is decrypted only when it is JSON or the metadata
  has a real iv/tag *and* the ciphertext looks like base64; a failure gives an empty string, as on the web.
- **Tasks / lists / quick links:** `EncryptedField.decrypt`, as in `WidgetPresenter`.
- **Writing an encrypted rating:** `Aes.encrypt(rating.toString())` → JSON `{encrypted, iv, tag}`, with `rating = 0`,
  as in `domain-store.ts#updateDomainRatingsBulk`.

### Today task selection (port of the snapshot's `loadTasks`)

Open tasks that aren't recurrence templates or subtasks, whose local due date is today or earlier, plus undated tasks in
the first (oldest) task list. Sorted overdue first by date, then by time, then by priority (highest first). The
server's 25-item cap doesn't apply on this screen.

### Other ported web logic (each with tests)

- `components/domains/domain-colors.ts#getDomainColors` / `resolveDomainColorName`: custom color > score color
  (`SCORE_TO_COLOR`) > scheme palette by name > by position > scheme accent. The 20 Tailwind families are mapped to
  hex for light and dark (text 700 / 400, banner 500@20% / 900@40%).
- `lib/domains/colors.ts`: `SOFT_SCORE_GRADIENT_COLORS`, `getSegmentGradientColor`, `getScoreProgressiveFill` (for the
  scoring circles' liquid fill).
- `utils/scoring.ts#SCORE_DESCRIPTIONS` (1 Rock Bottom … 10 Flourishing) and the per-position rating questions
  (`domains.json` `info.question.*`).
- `scoring-circles.tsx#isDomainHidden` / `isUnhidableDomain` / `getEffectiveRating`.
- `search-helpers.ts#findWarningItems` / `findReminderItems`.
- `subsubdomain-item.tsx#renderImageContent`: `[IMG:<uri>]` or JSON `{ type: "image", uri }`.
- `lib/domains/reminder-dates.ts`: reminder date labels.

### Rendering details

- **Rich text:** sanitise, then `AnnotatedString.fromHtml` (Compose UI text) with a `LinkInteractionListener` that opens
  links through `Intent.ACTION_VIEW`. Covers paragraphs, bold/italic/underline, lists, links and headings. Unsupported
  constructs (tables, embedded media) fall back to plain text plus an "Open in web" affordance. Long items collapse after
  N lines, honouring `is_collapsed`.
- **Images:** add Coil 3 (`io.coil-kt.coil3:coil-compose`) for URL and data-URI images. Tap opens a full-screen viewer.
  Size-bounded memory cache.
- **Predator style in Compose:** Compose can clip, so chamfers, parallelograms and hexagons are `GenericShape`s, not the
  widgets' layer-list masks. Bundle **Chakra Petch** (OFL) as a `res/font` family for labels. Colors come from
  `PredatorColors.palette`. Classic style uses the existing `LifeMappColors` Material 3 schemes with 24 dp rounded cards.
- **Theme:** `color_scheme` and `ui_style` come from the settings row, with light/dark following the system, as the
  widgets do.

---

## 4. Screen behaviour

- **Navigation:** `Screen.HOME` becomes `TODAY`. A top app bar holds LIFE MAPP, a reload button, "Open in web" and
  Settings, which hosts today's sync, widgets, encryption, battery and account cards unchanged.
- **Loading:** render from the caches immediately. On resume (cache older than 5 minutes), on pull-to-refresh and on
  reload, run `TodayRepository.refresh()` and `SnapshotWorker.syncNow`. Each counter shows a placeholder only until its
  first value arrives, like `useLoadedOnce` on the web. The domain skeleton shows only when nothing is cached.
- **Failure:** a failed part keeps its cached copy. With no cache at all, show the web's "Failed to load" + Retry.
- **Locked encryption:** titles, names and contents render as "🔒 Locked" with a banner linking to the Unlock screen. The
  rating sheet is disabled while locked.
- **Counters:** Events and Tasks scroll the `LazyColumn` to `section-events` / `section-tasks`. The tasks count is
  computed on device from the Supabase task rows. Mail opens `mail.inboxUrl`, else `WebRoutes.MAIL`.
- **Task complete / undo:** optimistic. Complete through `CompleteTaskWorker` (the widget's path, so the screen and the
  widgets agree), then show a Snackbar with "Undo" that calls `setTaskCompleted(id, false)`. Tapping a task opens
  `/today#section-tasks` in web; "+" opens `WebRoutes.NEW_TASK`.
- **Rating:** tap a scoring circle or a domain's rating badge to open a bottom sheet with 1–10 (two rows), the score
  description and the domain question. Selecting a score saves immediately, sending every sibling life domain in the
  same bulk request as the web does. "Add note" and "History" open web.
- **Edit taps:** item long-press or overflow → "Edit in LIFE MAPP" → `/today/<subdomainId>/<itemId>`, which reveals the
  item (`useItemDeepLink`). Subdomain / domain overflow → `/today/<subdomainId>`. Weather long-press →
  `WebRoutes.SETTINGS`.
- **Expand / collapse state:** kept locally in DataStore preferences; not written back to the server in v1.
- **Widget integration:** add deep links into the screen (`section=events|tasks`) on `MainActivity`, and a setting
  "Widget taps open: App / Web" (default App) for the Today widget's counters and the list widgets' headers.
- **Midnight rollover:** the existing alarm triggers a sync. The screen also recomputes "today" when it resumes after
  local midnight.

---

## 5. Phases

| # | Phase | Deliverables | Est. |
|---|---|---|---|
| 1 | Supabase data layer | RLS check for every read in §2.1; `Today.kt` DTOs; `SupabaseTodayApi`, weather, ratings and task-undo calls; `TodayCacheStore`; `TodayRepository` (parallel refresh with per-part fallback, optimistic rating and task changes); tests with recorded JSON fixtures (plain and encrypted accounts) | 2.5 d |
| 2 | `TodayPresenter` + ported logic | Decryption (all legacy formats), filter, hidden domains, ordering, warnings, reminders, Today task selection, colors, score colors and descriptions, image parsing, date labels; unit tests matching the web's | 2.5 d |
| 3 | Screen shell + navigation | `:feature:today` module, Today/Settings split, top bar, pull-to-refresh, scroll-to-section, loading / error / locked states | 1.5 d |
| 4 | Hero | Greeting, date (user date format), weather card + multi-day forecast, counter bar, reload, Journal / Budget, scoring circles, rating sheet (1–10) | 2.5 d |
| 5 | Events + Tasks sections | Grouped events (Today / Tomorrow / weekday, all-day, calendar dot, tap → `htmlLink` or web); tasks (overdue first, priority, list color, recurring icon, complete + undo Snackbar) | 2 d |
| 6 | Domain cards | Domain header, info box, subdomains, items with HTML and images (Coil), status flags, reminder chips, Integrity (Warnings + Reminders), Tenacity + Trayectory | 4 d |
| 7 | Workspace | Quick-link grid (Lucide icons / emoji / letter fallback), mail total + inbox link, Drive header → web | 1 d |
| 8 | Predator + Classic styling | Compose shape primitives, Chakra Petch, all 7 schemes, light/dark; screenshot comparison against the web at 390 px width | 2.5 d |
| 9 | i18n + accessibility | en / es / de strings ported from `locales/*/today.json` and `domains.json`, content descriptions matching the web's aria labels, 48 dp touch targets, font scaling | 1 d |
| 10 | Widget hand-off + QA | Widget deep links into the screen, "open in" setting, large-account performance (100+ items, images), offline, locked, token expiry | 1.5 d |

**Total:** about 21 dev days. No web work.

---

## 6. Testing

- **Unit (JVM):** `TodayPresenter` against fixtures exported from a real account (plain and encrypted, including legacy
  metadata-format rows), covering hidden domains (null vs list), ordering, warnings, reminders, average rating with
  locked encryption, Today task selection across midnight, and image parsing. `TodayRepository`: a failed part keeps its
  cache, and a rating rolls back on a 500. Rating encryption round-trip with `WebParityTest` vectors.
- **Compose UI tests (Robolectric):** each section's empty, populated, locked and error states; the counter tap scrolls
  to its section; task complete → Undo restores the row; the rating sheet sends a bulk request with every sibling.
- **Screenshot tests:** Roborazzi or Paparazzi for Predator × Classic × light/dark on the default scheme, plus one other
  scheme.
- **Manual:** compare side by side with the web Today page on a phone-width browser for the same account. Check that
  every "Edit in LIFE MAPP" link lands on the right item, already signed in.

---

## 7. Known gaps / later

- **External task managers** (Todoist, Google Tasks, Microsoft To Do, TickTick): the web Tasks widget merges them, but
  their tasks aren't in Supabase (they come from the providers' APIs with server-held tokens). v1 lists LIFE MAPP tasks.
- **Per-label mail counts** in the Workspace mail section: the snapshot carries only the total. Adding the labels would
  need a snapshot field (a web change), so it's left out of v1.
- **Opening the item editor directly** from "Edit in LIFE MAPP" would need a web change (an `?edit=1` flag); v1 opens the
  page at the item.
- **Native editing** of items, subdomains, reminders, tasks and quick links. This would be the next scope step after v1.
- **Score history panel, rating notes, domain sharing, PDF export, quick-note button, search:** web only in v1.
- **Server-side `is_collapsed` sync.**
