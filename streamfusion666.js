/*
 * StreamFusion666 — plugin de flux VF pour 666TV (généré depuis src/addon666.js).
 * Interroge TON addon StreamFusion avec TA config, directement depuis la Shield.
 * L'adresse de config (qui contient les clés debrid) n'est jamais dans ce
 * fichier : 666TV la fournit via SCRAPER_SETTINGS.url, stockée uniquement
 * sur l'appareil (Plugins → « Comet666 – mes sources »).
 * Règles : VF seulement (pas de VOSTFR seule, pas de version cinéma),
 * 1080p et 4K, liens debrid déjà en cache, aucun torrent P2P.
 */

var ADDON_NAME = 'StreamFusion';
var FRANK_URL = 'https://lambda666-frankenstream.hf.space';
var MAX_RESULTS = 30;

// --- Règles de langue (mêmes que Frank et Comet666) -----------------------
var SUBTITLE_ONLY = /\bVOST(?:FR)?\b|\bSTFR\b|\bSUB\.?FR(?:ENCH)?\b|\bSUBFRENCH\b|\bFRENCH[ ._-]*SUB(?:S|BED|TITLE[SD]?)?\b|\bSOUS[ ._-]*TITR/i;
var FRENCH_AUDIO = /\bVF[FQI2]?\b|\bTRUE[ ._-]*FRENCH\b|\bFRENCH\b|\bVERSION FRAN[CÇ]AISE\b|\bVOF\b/i;
var MULTI = /\bMULTI(?:[ ._-]*(?:VFF|VFQ|VF2|TRUEFRENCH|FRENCH))?\b/i;
var FRENCH_FLAG = /🇫🇷/;
var CAM_RELEASE = /\b(?:HD[ ._-]?)?CAM(?:[ ._-]?RIP)?\b|\b(?:HD[ ._-]?)?TS\b|\bTELE[ ._-]?SYNC\b|\b(?:HD[ ._-]?)?TC\b|\bTELE[ ._-]?CINE\b|\bMD\b|\bMICRO[ ._-]?DUBBED\b|\bSCR(?:EENER)?\b/i;
// lien qui lance un téléchargement sur le debrid au lieu de lire tout de suite
var NOT_CACHED = /⬇️|⏳|\bnot[ ._-]?cached\b|\buncached\b|\bnon[ ._-]?en[ ._-]?cache\b|\bdownload(?:ing)?\b|\[(?:RD|AD|TB|PM|DL)[ ._-]?download\]/i;

function frenchLabel(text) {
    if (SUBTITLE_ONLY.test(text) || CAM_RELEASE.test(text)) return null;
    if (/\bVFQ\b/i.test(text)) return 'VFQ';
    if (/\bVFF\b|\bTRUE[ ._-]*FRENCH\b/i.test(text)) return 'VFF';
    if (/\bVF2\b/i.test(text)) return 'VF2';
    if (FRENCH_AUDIO.test(text)) return 'VF';
    if (MULTI.test(text) && FRENCH_FLAG.test(text)) return 'MULTI VF';
    if (/\bMULTi\b/.test(text)) return 'MULTi (VF ?)';
    // l'addon signale la piste française par un drapeau ou « FR »
    if (FRENCH_FLAG.test(text) || /\bFR\b|\bFran[cç]ais\b/i.test(text)) return 'VF';
    return null;
}

function qualityOf(text) {
    if (/\b(2160p|4k|uhd)\b/i.test(text)) return '2160p';
    if (/\b1080p\b|\bFHD\b/i.test(text)) return '1080p';
    if (/\b720p\b/i.test(text)) return '720p';
    if (/\b480p\b/i.test(text)) return '480p';
    return null;
}
var QUALITY_RANK = { '2160p': 4, '1080p': 3, '720p': 2, '480p': 1 };

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
    var m = String(text || '').match(/([\d.,]+)\s*(TB|GB|MB|To|Go|Mo)\b/i);
    if (!m) return 0;
    var n = parseFloat(m[1].replace(',', '.'));
    var u = m[2].toUpperCase();
    return n * (u === 'TB' || u === 'TO' ? 1e12 : u === 'GB' || u === 'GO' ? 1e9 : 1e6);
}

function formatSize(bytes) {
    if (!bytes) return '';
    return bytes >= 1e9 ? (bytes / 1e9).toFixed(1) + ' Go' : Math.round(bytes / 1e6) + ' Mo';
}

var LAST_ERROR = '';

