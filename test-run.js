// Sonde ponctuelle (GitHub Actions) : La Quatrième Dimension (tt0052520) — que trouve Comet666 ? (07/11)
const fs = require('fs');
const plugin = require('./comet666.js');
async function get(u) {
  try { const r = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' } }); const t = await r.text();
    let j = null; try { j = JSON.parse(t); } catch (_) {} return { status: r.status, json: j, text: j ? undefined : t.slice(0, 4000) }; }
  catch (e) { return { err: String(e) }; }
}
(async () => {
  const report = { at: new Date().toISOString() };
  report.cases = [];
  for (const [se, ep] of [[1, 1], [1, 2], [2, 1], [3, 4], [5, 1]]) {
    const t0 = Date.now(); let res = [], err = null;
    try { res = await plugin.getStreams('tt0052520', 'tv', se, ep); } catch (x) { err = String(x); }
    const torrentio = 'https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9|language=french|qualityfilter=720p,480p,other,scr,cam,unknown';
    const t = await get(torrentio + '/stream/series/tt0052520:' + se + ':' + ep + '.json');
    const db = await get('https://torrentsdb.com/stream/series/tt0052520:' + se + ':' + ep + '.json');
    report.cases.push({ se, ep, ms: Date.now() - t0, err, count: res.length, comet: res.map(r => r.title.replace(/\n/g, ' | ')),
      torrentioFr: ((t.json && t.json.streams) || []).filter(x => /fr|🇫🇷|multi/i.test(x.title || '')).map(x => (x.title || '').replace(/\n/g, ' | ').slice(0, 160)),
      torrentsdbFr: ((db.json && db.json.streams) || []).filter(x => /french|🇫🇷|multi|vff|truefrench/i.test(x.title || '')).map(x => (x.title || '').replace(/\n/g, ' | ').slice(0, 160)) });
  }
  fs.writeFileSync('test-report.json', JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report.cases, null, 1).slice(0, 3000));
})();
