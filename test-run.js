// Sonde ponctuelle (GitHub Actions) : film « Affection » (TMDB 1339210) — VF ? catalogue nouveautés ?
const fs = require('fs');
const plugin = require('./comet666.js');
const FRANK = 'https://lambda666-frankenstream.hf.space';
async function get(u, json = true) {
  try { const r = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' } }); const t = await r.text();
    let j = null; if (json) { try { j = JSON.parse(t); } catch (_) {} }
    return { status: r.status, json: j, text: j ? undefined : t.slice(0, 600) }; }
  catch (e) { return { err: String(e) }; }
}
(async () => {
  const report = { at: new Date().toISOString(), cases: [] };
  const look = await get(FRANK + '/lookup/movie/1339210'); report.lookup = look;
  report.debug = await get(FRANK + '/poster-debug/movie/1339210');
  const man = await get(FRANK + '/manifest.json');
  report.catalogs = ((man.json && man.json.catalogs) || []).map(c => c.type + '/' + c.id + ' ' + c.name);
  report.inCatalogs = {};
  for (const c of ((man.json && man.json.catalogs) || []).filter(c => c.type === 'movie')) {
    const r = await get(FRANK + '/catalog/movie/' + c.id + '.json');
    const metas = (r.json && r.json.metas) || [];
    const hit = metas.filter(m => /affection/i.test(m.name || ''));
    report.inCatalogs[c.id] = { count: metas.length, hit: hit.map(m => ({ id: m.id, name: m.name, desc: (m.description || '').slice(0, 160) })) };
  }
  const imdb = look.json && look.json.imdbId;
  for (const id of ['1339210', imdb].filter(Boolean)) {
    const t0 = Date.now(); let res = [], err = null;
    try { res = await plugin.getStreams(id, 'movie'); } catch (x) { err = String(x); }
    report.cases.push({ id, ms: Date.now() - t0, count: res.length, err, sample: res.slice(0, 8).map(r => r.title.split('\n').slice(0, 2).join(' | ')) });
  }
  if (imdb) {
    const t = await get('https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9/stream/movie/' + imdb + '.json');
    report.torrentioAll = ((t.json && t.json.streams) || []).slice(0, 12).map(s => String(s.title || '').split('\n')[0]);
  }
  fs.writeFileSync('test-report.json', JSON.stringify(report, null, 1));
  console.log(JSON.stringify(report).slice(0, 3000));
})();
