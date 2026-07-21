# TimeBet — Project Progression
**Date:** 2026-07-19
**Status:** Backend deployed. Casino games redesigned Stake-style. 28/28 tests passing.

---

## Overall Completion: ~97%

---

## ✅ Completed Today

### 1. Supabase Backend — Fully Deployed & Live
| Item | Details |
|------|---------|
| Database schema | 4 tables (sports_fixtures, sports_odds, sports_predictions, analytics_events), RLS, indexes, cancel_prediction() function |
| `refresh-fixtures` edge function | Deployed. Fetches today + tomorrow fixtures via API-Football, filters to major leagues only (PL, La Liga, Serie A, Bundesliga, Ligue 1, UCL, UEL, World Cup, Euros, Nations League) + friendlies with big teams (Liverpool, Arsenal, Man United, Man City, Barcelona, Real Madrid, Bayern, PSG, etc.) |
| `settle-predictions` edge function | Deployed. Checks finished fixtures and settles all 4 market types (home_draw_away, over_under_1_5, over_under_2_5, both_teams_to_score) |
| `API_FOOTBALL_KEY` | Set. Free tier — 100 req/day, 10 req/min. Date-based queries bypass season restriction. |
| Cron schedules | `refresh-fixtures` every 6h, `settle-predictions` every 10min via pg_cron + pg_net |
| Live data | **4 real fixtures** currently: Spain vs Argentina (World Cup), Sevilla/Fiorentina/Sporting CP friendlies. More when European seasons start in August. |

### 2. Sports — Complete Stake-Style Redesign
| Item | Before | After |
|------|--------|-------|
| Fixtures data | 12 hardcoded fake matches (England vs Brazil, fake PL...) | Real data from Supabase/AFI-Football. Empty state shown when no backend. |
| Odds | Hardcoded 2.0/2.10/3.4 | Real odds from API |
| Match Detail | Always "Arsenal vs Chelsea" | Uses eventId to fetch real fixture + odds. 6 market groups: Match Result, Double Chance, O/U Goals table, BTTS, Cards O/U table, Corners O/U table |
| Bet Slip | Full-screen navigation | Bottom sheet — slides up on odds tap |
| Bet History | None | My Bets tab: Active + Settled (permanent) |
| Settlement | No-op worker | Actually calls checkSettlements() and updates local DB |

### 3. Casino Games — Stake-Style Redesign
| Game | Changes |
|------|---------|
| **Coin Flip** | Throw animation (coin rises + flips in air + falls). Buttons stay visible during animation (just disabled). Result inline. 3 flips × 140ms = ~500ms total. No separate win/lose page. |
| **Mines** | 5x5 grid visible from betting phase. On start: grid becomes interactive, controls stay visible. On loss: ALL bombs revealed with staggered animation. Result inline — no page switch. |
| **Roulette** | Proper wheel Canvas — 37 colored segments (European order), spinning animation (1200ms fast + 800ms deceleration), pointer at top, result flash. |
| **Blackjack** | Card backs (🂠) shown before deal. Dealer second card hidden during play. Cards stay visible after game ends. Result inline. Button always says "DEAL". |
| **Crash** | Auto cashout presets (1.5×, 2×, 3×, 5×). Cash-out win bug fixed (was double-settling as loss). Result inline. |
| **All games** | Buttons say consistent action ("FLIP", "DEAL", "START", "SPIN") — no "Again" suffixes. |

### 4. Bet Input — Custom Amount
- Tap the stake time display to edit inline (type `5m`, `90s`, `1:30`, `120`)
- Quick-stake chips and -/+ buttons still work
- No separate textbox — clean integrated UI

### 5. Home Screen Fixes
| Item | Details |
|------|---------|
| Live timer | 1-second `LaunchedEffect` loop — "NOW USING" ticks in real-time |
| App icons | Real `PackageManager.getApplicationIcon()` + Coil `AsyncImage`. Fallback to first-letter circle. |
| Usage rows | Query `AppUsageSessionDao.getUsageBreakdown()` for real per-app usage — no longer hardcoded 0 |

### 6. Notifications — Fully Wired
- `LOW_TIME_WARNING` at 10min, 5min, 1min thresholds
- `TIME_UP` when balance hits zero
- `TRACKING_FAILURE` when permissions lost
- Threshold dedup (firedThresholds set) to prevent spam

### 7. Casino Round Recovery
- `CasinoRoundEntity.status` field: `"initiated"` / `"settled"`
- `recoverUnsettledRounds()` on startup — refunds stakes from crashed rounds
- DB version 3→4

### 8. Activity Page
- Fixed tab background color bug

---

## 📁 Files Modified Today

