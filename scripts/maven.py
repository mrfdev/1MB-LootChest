"""Run installed Maven on Oracle 25 with isolated, validated 25/27 toolchains."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

from oracle_jdks import JAVA_OPTION_VARIABLES, java_properties, resolve_jdk

ROOT = Path(__file__).resolve().parent.parent
# These can replace the launcher, toolchains, compiler, or forked test JVM.
RESERVED_PREFIXES = ('maven.compiler.', 'maven.toolchains.', 'toolchain.', 'jdk.',
                     'java.', 'env.', 'lootchest.jdk.', 'surefire.', 'failsafe.',
                     'maven.ext.', 'maven.multiModuleProjectDirectory')
RESERVED_PROPERTIES = {'release', 'source', 'target', 'jvm', 'forkCount', 'forkMode',
                       'argLine', 'compilerId', 'maven.home', 'maven.test.skip',
                       'maven.test.skip.exec', 'maven.test.failure.ignore',
                       'skipTests', 'skipITs', 'enforcer.skip'}
RESERVED_OPTIONS = ('-t', '--toolchains', '-gt', '--global-toolchains',
                    '-s', '--settings', '-gs', '--global-settings',
                    '-f', '--file', '-P', '--activate-profiles', '--fail-never')


def validate_arguments(arguments):
    index = 0
    while index < len(arguments):
        argument = arguments[index]
        if argument in ('-fae', '-ff'):
            index += 1
            continue
        if argument in ('-D', '--define'):
            index += 1
            if index == len(arguments):
                raise RuntimeError(f'Missing value for {argument}')
            prop = arguments[index].split('=', 1)[0]
        elif argument.startswith('-D'):
            prop = argument[2:].split('=', 1)[0]
        elif argument.startswith('--define='):
            prop = argument[len('--define='):].split('=', 1)[0]
        else:
            prop = ''
        if prop in RESERVED_PROPERTIES or prop.startswith(RESERVED_PREFIXES):
            raise RuntimeError(f'{prop} is managed by ./mvnw; use ONE_MB_*_JAVA_HOME for JDK selection')
        for option in RESERVED_OPTIONS:
            if argument == option or argument.startswith(option + '=') or (
                    not option.startswith('--') and argument.startswith(option)):
                raise RuntimeError(f'{option} is managed by ./mvnw and cannot be overridden')
        index += 1


def validate_environment():
    for name in (*JAVA_OPTION_VARIABLES, 'MAVEN_OPTS', 'MAVEN_ARGS', 'MAVEN_DEBUG_OPTS',
                 'MAVEN_BASEDIR', 'MAVEN_CONFIG'):
        if os.environ.get(name, '').strip():
            raise RuntimeError(f'Unset {name} before ./mvnw; it can override the validated JVM setup')
    for name in ('maven.config', 'jvm.config', 'extensions.xml'):
        path = ROOT / '.mvn' / name
        if path.exists() and path.read_text().strip():
            raise RuntimeError(f'Remove or reconcile stale {path}; ./mvnw owns the local JVM setup')


def write_toolchains(path, build_home, test_home):
    root = ET.Element('toolchains')
    for version, home in ((25, build_home), (27, test_home)):
        chain = ET.SubElement(root, 'toolchain')
        ET.SubElement(chain, 'type').text = 'jdk'
        provides = ET.SubElement(chain, 'provides')
        ET.SubElement(provides, 'version').text = str(version)
        ET.SubElement(provides, 'vendor').text = 'Oracle Corporation'
        ET.SubElement(ET.SubElement(chain, 'configuration'), 'jdkHome').text = str(home)
    ET.ElementTree(root).write(path, encoding='utf-8', xml_declaration=True)


def main(arguments):
    validate_arguments(arguments)
    validate_environment()
    build_home = resolve_jdk(25, 'ONE_MB_BUILD_JAVA_HOME')
    test_home = resolve_jdk(27, 'ONE_MB_TEST_JAVA_HOME')
    maven = shutil.which('mvn')
    if not maven:
        raise RuntimeError('Install Maven locally first; ./mvnw does not download Maven or JDKs')
    env = dict(os.environ, JAVA_HOME=str(build_home),
               PATH=str(build_home / 'bin') + os.pathsep + os.environ.get('PATH', ''),
               MAVEN_SKIP_RC='1', MAVEN_BASEDIR=str(ROOT), MAVEN_PROJECTBASEDIR=str(ROOT),
               ONE_MB_BUILD_JAVA_HOME=str(build_home), ONE_MB_TEST_JAVA_HOME=str(test_home),
               LOOTCHEST_TOOLCHAINS_VALIDATED='1')
    for label, home in (('Maven / javac / test javac', build_home), ('Test JVMs / Paper', test_home)):
        props = java_properties(home)
        print(f"[jdk] {label}: {home} ({props['java.version']}, {props['java.vendor']})", flush=True)
    print('[jdk] Compiler release: 25 (class major 69); installed JDKs only, downloads disabled', flush=True)
    # Explicit user AND global files prevent ~/.m2 and Maven installation defaults
    # from selecting another JDK or activating an unreviewed build profile.
    with tempfile.TemporaryDirectory(prefix='lootchest-maven-') as directory:
        config = Path(directory)
        chains = config / 'toolchains.xml'
        write_toolchains(chains, build_home, test_home)
        empty_chains = config / 'global-toolchains.xml'
        empty_chains.write_text('<toolchains/>\n')
        settings = config / 'settings.xml'
        settings.write_text('<settings/>\n')
        command = [maven, '--toolchains', str(chains), '--global-toolchains', str(empty_chains),
                   '--settings', str(settings), '--global-settings', str(settings), *arguments]
        return subprocess.call(command, cwd=ROOT, env=env)


if __name__ == '__main__':
    try:
        sys.exit(main(sys.argv[1:]))
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(f'[jdk] {error}')
