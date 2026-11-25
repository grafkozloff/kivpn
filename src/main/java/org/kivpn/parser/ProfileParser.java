package org.kivpn.parser;

import org.kivpn.model.Profile;

public interface ProfileParser {

    String scheme();

    Profile parse(String link) throws ProfileParseException;
}
