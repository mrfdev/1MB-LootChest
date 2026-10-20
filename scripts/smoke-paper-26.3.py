#!/usr/bin/env python3
"""Smoke the pinned project-local Paper 26.3 instance, never the shared server."""
import datetime
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
INSTANCE = ROOT / 'servers/Paper-26.3'
JDK = Path('/Library/Java/JavaVirtualMachines/jdk-27.0.1.jdk/Contents/Home')
ANSI = re.compile(r'\x1b\[[0-9;?]*[ -/]*[@-~]')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def properties(text):
    return dict(line.split('=', 1) for line in text.splitlines()
                if '=' in line and not line.startswith('#'))


def validate_target(props):
    require(props['paperVersion'] == '26.3' and props['maven.compiler.release'] == '25',
            'Expected Paper 26.3 and Java 25 compilation')
    require(props['paperChannel'] == 'STABLE', 'Compatibility testing requires Paper STABLE')
    require(props['paperBuild'].isdigit() and int(props['paperBuild']) > 0,
            'Paper build must be a positive reproducible pin')
    require(props['paperApiVersion'] == f"26.3.build.{props['paperBuild']}-stable",
            'Paper API must match the pinned stable server build')
    require(re.fullmatch(r'[0-9a-f]{64}', props['paperSha256']),
            'Paper server must have a SHA-256 pin')


def validate_runtime(runtime):
    require(re.search(r'^\s*java\.version = 27\.0\.1\s*$', runtime, re.MULTILINE)
            and re.search(r'^\s*java\.vendor = Oracle Corporation\s*$', runtime, re.MULTILINE),
            'Expected the installed Oracle Java 27.0.1 runtime')


def persisted_definitions(text):
    # Paper may reorder serialized component mappings between JVMs. Sort whole
    # entries with their folded continuation lines, preserving every value byte.
    # This is limited to the pinned fixture's item component maps, not a general
    # YAML parser. Real ItemStack equality is also checked by the runtime probe.
    require(text.startswith('chests:\n'), 'Expected the existing saved-chest fixture')
    def order_components(match):
        entries = []
        for line in match[1].splitlines(keepends=True):
            if len(line) > 10 and not line[10].isspace():
                entries.append(line)
            else:
                require(entries, 'Unexpected component continuation without a key')
                entries[-1] += line
        return '        components:\n' + ''.join(sorted(entries))

    text = re.sub(r'^        components:\n((?: {10}[^\n]*\n)+)',
                  order_components, text, flags=re.MULTILINE)
    return re.sub(r'^    lastreset: [0-9]+$', '    lastreset: <clock>', text, flags=re.MULTILINE)


@contextmanager
def installed_probe(plugins):
    classes = ROOT / 'lootchest/target/test-classes'
    probe_class = classes / 'fr/black_eyes/lootchest/compat/PaperRuntimeProbe.class'
    require(probe_class.is_file(), 'Run mvn clean verify to compile the runtime probe')
    path = plugins / 'LootChestSmokeProbe.jar'
    require(not path.exists() and not path.is_symlink(), 'Remove the stale smoke probe first')
    try:
        with zipfile.ZipFile(path, 'x', zipfile.ZIP_DEFLATED) as zipped:
            for compiled in sorted(probe_class.parent.glob('PaperRuntimeProbe*.class')):
                zipped.write(compiled, compiled.relative_to(classes))
            zipped.writestr('plugin.yml', '''name: LootChestSmokeProbe
version: '1'
main: fr.black_eyes.lootchest.compat.PaperRuntimeProbe
api-version: '26.3'
depend: [LootChest]
softdepend: [CMI, CMILib]
commands:
  lootchestprobe:
    description: Isolated console-only compatibility checks
''')
        yield
    finally:
        path.unlink(missing_ok=True)


@contextmanager
def optional_integrations(identities, backup, without_integrations):
    moved = []
    try:
        if without_integrations:
            directory = backup / 'optional-plugins'
            directory.mkdir()
            for path, name in identities.items():
                if name != 'LootChest':
                    require(not path.is_symlink(), 'Refuse a symlinked optional plugin JAR')
                    destination = directory / path.name
                    shutil.move(path, destination)
                    moved.append((path, destination))
        yield
    finally:
        for original, archived in reversed(moved):
            require(not original.exists(), f'Refuse to overwrite a changed optional plugin: {original}')
            shutil.move(archived, original)


