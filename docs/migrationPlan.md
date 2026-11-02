# Native Monthly Screen: Migration Plan

Implement the web app's Monthly page (`lifeMappWeb/frontend/src/app/monthly/page.tsx`) as a native Compose screen
(`MonthlyScreen.kt`) in the Android app (`lifeMappWdgt`).

The Android app already implements the native `TodayScreen.kt`. The Monthly screen will become its sister screen,
hosted alongside it with seamless top-bar tab switching (`TODAY` | `MONTHLY`) and horizontal swipe navigation,
mirroring the web app's navigation (`home-header.tsx` and `useSwipeNavigation.ts`).

---

## 1. What the Web Monthly Screen Contains

The web Monthly page shares the Hero section structure with Today, but introduces monthly-specific scoring circles,
different domain filtering, embedded domain inversion, and omits the daily inline widgets:

| # | Block | Monthly Behavior | Web Source Component | Android Status |
|---|---|---|---|---|
| 1 | **Greeting** | "Hello, <name>, today is <date>" | `components/layout/hero-section.tsx` | Shared with Today |
| 2 | **Weather Card** | Location, high/low, conditions, multi-day forecast; long-press opens web settings | `components/weather/weather-card.tsx` | Shared with Today |
| 3 | **Counter Bar** | Events, Tasks, Mail counters. **Interaction difference:** Tapping Events or Tasks switches to the **Today** tab and scrolls to `#section-events` / `#section-tasks`. Mail opens the inbox URL | `hero-section.tsx#scrollToSection` | Needs cross-tab switch & scroll |
| 4 | **Reload Button** | Refreshes events, tasks, mail | `hero-section.tsx` | Shared with Today |
| 5 | **Action Buttons** | Journal and Budget buttons (open web routes) | `hero-section.tsx` | Shared with Today |
| 6 | **Scoring Circles** | Displays circles for **Monthly domains (positions 10–19)**. Tap opens 1–10 rating sheet | `components/domains/scoring-circles.tsx` | **New for Monthly** (§3.3) |
| 7 | **Average Rating** | Shared average across all life domains (positions < 20) | `scoring-circles.tsx#averageRating` | Shared calculation |
| 8 | **Integrity Domain (pos 1)** | Rendered first: Warnings collapsible section + Reminders subdomain with Reception target (Record / Create reminder) | `integrity-domain-with-widgets.tsx` | Reused from Today |
| 9 | **Inline Widgets** | Calendar Events, Tasks list, and Workspace widgets are **omitted** on Monthly | `domains-list.tsx` (`viewMode === 'monthly'`) | **Omitted** |
| 10 | **Trayectory Domain (pos 10)** | Rendered as a full domain card; embeds **Tenacity (pos 5)** as a collapsible section in `preActionContent` using Tenacity's color palette | `trayectory-domain-with-widgets.tsx` | **New for Monthly** (§3.4) |
| 11 | **Monthly Domains (pos 11–19)** | Standard domain cards (Header, rating badge, info box, subdomains, rich text & image items) | `domain-card.tsx` | **New for Monthly** (§3.5) |

---

## 2. Web vs Android Gap Analysis

| Feature | In Android Today | Required for Android Monthly |
|---|---|---|
| **Domain Scope** | Positions 1–5 (`integrity`, `todayDomains`) | Positions 1 (Integrity) + 10–19 (Monthly domains) |
| **Embedded Inversion** | Tenacity (5) embeds Trayectory (10) | Trayectory (10) embeds Tenacity (5) |
| **Scoring Circles** | 5 circles in a single row | 10 circles: `[3, 3, 4]` (Classic) or `[5, 5]` zigzag (Predator) |
| **Widgets in List** | Events, Tasks, Workspace inserted after Integrity | None (clean domain-only feed) |
| **Navigation** | Today is the only home screen | Pager/tabs hosting `TODAY` and `MONTHLY` with horizontal swipe |
| **Counter Taps** | Local scroll to `#section-events` / `#section-tasks` | Switch to Today tab + scroll to target section |

