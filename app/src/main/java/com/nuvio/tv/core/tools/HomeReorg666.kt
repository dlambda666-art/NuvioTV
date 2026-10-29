package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.core.sync.CollectionSyncService
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 666TVF : « Réorganiser mon accueil » (29/10).
 *  - 1re rangée : En VF cette semaine · Pour toi · Shudder · Prochainement ;
 *  - puis les portes de l'horreur, en deux rangées : Horreur, Classiques, puis un sous-genre par
 *    tuile, chacun avec ses listes Scary Only (filtrées en VF par Frank) reprises de la tuile Horreur ;
 *  - Netflix, Prime, HBO Max, Canal+ et les tuiles Films / Séries partent.
 * Tout le reste (Top 10, WWE, UFC, tuiles inconnues) ne bouge pas. Sauvegarde avant, « Annuler » après.
 */
@Singleton
class HomeReorg666 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val collectionsDataStore: CollectionsDataStore,
    private val collectionSyncService: CollectionSyncService,
    private val addonRepository: AddonRepository
) {
    private val prefs by lazy { context.getSharedPreferences("666tv_reorg", Context.MODE_PRIVATE) }

    fun canUndo(): Boolean = prefs.getString("before", null) != null

    /** Applique la réorganisation ; renvoie le compte rendu (une ligne par élément). */
    suspend fun apply(): List<String> {
        val current = collectionsDataStore.getCurrentCollections()
        if (current.isEmpty()) return listOf("Aucune tuile sur l'accueil : rien à réorganiser")
        val addons = withTimeoutOrNull(5_000) { addonRepository.getInstalledAddons().first() }.orEmpty()
        val names = HashMap<String, String>()
        addons.forEach { a -> a.catalogs.forEach { c -> names["${a.id}|${c.rawType}|${c.id}"] = c.name } }
        val classics = addons.firstNotNullOfOrNull { a ->
            a.catalogs.firstOrNull { it.id == "fs-horreur-classiques" }?.let { AddonCatalogCollectionSource(a.id, it.rawType, it.id) }
        }
        val result = plan(current, { s -> names["${s.addonId}|${s.type}|${s.catalogId}"] }, classics) { UUID.randomUUID().toString() }
        if (result.collections == current) return result.report
        // Sauvegarde (pour « Annuler ») seulement la première fois : on garde l'accueil d'origine.
        if (!canUndo()) collectionsDataStore.exportCurrentProfileJson()?.let { prefs.edit().putString("before", it).apply() }
        collectionsDataStore.setCollections(result.collections)
        collectionSyncService.triggerPush()
        return result.report
    }

    suspend fun undo(): Boolean {
        val json = prefs.getString("before", null) ?: return false
        val before = collectionsDataStore.importFromJson(json)
        if (before.isEmpty()) return false
        collectionsDataStore.setCollections(before)
        collectionSyncService.triggerPush()
        prefs.edit().remove("before").apply()
        return true
    }

    data class Plan(val collections: List<Collection>, val report: List<String>)

    companion object {
        /** Sous-genres, dans l'ordre voulu (titre de la tuile → mots des listes Scary Only). */
        val SUBGENRES: List<Pair<String, Regex>> = listOf(
            "Slasher" to Regex("slasher", RegexOption.IGNORE_CASE),
            "Les plus effrayants" to Regex("scariest|effrayant", RegexOption.IGNORE_CASE),
            "Créatures" to Regex("creature|créature|monster|monstre|kaiju", RegexOption.IGNORE_CASE),
            "Vampires" to Regex("vampire", RegexOption.IGNORE_CASE),
            "Loups-garous" to Regex("werewolf|loup", RegexOption.IGNORE_CASE),
            "Zombies" to Regex("zombie", RegexOption.IGNORE_CASE),
            "Cannibales" to Regex("cannibal", RegexOption.IGNORE_CASE),
            "Body horror" to Regex("body", RegexOption.IGNORE_CASE),
            "Maisons hantées" to Regex("haunt|hant", RegexOption.IGNORE_CASE),
            "Possessions" to Regex("possess|exorc", RegexOption.IGNORE_CASE),
            "Sorcellerie" to Regex("witch|sorc", RegexOption.IGNORE_CASE),
            "Horreur cosmique" to Regex("cosmic|cosmique|lovecraft", RegexOption.IGNORE_CASE)
        )
        const val PORTE_COVER = "666art:porte"

        internal fun norm(s: String?): String =
            Normalizer.normalize(s.orEmpty(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

        private fun CollectionFolder.has(cover: String) = coverImageUrl.orEmpty().contains(cover)
        internal fun isVfWeek(f: CollectionFolder) = f.has("g0hvcfM4") || norm(f.title).let { "vf cette semaine" in it || "nouveaute" in it }
        internal fun isPourToi(f: CollectionFolder) = "pour toi" in norm(f.title)
        internal fun isComingSoon(f: CollectionFolder) = f.has("cLGKrgcd") || norm(f.title).let { "prochainement" in it || "coming soon" in it }
        internal fun isShudder(f: CollectionFolder) = f.has("streaming-shudder") || "shudder" in norm(f.title)
        internal fun isHorror(f: CollectionFolder) = f.has("PrRXbjxT") || norm(f.title) == "horreur"
        internal fun isUnwanted(f: CollectionFolder): Boolean {
            val t = norm(f.title)
            return listOf("streaming-netflix", "streaming-prime", "streaming-hbo", "sf00sWZ2", "FKYdvQR5").any { f.has(it) } ||
                t in setOf("netflix", "prime video", "prime", "hbo max", "hbo", "max", "canal+", "canal", "films", "series")
        }

        /** Plan pur (testé) : ne touche à rien d'autre que les tuiles citées. */
        fun plan(
            collections: List<Collection>,
            catalogName: (AddonCatalogCollectionSource) -> String?,
            classics: AddonCatalogCollectionSource?,
            newId: () -> String
        ): Plan {
            val report = mutableListOf<String>()
            val all = collections.flatMap { it.folders }
            if (all.count { it.coverImageUrl == PORTE_COVER } > 1) {
                return Plan(collections, listOf("Ton accueil est déjà réorganisé : rien n'a été changé"))
            }
            val horror = all.firstOrNull(::isHorror)
                ?: return Plan(collections, listOf("Tuile « Horreur » introuvable : rien n'a été changé"))
            val vfWeek = all.firstOrNull(::isVfWeek)
            val pourToi = all.firstOrNull(::isPourToi)
            val shudder = all.firstOrNull(::isShudder)
            val coming = all.firstOrNull(::isComingSoon)
            val moved = listOfNotNull(vfWeek, pourToi, shudder, coming, horror).map { it.id }.toSet()
            val removed = all.filter { it.id !in moved && isUnwanted(it) }
            removed.forEach { report += "Enlevé : ${it.title}" }

            // Les portes de l'horreur
            val horrorSources = horror.sources.filterIsInstance<AddonCatalogCollectionSource>()
            fun door(title: String, sources: List<com.nuvio.tv.domain.model.CollectionSource>) = horror.copy(
                id = newId(), title = title, coverImageUrl = PORTE_COVER, focusGifUrl = null, titleLogoUrl = null, sources = sources
            )
            val doors = mutableListOf(horror.copy(coverImageUrl = PORTE_COVER, focusGifUrl = null, titleLogoUrl = null, title = "Horreur"))
            val classicsSource = all.flatMap { it.sources }.filterIsInstance<AddonCatalogCollectionSource>()
                .firstOrNull { it.catalogId == "fs-horreur-classiques" } ?: classics
            if (classicsSource != null) doors += door("Classiques", listOf(classicsSource))
            else report += "Pas trouvé : Classiques (catalogue Frank absent)"
            for ((title, re) in SUBGENRES) {
                val src = horrorSources.filter { s -> re.containsMatchIn(catalogName(s).orEmpty() + " " + s.catalogId) }
                if (src.isEmpty()) report += "Pas trouvé : $title (aucune liste à ce nom dans la tuile Horreur)"
                else { doors += door(title, src); report += "Porte : $title (${src.size} liste(s))" }
            }

            // 1re rangée : celle qui contient « Pour toi » (sinon celle de « En VF cette semaine »)
            val firstRowIndex = collections.indexOfFirst { c -> c.folders.any { it.id == pourToi?.id } }
                .takeIf { it >= 0 } ?: collections.indexOfFirst { c -> c.folders.any { it.id == vfWeek?.id } }
            val gone = removed.map { it.id }.toSet() + moved
            val out = mutableListOf<Collection>()
            val horrorCollection = collections.first { c -> c.folders.any { it.id == horror.id } }
            val half = (doors.size + 1) / 2
            val horrorRows = listOf(
                horrorCollection.copy(id = newId(), title = "Horreur", folders = doors.take(half)),
                horrorCollection.copy(id = newId(), title = "Horreur ·", folders = doors.drop(half))
            ).filter { it.folders.isNotEmpty() }
            collections.forEachIndexed { i, c ->
                val kept = c.folders.filter { it.id !in gone }
                if (i == firstRowIndex) {
                    out += c.copy(folders = listOfNotNull(vfWeek, pourToi, shudder, coming) + kept)
                    out += horrorRows
                } else if (kept.isNotEmpty()) {
                    out += c.copy(folders = kept)
                } else if (c.folders.isNotEmpty()) report += "Rangée vidée et retirée : ${c.title}"
            }
            if (firstRowIndex < 0) {
                out.add(0, collections.first().copy(id = newId(), title = "666", folders = listOfNotNull(vfWeek, pourToi, shudder, coming)))
                out.addAll(1, horrorRows)
            }
            return Plan(out, report)
        }
    }
}
