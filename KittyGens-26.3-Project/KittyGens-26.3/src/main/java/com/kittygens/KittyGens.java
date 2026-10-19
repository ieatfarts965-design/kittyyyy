package com.kittygens;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class KittyGens extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Island> islands = new HashMap<>();
    private final Map<UUID, UUID> memberIsland = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Integer> ownedGens = new HashMap<>();
    private final Map<UUID, Integer> slots = new HashMap<>();
    private final Map<UUID, Long> boostUntil = new HashMap<>();
    private final Map<UUID, Long> lastMeow = new HashMap<>();
    private final Map<String, Integer> generatorBlocks = new HashMap<>();
    private NamespacedKey furKey, tierKey, generatorKey;
    private File dataFile;
    private YamlConfiguration data;
    private Location spawn;
    private BukkitTask productionTask;

    private static final String[] GEN_NAMES = {
        "Starter Kitty", "Tabby Kitty", "Calico Kitty", "Tuxedo Kitty", "Siamese Kitty",
        "Ginger Kitty", "Snow Kitty", "Shadow Kitty", "Tortoiseshell Kitty", "Silver Kitty",
        "Golden Kitty", "Cloud Kitty", "Moss Kitty", "Ocean Kitty", "Rose Kitty",
        "Crystal Kitty", "Galaxy Kitty", "Lava Kitty", "Frost Kitty", "Emerald Kitty",
        "Ruby Kitty", "Sapphire Kitty", "Prism Kitty", "Phantom Kitty", "Celestial Kitty",
        "Royal Kitty", "Ancient Kitty", "Cosmic Kitty"
    };
    private static final Material[] GEN_BLOCKS = {
        Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.BLACK_WOOL, Material.GRAY_WOOL,
        Material.LIGHT_GRAY_WOOL, Material.BROWN_WOOL, Material.PINK_WOOL, Material.PURPLE_WOOL,
        Material.CYAN_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
        Material.GREEN_WOOL, Material.BLUE_WOOL, Material.MAGENTA_WOOL, Material.RED_WOOL,
        Material.WHITE_GLAZED_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA, Material.PURPLE_GLAZED_TERRACOTTA,
        Material.LIGHT_BLUE_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA, Material.RED_GLAZED_TERRACOTTA,
        Material.PRISMARINE, Material.CRYING_OBSIDIAN, Material.AMETHYST_BLOCK, Material.GOLD_BLOCK,
        Material.DIAMOND_BLOCK, Material.NETHERITE_BLOCK
    };

    @Override public void onEnable() {
        furKey = new NamespacedKey(this, "fur_tier");
        tierKey = new NamespacedKey(this, "generator_tier");
        generatorKey = new NamespacedKey(this, "placed_generator");
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String cmd : List.of("island", "spawn", "kittygens", "sellfur", "generators")) {
            Objects.requireNonNull(getCommand(cmd)).setExecutor(this);
            Objects.requireNonNull(getCommand(cmd)).setTabCompleter(this);
        }
        productionTask = Bukkit.getScheduler().runTaskTimer(this, this::tickGenerators, 20L, 20L);
        getLogger().info("KittyGens enabled for Paper 26.3.");
    }

    @Override public void onDisable() {
        if (productionTask != null) productionTask.cancel();
        saveData();
    }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (data.contains("spawn.world")) {
            World w = Bukkit.getWorld(data.getString("spawn.world", ""));
            if (w != null) spawn = new Location(w, data.getDouble("spawn.x"), data.getDouble("spawn.y"),
                    data.getDouble("spawn.z"), (float)data.getDouble("spawn.yaw"), (float)data.getDouble("spawn.pitch"));
        }
        ConfigurationSection ps = data.getConfigurationSection("players");
        if (ps != null) for (String key : ps.getKeys(false)) {
            UUID id = parseUuid(key); if (id == null) continue;
            balances.put(id, ps.getLong(key + ".balance", 0));
            ownedGens.put(id, ps.getInt(key + ".ownedGens", 3));
            slots.put(id, ps.getInt(key + ".slots", 25));
            if (ps.contains(key + ".island")) {
                UUID islandId = parseUuid(ps.getString(key + ".island"));
                if (islandId != null) memberIsland.put(id, islandId);
            }
            boostUntil.put(id, ps.getLong(key + ".boostUntil", 0));
            lastMeow.put(id, ps.getLong(key + ".lastMeow", 0));
        }
        ConfigurationSection is = data.getConfigurationSection("islands");
        if (is != null) for (String key : is.getKeys(false)) {
            UUID id = parseUuid(key); if (id == null) continue;
            islands.put(id, new Island(id, is.getInt(key + ".index", islands.size()), is.getInt(key + ".size", 64)));
        }
        ConfigurationSection gs = data.getConfigurationSection("generatorBlocks");
        if (gs != null) for (String key : gs.getKeys(false)) {
            int tier = gs.getInt(key, 0);
            if (tier >= 1 && tier <= 28) generatorBlocks.put(key.replace('|', ':'), tier);
        }
    }

    private void saveData() {
        if (data == null) return;
        data.set("spawn", null);
        if (spawn != null && spawn.getWorld() != null) {
            data.set("spawn.world", spawn.getWorld().getName()); data.set("spawn.x", spawn.getX());
            data.set("spawn.y", spawn.getY()); data.set("spawn.z", spawn.getZ());
            data.set("spawn.yaw", spawn.getYaw()); data.set("spawn.pitch", spawn.getPitch());
        }
        data.set("players", null);
        Set<UUID> all = new HashSet<>();
        all.addAll(balances.keySet()); all.addAll(ownedGens.keySet()); all.addAll(slots.keySet()); all.addAll(memberIsland.keySet());
        all.addAll(boostUntil.keySet()); all.addAll(lastMeow.keySet());
        for (UUID id : all) {
            String p = "players." + id;
            data.set(p + ".balance", balances.getOrDefault(id, 0L));
            data.set(p + ".ownedGens", ownedGens.getOrDefault(id, 3));
            data.set(p + ".slots", slots.getOrDefault(id, 25));
            if (memberIsland.containsKey(id)) data.set(p + ".island", memberIsland.get(id).toString());
            data.set(p + ".boostUntil", boostUntil.getOrDefault(id, 0L));
            data.set(p + ".lastMeow", lastMeow.getOrDefault(id, 0L));
        }
        data.set("islands", null);
        for (Island i : islands.values()) {
            data.set("islands." + i.owner + ".index", i.index);
            data.set("islands." + i.owner + ".size", i.size);
        }
        data.set("generatorBlocks", null);
        for (Map.Entry<String, Integer> entry : generatorBlocks.entrySet()) {
            data.set("generatorBlocks." + entry.getKey().replace(':', '|'), entry.getValue());
        }
        try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Could not save data.yml: " + e.getMessage()); }
    }

    private UUID parseUuid(String s) { try { return UUID.fromString(s); } catch (Exception e) { return null; } }
    private long balance(UUID id) { return balances.getOrDefault(id, 0L); }
    private void addMoney(UUID id, long amount) { balances.put(id, Math.max(0, balance(id) + amount)); }
    private UUID islandId(UUID player) { return memberIsland.get(player); }
    private Island island(UUID player) { UUID id = islandId(player); return id == null ? null : islands.get(id); }

    private Location islandCenter(Island island) {
        World world = Bukkit.getWorlds().get(0);
        int spacing = 256;
        int col = island.index % 100;
        int row = island.index / 100;
        return new Location(world, col * spacing + 0.5, world.getHighestBlockYAt(col * spacing, row * spacing) + 1, row * spacing + 0.5);
    }

    private void createIsland(Player p) {
        if (island(p.getUniqueId()) != null) { teleportIsland(p); return; }
        int index = islands.size();
        Island i = new Island(p.getUniqueId(), index, 64);
        islands.put(i.owner, i); memberIsland.put(p.getUniqueId(), i.owner);
        Location c = islandCenter(i);
        World w = c.getWorld();
        int half = i.size / 2;
        int y = Math.max(64, c.getBlockY() - 1);
        for (int x = -half; x < half; x++) for (int z = -half; z < half; z++) {
            Block b = w.getBlockAt(c.getBlockX()+x, y, c.getBlockZ()+z);
            b.setType(Material.GRASS_BLOCK, false);
            if (Math.abs(x) == half-1 || Math.abs(z) == half-1) {
                w.getBlockAt(c.getBlockX()+x, y+1, c.getBlockZ()+z).setType(Material.OAK_FENCE, false);
            }
        }
        c.setY(y + 1);
        p.teleport(c);
        ownedGens.putIfAbsent(p.getUniqueId(), 3);
        slots.putIfAbsent(p.getUniqueId(), 25);
        for (int n=0;n<3;n++) {
            ItemStack item = generatorItem(1);
            p.getInventory().addItem(item);
        }
        p.sendMessage(Component.text("Your Kitty Island is ready! You received 3 Starter Kitty generators.", NamedTextColor.LIGHT_PURPLE));
        saveData();
    }

    private void teleportIsland(Player p) {
        Island i = island(p.getUniqueId());
        if (i == null) { createIsland(p); return; }
        Location c = islandCenter(i); c.setY(Math.max(64, c.getWorld().getHighestBlockYAt(c.getBlockX(), c.getBlockZ()) + 1));
        p.teleport(c);
        p.sendMessage(Component.text("Teleported to your island.", NamedTextColor.GREEN));
    }

    private boolean withinIsland(Location loc, Island i) {
        if (loc.getWorld() == null || !loc.getWorld().equals(islandCenter(i).getWorld())) return false;
        Location c = islandCenter(i);
        int half = i.size / 2;
        return Math.abs(loc.getBlockX()-c.getBlockX()) < half && Math.abs(loc.getBlockZ()-c.getBlockZ()) < half;
    }

    private ItemStack generatorItem(int tier) {
        tier = Math.max(1, Math.min(28, tier));
        ItemStack it = new ItemStack(GEN_BLOCKS[tier-1]);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(GEN_NAMES[tier-1] + " Generator", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(Component.text("Place on your island to produce fur.", NamedTextColor.GRAY),
                Component.text("Tier " + tier + " • $" + format(price(tier)), NamedTextColor.GOLD)));
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack furItem(int tier, int amount) {
        ItemStack it = new ItemStack(Material.WHITE_DYE, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(GEN_NAMES[tier-1].replace(" Kitty","") + " Fur", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(Component.text("Tier " + tier + " fur • Sell value $" + price(tier), NamedTextColor.GOLD)));
        meta.getPersistentDataContainer().set(furKey, PersistentDataType.INTEGER, tier);
        it.setItemMeta(meta);
        return it;
    }

    private long price(int tier) { return Math.round(100 * Math.pow(1.75, tier-1)); }
    private String format(long n) { return String.format(Locale.US, "%,d", n); }
    private long slotCost(int currentSlots) { return 1000L * currentSlots; }

    private void tickGenerators() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Island i = island(id);
            if (i == null) continue;
            if (now - lastMeow.getOrDefault(id, now) >= 300_000L) {
                lastMeow.put(id, now);
                boostUntil.put(id, now + 300_000L);
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.8f, 1.2f);
                p.sendMessage(Component.text("Meow! Your generators produce 2x fur for 5 minutes!", NamedTextColor.LIGHT_PURPLE));
            }
        }
        // Produce fur every 10 seconds from registered generator blocks.
        if (Bukkit.getCurrentTick() % 200 != 0) return;
        long tickNow = System.currentTimeMillis();
        Iterator<Map.Entry<String, Integer>> iterator = generatorBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            String[] parts = entry.getKey().split(":");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            int x, y, z;
            try { x = Integer.parseInt(parts[1]); y = Integer.parseInt(parts[2]); z = Integer.parseInt(parts[3]); }
            catch (NumberFormatException ex) { continue; }
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.AIR) { iterator.remove(); continue; }
            UUID owner = ownerForLocation(block.getLocation());
            int amount = owner != null && tickNow < boostUntil.getOrDefault(owner, 0L) ? 2 : 1;
            world.dropItemNaturally(block.getLocation().add(0.5, 1.1, 0.5), furItem(entry.getValue(), amount));
        }
    }

    private UUID ownerForLocation(Location loc) {
        for (Island i : islands.values()) if (withinIsland(loc, i)) return i.owner;
        return null;
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        balances.putIfAbsent(p.getUniqueId(), 0L);
        ownedGens.putIfAbsent(p.getUniqueId(), 3);
        slots.putIfAbsent(p.getUniqueId(), 25);
        if (!p.hasPlayedBefore() && spawn != null) Bukkit.getScheduler().runTask(this, () -> p.teleport(spawn));
        saveData();
    }

    @EventHandler public void onPlace(BlockPlaceEvent e) {
        ItemMeta meta = e.getItemInHand().getItemMeta();
        if (meta == null) return;
        Integer tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        if (tier == null) return;
        Player p = e.getPlayer();
        Island i = island(p.getUniqueId());
        if (i == null || !withinIsland(e.getBlockPlaced().getLocation(), i)) {
            e.setCancelled(true); p.sendMessage(Component.text("Place generators on your own island.", NamedTextColor.RED)); return;
        }
        int count = countGenerators(i);
        int max = slots.getOrDefault(i.owner, 25);
        if (count >= max) {
            e.setCancelled(true); p.sendMessage(Component.text("Your island has reached its " + max + " generator slot limit.", NamedTextColor.RED)); return;
        }
        Block b = e.getBlockPlaced();
        b.setType(GEN_BLOCKS[tier-1], false);
        generatorBlocks.put(blockKey(b.getLocation()), tier);
        saveData();
        ownedGens.put(i.owner, ownedGens.getOrDefault(i.owner, 3));
    }

    @EventHandler public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Integer tier = generatorBlocks.get(blockKey(b.getLocation()));
        if (tier == null) return;
        Player p = e.getPlayer();
        Island i = island(p.getUniqueId());
        if (i == null || !i.owner.equals(p.getUniqueId()) && !isCoopAllowed(p.getUniqueId(), i)) {
            e.setCancelled(true); p.sendMessage(Component.text("You cannot break this generator.", NamedTextColor.RED)); return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        e.getBlock().setType(Material.AIR, false);
        p.getInventory().addItem(generatorItem(tier));
        generatorBlocks.remove(blockKey(b.getLocation()));
        saveData();
    }

    @EventHandler public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ItemStack stack = e.getItem().getItemStack();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        Integer tier = meta.getPersistentDataContainer().get(furKey, PersistentDataType.INTEGER);
        if (tier == null) return;
        Island i = island(p.getUniqueId());
        if (i == null || !withinIsland(e.getItem().getLocation(), i)) e.setCancelled(true);
    }

    private String blockKey(Location l) { return l.getWorld().getName()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ(); }
    private int countGenerators(Island i) {
        Location c = islandCenter(i); int half=i.size/2, count=0;
        for (int x=-half;x<half;x++) for (int z=-half;z<half;z++) {
            Block b=c.getWorld().getBlockAt(c.getBlockX()+x, c.getBlockY()-1, c.getBlockZ()+z);
            if (generatorBlocks.containsKey(blockKey(b.getLocation()))) count++;
        }
        return count;
    }
    private boolean isCoopAllowed(UUID player, Island i) { return Objects.equals(memberIsland.get(player), i.owner); }

    private void openIslandMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Kitty Island", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(10, named(Material.GRASS_BLOCK, "Teleport to Island", "Go to your island."));
        inv.setItem(12, named(Material.OAK_FENCE, "Expand Island", "Cost: $" + format(expandCost(island(p.getUniqueId())))));
        inv.setItem(14, named(Material.CHEST, "Generator Slots", "Current: " + slots.getOrDefault(p.getUniqueId(),25), "Upgrade cost: $" + format(slotCost(slots.getOrDefault(p.getUniqueId(),25)))));
        inv.setItem(16, named(Material.EMERALD, "Sell Fur", "Sell all fur in your inventory."));
        p.openInventory(inv);
    }

    private ItemStack named(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat); ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GREEN));
        meta.lore(Arrays.stream(lore).map(s -> Component.text(s, NamedTextColor.GRAY)).toList());
        it.setItemMeta(meta); return it;
    }

    private Island islandFor(Player p) { Island i = island(p.getUniqueId()); if (i == null) { createIsland(p); i = island(p.getUniqueId()); } return i; }
    private long expandCost(Island i) { return i == null ? 5000 : (long)i.size * i.size * 2L; }

    private void expandIsland(Player p) {
        Island i = islandFor(p);
        long cost = expandCost(i);
        if (!i.owner.equals(p.getUniqueId())) { p.sendMessage(Component.text("Only the island owner can expand it.", NamedTextColor.RED)); return; }
        if (balance(p.getUniqueId()) < cost) { p.sendMessage(Component.text("You need $" + format(cost) + " to expand.", NamedTextColor.RED)); return; }
        addMoney(p.getUniqueId(), -cost);
        int old = i.size; i.size += 16;
        Location c = islandCenter(i); int y = c.getBlockY()-1;
        int half=i.size/2;
        for (int x=-half;x<half;x++) for (int z=-half;z<half;z++) {
            if (Math.abs(x) < old/2 && Math.abs(z) < old/2) continue;
            c.getWorld().getBlockAt(c.getBlockX()+x,y,c.getBlockZ()+z).setType(Material.GRASS_BLOCK,false);
            if (Math.abs(x)==half-1 || Math.abs(z)==half-1) c.getWorld().getBlockAt(c.getBlockX()+x,y+1,c.getBlockZ()+z).setType(Material.OAK_FENCE,false);
        }
        p.sendMessage(Component.text("Island expanded to " + i.size + "x" + i.size + " blocks!", NamedTextColor.GREEN));
        saveData();
    }

    private void openGeneratorShop(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("Kitty Generator Shop", NamedTextColor.LIGHT_PURPLE));
        for (int tier=1;tier<=28;tier++) {
            ItemStack it = generatorItem(tier); ItemMeta meta=it.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.text("Buy for $" + format(price(tier)), NamedTextColor.GOLD));
            meta.lore(lore); it.setItemMeta(meta);
            int index=tier-1; inv.setItem((index/7)*9+(index%7), it);
        }
        p.openInventory(inv);
    }

    private void sellFur(Player p) {
        long earned=0; int count=0;
        for (int slot=0;slot<p.getInventory().getSize();slot++) {
            ItemStack it=p.getInventory().getItem(slot); if (it==null || !it.hasItemMeta()) continue;
            Integer tier=it.getItemMeta().getPersistentDataContainer().get(furKey,PersistentDataType.INTEGER);
            if (tier==null) continue;
            earned += price(tier)*it.getAmount(); count += it.getAmount(); p.getInventory().setItem(slot,null);
        }
        if (count==0) p.sendMessage(Component.text("You have no Kitty Fur to sell.", NamedTextColor.RED));
        else { addMoney(p.getUniqueId(),earned); p.sendMessage(Component.text("Sold " + count + " fur for $" + format(earned) + ". Balance: $" + format(balance(p.getUniqueId())), NamedTextColor.GREEN); }
        saveData();
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().title().toString();
        if (title.contains("Kitty Island")) {
            e.setCancelled(true);
            if (e.getRawSlot()==10) teleportIsland(p);
            if (e.getRawSlot()==12) expandIsland(p);
            if (e.getRawSlot()==14) upgradeSlots(p);
            if (e.getRawSlot()==16) sellFur(p);
            p.closeInventory();
        } else if (title.contains("Kitty Generator Shop")) {
            e.setCancelled(true);
            int raw=e.getRawSlot();
            if (raw<0 || raw>=54 || raw%9>=7) return;
            int tier=(raw/9)*7+(raw%9)+1;
            if (tier<1 || tier>28) return;
            long cost=price(tier);
            if (balance(p.getUniqueId())<cost) { p.sendMessage(Component.text("You need $" + format(cost) + ".", NamedTextColor.RED)); return; }
            addMoney(p.getUniqueId(),-cost);
            Map<Integer,ItemStack> overflow=p.getInventory().addItem(generatorItem(tier));
            overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(),item));
            p.sendMessage(Component.text("Bought " + GEN_NAMES[tier-1] + " Generator for $" + format(cost) + ".", NamedTextColor.GREEN));
            saveData();
        }
    }

    private void upgradeSlots(Player p) {
        UUID id=p.getUniqueId(); int current=slots.getOrDefault(id,25); long cost=slotCost(current);
        if (balance(id)<cost) { p.sendMessage(Component.text("You need $" + format(cost) + " to upgrade slots.", NamedTextColor.RED)); return; }
        addMoney(id,-cost); slots.put(id,current+1);
        p.sendMessage(Component.text("Generator slots increased to " + (current+1) + ".", NamedTextColor.GREEN)); saveData();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd=command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("kittygens")) {
            if (args.length>0 && args[0].equalsIgnoreCase("setspawn")) {
                if (!(sender instanceof Player p) || !p.hasPermission("kittygens.admin")) { sender.sendMessage("Only an operator can set spawn in-game."); return true; }
                spawn=p.getLocation().clone(); saveData(); p.sendMessage(Component.text("KittyGens spawn set.", NamedTextColor.GREEN)); return true;
            }
            if (args.length>0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("kittygens.admin")) { loadData(); sender.sendMessage("KittyGens data reloaded."); return true; }
            sender.sendMessage("KittyGens 26.3: /island, /spawn, /generators, /sellfur, /kittygens setspawn"); return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        UUID id=p.getUniqueId();
        switch(cmd) {
            case "spawn" -> { if (spawn==null) p.sendMessage(Component.text("Spawn isn't set yet. Ask an admin to use /kittygens setspawn.", NamedTextColor.RED)); else p.teleport(spawn); }
            case "island" -> {
                if (args.length==0) openIslandMenu(p);
                else if (args[0].equalsIgnoreCase("invite") && args.length>=2) {
                    Player target=Bukkit.getPlayerExact(args[1]); Island i=island(id);
                    if (i==null || !i.owner.equals(id)) p.sendMessage(Component.text("Only island owners can invite players.", NamedTextColor.RED));
                    else if (target==null) p.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
                    else { pendingInvites.put(target.getUniqueId(),i.owner); target.sendMessage(Component.text("You were invited to an island. Type /island accept.",NamedTextColor.LIGHT_PURPLE)); p.sendMessage(Component.text("Invite sent.",NamedTextColor.GREEN)); }
                } else if (args[0].equalsIgnoreCase("accept")) {
                    UUID owner=pendingInvites.remove(id);
                    if (owner==null || !islands.containsKey(owner)) p.sendMessage(Component.text("You don't have a pending island invite.",NamedTextColor.RED));
                    else { memberIsland.put(id,owner); p.sendMessage(Component.text("You joined the island co-op.",NamedTextColor.GREEN)); teleportIsland(p); saveData(); }
                } else if (args[0].equalsIgnoreCase("leave")) {
                    UUID owner=memberIsland.get(id);
                    if (owner==null) p.sendMessage(Component.text("You don't belong to an island.",NamedTextColor.RED));
                    else if (owner.equals(id)) p.sendMessage(Component.text("The island owner cannot leave their own island.",NamedTextColor.RED));
                    else { memberIsland.remove(id); p.sendMessage(Component.text("You left the co-op island.",NamedTextColor.GREEN)); saveData(); }
                } else if (args[0].equalsIgnoreCase("expand")) expandIsland(p);
                else p.sendMessage("Usage: /island [invite <player>|accept|leave|expand]");
            }
            case "sellfur" -> sellFur(p);
            case "generators" -> openGeneratorShop(p);
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("island") && args.length==1) return List.of("invite","accept","leave","expand");
        if (command.getName().equalsIgnoreCase("kittygens") && args.length==1) return List.of("setspawn","reload");
        if (command.getName().equalsIgnoreCase("island") && args.length==2 && args[0].equalsIgnoreCase("invite")) return null;
        return List.of();
    }

    private static final class Island {
        final UUID owner; final int index; int size;
        Island(UUID owner,int index,int size) { this.owner=owner; this.index=index; this.size=size; }
    }
}
