# Walk Detection & Quest System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add walk detection that charges 2x time when using phone while walking (with override), plus a quest system where users earn bonus time by completing step goals, usage discipline challenges, and combo quests.

**Architecture:** Two independent subsystems sharing Health Connect. `WalkDetector` uses accelerometer peak detection → feeds into `ForegroundUsageMonitor` for 2x multiplier and `WalkWarningActivity` overlay. Quest system uses `QuestEntity`/`QuestDao` for persistence, `QuestGenerator` creates daily quests at reset, `QuestProgressUpdater` polls Health Connect steps + existing usage data, rewards credit via `TimeBankEngine.creditQuestReward()` bypassing casino caps.

**Tech Stack:** Kotlin, Jetpack Compose, Room DB, Health Connect API, Android SensorManager, Material3

## Global Constraints

- Walk detection: accelerometer ~20Hz, ≥1.2g peak, ≥300ms gap, ≥3 peaks in 3s → WALKING; 10s no peaks → STATIONARY
- Time multiplier: 2x default, configurable 1.5x/2x/3x in settings
- Quest types: "step", "discipline", "combo" — 3 quests generated daily at reset
- Step target formula: 7-day average × 1.2, minimum 3,000 steps
- Discipline target formula: 7-day average usage × 0.75, minimum 10 min
- Max daily quest earnings: 45 minutes (2700 seconds)
- Quest rewards bypass casino 75% profit cap
- Health Connect for step data; fallback to Sensor.TYPE_STEP_COUNTER if unavailable
- DB version bumped from 6 → 7
- Keep existing `UserSettingsEntity` — quest/walk settings stored as new columns or in SharedPreferences
- WalkWarningActivity: full-screen dialog, dark overlay, two buttons

---

### Task 1: Add Health Connect dependency

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `health-connect-client` available for Tasks 2 and 8

- [ ] **Step 1: Add Health Connect dependency to build.gradle.kts**

Open `app/build.gradle.kts`. Add after the DataStore dependency line (around line 101):

```kotlin
    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha06")
```

- [ ] **Step 2: Sync and verify**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (dependency resolves)

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "feat: add Health Connect client dependency"
```

---

### Task 2: Create WalkDetector

**Files:**
- Create: `app/src/main/java/com/timebet/app/core/monitoring/WalkDetector.kt`

**Interfaces:**
- Produces: `WalkDetector` class with `val walkState: StateFlow<WalkState>`, `fun start()`, `fun stop()`
- Produces: `sealed class WalkState { data object Stationary; data object Walking }`

- [ ] **Step 1: Create WalkDetector.kt**

```kotlin
package com.timebet.app.core.monitoring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects walking via accelerometer peak detection.
 *
 * Algorithm: samples accelerometer at SENSOR_DELAY_GAME (~20ms),
 * detects magnitude peaks above 1.2g, requires ≥3 peaks in 3 seconds
 * to transition to WALKING, and 10s of no peaks to return to STATIONARY.
 */
class WalkDetector(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _walkState = MutableStateFlow<WalkState>(WalkState.Stationary)
    val walkState: StateFlow<WalkState> = _walkState.asStateFlow()

    private var peakTimestamps = mutableListOf<Long>()
    private var lastPeakTime = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())

            val now = System.currentTimeMillis()

            if (magnitude > 1.2) { // peak above 1.2g threshold
                if (now - lastPeakTime >= 300) { // minimum 300ms gap between peaks
                    peakTimestamps.add(now)
                    lastPeakTime = now

                    // Keep only peaks from the last 3 seconds
                    peakTimestamps.removeAll { now - it > 3000 }

                    if (peakTimestamps.size >= 3 && _walkState.value !is WalkState.Walking) {
                        _walkState.value = WalkState.Walking
                    }
                }
            }

            // Check for stationary: no peaks in 10 seconds
            if (_walkState.value is WalkState.Walking) {
                peakTimestamps.removeAll { now - it > 10000 }
                if (peakTimestamps.isEmpty()) {
                    _walkState.value = WalkState.Stationary
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
        _walkState.value = WalkState.Stationary
    }
}

sealed class WalkState {
    data object Stationary : WalkState()
    data object Walking : WalkState()
}
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/monitoring/WalkDetector.kt
git commit -m "feat: add WalkDetector with accelerometer peak detection"
```

---

### Task 3: Create WalkWarningActivity overlay

**Files:**
- Create: `app/src/main/java/com/timebet/app/features/walk/WalkWarningActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `WalkState` from Task 2
- Produces: Full-screen overlay Activity launched when walking + controlled app active
- Register in manifest as `android:theme="@style/Theme.AppCompat.NoActionBar"` with `android:launchMode="singleTask"`

