// Sonde (13/11) : Clayface — TMDB, French-Stream, liens publics (aucune clé debrid)
const fs = require('fs');
(async () => {
  const k = process.env.TMDB_API_KEY; const r = { at: new Date().toISOString() };
  const j = async (u, o) => { try { const x = await fetch(u, Object.assign({ headers: { 'User-Agent': 'Mozilla/5.0' }, signal: AbortSignal.timeout(20000) }, o || {})); const t = await x.text(); try { return JSON.parse(t) } catch (e) { return t } } catch (e) { return String(e).slice(0, 100) } };
  const s = await j(`https://api.themoviedb.org/3/search/movie?language=fr-FR&query=Clayface&api_key=${k}`);
  r.tmdb = (s.results || []).slice(0, 4).map(m => ({ id: m.id, t: m.title, o: m.original_title, d: m.release_date, lang: m.original_language }));
  const m0 = r.tmdb[0];
  if (m0) {
    m0.imdb = (await j(`https://api.themoviedb.org/3/movie/${m0.id}/external_ids?api_key=${k}`)).imdb_id;
    const rd = await j(`https://api.themoviedb.org/3/movie/${m0.id}/release_dates?api_key=${k}`);
    m0.rel = (rd.results || []).filter(x => ['FR', 'BE', 'US', 'CA'].includes(x.iso_3166_1)).map(x => x.iso_3166_1 + ':' + x.release_dates.map(d => d.type + '@' + d.release_date.slice(0, 10)).join(','));
    const pv = await j(`https://api.themoviedb.org/3/movie/${m0.id}/watch/providers?api_key=${k}`);
    m0.providers = ['FR', 'BE'].map(c => c + ':' + JSON.stringify(Object.fromEntries(Object.entries((pv.results || {})[c] || {}).filter(([kk]) => kk !== 'link').map(([kk, v]) => [kk, (v || []).map(p => p.provider_name)]))));
    if (m0.imdb) { const t = await j(`https://torrentio.strem.fun/stream/movie/${m0.imdb}.json`); m0.torrentio = (t.streams || []).map(x => (x.title || '').split('\n')[0]).slice(0, 15); }
  }
  const fsr = await j('https://fs23.lol/engine/ajax/search.php', { method: 'POST', headers: { 'User-Agent': 'Mozilla/5.0', 'Content-Type': 'application/x-www-form-urlencoded', Referer: 'https://fs23.lol/' }, body: 'query=Clayface&page=1' });
  r.fs = typeof fsr === 'string' ? fsr.replace(/\s+/g, ' ').slice(0, 1200) : fsr;
  const rss = await j('https://fs23.lol/engine/rss.php?do=cat&category=films');
  r.rss = typeof rss === 'string' ? (rss.match(/<item[\s\S]*?Claw[\s\S]*?<\/item>/i) || ['absent'])[0].slice(0, 800) : 'err';
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
