# TimeBet — Changelog & Progress

## v1.0.10 (2026-07-23) — Email Login + Multi-Bet Roulette

### Auth
- **Replaced Google Sign-In with email/password login** via Supabase
- No Google Play Services or OAuth setup needed
- Sign up (new account) or Sign in (returning user)
- Clean dark-themed login card with validation

### Roulette
- **Multi-bet support** — tap multiple numbers + Red/Black + dozens simultaneously
- Shows combined bet count and total stake
- Combined win/loss result display
- **Faster spin animation** (~1.4s, down from ~2.7s)
- Simplified betting board: compact 6-col number grid, big Red/Black buttons
- Removed dense 12-row table layout

### Sign-in
- Prominent error card with icon (replaced tiny text)
- Toggle between Sign In and Create Account

---

## v1.0.9 (2026-07-23)

### Monitoring
- Hidden when working — only shows red warning bar when paused
- 3-second startup delay to avoid false "paused" message
- Service now started from MainActivity (Android 14+ compliance)
- **Fixed**: single monitor instance shared between UI and service

### Sports Betting
- **Dynamic per-fixture odds** (no more fixed 1.85)
- **Double Chance** market (1X, 12, X2)
- **Total Corners** market (8.5, 9.5, 10.5)
- **Total Cards** market (3.5, 4.5, 5.5)

### Time Display
- `formatDetailed()` shows seconds: "32m 15s"
- Activity ScreenTime tab now uses detailed format

### Permission Check
- Switched from deprecated `checkOpNoThrow` to `unsafeCheckOpNoThrow` (API 29+)
- "Fix" button on monitoring warning bar

---

## v1.0.8 (2026-07-23)

### Foreground Detection
- **Dual detection**: `queryEvents` + `queryUsageStats` for reliability
- `detectInitialForegroundApp()` catches apps already open before monitoring starts
- `getMostRecentPackage()` helper for OEM compatibility

### Pull-to-Refresh
- Refresh button on HomeScreen header
- Refresh button on ActivityScreen header
- All LaunchedEffects keyed to refreshTrigger

### Mines UI
- Scrollable layout — start button always reachable
- Grid reduced to 260dp, cells to 48dp

### Roulette UI
- Center number reduced from 40sp to 30sp
- Scrollable layout

---

## v1.0.7 (2026-07-22)

### Critical Fix
- **Single monitor instance**: Service and UI now share the same `ForegroundUsageMonitor`
- Fixed "monitoring paused" stuck notification

---

## v1.0.6 (2026-07-22)

### Android 14-17 Compatibility
- Service start moved from Application to Activity context
- Proper foreground service compliance for API 34+

---

## v1.0.5 (2026-07-22)

### Monitoring Auto-Start
- Service now starts on app launch (was only on device reboot)
- Fixed Supabase token exchange API format

---

## v1.0.4 (2026-07-22)

### Permission Detection
- Fixed `hasUsageStatsPermission()` for newer Android
- "Fix" button on home screen monitoring bar

---

## v1.0.3 (2026-07-22)

### UI Fixes
- Mines: scrollable layout, grid resized
- Roulette: center number display resized
- App list: removed package names
- Monitoring status indicator on home screen

---

## v1.0.2 (2026-07-22)

### Cross-Device Sync
- Google Sign-In (later replaced with email)
- Supabase sync engine pushing/pulling every 30s
- Device detection (auto-names from Build.MODEL)
- Device list with per-device usage stats
- Per-device breakdown in Activity tab

### Activity Screen Redesign
- 4 tabs: Screen Time, Casino, Sports, History
- Bordered cards with icons, color-coded values
- Custom tab buttons (no Material3 Tab interference)
- Loading and empty states

### History & Trends
- Weekly/monthly/6-month bar charts
- Daily aggregation worker
- 6-month data retention (auto-cleanup)

### Block Screen
- Shows real remaining time (not fake "00:00")
- Styled summary card with borders
- Only shows non-zero stats

### Home Screen
- Empty state when no entertainment apps selected
- Pull-to-refresh support
- Fixed `bankState!!` null assertion crash

---

## v1.0.0 — Initial Release

### Time Tracking
- ForegroundUsageMonitor with UsageStatsManager polling
- Shared Time Bank with real-time deduction
- 2-second minimum session filter
- Permission health monitoring

### Casino (7 Games)
- Coin Flip, Mines, Roulette, Blackjack, Baccarat, Crash, Chicken
- Daily profit cap (75% of base allowance)
- Max stake limit (50% of balance)
- Stake selector with quick presets

### Sports Betting
- Football fixtures via Supabase cache
- Match Result, Over/Under, Both Teams to Score markets
- Bet slip with stake selection and odds display

### Blocking
- Full-screen overlay when Time Bank reaches zero
- Usage summary on block screen
- Next reset countdown

### Navigation
- 4-tab bottom navigation (Home, Casino, Sports, Activity)
- Immersive game screens hide bottom nav
- Settings, Onboarding, Controlled Apps screens

---

## Technical Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Architecture | Single-Activity, manual DI (ServiceLocator) |
| Local DB | Room (SQLite) |
| Cloud | Supabase (PostgREST + Auth) |
| Background | WorkManager + Foreground Service |
| Auth | Email/Password via Supabase Auth REST API |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |

---

## Known Limitations

| Issue | Status |
|-------|--------|
| No ViewModel architecture | Manual DI, `remember` state |
| No deep link support | Future |
| Blocking via overlay only | No system-level enforcement |
| Casino stats not reactive after play | One-shot fetch |
| No sound/haptic feedback | Feature toggle removed |
| No light theme | Dark-only by design |
| No pull-to-refresh gesture | Refresh buttons added as workaround |
