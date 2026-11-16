// Sonde (16/11) : Wastream répond-il (sans aucune config ni clé) ?
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  for (const u of ['https://wastream.noplay.fr/', 'https://wastream.noplay.fr/manifest.json', 'https://wastream.noplay.fr/stream/movie/tt0133093.json', 'https://wastream.noplay.fr/health']) {
    const t0 = Date.now();
    try {
      const x = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' }, redirect: 'manual', signal: AbortSignal.timeout(30000) });
      const t = await x.text();
      r[u] = { status: x.status, ms: Date.now() - t0, location: x.headers.get('location'), server: x.headers.get('server'), body: t.replace(/\s+/g, ' ').slice(0, 300) };
    } catch (e) { r[u] = { error: String(e).slice(0, 160), cause: String(e.cause || '').slice(0, 160), ms: Date.now() - t0 }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
