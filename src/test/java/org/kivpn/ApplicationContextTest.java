package org.kivpn;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.kivpn.cli.CommandRouter;
import org.kivpn.cli.ConsoleAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "kivpn.db.path=target/test-context/kivpn.db")
class ApplicationContextTest {

    @Autowired
    private ConsoleAdapter consoleAdapter;

    @Autowired
    private CommandRouter commandRouter;

    @Test
    void contextLoadsCliBeans() {
        assertNotNull(consoleAdapter);
        assertNotNull(commandRouter);
    }
}
