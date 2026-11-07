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
        nom: 'AHS S13 (26/10) : MULTi d\'un groupe étranger écarté, d\'un groupe français gardé',
        args: ['tt1844624', 'tv', 13, 1],
        air: '2026-10-20',
        streams: ['American.Horror.Story.S13E01.MULTi.2160p.DSNP.WEB-DL.HEVC-Ralf.mkv', 'American.Horror.Story.S13E01.MULTi.1080p.WEB.H264-QTZ.mkv'],
        groupes: { fr: ['QTZ'], other: ['RALF'] },
        garde: ['-QTZ'], jette: ['-Ralf']
    },
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
            if (url.includes('/groupes-vf')) return c.groupes || { fr: [], other: [] };
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
    // Config morte en silence (Wastream, 26/10) : le lien répond mais 0 flux →
    // au bouton Tester, la 1re ligne doit le dire clairement.
    {
        const plugin = loadTemplate(url => {
            if (url.includes('api.themoviedb.org/3/movie/603')) return { title: 'Matrix', original_title: 'The Matrix', release_date: '1999-03-31', external_ids: { imdb_id: 'tt0133093' }, release_dates: { results: [] } };
            if (url.startsWith('https://addon.test/cfg/stream/')) return { streams: [] };
            return undefined;
        });
        const out = await plugin.getStreams('603', 'movie');
        const first = (out[0] && out[0].title) || '';
        if (!/^0 flux reçus/.test(first)) { fails++; console.log(`✗ Config morte : 1re ligne du test = « ${first} »`); }
        else console.log('✓ Config morte (0 flux) signalée en 1re ligne du test');
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
        ['Show.INTEGRALE.FRENCH', 2, 3, true],
        ['American.Horror.Story.S13E01-06.MULTi.1080p', 13, 5, true],
        ['American.Horror.Story.S13E01-06.MULTi.1080p', 13, 7, false],
        ['Show.S02E01-E04.FRENCH', 2, 3, true]
    ];
    for (const [t, s, e, want] of ce) {
        const got = m(t, s, e);
        if (got !== want) { fails++; console.log(`✗ Comet666 ${t} (S${s}E${e}) → ${got}, attendu ${want}`); }
        else console.log(`✓ Comet666 ${t} (S${s}E${e})`);
    }
    // AHS S13 (26/10) : « MULTi » sans mot français = incertain, signalé « (VF ?) »
    // (le calendrier 666TVF ne compte pas ces flux comme de la VF).
    if (comet._frenchLabel('American.Horror.Story.S13E01-06.MULTi.2160p.DSNP.WEB-DL.DD5.1.HEVC-Ralf') !== 'MULTi (VF ?)') {
        fails++; console.log('✗ AHS S13 MULTi doit rester « (VF ?) »');
    } else console.log('✓ AHS S13 MULTi marqué « (VF ?) » (non compté par le calendrier)');
    // Sources françaises reprises de Frenchio : C411 (avec clé) et YGG (via Frank)
    {
        const saved = { fetch: global.fetch, settings: global.SCRAPER_SETTINGS };
        global.SCRAPER_SETTINGS = { c411: 'CLE' };
        const xml = '<rss><channel><item><title>The.Matrix.1999.MULTi.VFF.2160p.UHD-QTZ</title><size>5</size><torznab:attr name="infohash" value="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"/></item></channel></rss>';
        const urls = [];
        global.fetch = async u => {
            u = String(u); urls.push(u);
            const ok = body => ({ ok: true, status: 200, text: async () => typeof body === 'string' ? body : JSON.stringify(body), json: async () => body });
            if (u.startsWith('https://c411.org/api')) return ok(xml);
            if (u.includes('/ygg/movie/')) return ok({ titles: ['Matrix', 'The Matrix'], year: 1999, items: [
                { title: 'The.Matrix.1999.MULTi.TRUEFRENCH.1080p.BluRay-FW', hash: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', size: 9 },
                { title: 'The.Matrix.Resurrections.2021.MULTi.VFF.1080p-FW', hash: 'cccccccccccccccccccccccccccccccccccccccc', size: 9 }
            ] });
            return { ok: false, status: 404, text: async () => '', json: async () => null };
        };
        const out = await comet.getStreams('tt0133093', 'movie');
        const lines = out.map(x => x.title);
        const has = k => lines.some(t => t.includes(k));
        const keyHidden = !out.some(x => JSON.stringify(x).includes('CLE'));
        if (!has('C411') || has('Resurrections') || !keyHidden || !urls.some(u => u.includes('apikey=CLE'))) {
            fails++; console.log('✗ Sources françaises : ' + lines.map(t => t.split('\n')[0] + ' / ' + (t.split('\n')[2] || '')).join(' | '));
        } else console.log('✓ Sources françaises : C411 (clé jamais affichée)');
        global.SCRAPER_SETTINGS = {};
        urls.length = 0;
        await comet.getStreams('tt0133093', 'movie');
        if (urls.some(u => u.includes('c411.org'))) { fails++; console.log('✗ C411 interrogé sans clé'); }
        else console.log('✓ Sans clé, C411 n\'est pas interrogé');
        delete global.wsRequest;
        global.fetch = saved.fetch; global.SCRAPER_SETTINGS = saved.settings;
    }
    // Qualité (06/11) : 1080p, 1440p et 4K gardés, jamais de 720p
    if (comet._qualityOf ? (comet._qualityOf('Film.2026.FRENCH.1440p.WEB') !== '1440p' || comet._qualityOf('Film.2026.FRENCH.720p.WEB') !== '720p') : false) {
        fails++; console.log('✗ Qualité 1440p / 720p mal lue');
    } else console.log('✓ 1440p reconnu, 720p toujours écarté');
    // YggReborn (06/11) : recherche par titre avec la passkey, clé jamais affichée, bon titre seulement
    {
        const saved = { fetch: global.fetch, settings: global.SCRAPER_SETTINGS };
        global.SCRAPER_SETTINGS = { yggreborn: 'PASSKEY' };
        const xml = '<rss><channel>' +
            '<item><title>La.Quatrieme.Dimension.S03E04.FRENCH.1080p.WEB</title><size>9</size><torznab:attr name="infohash" value="dddddddddddddddddddddddddddddddddddddddd"/></item>' +
            '<item><title>Twilight.Zone.2019.S03E04.MULTi.VFF.1080p</title><size>9</size><torznab:attr name="infohash" value="eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"/></item>' +
            '<item><title>La.Quatrieme.Dimension.S03E05.FRENCH.1080p.WEB</title><size>9</size><torznab:attr name="infohash" value="ffffffffffffffffffffffffffffffffffffffff"/></item>' +
            '</channel></rss>';
        const urls = [];
        global.fetch = async u => {
            u = String(u); urls.push(u);
            const ok = body => ({ ok: true, status: 200, text: async () => typeof body === 'string' ? body : JSON.stringify(body), json: async () => body });
            if (u.startsWith('https://www.yggreborn.org/api')) return ok(xml);
            if (u.includes('/ygg/series/')) return ok({ titles: ['La Quatrième Dimension', 'The Twilight Zone'], year: 1959,
                queries: ['La Quatrième Dimension S03E04', 'La Quatrième Dimension S03'], items: [] });
            return { ok: false, status: 404, text: async () => '', json: async () => null };
        };
        const out = await comet.getStreams('tt0052520', 'tv', 3, 4);
        const keyHidden = !out.some(x => JSON.stringify(x).includes('PASSKEY'));
        const hashes = out.map(x => String(x.infoHash || x.url || '')).join(' ');
        const okFound = hashes.includes('dddd') && !hashes.includes('ffff');
        if (!okFound || !keyHidden || !urls.some(u => u.includes('apikey=PASSKEY') && u.includes('t=search'))) {
            fails++; console.log('✗ YggReborn : ' + out.map(x => (x.title || '').split('\n')[0]).join(' | '));
        } else console.log('✓ YggReborn : bon épisode trouvé par le titre, passkey jamais affichée');
        global.SCRAPER_SETTINGS = {};
        urls.length = 0;
        await comet.getStreams('tt0052520', 'tv', 3, 4);
        if (urls.some(u => u.includes('yggreborn'))) { fails++; console.log('✗ YggReborn interrogé sans passkey'); }
        else console.log('✓ Sans passkey, YggReborn n\'est pas interrogé');
        global.fetch = saved.fetch; global.SCRAPER_SETTINGS = saved.settings;
    }
    // WWE Raw (27/10) : Comet666 avait lancé un vieux Raw (fichier daté d'un autre jour)
    {
        const dm = comet._dateMismatch;
        const cases = [
            ['WWE.Monday.Night.Raw.2026.10.19.FRENCH.1080p', '2026-10-26', true],
            ['WWE.Monday.Night.Raw.2026.10.26.FRENCH.1080p', '2026-10-26', false],
            ['WWE.Monday.Night.Raw.2026.10.27.FRENCH.1080p', '2026-10-26', false],
            ['Show.S01E05.FRENCH.1080p', '2026-10-26', false]
        ];
        for (const [t, d, want] of cases) {
            if (dm(t, d) !== want) { fails++; console.log(`✗ Date ${t} / ${d} → ${!want}`); }
            else console.log(`✓ Date ${t} / ${d}`);
        }
        const saved = { fetch: global.fetch, key: global.TMDB_API_KEY, settings: global.SCRAPER_SETTINGS };
        global.TMDB_API_KEY = 'K'; global.SCRAPER_SETTINGS = {};
        const json = o => ({ ok: true, json: async () => o, text: async () => JSON.stringify(o) });
        global.fetch = async u => {
            if (u.includes('/find/')) return json({ tv_results: [{ id: 4656 }] });
            if (u.includes('/episode/')) return json({ air_date: '2026-10-26' });
            if (u.includes('torrentio')) return json({ streams: [
                { infoHash: 'a'.repeat(40), title: 'WWE.Monday.Night.Raw.2026.10.19.FRENCH.1080p.WEB.h264\n👤 50 💾 3 GB' },
                { infoHash: 'b'.repeat(40), title: 'WWE.Monday.Night.Raw.2026.10.26.FRENCH.1080p.WEB.h264\n👤 20 💾 3 GB' }
            ] });
            return { ok: false, json: async () => null, text: async () => '' };
        };
        const out = await comet.getStreams('tt0185103', 'tv', 2026, 43);
        const titles = out.map(x => x.title.split('\n')[0]).join(' | ');
        if (titles.includes('2026.10.19') || !titles.includes('2026.10.26')) { fails++; console.log('✗ Raw : vieux Raw gardé → ' + titles); }
        else console.log('✓ Raw : seul le Raw du bon jour est gardé');
        global.fetch = saved.fetch; global.TMDB_API_KEY = saved.key; global.SCRAPER_SETTINGS = saved.settings;
    }
    // TorBox search (07/11) : nouvelle source, clé passée par 666TVF uniquement
    {
        const p = comet._parseTorboxSearch;
        const data = { success: true, data: { torrents: [
            { hash: 'C'.repeat(40), raw_title: 'The.Twilight.Zone.S01E05.MULTi.1080p.WEB-TEAM', size: 2e9, last_known_seeders: 12, cached: true },
            { hash: 'd'.repeat(40), raw_title: 'The.Twilight.Zone.S01E06.MULTi.1080p.WEB-TEAM', size: 2e9, last_known_seeders: 3 },
            { hash: 'pasunhash', raw_title: 'The.Twilight.Zone.S01E05.FRENCH.1080p', size: 1 }
        ] } };
        const out = p(data, true, 1, 5);
        if (out.length !== 1 || out[0].hash !== 'c'.repeat(40) || out[0].source !== 'TorBox ⚡') { fails++; console.log('✗ TorBox search mal lu → ' + JSON.stringify(out)); }
        else console.log('✓ TorBox search : bon épisode, hash valide, marqué ⚡ si en cache');
        if (p(null, false).length !== 0 || p({ success: false, data: null }, false).length !== 0) { fails++; console.log('✗ TorBox search : réponse vide mal gérée'); }
        else console.log('✓ TorBox search : réponse vide ou en erreur → rien');
        const saved = { fetch: global.fetch, settings: global.SCRAPER_SETTINGS };
        const urls = [];
        global.SCRAPER_SETTINGS = {};
        global.fetch = async u => { urls.push(u); return { ok: false, json: async () => null, text: async () => '' }; };
        await comet.getStreams('tt0052520', 'tv', 1, 5);
        if (urls.some(u => u.includes('torbox'))) { fails++; console.log('✗ TorBox search interrogé sans clé'); }
        else console.log('✓ Sans clé, TorBox search n\'est pas interrogé');
        global.fetch = saved.fetch; global.SCRAPER_SETTINGS = saved.settings;
    }
    // 4e dimension S03E06 (07/11) : YggReborn sans accents et avec l'année
    {
        const q = comet._yggQueries(['La Quatrième Dimension S03E06', 'La Quatrième Dimension S03', 'The Twilight Zone S03E06', 'The Twilight Zone S03'],
            ['La Quatrième Dimension', 'The Twilight Zone'], 1959, true, 3, 6);
        const want = ['La Quatrième Dimension S03E06', 'La Quatrieme Dimension S03E06', 'La 4eme Dimension S03E06', 'The Twilight Zone S03E06',
            'La Quatrième Dimension S03', 'La Quatrieme Dimension S03', 'La 4eme Dimension S03', 'The Twilight Zone S03'];
        if (JSON.stringify(q) !== JSON.stringify(want)) { fails++; console.log('✗ Requêtes YggReborn → ' + JSON.stringify(q)); }
        else console.log('✓ YggReborn : variantes sans accents et « 4eme », épisode exact d\'abord, 8 max');
        const f = comet._yggQueries(['Dune Deuxième Partie 2024'], ['Dune : Deuxième partie', 'Dune: Part Two'], 2024, false);
        if (JSON.stringify(f) !== JSON.stringify(['Dune Deuxième Partie 2024', 'Dune Deuxieme Partie 2024', 'Dune 2eme Partie 2024'])) { fails++; console.log('✗ Requêtes film → ' + JSON.stringify(f)); }
        else console.log('✓ YggReborn film : titre + variante sans accents');
    }
    // 4e dimension (07/11) : « La 4eme dimension REMUX S03 » trouvé par StreamFusion, pas par Comet666
    {
        const rel = 'La 4eme dimension REMUX S03';
        const titles = ['La Quatrième Dimension', 'The Twilight Zone'];
        const checks = [
            [comet._releaseMatches(rel, titles, 1959, true), true, 'titre « 4eme » reconnu'],
            [comet._matchesEpisode(rel, 3, 6), true, 'pack S03 garde S03E06'],
            [comet._qualityOf(rel), '1080p', 'REMUX sans définition = 1080p'],
            [comet._qualityOf('Film DVD REMUX'), null, 'REMUX DVD jamais en 1080p'],
            [comet._frenchTitleOnly(rel, titles), true, 'titre français seul = VF probable'],
            [comet._frenchTitleOnly('The.Twilight.Zone.1961.S03.1080p.BluRay', titles), false, 'titre original = pas de VF supposée'],
            [comet._normTitle('La 4e Dimension'), 'la 4eme dimension', '« 4e » = « 4eme »']
        ];
        for (const [got, want, label] of checks) {
            if (got !== want) { fails++; console.log('✗ 4e dimension : ' + label + ' → ' + got); }
            else console.log('✓ 4e dimension : ' + label);
        }
        const saved = { fetch: global.fetch, key: global.TMDB_API_KEY, settings: global.SCRAPER_SETTINGS };
        global.TMDB_API_KEY = ''; global.SCRAPER_SETTINGS = { yggreborn: 'PK' };
        const json = o => ({ ok: true, json: async () => o, text: async () => (typeof o === 'string' ? o : JSON.stringify(o)) });
        global.fetch = async u => {
            if (u.includes('/ygg/')) return json({ titles, year: 1959, queries: ['La Quatrième Dimension S03E06', 'La Quatrième Dimension S03'] });
            // les 1res requêtes répondent 3 VOSTFR / 720p : la recherche doit continuer jusqu'à « 4eme »
            if (u.includes('yggreborn') && !decodeURIComponent(u).includes('4eme'))
                return json('<rss>' + [1, 2, 3].map(i => '<item><title>La Quatrieme Dimension S03E06 VOSTFR 720p v' + i + '</title><torznab:attr name="infohash" value="' + String(i).repeat(40) + '"/></item>').join('') + '</rss>');
            if (u.includes('yggreborn') && decodeURIComponent(u).includes('La 4eme Dimension S03'))
                return json('<rss><item><title>La 4eme dimension REMUX S03</title><size>148000000000</size><torznab:attr name="infohash" value="' + 'e'.repeat(40) + '"/></item></rss>');
            return { ok: false, json: async () => null, text: async () => '' };
        };
        const out = await comet.getStreams('tt0052520', 'tv', 3, 6);
        const hit = out.find(x => x.infoHash === 'e'.repeat(40));
        if (!hit || !/VF \?/.test(hit.title)) { fails++; console.log('✗ 4e dimension S03E06 pas proposé (ou pas en « VF ? ») → ' + JSON.stringify(out.map(x => x.title))); }
        else console.log('✓ 4e dimension S03E06 proposé en « VF ? » via YggReborn');
        global.fetch = saved.fetch; global.TMDB_API_KEY = saved.key; global.SCRAPER_SETTINGS = saved.settings;
    }
    // Mode enquête (07/11) : rien de lisible → Comet666 dit pourquoi, source par source
    {
        const saved = { fetch: global.fetch, key: global.TMDB_API_KEY, settings: global.SCRAPER_SETTINGS };
        global.TMDB_API_KEY = ''; global.SCRAPER_SETTINGS = { yggreborn: 'PK' };
        const json = o => ({ ok: true, json: async () => o, text: async () => (typeof o === 'string' ? o : JSON.stringify(o)) });
        global.fetch = async u => {
            if (u.includes('/ygg/')) return json({ titles: ['La Quatrième Dimension', 'The Twilight Zone'], year: 1959, queries: ['La Quatrième Dimension S03E06'] });
            if (u.includes('yggreborn')) return json('<rss>'
                + '<item><title>La Quatrieme Dimension S03E06 VOSTFR 1080p</title><torznab:attr name="infohash" value="' + '1'.repeat(40) + '"/></item>'
                + '<item><title>La Quatrieme Dimension S03E07 FRENCH 1080p</title><torznab:attr name="infohash" value="' + '2'.repeat(40) + '"/></item>'
                + '</rss>');
            if (u.includes('torrentio')) return json({ streams: [{ infoHash: '3'.repeat(40), title: 'The.Twilight.Zone.S03E06.FRENCH.720p.WEB\n👤 5' }] });
            return { ok: false, json: async () => null, text: async () => '' };
        };
        const out = await comet.getStreams('tt0052520', 'tv', 3, 6);
        const txt = out.map(x => x.title).join(' | ');
        const okLines = out.length >= 2 && out.every(x => x.name === 'Comet666 enquête' && !x.infoHash);
        if (!okLines || !/YggReborn : \d+ recherche/.test(txt) || !/1 VOSTFR/.test(txt) || !/autre épisode/.test(txt) || !/720p/.test(txt)) {
            fails++; console.log('✗ Mode enquête → ' + txt);
        } else console.log('✓ Mode enquête : VOSTFR, autre épisode et 720p expliqués, aucun lien lançable');
        // un lien proposable → pas de ligne d'enquête
        global.fetch = async u => {
            if (u.includes('torrentio')) return json({ streams: [{ infoHash: '4'.repeat(40), title: 'The.Twilight.Zone.S03E06.FRENCH.1080p.WEB\n👤 5' }] });
            return { ok: false, json: async () => null, text: async () => '' };
        };
        global.SCRAPER_SETTINGS = {};
        const out2 = await comet.getStreams('tt0052520', 'tv', 3, 6);
        if (out2.some(x => x.name === 'Comet666 enquête')) { fails++; console.log('✗ Ligne d\'enquête alors qu\'un lien existe'); }
        else console.log('✓ Pas d\'enquête quand un lien VF 1080p existe');
        global.fetch = saved.fetch; global.TMDB_API_KEY = saved.key; global.SCRAPER_SETTINGS = saved.settings;
    }
    // 4e dimension (07/11) : sources lentes → Comet666 ne dépasse jamais son budget, et le dit
    {
        const saved = { fetch: global.fetch, key: global.TMDB_API_KEY, settings: global.SCRAPER_SETTINGS };
        global.TMDB_API_KEY = ''; global.SCRAPER_SETTINGS = { yggreborn: 'PK' };
        comet._budget(300);
        const json = o => ({ ok: true, json: async () => o, text: async () => (typeof o === 'string' ? o : JSON.stringify(o)) });
        global.fetch = async u => {
            if (u.includes('/ygg/')) return json({ titles: ['La Quatrième Dimension', 'The Twilight Zone'], year: 1959, queries: ['La Quatrième Dimension S03E06', 'La Quatrième Dimension S03'] });
            if (u.includes('yggreborn')) { await new Promise(r => setTimeout(r, 200)); return json('<rss></rss>'); }
            return { ok: false, json: async () => null, text: async () => '' };
        };
        const t0 = Date.now();
        const out = await comet.getStreams('tt0052520', 'tv', 3, 6);
        const ms = Date.now() - t0;
        const txt = out.map(x => x.title).join(' | ');
        if (ms > 1500 || !/pas interrogé \(temps écoulé\)|coupées/.test(txt)) { fails++; console.log('✗ Budget de temps (' + ms + ' ms) → ' + txt); }
        else console.log('✓ Sources lentes : Comet666 s\'arrête à temps et le dit (' + ms + ' ms)');
        comet._budget(55000);
        global.fetch = saved.fetch; global.TMDB_API_KEY = saved.key; global.SCRAPER_SETTINGS = saved.settings;
    }
    // Traces d'enquête envoyées à Frank (07/11) : jamais une clé ni une passkey
    {
        const saved = { fetch: global.fetch, key: global.TMDB_API_KEY, settings: global.SCRAPER_SETTINGS };
        global.TMDB_API_KEY = ''; global.SCRAPER_SETTINGS = { yggreborn: 'PASSKEY-SECRETE', c411: 'CLE-C411', torbox_auto: 'CLE-TORBOX' };
        const bodies = [];
        const json = o => ({ ok: true, json: async () => o, text: async () => (typeof o === 'string' ? o : JSON.stringify(o)) });
        global.fetch = async (u, opts) => {
            if (u.includes('/comet/trace')) { bodies.push(String(opts && opts.body)); return json({ ok: true }); }
            if (u.includes('/ygg/')) return json({ titles: ['La Quatrième Dimension', 'The Twilight Zone'], year: 1959, queries: ['La Quatrième Dimension S03E06'] });
            if (u.includes('yggreborn')) return json('<rss><item><title>La 4eme dimension REMUX S03</title><torznab:attr name="infohash" value="' + 'e'.repeat(40) + '"/></item></rss>');
            return { ok: false, json: async () => null, text: async () => '' };
        };
        await comet.getStreams('tt0052520', 'tv', 3, 6);
        const all = bodies.join(' ');
        if (bodies.length !== 2 || /PASSKEY-SECRETE|CLE-C411|CLE-TORBOX|apikey/i.test(all) || !/La 4eme dimension REMUX S03/.test(all)) {
            fails++; console.log('✗ Traces Frank → ' + bodies.length + ' envois · ' + all.slice(0, 300));
        } else console.log('✓ Traces Frank : début + fin, titres trouvés, aucune clé');
        global.fetch = saved.fetch; global.TMDB_API_KEY = saved.key; global.SCRAPER_SETTINGS = saved.settings;
    }
    // 4e dimension (07/11) : le REMUX est dans le compte AllDebrid (ajouté par StreamFusion), pas sur YggReborn
    {
        const titles = ['La Quatrième Dimension', 'The Twilight Zone'];
        const H = 'f'.repeat(40);
        const acct = { status: 'success', data: { magnets: [
            { hash: H, filename: 'La 4eme dimension REMUX S03', size: 148e9, statusCode: 4, status: 'Ready' },
            { hash: 'a'.repeat(40), filename: 'The.Matrix.1999.2160p.UHD.BLURAY.REMUX-tlub.mkv', size: 5e10, statusCode: 4 },
            { hash: 'b'.repeat(40), filename: 'La 4eme dimension REMUX S04', size: 1e11, statusCode: 4 },
            { hash: 'c'.repeat(40), filename: 'La 4eme dimension REMUX S03', size: 1e11, statusCode: 1, status: 'Downloading' }
        ] } };
        const got = comet._parseAllDebridAccount(acct, titles, 1959, true, 3, 6);
        if (got.length !== 1 || got[0].hash !== H || !got[0].frTitle) { fails++; console.log('✗ Compte AllDebrid mal lu → ' + JSON.stringify(got)); }
        else console.log('✓ Compte AllDebrid : seul le REMUX S03 prêt correspond à S03E06');
        const saved = { fetch: global.fetch, key: global.TMDB_API_KEY, settings: global.SCRAPER_SETTINGS };
        global.TMDB_API_KEY = ''; global.SCRAPER_SETTINGS = { alldebrid_auto: 'CLE-AD' };
        const json = o => ({ ok: true, json: async () => o, text: async () => JSON.stringify(o) });
        let bodies = [];
        global.fetch = async (u, o) => {
            if (u.includes('/comet/trace')) { bodies.push(String(o && o.body)); return json({ ok: true }); }
            if (u.includes('/ygg/')) return json({ titles, year: 1959, queries: [] });
            if (u.includes('api.alldebrid.com')) return (o && o.headers && o.headers.Authorization === 'Bearer CLE-AD') ? json(acct) : { ok: false };
            return { ok: false, json: async () => null, text: async () => '' };
        };
        const out = await comet.getStreams('tt0052520', 'tv', 3, 6);
        const hit = out.find(x => x.infoHash === H);
        if (!hit || !/VF \?/.test(hit.title) || /CLE-AD/.test(bodies.join(' '))) { fails++; console.log('✗ 4e dimension via compte AllDebrid → ' + JSON.stringify(out.map(x => x.title))); }
        else console.log('✓ 4e dimension S03E06 sort de ton compte AllDebrid (VF ?), clé jamais envoyée à Frank');
        global.fetch = saved.fetch; global.TMDB_API_KEY = saved.key; global.SCRAPER_SETTINGS = saved.settings;
    }
    // SmackDown S28E45 (07/11) : YggReborn ne cherchait que « S28E45 », la release est datée
    {
        const q = comet._yggQueries(['WWE SmackDown S28E45', 'WWE SmackDown S28'], ['WWE SmackDown'], 1999, true, 28, 45, '2026-11-06');
        if (q.indexOf('WWE SmackDown 2026.11.06') < 0 || q.indexOf('WWE SmackDown 2026.11.06') > 2) { fails++; console.log('✗ Recherche par date → ' + JSON.stringify(q)); }
        else console.log('✓ YggReborn cherche aussi « WWE SmackDown 2026.11.06 », juste après l\'épisode');
        const f = comet._yggQueries(['Show S01E02'], ['Show'], 2020, true, 1, 2, null);
        if (f.some(x => /20\d\d\.\d\d/.test(x))) { fails++; console.log('✗ Date ajoutée sans date de diffusion'); }
        else console.log('✓ Sans date de diffusion, pas de recherche par date');
    }
    // SmackDown (07/11) : la release datée du bon jour est le bon épisode ; une autre date non
    {
        comet._setAirDate('2026-11-06');
        const ok = comet._matchesEpisode('WWE.SmackDown.2026.11.06.MULTI.VFF.1080p.WEB.AAC.h265-HEEL.mkv', 28, 45);
        const old = comet._matchesEpisode('WWE.SmackDown.2026.10.31.MULTI.VFF.1080p.WEB.AAC.h265-HEEL.mkv', 28, 45);
        comet._setAirDate(null);
        const none = comet._matchesEpisode('WWE.SmackDown.2026.11.06.MULTI.VFF.1080p.WEB-HEEL', 28, 45);
        if (!ok || old || none) { fails++; console.log('✗ Épisode daté → bon jour ' + ok + ' · autre date ' + old + ' · sans date connue ' + none); }
        else console.log('✓ SmackDown daté : bon jour gardé, 31/10 écarté, rien sans date de diffusion');
    }
    console.log(fails ? `\n${fails} cas piège(s) cassé(s)` : '\nTous les cas pièges passent');
    process.exit(fails ? 1 : 0);
})();