---

## 3. Detailed Specifications

### 3.1 Domain Filtering & Visibility Logic (`domains-list.tsx`, `scoring-circles.tsx`)

- **Domain Filter:** `(domain.position >= 10 && domain.position <= 19) || domain.position === 1`.
- **Order:** Sorted by `position` ascending:
  1. `01 Integrity`
  2. `10 Trayectory` (with embedded `05 Tenacity`)
  3. `11` through `19` (remaining monthly domains, e.g. Community, Finances, Career, etc.)
- **Hidden Domains Rule (`isDomainHidden`):**
  - Positions 1 (Integrity), 5 (Tenacity), and 10 (Trayectory) can **never** be hidden (`isUnhidableDomain`).
  - When `preferences.hidden_domain_ids` is null, unhidable domains are shown and non-permanent domains default to hidden.
  - When `preferences.hidden_domain_ids` contains domain IDs, those matching domains are filtered out.
  - Deduped by ID and position.

### 3.2 Navigation & Gestures

- **Top Bar Segmented Toggle:**
  - Placed in the top bar center (matching `home-header.tsx`):
    - `[ TODAY | MONTHLY ]`
  - Active tab is highlighted with the scheme accent color; inactive tab is muted.
- **Swipe Navigation:**
  - Built with Compose `HorizontalPager(state = pagerState)`.
  - Swiping left moves from Today to Monthly; swiping right moves from Monthly to Today (matching web `useSwipeNavigation`).
  - Synced two-way with the top-bar segmented toggle.
- **Cross-Tab Counter Jumps:**
  - When viewing the Monthly screen, tapping the **Events** or **Tasks** counter in the Hero section:
    1. Animates `pagerState.animateScrollToPage(0)` (Today tab).
    2. Dispatches a scroll event to Today's `LazyListState` to scroll to `section-events` or `section-tasks`.
  - Tapping **Mail** directly opens the user's inbox URL (or `WebRoutes.MAIL`), exactly as on Today.
- **Web Handoff:**
  - Tapping "Open in web" on the Monthly tab opens `WebRoutes.MONTHLY` (`/monthly`).
  - Long-press / overflow on an item opens `/monthly/<subdomainId>/<itemId>`.

### 3.3 Monthly Scoring Circles Layout (`scoring-circles.tsx`)

On Monthly, the scoring area renders circles for domains with `position >= 10 && position <= 19`:
- **Small Screens (Mobile):**
  - **Classic / Material 3 Style:**
    - Pyramid layout split into 3 rows: `[3, 3, 4]`.
    - Row widths: Row 1 = 90%, Row 2 = 90%, Row 3 = 100% (the 3-circle rows spread wider for visual balance).
  - **Predator Style:**
    - 2 rows of 5: `[5, 5]`.
    - Alternating items use a **zigzag vertical offset** so that the domain name labels above the hexagons do not collide.
- **Desktop / Wide Layout:**
  - Centered row / flex layout within `max-w-[1596px]`.
- **Interaction:**
  - Tapping a circle opens the existing 1–10 rating bottom sheet (`RatingModal`) for that monthly domain, with the score question (`info.question.*`), score description, and liquid fill animation.
  - Ratings save immediately via `PUT /api/domains/scores/bulk`.
  - Eye button ("Hide/Show all") toggles visibility for hidable monthly domains (11–19), exempting Trayectory (10).

### 3.4 Embedded Domain Inversion: Trayectory (10) & Tenacity (5)

On the Today screen, Tenacity (5) embeds Trayectory (10). On the Monthly screen, the relationship is inverted:
- **Trayectory Card (pos 10):**
  - Renders Trayectory header (hex icon, title, rating badge, info box).
  - Contains a **Tenacity Collapsible Section** in `preActionContent` (above the subdomains).
  - The embedded Tenacity header:
    - Displays `05` and `TENACITY` (or localized name).
    - Uses **Tenacity's own color palette** (banner gradient, border, and text).
    - Has an independent expand/collapse chevron.
    - When expanded, renders Tenacity's subdomains and items.
  - Below the embedded Tenacity section, Trayectory renders its own subdomains and items.

