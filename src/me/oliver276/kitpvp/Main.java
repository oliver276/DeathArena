package me.oliver276.kitpvp;

import com.google.common.eventbus.DeadEvent;
import com.mysql.jdbc.Buffer;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class Main extends JavaPlugin implements Listener{

    private HashMap<String, ItemStack[]> mySavedItems = new HashMap<String, ItemStack[]>();

    private HashMap<String, ItemStack[]> MySavedAmour = new HashMap<String, ItemStack[]>();

    public void saveInventory(Player player){

        this.mySavedItems.put(player.getName(), copyInventory(player.getInventory()));
        this.MySavedAmour.put(player.getName(), player.getInventory().getArmorContents());
    }

    /**
     * This removes the saved inventory from our HashMap, and restores it to the player if it existed.
     * return true iff success
     */
    public boolean restoreInventory(Player player){

        ItemStack[] savedInventory = this.mySavedItems.remove(player.getName());
        if(savedInventory == null)
            return false;
        restoreInventory(player, savedInventory);

        ItemStack[] savedArmour = this.MySavedAmour.remove(player.getName());
        if(savedArmour != null){
            player.getInventory().setArmorContents(savedArmour);
        }
        return true;
    }

    private ItemStack[] copyInventory(Inventory inv){

        ItemStack[] original = inv.getContents();
        ItemStack[] copy = original.clone();

        return copy;
    }

    private void restoreInventory(Player p, ItemStack[] inventory)
    {
        p.getInventory().setContents(inventory);
    }


    private HashMap<String,ItemStack[]> kit = new HashMap<String, ItemStack[]>();
    private HashMap<String,ItemStack[]> kitarm = new HashMap<String, ItemStack[]>();
    private HashMap<String,String> LastKit = new HashMap<String, String>();
    private HashMap<String,Collection<PotionEffect>> kitEffects = new HashMap<String, Collection<PotionEffect>>();
    private ArrayList<String> kits = new ArrayList<String>();
    private Location ArenaSpawn = null;
    private HashMap<String,Location> PreviousPos = new HashMap<String, Location>();
    private HashMap<String,Double> PrevHealth = new HashMap<String, Double>();
    public boolean hasEconomy = false;
    private ArrayList<Player> immuneList = new ArrayList<Player>();
    private Scoreboard sboard = Bukkit.getScoreboardManager().getNewScoreboard();
    private Objective objective;
    private List<String> kitlis = null;

    public ArrayList<Player> inGame = new ArrayList<Player>();

    public void Leave(Player p){
        String msg;
        try {
            msg = getConfig().getString("LeaveMessage");
        } catch (Exception ex) {
            msg = "&6You left KitPvP!";
        }
        inGame.remove(p);
        p.sendMessage(msg.replaceAll("&","§"));
        Location location = PreviousPos.get(p.getName());
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.teleport(location);
        restoreInventory(p);
        p.setHealth(PrevHealth.remove(p.getName()));
        LastKit.remove(p.getName());
        PreviousPos.remove(p.getName());
        for (PotionEffectType potionEffect : PotionEffectType.values()){
            try{
                p.removePotionEffect(potionEffect);
            }catch(NullPointerException ex){
            }
        }
        p.updateInventory();
        p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

    }

    public void Join(Player player,String kitname){
        for (PotionEffectType potionEffect : PotionEffectType.values()){
            try{
                player.removePotionEffect(potionEffect);
            }catch(NullPointerException ex){
            }
        }
        saveInventory(player);
        PreviousPos.put(player.getName(), player.getLocation());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.teleport(ArenaSpawn);
        inGame.add(player);
        PrevHealth.put(player.getName(), player.getHealth());
        String msg;
        try {
            msg = getConfig().getString("JoinMessage");
        } catch (Exception ex) {
            msg = "&aYou joined KitPvP!";
        }
        player.sendMessage(msg.replaceAll("&","§"));
        player.getInventory().setContents(kit.get(kitname));
        player.getInventory().setArmorContents(kitarm.get(kitname));
        player.teleport(ArenaSpawn);
        LastKit.put(player.getName(), kitname);
        player.updateInventory();
        player.setGameMode(GameMode.SURVIVAL);
        for (PotionEffect pot : kitEffects.get(kitname)){
            player.addPotionEffect(pot);
        }
        final Player player1 = player;
        immuneList.add(player1);
        int i = this.getServer().getScheduler().scheduleSyncDelayedTask(this, new BukkitRunnable() {
            public void run() {
                if (immuneList.contains(player1)) {
                    immuneList.remove(player1);
                }
            }
        }, getConfig().getLong("ImmuneTimeInSeconds") * 20);
        player.setScoreboard(sboard);
    }


    public void GetStuff(){
        Bukkit.getPluginManager().registerEvents(this,this);
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (!getConfig().contains("spawn.world")) return;
        try{
            getServer().getWorld("spawn.world");
            World w = Bukkit.getWorld(getConfig().getString("spawn.world"));
            ArenaSpawn = new Location(w,getConfig().getDouble("spawn.x"),getConfig().getDouble("spawn.y"),getConfig().getDouble("spawn.z"),Float.parseFloat(getConfig().getString("spawn.yaw")),Float.parseFloat(getConfig().getString("spawn.pitch")));
        }catch (Exception ex){
            System.out.print("World does not exist!");
        }
    }
    public void loadKit(String name){
        FileConfiguration cfg = null;
        File file = new File(this.getDataFolder(), name + ".kitinv");
        cfg = YamlConfiguration.loadConfiguration(file);
        List<ItemStack> Inv = (List<ItemStack>) cfg.getList("CONTENT");
        ItemStack[] itemStack = Inv.toArray(new ItemStack[36]);
        kit.put(name,itemStack);
    }
    public void loadKitArm(String name){
        FileConfiguration cfg = null;
        File file = new File(this.getDataFolder(), name + ".kitarm");
        cfg = YamlConfiguration.loadConfiguration(file);
        List<ItemStack> Inv = (List<ItemStack>) cfg.getList("CONTENT");
        System.out.println(Inv);
        ItemStack[] itemStack = Inv.toArray(new ItemStack[4]);
        kitarm.put(name,itemStack);
    }

    public void unscramble(){
        try {
            String thi = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            String str = thi.substring(0, thi.lastIndexOf('/'));
            String string = str + "/KitPvP/";
            File folder = new File(string);
            String[] files = folder.list();
            List<String> array = Arrays.asList(files);
            for (String st : array){
                if (st.endsWith("kitarm") || st.endsWith("yml") || st.endsWith(".kiteff")){
                    continue;
                }
                String stri = st.replaceAll(".kitinv","");
                if (stri.contains("kiteff")){
                    continue;
                }
                System.out.print("KitEff : " + stri);
                kits.add(stri);
            }
            System.out.println("Kits: " + kits);
        } catch (URISyntaxException ex) {

        }
    }

    public void saveKitArm(ItemStack[] stack, String name, Plugin plg){
        FileConfiguration cfg = null;
        File file = new File(plg.getDataFolder(), name + ".kitarm");
        cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("CONTENT", stack);
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveKit(ItemStack[] stack, String name, Plugin plg){
        FileConfiguration cfg = null;
        File file = new File(plg.getDataFolder(), name + ".kitinv");
        cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("CONTENT", stack);
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void saveKitPot(Collection<PotionEffect> effects, String name, Plugin plg){
        FileConfiguration cfg = null;
        File file = new File(plg.getDataFolder(), name + ".kiteff");
        cfg = YamlConfiguration.loadConfiguration(file);
        cfg.set("CONTENT", effects);
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void loadKitPot(String name){
        FileConfiguration cfg = null;
        File file = new File(this.getDataFolder(), name + ".kiteff");
        cfg = YamlConfiguration.loadConfiguration(file);
        List<PotionEffect> Inv = (List<PotionEffect>) cfg.getList("CONTENT");
        Collection<PotionEffect> Effects = Inv;
        kitEffects.put(name,Effects);
    }
    public static Permission permission = null;
    public static Economy economy = null;
    public static Chat chat = null;

    private boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
        return (permission != null);
    }

    private boolean setupChat()
    {
        RegisteredServiceProvider<Chat> chatProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
        if (chatProvider != null) {
            chat = chatProvider.getProvider();
        }

        return (chat != null);
    }

    private boolean setupEconomy()
    {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }

        return (economy != null);
    }

    public void reload(RELOADTYPE reloadtype, CommandSender sender){
        for (Player player : inGame){
            player.sendMessage(ChatColor.YELLOW + "Reloading the plugin.");
            Leave(player);
        }
        if (reloadtype.equals(RELOADTYPE.CONFIG)){
            reloadConfig();
            if (sender != null) sender.sendMessage(ChatColor.DARK_GREEN + "Config reloaded");
        } else if (reloadtype.equals(RELOADTYPE.FILES)){


            for (String s : kits){
                saveKit(kit.get(s),s,this);
            }
            for (String s : kits){
                saveKitArm(kitarm.get(s),s,this);
            }
            for (String s : kits){
                saveKitPot(kitEffects.get(s),s,this);
            }
            try{
                for (String s : kits){
                    loadKit(s);
                }
            }catch (Exception ex){
            }
            try{
                for (String s : kits){
                    loadKitArm(s);
                }
            }catch (Exception ex){
            }
            try{
                for (String s : kits){
                    loadKitPot(s);
                }
            }catch (Exception ex){

            }

        } else if (reloadtype.equals(RELOADTYPE.SPAWNLOCATION)){
            reloadConfig();
            getConfig().set("spawn.world",ArenaSpawn.getWorld().getName());
            getConfig().set("spawn.x",ArenaSpawn.getBlockX());
            getConfig().set("spawn.y",ArenaSpawn.getBlockY());
            getConfig().set("spawn.z",ArenaSpawn.getBlockZ());
            getConfig().set("spawn.yaw",ArenaSpawn.getYaw());
            getConfig().set("spawn.pitch",ArenaSpawn.getPitch());
            saveConfig();
        } else if (reloadtype.equals(RELOADTYPE.PLUGIN)){
            isRestarting = true;
            Bukkit.getPluginManager().disablePlugin(this);
        } else if (reloadtype.equals(RELOADTYPE.SAVEFILES)){
            for (String s : kits){
                saveKit(kit.get(s),s,this);
            }
            for (String s : kits){
                saveKitArm(kitarm.get(s),s,this);
            }
            for (String s : kits){
                saveKitPot(kitEffects.get(s),s,this);
            }
        } else if (reloadtype.equals(RELOADTYPE.LOADFILES)){
            try{
                for (String s : kits){
                    loadKit(s);
                }
            }catch (Exception ex){
            }
            try{
                for (String s : kits){
                    loadKitArm(s);
                }
            }catch (Exception ex){
            }
            try{
                for (String s : kits){
                    loadKitPot(s);
                }
            }catch (Exception ex){

            }
        }

    }

    protected boolean isRestarting = false;

    public enum RELOADTYPE {
        PLUGIN,       //Disable & Enable
        CONFIG,       //reloadConfig();
        FILES,        //Saves, then loads files
        SPAWNLOCATION,//Re-Gets the spawn location
        SAVEFILES,    //save the files to disk
        LOADFILES     //load files without saving

    }
    public ArrayList<String> reloadtypelist = new ArrayList<String>();


    public void onEnable(){
        objective = sboard.registerNewObjective("PlayerHealth", "health");
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        objective.setDisplayName("/ 20");
        if (getConfig().getBoolean("EnableAutoUpdater")){
            int myID = 72548;
            Updater updater = new Updater(this, myID,this.getFile(), Updater.UpdateType.DEFAULT,true);
        }else{
            getLogger().warning("[KitPvP] The auto-updater is not enabled in the KitPvP config.");
        }
        reloadtypelist.add("PLUGIN");
        reloadtypelist.add("CONFIG");
        reloadtypelist.add("FILES");
        reloadtypelist.add("SPAWNLOCATION");
        reloadtypelist.add("SAVEFILES");
        reloadtypelist.add("LOADFILES");
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")){
            setupPermissions();
            setupChat();
            setupEconomy();
            hasEconomy = true;
        }
        GetStuff();
        unscramble();

        getConfig().addDefault("HasDoneTheUUIDs",false);
        if (!getConfig().getBoolean("HasDoneTheUUIDs")) {
            Bukkit.getLogger().warning("[KitPvP] KitPvP is now preparing to convert playernames to UUIDs - prepare for some lag!");
            List<String> arrayList = new ArrayList<String>();
            for (OfflinePlayer pl : Bukkit.getServer().getOfflinePlayers()){
                arrayList.add(pl.getName());
                System.out.println(pl.getName());
            }
            System.out.println(arrayList);
            UUIDFetcher fetcher = new UUIDFetcher(arrayList);
            Map<String, UUID> response = null;
            try {
                response = UUIDFetcher.call();
            } catch (Exception e) {
                getLogger().warning("Exception while running UUIDFetcher");
                e.printStackTrace();
            }
            final Map<String,UUID> res = response;
            int i = Bukkit.getScheduler().scheduleAsyncDelayedTask(this,new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.getLogger().warning("CONVERTING ALL PLAYERS TO UUIDs IN PREPARATION FOR 1.8 -- EXPECT SOME LAGG!!!!!!!!");
                    for (String name: res.keySet()){
                        getConfig().set("stats.kills." + res.get(name), getConfig().getInt("stats.kills." + name.toLowerCase()));
                        getConfig().set("stats.deaths." + res.get(name), getConfig().getInt("stats.deaths." + name.toLowerCase()));
                    }
                    getConfig().set("HasDoneTheUUIDs" , true);
                    saveConfig();
                    Bukkit.getLogger().info("THE UUID CONVERSION HAS FINISHED ( :) )");
                }
            },(res.size() * 20) + 40L);

        }
        try{
        for (String s : kits){
            loadKit(s);
        }
        }catch (Exception ex){

        }
        try{
            for (String s : kits){
                loadKitArm(s);
            }
        }catch (Exception ex){

        }

        try{
            for (String s : kits){
                loadKitPot(s);
            }
        }catch (Exception ex){

        }
        getConfig().addDefault("DeathMessage","&9%killer% &6(on %health% hearts) just killed &9%victim% &6and earned %money% &9%currency%&6.");
        getConfig().addDefault("DeathMessagePrefix", "&bKitPvP");

        saveConfig();

    }
    /*
    getServer().getLogger().info(pl.getName());
                this.getConfig().set("stats.kills." + pl.getPlayer().getUniqueId().toString(), getConfig().getInt("stats.kills." + pl.getName().toLowerCase()));
                this.getConfig().set("stats.deaths." + pl.getPlayer().getUniqueId().toString(), getConfig().getInt("stats.deaths." + pl.getName().toLowerCase()));

     */


    public void onDisable(){
        reloadConfig();
        getConfig().set("spawn.world",ArenaSpawn.getWorld().getName());
        getConfig().set("spawn.x",ArenaSpawn.getBlockX());
        getConfig().set("spawn.y",ArenaSpawn.getBlockY());
        getConfig().set("spawn.z",ArenaSpawn.getBlockZ());
        getConfig().set("spawn.yaw",ArenaSpawn.getYaw());
        getConfig().set("spawn.pitch",ArenaSpawn.getPitch());
        saveConfig();

        for (String s : kits){
            saveKit(kit.get(s),s,this);
        }
        for (String s : kits){
            saveKitArm(kitarm.get(s),s,this);
        }
        for (String s : kits){
            saveKitPot(kitEffects.get(s),s,this);
        }
        if (inGame.isEmpty()) return;
        for (Player player : inGame){
            Leave(player);
        }
        int i = this.getServer().getScheduler().scheduleSyncDelayedTask(this, new BukkitRunnable() {
            public void run() {
            Bukkit.getPluginManager().enablePlugin(Bukkit.getPluginManager().getPlugin("KitPvP"));
            }
        }, 10L);
    }

    @EventHandler
    public void onSignUpdate(SignChangeEvent e){
        if (!(e.getPlayer().hasPermission("kitpvp.signs"))){
            return;
        }
        if (!(e.getLine(0).equals(getConfig().getString("SignStart")))){
            return;
        }
        String begin = getConfig().getString("SignStart");
        if (e.getLine(1).equals("leave")){
            e.setLine(0,ChatColor.DARK_BLUE + begin);
            e.getPlayer().sendMessage(ChatColor.GOLD + "Leave sign created.");
            return;
        }
        if (e.getLine(1).equals("kit")){
            if (!(kits.contains(e.getLine(2)))){
                e.getPlayer().sendMessage(ChatColor.RED + "That kit does not exist!");
                e.setLine(0,ChatColor.DARK_RED + begin);
                return;
            }
            e.setLine(0,ChatColor.DARK_BLUE + "[KitPvP]");
            e.getPlayer().sendMessage(ChatColor.GOLD + "Kit sign created!");
            return;
        }
        if (e.getLine(1).equals("join")){
            if (!(kits.contains(e.getLine(2)))){
                e.getPlayer().sendMessage(ChatColor.RED + "That kit does not exist!");
                e.setLine(0,ChatColor.DARK_RED + begin);
                return;
            }
            e.setLine(0,ChatColor.DARK_BLUE + begin);
            e.getPlayer().sendMessage(ChatColor.GOLD + "Join sign created!");
            return;
        }
        e.getPlayer().sendMessage(ChatColor.RED + "Error: Can only be 'join', 'leave' or 'kit'");
        e.getBlock().breakNaturally();

    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e){
        try {
            if (e.getClickedBlock().getState().getType().equals(Material.SIGN_POST) || e.getClickedBlock().getState().getType().equals(Material.WALL_SIGN)) {
                Sign sign = (Sign) e.getClickedBlock().getState();
                String begin = getConfig().getString("SignStart");
                if (!(sign.getLine(0).equalsIgnoreCase(ChatColor.DARK_BLUE + begin))) return;
                if (sign.getLine(1).equalsIgnoreCase("join")) {
                    if (inGame.contains(e.getPlayer())) {
                        e.getPlayer().sendMessage(ChatColor.RED + "You're already in the game!  To change kits, use a kit sign.");
                        return;
                    }
                    if (!(kits.contains(sign.getLine(2)))) {
                        e.getPlayer().sendMessage(ChatColor.RED + "Sorry, that kit is not available");
                        return;
                    }
                    if (!(e.getPlayer().hasPermission("kitpvp.kit." + sign.getLine(2)))) {
                        e.getPlayer().sendMessage(ChatColor.RED + "You do not have permission for this kit!");
                        return;
                    }
                    Join(e.getPlayer(), sign.getLine(2));
                }
                if (sign.getLine(1).equalsIgnoreCase("kit")) {
                    if (!(inGame.contains(e.getPlayer()))) {
                        e.getPlayer().sendMessage(ChatColor.RED + "You're not in a game!");
                        return;
                    }
                    if (!kits.contains(sign.getLine(2)) || !kit.containsKey(sign.getLine(2)) || !kit.containsKey(sign.getLine(2))) {
                        e.getPlayer().sendMessage(ChatColor.RED + "Sorry, that kit is not available");
                        return;
                    }
                    if (!(e.getPlayer().hasPermission("kitpvp.kit." + sign.getLine(2)))) {
                        e.getPlayer().sendMessage(ChatColor.RED + "You do not have permission for this kit!");
                        return;
                    }
                    LastKit.remove(e.getPlayer().getName());
                    LastKit.put(e.getPlayer().getName(), sign.getLine(2));
                    e.getPlayer().sendMessage(ChatColor.GOLD + "You've changed kit to " +ChatColor.AQUA +  sign.getLine(2) + ChatColor.GOLD + ".  You'll recieve it when you next respawn!");
                    return;
                }
                if (sign.getLine(1).equalsIgnoreCase("leave")) {
                    if (!(inGame.contains(e.getPlayer()))) {
                        e.getPlayer().sendMessage(ChatColor.RED + "You can't leave a game you're not in!");
                        return;
                    }
                    Leave(e.getPlayer());
                }
            }
        } catch (Exception ex) {

        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent e){
        if(!(inGame.contains(e.getPlayer()))) return;

        String PlayerName = e.getPlayer().getName();
        String lastkit =  LastKit.get(PlayerName);
        e.setRespawnLocation(ArenaSpawn);
        for (PotionEffectType potionEffect : PotionEffectType.values()){
            try{
                e.getPlayer().removePotionEffect(potionEffect);
            }catch(NullPointerException ex){
            }
        }
        final String semi = lastkit;
        final Player semip = e.getPlayer();
        int i = this.getServer().getScheduler().scheduleSyncDelayedTask(this, new BukkitRunnable() {
            public void run() {
                semip.getInventory().setContents(kit.get(semi));
                semip.getInventory().setArmorContents(kitarm.get(semi));
                semip.addPotionEffects(kitEffects.get(semi));
            }
        }, 10L);
        immuneList.add(semip);
        int in = this.getServer().getScheduler().scheduleSyncDelayedTask(this, new BukkitRunnable() {
            public void run() {
                if (immuneList.contains(semip)) {
                    immuneList.remove(semip);
                }
            }
        }, getConfig().getLong("ImmuneTimeInSeconds") * 20);


    }
    Plugin plugin = this;



    public void giveEffects(Player player, String lastkit){
        for (PotionEffect pot : kitEffects.get(lastkit)){
            player.addPotionEffect(pot);
        }
        player.updateInventory();
        player.getInventory().setContents(kit.get(lastkit));
        player.getInventory().setArmorContents(kitarm.get(lastkit));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e){
        if (!(inGame.contains(e.getPlayer()))) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void  onFoodLevelChange(FoodLevelChangeEvent e){
        Player player = (Player) e.getEntity();
        if (!(inGame.contains(player))) return;
        e.setFoodLevel(20);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e){

        if(inGame.contains(e.getPlayer())){
            Block b = e.getBlock();
            ItemStack s = new ItemStack(b.getType());

            final Block bl = e.getBlock();
            final Block block = e.getBlockReplacedState().getBlock();

            if (s.getType().equals(new ItemStack(Material.WEB).getType())){
                getServer().getScheduler().scheduleSyncDelayedTask(this.plugin, new BukkitRunnable() {
                    @Override
                    public void run() {
                        bl.getWorld().getBlockAt(bl.getLocation()).setType(block.getType());
                    }
                },getConfig().getLong("CobwebDestroyTimeInSeconds") * 20 );
            }

            if (s.getType().equals(new ItemStack(Material.TNT).getType())){
                Material material = e.getBlockReplacedState().getType();
                b.setType(material);
                World w = ArenaSpawn.getWorld();
                Location loc = e.getBlockPlaced().getLocation().add(0,1,0);
                w.spawnEntity(loc, EntityType.PRIMED_TNT);
                Location loc1 = e.getBlockPlaced().getLocation();
                loc1.getWorld().playSound(loc,Sound.FUSE,1F,1F);
            }
        }
    }

    @EventHandler
    public void onEntityExplodeEvent(EntityExplodeEvent e){
        if (ArenaSpawn == null) return;
        World world = Bukkit.getWorld(this.ArenaSpawn.getWorld().getName());
        if (e.getLocation().getWorld().equals(world)){
            try{
                if (e.getEntity().getType().equals(EntityType.UNKNOWN)){
                    e.setCancelled(true);
                    return;
                }
            }catch (Exception ex){
                e.setCancelled(true);
                return;
            }
            if (e.getEntity().getType().equals(EntityType.PRIMED_TNT)){
                e.setCancelled(true);
                World w = ArenaSpawn.getWorld();
                w.createExplosion(e.getLocation(),4);
            }
        }
    }



    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        if(inGame.contains(e.getPlayer())){
            Leave(e.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent e){
        if (!(inGame.contains(e.getPlayer()))) return;
        List<String> StringList = (List<String>) getConfig().getList("CommandWhitelist");
        if (e.getMessage().startsWith("/kitpvp") || e.getMessage().startsWith("/deatharena")) return;
        for (String  str : StringList){
            if (e.getMessage().startsWith("/" + str)) return;
        }
        if (e.getMessage().startsWith("/leave")){
            Player p = e.getPlayer();
            Leave(p);
            e.setCancelled(true);
            return;

        }
        String str;
        try{
            str = getConfig().getString("BadCommand");
        } catch (Exception ex){
            str = "&4You can't use that in here. If you want to leave, use &6/leave";
        }
        e.getPlayer().sendMessage(str.replaceAll("&","§"));
        e.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e){
        if (e.getEntity() instanceof  Player){
            if (immuneList.contains(e.getEntity())){
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamagebyEntity(EntityDamageByEntityEvent e){

        if (e.getDamager() instanceof Player && e.getEntity() instanceof Player){
            Player attacker = (Player) e.getDamager();
            Player victim = (Player) e.getEntity();

            if (inGame.contains(attacker) && (!inGame.contains(victim))){
                e.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "You can't attack players who are not playing!");

            } else if (inGame.contains(victim) && (!inGame.contains(attacker))){
                e.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "You can't attack players who are in the game!");

            } else if (immuneList.contains(victim)){
                attacker.sendMessage(ChatColor.RED + "That player is currently immune!");
                e.setCancelled(true);

            } else if (immuneList.contains(attacker)){
                immuneList.remove(attacker);
                attacker.sendMessage(ChatColor.YELLOW + "Your immunity has been revoked - you hit an ingame player.");
            }



        }else if(e.getDamager() instanceof Player){
            Player attacker = (Player) e.getDamager();
            if ((!(inGame.contains(attacker)))) return;

            e.setCancelled(true);
        }else if(e.getEntity() instanceof Player){
            Player victim = (Player) e.getEntity();

            if ((!(inGame.contains(victim))) || e.getDamager().getType().equals(EntityType.ARROW) || e.getDamager().getType().equals(EntityType.FISHING_HOOK) || e.getDamager().getType().equals(EntityType.SPLASH_POTION)) return;

            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e){
        try {
            if(inGame.contains(e.getEntity())){
                e.getDrops().clear();
            }
        if(inGame.contains(e.getEntity())&&inGame.contains(e.getEntity().getKiller())){
            List<String> strList = new ArrayList<String>();
            strList.add(e.getEntity().getPlayer().getName());
            strList.add(e.getEntity().getKiller().getName());
            me.oliver276.docs.java.UUIDFetcher fetcher = new me.oliver276.docs.java.UUIDFetcher(strList);
            e.getDrops().clear();
            if (getConfig().getBoolean("DropGoldenApple")){
               e.getDrops().add(new ItemStack(Material.GOLDEN_APPLE));
            }
            e.setDeathMessage(null);
            Player died = e.getEntity();
            Player killer = died.getKiller();
            final Boolean suicide;
            if (died != killer){                         // Make sure that the player hasn't killed them self
                Double hearts = killer.getHealth();
                reloadConfig();
                hearts = (((Math.round(hearts) + 0.1) - 0.1) / 2);
                String DeathMessage;
                try{
                    DeathMessage = (getConfig().getString("DeathMessagePrefix") + " " + getConfig().getString("DeathMessage"));
                }catch (Exception ex){
                    DeathMessage = "&9%killer% &6(on %health% hearts) just killed &9%victim% &6and earned %money% &9%currency%&6.";
                }
                DeathMessage = DeathMessage.replaceAll("%killer%",killer.getName());
                DeathMessage = DeathMessage.replaceAll("%victim%",died.getName());
                DeathMessage = DeathMessage.replaceAll("%health%", hearts.toString());
                DeathMessage = DeathMessage.replaceAll("%money%",getConfig().getString("moneyperkill"));
                DeathMessage = DeathMessage.replaceAll("%currency%",getConfig().getString("currencyname"));
                DeathMessage = DeathMessage.replace('&','§');
                Bukkit.getServer().broadcastMessage(DeathMessage);
                economy.depositPlayer(killer.getName(),getConfig().getInt("moneyperkill"));
                suicide = false;
            }else{
                suicide = true;
            }
            final Map<String,UUID> uuidMap = me.oliver276.docs.java.UUIDFetcher.call();
            final String killerName = killer.getName();
            final String killedName = died.getName();
            int i = Bukkit.getScheduler().scheduleAsyncDelayedTask(this,new BukkitRunnable() {
                @Override
                public void run() {

                    if (!suicide){
                        getConfig().set("stats.kills." + uuidMap.get(killerName).toString(),getConfig().getInt(("stats.kills." + uuidMap.get(killerName).toString())) + 1);
                    }
                    getConfig().set("stats.deaths." + uuidMap.get(killedName).toString(),getConfig().getInt(("stats.deaths." + uuidMap.get(killedName).toString())) + 1);
                    saveConfig();
                }
            });
            saveConfig();

            e.getEntity().getInventory().clear();
        }
        }catch(Exception ex){

        }
    }

    @EventHandler
    public void onPlayerPickup(PlayerPickupItemEvent e){
        if (!(inGame.contains(e.getPlayer()))) return;
        if (e.getItem().getItemStack().getType().equals(Material.GOLDEN_APPLE)){
            e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,10 * 20,2));
            e.getItem().remove();
            e.getPlayer().getInventory().remove(Material.GOLDEN_APPLE);
        }
    }


    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e){                             //Stop the players dropping anything
        if (inGame.contains(e.getPlayer())){
            e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.RED + "You can't drop items in here!");
        }

    }

    @Override
    public boolean onCommand(final CommandSender sender, Command cmd, String label, String[] args) {
        if(cmd.getName().equalsIgnoreCase("KitPvP") || cmd.getName().equalsIgnoreCase("deatharena")){
            int arg = args.length;
            if (arg == 0 || args[0].equalsIgnoreCase("help")){
                sender.sendMessage(ChatColor.GREEN + "-=-=-= KitPvP Help =-=-=-");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " join <KitName>");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " leave");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " stats");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " setinv <KitName>");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " setspawn");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " kit <KitName>");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " removekit <KitName>");
                sender.sendMessage(ChatColor.DARK_GREEN + "/" + label + " reload <ReloadType>");
                return true;

            }
            if (args[0].equalsIgnoreCase("reload")){
                if (sender.hasPermission("kitpvp.reload")){
                    sender.sendMessage(ChatColor.RED + "You haven't got \"kitpvp.reload\" for that!");
                    return true;
                }
                if (arg == 1){
                    sender.sendMessage(ChatColor.RED + "You need to specify reload type!");
                    StringBuilder str = new StringBuilder();
                    for (String re : reloadtypelist){
                        str.append(re + " ");
                    }
                    String list = str.toString();

                    sender.sendMessage(ChatColor.DARK_RED + list);
                    return true;
                }
                if (!reloadtypelist.contains(args[1].toUpperCase())){
                    sender.sendMessage(ChatColor.RED + "Sorry; that's not a reload type.");
                    StringBuilder str = new StringBuilder();
                    for (String re : reloadtypelist){
                        str.append(re + " ");
                    }
                    String list = str.toString();

                    sender.sendMessage(ChatColor.DARK_RED + list);
                    return true;
                }

                String stri = args[1].toUpperCase();
                RELOADTYPE reload = RELOADTYPE.valueOf(stri);
                reload(reload,sender);
                sender.sendMessage(ChatColor.GREEN + "Reloaded");
                return true;
            }
            if (args[0].equalsIgnoreCase("removekit")){
                if (!(sender.hasPermission("kitpvp.removekit"))) {                                    //No permissions
                    sender.sendMessage(ChatColor.RED + "You haven't got \"kitpvp.removekit\" for that.");
                    return true;
                }
                if (arg != 2){
                    sender.sendMessage(ChatColor.RED + "You should put 1 kit as an argument.");       //Haven't specified 1 kit
                    return true;
                }
                String kitname = args[1];
                if (!((kit.containsKey(kitname) || kits.contains(kitname) || kitarm.containsKey(kitname)))){                //Make sure the kit exists
                    sender.sendMessage(ChatColor.RED + "That kit was not found! xD");
                    return true;
                }
                kits.remove(kitname);
                kit.remove(kitname);
                kitarm.remove(kitname);
                String thi;
                try{
                    thi = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();                //Get the plugin's data folder
                }catch (Exception ex){
                  thi = "turd";
                }
                String str = thi.substring(0, thi.lastIndexOf('/'));
                String string = str + "/KitPvP/";
                if (new File(string + kitname + ".kitarm").exists()){
                    System.out.println(new File(string + kitname + ".kitarm").exists());
                    File s = new File(string);
                    if (!s.delete()){
                        s.deleteOnExit();
                    }
                }
                if (new File(string + kitname + ".kitinv").exists()){
                    System.out.println(new File(string + kitname + ".kitinv").exists());
                    File s = new File(string);
                    if (s.delete()){
                        s.deleteOnExit();
                    }
                }
                sender.sendMessage(ChatColor.GOLD + "Done, but you'll need to actually delete the files...");                           //#WTFBug
                return true;
            }
            if (args[0].equalsIgnoreCase("givekit")){
                if (!(sender.hasPermission("kitpvp.givekit"))){
                    sender.sendMessage(ChatColor.RED + "No permission :'(");
                    return true;
                }
                if (!(args.length == 3)){
                    sender.sendMessage(ChatColor.RED + "Wrong number of arguments!");
                    sender.sendMessage(ChatColor.GOLD + "This is the correct use: " + ChatColor.DARK_GREEN + "/kitpvp givekit <kit> <Player>");
                    return true;
                }
                String kit = args[1];
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null){
                    sender.sendMessage(ChatColor.RED + "Error 404: " + ChatColor.DARK_RED + "Player was not found!");
                    return true;
                }


                if (ArenaSpawn == null){
                    sender.sendMessage(ChatColor.RED + "Error! The KitPvP spawn has not been set!");
                    return true;
                }

                if (kits.isEmpty() || kit.isEmpty() || kitarm.isEmpty()){
                    sender.sendMessage(ChatColor.RED + "Error! There are no available kits!");
                    return true;
                }

                if (inGame.contains(target)){
                    sender.sendMessage(ChatColor.DARK_RED + "That player is already in the game!");
                    return true;
                }
                if ((!(kits.contains(kit)))){
                    sender.sendMessage(ChatColor.DARK_RED + "The "+ kit +" kit does not exist!  These kits exist:");
                    sender.sendMessage(ChatColor.GOLD + kits.toString());
                    return true;
                }
                Join(target,kit);
                return true;
            }
            if (args[0].equalsIgnoreCase("kit")){
                if (!(sender instanceof Player)){
                    sender.sendMessage(ChatColor.RED + "You can't use this.");                     //Console
                    return true;
                }
                Player p = (Player) sender;
                if (!(inGame.contains(p))){
                    p.sendMessage(ChatColor.RED + "You aren't in an arena!");                         //Make sure they're in a game
                    return true;
                }
                if (arg == 1){
                    p.sendMessage(ChatColor.RED + "Specify a new kit.");
                    p.sendMessage(ChatColor.GOLD + "These are available" + ChatColor.DARK_AQUA + kits.toString());  //print the kits
                    return true;
                }
                String kitname = args[1];
                if (!(kits.contains(kitname))){
                    p.sendMessage(ChatColor.RED + "That kit does not exist.");
                    p.sendMessage(ChatColor.GOLD + "These are available" + ChatColor.DARK_AQUA + kits.toString());
                    return true;
                }
                if (!(p.hasPermission("kitpvp.kit." + kitname))){
                    p.sendMessage(ChatColor.RED + "You do not have permission for that kit.");                                      //Permission
                    p.sendMessage(ChatColor.GOLD + "These are available" + ChatColor.DARK_AQUA + kits.toString());
                    return true;
                }
                LastKit.remove(p.getName());
                LastKit.put(p.getName(),kitname);
                p.sendMessage(ChatColor.GOLD + "Changed your kit to " + kitname + " .  You will have this kit next time you respawn.");
                return true;
            }
            if (args[0].equalsIgnoreCase("stats")){
                if ((!(sender instanceof Player)) && args.length == 1){
                    sender.sendMessage(ChatColor.RED + "The console cannot check their stats!");
                    return true;
                }
                reloadConfig();
                String player = null;
                Map<String, UUID> response = new HashMap<String,UUID>();
                if (arg == 1){
                    player = ((Player) sender).getName();
                }else{
                    player = args[1];
                }
                List<String> list = new ArrayList<String>();
                list.add(player);
                UUIDFetcher fetcher = new UUIDFetcher(list);

                try {
                    response = UUIDFetcher.call();
                } catch (Exception e) {
                    getLogger().warning("Exception while running UUIDFetcher");
                    e.printStackTrace();
                    Map<String,UUID> frog = new HashMap<String, UUID>();
                    frog.put(player,null);
                    response = frog;
                }
                final String finalPlayer = player;
                final Map<String, UUID> finalResponse = response;
                int i = Bukkit.getScheduler().scheduleAsyncDelayedTask(this,new BukkitRunnable() {
                    @Override
                    public void run() {
                        UUID finalPlayerPath;
                        finalPlayerPath = finalResponse.get(finalPlayer);


                        if (finalPlayerPath == null){
                            sender.sendMessage(ChatColor.RED + "Sorry, that player was not found!");
                        }  else{
                            int kills = getConfig().getInt("stats.kills." + finalPlayerPath.toString());
                            int deaths = getConfig().getInt("stats.deaths." + finalPlayerPath.toString());

                            sender.sendMessage(ChatColor.GOLD + "-=-=-=-= KitPvP Stats =-=-=-=-");

                            sender.sendMessage(ChatColor.GREEN + finalPlayer + "'s Player Kills: " + ChatColor.DARK_GREEN + kills + ChatColor.GREEN + "!");
                            sender.sendMessage(ChatColor.GREEN + finalPlayer + "'s Deaths: " + ChatColor.DARK_GREEN + deaths + ChatColor.GREEN + "!");
                            sender.sendMessage(ChatColor.GREEN + finalPlayer + "'s K/D Ratio: " + ChatColor.DARK_GREEN + kills / deaths + ChatColor.GREEN + "!");
                        }
                    }
                });
                    return true;
                }
            }

            if (args[0].equalsIgnoreCase("join")){
                if (!(sender instanceof Player)){
                    sender.sendMessage(ChatColor.RED + "The console cannot play :(");
                    return true;
                }
                if (!(sender.hasPermission("kitpvp.join"))){
                    sender.sendMessage(ChatColor.RED + "No permission :'(");
                    return true;
                }
                Player player = (Player) sender;

                if (ArenaSpawn == null){
                    sender.sendMessage(ChatColor.RED + "Error! The KitPvP spawn has not been set!");
                    return true;
                }

                if (kits.isEmpty() || kit.isEmpty() || kitarm.isEmpty()){
                    sender.sendMessage(ChatColor.RED + "Error! There are no available kits!");
                    return true;
                }

                if (inGame.contains(player)){
                    sender.sendMessage(ChatColor.DARK_RED + "You're already in the game - I know it's good, but carry on with the one you're in!");
                    return true;
                }
                if (args.length < 2){
                    sender.sendMessage(ChatColor.DARK_RED + "You have not specified a kit! These kits currently exist:");
                    sender.sendMessage(ChatColor.GOLD + kits.toString());
                    return true;
                }
                String kitname = args[1];
                if ((!(kits.contains(kitname))) || (!(sender.hasPermission("kitpvp.kit." + kitname)))){
                    sender.sendMessage(ChatColor.DARK_RED + "The "+ kitname +" kit does not exist or you don't have permission for it!  These kits exist:");
                    sender.sendMessage(ChatColor.GOLD + kits.toString());
                    return true;
                }
                Join(player,kitname);
                return true;
            }
            if (args[0].equalsIgnoreCase("leave")){
                if (!(sender instanceof Player)) return true;
                Player p = (Player) sender;
                if (!(inGame.contains(p))){
                    p.sendMessage("You cannot leave the game - as you're not in one!");
                    return true;
                }
                Leave(p);
                return true;
            }
            if (args[0].equalsIgnoreCase("setspawn")){
                if (!(sender instanceof Player)){
                    sender.sendMessage(ChatColor.DARK_RED + "The console cannot use this!");
                    return true;
                }
                if (!(sender.hasPermission("kitpvp.setspawn"))){
                    sender.sendMessage(ChatColor.DARK_RED + "You haven't got permission...");
                    return true;
                }
                Player player = (Player) sender;
                ArenaSpawn = player.getLocation();
                sender.sendMessage(ChatColor.GREEN + "Done");
                return true;
            }
            if (args[0].equalsIgnoreCase("setinv")){
                if (!(sender instanceof Player)){
                    sender.sendMessage(ChatColor.RED + "Sorry, you can't use this...");
                    return true;
                }
                if (sender.hasPermission("kitpvp.setinv")){
                    if (args.length < 2){
                        sender.sendMessage(ChatColor.DARK_RED + "Sorry, but you have to specify a kit name.");
                        return true;
                    }

                    Player pl = (Player) sender;
                    String kitName = args[1];

                    if (kits.contains(kitName)){
                        kits.remove(kitName);
                        kit.remove(kitName);
                        kitarm.remove(kitName);
                        kitEffects.remove(kitName);
                    }
                    kit.put(kitName,pl.getInventory().getContents());
                    kitarm.put(kitName,pl.getInventory().getArmorContents());
                    kits.add(kitName);
                    kitEffects.put(kitName,pl.getActivePotionEffects());

                    pl.sendMessage(ChatColor.DARK_GREEN + "Inventory saved :)");
                }else{
                    sender.sendMessage(ChatColor.DARK_RED + "You cannot use this :D");
                }
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Not a KitPvP command. Do " + ChatColor.YELLOW + "/" +  label + " help" + ChatColor.RED + " for command help.");
        return true;
    }

}

