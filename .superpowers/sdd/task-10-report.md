# Task 10 Report: Update DailyResetManager for quest generation and settlement

## Changes Made

### 1. `DailyResetManager.kt`
- **Added** `QuestGenerator` constructor parameter
- **Added** quest generation for the new day in `checkAndResetIfNeeded()` — generates daily quests via `questGenerator.generateDailyQuests(today)` and persists them through `ServiceLocator.database.questDao().upsert()` when the date has changed
- **Added** settlement of yesterday's discipline/combo quests — checks if the user stayed under the usage target, marks quests as completed/expired, and credits rewards via `timeBankEngine.creditQuestReward()` when completed
- **Added** `getYesterdayUsageBreakdown()` private helper that queries `AppUsageSessionDao.getUsageBreakdown()` for yesterday's controlled app usage

### 2. `ServiceLocator.kt`
- **Added** `questGenerator` field (private + public accessor)
- **Initialized** `QuestGenerator(context)` during `init()`
- **Passed** `questGenerator` to `DailyResetManager` constructor

## Files Modified
- `app/src/main/java/com/timebet/app/core/time/DailyResetManager.kt`
- `app/src/main/java/com/timebet/app/ServiceLocator.kt`

## Build Result
`./gradlew assembleDebug` — **BUILD SUCCESSFUL**