async function getJson(url, label) {
    try {
        var res = await fetch(url, { headers: { 'Accept': 'application/json' } });
        if (!res || !res.ok) {
            LAST_ERROR = (label || 'requête') + ' → HTTP ' + (res && res.status);
            console.log(ADDON_NAME + '666 : ' + LAST_ERROR);
            return null;
        }
        var body = await res.text();
        try {
            return JSON.parse(body);
        } catch (e) {
            LAST_ERROR = (label || 'requête') + ' → réponse non JSON : ' + scrub(body).slice(0, 120);
            return null;
        }
    } catch (e) {
        LAST_ERROR = (label || 'requête') + ' → ' + (e && e.message);
        console.log(ADDON_NAME + '666 : ' + LAST_ERROR);
        return null;
    }
}

// retire toute adresse (qui pourrait contenir une config) d'un texte affiché
function scrub(text) {
    return String(text || '').replace(/(?:https?|stremio):\/\/\S+/gi, '[lien]').replace(/\s+/g, ' ').trim();
}

// Bouton « Tester » de 666TV = Matrix (TMDB 603) : si rien n'est gardé,
// on renvoie une ligne de diagnostic lisible au lieu d'une liste vide.
function diagnostic(tmdbId, lines) {
    if (String(tmdbId).replace(/^tmdb:/i, '') !== '603') return [];
    return [{
        name: ADDON_NAME + '666 diagnostic',
        title: lines.filter(Boolean).join('\n'),
        url: 'https://diagnostic.invalid/' + ADDON_NAME.toLowerCase(),
        provider: ADDON_NAME + '666',
        type: 'direct'
    }];
}

