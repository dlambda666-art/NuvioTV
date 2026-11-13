// Sonde (13/11) : options de l'addon TMDB public liées aux identifiants IMDb (aucune clé)
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  const t = await (await fetch('https://tmdb.elfhosted.com/configure/assets/index-b2Yfghja.js')).text();
  const i = t.indexOf('showAgeRatingWithImdbRating:typeof');
  r.cfgObj = t.slice(Math.max(0, i - 2500), i + 200);
  r.imdbKeys = [...new Set((t.match(/[a-zA-Z]*[Ii]mdb[a-zA-Z]*/g) || []))].slice(0, 40);
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
