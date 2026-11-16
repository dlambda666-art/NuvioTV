// Sonde (16/11) : Wacustom / Viewio répondent-ils (sans aucune config ni clé) ?
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  for (const u of ['https://wacustom.nipva.com/', 'https://wacustom.nipva.com/manifest.json', 'https://wacustom.nipva.com/health', 'https://viewio.fr/', 'https://viewio.app/']) {
    const t0 = Date.now();
    try {
      const x = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' }, redirect: 'manual', signal: AbortSignal.timeout(30000) });
      const t = await x.text();
      r[u] = { status: x.status, ms: Date.now() - t0, location: x.headers.get('location'), server: x.headers.get('server'), body: t.replace(/\s+/g, ' ').slice(0, 300) };
    } catch (e) { r[u] = { error: String(e).slice(0, 160), cause: String(e.cause || '').slice(0, 160), ms: Date.now() - t0 }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
