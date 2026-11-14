// Détourage (14/11) : Frank ado, pour le logo FrankenStream (rembg, sans clé)
const fs = require('fs'); const { execSync } = require('child_process');
const r = { at: new Date().toISOString() };
try {
  execSync('pip install -q "rembg[cpu]" 2>&1 | tail -2', { stdio: 'inherit', timeout: 600000 });
  fs.writeFileSync('cut.py', `
from rembg import remove, new_session
from PIL import Image
im = Image.open('frank_crop.png').convert('RGB')
for m in ['birefnet-general', 'isnet-general-use']:
    try:
        out = remove(im, session=new_session(m)); out.save('cut_' + m + '.png'); print('ok', m)
    except Exception as e: print('fail', m, e)
`);
  r.log = execSync('python3 cut.py 2>&1 | tail -5', { timeout: 1200000 }).toString();
  for (const m of ['birefnet-general', 'isnet-general-use']) { const f = 'cut_' + m + '.png'; if (fs.existsSync(f)) r[m] = fs.readFileSync(f).toString('base64'); }
} catch (e) { r.err = String(e).slice(0, 500); }
fs.writeFileSync('test-report.json', JSON.stringify(r)); console.log('ok');
