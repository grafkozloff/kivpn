package org.kivpn.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import org.kivpn.vpn.FakeProcessManager;
import org.kivpn.vpn.SingBoxConfigGenerator;
import org.kivpn.vpn.XrayConfigGenerator;

class ConsoleAdapterTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    private CommandRouter router() {
        String dbPath = tempDir.resolve("data/kivpn.db").toString();
        SqliteConnectionManager connections = new SqliteConnectionManager(dbPath);
        new SchemaInitializer(connections).init();
        ProfileParserDispatcher dispatcher =
                new ProfileParserDispatcher(java.util.List.of(new VlessParser(), new SsParser(), new TrojanParser()));
        ServerChecker checker = new ServerChecker((host, port, timeoutMs) -> {
        }, 3000);
        ProfileRepository profileRepository = new ProfileRepository(connections);
        ProfileService service = new ProfileService(dispatcher, profileRepository,
                checker, new CheckRepository(connections), new ServerSelector());
        ConfigGeneratorDispatcher generators =
                new ConfigGeneratorDispatcher(java.util.List.of(new XrayConfigGenerator(), new SingBoxConfigGenerator()));
        ConnectionService connection = new ConnectionService(profileRepository, new SettingsRepository(connections),
                generators, new FakeProcessManager(), dbPath);
        SourceProviderDispatcher providers = new SourceProviderDispatcher(
                java.util.List.of(new SubscriptionSourceProvider(url -> ""), new FileSourceProvider()));
        SourceService sources = new SourceService(new SourceRepository(connections), profileRepository, providers, dispatcher);
        return new CommandRouter(service, connection, sources);
    }

    private int run(String input) {
        PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
        return new ConsoleAdapter(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out, err, router())
                .run();
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
