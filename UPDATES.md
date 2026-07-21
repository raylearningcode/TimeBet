# TimeBet — All Updates
**Last updated:** 2026-07-21

---

## Session 1 — July 19 (Previous)
- Supabase backend deployed (real fixtures, settlement)
- Sports redesign: Stake-style layout, bottom-sheet bet slip, My Bets tab
- Casino games: Coin Flip animation, Mines bomb reveal, Roulette wheel, Crash auto-cashout, Blackjack card backs
- Home screen: live timer, real app icons, real usage data
- Notifications wired (low time warnings)
- Casino round recovery on crash
- 28/28 tests passing

---

## Session 2 — July 21 (Today)

### 1. Sports — Remove Friendlies
**File:** `supabase/functions/refresh-fixtures/index.ts`
- Removed `MAJOR_TEAMS` set (~80 lines) — no more club friendlies
- `isWorthShowing()` now only passes `MAJOR_LEAGUE_IDS` (Premier League, La Liga, Serie A, Bundesliga, Ligue 1, UCL, UEL, World Cup, Euros, Nations League, etc.)
- Added cleanup step: deletes any cached friendlies from Supabase on each run
- Reduced date window: yesterday + today + 5 days forward (was 10 days forward)
- Now fetches yesterday to auto-update finished match statuses to `"finished"` so they drop out of the app

### 2. Sports — Real Sportsbook Odds
**Files:** `SportsLandingScreen.kt`, `SupabaseSyncManager.kt`
- Added `fetchOddsBatch()` to fetch odds for all fixtures in one API call instead of N separate calls
- `FixtureCard` now populated with **real odds** from Supabase `sports_odds` table
- Falls back to `computeDefaultOdds()` with proper ~6% bookmaker margin when API odds unavailable
- Default odds use a deterministic hash of team names — every match gets unique, realistic odds (not identical 2.0/3.4/3.5 for all)

### 3. Coin Flip — Smooth Animation + Instant Replay
**File:** `CoinFlipScreen.kt`
- **Animation:** 3 fast flips (120ms each, LinearEasing) → 2 slow deceleration flips (200ms each, FastOutSlowInEasing) → spring settle on result. Total ~1000ms. No jarring snap-to.
- **Controls always visible** — Heads/Tails chips and stake selector never disappear
- FLIP button says "FLIPPING…" during animation, re-enabled immediately after
- **Auto-return:** Uses `LaunchedEffect(phase)` for reliable recomposition. Result shows for 2s then auto-returns to betting.
- **No "Flip Again" or "Exit" buttons** — you can immediately flip again after the result fades

### 4. Mines — Grid Always Visible + Diamond Tiles
**File:** `MinesScreen.kt`
- **5×5 grid visible at all times** — including during SETUP (before game starts)
- Tiles show **diamond gems** (rotated gold squares, like Stake.com) instead of tiny dots
- Controls (mine count slider, stake, START button) below the grid
- After CASHED_OUT or MINED: result stats shown inline for 2-2.5s, then **auto-returns to SETUP**
- **No "Play Again" or "Exit" buttons** — can immediately change mines/stake and hit START

### 5. Blackjack — Instant Replay
**File:** `BlackjackScreen.kt`
- Result (WIN/LOSE/PUSH) shown for 2s, then **auto-returns to BETTING**
- Stake amount preserved — DEAL button ready immediately
- **No "Play Again" or "Exit" buttons**

### 6. Roulette — Smooth Wheel + Auto-Return
**File:** `RouletteScreen.kt`
- **3-phase wheel spin:** fast spin (5-8 rotations, 1200ms) → deceleration (600ms, FastOutSlowInEasing) → spring settle (lands precisely on result number)
- **Auto-return to betting** after 2.5s
- **No "Spin Again" or "Exit" buttons** — result shown inline with "Returning to bet…"

### 7. Crash — Auto-Cashout Presets + Race Condition Fix
**File:** `CrashScreen.kt`
- **Race condition fixed:** Added `phase == CrashPhase.FLYING` guard in crash detection loop — prevents double-settlement when user cashes out
- Added `phase != CrashPhase.FLYING` guard in `cashOut()` — prevents acting twice
- **Auto-cashout preset buttons:** 1.5x, 2x, 3x, 5x — shown below multiplier during flight
- CASH OUT button now shows **total payout** (stake + profit) instead of just multiplier
- CASHED_OUT result shows total payout + profit + "Crashed at X.XXx" clearly
- **Auto-return to betting** after 2.5s on both CRASHED and CASHED_OUT

### 8. Supabase — Deployed + Cleaned
- `refresh-fixtures` edge function **deployed** with all changes
- Old friendlies **deleted** from production database
- Only real competitions now showing (World Cup, UCL, UECL qualifiers)

---

## General Pattern: Stake-Style Flow
Every casino game now follows this pattern:
- **Betting UI is the default state** — always visible
- **Result is an inline overlay**, not a separate screen
- **Auto-returns to betting** after 2-2.5 seconds
- **No "Play Again" buttons anywhere**

---

## Test Status
- **28/28 tests passing**
- Build: **SUCCESSFUL** (clean build verified)
- Zero crashes on emulator

---

## Files Modified (July 21)

| File | Change |
|------|--------|
| `app/.../coinflip/CoinFlipScreen.kt` | Smooth 5-phase animation, LaunchedEffect auto-return, controls always visible |
| `app/.../mines/MinesScreen.kt` | Diamond gem tiles, grid always visible, auto-return, no Play Again |
| `app/.../roulette/RouletteScreen.kt` | 3-phase wheel spin, spring settle, auto-return |
| `app/.../crash/CrashScreen.kt` | Auto-cashout presets, race condition fix, total payout display |
| `app/.../blackjack/BlackjackScreen.kt` | Auto-return to BETTING, no Play Again |
| `app/.../sports/SportsLandingScreen.kt` | Real odds from batch fetch, computeDefaultOdds helper |
| `app/.../sync/SupabaseSyncManager.kt` | fetchOddsBatch() for single-request odds |
| `supabase/functions/refresh-fixtures/index.ts` | Friendlies removed, cleanup, yesterday fetch for status updates |

---

## APK Location
```
C:\Users\ACER\OneDrive\Desktop\TimeBet.apk
```
```
C:\Users\ACER\OneDrive\Desktop\TimeBet\app\build\outputs\apk\debug\app-debug.apk
```

## How to Run in Android Studio
1. **File → Sync Project with Gradle Files**
2. **Build → Clean Project**
3. **Build → Rebuild Project**
4. Click **Run** (green ▶️)
