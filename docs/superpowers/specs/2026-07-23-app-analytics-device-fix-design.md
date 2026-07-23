# App Detail Analytics Dashboard & Device Section Fix — Design Spec

**Date:** 2026-07-23
**Status:** Approved
**Scope:** AppDetailScreen, ActivityScreen (By Device), Data Layer

---

## Problem Summary

1. **AppDetailScreen** is too basic — stat cards and a 7-day bar chart. No trend indicators, hourly patterns, ranking comparisons, or session analytics.
2. **ActivityScreen "By Device"** hardcodes `"Other Device"` for all non-current devices because `AppUsageSessionEntity` has no `deviceName` column and `SyncEngine.pullUsageSessions` drops the `device_name` field from Supabase.
3. Other-device rows show full stats (fraction bars, etc.) when they should only show app list + durations.

---

## Design

### Part 1: Data Layer Changes

#### 1.1 DB Schema Migration

Add `deviceName` column to `app_usage_sessions`:

```
deviceName: String = ""   // e.g. "Samsung Galaxy S24", "Pixel 9 Pro"
```

Bump Room database version. Provide a migration that adds the column with default `""`.

#### 1.2 Sync Fix

`SyncEngine.pullUsageSessions` — add `deviceName` when inserting pulled sessions:

```kotlin
deviceId = obj.optString("device_id", "unknown"),
deviceName = obj.optString("device_name", ""),  // NEW
```

`ForegroundUsageMonitor` — when creating new sessions, populate `deviceName` from `authManager.deviceName`.

#### 1.3 New DAO Queries (AppUsageSessionDao)

| Query | SQL Shape | Returns |
|---|---|---|
| `getHourlyUsageForApp(packageName, start, end)` | `GROUP BY hour(startedAt)` | `List<HourlyUsage>` (hour: Int, seconds: Long) |
| `getAppSessionStats(packageName, start, end)` | `COUNT, AVG, MAX, MIN` of `durationSeconds` | `AppSessionStats` |
| `getDeviceAppBreakdown(deviceId, start, end)` | `GROUP BY packageName WHERE deviceId = :id` | `List<AppUsageBreakdown>` |
| `getDistinctDevices(start, end)` | `SELECT DISTINCT deviceId, deviceName` | `List<DeviceInfo>` |
| `getWeeklyComparison(packageName, thisWeekStart, thisWeekEnd, lastWeekStart, lastWeekEnd)` | Two queries or one UNION | `WeeklyComparison` (thisWeek: List<DailyUsagePoint>, lastWeek: List<DailyUsagePoint>) |
| `getAllControlledUsageRanked(start, end)` | Existing `getUsageBreakdown` already covers this | Reuse existing |

#### 1.4 Expanded AppDetail Model

```kotlin
data class AppDetail(
    val packageName: String,
    val appName: String,
    val isControlled: Boolean,
    // Today
    val todayUsageSeconds: Long,
    val hourlyUsage: List<HourlySlot>,      // 24 slots (0-23)
    val peakHour: Int,                       // 0-23
    // Sessions
    val sessionCount: Int,
    val avgSessionSeconds: Long,
    val longestSessionSeconds: Long,
    val shortestSessionSeconds: Long,
    // Trends
    val trendVsYesterday: Double,            // e.g. 0.12 = +12%
    val trendVsLastWeek: Double,             // e.g. -0.08 = -8%
    // Weekly
    val weeklyAverageSeconds: Long,
    val weeklyUsage: List<DailyUsagePoint>,
    val lastWeekUsage: List<DailyUsagePoint>, // for overlay comparison
    // Ranking
    val rankAmongControlled: Int,
    val totalControlledApps: Int,
    val percentageOfTotal: Double,
    val percentOfAllowance: Double
)

data class HourlySlot(val hour: Int, val usageSeconds: Long)
```

#### 1.5 New Repository Method

```kotlin
suspend fun getDeviceAppUsage(deviceId: String): List<DeviceAppItem>
// DeviceAppItem(packageName, appName, usageSeconds)
```

Fetches app-level breakdown for a specific device today.

---

### Part 2: AppDetailScreen — Full Analytics Dashboard

Rebuilt into **6 scrollable sections**, each in a distinct styled card:

#### ① App Header
- Real app icon (64dp, rounded), app name, package name
- "Monitored" badge if controlled
- Cleaner, more compact than current

#### ② Today's Snapshot
- **Large timer display** for today's usage (prominent, like HomeScreen balance)
- **Trend pills**: `↑12% vs yesterday` (green) or `↓8% vs yesterday` (red)
- **Trend pills**: `↑5% vs last week` (green) or `↓3% vs last week` (red)
- **Mini stat row**: Sessions · Avg Session · Longest

#### ③ Hourly Heatmap
- 24 horizontal bars (one per hour, 0-23)
- Peak hour(s) highlighted in green, others in muted green/gray
- Label below: "Most active: 8–9 PM"
- Hours with zero usage shown as thin gray lines

#### ④ Weekly Trend
- 7-day bar chart for current week
- Overlay line/dots for last week's values (direct day-by-day comparison)
- Week total card above the chart

#### ⑤ Ranking & Impact
- "Ranked #2 of 5 entertainment apps"
- Progress bar: "Consumes 34% of your daily allowance"
- Mini top-3 ranking list with app names + times

#### ⑥ Session Patterns
- Avg sessions/day
- Session type breakdown: Short (<5m) · Medium (5-30m) · Long (30m+)
- Horizontal stacked bar showing distribution

---

### Part 3: Activity Screen "By Device" Fix

#### 3.1 Device Rows

- **Guard:** Only render the section when `deviceUsageMap.size > 1` (already correct)
- **Real names:** Read `deviceName` from session entities, not hardcoded string
- **Current device row:** Unchanged — full stats, fraction bar, "You" badge
- **Other device rows:** Compact — icon + real device name + total usage time + chevron. No fraction bar, no full stats.

#### 3.2 Device App List Popup

Tapping another device opens a `ModalBottomSheet`:
- Title: device name
- Content: scrollable list of apps
  - Each row: app icon (36dp) + app name + usage duration
- Empty state: "No app usage recorded on this device today"
- Dismiss: swipe down or tap outside

---

### Part 4: UI Polish

- Consistent `RoundedCornerShape(14.dp)` for all cards
- Section headers with icon + label pattern
- Smooth color transitions for trend indicators
- Proper spacing (20dp horizontal padding, 16dp between sections)
- Existing pull-to-refresh and loading skeleton preserved

---

## Files Touched

| File | Change |
|---|---|
| `AppUsageSessionEntity.kt` | Add `deviceName` field |
| `AppDatabase.kt` | Bump version, add migration |
| `AppUsageSessionDao.kt` | 4 new queries |
| `AppRepository.kt` | Expand `AppDetail`, add `getDeviceAppUsage()` |
| `AppDetailScreen.kt` | Full redesign — 6-section dashboard |
| `ActivityScreen.kt` | Fix device names, other-device rows, add popup |
| `DevicesScreen.kt` | Fix hardcoded "Other Device" names |
| `SyncEngine.kt` | Store `device_name` on pull |
| `ForegroundUsageMonitor.kt` | Populate `deviceName` on new sessions |

## Out of Scope

- Backend/Supabase schema changes (device_name already stored)
- Push notification analytics
- Export/share analytics
- Cross-device casino/sports breakdown (screen time only for now)
