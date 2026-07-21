# TimeBet — Full Product Requirements Document (PRD)

**Status:** Draft v1.0  
**Platform:** Android-first  
**Primary build style:** AI-assisted / vibe-coded  
**Document role:** Product, UX, design, architecture, security, implementation, and delivery source of truth

---

# 1. Executive Summary

TimeBet is an Android-first digital wellbeing application that turns a user's daily entertainment screen-time allowance into a shared **Time Bank**.

The user chooses which apps are controlled by TimeBet. Whenever one of those apps is actively used in the foreground, the user's Time Bank decreases in real time.

The user can also risk some of their remaining time in casino-style games. Winning increases the current day's available screen time. Losing decreases it. The casino is designed with a fixed mathematical house advantage so that, over repeated play, the user is statistically expected to lose time rather than gain unlimited screen time.

TimeBet uses **time only** as the wager unit. It does not use real money, deposits, withdrawals, purchasable chips, convertible virtual currency, or cash prizes.

The core product idea is:

> **Risk time. Not money.**

The app is not intended to be marketed as addiction treatment. It should be positioned as a digital wellbeing and harm-reduction-inspired product that makes screen-time limits more engaging for users who enjoy risk/reward mechanics.

---

# 2. Product Vision

## 2.1 Long-Term Vision

Build a polished digital wellbeing platform where entertainment screen time behaves like a limited resource that users can consciously spend, save within the current day, or risk.

The long-term experience should make users more aware of the cost of passive scrolling by giving every minute a visible and immediate value.

## 2.2 Product Principles

1. **Time is the only currency.**
2. **No real-money wagering.**
3. **No negative balances.**
4. **No borrowing future screen time.**
5. **No purchasing more time.**
6. **No ad-watching to restore time.**
7. **All controlled apps draw from one shared Time Bank.**
8. **Casino games have transparent rules and fixed mathematical behavior.**
9. **The product should statistically reduce available screen time over repeated gambling.**
10. **The interface must remain calm, premium, minimalist, and polished.**
11. **The app must not encourage endless gambling loops through manipulative notifications or promotions.**
12. **The system must never silently pretend app usage tracking is accurate if Android tracking permissions fail.**

## 2.3 What TimeBet Must Never Become

- A real-money casino.
- A crypto casino.
- A cash-reward product.
- A purchasable virtual-currency game.
- A product that claims to clinically treat gambling addiction.
- A casino app whose business success depends on gambling volume.
- A generic screen-time timer with casino graphics added on top.

---

# 3. Problem Statement

## 3.1 Screen-Time Problem

Traditional screen-time tools usually rely on:

- Daily app limits.
- Scheduled downtime.
- Blocking rules.
- Usage charts.

These tools are useful but often feel passive and easy to ignore.

TimeBet introduces a shared resource model:

> The user has a limited amount of entertainment time and must decide how to spend it.

This makes the cost of each additional minute more visible.

## 3.2 Gambling-Impulse Problem

Some users enjoy risk/reward mechanics but do not want to risk money.

TimeBet provides casino-like gameplay where the consequence is loss of optional entertainment time instead of financial loss.

This is a product experiment, not a clinical intervention.

---

# 4. Target Users

## 4.1 Primary User

A smartphone user who:

- Spends more time than desired on social media, video, games, or entertainment apps.
- Finds conventional screen-time blockers boring or easy to disable.
- Enjoys game mechanics, probability, and risk/reward interactions.
- Wants a stronger psychological cost attached to entertainment use.

## 4.2 Secondary User

A user who enjoys gambling-style mechanics but prefers not to wager money.

## 4.3 Excluded Positioning

The app should not present itself as:

- Medical treatment.
- Gambling addiction therapy.
- Financial harm recovery treatment.

---

# 5. Product Goals

## 5.1 MVP Goals

The MVP must prove that TimeBet can reliably:

1. Track selected Android apps when they are in the foreground.
2. Deduct time from one shared Time Bank.
3. Stop deduction when the controlled app leaves the foreground.
4. Block controlled apps when the Time Bank reaches zero.
5. Reset the daily Time Bank correctly.
6. Allow users to gamble available time in deterministic, testable game systems.
7. Enforce a daily winnings ceiling.
8. Show detailed app usage on Home and Activity.
9. Recover safely from permission loss, app restart, phone reboot, and tracking interruptions.

## 5.2 Short-Term Goals

- Add additional casino games.
- Add real sports predictions using bookmaker-style odds.
- Improve analytics.
- Improve reliability across Android manufacturers.
- Add widgets and optional notifications.

## 5.3 Long-Term Goals

- Broader Android device support.
- Optional account sync.
- Cross-device analytics.
- Additional wellbeing modes.
- Optional lower self-imposed risk limits.

---

# 6. Non-Goals

The MVP will not include:

