# TimeBet — Stake-Style Betting Polish
**Date:** 2026-07-21
**Status:** Approved — Implementing

---

## Scope

Seven fixes to make betting flow match Stake.com's instant-replay pattern.

---

## 1. Sports — Remove Friendlies

**File:** `supabase/functions/refresh-fixtures/index.ts`

- Remove `MAJOR_TEAMS` set entirely
- Remove friendlies clause from `isWorthShowing()` — only `MAJOR_LEAGUE_IDS` remains
- Result: only real league/cup fixtures appear, no club friendlies

## 2. Sports — Auto-remove Finished Matches

**File:** `supabase/functions/refresh-fixtures/index.ts`

- During refresh, when a fixture's API status is `FT`/`AET`/`PEN`, update the Supabase row `status` to `"finished"`
- This ensures finished matches drop out of `status=eq.scheduled` queries
- The client already filters with `filterStatus = "scheduled"` so finished matches will auto-disappear

## 3. Sports — Real Sportsbook Odds

**Files:** `SportsLandingScreen.kt`, `SupabaseSyncManager.kt`

- Add `fetchOddsForFixtures(fixtureIds: List<String>)` to SupabaseSyncManager that fetches odds for multiple fixtures in one query
- In `SportsLandingScreen`, after fetching fixtures, fetch real odds and populate `FixtureCard` with actual odds values
- When real odds are unavailable, compute fair odds with a consistent ~6% margin (overround) instead of arbitrary 2.0/3.4/3.5 defaults
- Add a helper `computeDefaultOdds(homeStrength, awayStrength)` that produces realistic 1X2 odds

## 4. Coin Flip — Smooth Animation + Instant Replay

**File:** `CoinFlipScreen.kt`

- Replace the snap-to (`coinRotation.snapTo`) with a smooth deceleration: 3 fast flips (120ms each) → 2 slow flips (200ms each) → settle with spring
- Total animation ~1000ms, ends naturally on the result face — no jarring snap
- After result: show win/loss for ~2s, then auto-transition back to SETUP
- Remove "Flip Again" / "Exit" buttons — user can immediately flip again
- Result shown as inline overlay that fades, not a separate page
- Maintain a single `CoinFlipPhase` enum: `SETUP`, `ANIMATING`, `RESULT` — but RESULT auto-transitions

## 5. Mines — Grid Always Visible + Instant Replay

**File:** `MinesScreen.kt`

- In SETUP phase, render the 5×5 grid with all 25 tiles showing a subtle dot (clickable indicator)
- Controls (mine slider, stake, START) remain below the grid as they are now
- After CASHED_OUT or MINED: show result stats inline for ~2s, then auto-transition back to SETUP
- Remove "Play Again" buttons — user can immediately change mines/stake and hit START

## 6. Blackjack — Instant Replay

**File:** `BlackjackScreen.kt`

- After RESULT: show outcome (YOU WIN / DEALER WINS / PUSH) + amount for ~2s, then auto-transition back to BETTING
- Remove "Play Again" / "Exit" buttons — user can immediately DEAL again
- Preserve previous stake amount for convenience

## 7. Crash — Fix Cashout Race Condition

**File:** `CrashScreen.kt`

- Add guard in the while loop: `if (currentMultiplier >= point && phase == CrashPhase.FLYING)` before settling as loss
- This prevents the 50ms delay window from double-settling after user cashes out
- The "Crashed at X.XX" line already shows how high it could have gone — keep that, just ensure it's visually prominent

---

## General Stake Pattern

The unifying principle across all casino games:

- **Betting UI is always the default state.** The game transitions back to it automatically.
- **Result is an overlay/toast, not a separate screen.** 2-second auto-dismiss.
- **Controls stay visible during play** — disabled, not hidden.
- **No "Play Again" buttons anywhere.** The betting UI IS the "play again" state.