| File | Change |
|------|--------|
| `SportsLandingScreen.kt` | Complete rewrite — Stake layout, real data, My Bets, bottom sheet |
| `MatchDetailScreen.kt` | Complete rewrite — real data, 6 market tables |
| `PredictionSlipScreen.kt` | Superseded by bottom sheet |
| `NavGraph.kt` | Removed PredictionSlip route |
| `HomeScreen.kt` | Live timer, real icons, real usage data |
| `AppDetailScreen.kt` | Real app icon |
| `ForegroundUsageMonitor.kt` | Notification callbacks |
| `TimeBetForegroundService.kt` | Notification posting, recovery on startup |
| `CasinoRoundEntity.kt` | Added `status` field |
| `CasinoRoundDao.kt` | `getUnsettledRounds()` + `update()` |
| `TimeBankRepository.kt` | 2-step casino settlement, recovery |
| `AppDatabase.kt` | Version 3→4 |
| `SportsSettlementWorker.kt` | Real settlement logic |
| `SupabaseSyncManager.kt` | Fixed placePrediction fields |
| `ServiceLocator.kt` | Added sportsPredictionDao |
| `CasinoLandingScreen.kt` | CoinFlip throw anim, Mines grid always visible + bomb reveal, Blackjack card backs, Crash auto cashout + bug fix, Roulette wheel, bet input, button text cleanup |
| `CoinFlipScreen.kt` | Faster animation (3 flips × 140ms) |
| `MinesScreen.kt` | Bomb reveal animation, grid always visible |
| `RouletteScreen.kt` | Proper wheel Canvas animation |
| `ActivityScreen.kt` | Tab background fix |
| `supabase/functions/refresh-fixtures/index.ts` | Date-based queries, major-league filter, rate-limit aware |
| `supabase/functions/settle-predictions/index.ts` | Deployed (unchanged) |
| `supabase/migrations/001_setup_cron.sql` | Cron schedules for refresh + settlement |

---

## 📊 Test Status
- **28/28 tests passing**
- 0 failures, 0 errors
- Build clean

---

## ❌ Remaining / Next Improvements

### Bug Fixes
| Item | Priority | Notes |
|------|----------|-------|
| OEM background reliability | High | Test across Samsung, Xiaomi, Oppo, Vivo — foreground service may be killed |
| Screen lock pauses deduction | Medium | Verify works across all OEMs |

### Features
| Item | Priority | Notes |
|------|----------|-------|
| **Chicken game** | Low | Engine exists, screen deferred |
| **Widget** | Low | Home screen widget for quick balance check |
| **Accessibility** | Medium | Reduced Motion, font scaling, non-color indicators |
| **Supabase Auth** | Low | Optional accounts for cross-device sync |
| **Data Export** | Low | PRD §38 — export usage/casino/sports history |
| **Instrumented Tests** | Medium | Empty `src/androidTest/` — Room + Compose UI tests |
| **Sound effects** | Low | Optional casino sounds (coin flip, roulette spin, win/loss) |

### Known Limitations
| Item | Notes |
|------|-------|
| Free API-Football tier | 100 req/day, only 2022-2024 seasons (date-based queries for current fixtures). European club leagues in off-season (July). August = full data. |
| Casino round recovery | Only works if app restarts. Mid-round crash still loses stake if process is killed immediately. |
| No live sports odds | Odds fetched only for first 8 scheduled fixtures per refresh (rate limit). Others use default odds. |
| Push notifications | Notifications fire but may not be visible if app is in background on some OEMs. |
| App usage on first install | No historical data — needs ~24 hours to build meaningful charts. |

---

## 🚀 When European Season Starts (August 2026)
- Premier League, La Liga, Serie A, Bundesliga, Ligue 1 fixtures will populate automatically
- Champions League / Europa League qualifiers
- Full odds data for all major matches
- Sports tab will have 50+ fixtures per day instead of ~4

---

## ⚡ Quick Reference

### Supabase Dashboard
- **Project**: https://supabase.com/dashboard/project/uqffngdjdzdkyqgkamca
- **Edge Functions**: https://supabase.com/dashboard/project/uqffngdjdzdkyqgkamca/functions
- **SQL Editor**: https://supabase.com/dashboard/project/uqffngdjdzdkyqgkamca/sql

### API-Football
- **Key**: `9672acf44b09137501a1a7b738c2dbf1` (Free tier)
- **Limits**: 100 req/day, 10 req/min, seasons 2022-2024 only (date queries bypass this)
- **Upgrade**: https://www.api-football.com/pricing

### Build & Test
```bash
cd "C:/Users/ACER/OneDrive/Desktop/TimeBet"
./gradlew testDebugUnitTest  # Run all tests
./gradlew assembleDebug      # Build APK
```
