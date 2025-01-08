package com.debianrose; //Privet! my telegram is - fxsharic

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
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.HashMap;
import java.util.Map;

public class Main extends PluginBase implements Listener {

    private TelegramBot bot;
    private String botToken;
    private String chatId;
    private String language;
    private Map<String, Map<String, String>> languages;
    private long botStartTime;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.botToken = this.getConfig().getString("telegram.botToken");
        this.chatId = this.getConfig().getString("telegram.chatId");
        this.language = this.getConfig().getString("language", "en");

        this.loadLanguages();
        this.getServer().getPluginManager().registerEvents(this, this);

        if (botToken != null && !botToken.isEmpty()) {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                this.bot = new TelegramBot();
                botsApi.registerBot(this.bot);
                this.botStartTime = System.currentTimeMillis() / 1000;
            } catch (TelegramApiException e) {
                this.getLogger().error("Failed to initialize Telegram bot", e);
            }
        } else {
            this.getLogger().error("Bot token is not set in config.yml!");
        }
    }

    private void loadLanguages() {
        this.languages = new HashMap<>();

        // Английский
        Map<String, String> en = new HashMap<>();
        en.put("online", "Players online: ");
        en.put("join", " joined the game!");
        en.put("quit", " left the game!");
        this.languages.put("en", en);

        // Русский
        Map<String, String> ru = new HashMap<>();
        ru.put("online", "Игроков онлайн: ");
        ru.put("join", " зашел на сервер!");
        ru.put("quit", " вышел с сервера!");
        this.languages.put("ru", ru);

        // Украинский
        Map<String, String> ua = new HashMap<>();
        ua.put("online", "Гравців онлайн: ");
        ua.put("join", " зайшов на сервер!");
        ua.put("quit", " вийшов з сервера!");
        this.languages.put("ua", ua);

        // Казахский
        Map<String, String> kz = new HashMap<>();
        kz.put("online", "Ойыншылар онлайн: ");
        kz.put("join", " серверге кірді!");
        kz.put("quit", " серверден шықты!");
        this.languages.put("kz", kz);

        // Испанский
        Map<String, String> es = new HashMap<>();
        es.put("online", "Jugadores en línea: ");
        es.put("join", " se unió al juego!");
        es.put("quit", " dejó el juego!");
        this.languages.put("es", es);

        // Арабский
        Map<String, String> ar = new HashMap<>();
        ar.put("online", "اللاعبون على الإنترنت: ");
        ar.put("join", " انضم إلى اللعبة!");
        ar.put("quit", " غادر اللعبة!");
        this.languages.put("ar", ar);
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        String message = event.getMessage();
        this.bot.sendToTelegram(event.getPlayer().getName() + ": " + message);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String joinMessage = event.getPlayer().getName() + languages.get(language).get("join");
        this.bot.sendToTelegram(joinMessage);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String quitMessage = event.getPlayer().getName() + languages.get(language).get("quit");
        this.bot.sendToTelegram(quitMessage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setlanguage")) {
            if (args.length == 1 && this.languages.containsKey(args[0])) {
                this.language = args[0];
                this.getConfig().set("language", args[0]);
                this.saveConfig();
                sender.sendMessage("Language set to " + args[0]);
            } else {
                sender.sendMessage("Usage: /setlanguage <ru|en|es|uk|kk|ar>");
            }
            return true;
        }
        return false;
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
                        String message = "[Telegram] " + update.getMessage().getFrom().getUserName() + ": " + text;
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
    }
}
