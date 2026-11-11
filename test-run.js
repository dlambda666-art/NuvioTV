// Sonde ponctuelle (11/11) : Flemmix (ex-Wiflix) + Malfaisante (TMDB / French-Stream). Clé TMDB jamais écrite.
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString(), hosts: {}, tmdb: null, fs: null };
  const H = { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36' };
  for (const h of ['flemmix.casa', 'flemmix.app', 'flemmix.net', 'flemmix.top', 'flemmix.one', 'flemmix.fun', 'flemmix.pro', 'flemmix.wiki', 'wiflix.al', 'wiflix.name', 'flemmix.bond', 'flemmix.click', 'flemmix.cfd', 'flemmix.sbs']) {
    try {
      const res = await fetch('https://' + h + '/', { headers: H, redirect: 'manual', signal: AbortSignal.timeout(12000) });
      const t = res.status < 300 ? await res.text() : '';
      r.hosts[h] = { status: res.status, loc: res.headers.get('location'), len: t.length, cf: /just a moment|cf-chl/i.test(t), title: (t.match(/<title>([^<]*)/) || [])[1],
        rss: [...new Set([...t.matchAll(/href=["']([^"']*(rss|xml)[^"']*)["']/g)].map(m => m[1]))].slice(0, 5),
        links: [...new Set([...t.matchAll(/href=["']([^"']+)["']/g)].map(m => m[1]))].filter(u => !/\.(css|png|ico|svg|js)/.test(u)).slice(0, 50) };
    } catch (e) { r.hosts[h] = { err: String(e).slice(0, 80) }; }
  }
  const k = process.env.TMDB_API_KEY;
  try { const j = await (await fetch(`https://api.themoviedb.org/3/search/movie?language=fr-FR&query=Malfaisante&api_key=${k}`)).json();
    r.tmdb = (j.results || []).slice(0, 5).map(m => ({ id: m.id, t: m.title, o: m.original_title, d: m.release_date })); } catch (e) { r.tmdb = String(e); }
  try { const res = await fetch('https://fs23.lol/engine/ajax/search.php', { method: 'POST', headers: { ...H, 'Content-Type': 'application/x-www-form-urlencoded', Referer: 'https://fs23.lol/', Origin: 'https://fs23.lol' }, body: 'query=Malfaisante&page=1' });
    const t = await res.text(); r.fs = { status: res.status, sample: t.slice(0, 1500) };
    const href = (t.match(/location\.href=['"]([^'"]+)['"]/) || [])[1];
    if (href) { const p = await (await fetch('https://fs23.lol' + (href.startsWith('/') ? href : '/' + href), { headers: H })).text();
      r.fs.lang = (p.match(/id=["']film_lang["'][^>]*>([\s\S]*?)<\/span>/) || [])[1]; r.fs.quality = (p.match(/id=["']film_quality["'][^>]*>([\s\S]*?)<\/span>/) || [])[1]; }
  } catch (e) { r.fs = String(e); }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
