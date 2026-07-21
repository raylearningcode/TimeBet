# TimeBet — Project Progression (ARCHIVE)
**Date:** 2026-07-16
**Status:** Core app scaffolded. Supabase + API-Football pending deployment. Sports needs live wiring.
**Updated:** See [PROGRESSION_2026-07-19.md](./PROGRESSION_2026-07-19.md) for latest.

---

## Overall Completion: ~80% (now ~92% as of 2026-07-19)

---

## ✅ Completed

### Build System
| Item | Status | Notes |
|------|--------|-------|
| Gradle wrapper (8.13) | ✅ | Upgraded from 9.3.0 → 8.13 for AGP compatibility |
| Version catalog (`libs.versions.toml`) | ✅ | AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2024.12, Room 2.6.1 |
| Root `build.gradle.kts` | ✅ | Plugin declarations |
| App `build.gradle.kts` | ✅ | minSdk 26, targetSdk 35, Compose enabled, KSP, Supabase SDK |
| `gradle.properties` | ✅ | Supabase URL + anon key configured |
| ProGuard rules | ✅ | Room, Moshi, Supabase, Coroutines rules |
| `.gitignore` | ✅ | |

### Android Resources
| Item | Status | Notes |
|------|--------|-------|
| `AndroidManifest.xml` | ✅ | Permissions (Usage Stats, Overlay, Foreground Service, Boot), Activity, Service, Receiver |
| `strings.xml` | ✅ | All user-facing strings |
| `themes.xml` | ✅ | Black background, no action bar |
| `colors.xml` | ✅ | Icon background color |
| Adaptive launcher icons | ✅ | "TB" monogram vector, `mipmap-anydpi-v26` |
| Notification channels | ✅ | LOW_TIME, BLOCKING, SPORTS |

### Core Engine Layer (pure Kotlin, zero Android deps)
| Item | File | Status |
|------|------|--------|
| TimeBankEngine | `core/time/TimeBankEngine.kt` | ✅ Mutex-protected, atomic deduction/reset/cap enforcement |
| CoinFlipEngine | `core/time/CoinFlipEngine.kt` | ✅ 48.5% win rate, 1.95x payout, ~3% house edge |
| MinesEngine | `core/time/MinesEngine.kt` | ✅ 5×5 grid, 1-24 mines, dynamic multiplier formula |
| RouletteEngine | `core/time/RouletteEngine.kt` | ✅ European single-zero, all PRD bet types |
| BlackjackEngine | `core/time/BlackjackEngine.kt` | ✅ 6-deck shoe, S17, hit/stand/double, 3:2 blackjack |
| CrashEngine | `core/time/CrashEngine.kt` | ✅ Provably fair, house edge ~3%, instant crash 1% |
| CryptoRNG | `core/security/CryptoRNG.kt` | ✅ `SecureRandom.getInstanceStrong()`, Fisher-Yates shuffle |
| DailyResetManager | `core/time/DailyResetManager.kt` | ✅ WorkManager periodic, locks past-day predictions |

### Database Layer (Room)
| Item | Status |
|------|--------|
| `UserSettingsEntity` + DAO | ✅ Singleton row, all settings fields |
| `ControlledAppEntity` + DAO | ✅ Package name, toggle, Flow observation |
| `DailyTimeBankEntity` + DAO | ✅ Date-unique, balance tracking, profit/loss |
| `AppUsageSessionEntity` + DAO | ✅ Session tracking, usage breakdown queries |
| `CasinoRoundEntity` + DAO | ✅ Game type, profit/loss, stats aggregation queries |
| `SportsPredictionEntity` + DAO | ✅ All 6 statuses, lock/settle/cancel queries |
| `DailyUsageAggregateEntity` + DAO | ✅ Derived performance table |

### Infrastructure
| Item | Status |
|------|--------|
| `ForegroundUsageMonitor` | ✅ UsageStatsManager polling, session start/end tracking |
| `AppBlockController` | ✅ Zero-balance detection, blocked activity launch |
| `PermissionHealthMonitor` | ✅ Usage Stats + Overlay check, tracking state enum |
| `TimeBetForegroundService` | ✅ START_STICKY, hosts monitor + block controller |
| `BootReceiver` | ✅ Restarts service after reboot |
| `DailyResetWorker` | ✅ Creates new day bank, locks past predictions |
| `SportsSettlementWorker` | ✅ Polling structure for settlement |

