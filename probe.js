const fs = require('fs');
(async () => {
  const out = {};
  async function j(name, url, opts) {
    try { const r = await fetch(url, opts); const t = await r.text(); let d=null; try{d=JSON.parse(t)}catch{}; out[name] = { status: r.status, len: t.length, sample: d ? JSON.stringify(d).slice(0, 1500) : t.slice(0, 300) }; }
    catch (e) { out[name] = { err: String(e) }; }
  }
  const q = 'quatrieme dimension';
  await j('knaben', 'https://api.knaben.org/v1', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ search_type: '100%', search_field: 'title', query: q + ' french', order_by: 'seeders', order_direction: 'desc', size: 20, hide_unsafe: true, hide_xxx: true }) });
  await j('apibay', 'https://apibay.org/q.php?q=' + encodeURIComponent(q + ' french'));
  await j('torrentio_tz', 'https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9|language=french/stream/series/tt0052520:1:1.json');
  await j('zilean_tz', 'https://zileanfortheweebs.midnightignite.me/dmm/filtered?ImdbId=tt0052520&Season=1&Episode=1');
  await j('zilean_search', 'https://zileanfortheweebs.midnightignite.me/dmm/search', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ queryText: 'quatrieme dimension' }) });
  fs.writeFileSync('test-report.json', JSON.stringify(out, null, 1));
})();
