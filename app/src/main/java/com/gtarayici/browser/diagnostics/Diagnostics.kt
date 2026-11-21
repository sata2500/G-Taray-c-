package com.gtarayici.browser.diagnostics

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Genel tanılama kaydı: uygulama olaylarının (hata, bağlantı, indirme, resim içinde resim…) zaman çizelgesi.
 *
 * - Yalnızca cihazda tutulur; kullanıcı "Sorun bildir" ile paylaşmadıkça hiçbir yere gönderilmez.
 * - Her satır [PrivacyFilter] ile maskelenir (adreslerde yalnızca alan adı, e-posta/IP/kimlik gizli).
 * - Son [MAX_LINES] satır bellekte halka tampon olarak tutulur ve [file] dosyasına yazılır; uygulama yeniden
 *   açıldığında (çökme sonrası dahil) önceki oturumun kaydı kaybolmaz.
 * - Art arda gelen aynı ileti tek satırda birleştirilir ("(×3)"), böylece yeniden çizilen ekranlar kaydı doldurmaz.
 */
object Diagnostics {
    const val MAX_LINES = 500

    private data class Entry(val time: Long, val category: String, val message: String, val repeats: Int = 1)

    private val entries = ArrayDeque<Entry>()
    private val writer = Executors.newSingleThreadScheduledExecutor { Thread(it, "tanilama-kaydi").apply { isDaemon = true } }
    private var file: File? = null
    private var enabled: () -> Boolean = { true }
    private var saveScheduled = false

    /** Uygulama açılışında bir kez çağrılır: önceki kaydı dosyadan yükler. */
    @Synchronized
    fun init(file: File, enabled: () -> Boolean) {
        this.file = file
        this.enabled = enabled
        // Dosyadaki önceki oturum önce, bu oturumda başlatmadan önce yazılanlar sonra gelir.
        val previous = runCatching { if (file.exists()) file.readLines().mapNotNull(::parse) else emptyList() }.getOrDefault(emptyList())
        val current = entries.toList()
        entries.clear()
        (previous + current).takeLast(MAX_LINES).forEach(entries::addLast)
    }

    fun log(message: String, category: String = "genel") = add(category, message)

    /** Resim içinde resim adımları (cihaza bağlı davranışları ayırt etmek için ayrı kategori). */
    fun pip(message: String) = add("pip", message)

    /** Hata: istisna sınıfı ve iletisi (nedenleriyle) kaydedilir. */
    fun error(category: String, error: Throwable, context: String? = null) =
        add(category, listOfNotNull(context, describe(error)).joinToString(": "))

    @Synchronized
    private fun add(category: String, message: String) {
        if (!enabled()) return
        val masked = PrivacyFilter.mask(message.replace('\n', ' ').trim()).take(MAX_MESSAGE)
        val last = entries.lastOrNull()
        if (last != null && last.category == category && last.message == masked) {
            entries.removeLast()
            entries.addLast(last.copy(time = System.currentTimeMillis(), repeats = last.repeats + 1))
        } else {
            entries.addLast(Entry(System.currentTimeMillis(), category, masked))
            while (entries.size > MAX_LINES) entries.removeFirst()
        }
        scheduleSave()
    }

    /** Kaydın okunur metni (en eski üstte). [limit] verilirse yalnızca son satırlar. */
    @Synchronized
    fun dump(limit: Int = MAX_LINES): String = entries.toList().takeLast(limit).joinToString("\n", transform = ::format)

    @Synchronized
    fun lineCount(): Int = entries.size

    /** Kaydı siler ve silinen satırları döner (ör. "Geri al" için). */
    @Synchronized
    fun clear(): List<String> {
        val removed = entries.map(::format)
        entries.clear()
        scheduleSave()
        return removed
    }

    /** [clear] ile silinen satırları geri yükler. */
    @Synchronized
    fun restore(lines: List<String>) {
        val current = entries.toList()
        entries.clear()
        (lines.mapNotNull(::parse) + current).takeLast(MAX_LINES).forEach(entries::addLast)
        scheduleSave()
    }

    /** Çökme anında (iş parçacığı beklemeden) kaydı diske yazar. */
    @Synchronized
    fun flush() = save()

    private fun scheduleSave() {
        if (file == null || saveScheduled) return
        saveScheduled = true
        writer.schedule({
            synchronized(this) {
                saveScheduled = false
                save()
            }
        }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun save() {
        val target = file ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(entries.joinToString("\n", transform = ::format) + if (entries.isEmpty()) "" else "\n")
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
    }

    internal fun describe(error: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && parts.size < 4) {
            parts += listOfNotNull(current.javaClass.simpleName, current.message?.takeIf { it.isNotBlank() }).joinToString(": ")
            current = current.cause?.takeIf { it !== current }
        }
        return parts.joinToString(" ← ")
    }

    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private fun format(entry: Entry): String {
        val repeat = if (entry.repeats > 1) " (×${entry.repeats})" else ""
        return "${timeFormat.get()!!.format(Date(entry.time))} [${entry.category}] ${entry.message}$repeat"
    }

    private val LINE = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[([^\]]+)] (.*?)(?: \(×(\d+)\))?$""")

    private fun parse(line: String): Entry? {
        val match = LINE.find(line) ?: return null
        val (time, category, message, repeats) = match.destructured
        val millis = runCatching { timeFormat.get()!!.parse(time)!!.time }.getOrNull() ?: return null
        return Entry(millis, category, message, repeats.toIntOrNull() ?: 1)
    }

    private const val MAX_MESSAGE = 600
    private const val SAVE_DELAY_MS = 2_000L
}
