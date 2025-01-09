package com.debianrose;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.EventHandler;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class Main extends PluginBase implements Listener {
    private static final String GITHUB_REPO = "kamikarus/TG2Nukkit";
    private TelegramBot bot;
    private String botToken, language;
    private Map<String, Map<String, String>> languages;
    private long botStartTime;
    private String telegramToMinecraftFormat, minecraftToTelegramFormat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        botToken = getConfig().getString("telegram.botToken");
        language = getConfig().getString("language", "en");
        telegramToMinecraftFormat = getConfig().getString("telegram-to-minecraft-format", "[TG] {sender}: {message}");
        minecraftToTelegramFormat = getConfig().getString("minecraft-to-telegram-format", "[MC] {sender}: {message}");
        loadLanguages();
        getServer().getPluginManager().registerEvents(this, this);

        if (botToken != null && !botToken.isEmpty()) {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                bot = new TelegramBot();
                botsApi.registerBot(bot);
                botStartTime = System.currentTimeMillis() / 1000;
                getLogger().info("Telegram бот успешно запущен!");
            } catch (TelegramApiException e) {
                getLogger().error("Failed to initialize Telegram bot", e);
            }
        } else {
            getLogger().error("Bot token is not set in config.yml!");
        }

        if (getConfig().getBoolean("check-for-updates", true)) {
            checkForUpdates();
        } else {
            getLogger().info("Проверка обновлений отключена в config.yml.");
        }
    }

    private void loadLanguages() {
        languages = new HashMap<>();
        Map<String, String> en = new HashMap<>(), ru = new HashMap<>(), ua = new HashMap<>(), kz = new HashMap<>(), es = new HashMap<>(), ar = new HashMap<>();
        en.put("online", "Players online: "); en.put("join", " joined the game!"); en.put("quit", " left the game!");
        ru.put("online", "Игроков онлайн: "); ru.put("join", " зашел на сервер!"); ru.put("quit", " вышел с сервера!");
        ua.put("online", "Гравців онлайн: "); ua.put("join", " зайшов на сервер!"); ua.put("quit", " вийшов з сервера!");
        kz.put("online", "Ойыншылар онлайн: "); kz.put("join", " серверге кірді!"); kz.put("quit", " серверден шықты!");
        es.put("online", "Jugadores en línea: "); es.put("join", " se unió al juego!"); es.put("quit", " dejó el juego!");
        ar.put("online", "اللاعبون على الإنترنت: "); ar.put("join", " انضم إلى اللعبة!"); ar.put("quit", " غادر اللعبة!");
        languages.put("en", en); languages.put("ru", ru); languages.put("ua", ua); languages.put("kz", kz); languages.put("es", es); languages.put("ar", ar);
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        bot.sendToTelegram(minecraftToTelegramFormat.replace("{sender}", event.getPlayer().getName()).replace("{message}", event.getMessage()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        bot.sendToTelegram(minecraftToTelegramFormat.replace("{sender}", event.getPlayer().getName()).replace("{message}", languages.get(language).get("join")));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bot.sendToTelegram(minecraftToTelegramFormat.replace("{sender}", event.getPlayer().getName()).replace("{message}", languages.get(language).get("quit")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setlanguage")) {
            if (args.length == 1 && languages.containsKey(args[0])) {
                language = args[0];
                getConfig().set("language", args[0]);
                saveConfig();
                sender.sendMessage("Language set to " + args[0]);
            } else {
                sender.sendMessage("Usage: /setlanguage <ru|en|es|uk|kk|ar>");
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("setformat")) {
            if (args.length == 2) {
                String formatType = args[0].toLowerCase();
                if (formatType.equals("telegram") || formatType.equals("minecraft")) {
                    if (formatType.equals("telegram")) {
                        telegramToMinecraftFormat = args[1];
                        getConfig().set("telegram-to-minecraft-format", args[1]);
                    } else {
                        minecraftToTelegramFormat = args[1];
                        getConfig().set("minecraft-to-telegram-format", args[1]);
                    }
                    saveConfig();
                    sender.sendMessage("Format updated successfully!");
                } else {
                    sender.sendMessage("Usage: /setformat <telegram|minecraft> <new_format>");
                }
            } else {
                sender.sendMessage("Usage: /setformat <telegram|minecraft> <new_format>");
            }
            return true;
        }
        return false;
    }

    private void checkForUpdates() {
        getLogger().info("Checking for updates...");
        try {
            URL url = new URL("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();
            JSONObject json = new JSONObject(response.toString());
            String latestVersion = json.getString("tag_name").replace("v", "");
            String currentVersion = getDescription().getVersion();
            if (isNewerVersion(latestVersion, currentVersion)) {
                String updateMessage = "A new version of TG2Nukkit is available: v" + latestVersion + " (you have v" + currentVersion + "). Download it from: https://github.com/" + GITHUB_REPO + "/releases/latest";
                getLogger().warning(updateMessage);
                if (bot != null) bot.sendToTelegram(updateMessage);
            } else {
                getLogger().info("TG2Nukkit is up to date!");
            }
        } catch (Exception e) {
            getLogger().error("Failed to check for updates: " + e.getMessage());
        }
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
        String[] latestParts = latestVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");
        for (int i = 0; i < Math.min(latestParts.length, currentParts.length); i++) {
            int latest = Integer.parseInt(latestParts[i]);
            int current = Integer.parseInt(currentParts[i]);
            if (latest > current) return true;
            else if (latest < current) return false;
        }
        return latestParts.length > currentParts.length;
    }

    private class TelegramBot extends TelegramLongPollingBot {
        @Override
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText() && update.getMessage().getDate() >= botStartTime) {
                Message message = update.getMessage();
                Chat chat = message.getChat();
                if (chat.isGroupChat() || chat.isSuperGroupChat()) {
                    String text = message.getText();
                    if (text.equalsIgnoreCase("/online")) {
                        sendToChat(chat.getId().toString(), languages.get(language).get("online") + getServer().getOnlinePlayers().size());
                    } else {
                        String sender = message.getFrom().getUserName();
                        String formattedMessage = telegramToMinecraftFormat
                                .replace("{sender}", sender)
                                .replace("{message}", text);
                        getServer().broadcastMessage(formattedMessage);
                    }
                }
            }
        }

        @Override
        public String getBotUsername() { return "TG2NukkitBot"; }

        @Override
        public String getBotToken() { return botToken; }

        public void sendToTelegram(String message) {}

        public void sendToChat(String chatId, String message) {
            try {
                execute(new SendMessage(chatId, message));
            } catch (TelegramApiException e) {
                getLogger().error("Failed to send message to Telegram", e);
            }
        }
    }
}