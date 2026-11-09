// Sonde ponctuelle : mots-clés TMDB pour les portes de l'horreur (09/11). Jamais la clé dans le rapport.
const fs = require('fs');
const K = process.env.TMDB_API_KEY || '';
async function j(path) { try { const r = await fetch('https://api.themoviedb.org/3' + path + (path.includes('?') ? '&' : '?') + 'api_key=' + K); return await r.json(); } catch (e) { return { err: String(e).replace(K, '***') }; } }
(async () => {
  const r = { at: new Date().toISOString(), hasKey: !!K, kw: {}, companies: {}, providers: null };
  for (const q of ['slasher', 'vampire', 'werewolf', 'zombie', 'cannibal', 'body horror', 'haunted house', 'demonic possession', 'possession', 'exorcism', 'witch', 'witchcraft', 'lovecraftian', 'cosmic horror', 'creature', 'monster', 'creature feature', 'kaiju', 'giant monster']) {
    const d = await j('/search/keyword?query=' + encodeURIComponent(q));
    r.kw[q] = (d.results || []).slice(0, 6).map(x => x.id + ':' + x.name);
  }
  for (const q of ['Blumhouse']) { const d = await j('/search/company?query=' + q); r.companies[q] = (d.results || []).slice(0, 5).map(x => x.id + ':' + x.name + ':' + x.origin_country); }
  const p = await j('/watch/providers/movie?watch_region=FR');
  r.providers = (p.results || []).filter(x => /shudder|netflix|prime|canal|disney|max|apple|paramount|ocs|filmo|universciné|arte/i.test(x.provider_name)).map(x => x.provider_id + ':' + x.provider_name);
  const pu = await j('/watch/providers/movie?watch_region=US');
  r.providersUS = (pu.results || []).filter(x => /shudder/i.test(x.provider_name)).map(x => x.provider_id + ':' + x.provider_name);
  for (const [name, path] of [['fr_horror_count', '/discover/movie?with_genres=27&watch_region=FR&with_watch_monetization_types=flatrate|free|ads|rent|buy&sort_by=popularity.desc&language=fr-FR'], ['shudder_fr', '/discover/movie?watch_region=FR&with_watch_providers=99&sort_by=popularity.desc'], ['shudder_us', '/discover/movie?watch_region=US&with_watch_providers=99&sort_by=popularity.desc']]) {
    const d = await j(path); r[name] = { total: d.total_results, first: (d.results || []).slice(0, 5).map(x => x.title) };
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r, null, 2)); console.log('ok');
})();
