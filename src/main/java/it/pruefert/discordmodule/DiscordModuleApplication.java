package it.pruefert.discordmodule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiscordModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscordModuleApplication.class, args);
    }
}
