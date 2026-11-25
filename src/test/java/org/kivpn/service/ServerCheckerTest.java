package org.kivpn.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;

class ServerCheckerTest {

    private static Profile profile(long id) {
        return new Profile(id, null, "Server", "vless", "example.com", 443, "vless://x", null, false);
    }

    @Test
    void successfulProbeReturnsOkWithLatency() {
        ServerChecker checker = new ServerChecker((host, port, timeoutMs) -> {
        }, 3000);
        CheckResult result = checker.check(profile(1));
        assertEquals("ok", result.status());
        assertEquals(1, result.profileId());
        assertNotNull(result.latencyMs());
        assertTrue(result.latencyMs() >= 0);
        assertNull(result.error());
        assertNotNull(result.checkedAt());
    }

    @Test
    void failedProbeReturnsFailWithError() {
        ServerChecker checker = new ServerChecker((host, port, timeoutMs) -> {
            throw new IOException("connection timed out");
        }, 3000);
        CheckResult result = checker.check(profile(2));
        assertEquals("fail", result.status());
        assertNull(result.latencyMs());
        assertEquals("connection timed out", result.error());
    }

    @Test
    void nullErrorMessageFallsBackToExceptionName() {
        ServerChecker checker = new ServerChecker((host, port, timeoutMs) -> {
            throw new IOException();
        }, 3000);
        CheckResult result = checker.check(profile(3));
        assertEquals("fail", result.status());
        assertEquals("IOException", result.error());
    }

    @Test
    void probeReceivesProfileAddressAndPort() {
        StringBuilder seen = new StringBuilder();
        ServerChecker checker = new ServerChecker((host, port, timeoutMs) -> seen.append(host).append(':').append(port),
                3000);
        checker.check(profile(4));
        assertEquals("example.com:443", seen.toString());
    }
}
