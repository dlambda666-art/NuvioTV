// Sonde (13/11) : adresse d'installation de l'addon TMDB public en français, testée (aucune clé)
const fs = require('fs'); const { execSync } = require('child_process');
(async () => {
  const r = { at: new Date().toISOString() };
  execSync('npm i --no-save --silent lz-string@1.5.0', { stdio: 'ignore' });
  const lz = require(process.cwd() + '/node_modules/lz-string');
  const cats = [];
  for (const [id, name] of [['tmdb.latest', 'Latest Releases'], ['tmdb.trending', 'Trending'], ['tmdb.top', 'Popular']])
    for (const t of ['movie', 'series']) cats.push({ id, type: t, name, showInHome: true, enabled: true });
  const cfg = { language: 'fr-FR', catalogs: cats, searchEnabled: 'true' };
  const base = 'https://tmdb.elfhosted.com/' + lz.compressToEncodedURIComponent(JSON.stringify(cfg));
  r.url = base + '/manifest.json';
  const m = await (await fetch(r.url)).json();
  r.desc = m.description; r.cats = (m.catalogs || []).map(c => c.type + ':' + c.id + ':' + c.name);
  for (const c of (m.catalogs || []).slice(0, 2)) {
    const x = await (await fetch(`${base}/catalog/${c.type}/${c.id}.json`)).json();
    r['items_' + c.type + '_' + c.id] = (x.metas || []).slice(0, 5).map(v => v.name + (v.poster ? ' [affiche]' : ''));
  }
  fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
})();