- iOS support.
- Real-money betting.
- Crypto deposits.
- Purchasable time.
- Ad-based time rewards.
- Social casino leaderboards.
- Multiplayer casino games.
- User-to-user transfers.
- Cash prizes.
- Parlays.
- Same-game multis.
- Live sports betting.
- Sports cash-out.
- Player props.
- Casino bonuses.
- Daily free spins.
- Loot boxes.

---

# 7. Core Product Model

## 7.1 Shared Time Bank

Every user has one active daily entertainment Time Bank.

Example:

```text
Base Daily Allowance: 120 minutes
Current Balance: 86 minutes
```

All controlled apps consume from this same balance.

There are no separate TikTok, Instagram, YouTube, or gaming limits in MVP.

## 7.2 Base Daily Allowance

The user chooses a base daily allowance during onboarding.

Recommended quick options:

- 1 hour
- 2 hours
- 3 hours
- Custom

Suggested minimum:

30 minutes

Suggested recommended maximum:

6 hours

The app may technically allow more, but unusually high values should trigger a wellbeing warning.

## 7.3 Daily Reset

Default reset:

Local midnight.

At reset:

- Remaining usable balance expires.
- New balance is restored to base allowance.
- Daily casino profit counter resets.
- Daily usage totals begin a new day.
- Pending sports predictions remain pending.
- Locked sports wagers remain attached to their prediction.

Unused time does not roll over.

---

# 8. Casino Economy

## 8.1 General Rule

The user may gamble only from currently available Time Bank balance.

No:

- Negative balance.
- Borrowing.
- Credit.
- Future-time staking.

## 8.2 Daily Winnings Cap

Maximum net gambling profit per day:

**75% of the user's base daily allowance.**

Formula:

```text
max_daily_bonus = base_daily_allowance_seconds * 0.75
max_daily_balance = base_daily_allowance_seconds + max_daily_bonus
```

Example:

```text
Base allowance: 120 minutes
Maximum net winnings: 90 minutes
Maximum daily usable balance: 210 minutes
```

The cap applies to combined casino and settled sports profit.

## 8.3 House Advantage

TimeBet uses a fixed mathematical house advantage.

Rules:

- Results must not be dynamically rigged based on a user's history.
- A user who has won several games must not be secretly forced to lose.
- High balances must not reduce personal win probability.
- RNG must not be altered per user for retention or punishment.

The long-run disadvantage should come from the game math itself.

The exact house advantage can vary by game.

The interface may display only the relevant multiplier or odds during normal play, while the full game math and house-edge explanation is available in Game Info / Fairness.

## 8.4 Profit Cap Behavior

If a user is near the 75% daily bonus cap and a win would exceed it:

- The result remains a win.
- Only the amount up to the remaining cap is credited.
- The user must be warned before placing a wager if the possible payout is meaningfully capped.

Example:

```text
Remaining bonus capacity: 12 minutes
Potential game profit: 40 minutes
Maximum credited profit: 12 minutes
```

## 8.5 Reaching the Daily Bonus Cap

Recommended behavior:

Once the user's net daily gambling profit reaches the maximum 75% cap:

- Further casino games are disabled for the day.
- The user can still use their remaining Time Bank normally.
- Existing sports predictions continue to settle, but no settlement may push profit above the daily cap.

Reason:

This prevents a user from reaching the cap, gambling down, then endlessly continuing the casino loop.

---

# 9. Casino Game Scope

## MVP

- Coin Flip
- Mines
- Roulette

## Phase 2

- Blackjack
- Crash

## Phase 3

- Chicken

---

# 10. Coin Flip Specification

## 10.1 Purpose

Simple, fast, understandable risk mechanic.

## 10.2 Flow

1. User chooses stake.
2. User selects Heads or Tails.
3. User taps Flip.
4. RNG resolves outcome.
5. Result animation plays.
6. Balance updates.

## 10.3 Payout Model

The experience should feel like a simple double-or-lose game.

Implementation must use a predefined probability and payout model that creates a small house advantage.

Do not alter the probability based on individual history.

## 10.4 States

- Setup
- Animating
- Win
- Loss
- Balance too low
- Daily bonus cap reached

---

# 11. Mines Specification

## 11.1 Board

5 × 5 grid.

Total tiles:

25

## 11.2 Mine Count

User may select:

1–24 mines.

The number of safe tiles is:

```text
25 - mine_count
```

## 11.3 Flow

1. Select stake.
2. Select mine count.
3. Start round.
4. Mine positions are generated.
5. User selects a tile.
6. Safe tile increases cash-out multiplier.
7. User may cash out or continue.
8. Mine hit loses the entire stake.

## 11.4 Payout Calculation

The multiplier must be calculated dynamically based on:

- Total tiles.
- Mine count.
- Number of safe tiles revealed.
- Probability of surviving the selected number of reveals.
- Configured house advantage.

