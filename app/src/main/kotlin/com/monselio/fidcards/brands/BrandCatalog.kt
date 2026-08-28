package com.monselio.fidcards.brands

import android.content.Context
import com.monselio.fidcards.util.searchNormalized
import org.json.JSONArray

/**
 * Catalogue d'enseignes (docs/PROJET.md §4.2 et §6) : asset en lecture seule,
 * jamais en base. Une carte ne stocke que `brandId` ; tout le reste vient d'ici.
 */
data class Brand(
    val id: String,
    val name: String,
    val sector: String?,
    val website: String?,
    /** Nom de fichier dans assets/brands/, nul si l'enseigne n'a pas de logo. */
    val logo: String?,
    /** Pastille sur laquelle poser le logo : `light` ou `dark`, indépendant du thème (§6.3.1). */
    val backgroundDark: Boolean,
    /** Couleur primaire officielle (seed_brands_3.csv), ARGB opaque, nulle si inconnue. */
    val colorArgb: Int?,
)

class BrandCatalog(private val context: Context) {

    val brands: List<Brand> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { load() }

    private val byId: Map<String, Brand> by lazy { brands.associateBy { it.id } }

    /**
     * Index des fichiers réellement embarqués, par nom de base : l'optimisation
     * (tools/optimize-logos.sh) peut changer l'extension (svg rasterisé, png
     * converti en webp) sans que brands.json ait à suivre.
     */
    private val logoFiles: Map<String, String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.assets.list("brands")
            ?.associateBy { it.substringBeforeLast('.') }
            .orEmpty()
    }

    fun logoAssetUri(brand: Brand?): String? {
        val declared = brand?.logo ?: return null
        val actual = logoFiles[declared.substringBeforeLast('.')] ?: return null
        return "file:///android_asset/brands/$actual"
    }

    private fun load(): List<Brand> {
        val json = context.assets.open("brands.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    Brand(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        sector = o.cleanString("sector"),
                        website = o.cleanString("website"),
                        logo = o.cleanString("logo"),
                        backgroundDark = o.optString("background") == "dark",
                        colorArgb = o.optString("color")
                            .takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) }
                            ?.let { 0xFF000000.toInt() or it.substring(1).toInt(16) },
                    ),
                )
            }
        }
    }

    /** optString d'org.json rend la CHAÎNE « null » pour un null JSON. */
    private fun org.json.JSONObject.cleanString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    fun byId(id: String?): Brand? = id?.let { byId[it] }

    private val byNormalizedName: Map<String, Brand> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        brands.associateBy { it.name.searchNormalized() }
    }

    /**
     * Correspondance exacte sur le nom normalisé : relie les cartes créées avant
     * que leur enseigne n'entre au catalogue (aucun brandId en base).
     */
    fun byName(name: String): Brand? = byNormalizedName[name.searchNormalized()]

    /**
     * Autocomplétion insensible à la casse et aux accents, préfixes d'abord.
     * Les cartes communes sont simplifiées à la source : une seule entrée
     * « Carrefour » au catalogue, pas de notion de groupe dans l'app (§6.4).
     */
    fun search(query: String, limit: Int = 8): List<Brand> {
        val needle = query.searchNormalized()
        if (needle.isEmpty()) return emptyList()
        return brands
            .filter { needle in it.name.searchNormalized() }
            .sortedBy { !it.name.searchNormalized().startsWith(needle) }
            .take(limit)
    }
}
