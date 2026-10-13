/*
 * Comet666 — plugin de flux VF pour 666TV.
 * Tourne sur la Shield (QuickJS). Cherche des torrents dans des index publics,
 * ne garde que la VF (pas de VOSTFR seule, pas de version cinéma) et renvoie
 * l'empreinte : c'est l'intégration debrid de 666TV (TorBox) qui fournit le
 * lien. 666TV ne lit jamais un torrent en P2P.
 */

var FRANK_URL = 'https://lambda666-frankenstream.hf.space';
var TORRENTIO_URL = 'https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9|language=french';
var ZILEAN_URL = 'https://zileanfortheweebs.midnightignite.me';
var MAX_RESULTS = 40;

// --- Règles de langue (mêmes que Frank) -----------------------------------
var SUBTITLE_ONLY = /\bVOST(?:FR)?\b|\bSTFR\b|\bSUB\.?FR(?:ENCH)?\b|\bSUBFRENCH\b|\bFRENCH[ ._-]*SUB(?:S|BED|TITLE[SD]?)?\b|\bSOUS[ ._-]*TITR/i;
var FRENCH_AUDIO = /\bVF[FQI2]?\b|\bTRUE[ ._-]*FRENCH\b|\bFRENCH\b|\bVERSION FRAN[CÇ]AISE\b|\bVOF\b/i;
var MULTI = /\bMULTI(?:[ ._-]*(?:VFF|VFQ|VF2|TRUEFRENCH|FRENCH))?\b/i;
var FRENCH_FLAG = /🇫🇷/;
var CAM_RELEASE = /\b(?:HD[ ._-]?)?CAM(?:[ ._-]?RIP)?\b|\b(?:HD[ ._-]?)?TS\b|\bTELE[ ._-]?SYNC\b|\b(?:HD[ ._-]?)?TC\b|\bTELE[ ._-]?CINE\b|\bMD\b|\bMICRO[ ._-]?DUBBED\b|\bSCR(?:EENER)?\b/i;

function frenchLabel(text) {
    if (SUBTITLE_ONLY.test(text) || CAM_RELEASE.test(text)) return null;
    if (/\bVFQ\b/i.test(text)) return 'VFQ';
    if (/\bVFF\b|\bTRUE[ ._-]*FRENCH\b/i.test(text)) return 'VFF';
    if (/\bVF2\b/i.test(text)) return 'VF2';
    if (FRENCH_AUDIO.test(text)) return 'VF';
    if (MULTI.test(text) && FRENCH_FLAG.test(text)) return 'MULTI VF';
    return null;
}

function qualityOf(text) {
    if (/\b(2160p|4k|uhd)\b/i.test(text)) return '2160p';
    if (/\b1080p\b/i.test(text)) return '1080p';
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
    var info = await getJson(FRANK_URL + '/lookup/' + (isTv ? 'series' : 'movie') + '/' + id);
    return info && info.imdbId ? { imdbId: info.imdbId } : null;
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
        out.push({ hash: String(hash).toLowerCase(), release: title, text: title, bytes: Number(t.size) || 0, seeders: null, source: 'DMM' });
    });
    return out;
}

// --- Point d'entrée appelé par 666TV --------------------------------------
async function getStreams(tmdbId, mediaType, season, episode) {
    var isTv = mediaType === 'tv' || mediaType === 'series';
    var ids = await resolveIds(tmdbId, mediaType);
    if (!ids || !ids.imdbId) {
        console.log('Comet666 : id IMDb introuvable pour ' + tmdbId);
        return [];
    }
    var found = [];
    var lists = [await fromTorrentio(ids.imdbId, isTv, season, episode), await fromZilean(ids.imdbId, isTv, season, episode)];
    var seen = {};
    var seenNames = {};
    lists.forEach(function (list) {
        list.forEach(function (t) {
            // même release publiée deux fois (avec ou sans « .mkv ») : une seule ligne
            var key = String(t.release).toLowerCase().replace(/[ ._-]*mkv$/, '').replace(/[^a-z0-9]+/g, ' ').trim();
            if (seen[t.hash] || seenNames[key]) return;
            var lang = frenchLabel(t.text);
            if (!lang) return;
            seen[t.hash] = true;
            seenNames[key] = true;
            t.lang = lang;
            t.quality = qualityOf(t.text);
            found.push(t);
        });
    });
    found.sort(function (a, b) {
        var q = (QUALITY_RANK[b.quality] || 0) - (QUALITY_RANK[a.quality] || 0);
        if (q) return q;
        return (b.bytes || 0) - (a.bytes || 0);
    });
    return found.slice(0, MAX_RESULTS).map(function (t) {
        var line2 = ['🇫🇷 ' + t.lang].concat(extras(t.text)).join(' · ');
        var line3 = [formatSize(t.bytes), t.seeders != null ? '👤 ' + t.seeders : '', t.source].filter(Boolean).join(' · ');
        return {
            name: ('Comet666 ' + (t.quality || '')).trim(),
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
}

if (typeof module !== 'undefined' && module.exports) module.exports = { getStreams: getStreams, _frenchLabel: frenchLabel };
