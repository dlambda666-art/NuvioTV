/*
 * Comet666 — plugin de flux VF pour 666TV.
 * Tourne sur la Shield (QuickJS). Cherche des torrents dans des index publics,
 * ne garde que la VF (pas de VOSTFR seule, pas de version cinéma) et renvoie
 * l'empreinte : c'est l'intégration debrid de 666TV (TorBox) qui fournit le
 * lien. 666TV ne lit jamais un torrent en P2P.
 */

var TORRENTIO_URL = 'https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9|language=french|qualityfilter=720p,480p,other,scr,cam,unknown';
var ZILEAN_URL = 'https://zileanfortheweebs.midnightignite.me';
var STREMTHRU_URL = 'https://stremthru.13377001.xyz';
var TORRENTSDB_URL = 'https://torrentsdb.com';
var MAX_RESULTS = 40;

// --- Règles de langue (mêmes que Frank) -----------------------------------
var SUBTITLE_ONLY = /\bVOST(?:FR)?\b|\bSTFR\b|\bSUB\.?FR(?:ENCH)?\b|\bSUBFRENCH\b|\bFRENCH[ ._-]*SUB(?:S|BED|TITLE[SD]?)?\b|\bSOUS[ ._-]*TITR/i;
var FRENCH_AUDIO = /\bVF[FQI2]?\b|\bTRUE[ ._-]*FRENCH\b|\bFRENCH\b|\bVERSION FRAN[CÇ]AISE\b|\bVOF\b/i;
var MULTI = /\bMULTI(?:[ ._-]*(?:VFF|VFQ|VF2|TRUEFRENCH|FRENCH))?\b/i;
var FRENCH_FLAG = /🇫🇷/;
var CAM_RELEASE = /\b(?:HD[ ._-]?)?CAM(?:[ ._-]?RIP)?\b|\b(?:HD[ ._-]?)?TS\b|\bTELE[ ._-]?SYNC\b|\b(?:HD[ ._-]?)?TC\b|\bTELE[ ._-]?CINE\b|\bMD\b|\bMICRO[ ._-]?DUBBED\b|\bSCR(?:EENER)?\b/i;

// autre doublage annoncé (polonais, italien…) : un « MULTI » sans mot français est alors suspect
var OTHER_DUB = /\b(?:POLISH|PLDUB|PL|LEKTOR|DUBBING|ITA|ITALIAN|GER|GERMAN|DEUTSCH|SPANISH|CASTELLANO|LATINO|RUS|RUSSIAN|UKR|HUN|CZE|CZECH|TUR|TURKISH|POR|PORTUGUESE|HINDI|TAMIL)\b|🇵🇱|🇮🇹|🇩🇪|🇪🇸|🇷🇺|🇺🇦|🇹🇷|🇵🇹|🇧🇷|🇭🇺|🇨🇿|🇲🇽/i;

function frenchLabel(text) {
    if (SUBTITLE_ONLY.test(text) || CAM_RELEASE.test(text)) return null;
    if (!FRENCH_AUDIO.test(text) && OTHER_DUB.test(text)) return null;
    if (/\bVFQ\b/i.test(text)) return 'VFQ';
    if (/\bVFF\b|\bTRUE[ ._-]*FRENCH\b/i.test(text)) return 'VFF';
    if (/\bVF2\b/i.test(text)) return 'VF2';
    if (FRENCH_AUDIO.test(text)) return 'VF';
    if (MULTI.test(text) && FRENCH_FLAG.test(text)) return 'MULTI VF';
    // « MULTi » avec un i minuscule : convention de la scène française (VO + VF)
    if (/\bMULTi\b/.test(text)) return 'MULTi (VF ?)';
    // langue française repérée par l'index (DMM/Zilean)
    if (MULTI.test(text) && /\[lang:[^\]]*\bfr/i.test(text)) return 'MULTI VF';
    return null;
}

function qualityOf(text) {
    if (/\b(2160p|4k|uhd)\b/i.test(text)) return '2160p';
    if (/\b1440p\b|\b2K\b|\bQHD\b/i.test(text)) return '1440p';   // 06/11 : accepté aussi
    if (/\b1080p\b/i.test(text)) return '1080p';
    if (/\b720p\b/i.test(text)) return '720p';
    // 666TVF (07/11) : « La 4eme dimension REMUX S03 » ne dit pas sa définition ; un REMUX Blu-ray = 1080p
    if (/\bREMUX\b/i.test(text) && !/\bDVD/i.test(text)) return '1080p';
    if (/\b480p\b/i.test(text)) return '480p';
    return null;
}
var QUALITY_RANK = { '2160p': 4, '1440p': 3.5, '1080p': 3, '720p': 2, '480p': 1 };

function extras(text) {
    var tags = [];
    if (/\bREMUX\b/i.test(text)) tags.push('REMUX');
    if (/\b(DV|DoVi|Dolby[ ._-]?Vision)\b/i.test(text)) tags.push('DV');
    if (/\bHDR10\+|HDR10PLUS\b/i.test(text)) tags.push('HDR10+');
    else if (/\bHDR\b/i.test(text)) tags.push('HDR');
    if (/\bATMOS\b/i.test(text)) tags.push('Atmos');
    if (/\b(TRUEHD|DTS[ ._-]?HD|DTS[ ._-]?X)\b/i.test(text)) tags.push('HD audio');
    return tags;
}

function sizeToBytes(text) {
    var m = String(text || '').match(/([\d.,]+)\s*(TB|GB|MB)/i);
    if (!m) return 0;
    var n = parseFloat(m[1].replace(',', '.'));
    var u = m[2].toUpperCase();
    return n * (u === 'TB' ? 1e12 : u === 'GB' ? 1e9 : 1e6);
}

function formatSize(bytes) {
    if (!bytes) return '';
    return bytes >= 1e9 ? (bytes / 1e9).toFixed(1) + ' Go' : Math.round(bytes / 1e6) + ' Mo';
}

async function getJson(url) {
    try {
        var res = await fetch(url, { headers: { 'Accept': 'application/json' } });
        if (!res || !res.ok) return null;
        return await res.json();
    } catch (e) {
        console.log('Comet666 fetch error ' + url + ' : ' + (e && e.message));
        return null;
    }
}

// --- Identifiants ---------------------------------------------------------
async function resolveIds(tmdbId, mediaType) {
    var id = String(tmdbId || '').replace(/^tmdb:/i, '');
    if (/^tt\d+$/i.test(id)) return { imdbId: id };
    var isTv = mediaType === 'tv' || mediaType === 'series';
    if (typeof TMDB_API_KEY !== 'undefined' && TMDB_API_KEY) {
        var data = await getJson('https://api.themoviedb.org/3/' + (isTv ? 'tv' : 'movie') + '/' + id +
            '/external_ids?api_key=' + encodeURIComponent(TMDB_API_KEY));
        if (data && data.imdb_id) return { imdbId: data.imdb_id };
    }
    return null;   // 666TVF (09/11) : plus de Frank
}

