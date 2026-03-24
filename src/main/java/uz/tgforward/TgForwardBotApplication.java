package uz.tgforward;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TgForwardBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(TgForwardBotApplication.class, args);
    }
}
