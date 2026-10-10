package app.roadstr.roadtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.roadstr.feature.offline.NativeOfflinePackagesGateway
import app.roadstr.service.offline.InstalledOfflinePackage
import app.roadstr.service.offline.OfflineInstallOutcome
import app.roadstr.service.offline.OfflineManifestClient
import app.roadstr.service.offline.OfflineNetworkState
import app.roadstr.service.offline.OfflinePackageArtifact
import app.roadstr.service.offline.OfflinePackageManager
import app.roadstr.service.offline.OfflinePackageManifest

class NativeRoadTestOfflinePackagesGateway(
    context: Context,
    private val manager: OfflinePackageManager,
    private val manifestClient: OfflineManifestClient,
) : NativeOfflinePackagesGateway {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun installed(): List<InstalledOfflinePackage> = manager.installed()

    override fun availableBytes(): Long = manager.availableBytes()

    override suspend fun loadManifest(url: String): OfflinePackageManifest = manifestClient.load(url)

    override suspend fun install(
        artifact: OfflinePackageArtifact,
        allowMobileDataOnce: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): OfflineInstallOutcome = manager.install(
        artifact = artifact,
        network = networkState(),
        allowMobileDataOnce = allowMobileDataOnce,
        onProgress = onProgress,
    )

    override fun delete(packageId: String): Boolean = manager.delete(packageId)

    private fun networkState(): OfflineNetworkState {
        val active = connectivity.activeNetwork
        val capabilities = active?.let(connectivity::getNetworkCapabilities)
        return OfflineNetworkState(
            connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            metered = connectivity.isActiveNetworkMetered,
        )
    }
}
