package app.roadstr.service.routing

import android.content.Context
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.network.RoutingRequestProtocol
import app.roadstr.core.network.RoutingResponseException
import app.roadstr.core.network.RoutingResponseProtocol
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.feature.route.NativeRouteTransportMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.valhalla.config.ValhallaConfigBuilder
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigManager
import com.valhalla.valhalla.files.ValhallaFile
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

interface LocalValhallaSession : Closeable {
    fun routeRaw(requestJson: String): String
}

fun interface LocalValhallaSessionFactory {
    fun open(dataset: LocalRoutingDataset): LocalValhallaSession
}

class AndroidLocalValhallaSessionFactory(
    context: Context,
    private val configDirectory: File = File(context.filesDir, "offline-routing/config"),
) : LocalValhallaSessionFactory {
    private val appContext = context.applicationContext

    override fun open(dataset: LocalRoutingDataset): LocalValhallaSession {
        require(DATASET_ID.matches(dataset.id)) { "Invalid local routing dataset id" }
        require(dataset.version > 0) { "Invalid local routing dataset version" }
        val extract = File(dataset.tileExtractPath)
        if (!extract.isFile || extract.length() == 0L) throw IOException("Local routing dataset is missing")
        if (!configDirectory.exists() && !configDirectory.mkdirs()) {
            throw IOException("Local routing configuration directory is unavailable")
        }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val configFile = ValhallaFile(
            appContext,
            "${dataset.id}-${dataset.version}.json",
            configDirectory,
        )
        val manager = ValhallaConfigManager(appContext, configFile, moshi)
        val config = ValhallaConfigBuilder().withTileExtract(extract.absolutePath).build()
        val valhalla = Valhalla(appContext, config, manager, moshi)
        return object : LocalValhallaSession {
            override fun routeRaw(requestJson: String): String = valhalla.routeRaw(requestJson)
            override fun close() = valhalla.close()
        }
    }

    private companion object {
        val DATASET_ID = Regex("[a-z0-9][a-z0-9._-]{0,95}")
    }
}

class ValhallaLocalRoutingEngine(
    private val sessions: LocalValhallaSessionFactory,
) : RoutingEngine, Closeable {
    override val id: String = "valhalla-local"
    private val sessionLock = Any()
    private val openSessions = linkedMapOf<String, SessionEntry>()

    override suspend fun route(request: RoutingEngineRequest): RoutingEngineOutcome {
        if (request.mode != NativeRouteTransportMode.Driving) {
            return RoutingEngineOutcome.Unavailable(RoutingEngineUnavailableReason.ModeUnsupported)
        }
        val dataset = request.localDataset
            ?: return RoutingEngineOutcome.Unavailable(RoutingEngineUnavailableReason.DatasetMissing)
        val json = runCatching { requestJson(request) }.getOrElse {
            return RoutingEngineOutcome.Failed(RoutingEngineFailureKind.InvalidRequest)
        }
        return withContext(Dispatchers.IO) {
            coroutineContext.ensureActive()
            val raw = try {
                synchronized(sessionLock) { session(dataset).routeRaw(json) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                return@withContext RoutingEngineOutcome.Unavailable(
                    RoutingEngineUnavailableReason.DatasetInvalid,
                )
            } catch (_: Exception) {
                return@withContext RoutingEngineOutcome.Failed(RoutingEngineFailureKind.EngineFailure)
            }
            coroutineContext.ensureActive()
            parse(raw)
        }
    }

    fun closeDataset(datasetId: String) {
        synchronized(sessionLock) { openSessions.remove(datasetId)?.session?.close() }
    }

    override fun close() {
        val entries = synchronized(sessionLock) {
            openSessions.values.toList().also { openSessions.clear() }
        }
        entries.forEach { runCatching { it.session.close() } }
    }

    private fun session(dataset: LocalRoutingDataset): LocalValhallaSession {
        val current = openSessions[dataset.id]
        if (current?.dataset == dataset) return current.session
        current?.session?.close()
        return sessions.open(dataset).also { opened ->
            openSessions[dataset.id] = SessionEntry(dataset, opened)
        }
    }

    private fun parse(raw: String): RoutingEngineOutcome {
        val errorCode = responseErrorCode(raw)
        if (errorCode == AREA_NOT_DOWNLOADED_CODE) {
            return RoutingEngineOutcome.Unavailable(RoutingEngineUnavailableReason.AreaNotDownloaded)
        }
        if (errorCode != null) return RoutingEngineOutcome.Failed(RoutingEngineFailureKind.InvalidRequest)
        return try {
            RoutingEngineOutcome.Success(listOf(RoutingResponseProtocol.parseValhalla(raw).route))
        } catch (_: RoutingResponseException) {
            RoutingEngineOutcome.Failed(RoutingEngineFailureKind.InvalidResponse)
        }
    }

    private fun responseErrorCode(raw: String): Int? {
        val root = runCatching { BoundedJsonParser(raw).parse() as? Map<*, *> }.getOrNull()
            ?: return null
        return (root["error_code"] as? Number)?.toInt()
            ?: (root["code"] as? Number)?.toInt()
    }

    private fun requestJson(request: RoutingEngineRequest): String {
        require(request.via.size <= MAX_VIA) { "Too many intermediate stops" }
        val points = listOf(request.origin) + request.via + request.destination
        points.forEach(::validatePoint)
        return buildString {
            append("{\"locations\":[")
            points.forEachIndexed { index, point ->
                if (index > 0) append(',')
                append("{\"lat\":")
                append(point.latitude)
                append(",\"lon\":")
                append(point.longitude)
                append('}')
            }
            append("],\"costing\":\"auto\"")
            avoidanceOptions(request.avoidance)?.let { options ->
                append(",\"costing_options\":{\"auto\":{")
                append(options)
                append("}}")
            }
            append(",\"units\":\"kilometers\",\"language\":\"")
            append(RoutingRequestProtocol.valhallaLanguage(request.languageCode))
            append("\"}")
        }
    }

    private fun validatePoint(point: RoutingRequestPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) { "Invalid latitude" }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) { "Invalid longitude" }
    }

    private fun avoidanceOptions(avoidance: RoutingRouteAvoidance): String? = when (avoidance) {
        RoutingRouteAvoidance.None -> null
        RoutingRouteAvoidance.HighwayAndTollFree -> "\"exclude_highways\":true,\"exclude_tolls\":true"
        RoutingRouteAvoidance.MinimizedHighwaysAndTolls -> "\"use_highways\":0.1,\"use_tolls\":0.1"
        RoutingRouteAvoidance.OffRoadAvoided -> "\"use_tracks\":0"
    }

    private data class SessionEntry(
        val dataset: LocalRoutingDataset,
        val session: LocalValhallaSession,
    )

    private companion object {
        const val AREA_NOT_DOWNLOADED_CODE = 171
        const val MAX_VIA = 8
    }
}
