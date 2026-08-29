package com.monselio.fidcards.backup

import android.content.Context
import android.net.Uri
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.CardRepository
import com.monselio.fidcards.data.ImageStore
import java.io.File
import java.io.OutputStream
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
    private val imageStore: ImageStore,
) {

    /** Répertoire local des images de cartes (logos choisis, photos recto/verso). */
    val imagesDir: File
        get() = imageStore.dir

    /**
     * L'archive est d'abord construite dans le cache, puis recopiée d'un bloc
     * vers la destination : une sérialisation qui échoue ne doit pas détruire
     * la sauvegarde précédente, que la cible réécrit à chaque export.
     */
    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "export-${UUID.randomUUID()}.zip")
        try {
            val cards = repository.getAll()
            staging.outputStream().use { writeArchive(it, cards) }
            openTruncating(uri)?.use { out -> staging.inputStream().use { it.copyTo(out) } }
                ?: return@withContext BackupResult.Failed
            BackupResult.ExportOk(cards.size)
        } catch (_: Exception) {
            BackupResult.Failed
        } finally {
            staging.delete()
        }
    }

    /**
     * Le mode « wt » tronque le fichier existant : sans lui, une archive plus
     * courte que la précédente laisserait la queue de l'ancienne derrière elle.
     * Tous les fournisseurs SAF ne le gèrent pas, d'où le repli sur « w ».
     */
    private fun openTruncating(uri: Uri): OutputStream? =
        runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)

    private fun writeArchive(out: OutputStream, cards: List<Card>) {
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
    }

    suspend fun import(uri: Uri, mode: ImportMode): BackupResult = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "import-${UUID.randomUUID()}").apply { mkdirs() }
        val restoredImages = mutableListOf<File>()
        try {
            val cardsJson = extract(uri, tempDir) ?: return@withContext BackupResult.Failed

            val root = JSONObject(cardsJson)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                return@withContext BackupResult.UnknownVersion
            }

            // Tout le JSON est décodé AVANT la moindre écriture : une archive
            // tronquée doit échouer sur une bibliothèque intacte, jamais sur
            // une bibliothèque déjà vidée.
            val array = root.getJSONArray("cards")
            val parsed = (0 until array.length()).map { parseCard(array.getJSONObject(it)) }

            // En « remplacer », les anciennes images ne partent qu'une fois la
            // transaction validée : un échec laisse la bibliothèque complète.
            val previousImages = if (mode == ImportMode.REPLACE) {
                imagesDir.listFiles()?.toList().orEmpty()
            } else {
                emptyList()
            }

            var imported = 0
            var skipped = 0
            repository.inTransaction {
                if (mode == ImportMode.REPLACE) repository.deleteAll()

                // Clé de déduplication : barcodeValue + barcodeFormat (§8).
                val existingKeys = repository.getAll()
                    .map { it.barcodeValue to it.barcodeFormat }
                    .toMutableSet()

                val toInsert = mutableListOf<Card>()
                for (entry in parsed) {
                    if (entry == null) {
                        skipped++
                        continue
                    }
                    val key = entry.card.barcodeValue to entry.card.barcodeFormat
                    if (key in existingKeys) {
                        skipped++
                        continue
                    }
                    // Les images ne sont copiées que pour les cartes réellement
                    // conservées : un doublon ignoré ne doit rien laisser sur le disque.
                    toInsert += entry.card.copy(
                        logoPath = restore(entry.logo, tempDir, restoredImages),
                        frontImagePath = restore(entry.frontImage, tempDir, restoredImages),
                        backImagePath = restore(entry.backImage, tempDir, restoredImages),
                    )
                    existingKeys += key
                    imported++
                }
                repository.insertAll(toInsert)
            }
            previousImages.forEach { it.delete() }
            BackupResult.ImportOk(imported, skipped)
        } catch (_: Exception) {
            restoredImages.forEach { it.delete() }
            BackupResult.Failed
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Déballe l'archive dans [tempDir] et renvoie le contenu de cards.json. */
    private fun extract(uri: Uri, tempDir: File): String? {
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
        } ?: return null
        return cardsJson
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

    /** Carte décodée, ses images encore désignées par leur nom d'entrée dans l'archive. */
    private class ParsedCard(
        val card: Card,
        val logo: String?,
        val frontImage: String?,
        val backImage: String?,
    )

    /** Décode une carte sans aucun effet de bord ; null si une donnée obligatoire manque. */
    private fun parseCard(o: JSONObject): ParsedCard? {
        val format = runCatching {
            BarcodeFormat.valueOf(o.getString("barcodeFormat"))
        }.getOrNull() ?: return null
        val value = o.optString("barcodeValue").takeIf { it.isNotEmpty() } ?: return null
        val name = o.optString("name").takeIf { it.isNotEmpty() } ?: return null

        return ParsedCard(
            card = Card(
                name = name,
                barcodeValue = value,
                displayValue = o.optString("displayValue").takeIf { it.isNotEmpty() },
                barcodeFormat = format,
                brandId = o.optString("brandId").takeIf { it.isNotEmpty() },
                colorArgb = if (o.has("colorArgb")) o.optInt("colorArgb") else null,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = if (o.has("lastUsedAt")) o.optLong("lastUsedAt") else null,
                useCount = o.optInt("useCount"),
                score = o.optDouble("score", 0.0),
            ),
            logo = o.optString("logo").takeIf { it.isNotEmpty() },
            frontImage = o.optString("frontImage").takeIf { it.isNotEmpty() },
            backImage = o.optString("backImage").takeIf { it.isNotEmpty() },
        )
    }

    /** Copie une image déballée vers le magasin local ; [created] permet de tout défaire. */
    private fun restore(entryName: String?, extractedDir: File, created: MutableList<File>): String? {
        val source = entryName?.let { File(extractedDir, File(it).name) }
            ?.takeIf { it.isFile } ?: return null
        val target = File(imagesDir, "${UUID.randomUUID()}-${source.name}")
        source.copyTo(target, overwrite = true)
        created += target
        return target.absolutePath
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
