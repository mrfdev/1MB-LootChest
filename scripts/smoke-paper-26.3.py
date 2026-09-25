#!/usr/bin/env python3
"""Smoke the pinned project-local Paper 26.3 instance, never the shared server."""
import datetime
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
JDK = Path('/Library/Java/JavaVirtualMachines/jdk-27.jdk/Contents/Home')
ANSI = re.compile(r'\x1b\[[0-9;?]*[ -/]*[@-~]')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def properties(text):
    return dict(line.split('=', 1) for line in text.splitlines()
                if '=' in line and not line.startswith('#'))


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
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


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
    require(len(sys.argv) == 2, 'Usage: smoke-paper-26.3.py <candidate.jar>')
    require(INSTANCE.is_dir() and INSTANCE.resolve() == INSTANCE,
            'Prepare the separate project-local Paper-26.3 directory first')
    props = {n.tag.rsplit('}', 1)[-1]: n.text for n in
             ET.parse(ROOT / 'pom.xml').getroot().find('{*}properties')}
    require(props['paperVersion'] == '26.3' and props['maven.compiler.release'] == '25',
            'Expected Paper 26.3 and Java 25 compilation')
    jar = Path(sys.argv[1]).resolve()
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
    require(launcher_config['tmux_session'] == f'lootchest-paper-26.3-{ports[0]}',
            'PaperScript must use the distinct LootChest session name')
    plugins = INSTANCE / 'plugins'
    identities = {path: identity(path) for path in plugins.glob('*.jar')}
    require('DiscordSRV' not in identities.values(), 'DiscordSRV must remain inactive')
    require(all(name not in ('LootChest', 'DiscordSRV') for path in (plugins / 'update').glob('*.jar')
                for name in [identity(path)]), 'Remove pending LootChest or DiscordSRV update JARs')
    runtime = subprocess.run([str(JDK / 'bin/java'), '-version'],
                             capture_output=True, text=True, check=True).stderr
    require('java version "27"' in runtime, 'Expected installed Java 27 runtime')
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
        raw = logdir / 'console.raw.log'
        process = None
        with raw.open('w') as output:
            try:
                process = subprocess.Popen([str(INSTANCE / '1MB-minecraft.sh')], cwd=INSTANCE,
                                           env=env, stdin=subprocess.PIPE, stdout=output,
                                           stderr=subprocess.STDOUT, text=True, start_new_session=True)

                def log():
                    return ANSI.sub('', raw.read_text(errors='replace'))

                def wait_for(text, offset=0, timeout=180):
                    deadline = time.monotonic() + timeout
                    while time.monotonic() < deadline:
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

                wait_for('Running Java 27 ')
                wait_for(f"This server is running Paper version 26.3-{props['paperBuild']}-")
                wait_for(f"Implementing API version {props['paperApiVersion']}")
                wait_for('[LootChest] Plugin loaded')
                wait_for('Done (')
                command('lc info', f"Paper release 26.3 build {props['paperBuild']} {props['paperChannel']}")
                wait_for(f"Source {release['source.commit']}" + ('-dirty' if release['source.dirty'] == 'true' else ''))
                wait_for(f"Using CMI holograms: CMI {props['cmiTestedVersion']} / CMILib {props['cmiLibTestedVersion']}")
                command('lc help', '/lc help -')
                command('lc list', 'LootChests:')
                # The cloned fixture area spans ten chunks. With no players
                # online, keep these loaded so the audit actually checks blocks.
                command('execute in minecraft:overworld run forceload add -64 -64 15 -33',
                        'force load')
                command('lc reload', 'Configuration, locale, chest data, and LootChests were reloaded.')
                command('lc despawnall', 'All LootChests were despawned.')
                command('lc respawnall', 'All LootChests were respawned.')
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
                errors = r'NoClassDefFoundError|NoSuchMethodError|ClassNotFoundException|UnsupportedClassVersionError|Error occurred while (?:enabling|disabling) LootChest|Exception.*fr\.black_eyes|fr\.black_eyes.*Exception|LootChest holograms disabled|CommandException'
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
                (logdir / 'console.log').write_text(ANSI.sub('', raw.read_text(errors='replace')))
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
        }, indent=2) + '\n')
        print(f'[smoke] PASS; clean stop and ports released. Evidence: {logdir}', flush=True)


if __name__ == '__main__':
    try:
        run()
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f'[smoke] FAIL: {error}', file=sys.stderr)
        sys.exit(1)