Do not hardcode an arbitrary multiplier table unless it is generated from a documented formula and stored as a deterministic lookup for performance.

## 11.5 UX Requirements

Before starting, display:

- Stake.
- Mine count.
- Safe tile count.
- Risk level.

During play, display:

- Current multiplier.
- Current cash-out amount.
- Number of remaining safe tiles.

---

# 12. Roulette Specification

## 12.1 Type

European single-zero roulette.

Numbers:

0–36

## 12.2 MVP Bets

- Red
- Black
- Odd
- Even
- 1–18
- 19–36
- Dozens
- Columns
- Single numbers

## 12.3 Flow

1. User selects stake.
2. User selects betting position.
3. User confirms wager.
4. Wheel animation begins.
5. RNG determines result.
6. Ball settles.
7. Win/loss is settled.

## 12.4 House Advantage

Use the normal single-zero roulette probability structure.

Do not manipulate individual spins.

---

# 13. Blackjack Specification

## MVP+ Rules Recommendation

- One player hand.
- Dealer hand.
- Hit.
- Stand.
- Double down.
- No insurance.
- No side bets.
- Splits may be deferred.

Exact dealer rules must be documented before coding.

Recommended:

- Dealer stands on soft 17.
- Blackjack payout defined explicitly.
- Standard deterministic shoe rules.

The deck must not be dynamically manipulated per user.

---

# 14. Crash Specification

## 14.1 Flow

1. Select stake.
2. Start round.
3. Multiplier rises from 1.00x.
4. User may cash out at any time before crash.
5. If crash occurs first, stake is lost.

## 14.2 MVP Restrictions

No:

- Auto cash-out.
- Auto bet.
- Infinite repeat mode.

## 14.3 Fairness

Crash point generation must be deterministic and testable.

Do not determine the crash point in an easily modifiable client-only function.

---

# 15. Chicken Specification

P1/P2.

A push-your-luck crossing game.

Each successful step increases multiplier.

User may:

- Continue.
- Cash out.

Failure loses stake.

Because this overlaps behaviorally with Crash, it is not required before core product validation.

---

# 16. Sports Betting Lite

## 16.1 Product Position

Sports Betting Lite uses real fixtures and bookmaker-style fixed odds but deliberately avoids full sportsbook complexity.

## 16.2 Initial Sport

Football only.

## 16.3 MVP Markets

- Home / Draw / Away
- Over / Under 1.5 goals
- Over / Under 2.5 goals
- Both Teams to Score

## Later Markets

- Corners over/under
- Cards over/under
- Fouls over/under

## Explicitly Excluded Initially

- Parlays
- Live betting
- Same-game multi
- Player props
- Asian handicaps
- Sports cash-out

---

# 17. Sports Stake Model

## 17.1 Maximum Active Sports Stake

Maximum active sports stake is a fixed percentage of base daily allowance.

Recommended default:

20%.

Formula:

```text
max_active_sports_stake = base_daily_allowance_seconds * 0.20
```

Example:

```text
Base allowance: 120 minutes
Maximum active sports stake: 24 minutes
```

This limit applies to the sum of all active unsettled sports wagers.

Not per match.

## 17.2 Placement

When placed:

- Stake is immediately deducted from today's Time Bank.
- The bet records odds_at_placement.

## 17.3 Same-Day Cancellation

If the current local calendar date is the same as the date the prediction was placed:

- User may cancel.
- Full original stake is returned.

Once the local date changes:

- Prediction becomes locked.
- Cancellation is no longer possible.

## 17.4 Settlement

### Win

Credit only the profit.

Example:

```text
Stake: 10 minutes
Odds: 1.75
Theoretical return: 17m 30s
Credited profit: 7m 30s
```

The original stake belonged to the day the bet was placed and is not returned again.

### Loss

Credit nothing.

### Void

Return original stake.

## 17.5 Daily Bonus Cap Interaction

Sports profit counts toward the same 75% daily gambling profit cap.

---

# 18. Android App-Control Model

## 18.1 Platform Strategy

Android only for MVP.

iOS is explicitly deferred.

## 18.2 Desired User Experience

User opens TikTok normally from the launcher.

TimeBet automatically detects foreground usage.

No requirement to launch controlled apps through TimeBet.

## 18.3 Controlled App Logic

If foreground app is controlled:

- Start or resume deduction.

If foreground app is not controlled:

- Pause deduction.

If screen locks:

- Pause deduction.

If user returns to controlled app:

- Resume deduction.

## 18.4 Shared Bank

All controlled apps consume from the same Time Bank.

## 18.5 Tracking Accuracy Principle

TimeBet must never silently continue with stale or unknown tracking status.

If usage access, accessibility, or tracking stops working:

- Show an explicit tracking failure state.
- Pause authoritative balance deduction if accuracy cannot be guaranteed.
- Prompt user to restore permissions.

---

# 19. Home Screen

## 19.1 Purpose