def identity(path):
    with zipfile.ZipFile(path) as jar:
        for descriptor in ('plugin.yml', 'paper-plugin.yml'):
            if descriptor in jar.namelist():
                match = re.search(r'^name:\s*[\'\"]?([^\'\"\r\n]+)',
                                  jar.read(descriptor).decode(), re.MULTILINE)
                if match:
                    return match.group(1).strip()
    return None


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def free_ports(ports):
    held = []
    try:
        for port in ports:
            for kind in (socket.SOCK_STREAM, socket.SOCK_DGRAM):
                sock = socket.socket(socket.AF_INET, kind)
                held.append(sock)
                sock.bind(('0.0.0.0', port))
    finally:
        for sock in held:
            sock.close()


def stopped():
    result = subprocess.run(['lsof', '-Fpc', '+D', str(INSTANCE)],
                            capture_output=True, text=True)
    commands = [line[1:] for line in result.stdout.splitlines() if line.startswith('c')]
    # Spotlight can read .DS_Store just after a clean server exit.
    require(result.returncode in (0, 1) and not result.stderr.strip()
            and all(command.startswith(('mdworker', 'mds')) for command in commands),
            'Local instance has open files or its stopped state cannot be verified')


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--without-integrations', action='store_true',
                        help='Temporarily archive optional plugin JARs and test the standalone fallback')
    args = parser.parse_args()
    require(INSTANCE.is_dir() and INSTANCE.resolve() == INSTANCE,
            'Prepare the separate project-local Paper-26.3 directory first')
    props = {n.tag.rsplit('}', 1)[-1]: n.text for n in
             ET.parse(ROOT / 'pom.xml').getroot().find('{*}properties')}
    validate_target(props)
    jar = args.jar.resolve()
    artifact_sha = sha256(jar)
    with zipfile.ZipFile(jar) as zipped:
        release = properties(zipped.read('lootchest-build.properties').decode())
    require(identity(jar) == 'LootChest', 'Candidate must be the LootChest plugin')
    expected = {'plugin.version': 'revision', 'build.number': 'buildNumber',
                'paper.target': 'paperVersion', 'paper.build': 'paperBuild',
                'paper.channel': 'paperChannel', 'paper.api': 'paperApiVersion',
                'java.target': 'maven.compiler.release', 'cmi.tested': 'cmiTestedVersion',
                'cmilib.tested': 'cmiLibTestedVersion'}
    for embedded, prop in expected.items():
        require(release[embedded] == props[prop], f'Metadata mismatch: {embedded}')
    require(jar.name == release['artifact.name'], 'Artifact filename mismatch')
    require(release['source.dirty'] == 'false' or os.environ.get('LOOTCHEST_ALLOW_DIRTY') == '1',
            'Build from a clean commit; LOOTCHEST_ALLOW_DIRTY=1 is for development only')
    server_jar = INSTANCE / f"Paper-26.3-{props['paperBuild']}.jar"
    require(sha256(server_jar) == props['paperSha256'], 'Paper server checksum mismatch')
    require(sorted(INSTANCE.glob('Paper-*.jar')) == [server_jar],
            'Only the pinned Paper server JAR may be active')
    config = properties((INSTANCE / 'server.properties').read_text())
    require(config['server-ip'] == '127.0.0.1', 'Smoke server must bind loopback')
    require(config['enable-rcon'] == 'false' and config['enable-query'] == 'false',
            'RCON and query must remain disabled during local smoke')
    ports = [int(config[k]) for k in ('server-port', 'query.port', 'rcon.port')]
    require(len(set(ports)) == 3, 'Use distinct configured ports')
    launcher_config = json.loads((INSTANCE / 'paperscript/config.json').read_text())
    require(launcher_config['default_channel'] == props['paperChannel'] and
            launcher_config['check_latest_channel_only'] == props['paperChannel'],
            'PaperScript channel does not match the selected release')
    require(launcher_config['allow_same_version_build_upgrade'] is False,
            'Automatic Paper build upgrades must remain disabled')
    require(launcher_config['tmux_session'] == f'lootchest-paper-26.3-{ports[0]}',
            'PaperScript must use the distinct LootChest session name')
    plugins = INSTANCE / 'plugins'
    identities = {path: identity(path) for path in plugins.glob('*.jar')}
    require('DiscordSRV' not in identities.values(), 'DiscordSRV must remain inactive')
    require(all(name not in ('LootChest', 'DiscordSRV') for path in (plugins / 'update').glob('*.jar')
                for name in [identity(path)]), 'Remove pending LootChest or DiscordSRV update JARs')
    runtime = subprocess.run([str(JDK / 'bin/java'), '-XshowSettings:properties', '-version'],
                             capture_output=True, text=True, check=True).stderr
    validate_runtime(runtime)
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    logdir = ROOT / 'target/smoke-paper-26.3' / stamp
    logdir.mkdir(parents=True)
    (logdir / 'java-version.txt').write_text(runtime)
    (logdir / 'lootchest-build.properties').write_text('\n'.join(f'{k}={v}' for k, v in release.items()) + '\n')
    # Cooperating smoke runs hold this lock throughout installation and shutdown.
    with (ROOT / 'servers/.lootchest-paper-26.3-smoke.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        stopped()
        free_ports(ports)
        backup = INSTANCE / 'archive' / f'before-smoke-{stamp}'
        backup.mkdir()
        for path, name in identities.items():
            if name == 'LootChest':
                require(not path.is_symlink(), 'Refuse a symlinked LootChest JAR')
                shutil.move(str(path), backup / path.name)
        temporary = plugins / (jar.name + '.pending')
        shutil.copy2(jar, temporary)
        require(sha256(temporary) == artifact_sha, 'Installed artifact checksum mismatch')
        temporary.replace(plugins / jar.name)
        env = dict(os.environ, JAVA_HOME=str(JDK), PATH=str(JDK / 'bin') + ':' + os.environ['PATH'])
        env['JDK_JAVA_OPTIONS'] = env.get('JDK_JAVA_OPTIONS', '') + ' -Dlootchest.smoke=true'
        saved_after_first_stop = None
        # A second cold boot verifies actual disk persistence, not just reload.
        with optional_integrations(identities, backup, args.without_integrations), installed_probe(plugins):
            for boot in (1, 2):
                raw = logdir / f'boot-{boot}.raw.log'
                with raw.open('w') as output:
                    run_boot(output, raw, boot, env, props, release, plugins, args.without_integrations)
                saved = (plugins / 'LootChest/data.yml').read_text()
                (logdir / f'data-after-boot-{boot}.yml').write_text(saved)
                if boot == 1:
                    saved_after_first_stop = persisted_definitions(saved)
                else:
                    require(persisted_definitions(saved) == saved_after_first_stop,
                            'Saved definitions/items changed across restart (excluding clocks/component key order)')
                free_ports(ports)
                stopped()
        require(sha256(jar) == artifact_sha and sha256(plugins / jar.name) == artifact_sha,
                'Candidate or installed JAR changed during the smoke run')
        (logdir / 'result.json').write_text(json.dumps({
            'result': 'PASS', 'artifact': jar.name, 'sha256': artifact_sha,
            'source': release['source.commit'], 'dirty': release['source.dirty'],
            'paper': f"26.3-{props['paperBuild']}-{props['paperChannel']}",
            'api': props['paperApiVersion'], 'java': runtime.strip(),
            'instance': str(INSTANCE), 'ports': ports, 'shared_server_started': False,
            'cold_boots': 2, 'persistence': 'unchanged except respawn clocks and component key order',
            'malformed_reload': 'rejected; runtime retained; valid retry passed',
            'item_metadata': 'PDC, data components and nested shulker contents preserved',
            'copper_effects': 'all eight oxidation/wax states reactivate; unrelated barrel rejected',
            'async_api': 'off-thread mutation refused before world access',
            'holograms': ('optional integration absent' if args.without_integrations else
                          'CMI registry, transient settings, text updates, timers and despawn cleanup'),
            'profile': 'standalone' if args.without_integrations else 'integrated',
        }, indent=2) + '\n')
        print(f'[smoke] PASS; restart, persistence, clean stops and ports released. Evidence: {logdir}', flush=True)


def run_boot(output, raw, boot, env, props, release, plugins, without_integrations):
    process = None
    try:
        process = subprocess.Popen([str(INSTANCE / '1MB-minecraft.sh')], cwd=INSTANCE,
                                   env=env, stdin=subprocess.PIPE, stdout=output,
                                   stderr=subprocess.STDOUT, text=True, start_new_session=True)

        def log():
            return ANSI.sub('', raw.read_text(errors='replace'))

        def wait_for(text, offset=0, timeout=180):
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                require('PROBE FAIL' not in log(), f'Runtime probe failed; see {raw}')
                if text in log()[offset:]:
                    print(f'[smoke] OK: {text}', flush=True)
                    return
                require(process.poll() is None, f'Server exited waiting for {text}; see {raw}')
                time.sleep(0.5)
            raise RuntimeError(f'Timed out waiting for {text}; see {raw}')

        def command(text, response):
            offset = len(log())
            process.stdin.write(text + '\n')
            process.stdin.flush()
            wait_for(response, offset, 60)

        # Paper prints the major version before the VM's exact runtime build.
        wait_for('Running Java 27 (Java HotSpot(TM) 64-Bit Server VM 27.0.1+')
        wait_for(f"This server is running Paper version 26.3-{props['paperBuild']}-")
        wait_for(f"Implementing API version {props['paperApiVersion']}")
        wait_for('[LootChest] Plugin loaded')
        wait_for('Done (')
        command('lc info', f"Paper release 26.3 build {props['paperBuild']} {props['paperChannel']}")
        wait_for(f"Source {release['source.commit']}" + ('-dirty' if release['source.dirty'] == 'true' else ''))
        if without_integrations:
            wait_for('disabled (optional CMI/CMILib unavailable)')
            wait_for('LootChest will continue without holograms.')
        else:
            wait_for(f"Using CMI holograms: CMI {props['cmiTestedVersion']} / CMILib {props['cmiLibTestedVersion']}")
        command('lc help', '/lc help -')
        command('lc list', 'LootChests:')
        # The cloned fixture area spans ten chunks. With no players
        # online, keep these loaded so the audit actually checks blocks.
        command('execute in minecraft:overworld run forceload add -64 -64 15 -33',
                'force load')
        if boot == 1:
            command('lootchestprobe prepare', 'PROBE PASS prepare')
        else:
            command('lootchestprobe verify', 'PROBE PASS metadata, nested items and inventory isolation')
        command('lootchestprobe async', 'PROBE PASS async API refused before world access')
        command('lootchestprobe copper',
                'PROBE PASS all eight copper states reactivate effects; barrel rejected')
        if not without_integrations:
            command('lootchestprobe holograms', 'PROBE PASS CMI registry, transience and text updates')
        command('lc reload', 'Configuration, locale, chest data, and LootChests were reloaded.')
        if not without_integrations:
            command('lootchestprobe holograms', 'PROBE PASS CMI registry, transience and text updates')
        if boot == 1:
            # All writes target the disposable fixture. Restore the exact
            # language bytes even when the rejection check fails.
            language_path = plugins / 'LootChest/lang.yml'
            original_language = language_path.read_bytes()
            invalid_language = b'help: [unterminated\n'
            failure_offset = len(log())
            try:
                language_path.write_bytes(invalid_language)
                command('lc reload', 'Reload was aborted before candidate publication.')
                expected_failure_log = log()[failure_offset:]
                require('LootChest reload decoding failed; the running state was retained.'
                        in expected_failure_log
                        and 'InvalidConfigurationException' in expected_failure_log,
                        'Expected malformed YAML decoding failure')
                require(language_path.read_bytes() == invalid_language,
                        'Rejected reload overwrote the invalid candidate')
            finally:
                language_path.write_bytes(original_language)
            command('lc audit', 'No lifecycle inconsistencies were found.')
            command('lc reload', 'Configuration, locale, chest data, and LootChests were reloaded.')
        command('lc despawnall', 'All LootChests were despawned.')
        if not without_integrations:
            command('lootchestprobe despawned', 'PROBE PASS CMI registry empty after despawn')
        command('lc respawnall', 'All LootChests were respawned.')
        command('lootchestprobe verify', 'PROBE PASS metadata, nested items and inventory isolation')
        if not without_integrations:
            command('lootchestprobe holograms', 'PROBE PASS CMI registry, transience and text updates')
            command('lootchestprobe timer', 'PROBE PASS CMI timer updates and cleanup')
        audit_offset = len(log())
        command('lc audit', 'No lifecycle inconsistencies were found.')
        require('unavailable 0 | issues 0' in log()[audit_offset:],
                'Audit must inspect loaded fixture chunks, with zero issues')
        command('save-all flush', 'Saved the game')
        command('stop', '[LootChest] Disabling LootChest')
        require(process.wait(timeout=60) == 0, 'Server exited with a failure status')
        text = log()
        require('All RegionFile I/O tasks to complete' in text,
                'Paper 26.3 region save completion missing')
        errors = r'PROBE FAIL|NoClassDefFoundError|NoSuchMethodError|ClassNotFoundException|UnsupportedClassVersionError|Error occurred while (?:enabling|disabling) LootChest|Exception.*fr\.black_eyes|fr\.black_eyes.*Exception|LootChest holograms disabled|CommandException'
        require(not re.search(errors, text), f'Compatibility failure; inspect {raw}')
        activated = re.findall(r'Activated (\d+) of (\d+) saved LootChests', text)
        require(activated and all(int(a) > 0 and a == b for a, b in activated),
                'All existing saved chests must activate')
    finally:
        if process is not None and process.poll() is None:
            process.stdin.write('stop\n')
            process.stdin.flush()
            try:
                process.wait(timeout=60)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=15)
        raw.with_name(f'boot-{boot}.log').write_text(ANSI.sub('', raw.read_text(errors='replace')))


if __name__ == '__main__':
    try:
        run()
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f'[smoke] FAIL: {error}', file=sys.stderr)
        sys.exit(1)
