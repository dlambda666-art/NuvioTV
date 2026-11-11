// Sonde ponctuelle (11/11) : fiches de /films/vf/ et flux RSS de French-Stream (pas de clé)
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString(), pages: {} };
  const H = { 'User-Agent': 'Mozilla/5.0', Referer: 'https://fs23.lol/' };
  for (const p of ['/films/vf/', '/engine/rss.php?do=cat&category=films', '/engine/rss.php?do=cat&category=s-tv', '/xfsearch/lang/Français/']) {
    try {
      const res = await fetch('https://fs23.lol' + encodeURI(p).replace(/%25/g, '%'), { headers: H });
      const t = await res.text();
      const links = [...new Set([...t.matchAll(/href=["']([^"']+\.html)["']/g)].map(m => m[1]))];
      const i = t.indexOf(links[0] || '@@');
      r.pages[p] = { status: res.status, len: t.length, nlinks: links.length, links: links.slice(0, 40),
        sample: p.includes('rss') ? t.slice(0, 5000) : t.slice(Math.max(0, i - 1500), i + 3000) };
    } catch (e) { r.pages[p] = { err: String(e) }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
