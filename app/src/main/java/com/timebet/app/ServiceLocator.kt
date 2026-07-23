package com.timebet.app

import android.content.Context
import com.timebet.app.core.auth.AuthManager
import com.timebet.app.core.blocking.AppBlockController
import com.timebet.app.core.database.AppDatabase
import com.timebet.app.core.monitoring.ForegroundUsageMonitor
import com.timebet.app.core.permissions.PermissionHealthMonitor
import com.timebet.app.core.sync.SupabaseSyncManager
import com.timebet.app.core.sync.SyncEngine
import com.timebet.app.core.time.*
import com.timebet.app.data.repositories.AppRepository
import com.timebet.app.data.repositories.TimeBankRepository
import com.timebet.app.util.TimeBetConstants

/**
 * Simple service locator for dependency injection.
 *
 * In a production app, this would be replaced by Hilt/Koin.
 * For MVP, manual DI keeps things simple and testable.
 */
object ServiceLocator {

    private var _database: AppDatabase? = null
    private var _timeBankEngine: TimeBankEngine? = null
    private var _timeBankRepository: TimeBankRepository? = null
    private var _appRepository: AppRepository? = null
    private var _permissionMonitor: PermissionHealthMonitor? = null
    private var _usageMonitor: ForegroundUsageMonitor? = null
    private var _blockController: AppBlockController? = null
    private var _coinFlipEngine: CoinFlipEngine? = null
    private var _minesEngine: MinesEngine? = null
    private var _rouletteEngine: RouletteEngine? = null
    private var _blackjackEngine: BlackjackEngine? = null
    private var _crashEngine: CrashEngine? = null
    private var _baccaratEngine: BaccaratEngine? = null
    private var _chickenEngine: ChickenEngine? = null
    private var _questGenerator: com.timebet.app.core.quests.QuestGenerator? = null
    private var _dailyResetManager: DailyResetManager? = null
    private var _supabaseSync: SupabaseSyncManager? = null
    private var _authManager: AuthManager? = null
    private var _syncEngine: SyncEngine? = null

    fun init(context: Context) {
        val app = context.applicationContext as TimeBetApp
        _database = app.database

        _coinFlipEngine = CoinFlipEngine()
        _minesEngine = MinesEngine()
        _rouletteEngine = RouletteEngine()
        _blackjackEngine = BlackjackEngine()
        _crashEngine = CrashEngine()
        _baccaratEngine = BaccaratEngine()
        _chickenEngine = ChickenEngine()

        _timeBankEngine = TimeBankEngine(
            dailyTimeBankDao = database.dailyTimeBankDao()
        ) {
            database.userSettingsDao().get()?.baseDailyAllowanceSeconds
                ?: TimeBetConstants.DEFAULT_BASE_ALLOWANCE_SECONDS
        }

        _timeBankRepository = TimeBankRepository(
            dailyTimeBankDao = database.dailyTimeBankDao(),
            userSettingsDao = database.userSettingsDao(),
            casinoRoundDao = database.casinoRoundDao(),
            sportsPredictionDao = database.sportsPredictionDao(),
            dailyUsageAggregateDao = database.dailyUsageAggregateDao(),
            timeBankEngine = timeBankEngine,
            coinFlipEngine = coinFlipEngine,
            minesEngine = minesEngine,
            rouletteEngine = rouletteEngine,
            blackjackEngine = blackjackEngine,
            crashEngine = crashEngine,
            baccaratEngine = baccaratEngine,
            chickenEngine = chickenEngine
        )

        _appRepository = AppRepository(
            context = context,
            controlledAppDao = database.controlledAppDao(),
            appUsageSessionDao = database.appUsageSessionDao(),
            dailyUsageAggregateDao = database.dailyUsageAggregateDao(),
            userSettingsDao = database.userSettingsDao()
        )

        _permissionMonitor = PermissionHealthMonitor(context)

        _usageMonitor = ForegroundUsageMonitor(
            context = context,
            controlledAppDao = database.controlledAppDao(),
            appUsageSessionDao = database.appUsageSessionDao(),
            timeBankEngine = timeBankEngine,
            permissionMonitor = permissionMonitor
        )

        _blockController = AppBlockController(
            context = context,
            timeBankEngine = timeBankEngine
        )

        _questGenerator = com.timebet.app.core.quests.QuestGenerator(context)

        _dailyResetManager = DailyResetManager(
            context = context,
            timeBankEngine = timeBankEngine,
            sportsPredictionDao = database.sportsPredictionDao(),
            questGenerator = _questGenerator!!
        )

        _supabaseSync = SupabaseSyncManager(context)
        _authManager = AuthManager(context)
        _syncEngine = SyncEngine(context, authManager, database)
    }

    val database: AppDatabase get() = _database!!
    val timeBankEngine: TimeBankEngine get() = _timeBankEngine!!
    val timeBankRepository: TimeBankRepository get() = _timeBankRepository!!
    val appRepository: AppRepository get() = _appRepository!!
    val permissionMonitor: PermissionHealthMonitor get() = _permissionMonitor!!
    val usageMonitor: ForegroundUsageMonitor get() = _usageMonitor!!
    val blockController: AppBlockController get() = _blockController!!
    val coinFlipEngine: CoinFlipEngine get() = _coinFlipEngine!!
    val minesEngine: MinesEngine get() = _minesEngine!!
    val rouletteEngine: RouletteEngine get() = _rouletteEngine!!
    val blackjackEngine: BlackjackEngine get() = _blackjackEngine!!
    val crashEngine: CrashEngine get() = _crashEngine!!
    val baccaratEngine: BaccaratEngine get() = _baccaratEngine!!
    val chickenEngine: ChickenEngine get() = _chickenEngine!!
    val questGenerator: com.timebet.app.core.quests.QuestGenerator get() = _questGenerator!!
    val dailyResetManager: DailyResetManager get() = _dailyResetManager!!
    val supabaseSync: SupabaseSyncManager get() = _supabaseSync!!
    val authManager: AuthManager get() = _authManager!!
    val syncEngine: SyncEngine get() = _syncEngine!!
    val sportsPredictionDao get() = database.sportsPredictionDao()
}
