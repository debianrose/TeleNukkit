package com.debianrose.TeleNukkit;

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
import org.json.JSONObject;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;

import java.util.HashMap;
import java.util.Map;

public class Main extends PluginBase implements Listener {
    private static final String GITHUB_REPO = "debianrose/TeleNukkit";
    private BridgeManager bridgeManager;
    private String language;
    private Map<String, LanguagePack> languages;
    private boolean checkUpdates;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        
        initLanguages();
        
        language = getConfig().getString("language", "en");
        checkUpdates = getConfig().getBoolean("check-for-updates", true);
        
        bridgeManager = new BridgeManager(this);
        
        getServer().getPluginManager().registerEvents(this, this);
        
        if (checkUpdates) {
            new UpdateChecker(this).check();
        }
    }

    private void initLanguages() {
        languages = new HashMap<>();
        languages.put("en", new LanguagePack("Players online: ", " joined the game!", " left the game!"));
        languages.put("ru", new LanguagePack("Игроков онлайн: ", " зашел на сервер!", " вышел с сервера!"));
        languages.put("ua", new LanguagePack("Гравців онлайн: ", " зайшов на сервер!", " вийшов з сервера!"));
        languages.put("kz", new LanguagePack("Ойыншылар онлайн: ", " серверге кірді!", " серверден шықты!"));
        languages.put("es", new LanguagePack("Jugadores en línea: ", " se unió al juego!", " dejó el juego!"));
        languages.put("ar", new LanguagePack("اللاعبون على الإنترنت: ", " انضم إلى اللعبة!", " غادر اللعبة!"));
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), event.getMessage());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), languages.get(language).join);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bridgeManager.sendToBridges("minecraft", event.getPlayer().getName(), languages.get(language).quit);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setlanguage")) {
            if (args.length == 1 && languages.containsKey(args[0])) {
                language = args[0];
                getConfig().set("language", args[0]);
                saveConfig();
                sender.sendMessage("Language set to " + args[0]);
                return true;
            }
            sender.sendMessage("Usage: /setlanguage <ru|en|es|uk|kk|ar>");
            return true;
        }
        return false;
    }

    public String getLanguage() {
        return language;
    }

    public LanguagePack getLanguagePack() {
        return languages.get(language);
    }

    public BridgeManager getBridgeManager() {
        return bridgeManager;
    }

    private static class LanguagePack {
        public final String online;
        public final String join;
        public final String quit;

        public LanguagePack(String online, String join, String quit) {
            this.online = online;
            this.join = join;
            this.quit = quit;
        }
    }
}

class BridgeManager {
    private final Main plugin;
    private TelegramBridge telegramBridge;
    private MatrixBridge matrixBridge;
    
    public BridgeManager(Main plugin) {
        this.plugin = plugin;
        
        String telegramToken = plugin.getConfig().getString("telegram.botToken");
        if (telegramToken != null && !telegramToken.isEmpty()) {
            telegramBridge = new TelegramBridge(plugin, telegramToken);
        }
        
        JSONObject matrixConfig = plugin.getConfig().getObject("matrix");
        if (matrixConfig != null) {
            matrixBridge = new MatrixBridge(plugin, matrixConfig);
        }
    }
    
    public void sendToBridges(String source, String sender, String message) {
        String format = plugin.getConfig().getString(source + "-to-bridge-format", 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        
        String formatted = format.replace("{sender}", sender).replace("{message}", message);
        
        if (telegramBridge != null) {
            telegramBridge.sendMessage(formatted);
        }
        
        if (matrixBridge != null) {
            matrixBridge.sendMessage(formatted);
        }
    }
    
    public void sendToMinecraft(String source, String sender, String message) {
        String format = plugin.getConfig().getString(source + "-to-minecraft-format", 
            "[" + source.toUpperCase() + "] {sender}: {message}");
        
        String formatted = format.replace("{sender}", sender).replace("{message}", message);
        plugin.getServer().broadcastMessage(formatted);
    }
}

class TelegramBridge extends TelegramLongPollingBot {
    private final Main plugin;
    private final String token;
    
    public TelegramBridge(Main plugin, String token) {
        this.plugin = plugin;
        this.token = token;
        
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            plugin.getLogger().info("Telegram bot connected successfully!");
        } catch (TelegramApiException e) {
            plugin.getLogger().error("Failed to initialize Telegram bot", e);
        }
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            String sender = update.getMessage().getFrom().getUserName();
            
            if (text.equalsIgnoreCase("/online")) {
                sendMessage(plugin.getLanguagePack().online + plugin.getServer().getOnlinePlayers().size());
            } else {
                plugin.getBridgeManager().sendToMinecraft("telegram", sender, text);
            }
        }
    }
    
    @Override
    public String getBotUsername() {
        return "TeleNukkitBot";
    }
    
    @Override
    public String getBotToken() {
        return token;
    }
    
    public void sendMessage(String message) {
        try {
            execute(new SendMessage("@your_channel", message));
        } catch (TelegramApiException e) {
            plugin.getLogger().error("Failed to send Telegram message", e);
        }
    }
}

class MatrixBridge {
    private final Main plugin;
    private MXSession session;
    
    public MatrixBridge(Main plugin, JSONObject config) {
        this.plugin = plugin;
        
        try {
            String homeserver = config.getString("homeserver");
            String username = config.getString("username");
            String password = config.getString("password");
            String roomId = config.getString("roomId");
            
            MXDataHandler dataHandler = new MXDataHandler();
            session = new MXSession(dataHandler, homeserver);
            session.login(username, password, new MXEventListener() {
                @Override
                public void onLogin() {
                    plugin.getLogger().info("Connected to Matrix server!");
                    
                    Room room = session.getDataHandler().getRoom(roomId);
                    room.addEventListener(new MXEventListener() {
                        @Override
                        public void onLiveEvent(Event event, RoomState state) {
                            if (event.getType().equals(Event.EVENT_TYPE_MESSAGE)) {
                                Message message = JsonUtils.toMessage(event.getContent());
                                String sender = event.getSender();
                                plugin.getBridgeManager().sendToMinecraft("matrix", sender, message.body);
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            plugin.getLogger().error("Failed to initialize Matrix bridge", e);
        }
    }
    
    public void sendMessage(String message) {
        if (session != null && session.isAlive()) {
            try {
                Room room = session.getDataHandler().getRoom("@your_room:matrix.org");
                room.sendTextMessage(message);
            } catch (Exception e) {
                plugin.getLogger().error("Failed to send Matrix message", e);
            }
        }
    }
}

class UpdateChecker {
    private final Main plugin;
    
    public UpdateChecker(Main plugin) {
        this.plugin = plugin;
    }
    
    public void check() {
        plugin.getLogger().info("Checking for updates...");
    }
}