// --- Sources --------------------------------------------------------------
async function fromTorrentio(imdbId, isTv, season, episode) {
    var sid = isTv ? imdbId + ':' + season + ':' + episode : imdbId;
    var data = await getJson(TORRENTIO_URL + '/stream/' + (isTv ? 'series' : 'movie') + '/' + sid + '.json');
    var out = [];
    ((data && data.streams) || []).forEach(function (s) {
        if (!s.infoHash) return;
        var text = (s.title || '') + '\n' + (s.name || '');
        var release = String(s.title || '').split('\n')[0];
        var seeders = (text.match(/👤\s*(\d+)/) || [])[1];
        var size = (text.match(/💾\s*([\d.,]+\s*[GMT]B)/i) || [])[1];
        out.push({
            hash: s.infoHash.toLowerCase(), release: release, text: text,
            bytes: sizeToBytes(size), seeders: seeders ? parseInt(seeders, 10) : null,
            source: ((text.match(/⚙️\s*([^\n]+)/) || [])[1] || 'Torrentio').trim()
        });
    });
    return out;
}

async function fromZilean(imdbId, isTv, season, episode) {
    var url = ZILEAN_URL + '/dmm/filtered?ImdbId=' + imdbId + (isTv ? '&Season=' + season + '&Episode=' + episode : '');
    var data = await getJson(url);
    var out = [];
    (Array.isArray(data) ? data : []).forEach(function (t) {
        var hash = t.info_hash || t.infoHash;
        var title = t.raw_title || t.rawTitle || '';
        if (!hash || !title) return;
        var langs = (t.languages || []).join(',');
        out.push({ hash: String(hash).toLowerCase(), release: title, text: title + (langs ? ' [lang:' + langs + ']' : ''), bytes: Number(t.size) || 0, seeders: null, source: 'DMM' });
    });
    return out;
}


