package org.kivpn.vpn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.kivpn.db.ProfileRepository;
import org.kivpn.db.SettingsRepository;
import org.kivpn.model.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConnectionService {

    public static final String MODULE_PATH = "module.path";
    public static final String MODULE_TYPE = "module.type";

    private final ProfileRepository profiles;
    private final SettingsRepository settings;
    private final ConfigGeneratorDispatcher generators;
    private final ProcessManager processManager;
    private final AdminCheck adminCheck;
    private final Path workDir;
    private Long connectedProfileId;

    public ConnectionService(ProfileRepository profiles, SettingsRepository settings,
                             ConfigGeneratorDispatcher generators, ProcessManager processManager,
                             AdminCheck adminCheck,
                             @Value("${kivpn.db.path:data/kivpn.db}") String dbPath) {
        this.profiles = profiles;
        this.settings = settings;
        this.generators = generators;
        this.processManager = processManager;
        this.adminCheck = adminCheck;
        this.workDir = Path.of(dbPath).toAbsolutePath().getParent();
    }

    public String connect() throws ConnectionException {
        try {
            if (processManager.isRunning()) {
                throw new ConnectionException("Подключение уже активно. Сначала выполните disconnect.");
            }
            Optional<Profile> active = profiles.findActive();
            if (active.isEmpty()) {
                throw new ConnectionException("Нет активного профиля. Выберите командой use <id|auto>.");
            }
            String modulePath = settings.get(MODULE_PATH).orElse(null);
            if (modulePath == null || modulePath.isBlank()) {
                throw new ConnectionException("Внешний модуль не настроен. Задайте путь: module <путь к xray/sing-box>.");
            }
            String moduleType = settings.get(MODULE_TYPE).orElse("xray");
            if (!"sing-box".equals(moduleType)) {
                throw new ConnectionException("Полный туннель (TUN) поддерживается только модулем sing-box."
                        + " Задайте: module <путь к sing-box.exe> sing-box.");
            }
            if (!adminCheck.isAdmin()) {
                throw new ConnectionException("Для полного туннеля нужны права администратора."
                        + " Запустите run.bat от имени администратора.");
            }
            Path moduleDir = Path.of(modulePath).toAbsolutePath().getParent();
            if (moduleDir == null || !Files.exists(moduleDir.resolve("wintun.dll"))) {
                throw new ConnectionException("Рядом с sing-box.exe не найден wintun.dll."
                        + " Распакуйте официальный архив sing-box для Windows целиком (он содержит wintun.dll).");
            }
            Profile profile = active.get();
            String config = generators.generate(moduleType, profile);
            Files.createDirectories(workDir);
            Path configFile = workDir.resolve("module-config.json");
            Files.writeString(configFile, config);
            Path logFile = workDir.resolve("module.log");
            processManager.start(List.of(modulePath, "run", "-config", configFile.toString()), workDir, logFile);
            connectedProfileId = profile.id();
            return "Подключение запущено: [" + profile.id() + "] " + profile.name()
                    + " (модуль " + moduleType + ", полный туннель TUN).";
        } catch (ConnectionException e) {
            throw e;
        } catch (SQLException | IOException | IllegalArgumentException e) {
            throw new ConnectionException("Не удалось подключиться: " + e.getMessage(), e);
        }
    }

    public String disconnect() {
        boolean wasActive = processManager.isRunning() || connectedProfileId != null;
        processManager.stop();
        connectedProfileId = null;
        return wasActive ? "Подключение остановлено." : "Подключение не активно.";
    }

    public void onProfileSwitch(long newActiveId) {
        if (connectedProfileId != null && connectedProfileId != newActiveId) {
            processManager.stop();
            connectedProfileId = null;
        }
    }

    public String status() throws ConnectionException {
        try {
            StringBuilder output = new StringBuilder("Состояние: ")
                    .append(processManager.isRunning() ? "подключено" : "не подключено");
            if (processManager.isRunning()) {
                output.append("\nРежим: полный туннель (TUN)");
            }
            Optional<Profile> profile = connectedProfileId != null
                    ? profiles.findById(connectedProfileId)
                    : profiles.findActive();
            profile.ifPresent(p -> output.append("\nПрофиль: [").append(p.id()).append("] ").append(p.name()));
            String modulePath = settings.get(MODULE_PATH).orElse(null);
            String moduleType = settings.get(MODULE_TYPE).orElse("xray");
            output.append("\nМодуль: ").append(modulePath == null ? "не настроен" : moduleType + " (" + modulePath + ")");
            return output.toString();
        } catch (SQLException e) {
            throw new ConnectionException("Не удалось получить статус: " + e.getMessage(), e);
        }
    }

    public String setModule(String path, String type) throws ConnectionException {
        try {
            String resolvedType = (type == null || type.isBlank())
                    ? inferType(path) : type.toLowerCase(Locale.ROOT);
            if (!resolvedType.equals("xray") && !resolvedType.equals("sing-box")) {
                throw new ConnectionException("Неподдерживаемый тип модуля: " + resolvedType + ". Допустимо: xray, sing-box.");
            }
            settings.set(MODULE_PATH, path);
            settings.set(MODULE_TYPE, resolvedType);
            return "Модуль настроен: " + resolvedType + " (" + path + ").";
        } catch (SQLException e) {
            throw new ConnectionException("Не удалось сохранить настройки модуля: " + e.getMessage(), e);
        }
    }

    public String moduleInfo() throws ConnectionException {
        try {
            String path = settings.get(MODULE_PATH).orElse(null);
            String type = settings.get(MODULE_TYPE).orElse("xray");
            return path == null
                    ? "Модуль не настроен. Задайте: module <путь> [xray|sing-box]."
                    : "Модуль: " + type + " (" + path + ").";
        } catch (SQLException e) {
            throw new ConnectionException("Не удалось получить настройки модуля: " + e.getMessage(), e);
        }
    }

    private static String inferType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("sing-box") || lower.contains("singbox") ? "sing-box" : "xray";
    }
}
