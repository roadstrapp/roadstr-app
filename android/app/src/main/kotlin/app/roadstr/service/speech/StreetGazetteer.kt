package app.roadstr.service.speech

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.speech.StreetIndex
import app.roadstr.service.hazards.NativeOverpassClient
import java.io.File
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.floor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The street names of the area around the user, so that what a speech model heard can be matched with
 * what the map calls things.
 *
 * It asks Overpass once per area and keeps the answer on the phone for a month. The area is a cell of a
 * fixed grid (about 5 km), and the request names the cell's centre, not the user's position, so the
 * server learns the neighbourhood and never the exact spot. If the answer cannot be had, voice search
 * works as it did before: nothing here is ever required.
 */
class StreetGazetteer(
    private val overpass: NativeOverpassClient,
    private val cacheDirectory: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Cell(val latitudeIndex: Int, val longitudeIndex: Int) {
        val centre: GeoPoint
            get() = GeoPoint((latitudeIndex + 0.5) * CELL_DEGREES, (longitudeIndex + 0.5) * CELL_DEGREES)
        val fileName: String get() = "street-names-$latitudeIndex-$longitudeIndex.txt"
    }

    private val loadLock = Mutex()
    private val recent = LinkedHashMap<Cell, StreetIndex>()
    private var retryNotBefore = 0L

    fun cellOf(point: GeoPoint) = Cell(
        floor(point.latitude / CELL_DEGREES).toInt(),
        floor(point.longitude / CELL_DEGREES).toInt(),
    )

    /** The street names for [point] if they are already on the phone and fresh; never touches the network. */
    @Synchronized
    fun cached(point: GeoPoint): StreetIndex? {
        val cell = cellOf(point)
        recent[cell]?.let { return it }
        val names = readFile(cell) ?: return null
        return remember(cell, StreetIndex(names))
    }

    /** The street names for [point], fetched if they are not on the phone. Null if they cannot be had now. */
    suspend fun load(point: GeoPoint): StreetIndex? {
        cached(point)?.let { return it }
        return loadLock.withLock {
            cached(point) ?: fetch(cellOf(point))
        }
    }

    private suspend fun fetch(cell: Cell): StreetIndex? {
        if (clock() < retryNotBefore) return null
        var lastError: Exception? = null
        repeat(overpass.mirrorCount) {
            try {
                val text = overpass.fetchText(query(cell), MAX_BYTES, TIMEOUT_MILLIS)
                val names = parse(text)
                overpass.noteSuccess()
                writeFile(cell, names)
                synchronized(this) { return remember(cell, StreetIndex(names)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
                overpass.rotate()
            }
        }
        overpass.noteFailure(lastError)
        retryNotBefore = clock() + overpass.failureBackoff().toMillis()
        return null
    }

    internal fun query(cell: Cell): String {
        val centre = cell.centre
        val around = "(around:$RADIUS_METERS,${overpass.coord(centre.latitude)},${overpass.coord(centre.longitude)})"
        return "[out:csv(\"name\";false)][timeout:25];" +
            "way$around[\"highway\"][\"name\"]" +
            "[\"highway\"!~\"^($EXCLUDED_HIGHWAYS)$\"];out;"
    }

    private fun remember(cell: Cell, index: StreetIndex): StreetIndex {
        recent[cell] = index
        while (recent.size > RECENT_CELLS) recent.remove(recent.keys.first())
        return index
    }

    private fun readFile(cell: Cell): List<String>? {
        val file = File(cacheDirectory, cell.fileName)
        val lines = runCatching { if (file.isFile) file.readLines() else null }.getOrNull() ?: return null
        val written = lines.firstOrNull()?.removePrefix(HEADER)?.toLongOrNull() ?: return null
        if (clock() - written > MAX_AGE_MILLIS || written > clock() + MAX_AGE_MILLIS) return null
        return lines.drop(1).takeIf { it.isNotEmpty() }
    }

    private fun writeFile(cell: Cell, names: List<String>) {
        if (names.isEmpty()) return
        runCatching {
            cacheDirectory.mkdirs()
            File(cacheDirectory, cell.fileName).writeText(HEADER + clock() + "\n" + names.joinToString("\n"))
            pruneOldFiles()
        }
    }

    private fun pruneOldFiles() {
        val files = cacheDirectory.listFiles { file -> file.name.startsWith("street-names-") } ?: return
        files.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }

    companion object {
        const val CELL_DEGREES = 0.05
        const val RADIUS_METERS = 7_000
        private const val HEADER = "v1 "
        private const val MAX_BYTES = 3_000_000L
        private const val TIMEOUT_MILLIS = 20_000L
        private const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_FILES = 12
        private const val RECENT_CELLS = 3
        private const val MAX_NAMES = 25_000
        private const val EXCLUDED_HIGHWAYS =
            "service|track|path|footway|steps|cycleway|bridleway|corridor|proposed|construction|platform|bus_stop|elevator|raceway"

        /** One name per line, as `out csv` writes it; duplicates (one per way) and rubbish removed. */
        fun parse(csv: String): List<String> {
            val names = LinkedHashSet<String>()
            for (line in csv.lineSequence()) {
                val name = line.substringBefore('\t').trim()
                if (name.length !in 4..70 || name.any { it.isISOControl() }) continue
                names.add(name)
                if (names.size >= MAX_NAMES) break
            }
            return names.toList()
        }
    }
}