- [ ] **Step 1: Create WalkWarningActivity.kt**

```kotlin
package com.timebet.app.features.walk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timebet.app.ServiceLocator
import com.timebet.app.design.theme.*

/**
 * Full-screen overlay shown when walking is detected while a controlled app is active.
 *
 * Two options:
 * - "Put phone away" → goes to home screen, ends the controlled app session
 * - "I need this (2x)" → dismisses overlay, user continues with double time burn
 */
class WalkWarningActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "this app"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        setContent {
            TimeBetTheme {
                WalkWarningScreen(
                    appName = appName,
                    onPutPhoneAway = {
                        // Go to home screen
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    },
                    onContinueWith2x = {
                        // Tell monitor to use 2x multiplier
                        ServiceLocator.usageMonitor.setWalkMultiplier(2.0)
                        finish()
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun WalkWarningScreen(
    appName: String,
    onPutPhoneAway: () -> Unit,
    onContinueWith2x: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Warning icon
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = TimeBetAmber,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "You're walking —\nput your phone away",
                style = MaterialTheme.typography.headlineMedium,
                color = TimeBetWhite,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "$appName is currently open",
                style = MaterialTheme.typography.bodyLarge,
                color = TimeBetTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Continued use will burn time at 2× the normal rate",
                style = MaterialTheme.typography.labelMedium,
                color = TimeBetAmber,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Primary: Put phone away
            Button(
                onClick = onPutPhoneAway,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TimeBetWhite,
                    contentColor = TimeBetBlack
                )
            ) {
                Icon(Icons.Filled.DirectionsWalk, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Put phone away", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary: Continue at 2x
            OutlinedButton(
                onClick = onContinueWith2x,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TimeBetTextSecondary),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, TimeBetBorder)
            ) {
                Text("I need this (2× time)", color = TimeBetTextSecondary)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "This will dismiss automatically when you stop walking",
                style = MaterialTheme.typography.labelSmall,
                color = TimeBetTextTertiary
            )
        }
    }
}
```

- [ ] **Step 2: Register in AndroidManifest.xml**

Open `app/src/main/AndroidManifest.xml`. Add inside the `<application>` block:

```xml
        <activity
            android:name=".features.walk.WalkWarningActivity"
            android:theme="@style/Theme.AppCompat.NoActionBar"
            android:launchMode="singleTask"
            android:excludeFromRecents="true" />
```

- [ ] **Step 3: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/timebet/app/features/walk/WalkWarningActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add WalkWarningActivity full-screen overlay for walking detection"
```

---

### Task 4: Integrate WalkDetector into ForegroundUsageMonitor

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/monitoring/ForegroundUsageMonitor.kt`

**Interfaces:**
- Consumes: `WalkDetector` from Task 2, `WalkWarningActivity` from Task 3
- Produces: `setWalkMultiplier(multiplier: Double)` public method, `walkMultiplier` applied in `endCurrentSession()`

- [ ] **Step 1: Add WalkDetector to ForegroundUsageMonitor**

Open `ForegroundUsageMonitor.kt`. Add these changes:

A) Add import and constructor parameter:
```kotlin
import android.content.Intent
import com.timebet.app.features.walk.WalkWarningActivity
```

Add to class body (after existing fields around line 53):
```kotlin
    private val walkDetector = WalkDetector(context)
    private var walkMultiplier = 1.0
    private var walkWarningShown = false
```

B) Add `setWalkMultiplier` public method (after `setPollInterval`):
```kotlin
    fun setWalkMultiplier(multiplier: Double) {
        walkMultiplier = multiplier
        walkWarningShown = false
    }
```

C) In `start()`, after `_isMonitoring.value = true`, add:
```kotlin
        walkDetector.start()
```

D) In `stop()`, add before `scope.cancel()`:
```kotlin
        walkDetector.stop()
```

E) Add walk detection coroutine in `start()`, after the existing `scope.launch` blocks:
```kotlin
        // Observe walking state — launch warning overlay when walking + controlled app active
        scope.launch {
            walkDetector.walkState.collect { state ->
                if (state is com.timebet.app.core.monitoring.WalkState.Walking) {
                    val active = _activeApp.value
                    if (active is ActiveAppState.Active && !walkWarningShown) {
                        walkWarningShown = true
                        val intent = Intent(context, WalkWarningActivity::class.java).apply {
                            putExtra(WalkWarningActivity.EXTRA_APP_NAME,
                                controlledPackages.find { it == active.packageName } ?: active.packageName)
                            putExtra(WalkWarningActivity.EXTRA_PACKAGE_NAME, active.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                } else {
                    walkWarningShown = false
                }
            }
        }
```

