package org.kivpn.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kivpn.db.CheckRepository;
import org.kivpn.db.ProfileRepository;
import org.kivpn.db.SchemaInitializer;
import org.kivpn.db.SettingsRepository;
import org.kivpn.db.SourceRepository;
import org.kivpn.db.SqliteConnectionManager;
import org.kivpn.parser.ProfileParserDispatcher;
import org.kivpn.parser.SsParser;
import org.kivpn.parser.TrojanParser;
import org.kivpn.parser.VlessParser;
import org.kivpn.service.ProfileService;
import org.kivpn.service.ServerChecker;
import org.kivpn.service.ServerSelector;
import org.kivpn.source.FileSourceProvider;
import org.kivpn.source.SourceProviderDispatcher;
import org.kivpn.source.SourceService;
import org.kivpn.source.SubscriptionSourceProvider;
import org.kivpn.vpn.ConfigGeneratorDispatcher;
import org.kivpn.vpn.ConnectionService;
import org.kivpn.vpn.FakeAdminCheck;
import org.kivpn.vpn.FakeProcessManager;
import org.kivpn.vpn.SingBoxConfigGenerator;
import org.kivpn.vpn.XrayConfigGenerator;

class CommandRouterTest {

    private static final String UUID = "11111111-2222-3333-4444-555555555555";
    private static final String LINK = "vless://" + UUID + "@example.com:443?security=reality&pbk=SECRET_KEY#Server";

    @TempDir
    Path tempDir;

    private CommandRouter router;
    private ProfileService profiles;
    private ConnectionService connection;
    private SourceService sources;
    private FakeProcessManager processManager;
    private String modulePath;

