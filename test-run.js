// Sonde (13/11) : addon TMDB public d'ElfHosted — format de config et catalogues en français (aucune clé)
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  const get = async (u, json) => { try { const x = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' }, signal: AbortSignal.timeout(25000) }); const t = await x.text(); return { status: x.status, url: x.url, body: json ? (() => { try { return JSON.parse(t) } catch (e) { return t.slice(0, 500) } })() : t.slice(0, 6000) }; } catch (e) { return { err: String(e).slice(0, 120) }; } };
  r.manifest = await get('https://tmdb.elfhosted.com/manifest.json', true);
  r.configure = await get('https://tmdb.elfhosted.com/configure', false);
  const js = (r.configure.body || '').match(/src="([^"]+\.js)"/g) || [];
  r.js = js.slice(0, 5);
  for (const s of js.slice(0, 3)) { const u = new URL(s.slice(5, -1), 'https://tmdb.elfhosted.com/').href; const g = await get(u, false); r['js_' + u.split('/').pop()] = (g.body || '').match(/.{0,200}(compressToEncodedURIComponent|encodeURIComponent\(JSON|manifest\.json).{0,200}/g)?.slice(0, 4); }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