function decodeEntities(t) {
    return String(t || '').replace(/&#(\d+);/g, function (_, n) { return String.fromCharCode(parseInt(n, 10)); })
        .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
}

// Pour une série : l'épisode demandé, la saison entière ou l'intégrale
function matchesEpisode(title, season, episode) {
    var t = String(title);
    // pack d'épisodes « S13E01-06 » / « S13E01-E06 » : tous les épisodes de la plage
    var rangeEp = t.match(/\bS(\d{1,2})[ ._-]?E(\d{1,3})[ ._-]*-[ ._-]*E?(\d{1,3})\b/i);
    if (rangeEp) {
        var rs = parseInt(rangeEp[1], 10), ra = parseInt(rangeEp[2], 10), rb = parseInt(rangeEp[3], 10);
        return rs === season && episode >= ra && episode <= Math.max(ra, rb);
    }
    var se = t.match(/\bS(\d{1,2})[ ._-]?E(\d{1,3})\b/i);
    // 666TV : même épisode dans une autre saison gardé en réserve (séries anthologie,
    // ex. « Monster: The Lizzie Borden Story S01E05 » pour Monstre S04E05).
    if (se) return parseInt(se[2], 10) === episode && (parseInt(se[1], 10) === season || (season > 1 && parseInt(se[1], 10) === 1));
    var sOnly = t.match(/\bS(\d{1,2})\b(?![ ._-]?E\d)/i) || t.match(/\bSaison[ ._-]?(\d{1,2})\b/i) || t.match(/\bSeason[ ._-]?(\d{1,2})\b/i);
    if (sOnly) {
        var range = t.match(/\bS(\d{1,2})[ ._-]?-[ ._-]?S?(\d{1,2})\b/i);
        if (range) return season >= parseInt(range[1], 10) && season <= parseInt(range[2], 10);
        return parseInt(sOnly[1], 10) === season;
    }
    // 666TVF (07/11) : émission datée sans SxxEyy (« WWE.SmackDown.2026.11.06 ») : le bon jour = le bon épisode
    if (AIR_DATE && /\b20\d\d[ ._-]\d\d[ ._-]\d\d\b/.test(t)) return !dateMismatch(t, AIR_DATE);
    return /\b(INTEGRALE|COMPLETE|COMPLETA|INT[ÉE]GRALE)\b/i.test(t);
}

// 666TVF : date de diffusion de l'épisode (TMDB) — émissions datées (WWE Raw, talk-shows…).
// Comet666 ne l'avait pas : il a lancé un vieux Raw le 27/10 (cas piège).
async function episodeAirDate(tmdbId, season, episode) {
    if (typeof TMDB_API_KEY === 'undefined' || !TMDB_API_KEY) return null;
    var id = String(tmdbId || '').replace(/^tmdb:/i, '');
    var key = encodeURIComponent(TMDB_API_KEY);
    if (/^tt\d+$/i.test(id)) {
        var f = await getJson('https://api.themoviedb.org/3/find/' + id + '?external_source=imdb_id&api_key=' + key);
        id = f && f.tv_results && f.tv_results[0] ? String(f.tv_results[0].id) : '';
    }
    if (!/^\d+$/.test(id)) return null;
    var ep = await getJson('https://api.themoviedb.org/3/tv/' + id + '/season/' + season + '/episode/' + episode + '?api_key=' + key);
    return ep && ep.air_date ? ep.air_date : null;
}

// Vrai si le nom porte une date (2026.10.19) qui n'est pas celle de l'épisode (± 1 jour 1/2).
function dateMismatch(text, airDate) {
    if (!airDate) return false;
    var target = Date.parse(airDate + 'T00:00:00Z');
    if (isNaN(target)) return false;
    var re = /\b(20\d\d)[ ._-](\d\d)[ ._-](\d\d)\b/g, m, any = false;
    while ((m = re.exec(String(text)))) {
        var d = Date.parse(m[1] + '-' + m[2] + '-' + m[3] + 'T00:00:00Z');
        if (isNaN(d)) continue;
        any = true;
        if (Math.abs(d - target) <= 36 * 3600 * 1000) return false;
    }
    return any;
}

async function getText(url) {
    try {
        var res = await fetch(url, {});
        if (!res || !res.ok) return '';
        return await res.text();
    } catch (e) {
        return '';
    }
}

// StremThru : grosse base de torrents, très fournie en releases françaises
async function fromStremThru(imdbId, isTv, season, episode) {
    var url = STREMTHRU_URL + '/v0/torznab/api?t=search&imdbid=' + imdbId;
    var xml = await getText(url);
    var out = [];
    var items = xml.split('<item>').slice(1);
    items.forEach(function (it) {
        var title = decodeEntities((it.match(/<title>([^<]*)<\/title>/) || [])[1]);
        var hash = (it.match(/name="infohash"\s+value="([0-9a-fA-F]{40})"/) || it.match(/btih:([0-9a-fA-F]{40})/) || [])[1];
        if (!title || !hash) return;
        if (isTv && !matchesEpisode(title, season, episode)) return;
        var size = Number((it.match(/<size>(\d+)<\/size>/) || [])[1]) || Number((it.match(/name="size"\s+value="(\d+)"/) || [])[1]) || 0;
        var seeders = (it.match(/name="seeders"\s+value="(\d+)"/) || [])[1];
        out.push({ hash: hash.toLowerCase(), release: title, text: title, bytes: size, seeders: seeders ? parseInt(seeders, 10) : null, source: 'StremThru' });
    });
    return out;
}

// --- 666TVF : sources françaises reprises de Frenchio (aymene69/frenchio, MIT) ---

// Lecture d'une réponse Torznab (trackers C411, Tr4ker, Torr9)
function parseTorznab(xml, isTv, season, episode, label) {
    var out = [];
    String(xml || '').split('<item>').slice(1).forEach(function (it) {
        var title = decodeEntities((it.match(/<title>([^<]*)<\/title>/) || [])[1]);
        var hash = (it.match(/name="infohash"\s+value="([0-9a-fA-F]{40})"/) || it.match(/btih:([0-9a-fA-F]{40})/i) || [])[1];
        if (!title || !hash) return;
        if (isTv && !matchesEpisode(title, season, episode)) return;
        var size = Number((it.match(/<size>(\d+)<\/size>/) || [])[1]) || Number((it.match(/name="size"\s+value="(\d+)"/) || [])[1]) || 0;
        var seeders = (it.match(/name="seeders"\s+value="(\d+)"/) || [])[1];
        out.push({ hash: hash.toLowerCase(), release: title, text: title, bytes: size, seeders: seeders ? parseInt(seeders, 10) : null, source: label });
    });
    return out;
}

function setting(name) {
    var s = (typeof SCRAPER_SETTINGS !== 'undefined' && SCRAPER_SETTINGS) ? SCRAPER_SETTINGS : {};
    return String(s[name] || '').trim();
}

// Trackers français avec ta clé (collée sur la Shield, jamais dans ce fichier)
var TRACKERS = [
    { key: 'c411', label: 'C411', base: 'https://c411.org/api' },
    { key: 'tr4ker', label: 'Tr4ker', base: 'https://tr4ker.net/torznab' }   // Torr9 a fusionné avec Tr4ker
];

async function fromTrackers(ids, isTv, season, episode) {
    var out = [];
    for (var i = 0; i < TRACKERS.length; i++) {
        var t = TRACKERS[i];
        var apikey = setting(t.key);
        if (!apikey) continue;
        var q = isTv ? 't=tvsearch&season=' + season : 't=movie';
        q += '&imdbid=' + ids.imdbId + (ids.tmdbId ? '&tmdbid=' + ids.tmdbId : '');
        var xml = await getText(t.base + '?' + q + '&apikey=' + encodeURIComponent(apikey));
        out = out.concat(parseTorznab(xml, isTv, season, episode, t.label));
    }
    return out;
}

function normTitle(t) {
    var x = String(t || '');
    try { x = x.normalize('NFD').replace(/[\u0300-\u036f]/g, ''); } catch (e) { }
    return ordinalDigits(x.toLowerCase().replace(/&/g, ' and ').replace(/[^a-z0-9]+/g, ' ').trim());
}
// 666TVF (07/11) : « Quatrième » = « 4eme » = « 4e » (YGG : « La 4eme dimension »)
var ORDINALS = { premier: '1er', premiere: '1ere', deuxieme: '2eme', troisieme: '3eme', quatrieme: '4eme', cinquieme: '5eme',
    sixieme: '6eme', septieme: '7eme', huitieme: '8eme', neuvieme: '9eme', dixieme: '10eme' };
function ordinalDigits(t) {
    return String(t).replace(/\b([A-Za-z]+)\b/g, function (w) {
        return ORDINALS[w.toLowerCase()] || w;
    }).replace(/\b(\d{1,2})(?:e|eme|ieme|ème|ième)\b/gi, '$1eme');
}

// Une release trouvée par recherche texte doit porter le titre (et la bonne année pour un film)
function releaseMatches(release, titles, year, isTv) {
    var r = ' ' + normTitle(release) + ' ';
    var named = titles.some(function (t) { var n = normTitle(t); return n.length >= 2 && r.indexOf(' ' + n + ' ') >= 0; });
    if (!named) return false;
    if (isTv || !year) return true;
    var re = /(?:^|[^0-9])(19[2-9]\d|20[0-4]\d)(?![0-9])/g, m, any = false;
    var inTitle = titles.join(' ');
    while ((m = re.exec(release))) {
        if (inTitle.indexOf(m[1]) >= 0) continue;
        any = true;
        if (Math.abs(Number(m[1]) - year) <= 1) return true;
    }
    return !any;
}

// YGG sans compte, via le relais public « u2p » (même source que Frenchio).
// Le relais refuse les serveurs : c'est la Shield qui s'y connecte (wsRequest, 666TVF ≥ 1.0.64).
// Frank donne les titres (FR + VO) et l'année ; sans wsRequest, Frank tente lui-même.
var YGG_RELAY = 'wss://u2p.anhkagi.net/';
var YGG_DIAG = '';
function yggEvents(messages) {
    var out = [];
    (messages || []).forEach(function (m) {
        var d; try { d = JSON.parse(m); } catch (e) { return; }
        if (!d || d[0] !== 'EVENT' || !d[2]) return;
        var tags = d[2].tags || [], title = null, hash = null, size = 0, seed = null;
        tags.forEach(function (t) {
            if (t[0] === 'title') title = t[1];
            else if (t[0] === 'x') hash = t[1];
            else if (t[0] === 'size') size = Number(t[1]) || 0;
            else if (t[0] === 'l' && String(t[1]).indexOf('u2p.seed:') === 0) seed = Number(String(t[1]).split(':')[1]) || null;
        });
        if (title && /^[0-9a-fA-F]{40}$/.test(String(hash || ''))) out.push({ title: fixLatin(title), hash: String(hash).toLowerCase(), size: size, seeders: seed });
    });
    return out;
}
// titres en latin-1 mal décodés (« Ã© » pour « é »)
function fixLatin(t) {
    try { return /Ã|Â/.test(t) ? decodeURIComponent(escape(t)) : t; } catch (e) { return t; }
}
async function fromYgg(imdbId, isTv, season, episode) {
    var direct = typeof wsRequest === 'function';
    YGG_DIAG = direct ? '' : '666TVF trop ancien (≥ 1.0.64 pour YGG)';
    // 666TVF (09/11) : titres et recherches via TMDB (avant : Frank, mis en pause par Hugging Face)
    var data = direct ? await frankMeta(imdbId, isTv, season, episode) : null;
    var titles = (data && data.titles) || [];
    var year = data && data.year ? Number(data.year) : 0;
    var items = [];
    if (direct) {
        ((data && data.queries) || []).forEach(function (q) {
            var r = wsRequest(YGG_RELAY, {
                // mêmes en-têtes que Frenchio (le relais refuse les clients qui ne ressemblent pas à un navigateur)
                headers: { Origin: 'https://www.ygg.re', 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' },
                send: JSON.stringify(['REQ', 'comet666', { kinds: [2003], search: q, limit: 50 }]),
                until: '"EOSE"', timeout: 4000
            });
            items = items.concat(yggEvents(r && r.messages));
            if (r && r.error) YGG_DIAG = 'relais : ' + String(r.error).slice(0, 60);
        });
    }
    var out = [], seen = {};
    items.forEach(function (r) {
        if (!r.hash || !r.title || seen[r.hash]) return;
        seen[r.hash] = 1;
        if (!releaseMatches(r.title, titles, year, isTv)) return;
        if (isTv && !matchesEpisode(r.title, season, episode)) return;
        out.push({ hash: String(r.hash).toLowerCase(), release: r.title, text: r.title, bytes: Number(r.size) || 0, seeders: r.seeders || null, source: 'YGG' });
    });
    return out;
}

// 666TVF (06/11) : YggReborn avec TA passkey (collée sur la Shield, jamais dans ce fichier).
// API Torznab, mais recherche par titre seulement (pas d'id IMDb) : Frank donne les titres FR + VO,
// l'année et les requêtes « Titre S03E04 » / « Titre S03 » / « Titre 2026 » ; on garde ce qui
// porte vraiment le titre (et la bonne année pour un film, le bon épisode pour une série).
var YGGREBORN_URL = 'https://www.yggreborn.org/api';
// 666TVF (07/11) : mode enquête — ce que YggReborn a renvoyé et pourquoi c'est écarté avant le tri
var YGG_STATS = null;
var LAST_WHY = null;
var AIR_DATE = null;
var TMDB_NUM = null;
// 666TVF (07/11) : budget de temps (voir getStreams)
var BUDGET_MS = 55000, SLOW_MS = 8000, T0 = 0, TIMING = null;
function late() { return T0 > 0 && Date.now() - T0 > BUDGET_MS; }
// 666TVF (07/11) : la 4e dimension S03 n'était pas trouvée. Les noms YGG s'écrivent sans accents
// (« La.Quatrieme.Dimension ») et souvent avec l'année (« The.Twilight.Zone.1961.S03 ») : on ajoute
// ces variantes aux recherches de Frank (6 max, l'épisode exact d'abord).
function stripAccents(t) {
    try { return String(t).normalize('NFD').replace(/[\u0300-\u036f]/g, ''); } catch (e) { return String(t); }
}
// Lien YggReborn qui sera vraiment proposé (VF ou titre FR seul, 1080p / 1440p / 4K).
function usableYgg(t) {
    var q = qualityOf(t.text);
    if (q !== '1080p' && q !== '1440p' && q !== '2160p') return false;
    return !!frenchLabel(t.text) || (!!t.frTitle && !SUBTITLE_ONLY.test(t.text) && !CAM_RELEASE.test(t.text) && !OTHER_DUB.test(t.text));
}
// Vrai si la release porte le titre français (titres[0] de Frank) et pas le titre original.
function frenchTitleOnly(release, titles) {
    if (!titles || titles.length < 2) return false;
    var r = ' ' + normTitle(release) + ' ', fr = normTitle(titles[0]);
    var others = titles.slice(1).map(normTitle).filter(function (n) { return n && n !== fr; });
    if (!fr || !others.length) return false;
    return r.indexOf(' ' + fr + ' ') >= 0 && !others.some(function (n) { return r.indexOf(' ' + n + ' ') >= 0; });
}
function yggQueries(frankQueries, titles, year, isTv, season, episode, airDate) {
    var out = [];
    function add(q) { q = String(q || '').replace(/\s+/g, ' ').trim(); if (q && out.indexOf(q) < 0) out.push(q); }
    function variants(q) { add(q); add(stripAccents(q)); add(ordinalDigits(stripAccents(q))); }
    var pad = function (n) { return (n < 10 ? '0' : '') + n; };
    var ep = isTv ? 'S' + pad(season) + 'E' + pad(episode) : '';
    var exact = [], wide = [];
    frankQueries.forEach(function (q) { (isTv && q.indexOf(ep) < 0 ? wide : exact).push(q); });
    // 666TVF (07/11) : émissions datées (WWE, UFC…) — « WWE.SmackDown.2026.11.06 » : on cherche aussi par date
    if (isTv && airDate && /^\d{4}-\d\d-\d\d$/.test(airDate)) {
        var dot = airDate.replace(/-/g, '.');
        exact.slice(0, 1).forEach(variants);
        titles.map(stripAccents).filter(function (t, i, a) { return t && a.indexOf(t) === i; }).slice(0, 2)
            .forEach(function (t) { add(t + ' ' + dot); });
    }
    exact.forEach(variants);
    // 666TVF (08/11) : films — Frank ne donnait souvent qu'UNE recherche (titre français) : le guetteur
    // passait à côté des releases au titre original. On ajoute chaque titre (avec l'année, puis seul).
    if (!isTv && titles && titles.length) {
        titles.map(stripAccents).filter(function (t, i, a) { return t && a.indexOf(t) === i; }).slice(0, 3).forEach(function (t) {
            if (year) add(t + ' ' + year);
        });
        titles.map(stripAccents).filter(function (t, i, a) { return t && a.indexOf(t) === i; }).slice(0, 2).forEach(function (t) { add(t); });
    }
    if (isTv) {
        wide.forEach(variants);
        if (year && titles.length) add(stripAccents(titles[titles.length - 1]) + ' ' + year);
    }
    return out.slice(0, MAX_YGG_QUERIES);
}
var MAX_YGG_QUERIES = 8;
async function fromYggReborn(imdbId, isTv, season, episode) {
    var key = setting('yggreborn');
    if (!key) return [];
    var data = await frankMeta(imdbId, isTv, season, episode);
    var titles = (data && data.titles) || [];
    var year = data && data.year ? Number(data.year) : 0;
    var queries = yggQueries((data && data.queries) || [], titles, year, isTv, season, episode, AIR_DATE);
    var out = [], seen = {};
    YGG_STATS = { queries: 0, items: 0, episode: 0, title: 0, samples: [] };
    for (var i = 0; i < queries.length; i++) {
        if (late()) { YGG_STATS.cut = true; break; }
        var xml = await getText(YGGREBORN_URL + '?t=search&limit=50&q=' + encodeURIComponent(queries[i]) +
            '&apikey=' + encodeURIComponent(key));
        YGG_STATS.queries++;
        var all = parseTorznab(xml, false, 0, 0, 'YggReborn').filter(function (t) { return !seen[t.hash]; });
        var good = parseTorznab(xml, isTv, season, episode, 'YggReborn');
        YGG_STATS.items += all.length;
        all.slice(0, 4).forEach(function (t) { if (YGG_STATS.samples.length < 10) YGG_STATS.samples.push(String(t.release).slice(0, 90)); });
        YGG_STATS.episode += Math.max(0, all.length - good.filter(function (t) { return !seen[t.hash]; }).length);
        good.forEach(function (t) {
            if (seen[t.hash]) return;
            seen[t.hash] = 1;
            if (!releaseMatches(t.release, titles, year, isTv)) { YGG_STATS.title++; return; }
            // nom français (« La 4eme dimension ») sur un tracker français, sans mot de langue : VF probable
            t.frTitle = frenchTitleOnly(t.release, titles);
            out.push(t);
        });
        // l'épisode exact a déjà répondu par 3 liens VF 1080p+ : pas besoin de la saison entière.
        // 666TVF (07/11) : on ne compte que les liens utilisables (avant : 3 VOSTFR / 720p suffisaient
        // à arrêter la recherche avant « La 4eme Dimension S03 »).
        if (isTv && out.filter(usableYgg).length >= 3) break;
    }
    return out;
}

// 666TVF (09/11) : Frank a été mis en pause par Hugging Face. Titres / année / recherches viennent
// maintenant directement de TMDB (la clé TMDB est fournie par 666TVF). Une seule fois par recherche.
var FRANK_META = null;
function pad2(n) { return (n < 10 ? '0' : '') + n; }
/** Règle pure (testée) : recherches de base, comme celles que donnait Frank. */
function baseQueries(titles, year, isTv, season, episode) {
    var out = [];
    titles.forEach(function (t) {
        if (!t) return;
        if (isTv) { out.push(t + ' S' + pad2(season) + 'E' + pad2(episode)); }
        else if (year) { out.push(t + ' ' + year); }
        else { out.push(t); }
    });
    if (isTv) titles.forEach(function (t) { if (t) out.push(t + ' S' + pad2(season)); });
    return out.filter(function (q, i, a) { return a.indexOf(q) === i; }).slice(0, 4);
}
async function tmdbJson(path) {
    if (typeof TMDB_API_KEY === 'undefined' || !TMDB_API_KEY) return null;
    return getJson('https://api.themoviedb.org/3' + path + (path.indexOf('?') >= 0 ? '&' : '?') + 'api_key=' + encodeURIComponent(TMDB_API_KEY));
}
async function frankMeta(imdbId, isTv, season, episode) {
    var key = imdbId + '|' + isTv;
    if (FRANK_META && FRANK_META.key === key) return FRANK_META.data;
    var data = null;
    try {
        var tmdbId = TMDB_NUM && /^\d+$/.test(TMDB_NUM) ? TMDB_NUM : null;
        if (!tmdbId) {
            var found = await tmdbJson('/find/' + imdbId + '?external_source=imdb_id');
            var hit = found && ((isTv ? found.tv_results : found.movie_results) || [])[0];
            tmdbId = hit && hit.id ? String(hit.id) : null;
        }
        if (tmdbId) {
            var kind = isTv ? 'tv' : 'movie';
            var fr = await tmdbJson('/' + kind + '/' + tmdbId + '?language=fr-FR');
            var en = await tmdbJson('/' + kind + '/' + tmdbId + '?language=en-US');
            var titles = [];
            [fr && (fr.title || fr.name), en && (en.title || en.name), fr && (fr.original_title || fr.original_name)].forEach(function (t) {
                t = String(t || '').trim(); if (t && titles.indexOf(t) < 0) titles.push(t);
            });
            var date = String((fr && (fr.release_date || fr.first_air_date)) || '');
            var year = /^\d{4}/.test(date) ? Number(date.slice(0, 4)) : 0;
            data = { titles: titles, year: year, queries: baseQueries(titles.slice(0, 2), year, isTv, season, episode) };
        }
    } catch (e) { data = null; }
    FRANK_META = { key: key, data: data };
    return data;
}

// 666TVF (07/11) : ton compte AllDebrid comme source. Ce qui y est déjà prêt (ajouté par toi ou par
// StreamFusion) et qui correspond au titre et à l'épisode sort chez Comet666 (⚡ AllDebrid ✓ dans 666TVF).
// La clé est prêtée par 666TVF le temps de la recherche (jamais écrite ici ni enregistrée).
function parseAllDebridAccount(data, titles, year, isTv, season, episode) {
    var mags = data && data.status === 'success' && data.data && data.data.magnets;
    if (mags && !Array.isArray(mags)) mags = Object.keys(mags).map(function (k) { return mags[k]; });
    var out = [];
    (mags || []).forEach(function (m) {
        if (!m) return;
        var ready = m.statusCode === 4 || String(m.status || '').toLowerCase() === 'ready';
        var hash = String(m.hash || '').toLowerCase();
        var name = String(m.filename || '');
        if (!ready || !/^[0-9a-f]{40}$/.test(hash) || !name) return;
        if (!releaseMatches(name, titles, year, isTv)) return;
        if (isTv && !matchesEpisode(name, season, episode)) return;
        out.push({ hash: hash, release: name, text: name, bytes: Number(m.size) || 0, seeders: null,
            source: 'AllDebrid', frTitle: frenchTitleOnly(name, titles) });
    });
    return out;
}
async function fromAllDebridAccount(imdbId, isTv, season, episode) {
    var key = setting('alldebrid_auto');
    if (!key) return [];
    var meta = await frankMeta(imdbId, isTv, season, episode);
    var titles = (meta && meta.titles) || [];
    if (!titles.length) return [];
    try {
        var res = await fetch('https://api.alldebrid.com/v4.1/magnet/status?agent=666TVF&status=ready',
            { headers: { 'Accept': 'application/json', 'Authorization': 'Bearer ' + key } });
        if (!res || !res.ok) return [];
        return parseAllDebridAccount(await res.json(), titles, meta && meta.year ? Number(meta.year) : 0, isTv, season, episode);
    } catch (e) {
        console.log('Comet666 compte AllDebrid : ' + (e && e.message));
        return [];
    }
}

// 666TVF (07/11) : moteur de recherche TorBox (sa propre base, cache inclus).
// La clé TorBox est passée par 666TVF au moment de la recherche (jamais écrite ici ni enregistrée).
var TORBOX_SEARCH_URL = 'https://search-api.torbox.app';
function parseTorboxSearch(data, isTv, season, episode) {
    var list = (data && data.data && data.data.torrents) || [];
    var out = [];
    list.forEach(function (t) {
        var hash = String((t && t.hash) || '').toLowerCase();
        var title = String((t && (t.raw_title || t.title)) || '');
        if (!/^[0-9a-f]{40}$/.test(hash) || !title) return;
        if (isTv && !matchesEpisode(title, season, episode)) return;
        out.push({ hash: hash, release: title, text: title, bytes: Number(t.size) || 0,
            seeders: typeof t.last_known_seeders === 'number' ? t.last_known_seeders : null,
            source: 'TorBox' + (t.cached ? ' ⚡' : '') });
    });
    return out;
}
async function fromTorboxSearch(imdbId, isTv, season, episode) {
    var key = setting('torbox_auto');
    if (!key) return [];
    var url = TORBOX_SEARCH_URL + '/torrents/imdb_id:' + imdbId + '?check_cache=true&check_owned=false&metadata=false&search_user_engines=false' +
        (isTv ? '&season=' + season + '&episode=' + episode : '');
    try {
        var res = await fetch(url, { headers: { 'Accept': 'application/json', 'Authorization': 'Bearer ' + key } });
        if (!res || !res.ok) return [];
        return parseTorboxSearch(await res.json(), isTv, season, episode);
    } catch (e) {
        console.log('Comet666 TorBox search : ' + (e && e.message));
        return [];
    }
}

// TorrentsDB : addon public au format Stremio
// 666TVF (11/11) : ce que TorBox a DÉJÀ en cache démarre tout de suite ; le reste doit d'abord être
// téléchargé chez TorBox (Malfaisante en VFQ : liens qui ne démarraient pas, ou après de longues minutes).
// On demande en une fois quels fichiers sont prêts, et ceux-là passent devant.
function parseTorboxCached(data) {
    var d = data && data.data;
    var out = {};
    if (Array.isArray(d)) d.forEach(function (x) { var h = String((x && (x.hash || x)) || '').toLowerCase(); if (h) out[h] = true; });
    else if (d && typeof d === 'object') Object.keys(d).forEach(function (h) { if (d[h]) out[String(h).toLowerCase()] = true; });
    return out;
}
async function torboxCached(hashes) {
    var key = setting('torbox_auto');
    if (!key || !hashes.length) return {};
    var out = {};
    for (var i = 0; i < hashes.length; i += 80) {
        try {
            var res = await fetch('https://api.torbox.app/v1/api/torrents/checkcached?format=list&list_files=false&hash=' + hashes.slice(i, i + 80).join(','),
                { headers: { 'Accept': 'application/json', 'Authorization': 'Bearer ' + key } });
            if (res && res.ok) { var got = parseTorboxCached(await res.json()); Object.keys(got).forEach(function (h) { out[h] = true; }); }
        } catch (e) { console.log('Comet666 cache TorBox : ' + (e && e.message)); }
    }
    return out;
}

async function fromTorrentsDb(imdbId, isTv, season, episode) {
    var sid = isTv ? imdbId + ':' + season + ':' + episode : imdbId;
    var data = await getJson(TORRENTSDB_URL + '/stream/' + (isTv ? 'series' : 'movie') + '/' + sid + '.json');
    var out = [];
    ((data && data.streams) || []).forEach(function (s) {
        if (!s.infoHash) return;
        var text = (s.title || '') + '\n' + (s.name || '') + '\n' + (s.description || '');
        var release = String(s.title || s.description || '').split('\n')[0];
        var size = (text.match(/💾\s*([\d.,]+\s*[GMT]B)/i) || [])[1];
        var seeders = (text.match(/👤\s*(\d+)/) || [])[1];
        out.push({ hash: s.infoHash.toLowerCase(), release: release, text: text, bytes: sizeToBytes(size), seeders: seeders ? parseInt(seeders, 10) : null, source: 'TorrentsDB' });
    });
    return out;
}


// --- 666TVF : « MULTi » d'un groupe français = VF, d'un groupe étranger = écarté ---
// Liste tenue à jour par Frank (noms de releases vus + lectures des Shield).
var NOT_GROUP = { MKV: 1, MP4: 1, AVI: 1, X264: 1, X265: 1, H264: 1, H265: 1, HEVC: 1, WEB: 1, WEBDL: 1, WEBRIP: 1, DL: 1, HDR: 1, DV: 1, REMUX: 1, BLURAY: 1, MULTI: 1, FRENCH: 1, VFF: 1, VFQ: 1, VOSTFR: 1, AAC: 1, AC3: 1, DTS: 1, ATMOS: 1, SDR: 1, '1080P': 1, '2160P': 1, '720P': 1, PROPER: 1, REPACK: 1 };
function releaseGroup(release) {
    var line = String(release || '').split('\n')[0].trim().replace(/\s*\[[^\]]*\]\s*$/, '').replace(/\.(mkv|mp4|avi|ts|m2ts)$/i, '');
    var m = line.match(/-([A-Za-z0-9][A-Za-z0-9_]{1,19})$/);
    if (!m) return null;
    var g = m[1].toUpperCase();
    return NOT_GROUP[g] || /^\d+$/.test(g) ? null : g;
}
// Groupes connus quand Frank tournait encore (09/11), puis ce que 666TVF apprend à la lecture.
var GROUPS_FR_SEED = ['0VERFLOW', '2D', 'ALN', 'AMEN', 'ARTEFAC', 'ASUGAR', 'BATGIRL', 'BLL', 'BOUBA', 'BOUC', 'BRAD', 'BYOR', 'CHRISJ72',
    'FW', 'HEEL', 'FTMVHD', 'TFA', 'QTZ', 'LIBERTY', 'NORRIS', 'FRATERNITY', 'EXTREME', 'SUPPLY', 'MYSTERIOUS', 'LAZARUS', 'TLUB', 'REBIRTH', 'HYPERION', 'FAN2', 'TTK', 'CHEW'];
var GROUPS_OTHER_SEED = ['A4O', 'ALEKARTEM', 'ANONIMO_CREW', 'ARPI', 'AVC_BY_BATHORY', 'AWS', 'B9R', 'BAMAX71', 'BG', 'BLACKBIT', 'BOLLYROCKERS', 'BONE', 'MIXIO'];
function groupsLists() {
    function csv(k) { return String(setting(k) || '').split(',').map(function (g) { return g.trim().toUpperCase(); }).filter(Boolean); }
    var other = GROUPS_OTHER_SEED.concat(csv('groups_other_auto'));
    var fr = GROUPS_FR_SEED.concat(csv('groups_fr_auto')).filter(function (g) { return other.indexOf(g) < 0 || csv('groups_fr_auto').indexOf(g) >= 0; });
    return { fr: fr, other: other.filter(function (g) { return csv('groups_fr_auto').indexOf(g) < 0; }) };
}
async function settleMulti(items) {
    if (!items.some(function (t) { return t.lang.indexOf('?') >= 0; })) return items;
    // 666TVF (09/11) : groupes appris par 666TVF sur tes Shield (prêtés comme les clés), + la base de Frank
    var data = groupsLists();
    var fr = {}, other = {};
    (data.fr || []).forEach(function (g) { fr[g] = 1; });
    (data.other || []).forEach(function (g) { other[g] = 1; });
    return items.filter(function (t) {
        if (t.lang.indexOf('?') < 0) return true;
        var g = releaseGroup(t.release);
        if (g && other[g]) return false;          // groupe étranger : pas de VF
        if (g && fr[g]) t.lang = 'MULTi VF';      // groupe français : VF
        return true;
    });
}

// Mode enquête (pur, testé) : lignes courtes « Source : N trouvés · 2 VOSTFR · 1 720p ».
function investigation(why, ygg) {
    var lines = ['🔎 Comet666 : rien de lisible en VF 1080p+ — pourquoi :'];
    var parts = [];
    Object.keys(why).forEach(function (k) {
        var w = why[k];
        var r = Object.keys(w.reasons).sort(function (a, b) { return w.reasons[b] - w.reasons[a]; })
            .map(function (x) { return w.reasons[x] + ' ' + x; });
        parts.push(k + ' : ' + w.found + ' trouvé' + (w.found > 1 ? 's' : '') + (r.length ? ' · ' + r.join(' · ') : ''));
    });
    if (ygg) {
        var y = 'YggReborn : ' + ygg.queries + ' recherche' + (ygg.queries > 1 ? 's' : '') + ' · ' + ygg.items + ' réponse' + (ygg.items > 1 ? 's' : '');
        if (ygg.episode) y += ' · ' + ygg.episode + ' autre épisode';
        if (ygg.title) y += ' · ' + ygg.title + ' autre titre';
        parts.unshift(y);
    } else if (setting('yggreborn')) parts.unshift('YggReborn : pas interrogé');
    if (ygg && ygg.cut) parts.push('YggReborn : recherches coupées (trop long)');
    if (TIMING && TIMING.slow.length) parts.push('⏱ lent : ' + TIMING.slow.join(' · '));
    if (TIMING && TIMING.skipped.length) parts.push('⏱ pas interrogé (temps écoulé) : ' + TIMING.skipped.join(', '));
    if (!parts.length) parts.push('aucune source n\'a répondu');
    return lines.concat(parts.slice(0, 7)).map(function (l) { return l.slice(0, 90); });
}

// 666TVF (07/11) : trace envoyée à Frank pour l'enquête (titres et compteurs seulement :
// jamais de clé, de passkey ni d'adresse de source).
var COMET_VERSION = '1.1.23';
async function trace(data) { }   // 666TVF (09/11) : Frank en pause chez Hugging Face — plus de traces

// --- Point d'entrée appelé par 666TV --------------------------------------
async function getStreams(tmdbId, mediaType, season, episode) {
    var t0 = Date.now();
    var probe = String(tmdbId).replace(/^tmdb:/i, '') !== '603' && !(typeof SCRAPER_SETTINGS !== 'undefined' && SCRAPER_SETTINGS && SCRAPER_SETTINGS.__health);
    if (probe) await trace({ phase: 'start', id: String(tmdbId), type: mediaType, s: season, e: episode });
    try {
        var out = await getStreamsInner(tmdbId, mediaType, season, episode);
        if (probe) await trace({ phase: 'end', id: String(tmdbId), type: mediaType, s: season, e: episode, ms: Date.now() - t0,
            shown: out.length, top: out.slice(0, 8).map(function (x) { return String(x.title || '').split('\n')[0].slice(0, 90) + (x.infoHash ? '' : ' [info]'); }),
            why: LAST_WHY, ygg: YGG_STATS, timing: TIMING });
        return out;
    } catch (e) {
        if (probe) await trace({ phase: 'crash', id: String(tmdbId), s: season, e: episode, ms: Date.now() - t0, error: String(e && e.message || e).slice(0, 200) });
        // 666TVF (07/11) : un plantage se voit (avant : aucune ligne, comme si rien n'existait)
        console.log('Comet666 a planté : ' + (e && e.stack || e));
        return [{ name: 'Comet666 enquête', title: ('💥 Comet666 a planté : ' + (e && e.message || e)).slice(0, 90), url: 'https://diagnostic.invalid/comet', provider: 'Comet666', type: 'direct' }];
    }
}
async function getStreamsInner(tmdbId, mediaType, season, episode) {
    var isTv = mediaType === 'tv' || mediaType === 'series';
    YGG_STATS = null;
    LAST_WHY = null;
    FRANK_META = null;
    AIR_DATE = null;
    T0 = Date.now();
    var ids = await resolveIds(tmdbId, mediaType);
    if (!ids || !ids.imdbId) {
        console.log('Comet666 : id IMDb introuvable pour ' + tmdbId);
        return [{ name: 'Comet666 enquête', title: '🔎 Comet666 : id IMDb introuvable pour ' + tmdbId + ' (TMDB / Frank)', url: 'https://diagnostic.invalid/comet', provider: 'Comet666', type: 'direct' }];
    }
    var found = [];
    // Vrai si le nom annonce exactement la saison demandée (ou ne dit rien de précis).
    function exactSeason(t) {
        var m = String(t.release || t.text || '').match(/\bS(\d{1,2})[ ._-]?E(\d{1,3})\b/i);
        return !m || parseInt(m[1], 10) === parseInt(season, 10);
    }
    // 666TVF : sources françaises d'abord (trackers avec ta clé, YGG), puis les index publics
    var tmdbNum = String(tmdbId || '').replace(/^tmdb:/i, '');
    TMDB_NUM = tmdbNum;
    // 666TVF (07/11) : sur la Shield, chaque demande bloque jusqu'à sa réponse (une source en panne
    // peut faire attendre 30 s et plus). Au-delà de BUDGET_MS, on n'en lance plus : Comet666 répond
    // toujours avant que 666TVF ne l'abandonne (120 s), avec ce qu'il a — et l'enquête dit qui a traîné.
    var sources = [
        ['Trackers', function () { return fromTrackers({ imdbId: ids.imdbId, tmdbId: /^\d+$/.test(tmdbNum) ? tmdbNum : null }, isTv, season, episode); }],
        ['YggReborn', function () { return fromYggReborn(ids.imdbId, isTv, season, episode); }],
        ['TorBox', function () { return fromTorboxSearch(ids.imdbId, isTv, season, episode); }],
        ['AllDebrid', function () { return fromAllDebridAccount(ids.imdbId, isTv, season, episode); }],
        ['StremThru', function () { return fromStremThru(ids.imdbId, isTv, season, episode); }],
        ['Torrentio', function () { return fromTorrentio(ids.imdbId, isTv, season, episode); }],
        ['DMM', function () { return fromZilean(ids.imdbId, isTv, season, episode); }],
        ['TorrentsDB', function () { return fromTorrentsDb(ids.imdbId, isTv, season, episode); }]
    ];
    AIR_DATE = isTv ? await episodeAirDate(tmdbId, season, episode) : null;
    var lists = [];
    TIMING = { skipped: [], slow: [] };
    for (var si = 0; si < sources.length; si++) {
        if (late()) { TIMING.skipped.push(sources[si][0]); lists.push([]); continue; }
        var t1 = Date.now(), got = [];
        try { got = (await sources[si][1]()) || []; } catch (e) { got = []; }
        var dt = Date.now() - t1;
        if (dt > SLOW_MS) TIMING.slow.push(sources[si][0] + ' ' + Math.round(dt / 1000) + ' s');
        lists.push(got);
    }
    var airDate = AIR_DATE;
    var seen = {};
    var seenNames = {};
    // 666TVF (07/11) : mode enquête — par source : trouvés, gardés, et pourquoi le reste est écarté
    var why = {};
    function srcOf(t) { return String(t.source || '?').split(' ')[0]; }
    function note(t, reason) {
        var k = srcOf(t);
        why[k] = why[k] || { found: 0, kept: 0, reasons: {} };
        if (reason) why[k].reasons[reason] = (why[k].reasons[reason] || 0) + 1;
    }
    lists.forEach(function (list) { list.forEach(function (t) { note(t); why[srcOf(t)].found++; }); });
    lists.forEach(function (list) {
        list.forEach(function (t) {
            // fichier daté d'un autre jour (vieux WWE Raw) : jamais
            if (isTv && dateMismatch(String(t.release) + ' ' + String(t.text || ''), airDate)) { note(t, 'autre date'); return; }
            // même release publiée deux fois (avec ou sans « .mkv ») : une seule ligne
            var key = String(t.release).toLowerCase().replace(/[ ._-]*mkv$/, '').replace(/[^a-z0-9]+/g, ' ').trim();
            if (seen[t.hash] || seenNames[key]) { note(t, 'doublon'); return; }
            var lang = frenchLabel(t.text);
            // titre français seul, sans mot de langue (YggReborn) : proposé en « VF ? », jamais sûr
            if (!lang && t.frTitle && !SUBTITLE_ONLY.test(t.text) && !CAM_RELEASE.test(t.text) && !OTHER_DUB.test(t.text)) lang = 'VF ? (titre FR)';
            if (!lang) {
                note(t, SUBTITLE_ONLY.test(t.text) ? 'VOSTFR' : CAM_RELEASE.test(t.text) ? 'CAM' : 'pas de VF');
                return;
            }
            seen[t.hash] = true;
            seenNames[key] = true;
            t.lang = lang;
            t.quality = qualityOf(t.text);
            // 1080p, 1440p et 4K seulement (jamais de 720p)
            if (t.quality !== '1080p' && t.quality !== '1440p' && t.quality !== '2160p') { note(t, t.quality || 'définition ?'); return; }
            found.push(t);
        });
    });
    // Les fichiers « autre saison » ne servent que s'il n'y a rien d'exact.
    if (isTv && found.some(exactSeason)) {
        found.filter(function (t) { return !exactSeason(t); }).forEach(function (t) { note(t, 'autre saison'); });
        found = found.filter(exactSeason);
    }
    var beforeMulti = found.slice();
    found = await settleMulti(found);
    beforeMulti.filter(function (t) { return found.indexOf(t) < 0; }).forEach(function (t) { note(t, 'MULTi étranger'); });
    found.forEach(function (t) { why[srcOf(t)].kept++; });
    LAST_WHY = why;
    // 666TVF (11/11) : déjà prêt chez TorBox ?
    var ready = late() ? {} : await torboxCached(found.map(function (t) { return t.hash; }).filter(Boolean));
    found.forEach(function (t) { if (ready[t.hash] || /⚡/.test(String(t.source || ''))) t.cached = true; });
    found.sort(function (a, b) {
        // VF sûre d'abord, « MULTi (VF ?) » ensuite
        var c = (a.lang.indexOf('?') >= 0) - (b.lang.indexOf('?') >= 0);
        if (c) return c;
        // puis ce qui démarre tout de suite (en cache) avant ce qu'il faut d'abord télécharger
        var r = (b.cached ? 1 : 0) - (a.cached ? 1 : 0);
        if (r) return r;
        var q = (QUALITY_RANK[b.quality] || 0) - (QUALITY_RANK[a.quality] || 0);
        if (q) return q;
        return (b.bytes || 0) - (a.bytes || 0);
    });
    var shown = found.slice(0, MAX_RESULTS).map(function (t) {
        var line2 = ['🇫🇷 ' + t.lang].concat(extras(t.text)).join(' · ');
        var line3 = [t.cached ? '⚡ prêt' : '', formatSize(t.bytes), t.seeders != null ? '👤 ' + t.seeders : '', String(t.source || '').replace(/ ⚡$/, '')].filter(Boolean).join(' · ');
        return {
            name: ('Comet666 ' + (t.quality || '') + (t.cached ? ' ⚡' : '')).trim(),
            title: t.release + '\n' + line2 + (line3 ? '\n' + line3 : ''),
            url: 'magnet:?xt=urn:btih:' + t.hash + '&dn=' + encodeURIComponent(t.release),
            infoHash: t.hash,
            quality: t.quality || undefined,
            size: formatSize(t.bytes) || undefined,
            language: 'fr',
            provider: 'Comet666',
            type: 'torrent'
        };
    });
    // 666TVF (07/11) : mode enquête — rien de proposable : on dit pourquoi, source par source.
    if (!shown.length && String(tmdbId).replace(/^tmdb:/i, '') !== '603') {
        investigation(why, YGG_STATS).reverse().forEach(function (line) {
            shown.unshift({ name: 'Comet666 enquête', title: line, url: 'https://diagnostic.invalid/comet', provider: 'Comet666', type: 'direct' });
        });
    }
    // Bouton « Tester » (Matrix) : d'où viennent les liens (gardés / trouvés), en 1re ligne.
    if (String(tmdbId).replace(/^tmdb:/i, '') === '603') {
        var raw = {}, kept = {};
        lists.forEach(function (l) { l.forEach(function (t) { var k = String(t.source).split(' ')[0]; raw[k] = (raw[k] || 0) + 1; }); });
        found.forEach(function (t) { var k = String(t.source).split(' ')[0]; kept[k] = (kept[k] || 0) + 1; });
        var parts = ['C411', 'Tr4ker', 'YggReborn', 'TorBox', 'AllDebrid', 'StremThru', 'Torrentio', 'DMM', 'TorrentsDB'].filter(function (k) { return raw[k] || (k === 'C411' && setting('c411')) || (k === 'Tr4ker' && setting('tr4ker')) || (k === 'YggReborn' && setting('yggreborn')) || (k === 'TorBox' && setting('torbox_auto')) || (k === 'AllDebrid' && setting('alldebrid_auto')); })
            .map(function (k) { return k + ' ' + (kept[k] || 0) + '/' + (raw[k] || 0); });
        Object.keys(raw).forEach(function (k) { if (parts.join(' ').indexOf(k) < 0) parts.push(k + ' ' + (kept[k] || 0) + '/' + raw[k]); });
        var diag = [{ title: 'Sources (VF gardés/trouvés) : ' + parts.slice(0, 4).join(' · ') }];
        if (parts.length > 4) diag.push({ title: '… ' + parts.slice(4).join(' · ') });
        diag.reverse().forEach(function (d) {
            shown.unshift({ name: 'Comet666 diagnostic', title: d.title.slice(0, 75), url: 'https://diagnostic.invalid/comet', provider: 'Comet666', type: 'direct' });
        });
    }
    return shown;
}

if (typeof module !== 'undefined' && module.exports) module.exports = { _yggEvents: yggEvents, _parseTorznab: parseTorznab, _releaseMatches: releaseMatches, _releaseGroup: releaseGroup, _settleMulti: settleMulti, getStreams: getStreams, _frenchLabel: frenchLabel, _matchesEpisode: matchesEpisode, _dateMismatch: dateMismatch, _qualityOf: qualityOf, _parseTorboxSearch: parseTorboxSearch, _yggQueries: yggQueries, _setAirDate: function (d) { AIR_DATE = d; }, _frenchTitleOnly: frenchTitleOnly, _normTitle: normTitle, _investigation: investigation, _parseAllDebridAccount: parseAllDebridAccount, _baseQueries: baseQueries, _groupsLists: groupsLists, _budget: function (ms) { BUDGET_MS = ms; }, _parseTorboxCached: parseTorboxCached };
