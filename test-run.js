// Test réel du plugin (exécuté sur GitHub Actions, Node 20 avec fetch intégré).
const fs = require('fs');
const plugin = require('./comet666.js');
(async () => {
  const cases = [['tt1844624','tv',13,1],['tt1844624','tv',13,5],['tt15239678','movie'],['tt15398776','movie'],['tt6263850','movie'],['tt3581920','tv',1,1],['tt0944947','tv',1,1],['tt11280740','tv',1,1],['tt1190634','tv',1,1],['tt9218128','movie']];
  const report = { at: new Date().toISOString(), cases: [] };
  for (const [id, type, s, e] of cases) {
    const t0 = Date.now();
    let res = [], err = null;
    try { res = await plugin.getStreams(id, type, s, e); } catch (x) { err = String(x); }
    report.cases.push({ id, type, s, e, ms: Date.now() - t0, count: res.length, err,
      sample: res.slice(0, id === 'tt1844624' ? 20 : 6).map(r => ({ name: r.name, title: r.title })) });
  }
  // sondes brutes des sources
  for (const u of ['https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9|language=french/stream/movie/tt0111161.json',
                   'https://zileanfortheweebs.midnightignite.me/dmm/filtered?ImdbId=tt0111161']) {
    try { const r = await fetch(u); const b = await r.text(); report.cases.push({ probe: u.slice(0, 60), status: r.status, len: b.length, head: b.slice(0, 200) }); }
    catch (x) { report.cases.push({ probe: u.slice(0, 60), err: String(x) }); }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(report, null, 1));
  console.log(JSON.stringify(report).slice(0, 2000));
})();
