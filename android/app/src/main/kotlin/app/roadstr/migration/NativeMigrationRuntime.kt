package app.roadstr.migration

import android.content.Context
import app.roadstr.storage.AndroidKeystoreNativeSecretStore
import app.roadstr.storage.FileNativeActivityStore
import app.roadstr.storage.FileNativePublicSnapshotStore
import app.roadstr.storage.FileNativeSavedPlacesStore
import app.roadstr.storage.FileNativeSearchHistoryStore
import app.roadstr.storage.FileSnapshotBoundMigrationMarker
import app.roadstr.storage.NativeActivityMigrationStore
import app.roadstr.storage.NativeSecretCommitmentVerifier
import app.roadstr.storage.NativeSecretStore
import app.roadstr.storage.NativeSavedPlacesMigrationStore
import app.roadstr.storage.NativeSearchHistoryMigrationStore
import app.roadstr.storage.NativeStoragePaths

/**
 * Composes the native migration boundary without deciding when startup should
 * invoke it. [run] performs blocking legacy I/O and must be called off the main
 * thread by the future startup owner.
 */
class NativeMigrationRuntime private constructor(
    val paths: NativeStoragePaths,
    private val migration: TransactionalMigration,
) {
    fun run(): MigrationResult = migration.run()

    companion object {
        /** Production composition; it still requires an explicit caller. */
        fun forContext(
            context: Context,
            reader: LegacySnapshotReader,
            identityVerifier: IdentityVerifier,
        ): NativeMigrationRuntime {
            val paths = NativeStoragePaths.from(context)
            val secretStore = AndroidKeystoreNativeSecretStore(paths.rootDirectory)
            val searchHistoryStore = FileNativeSearchHistoryStore(paths)
            val savedPlacesStore = FileNativeSavedPlacesStore(paths)
            val activityStore = FileNativeActivityStore(paths)
            return create(
                paths,
                reader,
                identityVerifier,
                secretStore,
                secretStore,
                searchHistoryStore,
                savedPlacesStore,
                activityStore,
            )
        }

        /**
         * Future convenience boundary for the exact legacy Flutter reader.
         * The headless bridge remains dormant until the startup owner invokes
         * [run] on a worker thread after signed-install evidence is available.
         */
        fun forContextWithFlutterReader(
            context: Context,
            identityVerifier: IdentityVerifier,
            timeoutMillis: Long = 30_000L,
        ): NativeMigrationRuntime = forContext(
            context = context,
            reader = createLegacyHeadlessSnapshotReader(context, timeoutMillis),
            identityVerifier = identityVerifier,
        )

        /** Injectable composition used by host tests and future adapters. */
        fun create(
            paths: NativeStoragePaths,
            reader: LegacySnapshotReader,
            identityVerifier: IdentityVerifier,
            secretStore: NativeSecretStore,
            secretVerifier: NativeSecretCommitmentVerifier,
            searchHistoryStore: NativeSearchHistoryMigrationStore? = null,
            savedPlacesStore: NativeSavedPlacesMigrationStore? = null,
            activityStore: NativeActivityMigrationStore? = null,
        ): NativeMigrationRuntime {
            val publicStore = FileNativePublicSnapshotStore(paths.rootDirectory)
            val marker = FileSnapshotBoundMigrationMarker(
                directory = paths.rootDirectory,
                publicStore = publicStore,
                secretVerifier = secretVerifier,
                searchHistoryVerifier = searchHistoryStore,
                savedPlacesVerifier = savedPlacesStore,
                activityVerifier = activityStore,
            )
            return NativeMigrationRuntime(
                paths = paths,
                migration = TransactionalMigration(
                    reader = reader,
                    writer = app.roadstr.storage.CompositeNativeSnapshotWriter(
                        publicStore = publicStore,
                        secretStore = secretStore,
                        searchHistoryStore = searchHistoryStore,
                        savedPlacesStore = savedPlacesStore,
                        activityStore = activityStore,
                    ),
                    marker = marker,
                    identityVerifier = identityVerifier,
                ),
            )
        }
    }
}
