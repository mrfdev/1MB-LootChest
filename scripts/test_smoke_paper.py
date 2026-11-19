"""Offline regression checks for the stable server smoke runner's fail-closed gates."""
import importlib.util
from pathlib import Path
import re
import sys
import unittest
import tempfile
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))

SPEC = importlib.util.spec_from_file_location(
    'paper_smoke', Path(__file__).with_name('smoke-paper-26.3.py'))
SMOKE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SMOKE)


class StableSmokeTest(unittest.TestCase):
    def setUp(self):
        self.props = {n.tag.rsplit('}', 1)[-1]: n.text for n in
                      ET.parse(SMOKE.ROOT / 'pom.xml').getroot().find('{*}properties')}

    def test_current_pinned_target_is_accepted(self):
        SMOKE.validate_target(self.props)

    def test_runtime_accepts_new_oracle_27_patches(self):
        for version in ('27', '27.0.1', '27.0.1.0.3', '27.0.2'):
            with self.subTest(version=version):
                SMOKE.validate_runtime(
                    f'    java.version = {version}\n    java.specification.version = 27\n'
                    '    java.vendor = Oracle Corporation\n')

    def test_runtime_rejects_wrong_major_vendor_or_unverified_output(self):
        runtime = ('    java.version = 27.0.2\n    java.specification.version = 27\n'
                   '    java.vendor = Oracle Corporation\n')
        for invalid in (runtime.replace('specification.version = 27', 'specification.version = 25'),
                        runtime.replace('Oracle Corporation', 'Eclipse Adoptium'),
                        runtime.replace('    java.specification.version = 27\n', ''),
                        'java version "27.0.1"'):
            with self.subTest(runtime=invalid), self.assertRaises(RuntimeError):
                SMOKE.validate_runtime(invalid)

    def test_probe_packages_guarded_helpers_and_removes_them_after_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            compiled = root / 'lootchest/target/test-classes/fr/black_eyes/lootchest/compat'
            compiled.mkdir(parents=True)
            names = ('PaperRuntimeProbe.class', 'PaperRuntimeProbe$CmiChecks.class', 'AutoSellRuntimeChecks.class')
            for name in names:
                (compiled / name).write_bytes(b'test-class')
            (compiled / 'UnrelatedTest.class').write_bytes(b'exclude')
            plugins = root / 'plugins'
            plugins.mkdir()
            with patch.object(SMOKE, 'ROOT', root):
                with self.assertRaisesRegex(RuntimeError, 'simulated failure'):
                    with SMOKE.installed_probe(plugins):
                        with zipfile.ZipFile(plugins / 'LootChestSmokeProbe.jar') as jar:
                            self.assertEqual(
                                {'plugin.yml'} | {'fr/black_eyes/lootchest/compat/' + n for n in names},
                                set(jar.namelist()))
                            self.assertIn(b'softdepend: [CMI, CMILib, 1MB-CMIAPI-AutoSell]', jar.read('plugin.yml'))
                        raise RuntimeError('simulated failure')
            self.assertFalse((plugins / 'LootChestSmokeProbe.jar').exists())

    def test_unstable_or_mismatched_coordinates_are_rejected(self):
        for key, value in [('paperChannel', 'ALPHA'), ('paperChannel', 'BETA'),
                           ('paperVersion', '26.2'), ('paperBuild', '+'),
                           ('paperBuild', '207'), ('paperBuild', '240'),
                           ('paperApiVersion', '26.3.build.174-beta'),
                           ('paperApiVersion', '26.3.build.999-stable'),
                           ('maven.compiler.release', '27'), ('paperSha256', '')]:
            with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                SMOKE.validate_target(dict(self.props, **{key: value}))

    def test_cmi_fixture_has_distinct_active_positions_and_four_deferred_definitions(self):
        fixture = SMOKE.cmi_fixture()
        names = re.findall(r'^  ([a-z0-9_]+):$', fixture, re.MULTILINE)
        self.assertEqual(30, len(names))
        self.assertEqual(30, len(set(names)))
        positions = re.findall(r'      world: ([^\n]+)\n      x: ([-0-9]+)\n      y: 200\n      z: ([-0-9]+)',
                               fixture)
        active = {position for position in positions if position[0] == 'spawn'}
        self.assertEqual(26, len(active))
        self.assertIn(('spawn', '8', '-40'), active)
        self.assertEqual(4, sum(position[0] == 'unloaded_halloween_fixture' for position in positions))

    def test_cmi_runtime_refuses_a_wrong_plugin_or_pair_version(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / 'named-9.8.11.2.jar'
            with zipfile.ZipFile(jar, 'w') as zipped:
                zipped.writestr('plugin.yml', "name: CMI\r\nversion: '9.8.11.0'\r\n")
            self.assertEqual(SMOKE.sha256(jar), SMOKE.require_plugin_version(jar, 'CMI', '9.8.11.0'))
            with self.assertRaisesRegex(RuntimeError, 'Expected CMI version 9.8.11.2'):
                SMOKE.require_plugin_version(jar, 'CMI', '9.8.11.2')
            with self.assertRaisesRegex(RuntimeError, 'Expected CMILib plugin'):
                SMOKE.require_plugin_version(jar, 'CMILib', '9.8.11.0')

    def test_cmi_profile_validates_archived_artifact_without_matching_the_checkout(self):
        release = {'paper.target': '26.3', 'paper.build': '241', 'paper.channel': 'STABLE',
                   'paper.api': '26.3.build.241-stable', 'java.target': '25',
                   'source.commit': 'fe314dd3b3ea', 'source.dirty': 'false',
                   'build.number': '231', 'plugin.version': '2.5.9.3'}
        SMOKE.validate_cmi_artifact(release)
        for key, value in (('paper.channel', 'BETA'), ('paper.api', '26.3.build.256-stable'),
                           ('java.target', '27'), ('source.commit', 'unknown'), ('build.number', '')):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                SMOKE.validate_cmi_artifact(dict(release, **{key: value}))

    def test_restart_comparison_ignores_only_the_respawn_clock(self):
        fixture = ('chests:\n  sample:\n    lastreset: 1000\n    inventory:\n'
                   "      '0':\n        id: minecraft:diamond\n        count: 2\n"
                   '        components:\n          minecraft:custom_data: opaque-data\n')
        original = SMOKE.persisted_definitions(fixture)
        self.assertEqual(original, SMOKE.persisted_definitions(
            fixture.replace('lastreset: 1000', 'lastreset: 2000')))
        for before, after in [('opaque-data', 'lost-data'), ('count: 2', 'count: 1'),
                              ('sample:', 'renamed:'), ('diamond', 'stone')]:
            with self.subTest(before=before):
                self.assertNotEqual(original, SMOKE.persisted_definitions(fixture.replace(before, after)))

    def test_optional_plugins_are_restored_even_when_server_checks_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            backup = root / 'backup'
            backup.mkdir()
            lootchest, cmi = root / 'lootchest.jar', root / 'cmi.jar'
            lootchest.write_bytes(b'candidate')
            cmi.write_bytes(b'optional-plugin')
            with self.assertRaisesRegex(RuntimeError, 'simulated server failure'):
                with SMOKE.optional_integrations({lootchest: 'LootChest', cmi: 'CMI'}, backup, True):
                    self.assertTrue(lootchest.exists())
                    self.assertFalse(cmi.exists())
                    raise RuntimeError('simulated server failure')
            self.assertEqual(b'optional-plugin', cmi.read_bytes())
            self.assertEqual(b'candidate', lootchest.read_bytes())

    def test_component_order_is_ignored_without_losing_multiline_or_changed_values(self):
        prefix = "chests:\n  sample:\n    inventory:\n      '0':\n        components:\n"
        first = '          minecraft:custom_data: opaque-third-party-data\n'
        second = "          minecraft:container: 'nested\n            content'\n"
        suffix = '        schema_version: 1\n'
        before = SMOKE.persisted_definitions(prefix + first + second + suffix)
        self.assertEqual(before, SMOKE.persisted_definitions(prefix + second + first + suffix))
        self.assertNotEqual(before, SMOKE.persisted_definitions(prefix + first + suffix))
        self.assertNotEqual(before, SMOKE.persisted_definitions(
            (prefix + second + first + suffix).replace('content', 'changed')))


if __name__ == '__main__':
    unittest.main()
