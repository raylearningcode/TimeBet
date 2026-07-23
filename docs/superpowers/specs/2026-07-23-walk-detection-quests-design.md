# Walk Detection & Quest System — Design Spec

**Date:** 2026-07-23
**Status:** Approved
**Scope:** WalkDetector engine, Quest engine, HomeScreen UI additions, Health Connect integration

---

## Problem Summary

1. **Phone-while-walking habit**: User wants the app to detect walking and discourage phone use — either by locking or charging 2x/3x time, with an override option.
2. **Earn more time**: User wants a quest system that rewards healthy/productive real-world behavior (steps, usage discipline) with bonus time.
3. These are **two independent subsystems** that share Health Connect as a data source.

---

## Design

### Part 1: Walk Detection Engine

#### 1.1 How It Works

A new `WalkDetector` class uses Android's `SensorManager` with the `TYPE_ACCELEROMETER` sensor. It samples at ~20 Hz and runs threshold-based peak detection:

- Detects acceleration peaks above a threshold (~1.2g)
- Minimum time between peaks (~300ms) to filter noise
- When >= 3 peaks detected within a 3-second window → state = `WALKING`
- When no peaks for 10 seconds → state = `STATIONARY`

No ML, no GPS, no battery drain. Standard pedometer logic.

#### 1.2 States

```
IDLE → (3+ steps in 3s) → WALKING
WALKING → (0 steps in 10s) → IDLE
```

Exposed as a `StateFlow<WalkState>`:

```kotlin
sealed class WalkState {
    data object Stationary : WalkState()
    data object Walking : WalkState()
}
```

#### 1.3 Integration with ForegroundUsageMonitor

`ForegroundUsageMonitor` already tracks `isMonitoring` and `activeApp`. Add:

1. Observe `WalkDetector.walkState`
2. When `walkState == Walking && activeApp is ActiveAppState.Active`:
   - Set time multiplier to `2.0` (double deduction)
   - If the controlled app just entered foreground while walking → launch `WalkWarningActivity`
3. When walking stops → reset multiplier to `1.0`

The `endCurrentSession()` method already computes duration — multiply by the current `timeMultiplier` before deducting.

#### 1.4 Walk Warning Overlay

A new `WalkWarningActivity` — a full-screen dialog-style Activity:

```
┌─────────────────────────┐
│                         │
│         ⚠️              │
│  You're walking —       │
│  put your phone away    │
│                         │
│  [App icon + name]      │
│  is currently open      │
│                         │
│  ┌───────────────────┐  │
│  │  Put phone away   │  │  ← primary, closes controlled app
│  └───────────────────┘  │
│  ┌───────────────────┐  │
│  │ I need this (2x)  │  │  ← secondary dimmed, dismisses overlay
│  └───────────────────┘  │
│                         │
└─────────────────────────┘
```

- "Put phone away" → broadcasts an intent to go home, ends the controlled app session
- "I need this (2x)" → dismisses overlay, sets multiplier to 2x, user continues with double time burn
- Overlay auto-dismisses 10 seconds after `WalkState` returns to `Stationary`

#### 1.5 Permissions

- `ACTIVITY_RECOGNITION` — needed for sensor access (already covered by existing permission flow)
- No new manifest permissions needed beyond what's standard

---

### Part 2: Quest Engine

#### 2.1 Quest Types

| Type | Example | Verdict | Reward Timing |
|---|---|---|---|
| Step | "Walk 6,000 steps today" | Real-time via Health Connect | Instant on completion |
| Discipline | "Keep TikTok under 30 min today" | Verified at end of day | Awarded at daily reset |
| Combo | "8K steps + TikTok under 20 min" | Mixed verification | Partial at completion + final at reset |

#### 2.2 Quest Lifecycle

```
GENERATED (at daily reset)
  → ACTIVE (progress tracked live)
    → COMPLETED (goal met, reward pending for discipline)
      → CLAIMED (reward added to time bank)
    → EXPIRED (day ended, goal not met)
```

#### 2.3 Quest Generation

