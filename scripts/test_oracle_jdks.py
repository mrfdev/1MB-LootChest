"""Regression checks for installed-only Oracle JDK selection."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import oracle_jdks as JDK


class OracleJdkTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.home = Path(self.directory.name).resolve() / 'Oracle JDK with spaces'
        (self.home / 'bin').mkdir(parents=True)
        for name in ('java', 'javac'):
            executable = self.home / 'bin' / name
            executable.write_text('#!/bin/sh\nexit 0\n')
            executable.chmod(0o755)
        self.environment = patch.dict(os.environ, {}, clear=True)
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def properties(self, major=25, patch_version=None):
        return {'java.specification.version': str(major),
                'java.version': patch_version or f'{major}.0.5',
                'java.vendor': 'Oracle Corporation', 'java.home': str(self.home)}

    def test_macos_discovery_requests_exact_major_with_fail_fast(self):
        for major in (25, 27):
            discovery = subprocess.CompletedProcess([], 0, stdout=str(self.home) + '\n')
            compiler = subprocess.CompletedProcess([], 0, stdout=f'javac {major}.0.5\n')
            with self.subTest(major=major), patch.object(JDK.sys, 'platform', 'darwin'), \
                    patch.object(JDK.subprocess, 'run', side_effect=[discovery, compiler]) as run, \
                    patch.object(JDK, 'java_properties', return_value=self.properties(major)):
                self.assertEqual(self.home, JDK.resolve_jdk(major, 'EXPLICIT_JDK_HOME'))
                self.assertEqual(['/usr/libexec/java_home', '-F', '-v', str(major)],
                                 run.call_args_list[0].args[0])
                self.assertTrue(run.call_args_list[0].kwargs['check'])
                self.assertEqual([str(self.home / 'bin/javac'), '-version'],
                                 run.call_args_list[1].args[0])

    def test_explicit_home_works_without_macos_discovery_and_accepts_future_patches(self):
        for major, version in ((25, '25'), (25, '25.0.9'), (27, '27.0.1.0.3'), (27, '27.0.8')):
            with self.subTest(version=version), patch.dict(os.environ, {'EXPLICIT_JDK_HOME': str(self.home)}), \
                    patch.object(JDK.sys, 'platform', 'linux'), \
                    patch.object(JDK, 'java_properties', return_value=self.properties(major, version)), \
                    patch.object(JDK.subprocess, 'run', return_value=subprocess.CompletedProcess(
                        [], 0, stdout=f'javac {version}\n')) as run:
                self.assertEqual(self.home, JDK.resolve_jdk(major, 'EXPLICIT_JDK_HOME'))
                run.assert_called_once()
                self.assertEqual(str(self.home / 'bin/javac'), run.call_args.args[0][0])

    def test_missing_installation_fails_without_fallback(self):
        with patch.object(JDK.sys, 'platform', 'darwin'), \
                patch.object(JDK.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'java_home')) as run:
            with self.assertRaisesRegex(RuntimeError, 'Automatic JDK downloads are disabled'):
                JDK.resolve_jdk(25, 'EXPLICIT_JDK_HOME')
            run.assert_called_once()

    def test_wrong_major_vendor_or_reported_home_are_rejected(self):
        for changes in ({'java.specification.version': '27'}, {'java.vendor': 'Eclipse Adoptium'},
                        {'java.home': str(self.home.parent)}, {'java.vendor': ''}):
            with self.subTest(changes=changes), patch.dict(os.environ, {'EXPLICIT_JDK_HOME': str(self.home)}), \
                    patch.object(JDK, 'java_properties', return_value=dict(self.properties(), **changes)), \
                    patch.object(JDK.subprocess, 'run') as run:
                with self.assertRaisesRegex(RuntimeError, 'expected Oracle JDK 25'):
                    JDK.resolve_jdk(25, 'EXPLICIT_JDK_HOME')
                run.assert_not_called()

    def test_invalid_explicit_home_does_not_fall_back_to_discovery(self):
        for selected in ('', 'relative/path', str(self.home / 'missing')):
            with self.subTest(selected=selected), patch.dict(os.environ, {'EXPLICIT_JDK_HOME': selected}), \
                    patch.object(JDK.subprocess, 'run') as run:
                with self.assertRaises(RuntimeError):
                    JDK.resolve_jdk(25, 'EXPLICIT_JDK_HOME')
                run.assert_not_called()

    def test_jre_without_compiler_is_rejected(self):
        (self.home / 'bin/javac').unlink()
        with patch.dict(os.environ, {'EXPLICIT_JDK_HOME': str(self.home)}):
            with self.assertRaisesRegex(RuntimeError, 'not an installed JDK'):
                JDK.resolve_jdk(25, 'EXPLICIT_JDK_HOME')

    def test_mismatched_compiler_is_rejected(self):
        with patch.dict(os.environ, {'EXPLICIT_JDK_HOME': str(self.home)}), \
                patch.object(JDK, 'java_properties', return_value=self.properties()), \
                patch.object(JDK.subprocess, 'run', return_value=subprocess.CompletedProcess(
                    [], 0, stdout='javac 27.0.1\n')):
            with self.assertRaisesRegex(RuntimeError, 'Expected javac 25'):
                JDK.resolve_jdk(25, 'EXPLICIT_JDK_HOME')

    def test_java_probe_removes_injected_properties(self):
        output = ('    java.specification.version = 25\n    java.version = 25.0.9\n'
                  '    java.vendor = Oracle Corporation\n'
                  f'    java.home = {self.home}\n')
        injected = {name: '-Djava.vendor=Oracle Corporation' for name in JDK.JAVA_OPTION_VARIABLES}
        with patch.dict(os.environ, injected), patch.object(JDK.subprocess, 'run', return_value=
                subprocess.CompletedProcess([], 0, stderr=output)) as run:
            self.assertEqual(self.properties(patch_version='25.0.9'), JDK.java_properties(self.home))
            for name in injected:
                self.assertNotIn(name, run.call_args.kwargs['env'])
            self.assertEqual([str(self.home / 'bin/java'), '-XshowSettings:properties', '-version'],
                             run.call_args.args[0])

    def test_broken_java_reports_selected_home(self):
        with patch.object(JDK.subprocess, 'run', side_effect=OSError('cannot execute')):
            with self.assertRaisesRegex(RuntimeError, 'Cannot execute installed Java'):
                JDK.java_properties(self.home)


if __name__ == '__main__':
    unittest.main()