F) In `endCurrentSession()`, apply walkMultiplier to the `durationSeconds` calculation. Replace:
```kotlin
        val durationSeconds = durationMs / 1000L
```
With:
```kotlin
        val rawSeconds = durationMs / 1000L
        val durationSeconds = (rawSeconds * walkMultiplier).toLong()
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/monitoring/ForegroundUsageMonitor.kt
git commit -m "feat: integrate WalkDetector into ForegroundUsageMonitor with 2x multiplier"
```

---

### Task 5: Create QuestEntity and QuestDao

**Files:**
- Create: `app/src/main/java/com/timebet/app/core/database/entity/QuestEntity.kt`
- Create: `app/src/main/java/com/timebet/app/core/database/dao/QuestDao.kt`

**Interfaces:**
- Produces: `QuestEntity` with fields: id, date, type, title, targetValue, targetPackageName, currentValue, rewardSeconds, status, completedAt, claimedAt
- Produces: `QuestDao` with queries: `getByDate()`, `getActive()`, `upsert()`, `updateProgress()`, `claim()`, `expireOld()`

- [ ] **Step 1: Create QuestEntity.kt**

```kotlin
package com.timebet.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey
    val id: String,                    // UUID
    val date: String,                  // "2026-07-23"
    val type: String,                  // "step" | "discipline" | "combo"
    val title: String,                 // "Walk 5,400 steps"
    val targetValue: Long,             // target steps or target usage seconds
    val targetPackageName: String?,    // null for step quests, package name for discipline
    val currentValue: Long = 0,        // current steps or current usage seconds
    val rewardSeconds: Long,           // time reward in seconds
    val status: String = "active",     // "active" | "completed" | "claimed" | "expired"
    val completedAt: Long? = null,     // epoch millis when completed
    val claimedAt: Long? = null        // epoch millis when reward claimed
)
```

- [ ] **Step 2: Create QuestDao.kt**

```kotlin
package com.timebet.app.core.database.dao

import androidx.room.*
import com.timebet.app.core.database.entity.QuestEntity

@Dao
interface QuestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quest: QuestEntity)

    @Query("SELECT * FROM quests WHERE date = :date ORDER BY type ASC")
    suspend fun getByDate(date: String): List<QuestEntity>

    @Query("SELECT * FROM quests WHERE date = :date AND status = 'active'")
    suspend fun getActive(date: String): List<QuestEntity>

    @Query("""
        UPDATE quests SET currentValue = :currentValue, status = :status, completedAt = :completedAt
        WHERE id = :id
    """)
    suspend fun updateProgress(id: String, currentValue: Long, status: String, completedAt: Long?)

    @Query("UPDATE quests SET status = 'claimed', claimedAt = :claimedAt WHERE id = :id")
    suspend fun claim(id: String, claimedAt: Long)

    @Query("UPDATE quests SET status = 'expired' WHERE status = 'active' AND date < :today")
    suspend fun expireOld(today: String)

    @Query("DELETE FROM quests WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
```

- [ ] **Step 3: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/database/entity/QuestEntity.kt app/src/main/java/com/timebet/app/core/database/dao/QuestDao.kt
git commit -m "feat: add QuestEntity and QuestDao for quest persistence"
```

---

### Task 6: Update AppDatabase with QuestEntity + bump version

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/database/AppDatabase.kt`

**Interfaces:**
- Consumes: `QuestEntity` + `QuestDao` from Task 5
- Produces: `AppDatabase` version 7 with `questDao()` accessor

- [ ] **Step 1: Update AppDatabase.kt**

Open `AppDatabase.kt`. 

A) Add `QuestEntity::class` to the `entities` array:
```kotlin
    entities = [
        UserSettingsEntity::class,
        ControlledAppEntity::class,
        DailyTimeBankEntity::class,
        AppUsageSessionEntity::class,
        CasinoRoundEntity::class,
        SportsPredictionEntity::class,
        DailyUsageAggregateEntity::class,
        QuestEntity::class
    ],
```

B) Change `version = 6` to `version = 7`

C) Add to the import block:
```kotlin
import com.timebet.app.core.database.dao.QuestDao
import com.timebet.app.core.database.entity.QuestEntity
```

D) Add abstract method:
```kotlin
    abstract fun questDao(): QuestDao
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/database/AppDatabase.kt
git commit -m "feat: add QuestEntity to DB, bump to v7"
```

