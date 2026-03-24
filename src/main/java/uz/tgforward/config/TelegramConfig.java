package uz.tgforward.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import uz.tgforward.bot.TelegramBotHandler;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class TelegramConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Bean
    public OkHttpTelegramClient telegramClient() {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    public SpringLongPollingBot longPollingBot(TelegramBotHandler handler) {
        return new SpringLongPollingBot() {

            @Override
            public String getBotToken() {
                return botToken;
            }

            @Override
            public LongPollingUpdateConsumer getUpdatesConsumer() {
                return handler;
            }
        };
    }
}
