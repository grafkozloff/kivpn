package org.kivpn.vpn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProcessManagerTest {

    private final ProcessManager manager = new ProcessManager();

    private static List<String> longRunningCommand() {
        return List.of("ping", "-n", "30", "127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        manager.stop();
    }

    @Test
    void startLaunchesProcessAndStopTerminatesIt() throws Exception {
        manager.start(longRunningCommand(), null, null);
        assertTrue(manager.isRunning());
        manager.stop();
        assertFalse(manager.isRunning());
    }

    @Test
    void stopWhenIdleIsSafe() {
        manager.stop();
        assertFalse(manager.isRunning());
    }

    @Test
    void startTwiceThrows() throws Exception {
        manager.start(longRunningCommand(), null, null);
        assertThrows(IllegalStateException.class, () -> manager.start(longRunningCommand(), null, null));
    }
}
