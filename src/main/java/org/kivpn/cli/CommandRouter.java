package org.kivpn.cli;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;
import org.kivpn.model.Source;
import org.kivpn.parser.ProfileParseException;
import org.kivpn.service.ProfileService;
import org.kivpn.source.SourceException;
import org.kivpn.source.SourceService;
import org.kivpn.vpn.ConnectionException;
import org.kivpn.vpn.ConnectionService;
import org.springframework.stereotype.Component;

@Component
public final class CommandRouter {

    public record Result(String output, String error, boolean exitRequested) {
    }

    private final ProfileService profiles;
    private final ConnectionService connection;
    private final SourceService sources;

    public CommandRouter(ProfileService profiles, ConnectionService connection, SourceService sources) {
        this.profiles = profiles;
        this.connection = connection;
        this.sources = sources;
    }

    public Result dispatch(String input) {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].trim() : "";
        return switch (command) {
            case "help" -> new Result(helpText(), null, false);
            case "exit" -> new Result("Выход из приложения.", null, true);
            case "add" -> addProfile(argument);
            case "import" -> importProfiles(argument);
            case "list" -> listProfiles();
            case "check" -> checkProfiles(argument);
            case "use" -> useProfile(argument);
            case "connect" -> connect();
            case "disconnect" -> disconnect();
            case "status" -> status();
            case "module" -> module(argument);
            case "source" -> source(argument);
            case "update" -> update(argument);
            case "export" -> export(argument);
            default -> new Result(null,
                    "Неизвестная команда: " + command + ". Введите help для списка команд.", false);
        };
    }

    private Result addProfile(String link) {
        if (link.isEmpty()) {
            return new Result(null, "Использование: add <ссылка vless://...>", false);
        }
        try {
            Profile profile = profiles.add(link);
            return new Result("Профиль добавлен: [" + profile.id() + "] " + profile.name()
                    + " (" + profile.protocol() + ", " + profile.address() + ":" + profile.port() + ")", null, false);
        } catch (ProfileParseException e) {
            return new Result(null, e.getMessage(), false);
        } catch (SQLException e) {
            return new Result(null, "Ошибка базы данных: " + e.getMessage(), false);
        }
    }

    private Result importProfiles(String path) {
        if (path.isEmpty()) {
            return new Result(null, "Использование: import <путь к файлу>", false);
        }
        try {
            ProfileService.ImportSummary summary = profiles.importFile(path);
            StringBuilder output = new StringBuilder("Импортировано профилей: " + summary.imported() + ".");
            for (String error : summary.errors()) {
                output.append('\n').append(error);
            }
            return new Result(output.toString(), null, false);
        } catch (IOException e) {
            return new Result(null, "Не удалось прочитать файл: " + e.getMessage(), false);
        } catch (SQLException e) {
            return new Result(null, "Ошибка базы данных: " + e.getMessage(), false);
        }
    }

    private Result listProfiles() {
        try {
            List<Profile> all = profiles.list();
            if (all.isEmpty()) {
                return new Result("Профилей пока нет. Добавьте командой add или import.", null, false);
            }
            Map<Long, CheckResult> checks = profiles.latestChecks();
            StringBuilder output = new StringBuilder("Список профилей:");
            for (Profile profile : all) {
                output.append('\n').append("  [").append(profile.id()).append("] ").append(profile.name())
                        .append(" — ").append(profile.protocol())
                        .append(", ").append(profile.address()).append(':').append(profile.port())
                        .append(", ").append(statusText(checks.get(profile.id())));
                if (profile.active()) {
                    output.append(", активен");
                }
            }
            return new Result(output.toString(), null, false);
        } catch (SQLException e) {
            return new Result(null, "Ошибка базы данных: " + e.getMessage(), false);
        }
    }

    private Result checkProfiles(String argument) {
        try {
            if (argument.isEmpty()) {
                List<Profile> all = profiles.list();
                if (all.isEmpty()) {
                    return new Result("Профилей пока нет. Добавьте командой add или import.", null, false);
                }
                StringBuilder output = new StringBuilder("Результаты проверки:");
                for (Profile profile : all) {
                    output.append('\n').append(checkLine(profile, profiles.check(profile)));
                }
                return new Result(output.toString(), null, false);
            }
            long id = parseId(argument);
            Optional<Profile> profile = profiles.find(id);
            if (profile.isEmpty()) {
                return new Result(null, "Профиль с id " + id + " не найден.", false);
            }
            return new Result(checkLine(profile.get(), profiles.check(profile.get())), null, false);
        } catch (NumberFormatException e) {
            return new Result(null, "Использование: check [id], где id — число.", false);
        } catch (SQLException e) {
            return new Result(null, "Ошибка базы данных: " + e.getMessage(), false);
        }
    }

    private Result useProfile(String argument) {
        if (argument.isEmpty()) {
            return new Result(null, "Использование: use <id|auto>", false);
        }
        try {
            if (argument.equalsIgnoreCase("auto")) {
                Optional<Profile> best = profiles.useAuto();
                if (best.isEmpty()) {
                    return new Result("Нет доступных серверов по результатам проверки. Сначала выполните check.",
                            null, false);
                }
                Profile profile = best.get();
                connection.onProfileSwitch(profile.id());
                return new Result("Активный профиль (автовыбор): [" + profile.id() + "] " + profile.name(), null, false);
            }
            long id = parseId(argument);
            Optional<Profile> profile = profiles.find(id);
            if (profile.isEmpty()) {
                return new Result(null, "Профиль с id " + id + " не найден.", false);
            }
            profiles.use(id);
            connection.onProfileSwitch(id);
            return new Result("Активный профиль: [" + id + "] " + profile.get().name(), null, false);
        } catch (NumberFormatException e) {
            return new Result(null, "Использование: use <id|auto>, где id — число.", false);
        } catch (SQLException e) {
            return new Result(null, "Ошибка базы данных: " + e.getMessage(), false);
        }
    }

    private static long parseId(String argument) {
        return Long.parseLong(argument.trim());
    }

    private static String checkLine(Profile profile, CheckResult check) {
        return "  [" + profile.id() + "] " + profile.name() + " — " + statusText(check);
    }

    private static String statusText(CheckResult check) {
        if (check == null) {
            return "не проверен";
        }
        if ("ok".equals(check.status())) {
            return "ok (" + check.latencyMs() + " ms)";
        }
        return "fail (" + check.error() + ")";
    }

    private Result connect() {
        try {
            return new Result(connection.connect(), null, false);
        } catch (ConnectionException e) {
            return new Result(null, e.getMessage(), false);
        }
    }

    private Result disconnect() {
        return new Result(connection.disconnect(), null, false);
    }

    private Result status() {
        try {
            return new Result(connection.status(), null, false);
        } catch (ConnectionException e) {
            return new Result(null, e.getMessage(), false);
        }
    }

    private Result module(String argument) {
        try {
            if (argument.isEmpty()) {
                return new Result(connection.moduleInfo(), null, false);
            }
            String path = argument;
            String type = null;
            String lower = argument.toLowerCase(Locale.ROOT);
            if (lower.endsWith(" xray")) {
                type = "xray";
                path = argument.substring(0, argument.length() - " xray".length()).trim();
            } else if (lower.endsWith(" sing-box")) {
                type = "sing-box";
                path = argument.substring(0, argument.length() - " sing-box".length()).trim();
            }
            return new Result(connection.setModule(path, type), null, false);
        } catch (ConnectionException e) {
            return new Result(null, e.getMessage(), false);
        }
    }

    private Result source(String argument) {
        String[] parts = argument.split("\\s+", 2);
        String sub = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        try {
            return switch (sub) {
                case "add" -> sourceAdd(rest);
                case "list" -> sourceList();
                case "enable" -> sourceToggle(rest, true);
                case "disable" -> sourceToggle(rest, false);
                case "remove" -> sourceRemove(rest);
                default -> new Result(null, "Использование: source <add|list|enable|disable|remove> ...", false);
            };
        } catch (SourceException e) {
            return new Result(null, e.getMessage(), false);
        } catch (NumberFormatException e) {
            return new Result(null, "Использование: source <enable|disable|remove> <id>, где id — число.", false);
        }
    }

    private Result sourceAdd(String rest) throws SourceException {
        String[] parts = rest.split("\\s+", 3);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return new Result(null, "Использование: source add <type> <url> [имя]", false);
        }
        String name = parts.length > 2 ? parts[2] : null;
        Source source = sources.addSource(name, parts[0], parts[1]);
        return new Result("Источник добавлен: [" + source.id() + "] " + source.name()
                + " (" + source.type() + ")", null, false);
    }

    private Result sourceList() throws SourceException {
        List<Source> all = sources.listSources();
        if (all.isEmpty()) {
            return new Result("Источников пока нет. Добавьте: source add <type> <url>.", null, false);
        }
        StringBuilder output = new StringBuilder("Источники:");
        for (Source source : all) {
            output.append('\n').append("  [").append(source.id()).append("] ").append(source.name())
                    .append(" — ").append(source.type()).append(", ").append(source.url())
                    .append(source.enabled() ? ", включён" : ", отключён");
        }
        return new Result(output.toString(), null, false);
    }

    private Result sourceToggle(String rest, boolean enabled) throws SourceException {
        long id = parseId(rest);
        sources.setEnabled(id, enabled);
        return new Result("Источник [" + id + "] " + (enabled ? "включён" : "отключён") + ".", null, false);
    }

    private Result sourceRemove(String rest) throws SourceException {
        long id = parseId(rest);
        sources.removeSource(id);
        return new Result("Источник [" + id + "] удалён. Профили сохранены.", null, false);
    }

    private Result update(String argument) {
        try {
            Long id = argument.isEmpty() ? null : parseId(argument);
            SourceService.UpdateSummary summary = sources.update(id);
            StringBuilder output = new StringBuilder("Обновление источников: добавлено " + summary.added()
                    + ", пропущено (уже есть) " + summary.skipped() + ".");
            for (String error : summary.errors()) {
                output.append('\n').append(error);
            }
            return new Result(output.toString(), null, false);
        } catch (NumberFormatException e) {
            return new Result(null, "Использование: update [id], где id — число.", false);
        } catch (SourceException e) {
            return new Result(null, e.getMessage(), false);
        }
    }

    private Result export(String argument) {
        String[] parts = argument.split("\\s+", 2);
        String target = parts[0];
        String file = parts.length > 1 ? parts[1].trim() : "";
        if (target.isEmpty()) {
            return new Result(null, "Использование: export <id|all> [файл]", false);
        }
        try {
            if (target.equalsIgnoreCase("all")) {
                if (file.isEmpty()) {
                    return new Result(null, "Использование: export all <файл>", false);
                }
                int count = profiles.exportAllToFile(file);
                return new Result("Экспортировано профилей: " + count + " в " + file + ".", null, false);
            }
            long id = parseId(target);
            Optional<Profile> profile = profiles.find(id);
            if (profile.isEmpty()) {
                return new Result(null, "Профиль с id " + id + " не найден.", false);
            }
            if (file.isEmpty()) {
                return new Result(profile.get().uri(), null, false);
            }
            profiles.exportToFile(profile.get(), file);
            return new Result("Профиль [" + id + "] экспортирован в " + file + ".", null, false);
        } catch (NumberFormatException e) {
            return new Result(null, "Использование: export <id|all> [файл], где id — число.", false);
        } catch (IOException e) {
            return new Result(null, "Не удалось записать файл: " + e.getMessage(), false);
        } catch (SQLException e) {
            return new Result(null, "Ошибка базы данных: " + e.getMessage(), false);
        }
    }

    private static String helpText() {
        return """
                Доступные команды:
                  help           — список доступных команд
                  add <ссылка>   — добавить профиль из ссылки vless://, ss://, trojan://
                  import <файл>  — импортировать профили из файла (по одной ссылке на строку)
                  list           — показать список профилей со статусом
                  check [id]     — проверить доступность серверов (всех или одного)
                  use <id|auto>  — выбрать активный профиль вручную или автоматически
                  export <id|all> [файл] — экспорт профиля в ссылку или файл
                  source <add|list|enable|disable|remove> — управление источниками серверов
                  update [id]    — обновить источники (загрузить профили из подписок/файлов)
                  module <путь> [xray|sing-box] — задать внешний VPN-модуль (для connect нужен sing-box)
                  connect        — подключить активный профиль: полный туннель TUN через sing-box
                                   (запустите run.bat от имени администратора)
                  disconnect     — остановить внешний модуль
                  status         — состояние подключения и модуль
                  exit           — завершение работы приложения""";
    }
}