// --- Adresse de l'addon (jamais en clair dans les logs) -------------------
function addonBase() {
    var s = (typeof SCRAPER_SETTINGS !== 'undefined' && SCRAPER_SETTINGS) ? SCRAPER_SETTINGS : {};
    var url = String(s.url || '').trim();
    if (!url) return null;
    if (/^stremio:\/\//i.test(url)) url = 'https://' + url.substring('stremio://'.length);
    url = url.replace(/\/+$/, '').replace(/\/manifest\.json$/i, '').replace(/\/+$/, '');
    return /^https?:\/\//i.test(url) ? url : null;
}

function privateHost(url) {
    return String(url).replace(/^https?:\/\//i, '').split('/')[0];
}

// --- Identifiants ---------------------------------------------------------
async function resolveImdb(tmdbId, mediaType) {
    var id = String(tmdbId || '').replace(/^tmdb:/i, '');
    if (/^tt\d+$/i.test(id)) return id;
    var isTv = mediaType === 'tv' || mediaType === 'series';
    if (typeof TMDB_API_KEY !== 'undefined' && TMDB_API_KEY) {
        var data = await getJson('https://api.themoviedb.org/3/' + (isTv ? 'tv' : 'movie') + '/' + id +
            '/external_ids?api_key=' + encodeURIComponent(TMDB_API_KEY), 'TMDB');
        if (data && data.imdb_id) return data.imdb_id;
    }
    var info = await getJson(FRANK_URL + '/lookup/' + (isTv ? 'series' : 'movie') + '/' + id, 'lookup');
    return info && info.imdbId ? info.imdbId : null;
}

function streamHeaders(s) {
    var h = s.behaviorHints && s.behaviorHints.proxyHeaders && s.behaviorHints.proxyHeaders.request;
    if (!h || typeof h !== 'object') return undefined;
    var out = {};
    Object.keys(h).forEach(function (k) { out[k] = String(h[k]); });
    return Object.keys(out).length ? out : undefined;
}

// --- Point d'entrée appelé par 666TV --------------------------------------
async function getStreams(tmdbId, mediaType, season, episode) {
    var base = addonBase();
    if (!base) {
        console.log(ADDON_NAME + '666 : pas d\'adresse configurée (Plugins → Comet666 – mes sources)');
        var keys = (typeof SCRAPER_SETTINGS !== 'undefined' && SCRAPER_SETTINGS) ? Object.keys(SCRAPER_SETTINGS).join(',') : 'aucun';
        return diagnostic(tmdbId, ['Adresse absente ou invalide', 'Réglages reçus : ' + (keys || 'aucun'), 'id plugin : ' + (typeof SCRAPER_ID !== 'undefined' ? SCRAPER_ID : '?')]);
    }
    var isTv = mediaType === 'tv' || mediaType === 'series';
    var imdbId = await resolveImdb(tmdbId, mediaType);
    if (!imdbId) {
        console.log(ADDON_NAME + '666 : id IMDb introuvable pour ' + tmdbId);
        return diagnostic(tmdbId, ['Adresse OK (' + privateHost(base) + ')', 'id IMDb introuvable pour ' + tmdbId, LAST_ERROR]);
    }
    var sid = isTv ? imdbId + ':' + season + ':' + episode : imdbId;
    var data = await getJson(base + '/stream/' + (isTv ? 'series' : 'movie') + '/' + sid + '.json', ADDON_NAME);
    var streams = (data && data.streams) || [];

    var found = [];
    var seen = {};
    var dropped = { noUrl: 0, notCached: 0, lang: 0, quality: 0 };
    streams.forEach(function (s) {
        if (!s || !s.url || !/^https?:\/\//i.test(s.url)) { dropped.noUrl++; return; }
        var hints = s.behaviorHints || {};
        var filename = hints.filename || '';
        var text = [s.name, s.title, s.description, filename].filter(Boolean).join('\n');
        if (NOT_CACHED.test(String(s.name || ''))) { dropped.notCached++; return; }
        var lang = frenchLabel(text);
        if (!lang) { dropped.lang++; return; }
        var quality = qualityOf(text);
        if (quality !== '1080p' && quality !== '2160p') { dropped.quality++; return; }
        var release = filename || String(s.description || s.title || '').split('\n')[0] || ADDON_NAME;
        var key = String(release).toLowerCase().replace(/\.(mkv|mp4|avi)$/, '').replace(/[^a-z0-9]+/g, ' ').trim();
        if (seen[s.url] || seen[key]) return;
        seen[s.url] = true;
        seen[key] = true;
        var sizeMatch = text.match(/💾\s*([\d.,]+\s*(?:TB|GB|MB|To|Go|Mo))/i) || text.match(/([\d.,]+\s*(?:TB|GB|MB|To|Go|Mo))\b/i);
        found.push({
            url: s.url, release: release, text: text, lang: lang, quality: quality,
            bytes: Number(hints.videoSize) || sizeToBytes(sizeMatch && sizeMatch[1]),
            headers: streamHeaders(s)
        });
    });
    console.log(ADDON_NAME + '666 : ' + streams.length + ' flux reçus, ' + found.length + ' gardés (écartés : ' +
        dropped.noUrl + ' sans lien, ' + dropped.notCached + ' pas en cache, ' + dropped.lang + ' pas VF, ' +
        dropped.quality + ' < 1080p)');

    if (!found.length) {
        var first = streams[0] || {};
        return diagnostic(tmdbId, [
            'Adresse OK (' + privateHost(base) + ') · ' + imdbId,
            data ? streams.length + ' flux reçus de ' + ADDON_NAME : 'Aucune réponse : ' + LAST_ERROR,
            'écartés : ' + dropped.noUrl + ' sans lien, ' + dropped.notCached + ' pas en cache, ' + dropped.lang + ' pas VF, ' + dropped.quality + ' < 1080p',
            streams.length ? 'ex. nom : ' + scrub(first.name).slice(0, 80) : '',
            streams.length ? 'ex. titre : ' + scrub([first.title, first.description].filter(Boolean).join(' | ')).slice(0, 200) : '',
            streams.length ? 'ex. lien : ' + (first.url ? first.url.split(':')[0] : 'aucun') + (first.infoHash ? ' · infoHash' : '') : ''
        ]);
    }

    found.sort(function (a, b) {
        var c = (a.lang.indexOf('?') >= 0) - (b.lang.indexOf('?') >= 0);
        if (c) return c;
        var q = (QUALITY_RANK[b.quality] || 0) - (QUALITY_RANK[a.quality] || 0);
        if (q) return q;
        return (b.bytes || 0) - (a.bytes || 0);
    });

    return found.slice(0, MAX_RESULTS).map(function (t) {
        var line2 = ['🇫🇷 ' + t.lang].concat(extras(t.text)).join(' · ');
        var line3 = [formatSize(t.bytes), ADDON_NAME].filter(Boolean).join(' · ');
        return {
            name: (ADDON_NAME + '666 ' + t.quality).trim(),
            title: t.release + '\n' + line2 + '\n' + line3,
            url: t.url,
            quality: t.quality,
            size: formatSize(t.bytes) || undefined,
            language: 'fr',
            provider: ADDON_NAME + '666',
            type: 'direct',
            headers: t.headers
        };
    });
}

if (typeof module !== 'undefined' && module.exports) module.exports = { getStreams: getStreams, _frenchLabel: frenchLabel, _addonBase: addonBase };
