// Sonde ponctuelle (GitHub Actions) : American Horror Story S13E01 — faux positif VF (07/11)
const fs = require('fs');
const plugin = require('./comet666.js');
async function get(u) {
  try { const r = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' } }); const t = await r.text();
    let j = null; try { j = JSON.parse(t); } catch (_) {} return { status: r.status, json: j, text: j ? undefined : t.slice(0, 4000) }; }
  catch (e) { return { err: String(e) }; }
}
(async () => {
  const report = { at: new Date().toISOString() };
  const t0 = Date.now(); let res = [], err = null;
  try { res = await plugin.getStreams('tt1844624', 'tv', 13, 1); } catch (x) { err = String(x); }
  report.comet = { ms: Date.now() - t0, err, count: res.length, streams: res.map(r => ({ title: r.title, hash: r.infoHash })) };
  const torrentio = 'https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9|language=french|qualityfilter=720p,480p,other,scr,cam,unknown';
  const t = await get(torrentio + '/stream/series/tt1844624:13:1.json');
  report.torrentioRaw = ((t.json && t.json.streams) || []).map(s => ({ name: s.name, title: s.title, hash: s.infoHash }));
  const db = await get('https://torrentsdb.com/stream/series/tt1844624:13:1.json');
  report.torrentsdbRaw = ((db.json && db.json.streams) || []).slice(0, 30).map(s => ({ name: s.name, title: s.title, hash: s.infoHash }));
  const st = await get('https://stremthru.13377001.xyz/v0/torznab/api?t=search&imdbid=tt1844624');
  report.stremthruRaw = (st.text || '').split('<item>').slice(1).map(i => (i.match(/<title>([^<]*)<\/title>/) || [])[1]).filter(x => /S13E01|S13\b/i.test(x || '')).slice(0, 30);
  const z = await get('https://zileanfortheweebs.midnightignite.me/dmm/filtered?ImdbId=tt1844624&Season=13&Episode=1');
  report.zileanRaw = (Array.isArray(z.json) ? z.json : []).slice(0, 30).map(x => ({ t: x.raw_title, h: x.info_hash, l: x.languages }));
  fs.writeFileSync('test-report.json', JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report.comet, null, 1).slice(0, 3000));
})();
