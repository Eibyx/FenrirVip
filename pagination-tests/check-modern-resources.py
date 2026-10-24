"""Resource integrity and Spanish coverage checks; Android linking is verified by Gradle."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / 'app_fenrir/src/main/res'

def resources(folder):
    result = {}
    for path in sorted(folder.glob('*.xml')):
        for item in ET.parse(path).getroot():
            if item.tag not in ('string', 'string-array', 'plurals'):
                continue
            key = (item.tag, item.get('name'))
            assert key not in result, f'Duplicate {key} in {path}'
            result[key] = item
    return result

base, spanish = resources(res/'values'), resources(res/'values-es')
pattern = re.compile(r'%(?:\d+\$)?[sdfoxXeEgGcCbBhHaA%]')
checked = 0
for key, original in base.items():
    if original.get('translatable') == 'false':
        continue
    assert key in spanish, f'Missing Spanish resource: {key}'
    translated = spanish[key]
    assert ''.join(translated.itertext()).strip(), f'Empty translation: {key}'
    if key[0] == 'string-array':
        assert len(original) == len(translated), f'Array size changed: {key}'
        pairs = zip(original, translated)
    else:
        pairs = [(original, translated)]
    for source, target in pairs:
        assert sorted(pattern.findall(''.join(source.itertext()))) == sorted(pattern.findall(''.join(target.itertext()))), f'Format arguments changed: {key}'
    checked += 1
assert [x.text for x in base[('string-array','array_language_items')]] == ['0','1','2','3']
assert spanish[('string-array','array_language_names')][-1].text == 'Español'
print(f'{checked} Spanish resources checked: coverage, duplicates, array sizes and formatting arguments')
