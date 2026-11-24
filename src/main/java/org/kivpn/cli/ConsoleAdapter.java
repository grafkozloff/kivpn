package org.kivpn.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class ConsoleAdapter {

    private final BufferedReader in;
    private final PrintStream out;
    private final PrintStream err;
    private final CommandRouter router;

    @Autowired
    public ConsoleAdapter(CommandRouter router) {
        this(System.in, utf8(System.out), utf8(System.err), router);
    }

    public ConsoleAdapter(InputStream in, PrintStream out, PrintStream err) {
        this(in, out, err, new CommandRouter());
    }

    public ConsoleAdapter(InputStream in, PrintStream out, PrintStream err, CommandRouter router) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.err = err;
        this.router = router;
    }

    private static PrintStream utf8(PrintStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    public int run() {
        out.println("kivpn — консольный VPN-клиент. Введите help для списка команд.");
        while (true) {
            out.print("kivpn> ");
            out.flush();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                err.println("Ошибка чтения консоли: " + e.getMessage());
                return 1;
            }
            if (line == null) {
                break;
            }
            String input = line.trim();
            if (input.isEmpty()) {
                continue;
            }
            CommandRouter.Result result = router.dispatch(input);
            if (result.output() != null) {
                out.println(result.output());
            }
            if (result.error() != null) {
                err.println(result.error());
            }
            if (result.exitRequested()) {
                break;
            }
        }
        out.println("Завершение работы.");
        return 0;
    }
}