Immediately answer:

- How much time is left?
- How much time has been used?
- How much time has been won?
- How much time has been lost?
- Which apps used the time?
- Is a controlled app currently active?

## 19.2 Header

```text
TIMEBET
Thursday, July 16
```

Profile/settings icon on the right.

No generic greeting.

## 19.3 Main Balance

```text
01:26
REMAINING
```

Very large typography.

Below:

```text
Base 02:00
Won +00:34
Lost -00:18
```

## 19.4 Used Today

```text
USED TODAY
01:08
```

Show a thin visual usage indicator.

## 19.5 Live Controlled App

Only visible when active:

```text
NOW USING
TikTok
00:07:42
```

Balance should update live.

## 19.6 App Usage Breakdown

Show top 3–5 controlled apps:

```text
TikTok       32m
Instagram    21m
YouTube      10m
Reddit        5m
```

Each row:

- App icon.
- App name.
- Today's foreground usage.
- Thin relative usage bar.

Tap row → App Detail.

---

# 20. App Detail Screen

Display:

- App icon.
- App name.
- Today's usage.
- 7-day average.
- Session count.
- Longest session.
- Percentage of total controlled usage.
- 7-day usage chart.

No individual app allowance in MVP.

---

# 21. Low-Time Warnings

Default thresholds:

- 10 minutes
- 5 minutes
- 1 minute

Android notification at each threshold.

Optional subtle overlay in the final minute.

Final 10-second countdown may be shown if technically reliable and not overly intrusive.

---

# 22. Zero-Balance Behavior

When balance reaches zero:

- Controlled apps are blocked.
- Casino is disabled.
- New sports wagers cannot be placed.
- Existing sports wagers remain active.

Blocked screen:

```text
00:00
TIME'S UP

You've used today's available entertainment time.
```

Display:

- Used today.
- Time won.
- Time lost.
- Next reset.

Actions:

- Back
- View Activity

Never show:

- Buy time.
- Watch ad.
- Free spin.
- Borrow time.

---

# 23. Primary Navigation

Four tabs:

1. Home
2. Casino
3. Sports
4. Activity

Settings and controlled-app management live behind the profile/settings icon.

Bottom navigation disappears inside immersive game screens.

---

# 24. Casino Landing Screen

Header:

```text
CASINO
01:26 available to play
```

Show daily bonus progress:

```text
42m / 90m
```

Game list:

- Roulette
- Coin Flip
- Mines
- Blackjack
- Crash
- Chicken later

Show today's casino summary:

- Time wagered.
- Time won.
- Time lost.
- Net result.

---

# 25. Universal Stake Selector

Reusable across games.

Display:

- Current balance.
- Stake.
- Plus/minus.
- Quick values.

Example quick stakes:

- 5m
- 10m
- 15m
- 30m

Optional percentage shortcuts:

- 25%
- 50%
- Custom

Avoid a prominent All-In button.

Validation:

- Stake > 0.
- Stake <= available balance.
- Game allowed under daily cap.

---

# 26. Sports UX

## 26.1 Sports Landing

Display:

- Active stake used.
- Maximum active stake.
- Upcoming matches.
- My Predictions.

Example:

```text
Active stake
12m / 24m
```

## 26.2 Match Card

Display:

- Competition.
- Teams.
- Kickoff time.
- Three primary odds.

## 26.3 Match Detail

Markets grouped by category.

Keep the screen intentionally limited.

## 26.4 Prediction Slip

Show:

- Selection.
- Odds.
- Stake.
- Theoretical return.
- Actual credited profit.
- Same-day cancellation rule.

## 26.5 Pending Prediction

Same-day:

- Cancel button visible.

After date changes:

- Status = Locked.
- Cancel button removed.

---

# 27. Activity

Tabs:

- Screen Time
- Casino
- Sports

## 27.1 Screen Time

Show:

- Controlled app usage.
- Base allowance.
- Time won.
- Time lost.
- Unused time.
- Top apps.
- 7-day chart.
- 30-day chart.

## 27.2 Casino

Show:

- Total time wagered.
- Total profit.
- Total losses.
- Net effect.
- Win rate.
- Most played game.
- Largest win.
- Largest loss.
- Per-game breakdown.

## 27.3 Sports

Show:

- Predictions placed.
- Pending.
- Won.
- Lost.
- Void.
- Total time staked.
- Total credited profit.

---

# 28. Settings

Sections:

- Time Bank
- Controlled Apps
- Casino
- Sports
- Permissions
- Notifications
- Appearance
- Privacy
- Help
- About

---

# 29. Design Direction

## 29.1 Visual Identity

Futuristic minimalist black-and-white design.

Reference feel:

- Modern poker UI.
- Premium fintech.
- Minimal digital wellbeing.

Not:

- Neon casino.
- Crypto casino.
- Generic Material template.
- AI-generated dashboard.

