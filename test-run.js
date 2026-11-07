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
  const FRANK = 'https://lambda666-frankenstream.hf.space';
  report.yggQueries = await get(FRANK + '/ygg/series/tt0052520?season=3&episode=6&norelay=1');
  const torrentioAll = 'https://torrentio.strem.fun';
  const t = await get(torrentioAll + '/stream/series/tt0052520:3:6.json');
  report.torrentioAll = ((t.json && t.json.streams) || []).map(x => (x.title || '').replace(/\n/g, ' | ').slice(0, 200));
  const db = await get('https://torrentsdb.com/stream/series/tt0052520:3:6.json');
  report.torrentsdbAll = ((db.json && db.json.streams) || []).map(x => (x.title || '').replace(/\n/g, ' | ').slice(0, 200));
  const st = await get('https://stremthru.13377001.xyz/v0/torznab/api?t=search&imdbid=tt0052520');
  report.stremthruAll = (st.text || '').split('<item>').slice(1).map(i => (i.match(/<title>([^<]*)<\/title>/) || [])[1]).slice(0, 60);
  let res = []; try { res = await plugin.getStreams('tt0052520', 'tv', 3, 6); } catch (x) {}
  report.comet = res.map(r => r.title.replace(/\n/g, ' | '));
  fs.writeFileSync('test-report.json', JSON.stringify(report, null, 2));
  console.log('ok');
})();
