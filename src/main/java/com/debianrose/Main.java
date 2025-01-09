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
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class Main extends PluginBase implements Listener {

    private static final String GITHUB_REPO = "kamikarus/TG2Nukkit";

    private TelegramBot bot;
    private String botToken;
    private String chatId;
    private String language;
    private Map<String, Map<String, String>> languages;
    private long botStartTime;

    private String telegramToMinecraftFormat;
    private String minecraftToTelegramFormat;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.botToken = this.getConfig().getString("telegram.botToken");
        this.language = this.getConfig().getString("language", "en");

        this.telegramToMinecraftFormat = this.getConfig().getString("telegram-to-minecraft-format", "[TG] {sender}: {message}");
        this.minecraftToTelegramFormat = this.getConfig().getString("minecraft-to-telegram-format", "[MC] {sender}: {message}");

        this.loadLanguages();
        this.getServer().getPluginManager().registerEvents(this, this);

        if (botToken != null && !botToken.isEmpty()) {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                this.bot = new TelegramBot();
                botsApi.registerBot(this.bot);
                this.botStartTime = System.currentTimeMillis() / 1000;

                this.chatId = this.bot.getChatId();
                if (this.chatId != null) {
                    this.getConfig().set("telegram.chatId", this.chatId);
                    this.saveConfig();
                    this.getLogger().info("Chat ID автоматически определен: " + this.chatId);
                } else {
                    this.getLogger().warning("Не удалось определить Chat ID. Убедитесь, что бот добавлен в чат и ему отправлено сообщение.");
                }
            } catch (TelegramApiException e) {
                this.getLogger().error("Failed to initialize Telegram bot", e);
            }
        } else {
            this.getLogger().error("Bot token is not set in config.yml!");
        }

        this.checkForUpdates();
    }

    private void loadLanguages() {
        this.languages = new HashMap<>();

        Map<String, String> en = new HashMap<>();
        en.put("online", "Players online: ");
        en.put("join", " joined the game!");
        en.put("quit", " left the game!");
        this.languages.put("en", en);

        Map<String, String> ru = new HashMap<>();
        ru.put("online", "Игроков онлайн: ");
        ru.put("join", " зашел на сервер!");
        ru.put("quit", " вышел с сервера!");
        this.languages.put("ru", ru);

        Map<String, String> ua = new HashMap<>();
        ua.put("online", "Гравців онлайн: ");
        ua.put("join", " зайшов на сервер!");
        ua.put("quit", " вийшов з сервера!");
        this.languages.put("ua", ua);

        Map<String, String> kz = new HashMap<>();
        kz.put("online", "Ойыншылар онлайн: ");
        kz.put("join", " серверге кірді!");
        kz.put("quit", " серверден шықты!");
        this.languages.put("kz", kz);

        Map<String, String> es = new HashMap<>();
        es.put("online", "Jugadores en línea: ");
        es.put("join", " se unió al juego!");
        es.put("quit", " dejó el juego!");
        this.languages.put("es", es);

        Map<String, String> ar = new HashMap<>();
        ar.put("online", "اللاعبون على الإنترنت: ");
        ar.put("join", " انضم إلى اللعبة!");
        ar.put("quit", " غادر اللعبة!");
        this.languages.put("ar", ar);
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        String sender = event.getPlayer().getName();
        String message = event.getMessage();
        String formattedMessage = minecraftToTelegramFormat.replace("{sender}", sender).replace("{message}", message);
        this.bot.sendToTelegram(formattedMessage);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String sender = event.getPlayer().getName();
        String joinMessage = minecraftToTelegramFormat.replace("{sender}", sender).replace("{message}", languages.get(language).get("join"));
        this.bot.sendToTelegram(joinMessage);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String sender = event.getPlayer().getName();
        String quitMessage = minecraftToTelegramFormat.replace("{sender}", sender).replace("{message}", languages.get(language).get("quit"));
        this.bot.sendToTelegram(quitMessage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tg2nk") || command.getName().equalsIgnoreCase("tg2nukkit")) {
            if (args.length == 0) {
                sender.sendMessage("§aTG2Nukkit Menu:");
                sender.sendMessage("§b/tg2nk language §7- Change language");
                sender.sendMessage("§b/tg2nk format §7- Change message format");
                sender.sendMessage("§b/tg2nk update §7- Check for updates");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "language":
                    if (args.length == 2 && this.languages.containsKey(args[1])) {
                        this.language = args[1];
                        this.getConfig().set("language", args[1]);
                        this.saveConfig();
                        sender.sendMessage("§aLanguage set to " + args[1]);
                    } else {
                        sender.sendMessage("§cUsage: /tg2nk language <ru|en|es|uk|kk|ar>");
                    }
                    return true;

                case "format":
                    if (args.length == 3) {
                        String formatType = args[1].toLowerCase();
                        String newFormat = args[2];

                        if (formatType.equals("telegram") || formatType.equals("minecraft")) {
                            if (formatType.equals("telegram")) {
                                this.telegramToMinecraftFormat = newFormat;
                                this.getConfig().set("telegram-to-minecraft-format", newFormat);
                            } else {
                                this.minecraftToTelegramFormat = newFormat;
                                this.getConfig().set("minecraft-to-telegram-format", newFormat);
                            }

                            this.saveConfig();
                            sender.sendMessage("§aFormat updated successfully!");
                        } else {
                            sender.sendMessage("§cUsage: /tg2nk format <telegram|minecraft> <new_format>");
                        }
                    } else {
                        sender.sendMessage("§cUsage: /tg2nk format <telegram|minecraft> <new_format>");
                    }
                    return true;

                case "update":
                    this.checkForUpdates();
                    return true;

                default:
                    sender.sendMessage("§cUnknown command. Use /tg2nk for help.");
                    return true;
            }
        }
        return false;
    }

    private void checkForUpdates() {
        try {
            URL url = new URL("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(response.toString());
            String latestVersion = json.getString("tag_name").replace("v", "");
            String currentVersion = this.getDescription().getVersion();

            if (isNewerVersion(latestVersion, currentVersion)) {
                String updateMessage = "§cA new version of TG2Nukkit is available: v" + latestVersion + " (you have v" + currentVersion + "). " +
                        "Download it from: https://github.com/" + GITHUB_REPO + "/releases/latest";
                this.getLogger().warning(updateMessage);

                if (bot != null) {
                    bot.sendToTelegram(updateMessage);
                }
            } else {
                this.getLogger().info("TG2Nukkit is up to date!");
            }
        } catch (Exception e) {
            this.getLogger().error("Failed to check for updates: " + e.getMessage());
        }
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
        String[] latestParts = latestVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");

        for (int i = 0; i < Math.min(latestParts.length, currentParts.length); i++) {
            int latest = Integer.parseInt(latestParts[i]);
            int current = Integer.parseInt(currentParts[i]);

            if (latest > current) {
                return true;
            } else if (latest < current) {
                return false;
            }
        }

        return latestParts.length > currentParts.length;
    }

    private class TelegramBot extends TelegramLongPollingBot {

        @Override
        public void onUpdateReceived(Update update) {
            if (update.hasMessage() && update.getMessage().hasText()) {
                long messageDate = update.getMessage().getDate();
                if (messageDate >= botStartTime) {
                    String text = update.getMessage().getText();
                    if (text.equalsIgnoreCase("/online")) {
                        String onlineMessage = languages.get(language).get("online") + getServer().getOnlinePlayers().size();
                        this.sendToChat(onlineMessage);
                    } else {
                        String sender = update.getMessage().getFrom().getUserName();
                        String message = telegramToMinecraftFormat.replace("{sender}", sender).replace("{message}", text);
                        getServer().broadcastMessage(message);
                    }
                }
            }
        }

        @Override
        public String getBotUsername() {
            return "TG2NukkitBot";
        }

        @Override
        public String getBotToken() {
            return botToken;
        }

        public void sendToTelegram(String message) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(message);
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                getLogger().error("Failed to send message to Telegram", e);
            }
        }

        public void sendToChat(String message) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(message);
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                getLogger().error("Failed to send message to Telegram", e);
            }
        }

        public String getChatId() {
            try {
                List<Update> updates = this.execute(new GetUpdates());
                for (Update update : updates) {
                    if (update.hasMessage()) {
                        return update.getMessage().getChatId().toString();
                    }
                }
            } catch (TelegramApiException e) {
                getLogger().error("Failed to get chat ID", e);
            }
            return null;
        }
    }
}