### 3.5 Monthly Domain Cards (positions 11–19)

- Standard cards rendered using the existing `DomainCard` / topic / item components:
  - Header with chamfer/rounded shape, position badge (e.g. `11`), title, rating circle/badge, and info box.
  - Subdomain topics with expand/collapse chevron and topic number.
  - Items with rich text (`AnnotatedString.fromHtml`), Coil images, warning status flags, and reminder chips.
  - Local expand/collapse state persisted per domain and topic.

---

## 4. Data Architecture

### 4.1 Supabase Data Access

The Monthly screen needs full domain data for positions 10–19 (and position 5 for the embed). This data is obtained
via direct PostgREST calls with the user's Bearer token:

```
GET /rest/v1/lm_life_domains?select=*,subdomains:lm_life_subdomains(*,subsubdomains:lm_life_subsubdomains(*))&user_id=eq.<userId>&order=position
```

- **Zero Web Backend Changes:** This single query already returns all user domains (1–29).
- **Unified Cache:** The existing DataStore domain cache (`today_cache.json` or unified `domains_cache.json`) holds all
  domains. Today consumes positions 1–5; Monthly consumes positions 1 + 10–19.
- **Decryption:** Monthly domain info boxes, subdomain names, item names, and item content decrypt on device using the
  same AES-GCM data key (`secrets.dataKey`) already used for Today.

### 4.2 Snapshot Fallback & Background Sync

- Counters (Events, Tasks, Mail) and Weather forecast continue to come from `SnapshotRepository` (`GET /api/widget/snapshot`
  and `GET /api/weather`), which is already cached in DataStore.
- `AppViewModel.reload()` refreshes both the snapshot and Supabase domain data concurrently.

---

## 5. Implementation Architecture in Android

### 5.1 Proposed Code Structure

```
app/src/main/kotlin/com/kalai73/lifemapp/widgets/
  ui/
    MainDashboard.kt            // Pager host (HorizontalPager), top-bar segmented toggle (TODAY | MONTHLY)
    TodayScreen.kt              // Existing Today screen (now page 0 of pager)
    MonthlyScreen.kt            // NEW: Monthly screen composable (page 1 of pager)
    MonthlyScoringSection.kt    // NEW: 10-domain scoring circles ([3, 3, 4] and [5, 5] zigzag)
    TrayectoryDomainCard.kt     // NEW: Trayectory card embedding collapsible Tenacity section
    AppViewModel.kt             // Holds _monthly StateFlow, activeTab, and crossTabScroll events
core/data/
  presentation/
    MonthlyPresenter.kt         // NEW: Transforms cached domains into MonthlyUiModel
    MonthlyUi.kt                // NEW: UI model for Monthly screen
```

### 5.2 Presenter Logic (`MonthlyPresenter.kt`)

```kotlin
object MonthlyPresenter {
    fun present(
        domains: List<LifeDomain>,
        hiddenIds: Set<String>?,
        ratings: Map<String, Int>,
        key: ByteArray?,
    ): MonthlyUi {
        // 1. Separate domains
        val integrity = domains.firstOrNull { it.position == 1 }
        val tenacity = domains.firstOrNull { it.position == 5 }
        val trayectory = domains.firstOrNull { it.position == 10 }
        val otherMonthly = domains.filter { it.position in 11..19 }

        // 2. Filter hidden (1 and 10 cannot be hidden)
        val visibleMonthly = otherMonthly.filterNot { isDomainHidden(it, hiddenIds) }

        // 3. Chunk scoring circles (10..19)
        val scoringDomains = (listOfNotNull(trayectory) + otherMonthly)
            .filterNot { isDomainHidden(it, hiddenIds) }

        // 4. Build Trayectory with embedded Tenacity
        val trayectoryUi = trayectory?.let {
            buildTrayectoryWithTenacity(it, tenacity, key)
        }

        return MonthlyUi(...)
    }
}
```