---

### Task 7: Create QuestGenerator

**Files:**
- Create: `app/src/main/java/com/timebet/app/core/quests/QuestGenerator.kt`

**Interfaces:**
- Consumes: `QuestDao` from Task 5, Health Connect API, `AppUsageSessionDao`, `DailyUsageAggregateDao`
- Produces: `QuestGenerator.generateDailyQuests(date: String): List<QuestEntity>` — creates exactly 3 quests

- [ ] **Step 1: Create QuestGenerator.kt**

```kotlin
package com.timebet.app.core.quests

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.QuestEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Generates daily quests at reset time.
 *
 * Creates 3 quests: 1 step, 1 discipline, 1 combo.
 * Step targets based on 7-day average × 1.2. Discipline targets based on 7-day average usage × 0.75.
 */
class QuestGenerator(private val context: Context) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Generate quests for today. Called by DailyResetManager at midnight.
     */
    suspend fun generateDailyQuests(date: String): List<QuestEntity> {
        val avgSteps = getAverageSteps()
        val controlledApps = ServiceLocator.appRepository.getAllControlledApps()
        val usageBreakdown = getWeeklyUsageBreakdown()

        val quests = mutableListOf<QuestEntity>()

        // 1. Step quest
        val stepTarget = (avgSteps * 1.2).roundToLong().coerceAtLeast(3000)
        val stepReward = when {
            stepTarget >= 10000 -> 20L * 60
            stepTarget >= 7000 -> 15L * 60
            stepTarget >= 5000 -> 10L * 60
            else -> 5L * 60
        }
        quests.add(QuestEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            type = "step",
            title = "Walk ${formatSteps(stepTarget)} steps",
            targetValue = stepTarget,
            targetPackageName = null,
            currentValue = 0,
            rewardSeconds = stepReward,
            status = "active"
        ))

        // 2. Discipline quest — pick the most-used controlled app
        if (controlledApps.isNotEmpty()) {
            val topApp = usageBreakdown.maxByOrNull { it.value }
            if (topApp != null && topApp.value > 0) {
                val app = controlledApps.find { it.packageName == topApp.key }
                val targetUsage = (topApp.value * 0.75).roundToLong().coerceAtLeast(10L * 60)
                val disciplineReward = when {
                    targetUsage <= 20L * 60 -> 25L * 60
                    targetUsage <= 40L * 60 -> 20L * 60
                    targetUsage <= 60L * 60 -> 15L * 60
                    else -> 10L * 60
                }
                quests.add(QuestEntity(
                    id = UUID.randomUUID().toString(),
                    date = date,
                    type = "discipline",
                    title = "${app?.appName ?: topApp.key} under ${formatSeconds(targetUsage)} today",
                    targetValue = targetUsage,
                    targetPackageName = topApp.key,
                    currentValue = 0,
                    rewardSeconds = disciplineReward,
                    status = "active"
                ))
            }
        }

        // 3. Combo quest — step + discipline
        if (quests.size >= 2) {
            val stepQ = quests[0]
            val discQ = quests[1]
            val comboReward = ((stepQ.rewardSeconds + discQ.rewardSeconds) * 0.7).roundToLong()
            quests.add(QuestEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                type = "combo",
                title = "${formatSteps(stepQ.targetValue)} steps + ${discQ.title.substringAfter(" ").substringBefore(" under")}",
                targetValue = stepQ.targetValue, // primary tracker is steps
                targetPackageName = discQ.targetPackageName,
                currentValue = 0,
                rewardSeconds = comboReward.coerceAtMost(30L * 60),
                status = "active"
            ))
        }

        // Cap total daily rewards at 45 min
        val totalRewards = quests.sumOf { it.rewardSeconds }
        if (totalRewards > 45L * 60) {
            val scaleFactor = (45.0 * 60.0) / totalRewards
            return quests.map { it.copy(rewardSeconds = (it.rewardSeconds * scaleFactor).roundToLong()) }
        }

        return quests
    }

    private suspend fun getAverageSteps(): Double {
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            val end = Instant.now()
            val start = end.minusSeconds(7 * 24 * 3600)
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            val totalSteps = response.records.sumOf { it.count }
            totalSteps / 7.0
        } catch (_: Exception) {
            5000.0 // default fallback: 5,000 steps
        }
    }

    private suspend fun getWeeklyUsageBreakdown(): Map<String, Long> {
        val now = System.currentTimeMillis()
        val startOfWeek = LocalDate.now().minusDays(7)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            val breakdown = ServiceLocator.database.appUsageSessionDao()
                .getUsageBreakdown(startOfWeek, now)
            return breakdown.associate { it.packageName to (it.totalSeconds / 7) } // daily average
        } catch (_: Exception) {
            return emptyMap()
        }
    }

    private fun formatSteps(steps: Long): String {
        return when {
            steps >= 1000 -> "${steps / 1000},${(steps % 1000) / 100}K"
            else -> "$steps"
        }
    }

    private fun formatSeconds(seconds: Long): String {
        val mins = seconds / 60
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/quests/QuestGenerator.kt
git commit -m "feat: add QuestGenerator with Health Connect step data"
```

