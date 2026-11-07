// Sonde ponctuelle : lecture des traces d'enquête de Comet666 chez Frank (07/11)
const fs = require('fs');
(async () => {
  let out = {};
  try { const r = await fetch('https://lambda666-frankenstream.hf.space/comet/trace', { headers: { 'User-Agent': 'Mozilla/5.0' } }); out = { status: r.status, body: await r.json() }; }
  catch (e) { out = { err: String(e) }; }
  fs.writeFileSync('test-report.json', JSON.stringify({ at: new Date().toISOString(), traces: out }, null, 2));
  console.log('ok');
})();
