# Native Side Panel (Editor · Links · Alerts · Feeds): Migration Plan

Implement the web app's side panel (`lifeMappWeb/frontend/src/components/links/*` and `components/feeds/*`) as a
native Compose panel in the Android app, opened from the Today and Monthly pages.

On the web it is **one panel with four tabs**: a segmented toggle `[ Editor | Links | Alerts | Feeds ]` in its header.
Editor, Links and Alerts are the *Links panel* (`LinksPanel` → `LinksContent`, tab in `?linkspanel=`); Feeds is a
separate panel (`FeedsPanel` → `FeedsContent`) that `switchSidePanel` swaps in place, so it reads as one panel.
The Android version should be a single panel with the four tabs, built on the web's **phone layout** (one pane at a
time), not the desktop multi-column one.

## Status (2026-11-20)

Decisions: feeds management is **built natively** (add / edit / delete feeds, folders, OPML import and export); the
panel gets a **widget deep link** (`DeepLink.Panel`, used by the Dashboard widget's LINKS header); the feed reader
uses the **light renderer** (`AnnotatedString.fromHtml`, pictures and embeds dropped).

Implemented on `feat/side-panel`:

| Area | Where |
|---|---|
| Model: reminder fields, tags, `updated_at`; `PlanLimits`; feed DTOs | `core/model/Snapshot.kt`, `core/model/SidePanel.kt` |
| API: domains select, topic/domain updates, plan limits, custom tags, feeds, link titles | `core/data/api/DomainsApi.kt`, `ItemsApi.kt`, `FeedsApi.kt` |
| Repositories: `saveReminder`, `renameTopic`, `saveInfoBox`, `planLimits`, `customTags`; `FeedRepository`; edits now also update `monthlyDomains` | `core/data/repository/` |
| Ports with tests: link extraction, link titles, reminder dates, tags, plan limits, filters | `core/presentation/panel/` |
| Panel UI: host, tabs, tree, structure view, inline editor with autosave, reminder sheet, links, alerts, feeds | `app/ui/sidepanel/` |
| Entry points: hero Feeds/Links, Integrity panel buttons, domain header → Editor, Shortcuts Links header | `TodayHero.kt`, `TodayDomains.kt`, `TodayShortcuts.kt` |

Still open: drag and drop (move up/down instead), heading folds, image upload in the editor, AI feed tags, folder
merge and feed reordering; a screenshot pass against the web panel (needs a signed-in device).

---

## 1. What the Web Panel Contains

### 1.1 Shell (`LinksPanel.tsx`, `FeedsPanel.tsx`, `side-panel-switch.tsx`, `app-store.ts`)

| Behavior | Web | Android |
|---|---|---|
| Container | Fixed left slide-out, `w-[1140px] max-w-[96vw]`, backdrop blur, Escape / backdrop tap closes | Full-screen overlay sliding in from the left over `MainDashboard`; scrim; system Back closes |
| Header | `LinksStreamModeToggle` (4 segments, icon + label) + close X | Same: segmented toggle + close |
| Tab state | `linksPanelMode` mirrored in URL; Back closes the panel, not the tab | `SidePanelState.mode`; Back steps out of a sub-pane first, then closes |
| Last view | `localStorage['lifemapp.links.lastView']`: domain/subdomain/item/tag selection, stream mode, sort mode, landing | Same fields in DataStore (`side_panel_view`) |
| Links ↔ Feeds swap | `switchSidePanel` with a 300–2500 ms skeleton, no slide | Plain content swap (crossfade); a skeleton only while the first feed fetch runs |
| Phone panes | Links: `sidebar` ↔ `stream`; Feeds: `sidebar` ↔ `stream` ↔ `reader` | Same pane stack per tab |
| Business domains | Positions 20–29 hidden from the panel | Same filter |
| Plan limits | `applySubscriptionLimitsToTree` (domain-limits.ts) | Port |

### 1.2 Entry points

| Where (web) | Opens | Android today | Change |
|---|---|---|---|
| Hero buttons row (`hero-section.tsx` 544–568): Feeds, Links, Journal, Budget | Feeds / Links tab | `TodayHero.kt` 242 has Journal + Budget only (web routes) | Add **Feeds** and **Links** buttons → panel |
| Integrity `PanelButtonsSection` (`integrity-domain-with-widgets.tsx` 71–113): Feeds, Links, Journal, Budget, **Items with alerts** | Feeds / Links / Alerts tab | — | Add the button block to the Integrity card on Today and Monthly |
| Domain card header (`domain-card.tsx` 149, `openLinksPanelOnDomain`) | Editor tab scoped to that domain | — | Header action on domain cards → Editor on that domain |
| Quick links "Manage" (`quick-links-section.tsx` 65) | Links tab | `TodayShortcuts.kt` opens `WebRoutes.QUICK_LINKS` | Open the panel's Links tab (landing) instead |
| Widget deep link | — | `DeepLink` (section/item/event/task) | Optional: `DeepLink.Panel(mode)` |

### 1.3 Editor tab (`LinksStream` `streamMode === 'editor'`)

- **Sidebar** (`LinksSidebar.tsx`): domain → subdomain → item tree with link counts, tag chips with counts, a search
  box, "Quick links" landing entry, expand state persisted (`usePersistedIdSet`). Drag & drop reorder/move.
- **No item selected → `LinksStructureView`**: the scoped domain's subdomains and items as rows with a text
  preview; inline rename of a subdomain (`SubdomainHeader`, Enter/blur saves); inline edit of the domain info box
  (`DomainInfoBox`); drag reorder.
- **Item selected → `SubsubdomainInlineEditor`**: name field + TipTap editor, **autosave** (`useAutoSave` +
  `AutoSaveIndicator`), plan char limit, heading folds (`fold-state`), image items, **reminder picker** that saves
  immediately (`reminder_date`, `reminder_recurrence_rule`, `reminder_lead_days`).

### 1.4 Links tab (`streamMode === 'links'`)

- Links come from `extractDomainLinks(domains)` (`lib/links/extract-domain-links.ts`): every `<a href>` and bare
  URL in item HTML, with the headings it sits under, domain/subdomain/item ids and names, tags, `updatedAt`.
- Untitled media links get real titles from `POST /api/links/titles` (`useLinkTitles`, batches of 40).
- Filters: domain / subdomain / item / tag / search (label, URL, host, names, headings); sort `structure | newest |
  alpha`; layout `cards | rows`; heading-level visibility toggles (H1–H6); breadcrumb of the scope.
- Per link: open, edit label/URL (`EditLinkDialog` → `updateLinkInHtml`), remove (`removeLinkFromHtml`), jump to
  the item in the editor.
- "Quick links" landing (`QuickLinksLanding`) shows the quick-links grid.

### 1.5 Alerts tab (`streamMode === 'reminders'`)

- Every item with `reminder_date`, scoped by the same filters; default sort by reminder date.
- Card/row shows status badge (`getReminderStatus`: due / overdue / upcoming / created), formatted date,
  recurrence (`describeReminderRecurrence`), lead days. Tapping an item opens the inline editor (same as Editor).

### 1.6 Feeds tab (`FeedsContent.tsx`, `feed-store.ts`)

- **Sidebar** (`FeedSidebar`): smart views (All / Unread / Starred, default Unread), folder tree (life-domain
  folders + user folders), feeds with unread/today counts, tags, add feed, OPML, refresh feed/folder, reorder.
- **Stream** (`FeedStream`): items list (list / cards / magazine), search, newest/oldest, paging (40, offset),
  mark all read (optionally older than N days).
- **Reader** (`FeedReader`): title, source, date, `content_html` or summary, thumbnail/media, open original,
  read/star toggles. Selecting an item marks it read.

### 1.7 Web API used

| Route | Purpose |
|---|---|
| Supabase `lm_life_domains` + nested subdomains/items | Tree (already read by `DomainsApi.fetchDomains`) |
| `PUT /api/subsubdomains/{id}` | Item name/content/reminder fields (already used by `ItemsApi.updateItem`) |
| `PUT /api/subdomains/{id}`, `PUT /api/domains/{id}` | Subdomain rename, domain info box |
| `PUT /api/subsubdomains/reorder` | Item order (already used) |
| `POST /api/links/titles` | Titles for untitled media links |
| `GET /api/feeds?since=` | Folders + feeds with counts |
| `GET /api/feeds/items?feedId|folderId|tag&unreadOnly&starredOnly&q&sort&limit&offset` | Items page |
| `PUT /api/feeds/items/{id}` `{is_read?, is_starred?}` | Read / star |
| `POST /api/feeds/items/mark-read` | Mark all read |
| `POST /api/feeds/{id}/refresh` | Sync a feed |

All are behind `getSupabaseUserClient()` (middleware sets `x-user-id`); the app already calls
`/api/subsubdomains/*` with `bearerAuth`, so the feeds routes should work the same way. **Verify once** against
`/api/feeds` before Phase 5.

---

## 2. Gap Analysis

| Need | Android today | Work |
|---|---|---|
| Full tree incl. Monthly domains | `DomainsApi.fetchDomains` returns all positions | Reuse; filter 20–29 out |
| Reminder fields, tags, `updated_at` | Not selected | Extend `DomainsApi` select + `TopicItem` / `Topic` model |
| Rich text editing | `RichEditText` + toolbar in `ItemEditor.kt` / `ItemEditorModal.kt` (modal, explicit Done) | Extract an inline variant with autosave |
| Encrypted save | `ItemRepository.save(itemId, name, html, encrypted)` | Reuse; add `saveReminder`, `renameTopic`, `saveInfoBox` |
| Char limit | `ItemsApi.itemCharLimit` | Reuse |
| Link extraction, reminder dates, tags | — | Port to `core:presentation` (plain Kotlin, unit-tested) |
| Feeds | — | New `FeedsApi` + `FeedRepository` + models |
| Panel shell / state | — | New `SidePanel*` files and view model |

---

## 3. Android Architecture

### 3.1 Code structure

```
core/model/
  Feeds.kt                     // NEW: FeedFolder, Feed, FeedItem, SmartView DTOs (mirror types/feeds.types.ts)
  Snapshot.kt                  // TopicItem/Topic += reminderDate, reminderRule, reminderLeadDays, tags, updatedAt
core/data/
  api/DomainsApi.kt            // select += reminder_*, tags, updated_at
  api/ItemsApi.kt              // += updateTopic, updateDomainInfoBox
  api/LinksApi.kt              // NEW: POST /api/links/titles
  api/FeedsApi.kt              // NEW: feeds, items, read/star, mark-read, refresh
  repository/FeedRepository.kt // NEW: paging, filter state, optimistic read/star
  repository/ItemRepository.kt // += saveReminder, renameTopic, saveInfoBox (encrypting like save)
core/presentation/
  links/LinkExtractor.kt       // NEW: port of extract-domain-links.ts (+ updateLinkInHtml / removeLinkFromHtml)
  links/LinkTitles.kt          // NEW: port of knownLinkLabel / canFetchLinkTitle
  links/DomainLimits.kt        // NEW: port of applySubscriptionLimitsToTree
  reminders/ReminderDates.kt   // NEW: port of reminder-dates.ts (status, format, lead days, recurrence text)
  tags/ItemTags.kt             // NEW: port of effectiveItemTags (lib/domains/tags.ts)
  SidePanelPresenter.kt        // NEW: decrypted tree → PanelTreeUi, LinkUi list, AlertUi list, counts
app/ui/sidepanel/
  SidePanelHost.kt             // NEW: overlay, scrim, slide-in, BackHandler, header toggle + close
  SidePanelViewModel.kt        // NEW: open/mode/focus, selection, filters, last view (DataStore)
  PanelTree.kt                 // NEW: domain/subdomain/item tree + tags + search (LinksSidebar)
  StructureView.kt             // NEW: LinksStructureView (rename topic, info box, move up/down)
  InlineItemEditor.kt          // NEW: SubsubdomainInlineEditor (RichEditText, autosave, reminder)
  ReminderPicker.kt            // NEW: date + time, recurrence presets, lead-days presets
  LinksStream.kt               // NEW: links list (cards/rows, sort, heading levels, breadcrumb)
  EditLinkDialog.kt            // NEW
  AlertsStream.kt              // NEW
  FeedsSidebar.kt / FeedsStream.kt / FeedReader.kt   // NEW
app/ui/
  MainDashboard.kt             // hosts SidePanelHost above the pager and floating bars
  TodayHero.kt                 // + Feeds, Links buttons
  TodayDomains.kt              // + Integrity panel buttons (Feeds, Links, Journal, Budget, Items with alerts)
  TodayShortcuts.kt            // "Manage" → panel Links landing
```

### 3.2 Panel state (`SidePanelViewModel`)

```kotlin
enum class PanelMode { EDITOR, LINKS, ALERTS, FEEDS }
enum class LinksSort { STRUCTURE, NEWEST, ALPHA }

data class PanelSelection(
    val domainId: String? = null, val topicId: String? = null, val itemId: String? = null,
    val tag: String? = null, val landing: Boolean = false,
)

data class SidePanelState(
    val open: Boolean = false,
    val mode: PanelMode = PanelMode.LINKS,
    val pane: Pane = Pane.STREAM,          // SIDEBAR | STREAM | READER (feeds only)
    val selection: PanelSelection = PanelSelection(),
    val search: String = "",
    val sort: LinksSort = LinksSort.STRUCTURE,
)

fun open(mode: PanelMode? = null)          // null: resume last saved mode (web: setLinksPanelOpen(true))
fun openOnDomain(domainId: String)         // web: openLinksPanelOnDomain → EDITOR, scope = domain
fun close()
fun back(): Boolean                        // pane back first; false when the panel should close
```

- Rules to port from `LinksContent.tsx`: restore last view before auto-select; first-time fallback picks the first
  domain with links; a missing selection is cleared, and an item that moved is followed to its new
  subdomain/domain; switching to Alerts leaves the landing view.
- The panel reads the same cached tree `TodayViewModel` uses and refreshes it on open (web: `initialize()` on
  open), so edits show on Today/Monthly at once.

### 3.3 Inline editor

- Factor the toolbar + `RichEditText` out of `ItemEditorModal.kt` into a `RichTextEditor` composable shared by the
  modal and the panel, so there is one editor.
- Autosave: debounce ~1.5 s after the last change and on leaving the item / closing the panel; status chip
  (Saving… / Saved / Failed, retry). Load the full text with `ItemRepository.loadText` (the cached content is cut).
- Name edits: blank restores the saved name; same rule as the web.
- Over the char limit: block save, show the counter in red (web `isOverContentLimit`).
- Reminder: `ReminderPicker` bottom sheet → save at once via `PUT /api/subsubdomains/{id}` (optimistic, revert on
  failure). Port the reminder limits (`lib/domains/reminder-limits.ts`). This also covers the "Reminder button"
  open point in `aDevGuide.md`.
- Encrypted accounts: locked device → read-only with the existing unlock prompt.

### 3.4 Feeds

- `FeedRepository` holds `folders`, `feeds`, `items`, `hasMore`, filters (`smartView = UNREAD` default, folder /
  feed / tag, search, sort); drops stale page responses like the web's `latestItemsRequest`.
- Reader renders `content_html` with `AnnotatedString.fromHtml` (as items do today) plus the thumbnail via Coil;
  "Open original" uses a Custom Tab. Opening an item marks it read; swipe or buttons for read / star.
- Feeds are not cached in DataStore in v1 (online only, like the web); show an offline empty state.

### 3.5 Phone UX mapping

- One pane at a time: the stream is the default pane; a "Folders" button opens the tree (web `onOpenFoldersMobile`);
  picking in the tree returns to the stream.
- Drag & drop is replaced by the existing item long-press menu (move up / down) for v1.
- Desktop keyboard shortcuts (j/k/m/s/o) don't apply.

---

## 4. Implementation Phases & Estimates

| # | Phase | Deliverables | Est. |
|---|---|---|---|
| 1 | **Data model** | Extend `DomainsApi` select and `Topic`/`TopicItem` (reminders, tags, updated_at); `ItemsApi.updateTopic`, `updateDomainInfoBox`; `ItemRepository.saveReminder/renameTopic/saveInfoBox`; tests | 1.5 d |
| 2 | **Ports (plain Kotlin)** | `LinkExtractor` (+ update/remove link in HTML), `LinkTitles`, `DomainLimits`, `ReminderDates`, `ItemTags`, `SidePanelPresenter`; JVM tests mirroring the web's `__tests__` | 2.5 d |
| 3 | **Panel shell + entry points** | `SidePanelHost` (slide, scrim, Back), header toggle, `SidePanelViewModel` with last-view persistence; Hero Feeds/Links, Integrity panel buttons, domain header → Editor, Shortcuts "Manage" | 1.5 d |
| 4 | **Editor tab** | `PanelTree`, `StructureView` (rename topic, info box, move), shared `RichTextEditor`, `InlineItemEditor` with autosave + char limit, `ReminderPicker` | 3.5 d |
| 5 | **Links + Alerts tabs** | `LinksStream` (filters, sort, cards/rows, heading levels, breadcrumb, title fetch), `EditLinkDialog`, remove link, quick links landing; `AlertsStream` with status badges | 2.5 d |
| 6 | **Feeds tab** | `FeedsApi`, `FeedRepository`, sidebar (smart views, folders, feeds, counts), stream with paging, reader, read/star/mark-all, refresh feed | 3.0 d |
| 7 | **Styling parity** | Classic + Predator (chamfers, `pd-line`, accent toggle), 7 color schemes light/dark, strings in en/de/es | 1.0 d |
| 8 | **QA** | Compose tests (open/close/Back, tab switch, last-view restore, autosave, alerts filter); side-by-side with the web panel at 390 px | 1.0 d |

**Total estimate:** ~16.5 dev days. Phases 1–2 unblock everything; Feeds (6) only needs Phase 3 and can run in
parallel with 4–5.

---

## 5. Testing Strategy

1. **JVM (core:presentation)**
   - `LinkExtractorTest`: anchors and bare URLs, heading levels, entity decoding, dedupe, `updateLinkInHtml` /
     `removeLinkFromHtml` — fixtures copied from `lib/links/__tests__` so output matches the web.
   - `ReminderDatesTest`: status at boundaries (lead days, due today, overdue), formatting, recurrence text.
   - `SidePanelPresenterTest`: business domains excluded, plan limits applied, tag counts, alerts sorted by date,
     encrypted names decrypted / locked fallback.
2. **core:data**: `FeedsApi` query params and paging; stale-response drop; optimistic read/star rollback;
   `saveReminder` encryption parity with `save`.
3. **Compose (Robolectric)**: entry points open the right tab; Back closes sub-pane then panel; switching to Feeds
   and back keeps the Links selection; autosave fires once after idle and on close; over-limit blocks save.

---

## 6. Out of Scope for v1 (open in web)

- Drag & drop in the tree / structure view (move up/down menu instead).
- Feeds management: add / edit / delete feed, folders CRUD and merge, OPML import/export, AI tags, reorder.
- Heading folds in the editor (`heading-fold-extension.ts`) and image upload in the inline editor.
- Business MAPP domains (20–29), as on the web panel.
- Offline cache of feeds.

## 7. Open Questions

1. Feeds management (add feed, OPML): open the web, or build native in a v2?
2. Should the panel get its own widget deep link (e.g. "Items with alerts" from the Integrity widget)?
3. Feed reader: `AnnotatedString.fromHtml` is light but drops embeds/tables; a sandboxed WebView renders like the
   web. Recommendation: fromHtml for v1.
