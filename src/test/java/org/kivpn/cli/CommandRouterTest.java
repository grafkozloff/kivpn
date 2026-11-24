package org.kivpn.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandRouterTest {

    private final CommandRouter router = new CommandRouter();

    @Test
    void helpListsAvailableCommands() {
        CommandRouter.Result result = router.dispatch("help");
        assertNotNull(result.output());
        assertTrue(result.output().contains("help"));
        assertTrue(result.output().contains("exit"));
        assertNull(result.error());
        assertFalse(result.exitRequested());
    }

    @Test
    void commandNameIsCaseInsensitive() {
        assertTrue(router.dispatch("HELP").output().contains("Доступные команды"));
    }

    @Test
    void exitRequestsShutdown() {
        CommandRouter.Result result = router.dispatch("exit");
        assertTrue(result.exitRequested());
        assertNull(result.error());
    }

    @Test
    void unknownCommandReportsError() {
        CommandRouter.Result result = router.dispatch("foobar");
        assertNull(result.output());
        assertNotNull(result.error());
        assertTrue(result.error().contains("Неизвестная команда: foobar"));
        assertFalse(result.exitRequested());
    }

    @Test
    void plannedCommandReportsNotImplemented() {
        CommandRouter.Result result = router.dispatch("list");
        assertNotNull(result.output());
        assertTrue(result.output().contains("пока не реализована"));
        assertNull(result.error());
        assertFalse(result.exitRequested());
    }
}
