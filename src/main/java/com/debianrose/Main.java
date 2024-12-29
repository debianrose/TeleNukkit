package com.debianrose;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.HashMap;
import java.util.Map;

public class Main extends PluginBase implements Listener {
    private MyTelegramBot bot;
    private String chatId;
    private String language;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        Config config = this.getConfig();

        String botToken = config.getString("telegram.botToken");
        chatId = config.getString("telegram.chatId");
        language = config.getString("language", "en");

        if (botToken == null || botToken.isEmpty()) {
            this.getLogger().error(getMessage("bot_token_not_set"));
            this.getPluginLoader().disablePlugin(this);
            return;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            bot = new MyTelegramBot(botToken, chatId);
            botsApi.registerBot(bot);
            this.getLogger().info(getMessage("bot_started"));
        } catch (TelegramApiException e) {
            this.getLogger().error(getMessage("bot_start_failed") + e.getMessage());
        }

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        bot.sendMessageToTelegram(chatId, getMessage("game_chat_format", player.getName(), message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setlanguage")) {
            if (args.length == 1 && (args[0].equalsIgnoreCase("ru") || args[0].equalsIgnoreCase("en") || args[0].equalsIgnoreCase("es") || args[0].equalsIgnoreCase("uk") || args[0].equalsIgnoreCase("kk") || args[0].equalsIgnoreCase("ar"))) {
                language = args[0].toLowerCase();
                getConfig().set("language", language);
                saveConfig();
                sender.sendMessage(getMessage("language_set", language));
            } else {
                sender.sendMessage(getMessage("language_usage"));
            }
            return true;
        }
        return false;
    }

    private String getMessage(String key, Object... args) {
        Map<String, String> messages = new HashMap<>();
        switch (language) {
            case "ru":
                messages.put("bot_token_not_set", "Bot Token не установлен в config.yml!");
                messages.put("bot_started", "Telegram бот успешно запущен!");
                messages.put("bot_start_failed", "Не удалось запустить Telegram бота: ");
                messages.put("game_chat_format", "[Игра] %s: %s");
                messages.put("telegram_chat_format", "[Telegram] %s: %s");
                messages.put("language_set", "Язык изменен на %s.");
                messages.put("language_usage", "Использование: /setlanguage <ru|en|es|uk|kk|ar>");
                messages.put("command_online", "Игроки онлайн:\n%s");
                messages.put("command_unknown", "Неизвестная команда.");
                break;
            case "en":
                messages.put("bot_token_not_set", "Bot Token is not set in config.yml!");
                messages.put("bot_started", "Telegram bot started successfully!");
                messages.put("bot_start_failed", "Failed to start Telegram bot: ");
                messages.put("game_chat_format", "[Game] %s: %s");
                messages.put("telegram_chat_format", "[Telegram] %s: %s");
                messages.put("language_set", "Language set to %s.");
                messages.put("language_usage", "Usage: /setlanguage <ru|en|es|uk|kk|ar>");
                messages.put("command_online", "Online players:\n%s");
                messages.put("command_unknown", "Unknown command.");
                break;
            case "es":
                messages.put("bot_token_not_set", "¡Bot Token no está configurado en config.yml!");
                messages.put("bot_started", "¡Bot de Telegram iniciado correctamente!");
                messages.put("bot_start_failed", "Error al iniciar el bot de Telegram: ");
                messages.put("game_chat_format", "[Juego] %s: %s");
                messages.put("telegram_chat_format", "[Telegram] %s: %s");
                messages.put("language_set", "Idioma cambiado a %s.");
                messages.put("language_usage", "Uso: /setlanguage <ru|en|es|uk|kk|ar>");
                messages.put("command_online", "Jugadores en línea:\n%s");
                messages.put("command_unknown", "Comando desconocido.");
                break;
            case "ua":
                messages.put("bot_token_not_set", "Bot Token не встановлено в config.yml!");
                messages.put("bot_started", "Telegram бот успішно запущено!");
                messages.put("bot_start_failed", "Не вдалося запустити Telegram бота: ");
                messages.put("game_chat_format", "[Гра] %s: %s");
                messages.put("telegram_chat_format", "[Telegram] %s: %s");
                messages.put("language_set", "Мову змінено на %s.");
                messages.put("language_usage", "Використання: /setlanguage <ru|en|es|uk|kk|ar>");
                messages.put("command_online", "Гравці онлайн:\n%s");
                messages.put("command_unknown", "Невідома команда.");
                break;
            case "kz":
                messages.put("bot_token_not_set", "Bot Token config.yml файлында орнатылмаған!");
                messages.put("bot_started", "Telegram бот сәтті іске қосылды!");
                messages.put("bot_start_failed", "Telegram ботты іске қосу мүмкін емес: ");
                messages.put("game_chat_format", "[Ойын] %s: %s");
                messages.put("telegram_chat_format", "[Telegram] %s: %s");
                messages.put("language_set", "Тіл %s тіліне өзгертілді.");
                messages.put("language_usage", "Қолдану: /setlanguage <ru|en|es|uk|kk|ar>");
                messages.put("command_online", "Онлайн ойыншылар:\n%s");
                messages.put("command_unknown", "Белгісіз команда.");
                break;
            case "ar":
                messages.put("bot_token_not_set", "لم يتم تعيين Bot Token في config.yml!");
                messages.put("bot_started", "تم تشغيل بوت Telegram بنجاح!");
                messages.put("bot_start_failed", "فشل تشغيل بوت Telegram: ");
                messages.put("game_chat_format", "[اللعبة] %s: %s");
                messages.put("telegram_chat_format", "[Telegram] %s: %s");
                messages.put("language_set", "تم تغيير اللغة إلى %s.");
                messages.put("language_usage", "الاستخدام: /setlanguage <ru|en|es|uk|kk|ar>");
                messages.put("command_online", "اللاعبون المتصلون:\n%s");
                messages.put("command_unknown", "أمر غير معروف.");
                break;
        }
        return String.format(messages.getOrDefault(key, key), args);
    }

    public class MyTelegramBot extends TelegramLongPollingBot {
        private final String botToken;
        private final String chatId;

        public MyTelegramBot(String botToken, String chatId) {
            this.botToken = botToken;
            this.chatId = chatId;
        }

        @Override
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                if (messageText.startsWith("/")) {
                    handleCommand(messageText, chatId);
                } else {
                    String formattedMessage = getMessage("telegram_chat_format", update.getMessage().getFrom().getFirstName(), messageText);
                    getServer().broadcastMessage(formattedMessage);
                }
            }
        }

        private void handleCommand(String command, long chatId) {
            switch (command) {
                case "/online":
                    StringBuilder onlinePlayers = new StringBuilder();
                    for (Player player : getServer().getOnlinePlayers().values()) {
                        onlinePlayers.append("- ").append(player.getName()).append("\n");
                    }
                    sendMessageToTelegram(String.valueOf(chatId), getMessage("command_online", onlinePlayers.toString()));
                    break;
                default:
                    sendMessageToTelegram(String.valueOf(chatId), getMessage("command_unknown"));
                    break;
            }
        }

        public void sendMessageToTelegram(String chatId, String text) {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                getLogger().error("Ошибка при отправке сообщения в Telegram: " + e.getMessage());
            }
        }

        @Override
        public String getBotUsername() {
            return "NukkitServerBot";
        }

        @Override
        public String getBotToken() {
            return botToken;
        }
    }
}
