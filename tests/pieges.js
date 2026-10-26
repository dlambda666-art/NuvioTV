// 666TV — « cas pièges » des plugins : chaque panne trouvée devient un test.
// Sans réseau : les réponses (TMDB, addon, Frank) sont simulées avec des noms
// de fichiers réels. À lancer avant chaque publication : node tests/pieges.js
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const NOW = Date.parse('2026-10-23T12:00:00Z');

function loadTemplate(fixture) {
    const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'addon666.js'), 'utf8').replace(/__NAME__/g, 'Test');
    const ctx = {
        console: { log() {} },
        Date: class extends Date { constructor(...a) { super(...(a.length ? a : [NOW])); } static now() { return NOW; } },
        SCRAPER_SETTINGS: { url: 'https://addon.test/cfg' },
        TMDB_API_KEY: 'k',
        module: { exports: {} },
        setTimeout, Promise, JSON, Math, Number, String, Object, Array, RegExp, encodeURIComponent, parseInt, isNaN,
        fetch: async url => {
            const body = fixture(String(url));
            return body === undefined
                ? { ok: false, status: 404, text: async () => '' }
                : { ok: true, status: 200, text: async () => JSON.stringify(body) };
        }
    };
    ctx.Date.parse = Date.parse; ctx.Date.UTC = Date.UTC;
    vm.runInNewContext(src, ctx);
    return ctx.module.exports;
}

const stream = name => ({ name: 'Test', title: name, url: 'https://cdn.test/' + encodeURIComponent(name), behaviorHints: { filename: name } });

const cases = [
    {
        nom: 'Anthologie : Monstre S04E05 rangé en « S01E05 »',
        args: ['tt13207736', 'tv', 4, 5],
        air: '2026-09-20',
        streams: ['Monster.The.Lizzie.Borden.Story.S01E05.FRENCH.1080p.WEB.mkv'],
        attendu: 1
    },
    {
        nom: 'Série normale : S01E05 écarté quand S04E05 exact existe',
        args: ['tt0000001', 'tv', 4, 5],
        air: '2026-09-20',
        streams: ['Show.S04E05.FRENCH.1080p.mkv', 'Show.S01E05.FRENCH.1080p.mkv'],
        garde: ['Show.S04E05'], jette: ['Show.S01E05']
    },
    {
        nom: 'Émission datée (WWE) : seul le fichier du bon jour',
        args: ['tt0185103', 'tv', 34, 43],
        air: '2026-10-20',
        streams: ['WWE.Raw.2026.10.20.FRENCH.1080p.mkv', 'WWE.Raw.2026.10.13.FRENCH.1080p.mkv'],
        garde: ['2026.10.20'], jette: ['2026.10.13']
    },
    {
        nom: 'Épisode pas encore diffusé : rien',
        args: ['tt0185103', 'tv', 34, 44],
        air: '2026-10-27',
        streams: ['WWE.Raw.2026.10.20.FRENCH.1080p.mkv'],
        attendu: 0
    },
    {
        nom: 'Film français encore au cinéma (Kraken) : rien',
        args: ['1500001', 'movie'],
        movie: { imdb: 'tt90000001', year: '2026-09-30', lang: 'fr', title: 'Kraken', fr: [{ type: 3, release_date: '2026-09-30T00:00:00.000Z' }] },
        streams: ['Kraken.2026.FRENCH.1080p.WEB.mkv'],
        attendu: 0
    },
    {
        nom: 'Homonyme d\'une autre année : écarté',
        args: ['1500002', 'movie'],
        movie: { imdb: 'tt90000002', year: '2026-03-01', lang: 'en', title: 'Kraken', fr: [] },
        streams: ['Kraken.2026.FRENCH.1080p.mkv', 'Kraken.2020.TRUEFRENCH.2160p.mkv'],
        garde: ['Kraken.2026'], jette: ['Kraken.2020']
    },
    {
        nom: 'Année dans le titre (« 1998, l\'été de nos 18 ans ») : gardé',
        args: ['1500003', 'movie'],
        movie: { imdb: 'tt90000003', year: '2026-06-01', lang: 'fr', title: '1998, l\'été de nos 18 ans', fr: [{ type: 3, release_date: '2026-01-10T00:00:00.000Z' }, { type: 4, release_date: '2026-05-10T00:00:00.000Z' }] },
        streams: ['1998.l.ete.de.nos.18.ans.2026.FRENCH.1080p.mkv'],
        attendu: 1
    },
    {
        nom: 'Vieux film (Le Parrain 1972) : gardé',
        args: ['238', 'movie'],
        movie: { imdb: 'tt0068646', year: '1972-03-14', lang: 'en', title: 'Le Parrain', fr: [] },
        streams: ['The.Godfather.1972.MULTi.TRUEFRENCH.1080p.mkv'],
        attendu: 1
    },
    {
        nom: 'Pas VF / 720p / polonais : écartés',
        args: ['1500004', 'movie'],
        movie: { imdb: 'tt90000004', year: '2025-02-01', lang: 'en', title: 'X', fr: [] },
        streams: ['X.2025.ENG.1080p.mkv', 'X.2025.FRENCH.720p.mkv', 'X.2025.MULTi.POLISH.1080p.mkv', 'X.2025.VOSTFR.1080p.mkv', 'X.2025.FRENCH.1080p.mkv'],
        garde: ['X.2025.FRENCH.1080p'], jette: ['ENG', '720p', 'POLISH', 'VOSTFR']
    }
];

