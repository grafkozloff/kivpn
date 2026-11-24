package org.kivpn.cli;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class CommandRouter {

    public record Result(String output, String error, boolean exitRequested) {
    }

    private static final Set<String> PLANNED_COMMANDS = Set.of(
            "add", "import", "list", "check", "use", "update",
            "connect", "disconnect", "status", "export");

    public Result dispatch(String input) {
        String[] parts = input.split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "help" -> new Result(helpText(), null, false);
            case "exit" -> new Result("Выход из приложения.", null, true);
            default -> {
                if (PLANNED_COMMANDS.contains(command)) {
                    yield new Result("Команда " + command + " пока не реализована.", null, false);
                }
                yield new Result(null,
                        "Неизвестная команда: " + command + ". Введите help для списка команд.", false);
            }
        };
    }

    private static String helpText() {
        return """
                Доступные команды:
                  help  — список доступных команд
                  exit  — завершение работы приложения"""
                ;
    }
}
