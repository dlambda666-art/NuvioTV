// Sonde ponctuelle : Frank après reconstruction (09/11)
const fs = require('fs');
async function get(u) { const t0 = Date.now(); try { const r = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' } }); const t = await r.text(); return { status: r.status, ms: Date.now() - t0, body: t.slice(0, 600) }; } catch (e) { return { err: String(e) }; } }
(async () => {
  const r = { at: new Date().toISOString() };
  r.frankRoot = await get('https://lambda666-frankenstream.hf.space/');
  r.frankManifest = await get('https://lambda666-frankenstream.hf.space/manifest.json');
  r.hfStatus = await get('https://status.huggingface.co/api/v2/summary.json');
  r.wastream = await get('https://huggingface.co/api/spaces?search=wastream&limit=1');
  fs.writeFileSync('test-report.json', JSON.stringify(r, null, 2)); console.log('ok');
})();
