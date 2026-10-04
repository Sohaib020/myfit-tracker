"""Zips each character's rendered pack (dist/<id>.zip with pack.json) and writes a review contact sheet (prev/)."""
import os, sys, json, zipfile, re
from PIL import Image
src, dist, prev = sys.argv[1] + '/', sys.argv[2] + '/', sys.argv[3] + '/'
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
clips = re.findall(r"'(\w+)': \(\w+, \d+\)", open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'anims.py')).read())
rows = []; report = []
for who in sorted(os.listdir(src)):
    d = src + who + '/'
    have = [c for c in clips if os.path.exists(d + c + '.webp')]
    looks = len(os.listdir(d + 'look')) if os.path.isdir(d + 'look') else 0
    talk = len(os.listdir(d + 'talk')) if os.path.isdir(d + 'talk') else 0
    missing = [c for c in clips if c not in have]
    report.append('%s: %d/%d clips, %d looks, %d talk%s' % (who, len(have), len(clips), looks, talk, (' MISSING ' + ','.join(missing)) if missing else ''))
    if missing or looks < 49 or talk < 6: continue          # only publish complete packs
    with zipfile.ZipFile(dist + who + '.zip', 'w', zipfile.ZIP_STORED) as z:
        z.writestr('pack.json', json.dumps({'id': who, 'lookN': 7, 'version': 1, 'clips': have}))
        for root, _, files in os.walk(d):
            for f in files: z.write(os.path.join(root, f), os.path.relpath(os.path.join(root, f), d))
    row = []
    for c in ('idle', 'wave', 'celebrate', 'dance', 'flex', 'hydrate', 'sleepy', 'hearteyes'):
        im = Image.open(d + c + '.webp'); im.seek(im.n_frames // 2); row.append(im.convert('RGBA').resize((120, 120)))
    rows.append(row)
open(prev + 'report.txt', 'w').write('\n'.join(report)); print('\n'.join(report))
if rows:
    sheet = Image.new('RGBA', (120 * 8, 120 * len(rows)), 'white')
    for i, r in enumerate(rows):
        for j, im in enumerate(r): sheet.paste(im, (j * 120, i * 120), im)
    sheet.save(prev + 'sheet.png')
