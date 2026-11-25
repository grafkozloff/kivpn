package org.kivpn.source;

import java.io.IOException;
import java.util.List;
import org.kivpn.model.Source;

public interface SourceProvider {

    String type();

    List<String> fetch(Source source) throws IOException;
}