## 29.2 Core Palette

```text
Background: #000000
Elevated surface: #0A0A0A to #121212
Primary text: #FFFFFF
Secondary text: #A0A0A0 to #B5B5B5
Tertiary text: #666666 to #777777
Borders: #1C1C1C to #262626
```

Semantic accents should be restrained:

- Green for gain/win.
- Red for loss/error.
- Amber for warning.

## 29.3 Typography

Use a modern sans-serif with strong number rendering.

Recommended candidates:

- Inter
- Geist
- Manrope

Prefer tabular numerals for countdowns and balances.

## 29.4 Typography Scale

- Display XL: 48–72sp
- Display: 36–48sp
- H1: 28–32sp
- H2: 20–24sp
- Body: 15–17sp
- Label: 12–14sp
- Caption: 11–12sp

## 29.5 Spacing

4-point base system:

4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64

Recommended page margins:

20–24dp.

## 29.6 Design Rules

- Do not turn every section into a card.
- Do not add gradients unless explicitly specified.
- Do not use emoji as production icons.
- Do not use excessive pill buttons.
- Do not use glassmorphism.
- Do not add random accent colors.
- Prefer whitespace and typography over borders.
- Use one consistent icon family.
- Avoid fake casino felt and gold-chip visuals.

---

# 30. Motion and Haptics

## Motion Principles

Motion communicates:

- State.
- Cause/effect.
- Progress.
- Result.

Recommended durations:

- Microinteraction: 100–200ms
- Screen transition: 200–350ms

## Game Motion

Coin Flip:

- 1–2 seconds.

Roulette:

- 3–5 seconds.

Mines:

- Fast tile press and reveal.

Crash:

- Smooth multiplier growth.

## Haptics

Use selectively for:

- Button confirmation.
- Tile reveal.
- Coin landing.
- Roulette result.
- Win/loss settlement.

Sound should be optional and minimal.

---

# 31. Technical Architecture

## 31.1 Recommended MVP Stack

### Android

Recommended:

- Kotlin
- Jetpack Compose
- Android Architecture Components
- Coroutines / Flow
- Room for local persistence
- WorkManager for recoverable background tasks

Why:

The app depends heavily on Android foreground usage monitoring, permissions, services, and blocking behavior. A native Android implementation is safer and more controllable than forcing a cross-platform abstraction into the core enforcement layer.

### Backend

Recommended MVP:

- Supabase

Use for:

- Optional authentication.
- Cloud profile sync.
- Sports data cache.
- Prediction settlement records.
- Remote config.
- Analytics metadata.

The core Time Bank should remain locally authoritative for immediate enforcement.

### Sports Provider

Use a dedicated sports/odds provider in Phase 3.

The provider should supply:

- Fixtures.
- Start time.
- Markets.
- Odds.
- Final results.
- Settlement statistics where available.

## 31.2 Local-First Principle

Core functions must not require constant server connectivity:

- Time Bank.
- Foreground app usage tracking.
- App blocking.
- Daily reset.
- Local casino games.

Sports requires network.

---

# 32. Android Enforcement Architecture

## 32.1 Components

Recommended architecture:

1. ForegroundUsageMonitor
2. TimeBankEngine
3. AppBlockController
4. DailyResetManager
5. PermissionHealthMonitor
6. LocalEventStore
7. CasinoEngine
8. SportsPredictionRepository

## 32.2 Foreground Usage Monitoring

The implementation must investigate and combine Android-supported mechanisms suitable for:

- Detecting current foreground package.
- Reading usage events.
- Keeping tracking alive reliably.

Potential Android mechanisms may include:

- UsageStatsManager.
- AccessibilityService where justified.
- Foreground service where necessary for reliability.

A technical spike is required before finalizing the production enforcement path.

## 32.3 Time Deduction

The TimeBankEngine should operate on monotonic elapsed time, not repeated one-second database writes.

Recommended approach:

- Record active session start timestamp.
- When state changes, calculate elapsed duration.
- Deduct elapsed duration atomically.
- Persist session.

This avoids drift and excessive writes.

## 32.4 App Blocking

When balance <= 0 and foreground package is controlled:

- Trigger TimeBet block experience.
- Prevent ordinary continued access using the selected Android enforcement mechanism.

Blocking behavior must be tested across OEM variants.

## 32.5 Reboot Recovery

After device reboot:

- Reinitialize tracking.
- Restore today's balance.
- Reconcile any open session conservatively.
- Revalidate permissions.

---

# 33. Data Model

## 33.1 UserSettings

```text
id
base_daily_allowance_seconds
reset_timezone
reset_hour
sports_stake_limit_percentage
max_daily_bonus_percentage
notifications_enabled
haptics_enabled
sound_enabled
created_at
updated_at
```

## 33.2 ControlledApp

```text
id
package_name
app_name
is_controlled
created_at
updated_at
```

