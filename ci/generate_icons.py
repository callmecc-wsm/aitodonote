"""Build the offline icon bundle from the vendored, unmodified Semi SVGs."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
manifest = json.loads((ROOT / 'design-system/icons.json').read_text())
icons = {}
for name, source in manifest['icons'].items():
    svg = (ROOT / 'design-system/semi-icons' / (source + '.svg')).read_text().strip()
    root = ET.fromstring(svg)
    assert root.get('viewBox'), source
    assert not re.search(r'<(?:script|foreignObject|image)|\bon\w+=|(?:href|url\()', svg), source
    # Same normalization as Semi's React components: inherit the parent's ink.
    body = re.sub(r'^<svg\b[^>]*>|</svg>$', '', svg).strip()
    body = re.sub(r'\b(fill|stroke)="(?:black|#000(?:000)?)"', r'\1="currentColor"', body)
    icons[name] = {'source': source, 'viewBox': root.get('viewBox'), 'body': body}
content = '// Generated from @douyinfe/semi-icons ' + manifest['version'] + '; MIT, see LICENSE-SEMI.\n'
content += 'window.NoteIcons=' + json.dumps(icons, ensure_ascii=False, separators=(',', ':')) + ';\n'
target = ROOT / 'app/src/main/assets/icons.js'
if args.check:
    assert target.read_text() == content, 'Run python3 ci/generate_icons.py to refresh icons.js'
    print(f"Verified {len(icons)} Semi icon mappings")
else:
    target.write_text(content)
