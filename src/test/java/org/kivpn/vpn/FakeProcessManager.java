package org.kivpn.vpn;

import java.nio.file.Path;
import java.util.List;

public class FakeProcessManager extends ProcessManager {

    private boolean running;
    private List<String> lastCommand;

    @Override
    public void start(List<String> command, Path workDir, Path logFile) {
        this.lastCommand = command;
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public List<String> lastCommand() {
        return lastCommand;
    }
}
