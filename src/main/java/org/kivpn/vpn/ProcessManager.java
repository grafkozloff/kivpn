package org.kivpn.vpn;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessManager {

    private Process process;

    public synchronized void start(List<String> command, Path workDir, Path logFile) throws IOException {
        if (isRunning()) {
            throw new IllegalStateException("Внешний модуль уже запущен.");
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        builder.redirectErrorStream(true);
        if (logFile != null) {
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        } else {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }
        process = builder.start();
    }

    public synchronized void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
        process = null;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    @PreDestroy
    public synchronized void shutdown() {
        stop();
    }
}
