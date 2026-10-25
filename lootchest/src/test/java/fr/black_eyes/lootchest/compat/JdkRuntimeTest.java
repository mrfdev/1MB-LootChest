package fr.black_eyes.lootchest.compat;

import org.junit.jupiter.api.Test;

class JdkRuntimeTest {
    @Test
    void unitTestsAndTheirChildProcessesUseTheSelectedOracle27() throws Exception {
        JdkRuntimeAssertions.assertOracle27RuntimeAndChildProcess();
    }

    @Test
    void pluginAndTestSourcesProduceJava25Bytecode() throws Exception {
        JdkRuntimeAssertions.assertProductionAndTestBytecodeIsJava25();
    }
}
