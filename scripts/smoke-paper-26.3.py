#!/usr/bin/env python3
"""Smoke local Paper 26.3 with the maintained or a fresh CMI test fixture."""
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

from oracle_jdks import JAVA_OPTION_VARIABLES, java_properties, resolve_jdk

ROOT = Path(__file__).resolve().parent.parent
INSTANCE = ROOT / 'servers/Paper-26.3'
ANSI = re.compile(r'\x1b\[[0-9;?]*[ -/]*[@-~]')
CMI_ACTIVE = 26
CMI_DEFERRED = 4
CMI_UNTESTED = 'CMI holograms are running with an unvalidated version pair.'


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
    require(props['paperBuild'].isdigit() and int(props['paperBuild']) >= 241,
            'Paper 26.3 STABLE build 241 or newer is required')
    require(props['paperApiVersion'] == f"26.3.build.{props['paperBuild']}-stable",
            'Paper API must match the pinned stable server build')
    require(re.fullmatch(r'[0-9a-f]{64}', props['paperSha256']),
            'Paper server must have a SHA-256 pin')


def validate_runtime(runtime):
    require(re.search(r'^\s*java\.specification\.version = 27\s*$', runtime, re.MULTILINE)
            and re.search(r'^\s*java\.vendor = Oracle Corporation\s*$', runtime, re.MULTILINE),
            'Expected an installed Oracle Java 27 runtime')


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
    require(probe_class.is_file(), 'Run ./mvnw clean verify to compile the runtime probe')
    path = plugins / 'LootChestSmokeProbe.jar'
    require(not path.exists() and not path.is_symlink(), 'Remove the stale smoke probe first')
    try:
        with zipfile.ZipFile(path, 'x', zipfile.ZIP_DEFLATED) as zipped:
            for compiled in sorted(list(probe_class.parent.glob('PaperRuntimeProbe*.class'))
                                   + list(probe_class.parent.glob('AutoSellRuntimeChecks*.class'))):
                zipped.write(compiled, compiled.relative_to(classes))
            zipped.writestr('plugin.yml', '''name: LootChestSmokeProbe
version: '1'
main: fr.black_eyes.lootchest.compat.PaperRuntimeProbe
api-version: '26.3'
depend: [LootChest]
softdepend: [CMI, CMILib, 1MB-CMIAPI-AutoSell]
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


def free_ports(ports, *, allow_time_wait=False):
    held = []
    try:
        for port in ports:
            for kind in (socket.SOCK_STREAM, socket.SOCK_DGRAM):
                sock = socket.socket(socket.AF_INET, kind)
                held.append(sock)
                if allow_time_wait and kind == socket.SOCK_STREAM:
                    # A disconnected witness can leave a closed TCP connection
                    # in TIME_WAIT. A new listener must still bind successfully.
                    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                sock.bind(('0.0.0.0', port))
                if allow_time_wait and kind == socket.SOCK_STREAM:
                    sock.listen()
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


def cmi_fixture():
    lines = ['chests:']
    for number in range(CMI_ACTIVE + CMI_DEFERRED):
        name = 'stable_smoke_metadata' if number == 0 else f'cmi_smoke_{number:02}'
        deferred = number >= CMI_ACTIVE
        world = 'unloaded_halloween_fixture' if deferred else 'spawn'
        label = 'Smoke Chest Alpha' if number == 0 else f'Smoke Chest {number:02}'
        x, z = (8, -40) if number == 0 else (24 + 4 * ((number - 1) % 5),
                                               -48 + 4 * ((number - 1) // 5))
        # Write all persisted fields before inventory. LootChest replaces the
        # inventory section when saving, so it remains last after both boots.
        lines.extend((f'  {name}:', '    type: CHEST', '    direction: SOUTH',
                      f'    holo: {label}', '    time: 60', '    randomradius: 0',
                      '    position:', f'      world: {world}', f'      x: {x}',
                      '      y: 200', f'      z: {z}', '      pitch: 0.0', '      yaw: 0.0',
                      '    chance:', "      '0': 100", '    fall: false', '    respawn_cmd: false',
                      '    respawn_natural: false', '    take_message: false', '    protectionTime: 0',
                      '    lastreset: 0', '    particle: Disabled', '    maxFilledSlots: 0',
                      '    inventory:', "      '0':", '        ==: org.bukkit.inventory.ItemStack',
                      '        DataVersion: 5023', '        id: minecraft:cobblestone',
                      '        count: 1', '        schema_version: 1'))
    return '\n'.join(lines) + '\n'


def require_plugin_version(jar, name, version):
    require(identity(jar) == name, f'Expected {name} plugin: {jar}')
    with zipfile.ZipFile(jar) as zipped:
        descriptor = zipped.read('plugin.yml').decode()
    match = re.search(r'^version:\s*[\'\"]?([0-9.]+)[\'\"]?\s*$', descriptor, re.MULTILINE)
    require(match and version == match[1], f'Expected {name} version {version} in {jar}')
    return sha256(jar)


def validate_cmi_artifact(release):
    # The focused profile accepts an archived artifact even when the checkout
    # has since advanced. Validate the artifact's own compile coordinates.
    build = release.get('paper.build', '')
    require(release.get('paper.target') == '26.3' and release.get('paper.channel') == 'STABLE'
            and build.isdigit() and int(build) >= 241
            and release.get('paper.api') == f'26.3.build.{build}-stable'
            and release.get('java.target') == '25',
            'Expected artifact metadata for stable Paper 26.3 and Java 25')
    require(re.fullmatch(r'[0-9a-f]{12,40}', release.get('source.commit', ''))
            and release.get('source.dirty') in ('true', 'false')
            and release.get('build.number', '').isdigit()
            and re.fullmatch(r'[0-9]+(?:\.[0-9]+){2,}', release.get('plugin.version', '')),
            'Expected a version, build number and Git source in artifact metadata')


def run_cmi_smoke(args, jdk, jar, artifact_sha, release, runtime):
    require(args.paper_build.isdigit() and int(args.paper_build) >= 241
            and re.fullmatch(r'[0-9a-f]{7,40}', args.paper_commit)
            and re.fullmatch(r'[0-9a-f]{64}', args.paper_sha256),
            'Supply the exact stable Paper build, Git hash and published SHA-256')
    require(sha256(args.paper_jar) == args.paper_sha256, 'Paper server checksum mismatch')
    cmi_sha = require_plugin_version(args.cmi_jar, 'CMI', args.cmi_version)
    cmilib_sha = require_plugin_version(args.cmilib_jar, 'CMILib', args.cmilib_version)
    env = {name: value for name, value in os.environ.items() if name not in JAVA_OPTION_VARIABLES}
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    logdir = ROOT / 'target/smoke-paper-26.3' / f'cmi-{stamp}'
    fixtures = INSTANCE / 'disposable-cmi'
    require(fixtures.resolve() == fixtures, 'Refuse a symlinked fixture directory')
    instance = fixtures / stamp
    ports = (32951, 32952, 32953)
    with (ROOT / 'servers/.lootchest-paper-26.3-smoke.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        free_ports(ports, allow_time_wait=True)
        logdir.mkdir(parents=True)
        print(f'[smoke] CMI fixture: {instance}\n[smoke] Evidence: {logdir}', flush=True)
        plugins = instance / 'plugins'
        data_dir = plugins / 'LootChest'
        data_dir.mkdir(parents=True)
        paper = instance / f'Paper-26.3-{args.paper_build}.jar'
        for source, copy, digest in ((args.paper_jar, paper, args.paper_sha256),
                                     (args.cmi_jar, plugins / 'CMI.jar', cmi_sha),
                                     (args.cmilib_jar, plugins / 'CMILib.jar', cmilib_sha),
                                     (jar, plugins / jar.name, artifact_sha)):
            shutil.copy2(source, copy)
            require(sha256(copy) == digest, f'Fixture JAR checksum mismatch: {copy}')
        # Only reuse the upstream Minecraft binary. Paperclip verifies its own
        # expected checksum. Worlds and plugin directories are generated fresh.
        vanilla = INSTANCE / 'cache/mojang_26.3.jar'
        if vanilla.is_file():
            (instance / 'cache').mkdir()
            shutil.copy2(vanilla, instance / 'cache' / vanilla.name)
        (instance / 'eula.txt').write_text('eula=true\n')
        (instance / 'server.properties').write_text(
            f'server-ip=127.0.0.1\nserver-port={ports[0]}\nquery.port={ports[1]}\nrcon.port={ports[2]}\n'
            'enable-rcon=false\nenable-query=false\nmanagement-server-enabled=false\n'
            f'online-mode={str(not args.client_witness).lower()}\nenforce-secure-profile=false\nwhite-list=true\n'
            'view-distance=4\nsimulation-distance=4\nlevel-name=spawn\nlevel-seed=1\n'
            'level-type=minecraft:flat\n'
            'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1}]}\n'
            'generate-structures=false\nspawn-protection=0\n'
            'pause-when-empty-seconds=-1\ndifficulty=peaceful\n')
        if args.client_witness:
            (instance / 'whitelist.json').write_text(json.dumps([
                {'name': 'LootChestViewer', 'uuid': 'fdd9ca9a-9b6e-3cdd-bf8b-6e543c330610'},
            ]) + '\n')
        (data_dir / 'config.yml').write_text(
            'UseHologram: true\nHologram_distance_to_chest: 1.2\nParticles:\n  enable: false\n'
            'Timer_on_hologram:\n  Show_Timer_On_Hologram: true\n'
            "  Hours_Separator: 'h '\n  Minutes_Separator: 'm '\n  Seconds_Separator: 's'\n"
            "  Format: '%Hologram %Hours%Hsep%Minutes%Msep%Seconds%Ssep'\n")
        (data_dir / 'data.yml').write_text(cmi_fixture())
        (logdir / 'lootchest-build.properties').write_text(''.join(f'{key}={value}\n'
                                                                for key, value in release.items()))
        (logdir / 'java-version.txt').write_text(''.join(f'{key}={value}\n'
                                                       for key, value in runtime.items()))
        saved = []
        with installed_probe(plugins):
            for boot in (1, 2):
                raw = logdir / f'boot-{boot}.raw.log'
                with raw.open('w') as output:
                    run_boot(output, raw, boot, env, None, release, plugins, False,
                             runtime['java.vm.version'], instance=instance,
                             launch=[str(jdk / 'bin/java'), '-Xms512M', '-Xmx1536M',
                                     '-Dlootchest.smoke=true', '-jar', paper.name, 'nogui'],
                             cmi_profile=args)
                data = (data_dir / 'data.yml').read_text()
                (logdir / f'data-after-boot-{boot}.yml').write_text(data)
                saved.append(persisted_definitions(data))
                free_ports(ports, allow_time_wait=True)
                for file in (plugins / 'CMI').rglob('*'):
                    if file.is_file() and file.name.lower() in ('holograms.yml', 'holograms.yaml'):
                        require('lootchest-' not in file.read_text().lower(), 'CMI persisted a LootChest hologram')
        require(saved[0] == saved[1], 'Saved definitions changed across the cold restart')
        (logdir / 'result.json').write_text(json.dumps({
            'result': 'PASS', 'profile': 'cmi-holograms', 'artifact': jar.name, 'sha256': artifact_sha,
            'source': release['source.commit'], 'dirty': release['source.dirty'],
            'paper': f'26.3-{args.paper_build}-STABLE ({args.paper_commit})',
            'paper_sha256': args.paper_sha256, 'compiled_against': release['paper.api'],
            'cmi': args.cmi_version, 'cmi_sha256': cmi_sha,
            'cmilib': args.cmilib_version, 'cmilib_sha256': cmilib_sha,
            'java_home': str(jdk), 'java_version': runtime['java.version'],
            'java_vendor': runtime['java.vendor'], 'instance': str(instance), 'ports': ports,
            'startup': f'{CMI_ACTIVE} active / {CMI_ACTIVE + CMI_DEFERRED} saved; '
                       f'{CMI_DEFERRED} deferred for an unloaded world',
            'cold_boots': 2,
            'holograms': 'exact registry membership after startup, reload, respawn and cold restart; '
                         'countdown changed in place; zero after despawn and before CMI shutdown',
            'untested_pair_warning': args.expect_untested_warning,
            'client_witness': [path.read_text().strip() for path in sorted(logdir.glob('*.observed.txt'))],
        }, indent=2) + '\n')
        print(f'[smoke] PASS; CMI lifecycle, cold restart, and clean stops. Evidence: {logdir}', flush=True)


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    profile = parser.add_mutually_exclusive_group()
    profile.add_argument('--without-integrations', action='store_true',
                         help='Temporarily archive optional plugin JARs and test the standalone fallback')
    profile.add_argument('--cmi-holograms', action='store_true',
                         help='Verify an exact CMI pair and Paper build with a fresh synthetic fixture')
    for name in ('paper-jar', 'cmi-jar', 'cmilib-jar'):
        parser.add_argument(f'--{name}', type=Path)
    for name in ('paper-build', 'paper-commit', 'paper-sha256', 'cmi-version', 'cmilib-version'):
        parser.add_argument(f'--{name}')
    parser.add_argument('--client-witness', action='store_true',
                        help='Allow the loopback LootChestViewer and pause between CMI stages')
    parser.add_argument('--expect-untested-warning', action='store_true',
                        help='Require the unvalidated-pair warning with this artifact and runtime pair')
    args = parser.parse_args()
    cmi_options = (args.paper_jar, args.paper_build, args.paper_commit, args.paper_sha256,
                   args.cmi_jar, args.cmi_version, args.cmilib_jar, args.cmilib_version)
    if args.cmi_holograms and not all(cmi_options):
        parser.error('the CMI profile needs all Paper and CMI/CMILib coordinates')
    if not args.cmi_holograms and (any(cmi_options) or args.client_witness or args.expect_untested_warning):
        parser.error('the Paper and CMI coordinate overrides require --cmi-holograms')
    jdk = resolve_jdk(27, 'ONE_MB_TEST_JAVA_HOME')
    runtime_properties = java_properties(jdk)
    require(INSTANCE.is_dir() and INSTANCE.resolve() == INSTANCE,
            'Prepare the separate project-local Paper-26.3 directory first')
    jar = args.jar.resolve()
    artifact_sha = sha256(jar)
    with zipfile.ZipFile(jar) as zipped:
        release = properties(zipped.read('lootchest-build.properties').decode())
    require(identity(jar) == 'LootChest', 'Candidate must be the LootChest plugin')
    require(jar.name == release['artifact.name'], 'Artifact filename mismatch')
    require(release['source.dirty'] == 'false' or os.environ.get('LOOTCHEST_ALLOW_DIRTY') == '1',
            'Build from a clean commit; LOOTCHEST_ALLOW_DIRTY=1 is for development only')
    if args.cmi_holograms:
        validate_cmi_artifact(release)
        run_cmi_smoke(args, jdk, jar, artifact_sha, release, runtime_properties)
        return
    props = {n.tag.rsplit('}', 1)[-1]: n.text for n in
             ET.parse(ROOT / 'pom.xml').getroot().find('{*}properties')}
    validate_target(props)
    expected = {'plugin.version': 'revision', 'build.number': 'buildNumber',
                'paper.target': 'paperVersion', 'paper.build': 'paperBuild',
                'paper.channel': 'paperChannel', 'paper.api': 'paperApiVersion',
                'java.target': 'maven.compiler.release', 'cmi.tested': 'cmiTestedVersion',
                'cmilib.tested': 'cmiLibTestedVersion', 'cmi.tested.additional': 'cmiAdditionalTestedPairs'}
    for embedded, prop in expected.items():
        require(release[embedded] == props[prop], f'Metadata mismatch: {embedded}')
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
    if not args.without_integrations:
        require('1MB-CMIAPI-AutoSell' in identities.values() and '1MB-CMIAPI-Lib' in identities.values(),
                'The integrated AutoSell checks require the actual local AutoSell and Core fixture JARs')
    require('DiscordSRV' not in identities.values(), 'DiscordSRV must remain inactive')
    require(all(name not in ('LootChest', 'DiscordSRV') for path in (plugins / 'update').glob('*.jar')
                for name in [identity(path)]), 'Remove pending LootChest or DiscordSRV update JARs')
    runtime = subprocess.run([str(jdk / 'bin/java'), '-XshowSettings:properties', '-version'],
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
        env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / 'bin') + ':' + os.environ['PATH'])
        env['JDK_JAVA_OPTIONS'] = env.get('JDK_JAVA_OPTIONS', '') + ' -Dlootchest.smoke=true'
        saved_after_first_stop = None
        # A second cold boot verifies actual disk persistence, not just reload.
        with optional_integrations(identities, backup, args.without_integrations), installed_probe(plugins):
            for boot in (1, 2):
                raw = logdir / f'boot-{boot}.raw.log'
                with raw.open('w') as output:
                    run_boot(output, raw, boot, env, props, release, plugins, args.without_integrations,
                             runtime_properties['java.vm.version'])
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
            'api': props['paperApiVersion'], 'java': runtime.strip(), 'java_home': str(jdk),
            'instance': str(INSTANCE), 'ports': ports, 'shared_server_started': False,
            'cold_boots': 2, 'persistence': 'unchanged except respawn clocks and component key order',
            'malformed_reload': 'rejected; runtime retained; valid retry passed',
            'item_metadata': 'PDC, data components and nested shulker contents preserved',
            'copper_effects': 'all eight oxidation/wax states reactivate; unrelated barrel rejected',
            'async_api': 'off-thread mutation refused before world access',
            'autosell': ('absent; standalone opening allowed for Java and Bedrock identities'
                         if args.without_integrations else
                         'actual AutoSell: opening, live toggle, clicks/actions, drag, creative, break, drops, '
                         'safe-off wait, full inventory, rename and retry; Java and Bedrock UUIDs'),
            'holograms': ('optional integration absent' if args.without_integrations else
                          'CMI registry, transient settings, text updates, timers and despawn cleanup'),
            'profile': 'standalone' if args.without_integrations else 'integrated',
        }, indent=2) + '\n')
        print(f'[smoke] PASS; restart, persistence, clean stops and ports released. Evidence: {logdir}', flush=True)


def run_cmi_lifecycle(command, log, wait_for, evidence, boot, profile):
    startup = f'Activated {CMI_ACTIVE} of {CMI_ACTIVE + CMI_DEFERRED} saved LootChests'
    deferred = f'{CMI_DEFERRED} because a world is unavailable, and failed 0 unexpectedly.'
    registry = f'PROBE PASS CMI registry, transience and text updates ({CMI_ACTIVE} holograms)'
    wait_for(startup)
    require(any(startup in line and f'rejected 0 malformed, deferred {deferred}' in line
                for line in log().splitlines()), 'Unexpected saved-chest activation result')
    command('execute in minecraft:overworld run forceload add 0 -48 47 -17', 'force load')
    command(f'lootchestprobe holograms {CMI_ACTIVE}', registry)
    command(f'lootchestprobe timer {CMI_ACTIVE}', 'PROBE PASS CMI timer updates and cleanup')

    def witness(stage):
        if not profile.client_witness:
            return
        phase = f'boot-{boot}-{stage}'
        (evidence / 'client-stage.txt').write_text(phase + '\n')
        print(f'[smoke] Client witness stage: {phase}', flush=True)
        observation = evidence / f'{phase}.observed.txt'
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            if observation.is_file() and observation.read_text().strip():
                return
            time.sleep(0.5)
        raise RuntimeError(f'No gameplay observation recorded for {phase}')

    if profile.client_witness:
        if boot == 1:
            command('execute in minecraft:overworld run fill 3 199 -45 13 199 -32 minecraft:smooth_stone',
                    'Successfully filled')
        (evidence / 'client-stage.txt').write_text(f'boot-{boot}-join\n')
        print('[smoke] Waiting for LootChestViewer on loopback port 32951', flush=True)
        wait_for('LootChestViewer joined the game', timeout=360)
        if boot == 1:
            command('minecraft:gamemode creative LootChestViewer', 'Creative Mode')
        command('minecraft:tp LootChestViewer 8.5 200 -36.5 180 -3', 'Teleported LootChestViewer')
    witness('startup' if boot == 1 else 'cold-restart')
    command('lc reload', f'Reload completed with inactive saved definitions: 0 rejected, '
                        f'{CMI_DEFERRED} world-deferred, and 0 failed.')
    command(f'lootchestprobe holograms {CMI_ACTIVE}', registry)
    if boot == 1:
        witness('reload')
    command('lc despawnall', 'All LootChests were despawned.')
    command('lootchestprobe despawned', 'PROBE PASS CMI registry empty after despawn')
    if boot == 1:
        witness('despawned')
    command('lc respawnall', 'All LootChests were respawned.')
    command(f'lootchestprobe holograms {CMI_ACTIVE}', registry)
    if boot == 1:
        witness('respawned')
    audit_offset = len(log())
    command('lc audit', 'Read-only audit complete')
    require(f'present {CMI_ACTIVE} | absent 0 | wrong 0 | unavailable 0 | issues {CMI_DEFERRED}'
            in log()[audit_offset:]
            and f'world-deferred {CMI_DEFERRED} | activation-failed 0' in log()[audit_offset:],
            'Active fixtures must have no lifecycle issues and exactly four world-deferred definitions')
    command('lootchestprobe arm-shutdown', 'PROBE PASS CMI shutdown check armed')


def run_boot(output, raw, boot, env, props, release, plugins, without_integrations, runtime_vm_version,
             *, instance=INSTANCE, launch=None, cmi_profile=None):
    process = None
    try:
        process = subprocess.Popen(launch or [str(instance / '1MB-minecraft.sh')], cwd=instance,
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
        wait_for(f'Running Java 27 (Java HotSpot(TM) 64-Bit Server VM {runtime_vm_version}')
        if cmi_profile:
            build = cmi_profile.paper_build
            wait_for(f'This server is running Paper version 26.3-{build}-')
            require(re.search(rf'^.*This server is running Paper version 26\.3-{build}-[^ ]*'
                              rf'@{cmi_profile.paper_commit}[0-9a-f]* .*'
                              rf'Implementing API version 26\.3\.build\.{build}-stable',
                              log(), re.MULTILINE), 'Paper runtime build, Git hash or stable API mismatch')
            wait_for('[LootChest] Plugin loaded')
            wait_for('Done (')
            command('lc info', f"Build {release['build.number']} | {release['artifact.name']}")
            wait_for(f"Paper release 26.3 build {release['paper.build']} {release['paper.channel']}")
            wait_for(f"Source {release['source.commit']}" + ('-dirty' if release['source.dirty'] == 'true' else ''))
            wait_for(f'Using CMI holograms: CMI {cmi_profile.cmi_version} / CMILib {cmi_profile.cmilib_version}')
            run_cmi_lifecycle(command, log, wait_for, raw.parent, boot, cmi_profile)
            stop_boot(process, command, log, raw, cmi=True)
            require(log().count(CMI_UNTESTED) == int(cmi_profile.expect_untested_warning),
                    'Unexpected number of CMI unvalidated-pair warnings')
            return
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
        command('lootchestprobe autosell', 'PROBE PASS AutoSell collection safety')
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
        stop_boot(process, command, log, raw)
        activated = re.findall(r'Activated (\d+) of (\d+) saved LootChests', log())
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


def stop_boot(process, command, log, raw, cmi=False):
    command('save-all flush', 'Saved the game')
    command('stop', '[LootChest] Disabling LootChest')
    require(process.wait(timeout=60) == 0, 'Server exited with a failure status')
    text = log()
    require('All RegionFile I/O tasks to complete' in text, 'Paper 26.3 region save completion missing')
    errors = r'PROBE FAIL|NoClassDefFoundError|NoSuchMethodError|ClassNotFoundException|UnsupportedClassVersionError|Error occurred while (?:enabling|disabling) LootChest|Exception.*fr\.black_eyes|fr\.black_eyes.*Exception|LootChest holograms disabled|CommandException'
    require(not re.search(errors, text), f'Compatibility failure; inspect {raw}')
    if cmi:
        require('PROBE PASS CMI registry and LootChest tasks empty before CMI disable' in text,
                'Shutdown probe did not verify CMI registry and task cleanup')


if __name__ == '__main__':
    try:
        run()
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f'[smoke] FAIL: {error}', file=sys.stderr)
        sys.exit(1)
