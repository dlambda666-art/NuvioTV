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

// autre doublage annoncé (polonais, italien…) : un « MULTI » sans mot français est alors suspect
var OTHER_DUB = /\b(?:POLISH|PLDUB|PL|LEKTOR|DUBBING|ITA|ITALIAN|GER|GERMAN|DEUTSCH|SPANISH|CASTELLANO|LATINO|RUS|RUSSIAN|UKR|HUN|CZE|CZECH|TUR|TURKISH|POR|PORTUGUESE|HINDI|TAMIL)\b|🇵🇱|🇮🇹|🇩🇪|🇪🇸|🇷🇺|🇺🇦|🇹🇷|🇵🇹|🇧🇷|🇭🇺|🇨🇿|🇲🇽/i;

function frenchLabel(text) {
    if (SUBTITLE_ONLY.test(text) || CAM_RELEASE.test(text)) return null;
    // 666TV : MULTI / drapeau sans mot français + un autre doublage (ex. polonais) → pas VF.
    if (!FRENCH_AUDIO.test(text) && OTHER_DUB.test(text)) return null;
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
    // L'écran de test n'affiche que la 1re ligne des 3 premiers flux :
    // une entrée par ligne, coupée en morceaux courts.
    var out = [];
    lines.filter(Boolean).forEach(function (line) {
        var text = String(line);
        while (text.length) {
            out.push({
                name: ADDON_NAME + '666 diagnostic',
                title: text.slice(0, 70),
                url: 'https://diagnostic.invalid/' + ADDON_NAME.toLowerCase() + '/' + out.length,
                provider: ADDON_NAME + '666',
                type: 'direct'
            });
            text = text.slice(70);
        }
    });
    return out;
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
// 666TV : date de diffusion de l'épisode (TMDB), pour les émissions datées (WWE, talk-shows…)
async function episodeAirDate(tmdbId, season, episode) {
    if (typeof TMDB_API_KEY === 'undefined' || !TMDB_API_KEY) return null;
    var id = String(tmdbId || '').replace(/^tmdb:/i, '');
    var key = encodeURIComponent(TMDB_API_KEY);
    if (/^tt\d+$/i.test(id)) {
        var f = await getJson('https://api.themoviedb.org/3/find/' + id + '?external_source=imdb_id&api_key=' + key, 'TMDB');
        id = f && f.tv_results && f.tv_results[0] ? String(f.tv_results[0].id) : '';
    }
    if (!/^\d+$/.test(id)) return null;
    var ep = await getJson('https://api.themoviedb.org/3/tv/' + id + '/season/' + season + '/episode/' + episode + '?api_key=' + key, 'TMDB');
    return ep && ep.air_date ? ep.air_date : null;
}

// 666TV : vrai si le nom du fichier porte une date (2026.10.06) qui n'est pas celle de l'épisode (± 1 jour)
function dateMismatch(text, airDate) {
    if (!airDate) return false;
    var target = Date.parse(airDate + 'T00:00:00Z');
    if (isNaN(target)) return false;
    var re = /\b(20\d\d)[ ._-](\d\d)[ ._-](\d\d)\b/g, m, any = false;
    while ((m = re.exec(text))) {
        var d = Date.parse(m[1] + '-' + m[2] + '-' + m[3] + 'T00:00:00Z');
        if (isNaN(d)) continue;
        any = true;
        if (Math.abs(d - target) <= 36 * 3600 * 1000) return false;
    }
    return any;
}

// 666TV : vrai si le flux annonce un AUTRE épisode (ex. S01E01 alors qu'on demande S01E05).
// 666TV : séries anthologie — le même épisode rangé dans une autre saison
// (« Monster: The Lizzie Borden Story S01E05 » pour Monstre S04E05).
function otherSeasonSameEpisode(text, season, episode) {
    var re = /\bS(\d{1,2})[ ._-]?E(\d{1,3})\b/ig, m;
    while ((m = re.exec(text))) {
        // seulement « S01 » : une saison d'anthologie numérotée comme une série à part
        if (parseInt(m[2], 10) === parseInt(episode, 10) && parseInt(m[1], 10) === 1 && parseInt(season, 10) > 1) return true;
    }
    return false;
}

function episodeMismatch(text, season, episode) {
    var re = /\bS(\d{1,2})[ ._-]?E(\d{1,3})(?:[ ._-]?(?:-|E)[ ._-]?E?(\d{1,3}))?/ig;
    var m, any = false, s = parseInt(season, 10), e = parseInt(episode, 10);
    while ((m = re.exec(text))) {
        any = true;
        var a = parseInt(m[2], 10), b = m[3] ? parseInt(m[3], 10) : a;
        if (parseInt(m[1], 10) === s && e >= a && e <= Math.max(a, b)) return false;
    }
    return any;
}

// 666TV : UNE seule requête TMDB pour un film (id IMDb, année, titres, dates de
// sortie en France). Les requêtes du lecteur de plugins passent l'une après
// l'autre : chaque appel en plus retarde la « Lecture VF ».
async function movieDetails(tmdbId) {
    if (typeof TMDB_API_KEY === 'undefined' || !TMDB_API_KEY) return null;
    var id = String(tmdbId || '').replace(/^tmdb:/i, '');
    if (!/^\d+$/.test(id)) return null;
    return await getJson('https://api.themoviedb.org/3/movie/' + id +
        '?append_to_response=release_dates,external_ids&api_key=' + encodeURIComponent(TMDB_API_KEY), 'TMDB');
}

// Vrai si le nom du fichier annonce une autre année (à ±1 an près).
// Les années qui font partie du titre (« 1998, l'été de nos 18 ans ») ne comptent pas.
function yearMismatch(text, info) {
    if (!info || !info.year) return false;
    var inTitle = (info.titles || []).join(' ');
    var re = /(?:^|[^0-9])(19[2-9]\d|20[0-4]\d)(?![0-9])/g, m, any = false;
    while ((m = re.exec(text))) {
        var y = Number(m[1]);
        if (inTitle.indexOf(m[1]) >= 0) continue;
        any = true;
        if (Math.abs(y - info.year) <= 1) return false;
    }
    return any;
}

// 666TV : film français encore au cinéma. En France, la VOD n'arrive que 4 mois
// après la sortie en salle : avant ça, un « fichier » est soit un CAM, soit un
// homonyme (ex. Kraken avec Gazo, sorti le 30/09/2026, contre le Kraken norvégien).
function frenchFilmInCinema(m) {
    if (!m || m.original_language !== 'fr') return false;
    var fr = ((m.release_dates && m.release_dates.results) || []).filter(function (r) { return r.iso_3166_1 === 'FR'; })[0];
    var dates = (fr && fr.release_dates) || [];
    var now = Date.now(), cinema = 0, digital = false;
    dates.forEach(function (d) {
        var t = Date.parse(d.release_date);
        if (isNaN(t)) return;
        if ((d.type === 2 || d.type === 3) && t > cinema) cinema = t;
        if ((d.type === 4 || d.type === 5 || d.type === 6) && t <= now) digital = true;
    });
    if (!cinema && m.release_date) cinema = Date.parse(m.release_date) || 0;
    return !digital && cinema > 0 && cinema <= now + 86400000 && now - cinema < 120 * 86400000;
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
async function settleMulti(items) {
    if (!items.some(function (t) { return t.lang.indexOf('?') >= 0; })) return items;
    var data = await getJson(FRANK_URL + '/groupes-vf');
    if (!data) return items;
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

async function getStreams(tmdbId, mediaType, season, episode) {
    var base = addonBase();
    if (!base) {
        console.log(ADDON_NAME + '666 : pas d\'adresse configurée (Plugins → Comet666 – mes sources)');
        var keys = (typeof SCRAPER_SETTINGS !== 'undefined' && SCRAPER_SETTINGS) ? Object.keys(SCRAPER_SETTINGS).join(',') : 'aucun';
        return diagnostic(tmdbId, ['Adresse absente ou invalide', 'Réglages reçus : ' + (keys || 'aucun'), 'id plugin : ' + (typeof SCRAPER_ID !== 'undefined' ? SCRAPER_ID : '?')]);
    }
    var isTv = mediaType === 'tv' || mediaType === 'series';
    var details = isTv ? null : await movieDetails(tmdbId);
    var imdbId = (details && details.external_ids && details.external_ids.imdb_id) || await resolveImdb(tmdbId, mediaType);
    if (!imdbId) {
        console.log(ADDON_NAME + '666 : id IMDb introuvable pour ' + tmdbId);
        return diagnostic(tmdbId, ['Adresse OK (' + privateHost(base) + ')', 'id IMDb introuvable pour ' + tmdbId, LAST_ERROR]);
    }
    var sid = isTv ? imdbId + ':' + season + ':' + episode : imdbId;
    var airDate = isTv ? await episodeAirDate(tmdbId, season, episode) : null;
    var film = details ? {
        year: Number(String(details.release_date || '').slice(0, 4)) || null,
        titles: [details.title, details.original_title].filter(Boolean)
    } : null;
    if (frenchFilmInCinema(details)) {
        console.log(ADDON_NAME + '666 : film français encore au cinéma, pas de VOD avant 4 mois');
        return [];
    }
    // 666TV : épisode pas encore diffusé → rien (sinon on lance un vieux fichier).
    if (airDate && Date.parse(airDate + 'T00:00:00Z') > Date.now() + 24 * 3600 * 1000) {
        console.log(ADDON_NAME + '666 : épisode pas encore diffusé (' + airDate + ')');
        return [];
    }
    var data = await getJson(base + '/stream/' + (isTv ? 'series' : 'movie') + '/' + sid + '.json', ADDON_NAME);
    var streams = (data && data.streams) || [];

    var found = [];
    var seen = {};
    var dropped = { noUrl: 0, notCached: 0, lang: 0, quality: 0, ep: 0 };
    streams.forEach(function (s) {
        if (!s || !s.url || !/^https?:\/\//i.test(s.url)) { dropped.noUrl++; return; }
        var hints = s.behaviorHints || {};
        var filename = hints.filename || '';
        var text = [s.name, s.title, s.description, filename].filter(Boolean).join('\n');
        if (NOT_CACHED.test(String(s.name || ''))) { dropped.notCached++; return; }
        var altSeason = false;
        if (isTv && dateMismatch(text, airDate)) { dropped.ep++; return; }
        if (isTv && episodeMismatch(text, season, episode)) {
            if (parseInt(season, 10) > 1 && otherSeasonSameEpisode(text, season, episode)) altSeason = true;
            else { dropped.ep++; return; }
        }
        if (!isTv && yearMismatch(text, film)) { dropped.ep++; return; }
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
            headers: streamHeaders(s),
            altSeason: altSeason
        });
    });
    console.log(ADDON_NAME + '666 : ' + streams.length + ' flux reçus, ' + found.length + ' gardés (écartés : ' +
        dropped.noUrl + ' sans lien, ' + dropped.notCached + ' pas en cache, ' + dropped.lang + ' pas VF, ' +
        dropped.quality + ' < 1080p, ' + dropped.ep + ' autre épisode / autre film)');

    // Les fichiers « autre saison » ne servent que s'il n'y a rien d'exact.
    if (found.some(function (f) { return !f.altSeason; })) found = found.filter(function (f) { return !f.altSeason; });

    if (!found.length) {
        var first = streams[0] || {};
        return diagnostic(tmdbId, [
            data ? streams.length + ' flux reçus de ' + ADDON_NAME : 'Aucune réponse : ' + LAST_ERROR,
            'Adresse OK (' + privateHost(base) + ') · ' + imdbId,
            'écartés : ' + dropped.noUrl + ' sans lien, ' + dropped.notCached + ' pas en cache, ' + dropped.lang + ' pas VF, ' + dropped.quality + ' < 1080p',
            streams.length ? 'ex. nom : ' + scrub(first.name).slice(0, 80) : '',
            streams.length ? 'ex. titre : ' + scrub([first.title, first.description].filter(Boolean).join(' | ')).slice(0, 200) : '',
            streams.length ? 'ex. lien : ' + (first.url ? first.url.split(':')[0] : 'aucun') + (first.infoHash ? ' · infoHash' : '') : ''
        ]);
    }

    found = await settleMulti(found);
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

if (typeof module !== 'undefined' && module.exports) module.exports = { _releaseGroup: releaseGroup, _settleMulti: settleMulti, getStreams: getStreams, _frenchLabel: frenchLabel, _addonBase: addonBase };