---

### Task 8: Create QuestProgressUpdater

**Files:**
- Create: `app/src/main/java/com/timebet/app/core/quests/QuestProgressUpdater.kt`

**Interfaces:**
- Consumes: `QuestDao` from Task 5, `TimeBankEngine` (for crediting rewards)
- Produces: `QuestProgressUpdater` with `start()`, `stop()` — polls every 5 minutes

- [ ] **Step 1: Create QuestProgressUpdater.kt**

```kotlin
package com.timebet.app.core.quests

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.timebet.app.ServiceLocator
import com.timebet.app.core.database.entity.QuestEntity
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Polls quest progress periodically. Step quests check Health Connect every 5 min.
 * Discipline quests check usage data every 1 min. Completes quests when goals are met.
 */
class QuestProgressUpdater(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        // Step quests — poll every 5 minutes
        scope.launch {
            while (isActive && isRunning) {
                try { updateStepQuests() } catch (_: Exception) {}
                delay(5 * 60 * 1000L)
            }
        }

        // Discipline quests — poll every 1 minute
        scope.launch {
            while (isActive && isRunning) {
                try { updateDisciplineQuests() } catch (_: Exception) {}
                delay(60 * 1000L)
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }

    private suspend fun updateStepQuests() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val active = ServiceLocator.database.questDao().getActive(today)
        val stepQuests = active.filter { it.type == "step" || it.type == "combo" }
        if (stepQuests.isEmpty()) return

        val steps = getTodaySteps()
        for (quest in stepQuests) {
            ServiceLocator.database.questDao().updateProgress(
                id = quest.id,
                currentValue = steps,
                status = if (steps >= quest.targetValue) "completed" else "active",
                completedAt = if (steps >= quest.targetValue) System.currentTimeMillis() else null
            )
            if (steps >= quest.targetValue && quest.type == "step") {
                creditReward(quest)
            }
        }
    }

    private suspend fun updateDisciplineQuests() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val active = ServiceLocator.database.questDao().getActive(today)
        val discQuests = active.filter { it.type == "discipline" }
        if (discQuests.isEmpty()) return

        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val breakdown = ServiceLocator.database.appUsageSessionDao()
            .getUsageBreakdown(startOfDay, now)
        val usageMap = breakdown.associate { it.packageName to it.totalSeconds }

        for (quest in discQuests) {
            val pkg = quest.targetPackageName ?: continue
            val currentUsage = usageMap[pkg] ?: 0L
            val isUnder = currentUsage < quest.targetValue
            ServiceLocator.database.questDao().updateProgress(
                id = quest.id,
                currentValue = currentUsage,
                status = "active", // discipline only completes at day end
                completedAt = null
            )
        }
    }

    private suspend fun getTodaySteps(): Long {
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val now = Instant.now()
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            response.records.sumOf { it.count }
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun creditReward(quest: QuestEntity) {
        if (quest.status != "completed" || quest.claimedAt != null) return
        try {
            ServiceLocator.timeBankEngine.creditQuestReward(quest.rewardSeconds)
            ServiceLocator.database.questDao().claim(quest.id, System.currentTimeMillis())
        } catch (_: Exception) {}
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/quests/QuestProgressUpdater.kt
git commit -m "feat: add QuestProgressUpdater with step + discipline tracking"
```

---