At daily reset (midnight or user's configured time), `QuestGenerator` creates 3 quests:

1. Pick quest types: always 1 step, 1 discipline, 1 combo
2. Step target: 7-day average steps × 1.2 (stretch goal), minimum 3,000
3. Discipline target: 7-day average usage × 0.75 (reduce by 25%), minimum 10 min
4. Combo: 1 step component + 1 discipline component, reward scaled up

**Example generation** (user averages 4,500 steps, 45 min TikTok):
- Step quest: "Walk 5,400 steps — earn 10 min"
- Discipline quest: "TikTok under 34 min — earn 15 min"
- Combo quest: "5K steps + TikTok under 40 min — earn 20 min"

#### 2.4 Quest Data Model

```kotlin
@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,           // UUID
    val date: String,                      // "2026-07-23"
    val type: String,                      // "step" | "discipline" | "combo"
    val title: String,                     // "Walk 5,400 steps"
    val targetValue: Long,                 // target steps or target usage seconds
    val targetPackageName: String?,        // null for step quests, package for discipline
    val currentValue: Long,                // current steps or current usage seconds
    val rewardSeconds: Long,               // time reward in seconds
    val status: String,                    // "active" | "completed" | "claimed" | "expired"
    val completedAt: Long?,                // epoch millis when completed
    val claimedAt: Long?                   // epoch millis when reward claimed
)
```

#### 2.5 Quest Progress Tracking

**Step quests**: `QuestProgressUpdater` polls Health Connect every 5 minutes via `RecordsClient.readRecords()`. Updates `currentValue` on active step quests. When `currentValue >= targetValue` → completes quest → adds reward to time bank → notifies user.

**Discipline quests**: `QuestProgressUpdater` reads from existing `AppUsageSessionDao.getUsageBreakdown()`. Updates `currentValue` every 1 minute. A discipline quest stays "active" all day — it's only completed IF at end-of-day the usage stayed below target. `DailyResetManager` runs the final check.

**Combo quests**: Step component tracked live; discipline component checked at reset. Rewarded only when BOTH parts pass.

#### 2.6 Reward Rules

- Step quests: 5–20 min per quest (scaled to step difficulty)
- Discipline quests: 10–25 min per quest (scaled to reduction percentage)
- Combo quests: 15–30 min per quest
- **Max daily quest earnings: 45 min** (keeps time bank economy balanced)
- Quest rewards bypass the 75% casino profit cap — they're behavioral, not gambling

#### 2.7 Health Connect Integration

Use Android's official `health-connect-client` library:

```kotlin
// Read today's step count
val response = healthConnectClient.readRecords(
    ReadRecordsRequest(
        recordType = StepsRecord::class,
        timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
    )
)
val totalSteps = response.records.sumOf { it.count }
```

**Permissions**: `android.permission.health.READ_STEPS` — requested on first quest screen visit.

**Fallback**: If Health Connect is unavailable (older device or denied), fall back to `Sensor.TYPE_STEP_COUNTER` — less accurate but always available.

---

### Part 3: UI Integration

#### 3.1 Home Screen Additions

Inserted between the existing "USED TODAY" bar and "ENTERTAINMENT APPS" section:

```
USED TODAY [bar]

--- 🚶 Walking Banner ---
(only visible when WalkState == Walking)
Box with amber background, running icon:
  "Walking detected — 2x time active"
  [Dismiss] text button → removes 2x multiplier, no overlay this session
--- end Walking Banner ---

--- 🎯 Quests Section ---
Section header: "TODAY'S QUESTS"
3 quest cards, each:
  Row:
    Quest icon (DirectionsWalk / Timer / Stars for combo)
    Column:
      Title ("Walk 5,400 steps")
      Progress bar (green fill, 60% = "3,200 / 5,400")
      Reward badge ("+10 min")
  Tap → QuestDetailSheet (modal bottom sheet with history + tips)

--- end Quests Section ---

ENTERTAINMENT APPS
[app list...]
```

#### 3.2 Quest Card States

| State | Appearance |
|---|---|
| Active | Muted bar, live progress number |
| Completed (pending reward) | Gold bar, ✨ "Ready to claim" badge |
| Claimed | Green bar, checkmark, "+15 min" |
| Expired | Grayed out, "Missed" |

#### 3.3 Settings Additions

New section in SettingsScreen:

```
WALK PROTECTION
  [Toggle] Walk detection (on/off)
  [Slider] Time multiplier when walking: 1.5x / 2x / 3x
  
QUESTS
  [Toggle] Enable quests (on/off)
  [Stat] Today's quest earnings: +25 min
  [Stat] Total quest earnings this week: +1h 40m
  [Button] Health Connect settings → opens system Health Connect
```

---

## Files Touched

| File | Change |
|---|---|
| `WalkDetector.kt` | **New** — accelerometer-based step detection |
| `WalkWarningActivity.kt` | **New** — full-screen walk overlay |
| `QuestEntity.kt` | **New** — Room entity for quests |
| `QuestDao.kt` | **New** — DAO for quest CRUD |
| `QuestGenerator.kt` | **New** — quest creation at daily reset |
| `QuestProgressUpdater.kt` | **New** — polls steps + usage, updates quests |
| `AppDatabase.kt` | Modify — add QuestEntity, bump version |
| `ForegroundUsageMonitor.kt` | Modify — observe WalkDetector, apply multiplier |
| `DailyResetManager.kt` | Modify — trigger quest generation, settle discipline quests |
| `TimeBankEngine.kt` | Modify — handle quest reward credits |
| `HomeScreen.kt` | Modify — add walking banner + quest section |
| `SettingsScreen.kt` | Modify — add walk + quest settings |
| `ServiceLocator.kt` | Modify — wire new dependencies |
| `build.gradle.kts` | Modify — add health-connect-client dependency |

## Out of Scope

- Social quests or leaderboards
- Custom user-created quests
- Quest streaks / badges
- GPS-based walking (indoor treadmill walking won't trigger GPS movement)
- Watch companion app (Health Connect handles watch data already)
- Quest notifications when app is in background (foreground progress only)
