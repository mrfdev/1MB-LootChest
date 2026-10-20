"""Offline regression checks for the stable server smoke runner's fail-closed gates."""
import importlib.util
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

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

    def test_runtime_requires_the_requested_oracle_patch(self):
        runtime = '    java.version = 27.0.1\n    java.vendor = Oracle Corporation\n'
        SMOKE.validate_runtime(runtime)
        for invalid in (runtime.replace('27.0.1', '27'), runtime.replace('27.0.1', '27.0.2'),
                        runtime.replace('Oracle Corporation', 'Eclipse Adoptium'),
                        'java version "27.0.1"'):
            with self.subTest(runtime=invalid), self.assertRaises(RuntimeError):
                SMOKE.validate_runtime(invalid)

    def test_probe_packages_guarded_helpers_and_removes_them_after_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            compiled = root / 'lootchest/target/test-classes/fr/black_eyes/lootchest/compat'
            compiled.mkdir(parents=True)
            names = ('PaperRuntimeProbe.class', 'PaperRuntimeProbe$CmiChecks.class')
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
                            self.assertIn(b'softdepend: [CMI, CMILib]', jar.read('plugin.yml'))
                        raise RuntimeError('simulated failure')
            self.assertFalse((plugins / 'LootChestSmokeProbe.jar').exists())

    def test_unstable_or_mismatched_coordinates_are_rejected(self):
        for key, value in [('paperChannel', 'ALPHA'), ('paperChannel', 'BETA'),
                           ('paperVersion', '26.2'), ('paperBuild', '+'),
                           ('paperApiVersion', '26.3.build.174-beta'),
                           ('paperApiVersion', '26.3.build.999-stable'),
                           ('maven.compiler.release', '27'), ('paperSha256', '')]:
            with self.subTest(key=key, value=value), self.assertRaises(RuntimeError):
                SMOKE.validate_target(dict(self.props, **{key: value}))

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