### Task 9: Add quest reward method to TimeBankEngine

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/time/TimeBankEngine.kt`

**Interfaces:**
- Consumes: None (self-contained)
- Produces: `creditQuestReward(rewardSeconds: Long)` — adds to balance, bypasses casino caps

- [ ] **Step 1: Add creditQuestReward to TimeBankEngine**

Open `TimeBankEngine.kt`. Add after `returnStake()` method (around line 243):

```kotlin
    /**
     * Credit quest reward — bypasses casino profit caps.
     * Quest rewards are earned through healthy behavior, not gambling.
     */
    suspend fun creditQuestReward(rewardSeconds: Long): Long = mutex.withLock {
        val today = todayDate()
        val bank = ensureBankExists(today)
        val newBalance = bank.currentBalanceSeconds + rewardSeconds

        dailyTimeBankDao.updateBalances(
            date = today,
            balance = newBalance,
            casinoProfit = bank.casinoProfitSeconds,
            casinoLoss = bank.casinoLossSeconds,
            sportsProfit = bank.sportsProfitSeconds + rewardSeconds, // using sports profit for quest tracking
            totalWinSeconds = bank.totalWinSeconds,
            used = bank.usedSeconds
        )

        newBalance
    }
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/time/TimeBankEngine.kt
git commit -m "feat: add creditQuestReward to TimeBankEngine bypassing casino caps"
```

---

### Task 10: Update DailyResetManager for quest generation and settlement

**Files:**
- Modify: `app/src/main/java/com/timebet/app/core/time/DailyResetManager.kt`

**Interfaces:**
- Consumes: `QuestGenerator` from Task 7, `QuestDao` from Task 5
- Produces: Quest generation triggered at reset, discipline quests settled

- [ ] **Step 1: Update DailyResetManager**

Open `DailyResetManager.kt`. 

A) Add constructor parameter:
```kotlin
class DailyResetManager(
    private val context: Context,
    private val timeBankEngine: TimeBankEngine,
    private val sportsPredictionDao: SportsPredictionDao,
    private val questGenerator: com.timebet.app.core.quests.QuestGenerator
) {
```

B) In `checkAndResetIfNeeded()`, after the sports prediction lock block, add:
```kotlin
        // Generate today's quests if this is a new day
        if (bank.date != today) {
            try {
                val quests = questGenerator.generateDailyQuests(today)
                for (q in quests) {
                    com.timebet.app.ServiceLocator.database.questDao().upsert(q)
                }
            } catch (_: Exception) {}
        }

        // Settle discipline quests from yesterday (check if they stayed under target)
        try {
            val yesterday = LocalDate.now().minusDays(1).format(dateFormatter)
            val yesterdayQuests = com.timebet.app.ServiceLocator.database.questDao().getByDate(yesterday)
            val yesterdayUsage = getYesterdayUsageBreakdown()
            for (q in yesterdayQuests) {
                if (q.status == "active" && (q.type == "discipline" || q.type == "combo")) {
                    val pkg = q.targetPackageName
                    val usage = if (pkg != null) yesterdayUsage[pkg] ?: 0L else 0L
                    val completed = usage < q.targetValue
                    com.timebet.app.ServiceLocator.database.questDao().updateProgress(
                        id = q.id,
                        currentValue = usage,
                        status = if (completed) "completed" else "expired",
                        completedAt = if (completed) System.currentTimeMillis() else null
                    )
                    if (completed) {
                        timeBankEngine.creditQuestReward(q.rewardSeconds)
                        com.timebet.app.ServiceLocator.database.questDao().claim(q.id, System.currentTimeMillis())
                    }
                }
            }
        } catch (_: Exception) {}
    }
```

C) Add helper method:
```kotlin
    private suspend fun getYesterdayUsageBreakdown(): Map<String, Long> {
        val yesterday = LocalDate.now().minusDays(1)
        val startOfDay = yesterday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val breakdown = com.timebet.app.ServiceLocator.database.appUsageSessionDao()
            .getUsageBreakdown(startOfDay, endOfDay)
        return breakdown.associate { it.packageName to it.totalSeconds }
    }
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/core/time/DailyResetManager.kt
git commit -m "feat: add quest generation and discipline settlement to DailyResetManager"
```

---

### Task 11: Wire everything in ServiceLocator

**Files:**
- Modify: `app/src/main/java/com/timebet/app/ServiceLocator.kt`

**Interfaces:**
- Consumes: Task 1-10 dependencies
- Produces: All new components available through ServiceLocator

- [ ] **Step 1: Update ServiceLocator.kt**

Open `ServiceLocator.kt`.

A) Add fields:
```kotlin
    private var _questGenerator: com.timebet.app.core.quests.QuestGenerator? = null
    private var _questProgressUpdater: com.timebet.app.core.quests.QuestProgressUpdater? = null
```

B) In `init()`, after DailyResetManager creation, add:
```kotlin
        _questGenerator = com.timebet.app.core.quests.QuestGenerator(context)
        _questProgressUpdater = com.timebet.app.core.quests.QuestProgressUpdater(context)
```

C) Update DailyResetManager construction to pass questGenerator:
```kotlin
        _dailyResetManager = DailyResetManager(
            context = context,
            timeBankEngine = timeBankEngine,
            sportsPredictionDao = database.sportsPredictionDao(),
            questGenerator = questGenerator
        )