(async () => {
    let fails = 0;
    for (const c of cases) {
        const fixture = url => {
            if (url.includes('/external_ids')) return { imdb_id: c.args[0].startsWith('tt') ? c.args[0] : 'tt' + c.args[0] };
            if (url.includes('/season/')) return { air_date: c.air };
            if (url.includes('api.themoviedb.org/3/movie/') && c.movie) return {
                original_language: c.movie.lang, release_date: c.movie.year, title: c.movie.title, original_title: c.movie.title,
                external_ids: { imdb_id: c.movie.imdb }, release_dates: { results: [{ iso_3166_1: 'FR', release_dates: c.movie.fr }] }
            };
            if (url.includes('/find/')) return { tv_results: [{ id: 1 }] };
            if (url.startsWith('https://addon.test/cfg/stream/')) return { streams: c.streams.map(stream) };
            return undefined;
        };
        const plugin = loadTemplate(fixture);
        const out = await plugin.getStreams(...c.args);
        const titles = out.map(s => s.title.split('\n')[0]);
        const problems = [];
        if (c.attendu !== undefined && out.length !== c.attendu) problems.push(`attendu ${c.attendu} flux, reçu ${out.length}`);
        for (const g of c.garde || []) if (!titles.some(t => t.includes(g))) problems.push(`manque « ${g} »`);
        for (const j of c.jette || []) if (titles.some(t => t.includes(j))) problems.push(`« ${j} » aurait dû être écarté`);
        if (problems.length) { fails++; console.log(`✗ ${c.nom} — ${problems.join(' ; ')} — reçu : ${titles.join(' | ') || 'rien'}`); }
        else console.log(`✓ ${c.nom}`);
    }
    // Comet666 : règle saison/épisode
    const comet = require(path.join(__dirname, '..', 'comet666.js'));
    const m = comet._matchesEpisode;
    const ce = [
        ['Monster.The.Lizzie.Borden.Story.S01E05', 4, 5, true],
        ['Show.S04E05', 4, 5, true],
        ['Show.S03E05', 4, 5, false],
        ['Show.S01E06', 1, 5, false],
        ['Show.S04.FRENCH.1080p', 4, 5, true],
        ['Show.INTEGRALE.FRENCH', 2, 3, true]
    ];
    for (const [t, s, e, want] of ce) {
        const got = m(t, s, e);
        if (got !== want) { fails++; console.log(`✗ Comet666 ${t} (S${s}E${e}) → ${got}, attendu ${want}`); }
        else console.log(`✓ Comet666 ${t} (S${s}E${e})`);
    }
    // Comet666 : AIOSources (Torznab, trackers français)
    const base = comet._aioSourcesBase;
    for (const [raw, want] of [
        ['https://x.example/torznab/abc /api CLE', 'https://x.example/torznab/abc/api?apikey=CLE'],
        ['https://x.example/torznab/abc/api?apikey=CLE', 'https://x.example/torznab/abc/api?apikey=CLE'],
        ['', null]
    ]) {
        const got = base(raw);
        if (got !== want) { fails++; console.log(`✗ AIOSources lien « ${raw.replace(/CLE/, '…')} » → ${got}`); }
        else console.log(`✓ AIOSources lien (${raw ? raw.split(/\s+/).length + ' morceau(x)' : 'vide'})`);
    }
    const xml = '<rss><channel><item><title>The.Godfather.1972.MULTi.VFF.1080p</title><size>9000000000</size>' +
        '<jackettindexer id="c411">C411</jackettindexer><torznab:attr name="infohash" value="0123456789abcdef0123456789abcdef01234567"/>' +
        '<torznab:attr name="seeders" value="12"/></item>' +
        '<item><title>Monster.The.Lizzie.Borden.Story.S01E05.MULTi.1080p</title><torznab:attr name="infohash" value="1123456789abcdef0123456789abcdef01234567"/></item>' +
        '<item><title>Show.S04E01.MULTi.1080p</title><torznab:attr name="infohash" value="2123456789abcdef0123456789abcdef01234567"/></item></channel></rss>';
    const film = comet._parseTorznab(xml, false, 0, 0, 'AIOSources');
    if (film.length !== 3 || film[0].source !== 'AIOSources · C411' || film[0].seeders !== 12) { fails++; console.log('✗ AIOSources lecture Torznab'); }
    else console.log('✓ AIOSources lecture Torznab (+ nom du tracker)');
    const ep = comet._parseTorznab(xml, true, 4, 5, 'AIOSources');
    if (ep.length !== 1 || !/Lizzie/.test(ep[0].release)) { fails++; console.log('✗ AIOSources Monstre S4E5 — reçu ' + ep.map(e => e.release).join(' | ')); }
    else console.log('✓ AIOSources Monstre S4E5 (S01E05, sans S04E01)');
    // AIOSources : recherche par titre — homonymes et autre année écartés
    const rm = comet._releaseMatches;
    for (const [rel, titles, year, tv, want] of [
        ['The.Matrix.1999.MULTi.VFF.2160p', ['Matrix', 'The Matrix'], 1999, false, true],
        ['Matrix.Trilogy.1999-2003.MULTI.TRUEFRENCH.2160p', ['Matrix', 'The Matrix'], 1999, false, true],
        ['The.Matrix.Resurrections.2021.MULTi.1080p', ['Matrix', 'The Matrix'], 1999, false, false],
        ['Kraken.2021.FRENCH.1080p', ['Kraken'], 2026, false, false],
        ['Le.Parrain.1972.MULTi.1080p', ['Le Parrain', 'The Godfather'], 1972, false, true],
        ['Monster.The.Lizzie.Borden.Story.S01E05.MULTi', ['Monstre', 'Monster'], 2022, true, true],
        ['Godzilla.Minus.One.2023.MULTi', ['Le Parrain', 'The Godfather'], 1972, false, false]
    ]) {
        const got = rm(rel, titles, year, tv);
        if (got !== want) { fails++; console.log(`✗ AIOSources titre ${rel} → ${got}, attendu ${want}`); }
        else console.log(`✓ AIOSources titre ${rel}`);
    }
    console.log(fails ? `\n${fails} cas piège(s) cassé(s)` : '\nTous les cas pièges passent');
    process.exit(fails ? 1 : 0);
})();
