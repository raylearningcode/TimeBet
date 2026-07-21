# TimeBet — Gamified Screen-Time Betting

**Turn your daily screen time allowance into casino chips.**

TimeBet replaces passive screen-time trackers with an active betting experience. You earn a daily time allowance, and instead of just watching it tick down, you can wager it across casino games and real sports fixtures. Win more time or lose it — every minute counts.

---

## Features

### Casino (7 Games)
| Game | Description | House Edge |
|------|-------------|------------|
| **Coin Flip** | Heads or tails with smooth 5-phase flip animation | ~3% |
| **Mines** | 5×5 grid with 1-24 mines — push your luck with diamond gems | Variable |
| **Roulette** | European single-zero wheel with colored segments + triangle pointer | ~2.7% |
| **Blackjack** | Classic Vegas rules, 6-deck shoe, S17, 3:2 blackjack | ~0.5% |
| **Baccarat** | Player vs Banker with 8-deck shoe, face-down reveal | ~1.06% |
| **Crash** | Rising multiplier — cash out before it crashes, auto-cashout presets | ~8% |
| **Chicken** | Cross the road — dodge cars across 4-10 lanes, cash out anytime | ~5% |

### Sports Betting
- **Real fixtures** from API-Football via Supabase edge functions
- **3 market types**: Match Result (1X2), Over/Under Goals (1.5, 2.5), Both Teams to Score
- **Real odds** from bookmakers with deterministic fallback defaults (~6% margin)
- **My Bets tab**: Active + settled predictions with cancel/resolve

### Time Bank System
- **Daily allowance** of screen time converted to betting stakes
- **Bonus cap**: Win up to 50% of your base allowance in bonuses per day
- **Foreground monitoring**: Tracks which apps you use in real-time
- **App blocking**: Zero balance = blocked apps until next day
- **Notifications**: Low-time warnings at 10min, 5min, 1min thresholds

### Designed for Daily Use
- **Persistent results**: Each game keeps its last result visible until you play again
- **Stake-style flow**: Betting UI always visible, result is an overlay, no page switches
- **Inline casino**: Play directly from the Casino tab without navigating away
- **7 games accessible** via horizontally scrollable chip selector

---

## Screenshots

> Screenshots coming soon. The app features a futuristic dark theme with gold accents, radial gradient coin faces, Canvas-drawn roulette wheel segments, and emoji-rich game feedback.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose (Material 3) |
| **Architecture** | MVVM + Repository pattern |
| **DI** | Manual Service Locator |
| **Database** | Room (SQLite) |
| **Background** | WorkManager, Foreground Service |
| **Backend** | Supabase (PostgREST + Edge Functions) |
| **Sports Data** | API-Football (free tier) |
| **RNG** | `SecureRandom.getInstanceStrong()` (cryptographically secure) |

---

## Architecture

```
app/src/main/java/com/timebet/app/
├── core/
│   ├── blocking/        # App blocking when balance hits zero
│   ├── database/        # Room entities + DAOs (6 entities, 6 DAOs)
│   ├── monitoring/      # ForegroundUsageMonitor
│   ├── notifications/   # Notification channels
│   ├── permissions/     # PermissionHealthMonitor
│   ├── security/        # CryptoRNG (secure Fisher-Yates shuffle)
│   ├── sync/            # SupabaseSyncManager (PostgREST API)
│   └── time/            # 7 game engines + TimeBankEngine + DailyResetManager
├── data/repositories/   # TimeBankRepository, AppRepository
├── design/theme/        # Color, Typography, Theme (dark scheme)
├── features/
│   ├── activity/        # Activity tab (screen time, casino, sports history)
│   ├── blocked/         # "Time's Up" full-screen overlay
│   ├── casino/          # CasinoLandingScreen + 7 game subdirectories
│   ├── controlledapps/  # App toggle management
│   ├── home/            # HomeScreen + AppDetailScreen
│   ├── onboarding/      # 4-step permission flow
│   ├── settings/        # Time bank, permissions, notifications
│   └── sports/          # SportsLandingScreen, MatchDetailScreen
├── navigation/          # NavGraph, NavRoutes, BottomNavBar
├── services/            # TimeBetForegroundService
├── util/                # TimeFormatter, TimeBetConstants
└── workers/             # DailyResetWorker, SportsSettlementWorker
```

---

## Getting Started

### Prerequisites
- **Android Studio** (Hedgehog or later)
- **JDK 17**
- **Gradle 8.13** (wrapper included)
- **Supabase account** (free tier works)
- **API-Football key** (free tier: 100 req/day)

### Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/raylearningcode/TimeBet.git
   cd TimeBet
   ```

2. **Set up Supabase**
   - Create a project at [supabase.com](https://supabase.com)
   - Run the SQL migrations in `supabase/migrations/`
   - Deploy edge functions:
     ```bash
     supabase functions deploy refresh-fixtures
     supabase functions deploy settle-predictions
     ```
   - Set secrets in Supabase Dashboard:
     - `SUPABASE_URL` — your project URL
     - `SUPABASE_SERVICE_ROLE_KEY` — your service role key
     - `API_FOOTBALL_KEY` — your API-Football key

3. **Configure Android project**
   - Set `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `gradle.properties`

4. **Build & Run**
   ```bash
   ./gradlew assembleDebug
   # or open in Android Studio and click Run ▶️
   ```

5. **Run tests**
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## Test Status

```
✅ 35/35 tests passing
✅ 0 failures, 0 errors
✅ Clean build — zero compilation errors
```

| Test Suite | Tests |
|-----------|-------|
| `CasinoMathTest` | 25 tests (coin flip, mines, roulette, blackjack, crash, baccarat, chicken, RNG) |
| `TimeBankEngineTest` | 10 tests (deduction, zero clamp, reset, profit cap, stake validation) |

---

## Key Design Decisions

- **No accounts required** for MVP — fully local with optional Supabase sync
- **Manual DI** over Hilt/Koin — keeps the project simple and testable
- **String-based phases** for inline games — lightweight state management without enum overhead
- **Client-side odds fallback** — deterministic hash of team names for unique, realistic default odds
- **Persistent results** — game outcomes stay visible until user plays again or navigates away
- **No "Play Again" buttons** (removed) — betting UI is always present, result is an overlay

---

## API Limits (Free Tier)

- **API-Football**: 100 requests/day, 10 requests/min
- **Supabase**: 500MB database, 2GB bandwidth, 2 edge functions
- **European club leagues** are in off-season (July). Fixtures populate automatically in August.

---

## License

This project is for educational and personal use. Commercial use requires proper licensing of API-Football data.

---

## Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/compose)
- Backend by [Supabase](https://supabase.com)
- Sports data by [API-Football](https://api-football.com)
- RNG via `SecureRandom.getInstanceStrong()` (NIST SP 800-90A compliant)

---

**Made with ♠️♥️♣️♦️**