### Networking & Sync
| Item | Status |
|------|--------|
| `SupabaseSyncManager` | ✅ Client setup, fixture fetch, prediction placement, settlement check, analytics |
| API data classes | ✅ `FixtureResponse`, `MarketResponse`, `PredictionRequest`, `SettlementResponse` |

### Data Layer
| Item | Status |
|------|--------|
| `TimeBankRepository` | ✅ Bridges all engines + DAOs for ViewModels, casino settlement flow |
| `AppRepository` | ✅ Installed app listing, controlled app CRUD, usage detail + weekly chart |
| `ServiceLocator` | ✅ Manual DI wiring all dependencies |

### Design System
| Item | Status |
|------|--------|
| Color palette | ✅ `#000000` base, surface variants, semantic green/red/amber, gold accent |
| Typography | ✅ Display XL→Caption scale, tabular numerals for balances |
| Theme | ✅ `darkColorScheme()` with all surface/error/outline tokens |

### Navigation
| Item | Status |
|------|--------|
| `NavRoutes` | ✅ 15 routes: 4 tabs + onboarding + settings + 7 casino games + 3 sports |
| `NavGraph` | ✅ All routes wired, bottom nav hidden on immersive screens |
| `BottomNavBar` | ✅ 4 tabs: Home, Casino, Sports, Activity |

### Feature Screens
| Screen | File | Status |
|--------|------|--------|
| **Onboarding** | `features/onboarding/OnboardingScreen.kt` | ✅ 4-step: Welcome → Permission explain → Grant → Allowance picker |
| **Home** | `features/home/HomeScreen.kt` | ✅ Balance display, used today, base/won/lost chips, live app indicator, usage breakdown rows |
| **App Detail** | `features/home/AppDetailScreen.kt` | ✅ Stats grid, 7-day bar chart, session info |
| **Controlled Apps** | `features/controlledapps/ControlledAppsScreen.kt` | ✅ Installed app list with toggles |
| **Casino Landing** | `features/casino/CasinoLandingScreen.kt` | ✅ Balance header, bonus progress bar, game list, today's summary stats |
| **Coin Flip** | `features/casino/coinflip/CoinFlipScreen.kt` | ✅ Heads/tails picker, animated flip, result display, StakeSelector |
| **Mines** | `features/casino/mines/MinesScreen.kt` | ✅ 5×5 grid, mine count slider, dynamic multiplier, cash-out button |
| **Roulette** | `features/casino/roulette/RouletteScreen.kt` | ✅ Wheel animation, bet type chips, number grid, dozens/columns |
| **Blackjack** | `features/casino/blackjack/BlackjackScreen.kt` | ✅ Hit/Stand/Double, card display, dealer AI (S17) |
| **Crash** | `features/casino/crash/CrashScreen.kt` | ✅ Rising multiplier, animated growth, cash-out, crash display |
| **Sports Landing** | `features/sports/SportsLandingScreen.kt` | ✅ Active stake bar, predictions list, fixture cards (placeholder data) |
| **Match Detail** | `features/sports/MatchDetailScreen.kt` | ✅ Market groups with odds, clickable selections |
| **Prediction Slip** | `features/sports/PredictionSlipScreen.kt` | ✅ Stake selector, payout summary, same-day cancel info, placement |
| **Activity** | `features/activity/ActivityScreen.kt` | ✅ 3 tabs: Screen Time, Casino, Sports with stat cards + lists |
| **Settings** | `features/settings/SettingsScreen.kt` | ✅ Time Bank, Permissions, Notifications, Haptics, Sound toggles |
| **Blocked** | `features/blocked/BlockedActivity.kt` | ✅ Full-screen "TIME'S UP", usage summary, View Activity / Back |

### Supabase Backend
| Item | Status |
|------|--------|
| SQL Migration (`001_initial_schema.sql`) | ✅ 4 tables, RLS policies, indexes, `cancel_prediction()` function |
| Edge Function: `refresh-fixtures` | ✅ Fetches 5 European leagues from API-Football, caches fixtures + odds |
| Edge Function: `settle-predictions` | ✅ Checks finished matches, settles all 4 market types, handles voids |
| Config in `gradle.properties` | ✅ URL + anon key set |

