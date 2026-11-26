package org.kivpn.vpn;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class AdminCheck {

    public boolean isAdmin() {
        try {
            Process process = new ProcessBuilder("net", "session")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
