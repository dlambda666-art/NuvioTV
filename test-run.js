// Sonde (15/11) : comment French-Stream annonce sa nouvelle adresse
const fs = require('fs');
(async () => {
  const r = { at: new Date().toISOString() };
  for (const u of ['https://maj.french-stream.pink/', 'https://fs23.lol/', 'https://french-stream.pink/', 'https://french-stream.legal/']) {
    try {
      const x = await fetch(u, { headers: { 'User-Agent': 'Mozilla/5.0' }, redirect: 'manual', signal: AbortSignal.timeout(20000) });
      const t = await x.text();
      const links = [...new Set((t.match(/https?:\/\/[a-z0-9.-]+\.[a-z]{2,}/gi) || []))].filter(l => !/google|cloudflare|jquery|gstatic|w3\.org|schema|facebook|twitter|discord|telegram|font/i.test(l)).slice(0, 40);
      r[u] = { status: x.status, location: x.headers.get('location'), title: (t.match(/<title>([^<]*)/i) || [])[1], links, text: t.replace(/<script[\s\S]*?<\/script>/gi, '').replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').slice(0, 1500) };
    } catch (e) { r[u] = String(e).slice(0, 120); }
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
