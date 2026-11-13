// Sonde (13/11) : config de l'addon TMDB public — comment l'adresse est construite (aucune clé)
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  const t = await (await fetch('https://tmdb.elfhosted.com/configure/assets/index-b2Yfghja.js')).text();
  r.len = t.length;
  r.hits = (t.match(/.{0,300}(compressToEncodedURIComponent|manifest\.json).{0,300}/g) || []).slice(0, 6);
  r.lang = (t.match(/.{0,200}fr-FR.{0,200}/g) || []).slice(0, 2);
  r.catalogs = (t.match(/.{0,120}tmdb\.(top|trending|year|language|discover).{0,160}/g) || []).slice(0, 6);
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
