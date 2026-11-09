// Sonde ponctuelle (09/11) : mots-clés TMDB des catalogues thématiques de David. Jamais la clé dans le rapport.
const fs = require('fs');
const K = process.env.TMDB_API_KEY || '';
async function j(path) { try { const r = await fetch('https://api.themoviedb.org/3' + path + (path.includes('?') ? '&' : '?') + 'api_key=' + K); return await r.json(); } catch (e) { return { err: 'x' }; } }
(async () => {
  const r = { at: new Date().toISOString(), kw: {} };
  for (const q of ['alien', 'alien invasion', 'artificial intelligence', 'dystopia', 'space travel', 'spacecraft', 'superhero', 'survival', 'time travel', 'found footage', 'supernatural', 'anthology', 'adult animation', 'miniseries', 'biography', 'detective', 'werewolf']) {
    const d = await j('/search/keyword?query=' + encodeURIComponent(q));
    r.kw[q] = (d.results || []).slice(0, 4).map(x => x.id + ':' + x.name);
  }
  const up = await j('/movie/upcoming?region=FR&language=fr-FR');
  r.upcoming = { total: up.total_results, first: (up.results || []).slice(0, 5).map(x => x.title + ' ' + x.release_date) };
  fs.writeFileSync('test-report.json', JSON.stringify(r, null, 2)); console.log('ok');
})();
