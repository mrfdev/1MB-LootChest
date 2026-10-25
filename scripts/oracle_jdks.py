"""Resolve installed Oracle JDKs without a patch pin or download fallback."""
import os
from pathlib import Path
import re
import subprocess
import sys


JAVA_OPTION_VARIABLES = ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVAC_OPTIONS')


def java_properties(home):
    # Inspect the executable itself, without injected -Djava.* properties.
    env = dict(os.environ)
    for name in JAVA_OPTION_VARIABLES:
        env.pop(name, None)
    try:
        result = subprocess.run(
            [str(home / 'bin/java'), '-XshowSettings:properties', '-version'],
            capture_output=True, text=True, check=True, env=env)
    except (OSError, subprocess.CalledProcessError) as error:
        raise RuntimeError(f'Cannot execute installed Java at {home}: {error}') from error
    return dict(re.findall(r'^\s*([\w.]+) = (.*?)\s*$', result.stderr, re.MULTILINE))


def resolve_jdk(major, override_variable):
    selected = os.environ.get(override_variable)
    if selected is None:
        if sys.platform != 'darwin':
            raise RuntimeError(f'Set {override_variable} to an installed Oracle JDK {major}')
        try:
            selected = subprocess.run(
                ['/usr/libexec/java_home', '-F', '-v', str(major)],
                capture_output=True, text=True, check=True).stdout.strip()
        except (OSError, subprocess.CalledProcessError) as error:
            raise RuntimeError(
                f'Installed Oracle JDK {major} unavailable; set {override_variable} '
                'to a valid installed home. Automatic JDK downloads are disabled.') from error
    if not selected or not Path(selected).is_absolute():
        raise RuntimeError(f'{override_variable} must be an absolute installed JDK {major} home')
    home = Path(selected).resolve()
    if not all((home / 'bin' / tool).is_file() and os.access(home / 'bin' / tool, os.X_OK)
               for tool in ('java', 'javac')):
        raise RuntimeError(f'{home} is not an installed JDK; expected Oracle JDK {major}')
    props = java_properties(home)
    if (props.get('java.specification.version') != str(major)
            or props.get('java.vendor') != 'Oracle Corporation'
            or Path(props.get('java.home', '')).resolve() != home):
        raise RuntimeError(
            f'{override_variable}: expected Oracle JDK {major} at {home}; found '
            f"{props.get('java.version', 'unknown version')} / "
            f"{props.get('java.vendor', 'unknown vendor')} / {props.get('java.home')}. "
            'Select a valid installed Oracle home; no fallback or download is allowed.')
    # Also verify the compiler in that home, not merely its Java launcher.
    env = {key: value for key, value in os.environ.items() if key not in JAVA_OPTION_VARIABLES}
    compiler = subprocess.run([str(home / 'bin/javac'), '-version'],
                              capture_output=True, text=True, check=True, env=env)
    if not re.fullmatch(rf'javac {major}(?:[.\-+].*)?\s*', compiler.stdout):
        raise RuntimeError(f'Expected javac {major} at {home}; found {compiler.stdout.strip()}')
    return home


if __name__ == '__main__':
    try:
        requested = int(sys.argv[1])
        print(resolve_jdk(requested, 'ONE_MB_BUILD_JAVA_HOME' if requested == 25
                          else 'ONE_MB_TEST_JAVA_HOME'))
    except (RuntimeError, ValueError, IndexError, subprocess.CalledProcessError) as error:
        sys.exit(f'[jdk] {error}')