---

## 6. Implementation Phases & Estimates

| # | Phase | Deliverables | Est. |
|---|---|---|---|
| 1 | **Data & Domain Loading** | Ensure domain query / cache loads positions 10–19; decrypt monthly domains, subdomains, items; add unit tests for monthly decryption | 1.5 d |
| 2 | **Dashboard Shell & Navigation** | Add segmented `TODAY` \| `MONTHLY` toggle in top bar; wrap screens in `HorizontalPager`; implement horizontal swipe navigation; handle deep links (`tab=monthly`) | 1.5 d |
| 3 | **Monthly Scoring Circles** | Implement `MonthlyScoringSection`: `[3, 3, 4]` layout for Classic/Material, `[5, 5]` zigzag layout for Predator; integrate rating sheet and eye toggle | 2.0 d |
| 4 | **Trayectory & Tenacity Inversion** | Implement `TrayectoryDomainCard` with embedded collapsible `TenacitySection` styled with Tenacity's color palette; expand/collapse state handling | 1.5 d |
| 5 | **MonthlyScreen List Composable** | Implement `MonthlyScreen.kt`: Hero (reused), ScoringCircles, Integrity (Warnings + Reminders), Trayectory card, Monthly domain cards 11–19; no widgets | 2.0 d |
| 6 | **Cross-Tab Counter Jumps** | Connect Events and Tasks counter clicks on Monthly to animate pager to Today (page 0) and trigger smooth scroll to section; Mail URL opening | 0.5 d |
| 7 | **Styling & Theme Parity** | Predator chamfer frames, hexagon badges, Chakra Petch fonts; Classic rounded cards; verify all 7 color schemes in light and dark mode | 1.5 d |
| 8 | **QA & Screenshot Parity** | Roborazzi screenshot tests for Monthly screen; verify side-by-side with web `/monthly` on mobile viewport | 1.0 d |

**Total Estimate:** ~11.5 dev days.

---

## 7. Testing Strategy

1. **Unit Tests (JVM):**
   - `MonthlyPresenterTest`:
     - Correct domain filtering: only position 1 and 10–19 are included.
     - Unhidable rule: position 10 (Trayectory) stays visible even when `hidden_domain_ids` is null or contains it.
     - Embedded inversion: Trayectory contains Tenacity subdomains; Tenacity is not duplicated as a root domain.
     - Scoring chunking: `[3, 3, 4]` for non-Predator and `[5, 5]` for Predator.
     - Average rating calculation across all life domains (`position < 20`).
2. **Compose UI Tests (Robolectric):**
   - Swiping pager transitions between Today and Monthly.
   - Tapping top bar segmented toggle animates pager between tabs.
   - On Monthly tab: tapping Events or Tasks switches to Today tab and scrolls to section.
   - Tapping a monthly scoring circle opens the rating sheet with correct domain title and 1–10 options.
   - Embedded Tenacity section inside Trayectory expands and collapses without affecting parent Trayectory state.
3. **Screenshot Parity Tests:**
   - Roborazzi tests comparing Android `MonthlyScreen` against web `/monthly` at 390px mobile viewport across Predator and Classic styles, light and dark themes.

---

## 8. Out of Scope for v1

- **Company MAPP (positions 20–29):** Company view remains web-only; scope is strictly Today and Monthly.
- **Interactive Multi-Step Scoring Wizard:** The full `MonthlyScoringPrompt` wizard opens in web; native provides the quick 1–10 rating sheet.
- **Native Editing of Domain Metadata:** Editing domain info, subdomains, and reminders opens the web app in signed-in Custom Tabs.