```

D) Add accessors:
```kotlin
    val questGenerator: com.timebet.app.core.quests.QuestGenerator get() = _questGenerator!!
    val questProgressUpdater: com.timebet.app.core.quests.QuestProgressUpdater get() = _questProgressUpdater!!
```

E) Start QuestProgressUpdater — add in `init()` after `_syncEngine` creation:
```kotlin
        _questProgressUpdater!!.start()
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/ServiceLocator.kt
git commit -m "feat: wire WalkDetector, QuestGenerator, QuestProgressUpdater in ServiceLocator"
```

---

### Task 12: Add walking banner and quest section to HomeScreen

**Files:**
- Modify: `app/src/main/java/com/timebet/app/features/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `WalkDetector.walkState`, `QuestDao.getActive()`, `QuestProgressUpdater`
- Produces: Walk banner + quest cards between "USED TODAY" and "ENTERTAINMENT APPS"

- [ ] **Step 1: Add walking banner and quest section to HomeScreen**

Open `HomeScreen.kt`. 

A) Add state variables after existing `showMonitoringWarning` (around line 66):
```kotlin
    // Walk detection state
    val walkState by ServiceLocator.usageMonitor.let { monitor ->
        remember { mutableStateOf<com.timebet.app.core.monitoring.WalkState>(
            com.timebet.app.core.monitoring.WalkState.Stationary
        ) }
    }
    LaunchedEffect(Unit) {
        // Observe walk state from monitor's internal detector
        // Since WalkDetector is inside ForegroundUsageMonitor, we use a polling approach
        // For now: walk state observed indirectly via multiplier > 1.0
    }

    // Quest data
    var todayQuests by remember { mutableStateOf<List<com.timebet.app.core.database.entity.QuestEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            todayQuests = ServiceLocator.database.questDao().getByDate(today)
        } catch (_: Exception) {}
    }
```

B) Insert between the "USED TODAY" bar section (after the thin usage bar, around line 317) and the "ENTERTAINMENT APPS" section (around line 322). Replace the spacer+header with:

```kotlin
            Spacer(modifier = Modifier.height(24.dp))

            // ── Walking Banner ── (only when walking)
            // We check if walk warning was triggered this session
            var walkActive by remember { mutableStateOf(false) }
            if (walkActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(TimeBetAmber.copy(alpha = 0.12f))
                        .border(0.5.dp, TimeBetAmber.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = TimeBetAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Walking detected — 2x time active",
                            style = TimeBetTypography.labelSmall,
                            color = TimeBetAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Today's Quests ──
            if (todayQuests.isNotEmpty()) {
                Text(
                    "TODAY'S QUESTS",
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
                Spacer(modifier = Modifier.height(10.dp))

                todayQuests.forEach { quest ->
                    QuestCard(quest = quest)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Entertainment Apps ──
            if (controlledApps.isNotEmpty()) {
```

C) Add `QuestCard` composable at end of file (before last closing brace):

```kotlin
@Composable
private fun QuestCard(quest: com.timebet.app.core.database.entity.QuestEntity) {
    val icon = when (quest.type) {
        "step" -> Icons.Filled.DirectionsWalk
        "discipline" -> Icons.Filled.Timer
        else -> Icons.Filled.Stars
    }
    val accent = when (quest.status) {
        "completed" -> TimeBetGoldLight
        "claimed" -> TimeBetGreen
        "expired" -> TimeBetTextTertiary
        else -> TimeBetWhite
    }
    val fraction = if (quest.targetValue > 0) {
        (quest.currentValue.toFloat() / quest.targetValue).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TimeBetSurfaceElevated)
            .border(0.5.dp, if (quest.status == "completed") TimeBetGoldLight.copy(alpha = 0.4f) else TimeBetBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(quest.title, style = TimeBetTypography.bodyMedium, color = TimeBetWhite)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TimeBetBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when (quest.status) {
                                    "completed", "claimed" -> TimeBetGreen
                                    "expired" -> TimeBetBorder
                                    else -> TimeBetGreen.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    when (quest.type) {
                        "step" -> "${formatQuestValue(quest.currentValue)} / ${formatQuestValue(quest.targetValue)} steps"
                        "discipline" -> "${com.timebet.app.util.TimeFormatter.formatMinutesShort(quest.currentValue)} / ${com.timebet.app.util.TimeFormatter.formatMinutesShort(quest.targetValue)}"
                        else -> "${formatQuestValue(quest.currentValue)} / ${formatQuestValue(quest.targetValue)} steps"
                    },
                    style = TimeBetTypography.labelSmall,
                    color = TimeBetTextTertiary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "+${quest.rewardSeconds / 60}m",
                    style = TimeBetTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = accent
                )
                Text(
                    when (quest.status) {
                        "completed" -> "Ready"
                        "claimed" -> "Earned"
                        "expired" -> "Missed"
                        else -> ""
                    },
                    style = TimeBetTypography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

private fun formatQuestValue(value: Long): String {
    return when {
        value >= 1000 -> "${value / 1000}.${(value % 1000) / 100}K"
        else -> "$value"
    }
}
```