## 33.3 DailyTimeBank

```text
id
date
base_allowance_seconds
current_balance_seconds
casino_profit_seconds
casino_loss_seconds
sports_profit_seconds
used_seconds
created_at
updated_at
```

Unique:

```text
date
```

## 33.4 AppUsageSession

```text
id
package_name
started_at
ended_at
duration_seconds
was_controlled
created_at
```

## 33.5 CasinoRound

```text
id
game_type
stake_seconds
profit_seconds
loss_seconds
result
round_metadata_json
started_at
settled_at
```

## 33.6 SportsPrediction

```text
id
provider_event_id
sport
competition
home_team
away_team
market_type
selection
odds_at_placement
stake_seconds
potential_profit_seconds
placed_at
placement_local_date
status
locked_at
settled_at
settlement_profit_seconds
provider_payload_json
```

Statuses:

- pending_cancelable
- pending_locked
- won
- lost
- void
- cancelled

## 33.7 DailyUsageAggregate

Optional derived table for performance.

```text
date
package_name
usage_seconds
session_count
longest_session_seconds
```

---

# 34. Time Bank Invariants

The following must always hold:

```text
current_balance_seconds >= 0
```

```text
daily_net_profit_seconds <= max_daily_bonus_seconds
```

```text
active_sports_stake_seconds <= max_active_sports_stake_seconds
```

No casino round may start if:

- Balance is zero.
- Stake exceeds balance.
- Daily bonus cap has already been reached.

---

# 35. Casino Transaction Safety

Every casino round should follow an atomic settlement flow.

Recommended sequence:

1. Validate current balance.
2. Reserve/deduct stake.
3. Generate result.
4. Calculate capped profit.
5. Apply settlement atomically.
6. Persist round.
7. Emit balance update.

Avoid UI-only balance updates.

The database/state engine must be authoritative.

---

# 36. Sports API Design

Recommended backend endpoints:

```text
GET /sports/fixtures
GET /sports/events/{eventId}
POST /sports/predictions
POST /sports/predictions/{id}/cancel
GET /sports/predictions
```

Server/background settlement job:

```text
POST /internal/sports/settle
```

The client must never be trusted to declare a sports prediction won.

---

# 37. Security Requirements

## 37.1 General

- No secrets in Android client.
- Sports provider keys remain server-side.
- Validate all backend input.
- Use least privilege.
- Use HTTPS.

## 37.2 Local Tampering

Because the Time Bank is local-first, rooted devices or modified clients may alter local state.

MVP stance:

This is acceptable for a personal wellbeing tool, but the architecture should make casual tampering harder.

Possible measures:

- Signed local snapshots.
- Encrypted preferences for critical settings.
- Server sync of major state transitions for logged-in users.

Do not overengineer anti-cheat before product validation.

## 37.3 RNG

Casino RNG must use a cryptographically secure random generator where appropriate.

Do not use predictable pseudo-random logic for production outcomes.

## 37.4 Sports Integrity

Odds at placement must be snapshotted.

Settlement must be based on provider result data, not client state.

---

# 38. Privacy

The app should collect the minimum required data.

Sensitive local data includes:

- Installed app list.
- Controlled app list.
- App usage sessions.

Privacy principles:

- App usage data stays local by default where possible.
- Cloud sync should be optional.
- Do not upload complete installed-app lists unless required and disclosed.
- Provide data deletion.
- Provide export later.

---

# 39. Permission UX

Required permissions must be explained before Android system screens appear.

For each permission show:

- Permission name.
- Why it is needed.
- What TimeBet can observe.
- What TimeBet cannot observe.

If required permission is revoked:

- Show persistent in-app warning.
- Mark tracking as unreliable.
- Provide Fix Tracking action.

---

# 40. Offline Behavior

Works offline:

- Home.
- Time Bank.
- Controlled app tracking.
- Blocking.
- Coin Flip.
- Mines.
- Roulette.
- Blackjack.
- Crash.
- Local Activity.

Requires network:

- Sports fixtures.
- Odds refresh.
- Sports prediction placement if server authoritative.
- Sports settlement refresh.

---

# 41. Analytics Events

Product analytics should track behavior, not sensitive content.

Suggested events:

- onboarding_completed
- permission_granted
- permission_revoked
- controlled_app_added
- controlled_app_removed
- controlled_app_session_started
- time_bank_zero_reached
- casino_round_started
- casino_round_settled
- sports_prediction_placed
- sports_prediction_cancelled
- sports_prediction_settled
- daily_reset_completed
- tracking_failure_detected

Do not record screen contents or typed user data.

---

# 42. Testing Strategy

## 42.1 Unit Tests

TimeBankEngine:

- Deduction.
- Zero clamp.
- Daily reset.
- Profit cap.
- Sports stake limit.

Casino math:

