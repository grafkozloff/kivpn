package org.kivpn.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;

class ServerSelectorTest {

    private final ServerSelector selector = new ServerSelector();

    private static Profile profile(long id, String name) {
        return new Profile(id, null, name, "vless", "host" + id, 443, "vless://x", null, false);
    }

    private static CheckResult check(long profileId, String status, Integer latency) {
        return new CheckResult(null, profileId, status, latency, "now", "fail".equals(status) ? "error" : null);
    }

    @Test
    void selectsOkWithLowestLatency() {
        Profile slow = profile(1, "Slow");
        Profile fast = profile(2, "Fast");
        Map<Long, CheckResult> checks = Map.of(
                1L, check(1, "ok", 200),
                2L, check(2, "ok", 20));
        assertEquals("Fast", selector.selectBest(List.of(slow, fast), checks).orElseThrow().name());
    }

    @Test
    void ignoresFailedAndUncheckedProfiles() {
        Profile failed = profile(1, "Failed");
        Profile unchecked = profile(2, "Unchecked");
        Profile ok = profile(3, "Ok");
        Map<Long, CheckResult> checks = Map.of(
                1L, check(1, "fail", null),
                3L, check(3, "ok", 50));
        assertEquals("Ok", selector.selectBest(List.of(failed, unchecked, ok), checks).orElseThrow().name());
    }

    @Test
    void emptyListReturnsEmpty() {
        assertTrue(selector.selectBest(List.of(), Map.of()).isEmpty());
    }

    @Test
    void allFailedReturnsEmpty() {
        Profile failed = profile(1, "Failed");
        Map<Long, CheckResult> checks = Map.of(1L, check(1, "fail", null));
        assertTrue(selector.selectBest(List.of(failed), checks).isEmpty());
    }

    @Test
    void okWithNullLatencyIsSelectableButLast() {
        Profile nullLatency = profile(1, "NullLatency");
        Profile measured = profile(2, "Measured");
        Map<Long, CheckResult> checks = Map.of(
                1L, check(1, "ok", null),
                2L, check(2, "ok", 100));
        assertEquals("Measured", selector.selectBest(List.of(nullLatency, measured), checks).orElseThrow().name());
    }
}
