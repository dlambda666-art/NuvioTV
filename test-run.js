// Sonde ponctuelle (11/11) : structure des pages « nouveautés » de French-Stream (pas de clé)
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString(), pages: {} };
  const H = { 'User-Agent': 'Mozilla/5.0', Referer: 'https://fs23.lol/' };
  for (const p of ['/', '/films/', '/s-tv/', '/xfsearch/version-film/TRUEFRENCH/', '/films/page/2/']) {
    try {
      const res = await fetch('https://fs23.lol' + p, { headers: H, redirect: 'follow' });
      const t = await res.text();
      const hrefs = [...new Set([...t.matchAll(/href=["']([^"'#]+)["']/g)].map(m => m[1]))].slice(0, 120);
      const i = t.search(/short|film-poster|movie-item|class="item/);
      r.pages[p] = { status: res.status, url: res.url, len: t.length, hrefs, sample: t.slice(Math.max(0, i - 300), i + 3500) };
    } catch (e) { r.pages[p] = { err: String(e) }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