    @BeforeEach
    void setUp() throws Exception {
        Path moduleDir = tempDir.resolve("sing-box");
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("sing-box.exe"), "");
        Files.writeString(moduleDir.resolve("wintun.dll"), "");
        modulePath = moduleDir.resolve("sing-box.exe").toString();
        String dbPath = tempDir.resolve("data/kivpn.db").toString();
        SqliteConnectionManager connections = new SqliteConnectionManager(dbPath);
        new SchemaInitializer(connections).init();
        ProfileParserDispatcher dispatcher =
                new ProfileParserDispatcher(java.util.List.of(new VlessParser(), new SsParser(), new TrojanParser()));
        ServerChecker.Probe probe = (host, port, timeoutMs) -> {
            if (host.startsWith("fail")) {
                throw new IOException("connection refused");
            }
        };
        ProfileRepository profileRepository = new ProfileRepository(connections);
        profiles = new ProfileService(dispatcher, profileRepository,
                new ServerChecker(probe, 3000), new CheckRepository(connections), new ServerSelector());
        processManager = new FakeProcessManager();
        ConfigGeneratorDispatcher generators =
                new ConfigGeneratorDispatcher(java.util.List.of(new XrayConfigGenerator(), new SingBoxConfigGenerator()));
        connection = new ConnectionService(profileRepository, new SettingsRepository(connections),
                generators, processManager, new FakeAdminCheck(), dbPath);
        SubscriptionSourceProvider subscription = new SubscriptionSourceProvider(url -> LINK);
        SourceProviderDispatcher providers =
                new SourceProviderDispatcher(java.util.List.of(subscription, new FileSourceProvider()));
        sources = new SourceService(new SourceRepository(connections), profileRepository, providers, dispatcher);
        router = new CommandRouter(profiles, connection, sources);
    }

    @Test
    void helpListsAvailableCommands() {
        CommandRouter.Result result = router.dispatch("help");
        assertNotNull(result.output());
        assertTrue(result.output().contains("help"));
        assertTrue(result.output().contains("add"));
        assertTrue(result.output().contains("import"));
        assertTrue(result.output().contains("list"));
        assertTrue(result.output().contains("check"));
        assertTrue(result.output().contains("use"));
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
    void addStoresProfileAndReportsId() throws Exception {
        CommandRouter.Result result = router.dispatch("add " + LINK);
        assertNull(result.error());
        assertTrue(result.output().contains("Профиль добавлен"));
        assertTrue(result.output().contains("Server"));
        assertTrue(result.output().contains("example.com:443"));
        assertEquals(1, profiles.list().size());
    }

    @Test
    void addWithoutArgumentReportsUsage() {
        CommandRouter.Result result = router.dispatch("add");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: add"));
    }

    @Test
    void addInvalidLinkReportsParseError() {
        CommandRouter.Result result = router.dispatch("add vless://broken");
        assertNull(result.output());
        assertNotNull(result.error());
    }

    @Test
    void importReadsFileAndReportsPerLineErrors() throws Exception {
        Path file = tempDir.resolve("profiles.txt");
        Files.writeString(file, "# комментарий\n\n" + LINK + "\nvless://broken\n"
                + "vless://" + UUID + "@example.org:8443?security=tls#Second\n");

        CommandRouter.Result result = router.dispatch("import " + file);
        assertNull(result.error());
        assertTrue(result.output().contains("Импортировано профилей: 2."));
        assertTrue(result.output().contains("Строка 4"));
        assertEquals(2, profiles.list().size());
    }

    @Test
    void importWithoutArgumentReportsUsage() {
        CommandRouter.Result result = router.dispatch("import");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: import"));
    }

    @Test
    void importMissingFileReportsError() {
        CommandRouter.Result result = router.dispatch("import " + tempDir.resolve("no-such-file.txt"));
        assertNull(result.output());
        assertTrue(result.error().contains("Не удалось прочитать файл"));
    }

    @Test
    void listEmptyReportsNoProfiles() {
        CommandRouter.Result result = router.dispatch("list");
        assertNull(result.error());
        assertTrue(result.output().contains("Профилей пока нет"));
    }

    @Test
    void listShowsProfilesWithoutSecrets() {
        router.dispatch("add " + LINK);
        CommandRouter.Result result = router.dispatch("list");
        assertNull(result.error());
        assertTrue(result.output().contains("Server"));
        assertTrue(result.output().contains("example.com:443"));
        assertFalse(result.output().contains(UUID));
        assertFalse(result.output().contains("SECRET_KEY"));
    }

    @Test
    void checkAllReportsStatusesAndPersists() throws Exception {
        router.dispatch("add " + LINK);
        router.dispatch("add vless://" + UUID + "@fail.example.com:443#Fail");
        CommandRouter.Result result = router.dispatch("check");
        assertNull(result.error());
        assertTrue(result.output().contains("ok ("));
        assertTrue(result.output().contains("fail ("));
        assertEquals(2, profiles.latestChecks().size());
    }

    @Test
    void checkSingleById() {
        router.dispatch("add " + LINK);
        CommandRouter.Result result = router.dispatch("check 1");
        assertNull(result.error());
        assertTrue(result.output().contains("Server"));
        assertTrue(result.output().contains("ok ("));
    }

    @Test
    void checkUnknownIdReportsError() {
        CommandRouter.Result result = router.dispatch("check 999");
        assertNull(result.output());
        assertTrue(result.error().contains("не найден"));
    }

    @Test
    void checkInvalidIdReportsUsage() {
        CommandRouter.Result result = router.dispatch("check abc");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: check"));
    }

    @Test
    void useManualSetsActive() {
        router.dispatch("add " + LINK);
        router.dispatch("add vless://" + UUID + "@example.org:8443#Second");
        CommandRouter.Result result = router.dispatch("use 2");
        assertNull(result.error());
        assertTrue(result.output().contains("Активный профиль"));
        assertTrue(result.output().contains("Second"));
        assertTrue(router.dispatch("list").output().contains("активен"));
    }

    @Test
    void useAutoSelectsWorkingProfile() {
        router.dispatch("add " + LINK);
        router.dispatch("add vless://" + UUID + "@fail.example.com:443#Fail");
        router.dispatch("check");
        CommandRouter.Result result = router.dispatch("use auto");
        assertNull(result.error());
        assertTrue(result.output().contains("автовыбор"));
        assertTrue(result.output().contains("Server"));
    }

    @Test
    void useAutoWithoutWorkingProfilesReportsMessage() {
        router.dispatch("add vless://" + UUID + "@fail.example.com:443#Fail");
        router.dispatch("check");
        CommandRouter.Result result = router.dispatch("use auto");
        assertNull(result.error());
        assertTrue(result.output().contains("Нет доступных серверов"));
    }

    @Test
    void useWithoutArgumentReportsUsage() {
        CommandRouter.Result result = router.dispatch("use");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: use"));
    }

    @Test
    void useUnknownIdReportsError() {
        CommandRouter.Result result = router.dispatch("use 999");
        assertNull(result.output());
        assertTrue(result.error().contains("не найден"));
    }

    @Test
    void listShowsStatusAndUnchecked() {
        router.dispatch("add " + LINK);
        router.dispatch("add vless://" + UUID + "@example.org:8443#Second");
        router.dispatch("check 1");
        CommandRouter.Result result = router.dispatch("list");
        assertNull(result.error());
        assertTrue(result.output().contains("ok ("));
        assertTrue(result.output().contains("не проверен"));
    }

    @Test
    void connectWithoutActiveProfileReportsError() {
        CommandRouter.Result result = router.dispatch("connect");
        assertNull(result.output());
        assertTrue(result.error().contains("Нет активного профиля"));
    }

    @Test
    void connectWithoutModuleReportsError() {
        router.dispatch("add " + LINK);
        router.dispatch("use 1");
        CommandRouter.Result result = router.dispatch("connect");
        assertNull(result.output());
        assertTrue(result.error().contains("модуль не настроен"));
    }

    @Test
    void connectStartsProcess() {
        router.dispatch("add " + LINK);
        router.dispatch("use 1");
        router.dispatch("module " + modulePath);
        CommandRouter.Result result = router.dispatch("connect");
        assertNull(result.error());
        assertTrue(result.output().contains("Подключение запущено"));
        assertTrue(processManager.isRunning());
    }

    @Test
    void connectWithXrayReportsError() {
        router.dispatch("add " + LINK);
        router.dispatch("use 1");
        router.dispatch("module C:/xray/xray.exe");
        CommandRouter.Result result = router.dispatch("connect");
        assertNull(result.output());
        assertTrue(result.error().contains("sing-box"));
        assertFalse(processManager.isRunning());
    }

    @Test
    void disconnectStopsProcess() {
        router.dispatch("add " + LINK);
        router.dispatch("use 1");
        router.dispatch("module " + modulePath);
        router.dispatch("connect");
        CommandRouter.Result result = router.dispatch("disconnect");
        assertNull(result.error());
        assertTrue(result.output().contains("Подключение остановлено"));
        assertFalse(processManager.isRunning());
    }

    @Test
    void statusShowsState() {
        CommandRouter.Result result = router.dispatch("status");
        assertNull(result.error());
        assertTrue(result.output().contains("Состояние:"));
        assertTrue(result.output().contains("Модуль:"));
    }

    @Test
    void moduleSetsPathAndType() {
        CommandRouter.Result result = router.dispatch("module C:/xray/xray.exe");
        assertNull(result.error());
        assertTrue(result.output().contains("Модуль настроен"));
        assertTrue(router.dispatch("module").output().contains("xray"));
    }

    @Test
    void moduleWithExplicitSingBoxType() {
        CommandRouter.Result result = router.dispatch("module C:/tools/core.exe sing-box");
        assertNull(result.error());
        assertTrue(result.output().contains("sing-box"));
    }

    @Test
    void moduleWithoutArgumentShowsInfo() {
        CommandRouter.Result result = router.dispatch("module");
        assertNull(result.error());
        assertTrue(result.output().contains("Модуль не настроен"));
    }

    @Test
    void helpIncludesConnectionCommands() {
        CommandRouter.Result result = router.dispatch("help");
        assertTrue(result.output().contains("connect"));
        assertTrue(result.output().contains("disconnect"));
        assertTrue(result.output().contains("status"));
        assertTrue(result.output().contains("module"));
        assertTrue(result.output().contains("source"));
        assertTrue(result.output().contains("update"));
    }

    @Test
    void sourceAddAndList() {
        CommandRouter.Result add = router.dispatch("source add subscription https://example.org/sub MySub");
        assertNull(add.error());
        assertTrue(add.output().contains("Источник добавлен"));
        CommandRouter.Result list = router.dispatch("source list");
        assertTrue(list.output().contains("MySub"));
        assertTrue(list.output().contains("subscription"));
    }

    @Test
    void sourceAddInvalidTypeReportsError() {
        CommandRouter.Result result = router.dispatch("source add vpngate https://example.org");
        assertNull(result.output());
        assertTrue(result.error().contains("Неподдерживаемый тип источника"));
    }

    @Test
    void sourceEnableDisable() {
        router.dispatch("source add subscription https://example.org/sub");
        assertTrue(router.dispatch("source disable 1").output().contains("отключён"));
        assertTrue(router.dispatch("source enable 1").output().contains("включён"));
    }

    @Test
    void sourceRemove() {
        router.dispatch("source add subscription https://example.org/sub");
        CommandRouter.Result result = router.dispatch("source remove 1");
        assertNull(result.error());
        assertTrue(result.output().contains("удалён"));
        assertTrue(router.dispatch("source list").output().contains("Источников пока нет"));
    }

    @Test
    void sourceUnknownSubcommandReportsUsage() {
        CommandRouter.Result result = router.dispatch("source foo");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: source"));
    }

    @Test
    void updateImportsFromSubscription() throws Exception {
        router.dispatch("source add subscription https://example.org/sub");
        CommandRouter.Result result = router.dispatch("update");
        assertNull(result.error());
        assertTrue(result.output().contains("добавлено 1"));
        assertEquals(1, profiles.list().size());
    }

    @Test
    void updateDeduplicates() {
        router.dispatch("source add subscription https://example.org/sub");
        router.dispatch("update");
        CommandRouter.Result second = router.dispatch("update");
        assertTrue(second.output().contains("добавлено 0"));
        assertTrue(second.output().contains("пропущено"));
    }

    @Test
    void updateUnknownIdReportsError() {
        CommandRouter.Result result = router.dispatch("update 999");
        assertNull(result.output());
        assertTrue(result.error().contains("не найден"));
    }

    @Test
    void exportPrintsUri() {
        router.dispatch("add " + LINK);
        CommandRouter.Result result = router.dispatch("export 1");
        assertNull(result.error());
        assertEquals(LINK, result.output());
    }

    @Test
    void exportToFile() throws Exception {
        router.dispatch("add " + LINK);
        Path file = tempDir.resolve("exported.txt");
        CommandRouter.Result result = router.dispatch("export 1 " + file);
        assertNull(result.error());
        assertTrue(result.output().contains("экспортирован"));
        assertEquals(LINK, Files.readString(file).trim());
    }

    @Test
    void exportAllToFile() throws Exception {
        router.dispatch("add " + LINK);
        router.dispatch("add vless://" + UUID + "@example.org:8443#Second");
        Path file = tempDir.resolve("all.txt");
        CommandRouter.Result result = router.dispatch("export all " + file);
        assertNull(result.error());
        assertTrue(result.output().contains("Экспортировано профилей: 2"));
        assertEquals(2, Files.readAllLines(file).size());
    }

    @Test
    void exportUnknownIdReportsError() {
        CommandRouter.Result result = router.dispatch("export 999");
        assertNull(result.output());
        assertTrue(result.error().contains("не найден"));
    }

    @Test
    void exportWithoutArgumentReportsUsage() {
        CommandRouter.Result result = router.dispatch("export");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: export"));
    }

    @Test
    void exportAllWithoutFileReportsUsage() {
        CommandRouter.Result result = router.dispatch("export all");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: export all"));
    }

    @Test
    void exportInvalidIdReportsUsage() {
        CommandRouter.Result result = router.dispatch("export abc");
        assertNull(result.output());
        assertTrue(result.error().contains("Использование: export"));
    }

    @Test
    void helpIncludesExport() {
        assertTrue(router.dispatch("help").output().contains("export"));
    }
}
