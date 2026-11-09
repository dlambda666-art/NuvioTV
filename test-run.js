// Sonde ponctuelle (09/11) : nouvelle adresse French-Stream (fs23.lol) — même recherche que l'ancienne ?
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  for (const base of ['https://fs23.lol', 'https://maj.french-stream.pink']) {
    try {
      const res = await fetch(base + '/engine/ajax/search.php', { method: 'POST', redirect: 'manual', headers: { 'User-Agent': 'Mozilla/5.0', Referer: base + '/', Origin: base, 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'query=Evil%20Dead%20Burn&page=1' });
      const t = await res.text();
      r[base] = { status: res.status, location: res.headers.get('location'), len: t.length, head: t.slice(0, 900) };
    } catch (e) { r[base] = { err: String(e) }; }
    try { const h = await fetch(base + '/', { redirect: 'manual', headers: { 'User-Agent': 'Mozilla/5.0' } }); r[base + ' home'] = { status: h.status, location: h.headers.get('location') }; } catch (e) { r[base + ' home'] = { err: String(e) }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r, null, 2)); console.log('ok');
})();
