package org.kivpn.model;

public record CheckResult(Long id, long profileId, String status, Integer latencyMs, String checkedAt, String error) {
}
