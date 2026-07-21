# TimeBet — Sports Redesign & Polish Spec
**Date:** 2026-07-19
**Status:** Approved — Implementing

---

## Scope

Five workstreams:
1. **Sports — Stake-style redesign** (remove fake data, real API only, bottom-sheet bet slip, My Bets history)
2. **Home Screen — Live timer + real icons + real usage**
3. **Notifications — Wire threshold checks**
4. **Casino Recovery — Persist round state before deduction**
5. **Match Detail — Expanded markets table**

---

## 1. Sports Redesign

### Data Source
- **Supabase only.** Call `SupabaseSyncManager.fetchFixtures()`. If empty, show clean empty state. No hardcoded fallback fixtures.
- Odds come from `fetchOdds(fixtureId)` per fixture — real API data, never hardcoded.
- `placePrediction()` calls both local Room insert AND `SupabaseSyncManager.placePrediction()`.
- `SportsSettlementWorker` actually calls `checkSettlements()` and updates local DB.

### Screen Layout
```
SportsLandingScreen
├── Balance bar (always visible): "⏱ 01:26 left | Active bets: 12m / 24m"
├── Tab toggle: [Fixtures] [My Bets]
├── Fixtures tab:
│   ├── Filter chips: [Today] [Upcoming] (Live deferred until backend supports it)
│   ├── Competition accordions (collapsible, with flag emoji)
│   └── Match rows: [Kickoff] [Home] [1] [X] [2] [Away] [+N markets]
│       - 1X2 odds are tappable chips
│       - "+N" opens Match Detail
└── My Bets tab:
    ├── Active bets (pending_cancelable + pending_locked)
    │   - Green dot, cancel button if same-day
    └── Settled bets (won/lost/void/cancelled) — permanent history
        - ✅ won, ❌ lost, ⚠️ void, ↩️ cancelled
```

### Bet Slip (Bottom Sheet)
- Opens on any odds tap
- Shows: match info, market type, selection, odds
- Stake selector (reuse existing)
- Potential return calculation
- "Place Bet" button
- Same-day cancellation note
- Does NOT navigate away from match list

### Match Detail (Market Table)
Expanded markets in a grouped scrollable list:

| Market Group | Selections | Display |
|-------------|-----------|---------|
| Match Result | Home / Draw / Away | 3 tappable chips |
| Double Chance | 1X / 12 / X2 | 3 tappable chips |
| Over/Under Goals | 0.5, 1.5, 2.5, 3.5, 4.5 | Table: Over Total Under per row |
| Both Teams to Score | Yes / No | 2 tappable chips |
| Cards Over/Under | 0.5, 1.5, 2.5, 3.5, 4.5 | Table: Over Total Under per row |
| Corners Over/Under | 0.5, 1.5, 2.5, 3.5 | Table: Over Total Under per row |

### Bet History
- Filter: last 7 days default, expandable to "All time"
- Stored permanently in Room `sports_predictions` table
- Grouped by: Active → pending, Settled → newest first

---

## 2. Home Screen Fixes

### Live Timer
- Add `LaunchedEffect` with 1-second `delay` loop
- Display ticks in real-time when `activeApp is ActiveAppState.Active`

### App Icons
- Use `context.packageManager.getApplicationIcon(packageName)` 
- Same pattern already working in `ControlledAppsScreen.kt:233-237`
- Fallback: first-letter circle if `NameNotFoundException`

### Usage Rows
- Query `AppUsageSessionDao.getUsageBreakdown(todayStart, todayEnd)` 
- Replace hardcoded `usageSeconds = 0` at `HomeScreen.kt:234`

---

## 3. Notifications

### Threshold Checking
In `ForegroundUsageMonitor`, after each `deduct()`:
- Check new balance against `LOW_TIME_THRESHOLDS` (10min, 5min, 1min)
- Use a `Set<Long>` to track fired thresholds (avoid spam)
- Reset fired set on daily reset

### Notification Posting
Add helper to `TimeBetForegroundService`:
- `postLowTimeWarning(remainingSeconds)` → `LOW_TIME_WARNING` (1001)
- `postTimeUp()` → `TIME_UP` (1002)
- `postTrackingFailure()` → `TRACKING_FAILURE` (1003)

---

## 4. Casino Round Recovery

### Entity Change
Add `status` column to `CasinoRoundEntity`:
- `"initiated"` — stake deducted, result pending
- `"settled"` — round complete
- Default: `"settled"` for backwards compat

### Flow
1. Before deduction: insert round with `status = "initiated"`
2. After result: update round with result + `status = "settled"`

### Recovery
On app start, query for `status = "initiated"`:
- Refund stake via `timeBankEngine.returnStake()`
- Mark round `status = "void"` with metadata note

---

## Files Modified

| File | Change |
|------|--------|
| `SportsLandingScreen.kt` | Full rewrite — Stake layout, real data, My Bets tab, bottom sheet |
| `MatchDetailScreen.kt` | Rewrite — real data, market tables |
| `PredictionSlipScreen.kt` | Replace with bottom-sheet component |
| `SportsSettlementWorker.kt` | Wire real settlement logic |
| `HomeScreen.kt` | Live timer, real icons, real usage |
| `AppDetailScreen.kt` | Real app icon |
| `ForegroundUsageMonitor.kt` | Threshold checks + notification callbacks |
| `TimeBetForegroundService.kt` | Notification posting methods |
| `CasinoRoundEntity.kt` | Add `status` field |
| `CasinoRoundDao.kt` | Add `getUnsettledRounds()`, `update()` |
| `CasinoLandingScreen.kt` | 2-step settle for all 5 inline games |
| `TimeBankRepository.kt` | Initiate + settle casino rounds, recovery |
| `TimeBankEngine.kt` | Add `returnStake()` for recovery |
| `AppDatabase.kt` | DB version bump |
| `SupabaseSyncManager.kt` | Fix `placePrediction()` team/competition fields |

---

## Test Status
- Must maintain 28/28 tests passing
- Add test for round recovery flow
