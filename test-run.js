// Sonde ponctuelle (09/11) : Cinemeta a-t-il les derniers épisodes WWE / séries suivies (calendrier, cloche) ?
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  for (const [n, id] of [['Raw', 'tt0185103'], ['SmackDown', 'tt0227972'], ['NXT', 'tt1601141'], ['AHS', 'tt1844624'], ['Monstre', 'tt13207736']]) {
    try {
      const j = await (await fetch('https://v3-cinemeta.strem.io/meta/series/' + id + '.json')).json();
      const v = ((j.meta && j.meta.videos) || []).filter(x => x.released).sort((a, b) => String(b.released).localeCompare(String(a.released)));
      r[n] = { name: j.meta && j.meta.name, count: v.length, latest: v.slice(0, 3).map(x => `S${x.season}E${x.episode} ${String(x.released).slice(0, 10)} ${x.name || ''}`) };
    } catch (e) { r[n] = { err: String(e) }; }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r, null, 2)); console.log('ok');
})();
