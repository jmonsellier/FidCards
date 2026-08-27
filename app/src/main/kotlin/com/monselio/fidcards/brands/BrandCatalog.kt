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
    /** Regroupement de cartes : cinq enseignes Carrefour, une seule carte (§6.4). */
    val cardGroup: String?,
    /** Nom de fichier dans assets/brands/, nul si l'enseigne n'a pas de logo. */
    val logo: String?,
    /** Pastille sur laquelle poser le logo : `light` ou `dark`, indépendant du thème (§6.3.1). */
    val backgroundDark: Boolean,
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
                        sector = o.optString("sector").takeIf { it.isNotBlank() },
                        website = o.optString("website").takeIf { it.isNotBlank() },
                        cardGroup = o.optString("cardGroup").takeIf { it.isNotBlank() },
                        logo = o.optString("logo").takeIf { it.isNotBlank() },
                        backgroundDark = o.optString("background") == "dark",
                    ),
                )
            }
        }
    }

    fun byId(id: String?): Brand? = id?.let { byId[it] }

    /** Autocomplétion insensible à la casse et aux accents, préfixes d'abord. */
    fun search(query: String, limit: Int = 8): List<Brand> {
        val needle = query.searchNormalized()
        if (needle.isEmpty()) return emptyList()
        val matches = brands.filter { needle in it.name.searchNormalized() }
        return matches.sortedBy { !it.name.searchNormalized().startsWith(needle) }.take(limit)
    }

    /** Les autres enseignes qui partagent la même carte, pour l'afficher à l'ajout (§6.4). */
    fun groupSiblings(brand: Brand): List<Brand> {
        val group = brand.cardGroup ?: return emptyList()
        return brands.filter { it.cardGroup == group && it.id != brand.id }
    }
}
