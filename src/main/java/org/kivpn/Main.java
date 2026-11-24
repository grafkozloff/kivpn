package org.kivpn;

import org.kivpn.cli.ConsoleAdapter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);
        int exitCode = context.getBean(ConsoleAdapter.class).run();
        context.close();
        System.exit(exitCode);
    }
}
