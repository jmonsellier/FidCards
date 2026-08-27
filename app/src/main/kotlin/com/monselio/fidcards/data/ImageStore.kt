package com.monselio.fidcards.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Les images des cartes (logos choisis, photos recto/verso) vivent dans
 * filesDir/images : copiées au moment du choix via le Photo Picker — donc sans
 * permission de stockage (docs/PROJET.md §7) — et embarquées dans l'export ZIP.
 */
class ImageStore(private val context: Context) {

    val dir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    /** Copie l'image pointée par [uri] et renvoie son chemin local, ou null en échec. */
    fun import(uri: Uri): String? = runCatching {
        val extension = context.contentResolver.getType(uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?: "img"
        val target = File(dir, "${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        target.absolutePath
    }.getOrNull()

    /** Supprime un fichier géré par ce magasin ; ignore tout chemin extérieur. */
    fun delete(path: String?) {
        val file = path?.let(::File) ?: return
        if (file.parentFile == dir) file.delete()
    }
}
