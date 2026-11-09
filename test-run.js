// Sonde ponctuelle (09/11) : structure de la recherche French-Stream pour la vérif VF sur la Shield
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  for (const q of ['The Thing', 'Cannibal Holocaust', 'Terrifier 3']) {
    try {
      const res = await fetch('https://maj.french-stream.pink/engine/ajax/search.php', { method: 'POST', headers: { 'User-Agent': 'Mozilla/5.0', Referer: 'https://maj.french-stream.pink/', Origin: 'https://maj.french-stream.pink', 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'query=' + encodeURIComponent(q) + '&page=1' });
      const t = await res.text();
      r[q] = { status: res.status, len: t.length, head: t.slice(0, 2500) };
    } catch (e) { r[q] = { err: String(e) }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r, null, 2)); console.log('ok');
})();
