/* This file is part of Vault.

    Vault is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Vault is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Vault.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.milkbowl.vault;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.IdentityEconomy;
import net.milkbowl.vault.economy.LegacyEconomy;
import net.milkbowl.vault.economy.wrappers.EconomyWrapper;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class Vault extends JavaPlugin {

    private static final String VAULT_BUKKIT_URL = "https://dev.bukkit.org/projects/Vault";
    private static Logger log;
    private String newVersionTitle = "";
    private double newVersion = 0;
    private double currentVersion = 0;
    private String currentVersionTitle = "";
    private ServicesManager sm;
    private Vault plugin;

    @Override
    public void onDisable() {
        // Remove all Service Registrations
        getServer().getServicesManager().unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
    }

    @Override
    public void onEnable() {
        plugin = this;
        log = this.getLogger();
        currentVersionTitle = getDescription().getVersion().split("-")[0];
        currentVersion = Double.valueOf(currentVersionTitle.replaceFirst("\\.", ""));
        sm = getServer().getServicesManager();
        // set defaults
        getConfig().addDefault("update-check", true);
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadPermission();

        getCommand("vault-info").setExecutor(this);
        getCommand("vault-convert").setExecutor(this);
        new LegacyEconomyListener(this);
        // Schedule to check the version every 30 minutes for an update. This is to update the most recent 
        // version so if an admin reconnects they will be warned about newer versions.
        this.getServer().getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                // Programmatically set the default permission value cause Bukkit doesn't handle plugin.yml properly for Load order STARTUP plugins
                org.bukkit.permissions.Permission perm = getServer().getPluginManager().getPermission("vault.update");
                if (perm == null) {
                    perm = new org.bukkit.permissions.Permission("vault.update");
                    perm.setDefault(PermissionDefault.OP);
                    plugin.getServer().getPluginManager().addPermission(perm);
                }
                perm.setDescription("Allows a user or the console to check for vault updates");

                getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

                    @Override
                    public void run() {
                        if (getServer().getConsoleSender().hasPermission("vault.update") && getConfig().getBoolean("update-check", true)) {
                            try {
                                log.info("Checking for Updates ... ");
                                newVersion = updateCheck(currentVersion);
                                if (newVersion > currentVersion) {
                                    log.warning("Stable Version: " + newVersionTitle + " is out!" + " You are still running version: " + currentVersionTitle);
                                    log.warning("Update at: https://dev.bukkit.org/projects/vault");
                                } else if (currentVersion > newVersion) {
                                    log.info("Stable Version: " + newVersionTitle + " | Current Version: " + currentVersionTitle);
                                }
                            } catch (Exception e) {
                                // ignore exceptions
                            }
                        }
                    }
                }, 0, 432000);

            }
        });

        // Load up the Plugin metrics
        Metrics metrics = new Metrics(this, 887);
        findCustomData(metrics);

        log.info(String.format("Enabled Version %s", getDescription().getVersion()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (!sender.hasPermission("vault.admin")) {
            sender.sendMessage("You do not have permission to use that command!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("vault-info")) {
            infoCommand(sender);
            return true;
        } else if (command.getName().equalsIgnoreCase("vault-convert")) {
            convertCommand(sender, args);
            return true;
        } else {
            // Show help
            sender.sendMessage("Vault Commands:");
            sender.sendMessage("  /vault-info - Displays information about Vault");
            sender.sendMessage("  /vault-convert [economy1] [economy2] - Converts from one Economy to another");
            return true;
        }
    }

    private void convertCommand(CommandSender sender, String[] args) {
        Collection<RegisteredServiceProvider<Economy>> econs = this.getServer().getServicesManager().getRegistrations(Economy.class);
        if (econs == null || econs.size() < 2) {
            sender.sendMessage("You must have at least 2 economies loaded to convert.");
            return;
        } else if (args.length != 2) {
            sender.sendMessage("You must specify only the economy to convert from and the economy to convert to. (names should not contain spaces)");
            return;
        }
        Economy econ1 = null;
        Economy econ2 = null;
        String economies = "";
        for (RegisteredServiceProvider<Economy> econ : econs) {
            String econName = econ.getProvider().getName().replace(" ", "");
            if (econName.equalsIgnoreCase(args[0])) {
                econ1 = econ.getProvider();
            } else if (econName.equalsIgnoreCase(args[1])) {
                econ2 = econ.getProvider();
            }
            if (economies.length() > 0) {
                economies += ", ";
            }
            economies += econName;
        }

        if (econ1 == null) {
            sender.sendMessage("Could not find " + args[0] + " loaded on the server, check your spelling.");
            sender.sendMessage("Valid economies are: " + economies);
            return;
        } else if (econ2 == null) {
            sender.sendMessage("Could not find " + args[1] + " loaded on the server, check your spelling.");
            sender.sendMessage("Valid economies are: " + economies);
            return;
        }

        sender.sendMessage("This may take some time to convert, expect server lag.");
        for (OfflinePlayer op : Bukkit.getServer().getOfflinePlayers()) {
            if (econ1.hasAccount(op)) {
                if (econ2.hasAccount(op)) {
                    continue;
                }
                econ2.createPlayerAccount(op);
                double diff = econ1.getBalance(op) - econ2.getBalance(op);
                if (diff > 0) {
                    econ2.depositPlayer(op, diff);
                } else if (diff < 0) {
                    econ2.withdrawPlayer(op, -diff);
                }

            }
        }
        sender.sendMessage("Conversion complete, please verify the data before using it.");
    }

    private void infoCommand(CommandSender sender) {
        // Get String of Registered Economy Services
        String registeredEcons = null;
        Collection<RegisteredServiceProvider<Economy>> econs = this.getServer().getServicesManager().getRegistrations(Economy.class);
        for (RegisteredServiceProvider<Economy> econ : econs) {
            Economy e = econ.getProvider();
            if (registeredEcons == null) {
                registeredEcons = e.getName();
            } else {
                registeredEcons += ", " + e.getName();
            }
        }

        // Get String of Registered Permission Services
        String registeredPerms = null;
        Collection<RegisteredServiceProvider<Permission>> perms = this.getServer().getServicesManager().getRegistrations(Permission.class);
        for (RegisteredServiceProvider<Permission> perm : perms) {
            Permission p = perm.getProvider();
            if (registeredPerms == null) {
                registeredPerms = p.getName();
            } else {
                registeredPerms += ", " + p.getName();
            }
        }

        String registeredChats = null;
        Collection<RegisteredServiceProvider<Chat>> chats = this.getServer().getServicesManager().getRegistrations(Chat.class);
        for (RegisteredServiceProvider<Chat> chat : chats) {
            Chat c = chat.getProvider();
            if (registeredChats == null) {
                registeredChats = c.getName();
            } else {
                registeredChats += ", " + c.getName();
            }
        }

        // Get Economy & Permission primary Services
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        Economy econ = null;
        if (rsp != null) {
            econ = rsp.getProvider();
        }
        Permission perm = null;
        RegisteredServiceProvider<Permission> rspp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rspp != null) {
            perm = rspp.getProvider();
        }
        Chat chat = null;
        RegisteredServiceProvider<Chat> rspc = getServer().getServicesManager().getRegistration(Chat.class);
        if (rspc != null) {
            chat = rspc.getProvider();
        }
        // Send user some info!
        sender.sendMessage(String.format("[%s] Vault v%s Information", getDescription().getName(), getDescription().getVersion()));
        sender.sendMessage(String.format("[%s] Economy: %s [%s]", getDescription().getName(), econ == null ? "None" : econ.getName(), registeredEcons));
        sender.sendMessage(String.format("[%s] Permission: %s [%s]", getDescription().getName(), perm == null ? "None" : perm.getName(), registeredPerms));
        sender.sendMessage(String.format("[%s] Chat: %s [%s]", getDescription().getName(), chat == null ? "None" : chat.getName(), registeredChats));
    }

    public double updateCheck(double currentVersion) {
        try {
            URL url = new URL("https://api.curseforge.com/servermods/files?projectids=33184");
            URLConnection conn = url.openConnection();
            conn.setReadTimeout(5000);
            conn.addRequestProperty("User-Agent", "Vault Update Checker");
            conn.setDoOutput(true);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            final String response = reader.readLine();
            final JSONArray array = (JSONArray) JSONValue.parse(response);

            if (array.size() == 0) {
                this.getLogger().warning("No files found, or Feed URL is bad.");
                return currentVersion;
            }
            // Pull the last version from the JSON
            newVersionTitle = ((String) ((JSONObject) array.get(array.size() - 1)).get("name")).replace("Vault", "").trim();
            return Double.valueOf(newVersionTitle.replaceFirst("\\.", "").trim());
        } catch (Exception e) {
            log.info("There was an issue attempting to check for the latest version.");
        }
        return currentVersion;
    }

    /**
     * Attempts to load Permission Addons
     */
    private void loadPermission() {
        Permission perms = new SuperPerms(this);
        sm.register(Permission.class, perms, this, ServicePriority.Lowest);
        log.info("[Permission] SuperPermissions loaded as backup permission system.");

        Permission perms1 = sm.getRegistration(Permission.class).getProvider();
    }

    private void findCustomData(Metrics metrics) {
        // Create our Economy Graph and Add our Economy plotters
        RegisteredServiceProvider<Economy> rspEcon = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        Economy econ = null;
        if (rspEcon != null) {
            econ = rspEcon.getProvider();
        }
        final String econName = econ != null ? econ.getName() : "No Economy";
        metrics.addCustomChart(new SimplePie("economy", new Callable<String>() {
            @Override
            public String call() {
                return econName;
            }
        }));

        // Create our Permission Graph and Add our permission Plotters
        final String permName = Bukkit.getServer().getServicesManager().getRegistration(Permission.class).getProvider().getName();
        metrics.addCustomChart(new SimplePie("permission", new Callable<String>() {
            @Override
            public String call() {
                return permName;
            }
        }));

        // Create our Chat Graph and Add our chat Plotters
        RegisteredServiceProvider<Chat> rspChat = Bukkit.getServer().getServicesManager().getRegistration(Chat.class);
        Chat chat = null;
        if (rspChat != null) {
            chat = rspChat.getProvider();
        }
        final String chatName = chat != null ? chat.getName() : "No Chat";
        metrics.addCustomChart(new SimplePie("chat", new Callable<String>() {
            @Override
            public String call() {
                return chatName;
            }
        }));
    }

    public static class LegacyEconomyListener implements Listener {
        private final Vault vault;
        private LegacyEconomy legacyProvider;

        private LegacyEconomyListener(Vault vault) {
            vault.getServer().getPluginManager().registerEvents(this, vault);
            this.vault = vault;
            RegisteredServiceProvider<Economy> legacyProvider = vault.getServer().getServicesManager().getRegistration(Economy.class);
            if (legacyProvider == null)
                return;
            RegisteredServiceProvider<IdentityEconomy> identityProvider = vault.getServer().getServicesManager().getRegistration(IdentityEconomy.class);
            if (identityProvider != null)
                return;
            EconomyWrapper wrapper = new EconomyWrapper(legacyProvider.getProvider());
            this.legacyProvider = wrapper.legacy();
            Bukkit.getLogger().info(wrapper.legacy().getName() + " has been provided with a legacy wrapper.");
            Bukkit.getServicesManager().register(IdentityEconomy.class, this.legacyProvider, vault, ServicePriority.Lowest);
        }

        @SuppressWarnings("unchecked")
        @EventHandler
        public void onLegacyRegister(ServiceRegisterEvent event) {
            RegisteredServiceProvider<?> eventProvider = event.getProvider();
            if (!eventProvider.getService().equals(Economy.class))
                return;
            RegisteredServiceProvider<Economy> legacyProvider = (RegisteredServiceProvider<Economy>) eventProvider;
            RegisteredServiceProvider<IdentityEconomy> identityProvider = vault.getServer().getServicesManager().getRegistration(IdentityEconomy.class);
            if (identityProvider != null)
                return;
            EconomyWrapper wrapper = new EconomyWrapper(legacyProvider.getProvider());
            this.legacyProvider = wrapper.legacy();
            Bukkit.getLogger().severe(ChatColor.RED + wrapper.legacy().getName() + " has been provided with a legacy wrapper.");
            Bukkit.getServicesManager().register(IdentityEconomy.class, this.legacyProvider, vault, ServicePriority.Lowest);
        }

        @EventHandler
        public void onUnregister(ServiceUnregisterEvent event) {
            RegisteredServiceProvider<?> eventProvider = event.getProvider();
            if (eventProvider.getService() != Economy.class)
                return;
            Bukkit.getServicesManager().unregister(IdentityEconomy.class, legacyProvider);
        }
    }
}