- Coin Flip expected distribution.
- Mines multiplier calculation.
- Roulette payouts.
- Blackjack settlement.
- Crash settlement.

## 42.2 Integration Tests

- Foreground app enters controlled state.
- Foreground app exits controlled state.
- Screen lock pauses usage.
- Balance reaches zero.
- Blocking activates.
- App restart restores balance.
- Reboot restores tracking.

## 42.3 End-to-End Tests

Critical flow:

Onboarding
→ Grant permissions
→ Select TikTok
→ Open TikTok
→ Time decreases
→ Return to TimeBet
→ Gamble
→ Balance changes
→ Return to TikTok
→ Reach zero
→ TikTok blocked

## 42.4 Reliability Tests

Test across:

- Pixel.
- Samsung.
- Xiaomi.
- Oppo.
- Vivo.

Background restrictions vary heavily by OEM.

## 42.5 Statistical Tests

Run large simulations for each casino game.

Verify:

- Observed win rate matches intended probability.
- Long-run RTP matches configured model.
- No user-history dependency exists.

---

# 43. Error Handling

## Tracking Failure

Message:

```text
Time tracking is temporarily unavailable.
```

Show:

- Last successful tracking timestamp.
- Likely cause.
- Fix Tracking action.

## Sports Failure

```text
Sports data couldn't be refreshed.
Check your connection and try again.
```

## Casino Failure

A casino result must never be lost after stake deduction.

Use transaction-safe round state:

- initiated
- stake_reserved
- result_generated
- settled

Recover incomplete rounds on restart.

---

# 44. Accessibility

- Minimum 48dp touch target where possible.
- Support Android font scaling where practical.
- Do not rely only on color.
- Use text + semantic color for win/loss.
- Reduced Motion setting planned.
- Haptics optional.
- Sound optional.

---

# 45. Development Phases

## Phase 0 — Product Foundation

Objective:

Create project skeleton and technical spikes.

Tasks:

- Native Android project setup.
- Compose theme.
- Navigation shell.
- Room database.
- TimeBankEngine prototype.
- Android usage-monitoring spike.
- App-blocking spike.

Definition of done:

A test build can detect a selected app entering foreground and log elapsed usage accurately.

## Phase 1 — Core Time Bank

- Onboarding.
- Base allowance.
- Controlled app selection.
- Daily reset.
- Foreground tracking.
- Shared balance.
- Home screen.
- App usage detail.

## Phase 2 — Blocking

- Zero-balance state.
- Controlled app blocking.
- Permission health monitoring.
- Reboot recovery.
- Low-time notifications.

## Phase 3 — Casino MVP

- Universal stake selector.
- Coin Flip.
- Mines.
- Roulette.
- Casino landing.
- Daily winnings cap.
- Casino Activity.

## Phase 4 — Casino Expansion

- Blackjack.
- Crash.
- Chicken later.

## Phase 5 — Sports Lite

- Sports provider integration.
- Fixture list.
- Match details.
- Fixed odds.
- Sports stake cap.
- Same-day cancellation.
- Date lock.
- Profit-only settlement.

## Phase 6 — Polish

- Motion.
- Haptics.
- Accessibility.
- OEM testing.
- Performance.
- Crash recovery.
- Visual refinement.

---

# 46. Vibe-Coding Task Breakdown

Every AI coding task should be small and verifiable.

Example task format:

## Task Name

Implement TimeBankEngine daily balance model.

## Objective

Create the domain logic responsible for current daily balance, deduction, reset, and bonus caps.

## Context

TimeBet uses one shared daily Time Bank for all controlled apps.

## Requirements

- Store all durations in seconds or milliseconds internally.
- Prevent negative balance.
- Support daily reset.
- Support net gambling profit cap of 75% of base allowance.
- Expose immutable observable state.

## Constraints

- No UI logic inside engine.
- No direct Android context dependency.
- Must be unit-testable.

## Acceptance Criteria

- Deducting 60 seconds reduces balance by exactly 60 seconds.
- Balance never goes below zero.
- Reset restores base allowance.
- Profit cannot exceed configured daily maximum.

## Tests

- Normal deduction.
- Over-deduction.
- Reset.
- Profit below cap.
- Profit exceeding cap.

---

# 47. AI Coding Agent Rules

1. Read this PRD before architectural changes.
2. Do not invent new product features.
3. Do not change the Time Bank economy without explicit approval.
4. Do not change database schema silently.
5. Reuse components before adding duplicates.
6. Keep business logic out of Composables.
7. Never expose API keys in the Android client.
8. Validate all input.
9. Handle loading, empty, error, permission, and offline states.
10. Keep Kotlin types strict.
11. Avoid premature abstraction.
12. Avoid unnecessary dependencies.
13. Preserve working enforcement logic when adding features.
14. Run relevant tests after changes.
15. Fix build and lint issues before marking complete.
16. Document new environment variables.
17. Do not use client-only settlement for sports.
18. Do not dynamically manipulate casino probability based on user history.
19. Do not add neon casino styling.
20. Do not turn every UI section into a rounded card.

