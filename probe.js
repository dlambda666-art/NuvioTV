const fs = require('fs');
const plugin = require('./comet666.js');
const ids = [['tt1675434','movie','Intouchables'],['tt1064932','movie','Chtis'],['tt0464913','movie','OSS117'],['tt0471061','series','Kaamelott',1,1],['tt0052520','series','TwilightZone',1,1]];
(async () => {
  const out = { at: new Date().toISOString(), rows: [] };
  async function get(url) { try { const r = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } }); const t = await r.text(); return { status: r.status, t }; } catch (e) { return { status: 0, t: String(e) }; } }
  for (const [id, type, name, s, e] of ids) {
    const sid = type === 'series' ? `${id}:${s}:${e}` : id;
    const row = { name };
    for (const [src, url] of [
      ['torrentsdb', `https://torrentsdb.com/stream/${type}/${sid}.json`],
      ['peerflix', `https://peerflix.mov/stream/${type}/${sid}.json`],
      ['peerflix_fr', `https://peerflix.mov/language=fr/stream/${type}/${sid}.json`],
      ['mediafusion', `https://mediafusion.elfhosted.com/stream/${type}/${sid}.json`],
    ]) {
      const r = await get(url); let n = 0, fr = 0, ex = '';
      try { const j = JSON.parse(r.t); const st = j.streams || []; n = st.length;
        for (const x of st) { const txt = (x.title||'')+'\n'+(x.name||'')+'\n'+(x.description||''); if (plugin._frenchLabel(txt)) { fr++; if (!ex) ex = txt.split('\n')[0].slice(0,80); } } } catch {}
      row[src] = { status: r.status, n, fr, ex, head: n ? '' : r.t.slice(0, 120) };
    }
    const r = await get(`https://stremthru.13377001.xyz/v0/torznab/api?t=search&imdbid=${id}`);
    const titles = [...r.t.matchAll(/<title>([^<]+)<\/title>/g)].map(m => m[1]);
    row.stremthru = { status: r.status, n: titles.length, fr: titles.filter(t => plugin._frenchLabel(t)).length, ex: titles.find(t => plugin._frenchLabel(t)) || '' };
    out.rows.push(row);
  }
  fs.writeFileSync('test-report.json', JSON.stringify(out, null, 1));
})();
