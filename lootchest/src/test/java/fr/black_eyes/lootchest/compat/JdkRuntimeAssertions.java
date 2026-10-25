package fr.black_eyes.lootchest.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

final class JdkRuntimeAssertions {
    private JdkRuntimeAssertions() {
    }

    static void assertOracle27RuntimeAndChildProcess() throws Exception {
        String selectedHome = System.getenv("ONE_MB_TEST_JAVA_HOME");
        assertNotNull(selectedHome, "Run tests through ./mvnw to select the installed Oracle JDKs");
        Path expectedHome = Path.of(selectedHome).toRealPath();
        Path expectedJava = expectedHome.resolve("bin/java").toRealPath();

        assertEquals(27, Runtime.version().feature(), "Tests must execute on Java 27");
        assertEquals("Oracle Corporation", System.getProperty("java.vendor"));
        assertEquals(expectedHome, Path.of(System.getProperty("java.home")).toRealPath());
        assertEquals(expectedJava, Path.of(ProcessHandle.current().info().command()
                .orElseThrow(() -> new AssertionError("Cannot inspect the test JVM executable")))
                .toRealPath());
        String environmentHome = System.getenv("JAVA_HOME");
        assertNotNull(environmentHome, "Forked tests must expose their selected JAVA_HOME");
        assertEquals(expectedHome, Path.of(environmentHome).toRealPath());

        Path output = Files.createTempFile("lootchest-child-jvm-", ".txt");
        try {
            Process child = new ProcessBuilder("java", "-XshowSettings:properties", "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            try {
                assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Child Java process did not finish");
                String diagnostics = Files.readString(output, StandardCharsets.UTF_8);
                assertEquals(0, child.exitValue(), diagnostics);
                Properties actual = new Properties();
                for (String line : diagnostics.lines().toList()) {
                    int separator = line.indexOf(" = ");
                    if (separator > 0) {
                        actual.setProperty(line.substring(0, separator).trim(),
                                line.substring(separator + 3).trim());
                    }
                }
                assertEquals("27", actual.getProperty("java.specification.version"), diagnostics);
                assertEquals("Oracle Corporation", actual.getProperty("java.vendor"), diagnostics);
                assertNotNull(actual.getProperty("java.home"), diagnostics);
                assertEquals(expectedHome, Path.of(actual.getProperty("java.home")).toRealPath(),
                        diagnostics);
            } finally {
                if (child.isAlive()) {
                    child.destroyForcibly();
                    child.waitFor(10, TimeUnit.SECONDS);
                }
            }
        } finally {
            Files.deleteIfExists(output);
        }

        System.out.printf("Verified test JVM: %s; %s; %s; child Java uses the same installed JDK%n",
                expectedJava, Runtime.version(), System.getProperty("java.vendor"));
    }

    static void assertProductionAndTestBytecodeIsJava25() throws Exception {
        Path testClasses = Path.of(JdkRuntimeAssertions.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path productionClasses = testClasses.resolveSibling("classes");
        for (Path directory : new Path[] {productionClasses, testClasses}) {
            assertTrue(Files.isDirectory(directory), () -> "Missing compiled classes: " + directory);
            try (var paths = Files.walk(directory)) {
                var classes = paths.filter(path -> path.toString().endsWith(".class")).toList();
                assertTrue(!classes.isEmpty(), () -> "No compiled classes in " + directory);
                for (Path compiled : classes) {
                    try (DataInputStream input = new DataInputStream(Files.newInputStream(compiled))) {
                        assertEquals(0xCAFEBABE, input.readInt(), compiled.toString());
                        assertEquals(0, input.readUnsignedShort(),
                                () -> "Preview bytecode is not supported: " + compiled);
                        assertEquals(69, input.readUnsignedShort(),
                                () -> "Expected Java 25 bytecode: " + compiled);
                    }
                }
                System.out.printf("Verified Java 25 bytecode (major 69): %d classes in %s%n",
                        classes.size(), directory);
            }
        }
    }
}