### Tests
| Item | Status |
|------|--------|
| `TimeBankEngineTest` | ✅ 11 tests: deduction, zero clamp, reset, profit cap, stake validation |
| `CasinoMathTest` | ✅ 18 tests: coin flip distribution, mines multipliers, roulette payouts, crash monotonicity, blackjack hand values, RNG range checks |

---

## ❌ Not Yet Done / Remaining

### 1. Supabase Deployment (YOU need to do)
| Task | How |
|------|-----|
| Set `API_FOOTBALL_KEY` secret | Supabase Dashboard → Settings → Edge Functions → Secrets |
| Deploy `refresh-fixtures` | Edge Functions → New Function → paste code → Deploy |
| Deploy `settle-predictions` | Edge Functions → New Function → paste code → Deploy |
| Run migration SQL | SQL Editor → paste `supabase/migrations/001_initial_schema.sql` → Run |
| Schedule `refresh-fixtures` | Function detail → Schedules → `*/15 * * * *` |
| Schedule `settle-predictions` | Function detail → Schedules → `*/5 * * * *` |

### 2. Sports — Wire Real Data
- **`SportsLandingScreen.kt`** currently calls `getPlaceholderFixtures()` — needs to call `SupabaseSyncManager.fetchFixtures()` instead
- Fixture response types need to be mapped to the UI's `FixtureCard` model
- Odds need to be fetched per fixture and mapped to the UI

### 3. Home Screen — Live Usage Data
- App usage rows currently show `usageSeconds = 0` — need to be populated from `DailyUsageAggregateDao` or `AppUsageSessionDao` aggregation
- The live "NOW USING" timer doesn't tick in real-time (needs a `LaunchedEffect` with a 1-second delay loop)

### 4. App Icons
- App icon placeholders use first-letter circles — should use `PackageManager.getApplicationIcon()` with Coil or `AndroidView` for real icons

### 5. Casino — Incomplete Round Recovery
- PRD Section 43: If app crashes mid-casino-round after stake deduction, the round must be recoverable. Current implementation doesn't persist `initiated`/`stake_reserved` states — only records after settlement.

### 6. Chicken Game
- Engine + screen deferred (PRD Phase 3). NavGraph has the route but it's commented out.

### 7. Low-Time Notifications
- Notification channels exist, thresholds defined, but the actual notification posting logic isn't wired into the monitoring loop.

### 8. Android Instrumented Tests
- Only `src/test/` (unit tests) exist. `src/androidTest/` is empty — needs integration tests for Room, UsageStatsManager flows, and Compose UI tests.

### 9. Accessibility
- PRD Section 44 lists: Reduced Motion setting, font scaling support, non-color-only indicators. These aren't implemented yet.

### 10. Widget
- PRD Section 5.2 mentions widgets for later phases. Not started.

### 11. Optional Supabase Auth
- Supabase Auth SDK is included as a dependency but no sign-up/login flow exists. Per PRD §51: "Do not require account creation for MVP."

### 12. Data Export
- PRD Section 38 mentions "Provide export later." Not implemented.

### 13. OEM Testing
- PRD Section 42.4 requires testing across Pixel, Samsung, Xiaomi, Oppo, Vivo. Needs physical devices or Firebase Test Lab.

---

## 📁 File Count

| Category | Files |
|----------|-------|
| Build system | 6 |
| Core — Database | 15 |
| Core — Engines | 7 |
| Core — Infrastructure | 7 |
| Data Layer | 3 |
| Design System | 3 |
| Navigation | 3 |
| Feature Screens | 16 |
| Services & Workers | 3 |
| Supabase Backend | 3 |
| Utilities | 1 |
| Tests | 2 |
| Android Resources | 6 |
| **Total Kotlin source files** | **61** |
| **Total lines of Kotlin** | **~8,200** |

---

## 🚀 Next Steps (Priority Order)

1. **Deploy Supabase** — set secret, deploy 2 functions, run migration, schedule (you do this from the web dashboard)
2. **Wire sports to live data** — replace `getPlaceholderFixtures()` with Supabase calls
3. **Wire home screen usage data** — populate app rows from the DAO
4. **Live timer** — add real-time ticking to HomeScreen's "NOW USING" section
5. **Low-time notifications** — wire threshold checks into monitoring loop
6. **Casino recovery** — persist round state before deduction, recover on restart
7. **Instrumented tests** — Room + Compose UI tests in `androidTest/`
8. **App icons** — replace letter placeholders with actual PackageManager icons
