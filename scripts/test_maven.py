"""Regression checks for JVM overrides and isolated Maven toolchains."""
import contextlib
import io
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parent))
import maven as MAVEN


class MavenLauncherTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        environment = patch.dict(os.environ, {}, clear=True)
        environment.start()
        self.addCleanup(environment.stop)
        project = patch.object(MAVEN, 'ROOT', self.root)
        project.start()
        self.addCleanup(project.stop)

    def test_managed_properties_cannot_override_compilers_or_test_forks(self):
        overrides = ('maven.compiler.release=27', 'maven.compiler.testRelease=27',
                     'maven.compiler.executable=/tmp/javac', 'maven.compiler.fork=false',
                     'maven.toolchains.jdk.version=27', 'java.home=/tmp/jdk',
                     'env.ONE_MB_BUILD_JAVA_HOME=/tmp/jdk', 'jdk.version=21',
                     'jvm=/tmp/java', 'forkCount=0', 'forkMode=never',
                     'surefire.jvm=/tmp/java', 'failsafe.jvm=/tmp/java',
                     'argLine=-Djava.vendor=fake', 'maven.ext.class.path=/tmp/extension.jar',
                     'skipTests=true', 'skipITs=true', 'maven.test.skip=true',
                     'enforcer.skip=true', 'release=27')
        for prop in overrides:
            for arguments in (['-D' + prop], ['-D', prop], ['--define=' + prop], ['--define', prop]):
                with self.subTest(arguments=arguments), self.assertRaisesRegex(RuntimeError, 'managed by ./mvnw'):
                    MAVEN.validate_arguments(arguments)

    def test_alternate_settings_toolchains_and_build_roots_are_rejected(self):
        for option in ('-t', '--toolchains', '-gt', '--global-toolchains', '-s', '--settings',
                       '-gs', '--global-settings', '-f', '--file', '-P', '--activate-profiles',
                       '--fail-never'):
            forms = ([option, '/tmp/other'], [option + '=/tmp/other'])
            if not option.startswith('--'):
                forms += ([option + '/tmp/other'],)
            for arguments in forms:
                with self.subTest(arguments=arguments), self.assertRaises(RuntimeError):
                    MAVEN.validate_arguments(arguments)

    def test_normal_goals_and_test_filters_are_supported(self):
        MAVEN.validate_arguments(['-B', '-o', '-fae', '-ff', 'clean', 'verify', '-Dtest=ExampleTest',
                                  '-Dit.test=ExampleIT', '--define', 'style.color=never'])
        for argument in ('-D', '--define'):
            with self.subTest(argument=argument), self.assertRaisesRegex(RuntimeError, 'Missing value'):
                MAVEN.validate_arguments([argument])

    def test_environment_injection_is_rejected(self):
        for name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', 'JDK_JAVAC_OPTIONS', '_JAVA_OPTIONS',
                     'MAVEN_OPTS', 'MAVEN_ARGS', 'MAVEN_DEBUG_OPTS', 'MAVEN_BASEDIR', 'MAVEN_CONFIG'):
            with self.subTest(name=name), patch.dict(os.environ, {name: '-Djava.home=/tmp/other'}), \
                    self.assertRaisesRegex(RuntimeError, name):
                MAVEN.validate_environment()

    def test_stale_project_configs_are_rejected(self):
        directory = self.root / '.mvn'
        directory.mkdir()
        for name in ('maven.config', 'jvm.config', 'extensions.xml'):
            config = directory / name
            config.write_text('stale override\n')
            with self.subTest(name=name), self.assertRaisesRegex(RuntimeError, name):
                MAVEN.validate_environment()
            config.unlink()
        MAVEN.validate_environment()

    def test_maven_invocation_uses_only_validated_homes_and_isolated_config(self):
        build = self.root / 'Oracle 25 & updated patch'
        test = self.root / 'Oracle 27 & updated patch'
        captured = {}

        def invoke(command, *, cwd, env):
            self.assertEqual(self.root, cwd)
            self.assertEqual('/installed/mvn', command[0])
            self.assertEqual(['clean', 'verify'], command[-2:])
            self.assertEqual(str(build), env['JAVA_HOME'])
            self.assertTrue(env['PATH'].startswith(str(build / 'bin') + os.pathsep))
            self.assertEqual(str(build), env['ONE_MB_BUILD_JAVA_HOME'])
            self.assertEqual(str(test), env['ONE_MB_TEST_JAVA_HOME'])
            self.assertEqual('1', env['MAVEN_SKIP_RC'])
            self.assertEqual(str(self.root), env['MAVEN_BASEDIR'])
            chains = Path(command[command.index('--toolchains') + 1])
            actual = ET.parse(chains).getroot().findall('toolchain')
            self.assertEqual([('25', str(build)), ('27', str(test))],
                             [(node.findtext('provides/version'), node.findtext('configuration/jdkHome'))
                              for node in actual])
            self.assertTrue(all(node.findtext('provides/vendor') == 'Oracle Corporation' for node in actual))
            self.assertEqual([], list(ET.parse(command[command.index('--global-toolchains') + 1]).getroot()))
            for option in ('--settings', '--global-settings'):
                self.assertEqual([], list(ET.parse(command[command.index(option) + 1]).getroot()))
            captured['chains'] = chains
            return 17

        with patch.dict(os.environ, {'JAVA_HOME': '/stale/default', 'PATH': '/usr/bin'}), \
                patch.object(MAVEN, 'resolve_jdk', side_effect=[build, test]) as resolve, \
                patch.object(MAVEN, 'java_properties', return_value={
                    'java.version': 'validated', 'java.vendor': 'Oracle Corporation'}), \
                patch.object(MAVEN.shutil, 'which', return_value='/installed/mvn'), \
                patch.object(MAVEN.subprocess, 'call', side_effect=invoke), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(17, MAVEN.main(['clean', 'verify']))
            self.assertEqual([(25, 'ONE_MB_BUILD_JAVA_HOME'), (27, 'ONE_MB_TEST_JAVA_HOME')],
                             [call.args for call in resolve.call_args_list])
        self.assertFalse(captured['chains'].exists(), 'Temporary machine paths must not persist')

    def test_invalid_options_stop_before_jdk_discovery_or_maven(self):
        with patch.object(MAVEN, 'resolve_jdk') as resolve, patch.object(MAVEN.subprocess, 'call') as invoke:
            with self.assertRaises(RuntimeError):
                MAVEN.main(['clean', 'verify', '-DforkCount=0'])
            resolve.assert_not_called()
            invoke.assert_not_called()


if __name__ == '__main__':
    unittest.main()
