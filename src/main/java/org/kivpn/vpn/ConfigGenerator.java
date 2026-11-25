package org.kivpn.vpn;

import org.kivpn.model.Profile;

public interface ConfigGenerator {

    String moduleType();

    String generate(Profile profile);
}
