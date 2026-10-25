package fr.black_eyes.lootchest.compat;

import org.junit.jupiter.api.Test;

class JdkRuntimeIT {
    @Test
    void integrationTestsAndTheirChildProcessesUseTheSelectedOracle27() throws Exception {
        JdkRuntimeAssertions.assertOracle27RuntimeAndChildProcess();
    }
}
