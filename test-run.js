// Sonde ponctuelle (11/11 soir) : Malfaisante / Other Mommy — ids TMDB/IMDb et liens publics (sans clé debrid)
const fs = require('fs');
(async () => {
  const k = process.env.TMDB_API_KEY; const r = { at: new Date().toISOString() };
  const j = async u => { try { const x = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' }, signal: AbortSignal.timeout(20000) }); return { status: x.status, body: await x.json() }; } catch (e) { return { err: String(e).slice(0, 100) }; } };
  const s = await j(`https://api.themoviedb.org/3/search/movie?language=fr-FR&query=Malfaisante&api_key=${k}`);
  r.search = (s.body?.results || []).map(m => ({ id: m.id, t: m.title, o: m.original_title, d: m.release_date }));
  for (const m of r.search.slice(0, 3)) {
    const e = await j(`https://api.themoviedb.org/3/movie/${m.id}/external_ids?api_key=${k}`); m.imdb = e.body?.imdb_id;
    const rd = await j(`https://api.themoviedb.org/3/movie/${m.id}/release_dates?api_key=${k}`);
    m.fr = (rd.body?.results || []).filter(x => ['FR', 'BE', 'US'].includes(x.iso_3166_1)).map(x => x.iso_3166_1 + ':' + x.release_dates.map(d => d.type + '@' + d.release_date.slice(0, 10)).join(','));
    if (m.imdb) {
      const t = await j(`https://torrentio.strem.fun/stream/movie/${m.imdb}.json`);
      m.torrentio = { status: t.status, n: t.body?.streams?.length, titles: (t.body?.streams || []).map(x => (x.title || '').split('\n')[0]).filter(x => /french|vff|truefrench|multi/i.test(x)).slice(0, 15) };
      const cm = await j(`https://v3-cinemeta.strem.io/meta/movie/${m.imdb}.json`); m.cinemeta = cm.body?.meta?.name || cm.status;
    }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
