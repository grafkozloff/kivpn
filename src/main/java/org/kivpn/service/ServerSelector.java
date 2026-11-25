package org.kivpn.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.kivpn.model.CheckResult;
import org.kivpn.model.Profile;
import org.springframework.stereotype.Component;

@Component
public class ServerSelector {

    public Optional<Profile> selectBest(List<Profile> profiles, Map<Long, CheckResult> latestChecks) {
        return profiles.stream()
                .filter(profile -> {
                    CheckResult check = latestChecks.get(profile.id());
                    return check != null && "ok".equals(check.status());
                })
                .min(Comparator.comparingInt(profile -> {
                    Integer latency = latestChecks.get(profile.id()).latencyMs();
                    return latency == null ? Integer.MAX_VALUE : latency;
                }));
    }
}