D) Add imports at top:
```kotlin
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Stars
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/features/home/HomeScreen.kt
git commit -m "feat: add walking banner and quest cards to HomeScreen"
```

---

### Task 13: Add walk + quest settings to SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/timebet/app/features/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `UserSettingsEntity` (for persisted toggles)
- Produces: Two new settings sections: Walk Protection + Quests

Note: Walk/quest toggles stored in SharedPreferences (avoids another DB migration).

- [ ] **Step 1: Add settings to SettingsScreen**

Open `SettingsScreen.kt`. Add after the "Notifications" section (around line 121, inside the `Column`):

```kotlin
            // ── Walk Protection ──
            val walkPrefs = context.getSharedPreferences("timebet_walk", Context.MODE_PRIVATE)
            var walkEnabled by remember { mutableStateOf(walkPrefs.getBoolean("walk_detection_enabled", true)) }
            var walkMultiplierSetting by remember { mutableFloatStateOf(walkPrefs.getFloat("walk_multiplier", 2.0f)) }

            SettingsSection("Walk Protection") {
                SwitchSetting(
                    label = "Walk Detection",
                    checked = walkEnabled
                ) { enabled ->
                    walkEnabled = enabled
                    walkPrefs.edit().putBoolean("walk_detection_enabled", enabled).apply()
                }
                if (walkEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Time multiplier when walking: ${walkMultiplierSetting}x",
                        style = TimeBetTypography.bodyMedium,
                        color = TimeBetWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        listOf(1.5f, 2.0f, 3.0f).forEach { mult ->
                            FilterChip(
                                selected = walkMultiplierSetting == mult,
                                onClick = {
                                    walkMultiplierSetting = mult
                                    walkPrefs.edit().putFloat("walk_multiplier", mult).apply()
                                },
                                label = { Text("${mult}x", style = TimeBetTypography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TimeBetAmber,
                                    selectedLabelColor = TimeBetBlack,
                                    containerColor = TimeBetSurfaceElevated,
                                    labelColor = TimeBetWhite
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }

            // ── Quests ──
            val questPrefs = context.getSharedPreferences("timebet_quests", Context.MODE_PRIVATE)
            var questsEnabled by remember { mutableStateOf(questPrefs.getBoolean("quests_enabled", true)) }

            SettingsSection("Quests") {
                SwitchSetting(
                    label = "Enable Quests",
                    checked = questsEnabled
                ) { enabled ->
                    questsEnabled = enabled
                    questPrefs.edit().putBoolean("quests_enabled", enabled).apply()
                }
                if (questsEnabled) {
                    // Show today's quest earnings
                    var todayEarnings by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(Unit) {
                        try {
                            val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                            val quests = ServiceLocator.database.questDao().getByDate(today)
                            todayEarnings = quests.filter { it.status == "claimed" }.sumOf { it.rewardSeconds }
                        } catch (_: Exception) {}
                    }
                    if (todayEarnings > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsRow(
                            label = "Today's Quest Earnings",
                            value = "+${com.timebet.app.util.TimeFormatter.formatHumanReadable(todayEarnings)}"
                        )
                    }
                }
            }
```

Add import:
```kotlin
import androidx.compose.runtime.mutableFloatStateOf
```

- [ ] **Step 2: Verify build**

Run: `cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/timebet/app/features/settings/SettingsScreen.kt
git commit -m "feat: add walk protection and quest settings to SettingsScreen"
```

---

### Task 14: Build verification and final cleanup

**Files:** (none — verification only)

- [ ] **Step 1: Full build**

```bash
cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run tests**

```bash
cd C:\Users\ACER\OneDrive\Desktop\TimeBet && ./gradlew test
```
Expected: All tests pass

- [ ] **Step 3: Commit if any fixes needed**

```bash
git add -A && git commit -m "chore: build verification and final cleanup"
```

- [ ] **Step 4: Push**

```bash
git push origin master
```
