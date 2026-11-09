// Sonde ponctuelle : état du Space AIOMeta après la fusion du 09/11
const fs = require('fs');
async function get(u, opts) {
  const t0 = Date.now();
  try { const r = await fetch(u, Object.assign({ headers: { 'User-Agent': 'Mozilla/5.0' } }, opts || {})); const t = await r.text();
    return { status: r.status, ms: Date.now() - t0, body: t.slice(0, 1500) }; }
  catch (e) { return { err: String(e), ms: Date.now() - t0 }; }
}
(async () => {
  const r = { at: new Date().toISOString() };
  r.space = await get('https://huggingface.co/api/spaces/Lambda666/AIOMeta');
  r.runtime = await get('https://huggingface.co/api/spaces/Lambda666/AIOMeta/runtime');
  r.home = await get('https://lambda666-aiometa.hf.space/');
  r.health = await get('https://lambda666-aiometa.hf.space/health');
  r.manifest = await get('https://lambda666-aiometa.hf.space/manifest.json');
  r.api = await get('https://lambda666-aiometa.hf.space/api/version');
  fs.writeFileSync('test-report.json', JSON.stringify(r, null, 2));
  console.log('ok');
})();
