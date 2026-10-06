package app.roadstr.startup

import android.content.Context
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.feature.onboarding.NativeMigrationReadiness
import app.roadstr.migration.IdentityVerifier
import app.roadstr.migration.MigrationOutcome
import app.roadstr.migration.MigrationResult
import app.roadstr.migration.TransactionalMigration
import app.roadstr.migration.createLegacyHeadlessSnapshotReader
import app.roadstr.roadtest.NativeLiveStoreNames
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The "profile import is done" record, in plain preferences and written last. */
class AndroidProfileImportFlag(context: Context, names: NativeLiveStoreNames) : ProfileImportFlag {
    private val preferences = context.applicationContext.getSharedPreferences(names.prefs("startup"), Context.MODE_PRIVATE)

    override fun isSet(): Boolean = preferences.getBoolean(IMPORT_COMPLETE, false)

    override fun set(): Boolean = preferences.edit().putBoolean(IMPORT_COMPLETE, true).commit()

    private companion object {
        const val IMPORT_COMPLETE = "import_complete"
    }
}

/**
 * Brings the old app's data over once, before the first frame of the new one.
 *
 * It runs on a worker, never touches the old files, and leaves the flag unset on any failure, so
 * the next launch tries again. A phone with nothing from the old app skips the Flutter engine.
 */
class NativeStartupMigration(
    private val migration: TransactionalMigration,
    private val legacyPresent: () -> Boolean,
    private val flag: ProfileImportFlag,
    private val executor: Executor,
) {
    private val mutableReadiness = MutableStateFlow(NativeMigrationReadiness.Checking)
    private val started = AtomicBoolean(false)

    val readiness: StateFlow<NativeMigrationReadiness> = mutableReadiness.asStateFlow()

    fun start() {
        if (flag.isSet()) {
            mutableReadiness.value = NativeMigrationReadiness.Ready
            return
        }
        if (!started.compareAndSet(false, true)) return
        try {
            executor.execute(::run)
        } catch (_: RuntimeException) {
            started.set(false)
            mutableReadiness.value = NativeMigrationReadiness.Failed
        }
    }

    private fun run() {
        mutableReadiness.value = try {
            if (legacyPresent()) after(migration.run()) else freshInstall()
        } catch (_: RuntimeException) {
            NativeMigrationReadiness.Failed
        }
    }

    private fun after(result: MigrationResult): NativeMigrationReadiness = when (result.outcome) {
        MigrationOutcome.Migrated, MigrationOutcome.AlreadyComplete -> NativeMigrationReadiness.Ready
        MigrationOutcome.NotNeeded -> freshInstall()
        MigrationOutcome.Failed -> NativeMigrationReadiness.Failed
    }

    /** Nothing to bring over: record it, so the next launch does not look again. */
    private fun freshInstall(): NativeMigrationReadiness =
        if (flag.set()) NativeMigrationReadiness.Ready else NativeMigrationReadiness.Failed

    companion object {
        private const val READER_TIMEOUT_MILLIS = 30_000L

        fun create(context: Context, names: NativeLiveStoreNames, executor: Executor): NativeStartupMigration {
            val flag = AndroidProfileImportFlag(context, names)
            return NativeStartupMigration(
                migration = TransactionalMigration(
                    reader = createLegacyHeadlessSnapshotReader(context, READER_TIMEOUT_MILLIS),
                    writer = LiveProfileSnapshotWriter(AndroidProfileImportTargets(context, names)),
                    marker = LiveMigrationMarker(flag),
                    identityVerifier = IdentityVerifier { NostrSchnorr.publicKey(it) },
                ),
                legacyPresent = { legacyDataPresent(context) },
                flag = flag,
                executor = executor,
            )
        }

        /**
         * Whether the old Flutter app left anything: its settings box or its secure storage file.
         * Neither is opened here; this only decides whether the Flutter engine is worth starting.
         */
        fun legacyDataPresent(context: Context): Boolean {
            val dataDir = File(context.applicationInfo.dataDir)
            return LEGACY_FILES.any { File(dataDir, it).isFile }
        }

        private val LEGACY_FILES = listOf(
            "app_flutter/settings.hive",
            "shared_prefs/FlutterSecureStorage.xml",
        )
    }
}
