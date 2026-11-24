package org.kivpn.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ConsoleAdapterTest {

    private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    private int run(String input) {
        PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
        return new ConsoleAdapter(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out, err).run();
    }

    private String out() {
        return outBuffer.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBuffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void helpThenExitCompletesSuccessfully() {
        int code = run("help\nexit\n");
        assertEquals(0, code);
        assertTrue(out().contains("kivpn> "));
        assertTrue(out().contains("Доступные команды"));
        assertTrue(out().contains("Завершение работы."));
        assertEquals("", err());
    }

    @Test
    void eofWithoutExitCompletesSuccessfully() {
        int code = run("");
        assertEquals(0, code);
        assertTrue(out().contains("Завершение работы."));
    }

    @Test
    void blankLinesAreIgnored() {
        int code = run("   \n\nexit\n");
        assertEquals(0, code);
        assertEquals("", err());
    }

    @Test
    void unknownCommandGoesToStderrAndLoopContinues() {
        int code = run("foobar\nexit\n");
        assertEquals(0, code);
        assertTrue(err().contains("Неизвестная команда: foobar"));
        assertTrue(out().contains("Завершение работы."));
    }

    @Test
    void cyrillicInputAndOutputSurviveUtf8() {
        int code = run("помощь\nexit\n");
        assertEquals(0, code);
        assertTrue(err().contains("Неизвестная команда: помощь"));
    }
}
