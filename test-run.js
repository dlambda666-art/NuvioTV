// Test réel du plugin (exécuté sur GitHub Actions, Node 20 avec fetch intégré).
const fs = require('fs');
const plugin = require('./comet666.js');
(async () => {
  const cases = [['tt0185103','tv',34,43],['tt0185103','tv',34,42],['tt0185103','tv',34,41],['tt15239678','movie'],['tt15398776','movie'],['tt6263850','movie'],['tt3581920','tv',1,1],['tt0944947','tv',1,1],['tt11280740','tv',1,1],['tt1190634','tv',1,1],['tt9218128','movie']];
  const report = { at: new Date().toISOString(), cases: [] };
  for (const [id, type, s, e] of cases) {
    const t0 = Date.now();
    let res = [], err = null;
    try { res = await plugin.getStreams(id, type, s, e); } catch (x) { err = String(x); }
    report.cases.push({ id, type, s, e, ms: Date.now() - t0, count: res.length, err,
      sample: res.slice(0, id === 'tt0185103' ? 15 : 6).map(r => ({ name: r.name, title: r.title })) });
  }
  // sondes brutes des sources
  for (const u of ['https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl,torrent9|language=french/stream/movie/tt0111161.json',
                   'https://zileanfortheweebs.midnightignite.me/dmm/filtered?ImdbId=tt0111161']) {
    try { const r = await fetch(u); const b = await r.text(); report.cases.push({ probe: u.slice(0, 60), status: r.status, len: b.length, head: b.slice(0, 200) }); }
    catch (x) { report.cases.push({ probe: u.slice(0, 60), err: String(x) }); }
  }
  // sonde du relais YGG (u2p) : avec et sans en-tête Origin
  for (const withOrigin of [true, false]) {
    const r = await new Promise(resolve => {
      const out = { withOrigin, events: 0, sample: [] };
      let ws;
      try { const WS = require('ws'); ws = new WS('wss://u2p.anhkagi.net/', withOrigin ? { origin: 'https://www.ygg.re' } : {}); }
      catch (e) { out.err = String(e); return resolve(out); }
      const t = setTimeout(() => { try { ws.close(); } catch (_) {} resolve(out); }, 6000);
      ws.on('open', () => ws.send(JSON.stringify(['REQ', 'probe', { kinds: [2003], search: 'Matrix 1999', limit: 5 }])));
      ws.on('message', m => { const d = JSON.parse(String(m)); if (d[0] === 'EVENT') { out.events++; const tt = (d[2].tags || []).find(x => x[0] === 'title'); out.sample.push(tt && tt[1]); } if (d[0] === 'EOSE') { clearTimeout(t); ws.close(); resolve(out); } });
      ws.on('unexpected-response', (req, res) => { out.status = res.statusCode; out.headers = res.headers; });
      ws.on('error', e => { out.err = String(e.message); clearTimeout(t); resolve(out); });
    });
    report.cases.push({ probe: 'ygg-relay', ...r });
  }
  fs.writeFileSync('test-report.json', JSON.stringify(report, null, 1));
  console.log(JSON.stringify(report).slice(0, 2000));
})();
