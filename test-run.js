// Sonde ponctuelle (10/11) : voix pour l'aperçu de l'accueil (synthèse vocale Google, en base64)
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString(), clips: {} };
  for (const [k, q] of [['six', 'Six cent soixante-six'], ['tvf', 'T. V. F.'], ['bienvenue', 'Bienvenue'], ['seance', 'La séance va commencer']]) {
    try {
      const u = 'https://translate.google.com/translate_tts?ie=UTF-8&tl=fr&client=tw-ob&ttsspeed=0.6&q=' + encodeURIComponent(q);
      const res = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0', Referer: 'https://translate.google.com/' } });
      const b = Buffer.from(await res.arrayBuffer());
      r.clips[k] = { status: res.status, type: res.headers.get('content-type'), b64: b.toString('base64') };
    } catch (e) { r.clips[k] = { err: String(e) }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