---

# 48. Anti-Generic Design Rules

Mandatory:

1. No dashboard-card overload.
2. No random gradients.
3. No glassmorphism.
4. No emoji as final icons.
5. No giant pill-shaped UI everywhere.
6. No filler statistics.
7. No meaningless decorative charts.
8. No neon casino visuals.
9. No gold coin currency metaphors.
10. No fake chips.
11. Time values must remain the dominant currency representation.
12. Screens must have one clear visual priority.
13. Use spacing before borders.
14. Use typography before containers.
15. Immersive game screens hide bottom navigation.

---

# 49. Definition of Done

TimeBet MVP is not complete until:

- Onboarding works.
- Mandatory permissions are explained and validated.
- Controlled apps can be selected.
- Foreground usage is tracked reliably.
- Shared Time Bank deducts accurately.
- Screen lock pauses deduction.
- Switching apps pauses/resumes correctly.
- Daily reset is reliable.
- Zero balance blocks controlled apps.
- Coin Flip works.
- Mines works with 1–24 mines.
- Roulette works.
- Daily gambling profit cap works.
- Home shows remaining time and used time.
- Home shows detailed app usage.
- Activity history works.
- Reboot recovery works.
- Permission loss is detected.
- No secrets are exposed.
- No major crash-recovery issues exist.
- Core unit and integration tests pass.
- The UI passes the TimeBet design review checklist.

---

# 50. Design Review Checklist

For every screen:

- Is its purpose obvious within two seconds?
- Is there one dominant visual element?
- Is the primary action easy to reach?
- Is spacing intentional?
- Is typography hierarchy clear?
- Are there too many containers?
- Are colors restrained?
- Are loading and error states defined?
- Does it work on smaller Android screens?
- Does it feel like the same product as the rest of TimeBet?
- Does it look deliberately designed rather than AI-generated?

---

# 51. Open Technical Decisions

## DECISION REQUIRED — Android Blocking Method

A technical spike must determine the most reliable, policy-compliant method for blocking controlled apps across supported Android devices.

## DECISION REQUIRED — Account Requirement

Recommendation:

Do not require account creation for MVP.

Use local-first operation.

Allow optional account/sync later.

## DECISION REQUIRED — Exact Casino House Edge

The exact RTP/house-edge target per game must be defined before production launch.

Recommendation:

Use a small fixed edge per game, documented in Game Info.

## DECISION REQUIRED — Sports Odds Provider

Choose provider based on:

- Football coverage.
- Odds coverage.
- Result reliability.
- Cost.
- Rate limits.
- Licensing terms.

## DECISION REQUIRED — Sports Void/Postponement Rules

Must define exact rules before launch.

---

# 52. Recommended Repository Structure

```text
app/
  src/main/java/.../
    app/
    core/
      database/
      time/
      permissions/
      monitoring/
      blocking/
      notifications/
    data/
      local/
      remote/
      repositories/
    domain/
      models/
      usecases/
    features/
      onboarding/
      home/
      controlledapps/
      casino/
        coinflip/
        mines/
        roulette/
        blackjack/
        crash/
      sports/
      activity/
      settings/
    design/
      theme/
      components/
      motion/
    navigation/
    services/
    workers/
    util/
  src/test/
  src/androidTest/
```

---

# 53. Final Product Loop

```text
DAILY RESET
    ↓
BASE TIME BANK
    ↓
USER OPENS CONTROLLED APP
    ↓
TIME DEDUCTS
    ↓
USER MAY CONTINUE USING APPS
OR
USER MAY GAMBLE AVAILABLE TIME
    ↓
WIN → BALANCE INCREASES WITHIN DAILY CAP
LOSE → BALANCE DECREASES
    ↓
BALANCE REACHES ZERO
    ↓
CONTROLLED APPS BLOCK
    ↓
ACTIVITY SHOWS WHERE TIME WENT
    ↓
NEXT DAILY RESET
```

Sports adds an asynchronous branch:

```text
PLACE SPORTS PREDICTION
    ↓
STAKE DEDUCTED TODAY
    ↓
SAME DAY → MAY CANCEL
NEXT DAY → LOCKED
    ↓
EVENT SETTLES
    ↓
WIN → PROFIT ONLY CREDITED
LOSS → NOTHING CREDITED
VOID → STAKE RETURNED
    ↓
SUBJECT TO DAILY BONUS CAP
```

---

# 54. Final Product Positioning

TimeBet should be presented as a polished digital wellbeing product with a unique time-risk mechanic.

The core message is:

> **Your time is limited. Spend it, or risk it.**

Secondary tagline:

> **Risk time. Not money.**

The product's success should be judged primarily by whether users become more intentional with entertainment screen time, not by how often they gamble.

