package fi.matiaspaavilainen.masuitechat.bungee;

import fi.matiaspaavilainen.masuitechat.bungee.channels.*;
import fi.matiaspaavilainen.masuitechat.bungee.objects.Group;
import fi.matiaspaavilainen.masuitechat.bungee.managers.MailManager;
import fi.matiaspaavilainen.masuitechat.bungee.managers.ServerManager;
import fi.matiaspaavilainen.masuitecore.bungee.Utils;
import fi.matiaspaavilainen.masuitecore.bungee.chat.Formator;
import fi.matiaspaavilainen.masuitecore.core.Updator;
import fi.matiaspaavilainen.masuitecore.core.configuration.BungeeConfiguration;
import fi.matiaspaavilainen.masuitecore.core.database.ConnectionManager;
import fi.matiaspaavilainen.masuitecore.core.objects.MaSuitePlayer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class MaSuiteChat extends Plugin implements Listener {

    public static HashMap<UUID, String> players = new HashMap<>();
    public static HashMap<UUID, Group> groups = new HashMap<>();
    public static boolean luckPermsApi = false;
    private Formator formator = new Formator();

    private Utils utils = new Utils();

    private BungeeConfiguration config = new BungeeConfiguration();
    private ConnectionManager cm = null;

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);

        // Create configs
        config.create(this, "chat", "messages.yml");
        config.create(this, "chat", "chat.yml");

        // Database
        Configuration dbInfo = config.load(null, "config.yml");
        cm = new ConnectionManager(dbInfo.getString("database.table-prefix"), dbInfo.getString("database.address"), dbInfo.getInt("database.port"), dbInfo.getString("database.name"), dbInfo.getString("database.username"), dbInfo.getString("database.password"));
        cm.connect();
        cm.getDatabase().createTable("mail", "(" +
                "id INT(10) UNSIGNED PRIMARY KEY AUTO_INCREMENT, " +
                "sender VARCHAR(36) NOT NULL, " +
                "receiver VARCHAR(36) NOT NULL, " +
                "message LONGTEXT NOT NULL, " +
                "seen TINYINT(1) NOT NULL DEFAULT '0', " +
                "timestamp BIGINT(16) NOT NULL" +
                ");");



        // Load actions, servers and channels
        ServerManager.loadServers();

        new Updator(new String[]{getDescription().getVersion(), getDescription().getName(), "60039"}).checkUpdates();
        if (getProxy().getPluginManager().getPlugin("LuckPerms") != null) {
            luckPermsApi = true;
        }
    }

    @Override
    public void onDisable() {
        cm.close();
    }

    @EventHandler
    public void onJoin(PostLoginEvent e) {
        ProxiedPlayer p = e.getPlayer();
        // Add player to global channel on join
        players.put(p.getUniqueId(), "global");
    }

    @EventHandler
    public void onQuit(PlayerDisconnectEvent e) {
        ProxiedPlayer p = e.getPlayer();
        // Remove player from channels on leave
        players.remove(p.getUniqueId());
    }

    @Override
    public void onLoad() {
        getProxy().getPlayers().forEach(p -> {
            players.put(p.getUniqueId(), "global");
            formator.sendMessage(p, config.load("chat", "messages.yml").getString("channel-changed.global"));
        });
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent e) throws IOException {
        BungeeConfiguration config = new BungeeConfiguration();
        Local localChannel = new Local(this);
        Private privateChannel = new Private();
        if (e.getTag().equals("BungeeCord")) {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(e.getData()));
            String subchannel = in.readUTF();
            if (subchannel.equals("MaSuiteChat")) {
                String childchannel = in.readUTF();
                if (childchannel.equals("Chat")) {
                    ProxiedPlayer p = ProxyServer.getInstance().getPlayer(UUID.fromString(in.readUTF()));
                    if (p == null) {
                        return;
                    }
                    if (players.containsKey(p.getUniqueId())) {
                        switch (players.get(p.getUniqueId())) {
                            case ("staff"):
                                Staff.sendMessage(p, in.readUTF());
                                break;
                            case ("global"):
                                Global.sendMessage(p, in.readUTF());
                                break;
                            case ("server"):
                                Server.sendMessage(p, in.readUTF());
                                break;
                            case ("local"):
                                String msg = in.readUTF();
                                localChannel.send(p, msg);
                                break;
                        }
                    }
                }
                if (childchannel.equals("ToggleChannel")) {
                    String channel = in.readUTF();
                    ProxiedPlayer p = getProxy().getPlayer(UUID.fromString(in.readUTF()));
                    if (p != null) {
                        switch (channel) {
                            case ("staff"):
                                players.put(p.getUniqueId(), "staff");
                                formator.sendMessage(p, config.load("chat", "messages.yml").getString("channel-changed.staff"));
                                break;
                            case ("global"):
                                players.put(p.getUniqueId(), "global");
                                formator.sendMessage(p, config.load("chat", "messages.yml").getString("channel-changed.global"));
                                break;
                            case ("server"):
                                players.put(p.getUniqueId(), "server");
                                formator.sendMessage(p, config.load("chat", "messages.yml").getString("channel-changed.server"));
                                break;
                            case ("local"):
                                players.put(p.getUniqueId(), "local");
                                formator.sendMessage(p, config.load("chat", "messages.yml").getString("channel-changed.local"));
                                break;
                        }
                    }

                }
                if (childchannel.equals("SendMessage")) {
                    String channel = in.readUTF();
                    ProxiedPlayer p = getProxy().getPlayer(UUID.fromString(in.readUTF()));
                    String value = in.readUTF();
                    if (p != null) {
                        switch (channel) {
                            case ("staff"):
                                Staff.sendMessage(p, value);
                                break;
                            case ("global"):
                                Global.sendMessage(p, value);
                                break;
                            case ("server"):
                                Server.sendMessage(p, value);
                                break;
                            case ("local"):
                                localChannel.send(p, value);
                                break;
                            case ("private"):
                                ProxiedPlayer receiver = getProxy().getPlayer(value);
                                if (utils.isOnline(receiver, p)) {
                                    privateChannel.sendMessage(p, receiver, in.readUTF());
                                }
                                break;
                            case ("reply"):
                                if (Private.conversations.containsKey(p.getUniqueId())) {
                                    ProxiedPlayer r = ProxyServer.getInstance().getPlayer(Private.conversations.get(p.getUniqueId()));
                                    if (utils.isOnline(r, p)) {
                                        privateChannel.sendMessage(p, r, value);
                                    }
                                }
                                break;
                        }
                    }

                }
                if (childchannel.equals("Mail")) {
                    String superchildchannel = in.readUTF();
                    MailManager mm = new MailManager();
                    switch (superchildchannel) {
                        case ("Send"):
                            mm.send(in.readUTF(), in.readUTF(), in.readUTF());
                            break;
                        case ("SendAll"):
                            mm.sendAll(in.readUTF(), in.readUTF());
                            break;
                        case ("Read"):
                            mm.read(in.readUTF());
                            break;
                    }

                }

                if (childchannel.equals("Nick")) {
                    ProxiedPlayer sender = ProxyServer.getInstance().getPlayer(UUID.fromString(in.readUTF()));
                    String nick = in.readUTF();
                    if (utils.isOnline(sender)) {
                        sender.setDisplayName(nick);
                        MaSuitePlayer msp = new MaSuitePlayer();
                        msp = msp.find(sender.getUniqueId());
                        msp.setNickname(nick);
                        msp.update();
                        formator.sendMessage(sender, config.load("chat", "messages.yml").getString("nickname-changed").replace("%nickname%", nick));
                    }
                }

                if (childchannel.equals("NickOther")) {
                    ProxiedPlayer sender = ProxyServer.getInstance().getPlayer(UUID.fromString(in.readUTF()));
                    ProxiedPlayer target = ProxyServer.getInstance().getPlayer(in.readUTF());
                    String nick = in.readUTF();
                    if (utils.isOnline(target, sender)) {
                        target.setDisplayName(in.readUTF());
                        MaSuitePlayer msp = new MaSuitePlayer();
                        msp = msp.find(target.getUniqueId());
                        msp.setNickname(nick);
                        msp.update();
                        formator.sendMessage(sender, config.load("chat", "messages.yml").getString("nickname-changed").replace("%nickname%", nick));
                    }

                }
                if (childchannel.equals("ResetNick")) {
                    ProxiedPlayer sender = ProxyServer.getInstance().getPlayer(UUID.fromString(in.readUTF()));
                    if (utils.isOnline(sender)) {
                        updateNick(config, sender);
                    }

                }
                if (childchannel.equals("ResetNickOther")) {
                    ProxiedPlayer sender = ProxyServer.getInstance().getPlayer(UUID.fromString(in.readUTF()));
                    ProxiedPlayer target = ProxyServer.getInstance().getPlayer(in.readUTF());
                    if (utils.isOnline(target, sender)) {
                        updateNick(config, target);
                    }
                }

                if (childchannel.equals("SetGroup")) {
                    UUID uuid = UUID.fromString(in.readUTF());
                    groups.put(uuid, new Group(uuid, in.readUTF(), in.readUTF()));
                }
            }
        }
    }

    private void updateNick(BungeeConfiguration config, ProxiedPlayer target) {
        target.setDisplayName(target.getName());
        MaSuitePlayer msp = new MaSuitePlayer();
        msp = msp.find(target.getUniqueId());
        msp.setNickname(null);
        msp.update();
        formator.sendMessage(target, config.load("chat", "messages.yml").getString("nickname-changed").replace("%nickname%", target.getName()));
    }
}