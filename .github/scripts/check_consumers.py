"""Compile and execute documentation as external Maven consumers; reject leaked receivers."""
from pathlib import Path
import os
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
VERSION = ET.parse(ROOT / 'pom.xml').getroot().findtext('m:version', namespaces=NS)

def pom(model):
    return f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
      <modelVersion>4.0.0</modelVersion><groupId>consumer.check</groupId><artifactId>external-{model}</artifactId><version>1</version>
      <dependencies><dependency><groupId>io.github.adumeige.infermats</groupId><artifactId>infermats-{model}</artifactId><version>{VERSION}</version></dependency></dependencies>
      <build><sourceDirectory>src/main/kotlin</sourceDirectory><plugins>
        <plugin><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-maven-plugin</artifactId><version>2.3.10</version>
          <configuration><jvmTarget>21</jvmTarget><compilerPlugins><plugin>kotlinx-serialization</plugin></compilerPlugins></configuration>
          <dependencies><dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-maven-serialization</artifactId><version>2.3.10</version></dependency></dependencies>
          <executions><execution><id>compile</id><phase>compile</phase><goals><goal>compile</goal></goals></execution></executions>
        </plugin>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version><configuration><release>21</release></configuration></plugin>
        <plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId><version>3.5.0</version></plugin>
        <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-dependency-plugin</artifactId><version>3.8.1</version></plugin>
      </plugins></build></project>'''

def run(model):
    with tempfile.TemporaryDirectory(prefix='infermats-consumer-') as directory:
        root = Path(directory)
        (root / 'pom.xml').write_text(pom(model))
        source = root / 'src/main/kotlin/Example.kt'
        source.parent.mkdir(parents=True)
        snippets = re.findall(r'```kotlin\n(.*?)```', (ROOT / f'docs/{model}.md').read_text(), re.S)
        assert len(snippets) == 1
        source.write_text(snippets[0])
        command = [str(ROOT / 'mvnw'), '-B', '-ntp', '-f', str(root / 'pom.xml')]
        result = subprocess.run(command + ['compile', 'exec:java', '-Dexec.mainClass=ExampleKt', 'dependency:tree', '-DoutputFile=dependencies.txt'], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if result.returncode: raise RuntimeError(result.stdout)
        tree = (root / 'dependencies.txt').read_text()
        sibling = 'jev' if model == 'ideogram' else 'ideogram'
        assert f'infermats-{sibling}' not in tree
        for banned in ('spring', 'vaadin', 'coroutines', 'kotlin-reflect', 'langchain', 'embabel', 'httpclient', 'logback'):
            assert banned not in tree, tree
        negative = {
            'ideogram': 'import io.github.adumeige.infermats.ideogram.v4.*\nfun bad() = ideogram4 { composition("x") { summary = "leaked" } }',
            'jev': 'import io.github.adumeige.infermats.jev.*\nfun bad() = jevRequest("m") { stateText("x"); questions { noul("x") { stateText("leaked") } } }'
        }
        source.write_text(negative[model])
        result = subprocess.run(command + ['clean', 'compile'], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        assert result.returncode != 0, 'Invalid implicit receiver unexpectedly compiled'
        assert 'implicit receiver' in result.stdout.lower(), result.stdout
        print(f'{model}: external example executed; dependency isolation and DSL negative compilation verified.')

if __name__ == '__main__':
    for model in ('ideogram', 'jev'): run(model)
