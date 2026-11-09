package com.nuvio.tv.core.tools

import android.content.Context
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.AddonCatalogCollectionSource
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionSource
import com.nuvio.tv.domain.model.TmdbCollectionFilters
import com.nuvio.tv.domain.model.TmdbCollectionMediaType
import com.nuvio.tv.domain.model.TmdbCollectionSort
import com.nuvio.tv.domain.model.TmdbCollectionSource
import com.nuvio.tv.domain.model.TmdbCollectionSourceType
import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 666TVF (09/11) : Frank a été mis en pause par Hugging Face. Ses listes, dans tes tuiles, sont
 * remplacées sur place (même tuile, même place, même nom) :
 *  - « En VF cette semaine » et « Pour toi, Lambda » → tenus sur la Shield ([LocalCatalogs666]) ;
 *  - « Classiques de l'horreur », les listes de l'horreur (Slasher, Vampire, Cannibal…), « Derniers
 *    films / séries » → listes TMDB intégrées à l'appli (affiches TMDB), limitées aux titres sortis
 *    en France (abonnement, location ou achat) : ce qui sort en France sort en VF.
 * Aucune autre tuile n'est touchée. Sauvegarde de l'accueil d'avant (une fois), relancé à chaque
 * démarrage (sans effet quand il n'y a plus rien de Frank).
 */
object FrankFree666 {
    const val FRANK_ADDON_ID = "community.french-stream-enhanced"
    private val FS_ID = Regex("""(?:^|[^a-z0-9])fs-([a-z0-9-]+)$""", RegexOption.IGNORE_CASE)

    // Mots-clés TMDB (vérifiés le 09/11 avec l'API TMDB)
    private const val HORROR = "27"
    private const val NO_DOCS_MOVIE = "99,10770"
    private const val NO_DOCS_TV = "99,10763,10764,10767"
    private const val HORROR_TV_KEYWORDS = "12339|3133|12564|12377|14895|283085|3358|161261|616|287274|215959"

    /** Sous-genres : mots du nom de la liste (ou de la tuile) → mots-clés TMDB. Ordre = priorité. */
    private val SUBGENRES: List<Triple<Regex, String, String?>> = listOf(
        Triple(Regex("slasher", RegexOption.IGNORE_CASE), "Slasher", "12339"),
        Triple(Regex("vampire", RegexOption.IGNORE_CASE), "Vampires", "3133"),
        Triple(Regex("werewolf|loup", RegexOption.IGNORE_CASE), "Loups-garous", "12564"),
        Triple(Regex("zombie", RegexOption.IGNORE_CASE), "Zombies", "12377"),
        Triple(Regex("cannibal", RegexOption.IGNORE_CASE), "Cannibales", "14895"),
        Triple(Regex("body[ _-]?horror", RegexOption.IGNORE_CASE), "Body horror", "283085"),
        Triple(Regex("haunt|hant", RegexOption.IGNORE_CASE), "Maisons hantées", "3358"),
        Triple(Regex("possess|exorc|d[ée]mon", RegexOption.IGNORE_CASE), "Possessions", "161261|9712|2626"),
        Triple(Regex("witch|sorc", RegexOption.IGNORE_CASE), "Sorcellerie", "616|40931"),
        Triple(Regex("cosmi|lovecraft", RegexOption.IGNORE_CASE), "Horreur cosmique", "287274|215959"),
        Triple(Regex("creature|créature|monst|kaiju", RegexOption.IGNORE_CASE), "Créatures", "13031|1299|158126|11100|161791"),
        // « les plus effrayants » : pas de mot-clé, les mieux notés
        Triple(Regex("scar|effray|terrif|peur", RegexOption.IGNORE_CASE), "Les plus effrayants", null)
    )

    private val MOVIE_GENRES = listOf(
        "action" to "28", "aventure|adventure" to "12", "animation" to "16", "com[ée]die|comedy" to "35",
        "crime|policier|enqu[êe]te|detective" to "80", "documentaire|documentary" to "99", "drame|drama" to "18",
        "famille|family" to "10751", "fantastique|fantasy" to "14", "histo" to "36", "romance" to "10749",
        "science[ -]?fiction|sci-?fi" to "878", "thriller" to "53", "guerre|war" to "10752", "western" to "37"
    )
    private val TV_GENRES = listOf(
        "action|aventure|adventure|espion" to "10759", "animation" to "16", "com[ée]die|comedy" to "35",
        "crime|policier|enqu[êe]te|detective|judiciaire|avocat" to "80", "documentaire|documentary" to "99",
        "drame|drama|m[ée]dical|h[ôo]pital" to "18", "famille|family" to "10751", "science[ -]?fiction|fantastique|sci-?fi" to "10765",
        "thriller|myst" to "9648", "guerre|war|histo" to "10768", "western" to "37", "r[ée]alit" to "10764", "k-?drama" to "18"
    )

    fun isFrank(s: AddonCatalogCollectionSource): Boolean =
        s.addonId == FRANK_ADDON_ID || s.addonId.contains("french-stream", true) ||
            FS_ID.containsMatchIn(s.catalogId.substringBefore(","))

    private fun fr(
        title: String, media: TmdbCollectionMediaType, sort: TmdbCollectionSort,
        genres: String? = null, keywords: String? = null, voteCount: Int? = null,
        from: String? = null, to: String? = null
    ) = TmdbCollectionSource(
        sourceType = TmdbCollectionSourceType.DISCOVER,
        title = title,
        mediaType = media,
        sortBy = sort.value,
        filters = TmdbCollectionFilters(
            withGenres = genres,
            withoutGenres = if (media == TmdbCollectionMediaType.TV) NO_DOCS_TV else NO_DOCS_MOVIE,
            withKeywords = keywords,
            voteCountGte = voteCount,
            releaseDateGte = from,
            releaseDateLte = to,
            // sortis en France (abonnement, location, achat) : distribués en VF
            watchRegion = "FR"
        )
    )

    /**
     * Règle pure (testée) : la liste qui remplace une liste de Frank.
     * [name] = nom de la liste (manifeste en cache), [folderTitle] = nom de la tuile (repli).
     */
    fun replacement(s: AddonCatalogCollectionSource, name: String?, folderTitle: String?): CollectionSource {
        val id = FS_ID.find(s.catalogId.substringBefore(","))?.groupValues?.get(1)?.lowercase().orEmpty()
        val tv = s.type == "series" || s.type == "tv"
        val media = if (tv) TmdbCollectionMediaType.TV else TmdbCollectionMediaType.MOVIE
        val popular = TmdbCollectionSort.POPULAR_DESC
        when (id) {
            "vf-week" -> return AddonCatalogCollectionSource(LocalCatalogs666.VFWEEK, if (tv) "series" else "movie", "vf-week")
            "pour-toi" -> return AddonCatalogCollectionSource(LocalCatalogs666.POURTOI, if (tv) "series" else "movie", "pour-toi")
            "horreur-classiques" -> return fr(
                "Classiques de l'horreur", TmdbCollectionMediaType.MOVIE, TmdbCollectionSort.VOTE_COUNT_DESC,
                genres = HORROR, voteCount = 25, from = "1930-01-01", to = "1990-12-31"
            )
            "derniers-films" -> return fr("Derniers films", TmdbCollectionMediaType.MOVIE, TmdbCollectionSort.RELEASE_DATE_DESC, voteCount = 20)
            "dernieres-series" -> return fr("Dernières séries", TmdbCollectionMediaType.TV, TmdbCollectionSort.FIRST_AIR_DATE_DESC, voteCount = 10)
        }
        val label = (name ?: "").replace(Regex("""^\s*(VF|FR|FS)\s*[·\-]\s*""", RegexOption.IGNORE_CASE), "").trim()
        val text = "$label $id ${folderTitle.orEmpty()}"
        val title = label.ifBlank { folderTitle?.takeIf { it.isNotBlank() } ?: "Horreur" }
        val horrorish = Regex("horr|scary|peur|effray|terreur|épouvante|epouvante", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            SUBGENRES.any { it.first.containsMatchIn(text) }
        // 1) un sous-genre de l'horreur (nom de la liste d'abord, puis nom de la tuile)
        val sub = SUBGENRES.firstOrNull { it.first.containsMatchIn("$label $id") }
            ?: SUBGENRES.firstOrNull { it.first.containsMatchIn(folderTitle.orEmpty()) }
        if (sub != null) {
            val (_, _, kw) = sub
            return if (kw == null) {
                fr(title, media, TmdbCollectionSort.VOTE_AVERAGE_DESC, genres = if (tv) null else HORROR,
                    keywords = if (tv) HORROR_TV_KEYWORDS else null, voteCount = if (tv) 100 else 1000)
            } else {
                fr(title, media, popular, genres = if (tv) null else HORROR, keywords = kw, voteCount = 10)
            }
        }
        // 2) horreur en général
        if (horrorish) return fr(title, media, popular, genres = if (tv) null else HORROR, keywords = if (tv) HORROR_TV_KEYWORDS else null, voteCount = 20)
        // 3) un genre (catalogues French-Stream : Séries Action, Films Comédie…)
        val genre = (if (tv) TV_GENRES else MOVIE_GENRES).firstOrNull { (re, _) -> Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(text) }?.second
        if (genre != null) return fr(title, media, popular, genres = genre, voteCount = 20)
        // 4) sinon : les plus populaires sortis en France
        return fr(title.ifBlank { if (tv) "Séries" else "Films" }, media, popular, voteCount = 50)
    }

    /** Listes déjà en panne pendant cette session (on passe directement au remplaçant). */
    private val dead = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun keyOf(s: AddonCatalogCollectionSource) = "${s.addonId}|${s.type}|${s.catalogId}"
    fun markDead(s: AddonCatalogCollectionSource) { dead += keyOf(s) }
    fun isDead(s: AddonCatalogCollectionSource) = keyOf(s) in dead

    /**
     * 666TVF (09/11, 2) : une liste qui ne répond plus (écran noir), passée par aiometadata ou autre,
     * reconnue à son NOM (« En VF cette semaine », « Pour toi », « Slasher », « Prochainement »…) →
     * son remplaçant. Null si le nom ne dit rien (on ne remplace pas au hasard : WWE, UFC… restent).
     */
    fun fallbackFor(s: AddonCatalogCollectionSource, name: String?, folderTitle: String?, today: java.time.LocalDate = java.time.LocalDate.now()): CollectionSource? {
        if (isFrank(s)) return replacement(s, name, folderTitle)
        // « S.Action » (séries d'aiometa) → « action »
        val n = HomeReorg666.norm(name).replace(Regex("^s\\.\\s*"), "")
        val f = HomeReorg666.norm(folderTitle)
        val tv = s.type == "series" || s.type == "tv"
        val t = if (tv) "series" else "movie"
        val media = if (tv) TmdbCollectionMediaType.TV else TmdbCollectionMediaType.MOVIE
        val title = name?.takeIf { it.isNotBlank() } ?: folderTitle.orEmpty()
        return when {
            "vf cette semaine" in n || "nouveautes vf" in n || "nouveaute vf" in n ->
                AddonCatalogCollectionSource(LocalCatalogs666.VFWEEK, t, "vf-week")
            "pour toi" in n -> AddonCatalogCollectionSource(LocalCatalogs666.POURTOI, t, "pour-toi")
            "classique" in n && ("horr" in n || "horr" in f) ->
                replacement(AddonCatalogCollectionSource(FRANK_ADDON_ID, "movie", "fs-horreur-classiques"), name, folderTitle)
            Regex("prochain|coming soon|bientot|a venir|upcoming").containsMatchIn(n) ->
                AddonCatalogCollectionSource(LocalCatalogs666.UPCOMING, t, "upcoming")
            "recherche french stream" in n -> REMOVE
            "shudder" in n -> TmdbCollectionSource(
                sourceType = TmdbCollectionSourceType.DISCOVER, title = title, mediaType = media,
                sortBy = TmdbCollectionSort.POPULAR_DESC.value,
                filters = TmdbCollectionFilters(watchRegion = "US", withWatchProviders = "99",
                    withoutGenres = if (tv) NO_DOCS_TV else NO_DOCS_MOVIE)
            )
            "blumhouse" in n -> fr(title, media, TmdbCollectionSort.POPULAR_DESC, voteCount = 20).let {
                it.copy(filters = it.filters.copy(withCompanies = if (tv) "68884|3172" else "3172"))
            }
            "betterposters" in n -> fr(title, media, TmdbCollectionSort.POPULAR_DESC, voteCount = 50)
            SUBGENRES.any { it.first.containsMatchIn(n) } ->
                replacement(AddonCatalogCollectionSource(FRANK_ADDON_ID, s.type, "fs-ext-0"), name, folderTitle)
            theme(n, tv) != null -> theme(n, tv)!!.let { (genres, kw) ->
                val src = fr(title, media, TmdbCollectionSort.POPULAR_DESC, genres = genres, keywords = kw, voteCount = 20)
                if (genres == "99") src.copy(filters = src.filters.copy(withoutGenres = null)) else src
            }
            Regex("^(derniers? films?|dernieres? series?|nouveaux? films?|nouvelles? series?|nouveautes?)$").containsMatchIn(n) ->
                if (tv) fr(title, media, TmdbCollectionSort.FIRST_AIR_DATE_DESC, voteCount = 10)
                else fr(title, media, TmdbCollectionSort.RELEASE_DATE_DESC, voteCount = 20)
            // listes de l'horreur : le nom de la liste (ou la tuile) parle d'horreur ou d'un sous-genre
            SUBGENRES.any { it.first.containsMatchIn(n) } || Regex("horr|scary|epouvante").containsMatchIn(n) ||
                (Regex("horr").containsMatchIn(f) && n.isNotBlank()) ->
                replacement(AddonCatalogCollectionSource(FRANK_ADDON_ID, s.type, "fs-ext-0"), name, folderTitle)
            else -> null
        }
    }

    /** Marqueur : liste à retirer (la recherche French-Stream de Frank n'existe plus). */
    val REMOVE = AddonCatalogCollectionSource("666tv.remove", "movie", "remove")

    /** Catalogues à thème (genres et mots-clés TMDB vérifiés le 09/11) : (genres, mots-clés). */
    fun theme(n: String, tv: Boolean): Pair<String?, String?>? {
        fun g(movie: String, series: String) = if (tv) series else movie
        return when {
            Regex("found ?footage").containsMatchIn(n) -> (if (tv) null else HORROR) to "163053"
            Regex("supernatural|surnaturel").containsMatchIn(n) -> null to "6152"
            Regex("animation pour adultes|adult animation").containsMatchIn(n) -> "16" to "161919"
            Regex("alien").containsMatchIn(n) -> null to "9951|14909"
            Regex("intelligence artificielle|\\bia\\b").containsMatchIn(n) -> null to "310"
            Regex("dystop").containsMatchIn(n) -> null to "4565"
            Regex("voyage spatial|espace").containsMatchIn(n) -> null to "3801|1612"
            Regex("super.?hero").containsMatchIn(n) -> null to "9715"
            Regex("voyage temporel|voyage dans le temps").containsMatchIn(n) -> null to "4379"
            Regex("survie").containsMatchIn(n) -> null to "10349"
            Regex("anthologie").containsMatchIn(n) -> null to "9706"
            Regex("mini.?serie").containsMatchIn(n) -> null to "11162"
            Regex("biopic|biograph").containsMatchIn(n) -> null to "5565"
            Regex("enquete").containsMatchIn(n) -> (if (tv) "80|9648" else null) to (if (tv) null else "703")
            Regex("documentaire").containsMatchIn(n) -> "99" to null
            Regex("science.?fiction").containsMatchIn(n) -> g("878", "10765") to null
            Regex("fantastique").containsMatchIn(n) -> g("14", "10765") to null
            Regex("histori").containsMatchIn(n) -> g("36", "10768") to null
            Regex("policier").containsMatchIn(n) -> "80" to null
            Regex("thriller").containsMatchIn(n) -> g("53", "9648") to null
            Regex("aventure").containsMatchIn(n) -> g("12", "10759") to null
            Regex("^action").containsMatchIn(n) -> g("28", "10759") to null
            Regex("comedie").containsMatchIn(n) -> "35" to null
            Regex("drame").containsMatchIn(n) -> "18" to null
            else -> null
        }
    }

    fun isAiometa(s: AddonCatalogCollectionSource) =
        s.addonId.contains("aiometadata", true) || s.addonId.contains("aiometa", true)

    /**
     * 666TVF (09/11, 4) : étape 2 — les catalogues d'aiometa remplacés par leurs équivalents intégrés
     * (TMDB, ou tenus sur la Shield), reconnus à leur nom. Ceux qu'on ne sait pas remplacer restent.
     */
    fun planAiometa(collections: List<Collection>, catalogName: (AddonCatalogCollectionSource) -> String?): Plan {
        val report = mutableListOf<String>()
        val out = collections.map { c ->
            c.copy(folders = c.folders.map { f ->
                if (f.sources.none { it is AddonCatalogCollectionSource && isAiometa(it) }) f
                else f.copy(sources = f.sources.mapNotNull { s ->
                    if (s !is AddonCatalogCollectionSource || !isAiometa(s)) return@mapNotNull s
                    val name = catalogName(s)
                    val r = fallbackFor(s, name, f.title)
                    when {
                        r == null -> { report += "${f.title} : « ${name ?: s.catalogId} » gardé (pas d'équivalent)"; s }
                        r == REMOVE -> { report += "${f.title} : « $name » retiré"; null }
                        else -> { report += "${f.title} : « ${name ?: s.catalogId} » → intégré"; r }
                    }
                }.distinct().ifEmpty { f.sources })
            })
        }
        return Plan(out, report)
    }

    /**
     * Règle pure (testée) : la tombe « Horreur » reprenait toutes les listes des autres tombes
     * (Slasher, Vampire…). Elle ne garde que celles qu'aucune autre tuile n'a (au moins une reste).
     */
    fun dedupeHorror(collections: List<Collection>): List<Collection> {
        val all = collections.flatMap { it.folders }
        val horror = all.firstOrNull { HomeReorg666.isHorror(it) } ?: return collections
        val elsewhere = all.filter { it.id != horror.id }.flatMap { it.sources }.toSet()
        val kept = horror.sources.filter { it !in elsewhere }
        if (kept.isEmpty() || kept.size == horror.sources.size) return collections
        return collections.map { c -> c.copy(folders = c.folders.map { f -> if (f.id == horror.id) f.copy(sources = kept) else f }) }
    }

    data class Plan(val collections: List<Collection>, val report: List<String>)

    /** Règle pure (testée) : remplace les listes de Frank, ne touche à rien d'autre. */
    fun plan(collections: List<Collection>, catalogName: (AddonCatalogCollectionSource) -> String?): Plan {
        val report = mutableListOf<String>()
        var changed = false
        val out = collections.map { c ->
            c.copy(folders = c.folders.map { f ->
                if (f.sources.none { it is AddonCatalogCollectionSource && isFrank(it) }) f
                else {
                    changed = true
                    val sources = f.sources.map { s ->
                        if (s is AddonCatalogCollectionSource && isFrank(s)) {
                            val r = replacement(s, catalogName(s), f.title)
                            report += "${f.title} : ${catalogName(s) ?: s.catalogId} → " +
                                (if (r is TmdbCollectionSource) "TMDB « ${r.title} »" else "sur la Shield")
                            r
                        } else s
                    }.distinct()
                    f.copy(sources = sources)
                }
            })
        }
        return Plan(if (changed) out else collections, report)
    }

    /** Au démarrage. Renvoie le compte rendu (vide si rien à faire). */
    suspend fun ensure(context: Context, store: CollectionsDataStore, addons: AddonRepository): List<String> {
        val loaded = runCatching { store.getCurrentCollections() }.getOrNull() ?: return emptyList()
        // 666TVF (09/11, 3) : la 1re tombe « Horreur » ne garde que ses listes à elle
        val current = dedupeHorror(loaded)
        if (current != loaded) {
            store.setCollections(current)
            runCatching { pushCollections(context) }
        }
        if (current.none { c -> c.folders.any { f -> f.sources.any { it is AddonCatalogCollectionSource && (isFrank(it) || isAiometa(it)) } } }) return emptyList()
        val installed = withTimeoutOrNull(5_000) { addons.getInstalledAddons().first() }.orEmpty()
        val names = HashMap<String, String>()
        installed.forEach { a -> a.catalogs.forEach { cat -> names["${a.id}|${cat.apiType}|${cat.id}"] = cat.name; names.putIfAbsent("*|${cat.apiType}|${cat.id}", cat.name) } }
        val nameOf: (AddonCatalogCollectionSource) -> String? = { s -> names["${s.addonId}|${s.type}|${s.catalogId}"] ?: names["*|${s.type}|${s.catalogId}"] }
        val first = plan(current, nameOf)
        // les noms d'aiometa ne sont connus que tant qu'il est installé : on remplace maintenant
        val second = planAiometa(first.collections) { s -> nameOf(s) }
        val plan = Plan(second.collections, first.report + second.report)
        if (plan.collections == current) return emptyList()
        val prefs = context.getSharedPreferences("666tv_frankfree", Context.MODE_PRIVATE)
        if (prefs.getString("before", null) == null) store.exportCurrentProfileJson()?.let { prefs.edit().putString("before", it).apply() }
        store.setCollections(plan.collections)
        runCatching { pushCollections(context) }
        prefs.edit().putString("report", plan.report.joinToString("\n")).putLong("at", System.currentTimeMillis()).apply()
        runCatching { Announce666.log(context, "🧟 Tuiles sans Frank : ${plan.report.size} liste(s) remplacée(s)", "accueil") }
        return plan.report
    }

    fun lastReport(context: Context): List<String> =
        context.getSharedPreferences("666tv_frankfree", Context.MODE_PRIVATE).getString("report", null)
            ?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    fun canUndo(context: Context) = context.getSharedPreferences("666tv_frankfree", Context.MODE_PRIVATE).getString("before", null) != null

    suspend fun undo(context: Context, store: CollectionsDataStore): Boolean {
        val prefs = context.getSharedPreferences("666tv_frankfree", Context.MODE_PRIVATE)
        val json = prefs.getString("before", null) ?: return false
        val before = store.importFromJson(json)
        if (before.isEmpty()) return false
        // on garde la sauvegarde : au prochain démarrage les listes de Frank seraient remplacées à nouveau,
        // sauf si on le désactive ici
        prefs.edit().putBoolean("disabled", true).apply()
        store.setCollections(before)
        runCatching { pushCollections(context) }
        return true
    }

    fun isDisabled(context: Context) =
        context.getSharedPreferences("666tv_frankfree", Context.MODE_PRIVATE).getBoolean("disabled", false)

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface SyncEntry { fun collectionSync(): com.nuvio.tv.core.sync.CollectionSyncService }

    /** Les tuiles changées partent aussi vers ton compte (sinon une synchro remettrait Frank). */
    private fun pushCollections(context: Context) {
        dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, SyncEntry::class.java)
            .collectionSync().triggerPush()
    }
}
