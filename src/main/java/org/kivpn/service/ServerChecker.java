package org.kivpn.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServerChecker {

    public interface Probe {
        void connect(String host, int port, int timeoutMs) throws IOException;
    }

    private final Probe probe;
    private final int timeoutMs;

    @Autowired
    public ServerChecker(@Value("${kivpn.check.timeout-ms:3000}") int timeoutMs) {
        this(ServerChecker::tcpConnect, timeoutMs);
    }

    public ServerChecker(Probe probe, int timeoutMs) {
        this.probe = probe;
        this.timeoutMs = timeoutMs;
    }

    public CheckResult check(Profile profile) {
        long start = System.nanoTime();
        try {
            probe.connect(profile.address(), profile.port(), timeoutMs);
            int latency = (int) ((System.nanoTime() - start) / 1_000_000);
            return new CheckResult(null, profile.id(), "ok", latency, Instant.now().toString(), null);
        } catch (IOException e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new CheckResult(null, profile.id(), "fail", null, Instant.now().toString(), error);
        }
    }

    private static void tcpConnect(String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
        }
    }
}
