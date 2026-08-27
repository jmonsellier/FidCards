package com.monselio.fidcards.backup

import android.content.Context
import android.net.Uri
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ImportMode { MERGE, REPLACE }

sealed interface BackupResult {
    data class ExportOk(val cards: Int) : BackupResult
    data class ImportOk(val imported: Int, val skipped: Int) : BackupResult
    /** schemaVersion inconnue : refus explicite plutôt qu'un import silencieusement faux (§8). */
    data object UnknownVersion : BackupResult
    data object Failed : BackupResult
}

/**
 * Export / import ZIP via le Storage Access Framework — l'unique chemin de
 * migration entre téléphones, Auto Backup étant désactivé (docs/PROJET.md §8).
 *
 * Structure : cards.json (schemaVersion + cartes) + images/ (logos et photos).
 */
class BackupManager(
    private val context: Context,
    private val repository: CardRepository,
) {

    /** Répertoire local des images de cartes (logos choisis, photos recto/verso). */
    val imagesDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val cards = repository.getAll()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    val entries = mutableMapOf<String, File>()
                    val json = JSONObject().apply {
                        put("schemaVersion", SCHEMA_VERSION)
                        put("exportedAt", System.currentTimeMillis())
                        put("cards", JSONArray().apply { cards.forEach { put(it.toJson(entries)) } })
                    }
                    zip.putNextEntry(ZipEntry("cards.json"))
                    zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    entries.forEach { (name, file) ->
                        zip.putNextEntry(ZipEntry("images/$name"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: return@withContext BackupResult.Failed
            BackupResult.ExportOk(cards.size)
        } catch (_: Exception) {
            BackupResult.Failed
        }
    }

    suspend fun import(uri: Uri, mode: ImportMode): BackupResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var cardsJson: String? = null
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "cards.json" ->
                                cardsJson = zip.readBytes().toString(Charsets.UTF_8)
                            !entry.isDirectory && entry.name.startsWith("images/") -> {
                                // Nom d'entrée non fiable (zip slip) : on ne garde que le nom de base.
                                val safeName = File(entry.name).name
                                File(tempDir, safeName).outputStream().use { zip.copyTo(it) }
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext BackupResult.Failed

            val root = JSONObject(cardsJson ?: return@withContext BackupResult.Failed)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                return@withContext BackupResult.UnknownVersion
            }

            if (mode == ImportMode.REPLACE) {
                repository.getAll().forEach { repository.delete(it) }
                imagesDir.listFiles()?.forEach { it.delete() }
            }

            // Clé de déduplication : barcodeValue + barcodeFormat (§8).
            val existingKeys = repository.getAll()
                .map { it.barcodeValue to it.barcodeFormat }
                .toMutableSet()

            val array = root.getJSONArray("cards")
            var imported = 0
            var skipped = 0
            for (i in 0 until array.length()) {
                val card = cardFromJson(array.getJSONObject(i), tempDir)
                if (card == null) {
                    skipped++
                    continue
                }
                val key = card.barcodeValue to card.barcodeFormat
                if (key in existingKeys) {
                    skipped++
                } else {
                    repository.insert(card)
                    existingKeys += key
                    imported++
                }
            }
            BackupResult.ImportOk(imported, skipped)
        } catch (_: Exception) {
            BackupResult.Failed
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Sérialise la carte ; les images référencées sont collectées dans [imageEntries]. */
    private fun Card.toJson(imageEntries: MutableMap<String, File>): JSONObject {
        fun register(path: String?): String? {
            val file = path?.let(::File)?.takeIf { it.isFile } ?: return null
            var name = file.name
            // Collision de noms entre répertoires différents : suffixe déterministe.
            while (imageEntries[name]?.let { it != file } == true) {
                name = "_$name"
            }
            imageEntries[name] = file
            return name
        }
        return JSONObject().apply {
            put("name", name)
            put("barcodeValue", barcodeValue)
            putOpt("displayValue", displayValue)
            put("barcodeFormat", barcodeFormat.name)
            putOpt("brandId", brandId)
            putOpt("colorArgb", colorArgb)
            putOpt("logo", register(logoPath))
            putOpt("frontImage", register(frontImagePath))
            putOpt("backImage", register(backImagePath))
            put("createdAt", createdAt)
            putOpt("lastUsedAt", lastUsedAt)
            put("useCount", useCount)
            put("score", score)
        }
    }

    /** Restaure une carte ; renvoie null si une donnée obligatoire est invalide. */
    private fun cardFromJson(o: JSONObject, extractedDir: File): Card? {
        val format = runCatching {
            BarcodeFormat.valueOf(o.getString("barcodeFormat"))
        }.getOrNull() ?: return null
        val value = o.optString("barcodeValue").takeIf { it.isNotEmpty() } ?: return null
        val name = o.optString("name").takeIf { it.isNotEmpty() } ?: return null

        fun restore(entryName: String?): String? {
            val source = entryName?.let { File(extractedDir, File(it).name) }
                ?.takeIf { it.isFile } ?: return null
            val target = File(imagesDir, "${UUID.randomUUID()}-${source.name}")
            source.copyTo(target, overwrite = true)
            return target.absolutePath
        }

        return Card(
            name = name,
            barcodeValue = value,
            displayValue = o.optString("displayValue").takeIf { it.isNotEmpty() },
            barcodeFormat = format,
            brandId = o.optString("brandId").takeIf { it.isNotEmpty() },
            colorArgb = if (o.has("colorArgb")) o.optInt("colorArgb") else null,
            logoPath = restore(o.optString("logo").takeIf { it.isNotEmpty() }),
            frontImagePath = restore(o.optString("frontImage").takeIf { it.isNotEmpty() }),
            backImagePath = restore(o.optString("backImage").takeIf { it.isNotEmpty() }),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            lastUsedAt = if (o.has("lastUsedAt")) o.optLong("lastUsedAt") else null,
            useCount = o.optInt("useCount"),
            score = o.optDouble("score", 0.0),
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun suggestedFileName(): String {
            val now = java.util.Calendar.getInstance()
            return "fidcards-backup-%04d-%02d-%02d.zip".format(
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH) + 1,
                now.get(java.util.Calendar.DAY_OF_MONTH),
            )
        }
    }
}
