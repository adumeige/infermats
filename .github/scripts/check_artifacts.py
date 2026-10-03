"""Check unsigned reactor distribution: parent POM and two genuine Kotlin libraries."""
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}

def check():
    version = ET.parse(ROOT / 'pom.xml').getroot().findtext('m:version', namespaces=NS)
    for model in ('ideogram', 'jev'):
        artifact = 'infermats-' + model
        directory = ROOT / artifact / 'target'
        expected = {f'{artifact}-{version}{c}.jar' for c in ('', '-sources', '-javadoc')}
        actual = {p.name for p in directory.glob('*.jar')}
        assert actual == expected, (artifact, actual, expected)
        for classifier, suffix in (('', '.class'), ('-sources', '.kt'), ('-javadoc', '.html')):
            with ZipFile(directory / f'{artifact}-{version}{classifier}.jar') as jar:
                names = jar.namelist()
                assert any(n.endswith(suffix) for n in names), (artifact, classifier, 'empty archive')
                assert not any(n.startswith('io/github/adumeige/infermats/' + ('jev' if model == 'ideogram' else 'ideogram')) for n in names), 'Sibling module bundled'
                assert not any('CaptionTest' in n or 'RequestTest' in n or 'Ticket.class' in n for n in names), 'Tests leaked into publication'
                if classifier == '-javadoc':
                    assert any(n.endswith('.html') and b'<html' in jar.read(n).lower() and len(jar.read(n)) > 1000 for n in names), 'No genuine generated HTML'
    assert not list((ROOT / 'target').glob('*.jar')), 'Parent should ship only a POM'

if __name__ == '__main__':
    check()
    print('Exact unsigned artifact set and contents verified.')
