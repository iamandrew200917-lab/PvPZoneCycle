package com.gradercraft.pvpzonecycle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerJoinEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.*;

public final class PvPZoneCycle extends JavaPlugin implements Listener, org.bukkit.command.TabCompleter {
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Set<String>> insideArenas = new HashMap<>();
    private final Map<UUID, Set<String>> notifiedUnprotectedArenas = new HashMap<>();
    private final Map<UUID, Long> adminReminderAt = new HashMap<>();
    private NamespacedKey selectorKey;
    private BukkitTask ticker;
    private BukkitTask selectionEffectsTask;

    @Override public void onEnable() {
        saveDefaultConfig(); selectorKey = new NamespacedKey(this, "arena_selector");
        // Remove legacy self-restoration markers from older versions. The plugin no longer
        // grants OP through a player command; OP changes must be performed by server staff/console.
        if (getConfig().isConfigurationSection("op-test.pending")) {
            getConfig().set("op-test.pending", null);
            saveConfig();
            getLogger().warning("Cleared legacy OP-test restore markers. Player-side OP restoration is disabled for security.");
        }
        loadArenas(); Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("pvpzone") != null) { getCommand("pvpzone").setExecutor(this); getCommand("pvpzone").setTabCompleter(this); }
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 20L);
        selectionEffectsTask = Bukkit.getScheduler().runTaskTimer(this, this::showSelectionEffects, 5L, 10L);
        getLogger().info("PvPZoneCycle 1.3.8 enabled; loaded " + arenas.size() + " arena(s).");
    }
    @Override public void onDisable() { if (ticker != null) ticker.cancel(); if (selectionEffectsTask != null) selectionEffectsTask.cancel(); saveArenas(); }

    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!adminQuiet(player) || !getConfig().getBoolean("admin-reminder.enabled", true)) return;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, getConfig().getLong("admin-reminder.cooldown-seconds", 60)) * 1000L;
        Long last = adminReminderAt.get(player.getUniqueId());
        if (last != null && now - last < cooldown) return;
        adminReminderAt.put(player.getUniqueId(), now);
        String prefix = getConfig().getString("messages.prefix", "&8[&cPvPZone&8] ");
        String reminder = getConfig().getString("admin-reminder.message", "&eWelcome, Admin! Use /pvpzone wand to select an arena region. ");
        player.sendMessage(Component.text(color(prefix + reminder))
            .append(Component.text(color(getConfig().getString("admin-reminder.click-label", "&a[CLICK TO GET WAND]")))
                .clickEvent(ClickEvent.runCommand("/pvpzone wand"))
                .hoverEvent(HoverEvent.showText(Component.text("Run /pvpzone wand")))));
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private void msg(CommandSender s, String text) { s.sendMessage(color(getConfig().getString("messages.prefix", "&8[&cPvPZone&8] ") + text)); }
    private boolean admin(CommandSender s) {
        if (s.hasPermission("pvpzone.admin") || s.isOp()) return true;
        msg(s, "&cYou do not have permission to use this command."); return false;
    }
    private Material selectorMaterial() {
        Material m = Material.matchMaterial(getConfig().getString("selection.tool", "GOLDEN_AXE"));
        return m == null || !m.isItem() ? Material.GOLDEN_AXE : m;
    }
    private ItemStack makeSelector() {
        ItemStack item = new ItemStack(selectorMaterial()); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("selection.name", "&c&lArena Selector")));
        meta.setLore(Collections.singletonList(color(getConfig().getString("selection.lore", "&7Left-click: position 1 | Right-click: position 2"))));
        meta.getPersistentDataContainer().set(selectorKey, PersistentDataType.BYTE, (byte)1); item.setItemMeta(meta); return item;
    }
    private boolean isSelector(ItemStack item) {
        if (item == null || item.getType() != selectorMaterial() || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer().get(selectorKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte)1;
    }
    /** Resolves a potion effect name across Minecraft versions: tries the Bukkit name, a legacy<->modern alias, then the registry key. */
    @SuppressWarnings("deprecation")
    private PotionEffectType resolveEffect(String rawName) {
        if (rawName == null) return null;
        String name = PvPZoneRules.normalizeEffectToken(rawName);
        PotionEffectType type = PotionEffectType.getByName(name);
        if (type == null) { String alias = PvPZoneRules.aliasOf(name); if (alias != null) type = PotionEffectType.getByName(alias); }
        if (type == null) { try { type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT))); } catch (Throwable ignored) {} }
        if (type == null) { String alias = PvPZoneRules.aliasOf(name); if (alias != null) { try { type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(alias.toLowerCase(Locale.ROOT))); } catch (Throwable ignored) {} } }
        return type;
    }
    private void loadArenas() {
        arenas.clear(); if (!getConfig().isConfigurationSection("arenas")) return;
        for (String id : getConfig().getConfigurationSection("arenas").getKeys(false)) {
            String p = "arenas." + id + "."; World w = Bukkit.getWorld(getConfig().getString(p + "world", "")); if (w == null) continue;
            Arena a = new Arena(id, w.getName(), getConfig().getInt(p+"x1"), getConfig().getInt(p+"y1"), getConfig().getInt(p+"z1"), getConfig().getInt(p+"x2"), getConfig().getInt(p+"y2"), getConfig().getInt(p+"z2"));
            a.onSeconds = Math.max(1, getConfig().getLong(p+"on-seconds", getConfig().getLong("cycle.default-on-seconds",60)));
            a.offSeconds = Math.max(1, getConfig().getLong(p+"off-seconds", getConfig().getLong("cycle.default-off-seconds",60)));
            a.active = getConfig().getBoolean(p+"active", getConfig().getBoolean("cycle.start-on", true)); a.protectedBlocks = getConfig().getBoolean(p+"protected", false); a.remaining = a.active ? a.onSeconds : a.offSeconds;
            if (getConfig().isConfigurationSection(p + "effects")) for (String effectName : getConfig().getConfigurationSection(p + "effects").getKeys(false)) { String ep = p + "effects." + effectName + "."; a.effects.put(effectName.toUpperCase(Locale.ROOT), new EffectSpec(getConfig().getInt(ep + "seconds", 30), getConfig().getInt(ep + "level", 1))); }
            arenas.put(id.toLowerCase(Locale.ROOT), a);
        }
    }
    private boolean saveArenas() {
        // Update known fields in place: preserve unrelated plugin config and arena metadata,
        // including arenas whose worlds are temporarily unavailable during startup.
        for (Arena a : arenas.values()) {
            String p="arenas."+a.id+".";
            getConfig().set(p+"world",a.world); getConfig().set(p+"x1",a.x1); getConfig().set(p+"y1",a.y1); getConfig().set(p+"z1",a.z1);
            getConfig().set(p+"x2",a.x2); getConfig().set(p+"y2",a.y2); getConfig().set(p+"z2",a.z2);
            getConfig().set(p+"on-seconds",a.onSeconds); getConfig().set(p+"off-seconds",a.offSeconds); getConfig().set(p+"active",a.active); getConfig().set(p+"protected",a.protectedBlocks);
            getConfig().set(p + "effects", null);
            for (Map.Entry<String, EffectSpec> effect : a.effects.entrySet()) { getConfig().set(p + "effects." + effect.getKey() + ".seconds", effect.getValue().seconds); getConfig().set(p + "effects." + effect.getKey() + ".level", effect.getValue().level); }
        }
        Path file = getDataFolder().toPath().resolve("config.yml");
        Path backup = getDataFolder().toPath().resolve("config.yml.bak");
        try {
            Files.createDirectories(getDataFolder().toPath());
            if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            saveConfig();
            return Files.exists(file) && Files.size(file) > 0;
        } catch (Exception ex) {
            getLogger().severe("Failed to save arena data; backup is at config.yml.bak when available: " + ex.getMessage());
            return false;
        }
    }
    private void tick() {
        updateArenaEffects();
        updateProtectionNotifications();
        for (Arena a : arenas.values()) {
            a.remaining--; if (a.remaining <= 0) { a.active = !a.active; a.remaining = a.active ? a.onSeconds : a.offSeconds; announce(a); if (!saveArenas()) getLogger().severe("Could not persist automatic PvP cycle state for arena " + a.id); }
            if (getConfig().getBoolean("cycle.actionbar", true)) for (Player p : Bukkit.getOnlinePlayers()) if (a.contains(p.getLocation())) {
                String state = a.active ? "&a&lPVP ON" : "&c&lPVP OFF";
                p.sendActionBar(color(state + " &8| &f" + format(a.remaining)));
            }
        }
    }
    /** Draws a lightweight cuboid outline only to the admin holding the marked selector. */

    private void updateArenaEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<String> nowInside = new HashSet<>();
            for (Arena arena : arenas.values()) {
                if (!arena.effects.isEmpty() && arena.contains(player.getLocation())) {
                    nowInside.add(arena.id);
                    Set<String> previouslyInside = insideArenas.getOrDefault(player.getUniqueId(), Collections.emptySet());
                    if (!previouslyInside.contains(arena.id)) {
                        for (Map.Entry<String, EffectSpec> entry : arena.effects.entrySet()) {
                            PotionEffectType type = resolveEffect(entry.getKey());
                            if (type == null) continue;
                            EffectSpec spec = entry.getValue();
                            player.addPotionEffect(new PotionEffect(type, Math.max(1, spec.seconds) * 20, Math.max(0, spec.level - 1), true, true, true));
                        }
                        msg(player, "&aArena &f" + arena.id + " &agave you its effects.");
                    }
                }
            }
            if (nowInside.isEmpty()) insideArenas.remove(player.getUniqueId());
            else insideArenas.put(player.getUniqueId(), nowInside);
        }
    }

    private void updateProtectionNotifications() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Set<String> current = new HashSet<>();
            Location location = player.getLocation();
            for (Arena arena : arenas.values()) {
                if (!arena.contains(location) || arena.protectedBlocks) continue;
                current.add(arena.id.toLowerCase(Locale.ROOT));
            }
            Set<String> previous = notifiedUnprotectedArenas.getOrDefault(id, Collections.emptySet());
            for (String arenaKey : current) {
                if (previous.contains(arenaKey)) continue;
                Arena arena = arenas.get(arenaKey);
                if (arena == null || arena.protectedBlocks) continue;
                String prefix = getConfig().getString("messages.prefix", "&8[&bPvPZone&8] ");
                player.sendMessage(color(prefix + getConfig().getString("messages.protection-hint", "&eWant to protect this Arena from griefing?")));
                player.sendMessage(color(prefix + getConfig().getString("messages.protection-hint-command", "&7Enable block protection using:")));
                player.sendMessage(color(prefix + getConfig().getString("messages.protection-hint-usage", "&b/pvpzone protect <arenaName>").replace("<arenaName>", arena.id).replace("{arena}", arena.id)));
                player.sendMessage(color(prefix + getConfig().getString("messages.protection-hint-example", "&7Example: &f/pvpzone protect {arena}").replace("<arenaName>", arena.id).replace("{arena}", arena.id)));
            }
            if (current.isEmpty()) notifiedUnprotectedArenas.remove(id);
            else notifiedUnprotectedArenas.put(id, current);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        insideArenas.remove(id);
        notifiedUnprotectedArenas.remove(id);
        selections.remove(id);
        adminReminderAt.remove(id);
    }

    private void showSelectionEffects() {
        if (!getConfig().getBoolean("selection.effects.enabled", true)) return;
        Particle particle;
        try { particle = Particle.valueOf(getConfig().getString("selection.effects.particle", "END_ROD").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { particle = Particle.END_ROD; }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!adminQuiet(p) || !isSelector(p.getInventory().getItemInMainHand())) continue;
            Selection sel = selections.get(p.getUniqueId());
            if (sel == null || sel.one == null || sel.two == null || !sel.one.getWorld().equals(sel.two.getWorld())) continue;
            drawCuboidForPlayer(p, sel.one, sel.two, particle);
        }
    }
    private boolean adminQuiet(CommandSender sender) { return sender.isOp() || sender.hasPermission("pvpzone.admin"); }
    private void drawCuboidForPlayer(Player player, Location first, Location second, Particle particle) {
        double minX = Math.min(first.getBlockX(), second.getBlockX()) + 0.5;
        double minY = Math.min(first.getBlockY(), second.getBlockY()) + 0.5;
        double minZ = Math.min(first.getBlockZ(), second.getBlockZ()) + 0.5;
        double maxX = Math.max(first.getBlockX(), second.getBlockX()) + 0.5;
        double maxY = Math.max(first.getBlockY(), second.getBlockY()) + 0.5;
        double maxZ = Math.max(first.getBlockZ(), second.getBlockZ()) + 0.5;
        Location[] corners = new Location[] {
            new Location(first.getWorld(), minX,minY,minZ), new Location(first.getWorld(),maxX,minY,minZ),
            new Location(first.getWorld(),minX,maxY,minZ), new Location(first.getWorld(),maxX,maxY,minZ),
            new Location(first.getWorld(),minX,minY,maxZ), new Location(first.getWorld(),maxX,minY,maxZ),
            new Location(first.getWorld(),minX,maxY,maxZ), new Location(first.getWorld(),maxX,maxY,maxZ)
        };
        int[][] edges = {{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
        for (int[] edge : edges) {
            Location a = corners[edge[0]], b = corners[edge[1]];
            double dx=b.getX()-a.getX(), dy=b.getY()-a.getY(), dz=b.getZ()-a.getZ();
            int points = Math.max(1, Math.min(32, (int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*1.5)));
            for (int i=0;i<=points;i++) {
                double t=(double)i/points;
                player.spawnParticle(particle, a.getX()+dx*t, a.getY()+dy*t, a.getZ()+dz*t, 1, 0,0,0, 0);
            }
        }
    }
    private String format(long seconds) { return PvPZoneRules.formatTime(seconds); }
    private void announce(Arena a) {
        for (Player p : Bukkit.getOnlinePlayers()) if (a.contains(p.getLocation())) {
            msg(p, a.active ? "&aPvP is now enabled in &f"+a.id : "&ePvP is now disabled in &f"+a.id);
            if (getConfig().getBoolean("cycle.sounds", true)) {
                String key = getConfig().getString(a.active ? "cycle.on-sound" : "cycle.off-sound", a.active ? "ENTITY_WITHER_SPAWN" : "BLOCK_BEACON_DEACTIVATE");
                try { Sound sound = Sound.valueOf(key.toUpperCase(Locale.ROOT)); p.playSound(p.getLocation(), sound, (float)getConfig().getDouble(a.active?"cycle.on-volume":"cycle.off-volume", .7), (float)getConfig().getDouble(a.active?"cycle.on-pitch":"cycle.off-pitch", 1)); }
                catch (IllegalArgumentException ex) { getLogger().warning("Unknown sound in config: " + key); }
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item=e.getItem(); if (item == null || item.getType()!=selectorMaterial()) return;
        boolean marked=isSelector(item); boolean protectedTool=getConfig().getBoolean("selection.prevent-normal-use",true);
        if (!marked && !protectedTool) return;
        if (protectedTool) e.setCancelled(true); // configured selector material is reserved, not a normal tool
        if (!marked) return;
        Player p=e.getPlayer(); if (!admin(p)) return;
        Action action=e.getAction(); if (action!=Action.LEFT_CLICK_BLOCK && action!=Action.RIGHT_CLICK_BLOCK && action!=Action.LEFT_CLICK_AIR && action!=Action.RIGHT_CLICK_AIR) return;
        Location loc;
        if (e.getClickedBlock() != null) loc = e.getClickedBlock().getLocation();
        else { org.bukkit.util.RayTraceResult trace = p.rayTraceBlocks(100.0); loc = trace != null && trace.getHitBlock() != null ? trace.getHitBlock().getLocation() : p.getLocation().getBlock().getLocation(); }
        Selection s=selections.computeIfAbsent(p.getUniqueId(), k->new Selection());
        if (action==Action.LEFT_CLICK_BLOCK || action==Action.LEFT_CLICK_AIR) { s.one=loc; msg(p,"&aPosition 1 set: &f"+coords(loc)); }
        else { s.two=loc; msg(p,"&aPosition 2 set: &f"+coords(loc)); }
        if (s.one != null && s.two != null) {
            if (!s.one.getWorld().equals(s.two.getWorld())) msg(p, "&cCorners are in different worlds; select both in the same world.");
            else {
                int dx=Math.abs(s.one.getBlockX()-s.two.getBlockX())+1, dy=Math.abs(s.one.getBlockY()-s.two.getBlockY())+1, dz=Math.abs(s.one.getBlockZ()-s.two.getBlockZ())+1;
                msg(p, "&bSelection: &fX "+Math.min(s.one.getBlockX(),s.two.getBlockX())+".."+Math.max(s.one.getBlockX(),s.two.getBlockX())+", Y "+Math.min(s.one.getBlockY(),s.two.getBlockY())+".."+Math.max(s.one.getBlockY(),s.two.getBlockY())+", Z "+Math.min(s.one.getBlockZ(),s.two.getBlockZ())+".."+Math.max(s.one.getBlockZ(),s.two.getBlockZ()));
                msg(p, "&bDimensions: &f"+dx+" × "+dy+" × "+dz+" blocks");
            }
        }
    }
    private String coords(Location l) { return l.getWorld().getName()+" "+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ(); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (isSelector(e.getItemDrop().getItemStack())) e.setCancelled(true); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) { if (isSelector(e.getMainHandItem()) || isSelector(e.getOffHandItem())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (adminQuiet(e.getPlayer())) return;
        Location loc = e.getBlock().getLocation();
        for (Arena a : arenas.values()) {
            if (a.protectedBlocks && a.containsProtectedBlock(loc)) {
                e.setCancelled(true);
                msg(e.getPlayer(), "&cYou cannot break blocks inside protected arena &f" + a.id + "&c.");
                return;
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (adminQuiet(e.getPlayer())) return;
        Location loc = e.getBlock().getLocation();
        for (Arena a : arenas.values()) {
            if (a.protectedBlocks && a.containsProtectedBlock(loc)) {
                e.setCancelled(true);
                msg(e.getPlayer(), "&cYou cannot place blocks inside protected arena &f" + a.id + "&c.");
                return;
            }
        }
    }

    /** Block protection uses the arena's X/Z footprint and lower Y bound, extending upward. */
    private boolean protectedAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (Arena arena : arenas.values()) {
            if (arena.protectedBlocks && arena.containsProtectedBlock(loc)) return true;
        }
        return false;
    }

    private boolean protectedArenaHasPvpOffAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (Arena arena : arenas.values()) {
            if (arena.protectedBlocks && !arena.active && arena.containsProtectedBlock(loc)) return true;
        }
        return false;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (adminQuiet(e.getPlayer())) return;
        org.bukkit.block.Block target = e.getBlockClicked().getRelative(e.getBlockFace());
        if (protectedAt(target.getLocation())) { e.setCancelled(true); msg(e.getPlayer(), "&cYou cannot use buckets inside a protected arena."); }
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (adminQuiet(e.getPlayer())) return;
        if (protectedAt(e.getBlockClicked().getLocation())) { e.setCancelled(true); msg(e.getPlayer(), "&cYou cannot use buckets inside a protected arena."); }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onTntPrime(TNTPrimeEvent event) {
        Location location = event.getBlock().getLocation();
        if (!protectedArenaHasPvpOffAt(location)) return;
        event.setCancelled(true);
        // Cancel priming and remove the TNT block so a redstone-triggered charge cannot remain armed.
        if (event.getBlock().getType() == Material.TNT) event.getBlock().setType(Material.AIR, false);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> protectedAt(b.getLocation())); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> protectedAt(b.getLocation())); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockBurn(BlockBurnEvent e) { if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockIgnite(BlockIgniteEvent e) { if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockSpread(BlockSpreadEvent e) { if (protectedAt(e.getBlock().getLocation()) || protectedAt(e.getSource().getLocation())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockFromTo(BlockFromToEvent e) {
        if (protectedAt(e.getBlock().getLocation()) || protectedAt(e.getToBlock().getLocation())) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBlockFade(BlockFadeEvent e) { if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onLeavesDecay(LeavesDecayEvent e) { if (protectedAt(e.getBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (protectedAt(e.getBlock().getLocation()) || e.getBlocks().stream().anyMatch(b -> protectedAt(b.getLocation()) || protectedAt(b.getRelative(e.getDirection()).getLocation()))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (protectedAt(e.getBlock().getLocation()) || e.getBlocks().stream().anyMatch(b -> protectedAt(b.getLocation()) || protectedAt(b.getRelative(e.getDirection()).getLocation()))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (protectedAt(e.getBlock().getLocation()) && e.getEntity() instanceof org.bukkit.entity.Enderman) e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker=playerFrom(e.getDamager()); if (!(e.getEntity() instanceof Player victim) || attacker==null) return;
        for (Arena a:arenas.values()) if (a.contains(victim.getLocation()) || a.contains(attacker.getLocation())) { if (!a.active) e.setCancelled(true); return; }
    }
    private Player playerFrom(Entity e) { if(e instanceof Player p)return p; if(e instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p)return p; return null; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length==0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        String sub=args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("new")) sub = "create";
        if (sub.equals("coordinates")) sub = "coords";
        if (sub.equals("remove")) sub = "delete";
        if (sub.equals("save")) { if (!admin(sender)) return true; audit(sender, args); saveArenas(); msg(sender, "&aAll arenas saved successfully."); return true; }
        // This command intentionally does not remove or grant OP. A player-side restore
        // command could let a demoted administrator regain privileges after a dispute.
        if (sub.equals("op") || sub.equals("back")) {
            msg(sender, "&cPlayer-side OP restoration is disabled for security. Ask the server owner/console to grant OP if appropriate.");
            return true;
        }
        if (sub.equals("info")) {
            int protectedCount = 0;
            for (Arena arena : arenas.values()) if (arena.protectedBlocks) protectedCount++;
            msg(sender, "&fPvPZoneCycle &b" + getDescription().getVersion() + " &8| &aStatus: enabled");
            msg(sender, "&7Arenas loaded: &f" + arenas.size() + " &8| &7Protected arenas: &f" + protectedCount);
            msg(sender, "&7Protection: &fblock break/place, fire, explosions, pistons, liquids and Enderman changes (per configured region).");
            msg(sender, "&7OP safety: &aNo player command can grant or restore OP.");
            msg(sender, "&7Use &f/pvpzone help &7for command list.");
            return true;
        }
        if (sub.equals("test")) {
            if (!(sender.isOp() || sender.hasPermission("pvpzone.admin") || sender.hasPermission("pvpzone.test"))) { msg(sender, "&cYou do not have permission to use this command."); return true; }
            msg(sender, "&aPvPZoneCycle command test passed. Version: &f" + getDescription().getVersion());
            msg(sender, "&aLoaded arenas: &f" + arenas.size() + " &8| &aConfig available: &f" + (getConfig() != null));
            msg(sender, "&7This is a diagnostics-only test; it will not remove or grant OP.");
            return true;
        }
        if(sub.equals("status")) { if(args.length<2){ msg(sender,"&cUsage: /pvpzone status <arena>"); return true; } Arena a=arenas.get(args[1].toLowerCase(Locale.ROOT)); if(a==null){msg(sender,"&cArena not found.");return true;} msg(sender,"&f"+a.id+" &8| "+(a.active?"&aPVP ON":"&cPVP OFF")+" &8| &f"+format(a.remaining)+" remaining"); return true; }
        if(sub.equals("list")) { if(arenas.isEmpty()){msg(sender,"&7No arenas configured.");return true;} msg(sender,"&eArenas:"); for(Arena a:arenas.values()) msg(sender,"&f- "+a.id+" &8| "+(a.active?"&aON":"&cOFF")+" &8| &f"+format(a.remaining)); return true; }
        if (sub.equals("protect")) {
            if (!(adminQuiet(sender) || sender.hasPermission("pvpzone.protect"))) {
                msg(sender, "&cYou do not have permission to use this command."); return true;
            }
            if (args.length < 2) { msg(sender, "&eUsage: /pvpzone protect <arenaName>"); return true; }
            Arena arena = arenas.get(args[1].toLowerCase(Locale.ROOT));
            if (arena == null) { msg(sender, "&cArena not found: &f" + args[1]); return true; }
            if (arena.protectedBlocks) { msg(sender, "&eThis Arena is already protected."); return true; }
            arena.protectedBlocks = true;
            audit(sender, args);
            if (!saveArenas()) { arena.protectedBlocks = false; msg(sender, "&cCould not save protection setting; it has been rolled back."); return true; }
            msg(sender, "&aBlock protection enabled for Arena &f" + arena.id + "&a!");
            return true;
        }
        if(!admin(sender)) return true;
        if (Arrays.asList("coords", "create", "delete", "unprotect", "effect", "effectremove", "ontime", "offtime", "set", "reload", "wand").contains(sub)) audit(sender, args);
        switch(sub) {
            case "coords" -> {
                if (args.length < 5) { msg(sender, "&eUsage: /pvpzone coords <arena> y <up|down> <height>"); return true; }
                Arena arena = arenas.get(args[1].toLowerCase(Locale.ROOT));
                if (arena == null) { msg(sender, "&cArena not found. Use /pvpzone list."); return true; }
                if (!args[2].equalsIgnoreCase("y") || !(args[3].equalsIgnoreCase("up") || args[3].equalsIgnoreCase("down"))) { msg(sender, "&eUsage: /pvpzone coords <arena> y <up|down> <height>"); return true; }
                int height;
                try { height = Integer.parseInt(args[4]); } catch (NumberFormatException ex) { msg(sender, "&cHeight must be an integer block Y coordinate."); return true; }
                int oldY1 = arena.y1, oldY2 = arena.y2;
                if (args[3].equalsIgnoreCase("up")) {
                    if (height < arena.y1) { msg(sender, "&cUpper Y cannot be below the lower Y (" + arena.y1 + ")."); return true; }
                    arena.y2 = height;
                } else {
                    if (height > arena.y2) { msg(sender, "&cLower Y cannot be above the upper Y (" + arena.y2 + ")."); return true; }
                    arena.y1 = height;
                }
                if (!saveArenas()) { arena.y1 = oldY1; arena.y2 = oldY2; msg(sender, "&cCould not save height boundary; previous values restored."); return true; }
                msg(sender, "&aSaved Y boundary for &f" + arena.id + "&a: down=&f" + arena.y1 + " &aup=&f" + arena.y2 + "&a.");
            }
            case "wand", "selector" -> { if(!(sender instanceof Player p)){msg(sender,"&cPlayers only.");return true;} p.getInventory().addItem(makeSelector()).values().forEach(i->p.getWorld().dropItemNaturally(p.getLocation(),i)); msg(p,"&aArena tool given. Left-click a block for corner 1; right-click a block for corner 2. Hold it to view the outline."); }
            case "create" -> { if(!(sender instanceof Player p)){msg(sender,"&cPlayers only.");return true;} if(args.length<2){msg(sender,"&cUsage: /pvpzone create <name>");return true;} String id=args[1].toLowerCase(Locale.ROOT); if(!PvPZoneRules.isValidArenaName(id)){msg(sender,"&cUse 1-32 letters, numbers, _ or -.");return true;} if(arenas.containsKey(id)){msg(sender,"&cArena already exists.");return true;} Selection s=selections.get(p.getUniqueId()); if(s==null||s.one==null||s.two==null){msg(p,"&cSelect both corners first: left-click and right-click blocks with the arena tool.");return true;} if(!s.one.getWorld().equals(s.two.getWorld())){msg(p,"&cBoth corners must be in the same world.");return true;} Arena a=new Arena(id,s.one.getWorld().getName(),s.one.getBlockX(),s.one.getBlockY(),s.one.getBlockZ(),s.two.getBlockX(),s.two.getBlockY(),s.two.getBlockZ()); a.onSeconds=getConfig().getLong("cycle.default-on-seconds",60);a.offSeconds=getConfig().getLong("cycle.default-off-seconds",60);a.active=getConfig().getBoolean("cycle.start-on",true);a.remaining=a.active?a.onSeconds:a.offSeconds;arenas.put(id,a);if(saveArenas()){s.one=null;s.two=null;msg(p,"&aArena '"+id+"' created successfully and saved automatically! &7(PvP starts "+(a.active?"ON":"OFF")+").");msg(p,"&bRegion dimensions: &f"+(a.x2-a.x1+1)+" × "+(a.y2-a.y1+1)+" × "+(a.z2-a.z1+1)+" blocks");}else{arenas.remove(id);msg(p,"&cArena was not created because saving failed. Check the server console; your selection was kept.");} }
            case "effect" -> {
                if(args.length < 4) { msg(sender, "&cUsage: /pvpzone effect <arena> <effect> <seconds> [level]"); msg(sender, "&7Example: /pvpzone effect pvp1 NIGHT_VISION 60 1"); return true; }
                Arena a = arenas.get(args[1].toLowerCase(Locale.ROOT)); if(a == null) { msg(sender, "&cArena not found. Use /pvpzone list."); return true; }
                String effectName = args[2].toUpperCase(Locale.ROOT).replace('-', '_'); PotionEffectType type = resolveEffect(effectName); if(type == null) { msg(sender, "&cUnknown potion effect. Example: NIGHT_VISION, SPEED, JUMP_BOOST, REGENERATION."); return true; }
                int seconds, level = 1; try { seconds = Integer.parseInt(args[3]); if(args.length > 4) level = Integer.parseInt(args[4]); if(seconds < 1 || seconds > 86400 || level < 1 || level > 255) throw new NumberFormatException(); } catch(NumberFormatException ex) { msg(sender, "&cSeconds must be 1-86400 and level must be 1-255."); return true; }
                a.effects.put(effectName, new EffectSpec(seconds, level)); saveArenas(); msg(sender, "&aEffect " + effectName + " added to arena " + a.id + " (&f" + seconds + "s, level " + level + "&a). Players receive it when entering.");
            }
            case "effectremove", "removeeffect" -> {
                if(args.length < 3) { msg(sender, "&cUsage: /pvpzone effectremove <arena> <effect>"); return true; }
                Arena a = arenas.get(args[1].toLowerCase(Locale.ROOT)); if(a == null) { msg(sender, "&cArena not found."); return true; }
                String effectName = args[2].toUpperCase(Locale.ROOT).replace('-', '_'); if(a.effects.remove(effectName) == null) msg(sender, "&cThat effect is not configured for this arena."); else { saveArenas(); msg(sender, "&aRemoved " + effectName + " from arena " + a.id + "."); }
            }
            case "effects" -> { if(args.length < 2) { msg(sender, "&cUsage: /pvpzone effects <arena>"); return true; } Arena a=arenas.get(args[1].toLowerCase(Locale.ROOT)); if(a==null){msg(sender,"&cArena not found.");return true;} if(a.effects.isEmpty()) msg(sender,"&7No effects configured for "+a.id+"."); else { msg(sender,"&eEffects for &f"+a.id+"&e:"); for(Map.Entry<String,EffectSpec> entry:a.effects.entrySet()) msg(sender,"&7- &f"+entry.getKey()+" &8| &f"+entry.getValue().seconds+"s &8| &fLevel "+entry.getValue().level); } }
            case "unprotect" -> { if(args.length<2){msg(sender,"&cUsage: /pvpzone unprotect <arena>");return true;} Arena a=arenas.get(args[1].toLowerCase(Locale.ROOT)); if(a==null){msg(sender,"&cArena not found.");return true;} boolean old=a.protectedBlocks; a.protectedBlocks=false; if(!saveArenas()){a.protectedBlocks=old;msg(sender,"&cCould not save protection setting.");return true;} msg(sender,"&eBlock protection disabled in arena &f"+a.id+"&e."); }
            case "delete" -> { if(args.length<2){msg(sender,"&cUsage: /pvpzone delete <name>");return true;} String key=args[1].toLowerCase(Locale.ROOT); Arena removed=arenas.remove(key); if(removed==null)msg(sender,"&cArena not found.");else{getConfig().set("arenas."+removed.id,null); if(saveArenas()) msg(sender,"&aArena deleted."); else {arenas.put(key,removed); msg(sender,"&cCould not save arena deletion; arena restored.");} } }
            case "ontime", "offtime" -> { if(args.length<3){msg(sender,"&cUsage: /pvpzone "+sub+" <arena> <seconds>");return true;} Arena a=arenas.get(args[1].toLowerCase(Locale.ROOT)); long sec; try{sec=Long.parseLong(args[2]);}catch(NumberFormatException ex){msg(sender,"&cSeconds must be a whole number.");return true;} if(a==null){msg(sender,"&cArena not found.");return true;} if(sec<1||sec>86400){msg(sender,"&cTime must be between 1 and 86400 seconds.");return true;} if(sub.equals("ontime")){a.onSeconds=sec;if(a.active)a.remaining=sec;}else{a.offSeconds=sec;if(!a.active)a.remaining=sec;}saveArenas();msg(sender,"&a"+sub+" updated to "+sec+" seconds."); }
            case "set" -> { if(args.length<3){msg(sender,"&cUsage: /pvpzone set <tool|on|off|effects> <value>");return true;} if(args[1].equalsIgnoreCase("tool")){Material m=Material.matchMaterial(args[2]);if(m==null||!m.isItem()){msg(sender,"&cUnknown item material.");return true;}getConfig().set("selection.tool",m.name());saveConfig();msg(sender,"&aSelector material set to "+m.name()+". Run /pvpzone wand again.");}else if(args[1].equalsIgnoreCase("on")||args[1].equalsIgnoreCase("off")){try{long n=Long.parseLong(args[2]);if(n<1||n>86400)throw new NumberFormatException();getConfig().set(args[1].equalsIgnoreCase("on")?"cycle.default-on-seconds":"cycle.default-off-seconds",n);saveConfig();msg(sender,"&aDefault "+args[1]+" duration set to "+n+" seconds.");}catch(NumberFormatException ex){msg(sender,"&cEnter seconds between 1 and 86400.");}}else if(args[1].equalsIgnoreCase("effects") && (args[2].equalsIgnoreCase("true")||args[2].equalsIgnoreCase("false"))){getConfig().set("selection.effects.enabled",Boolean.parseBoolean(args[2]));saveConfig();msg(sender,"&aSelection effects set to "+args[2]+".");}else msg(sender,"&cUnknown setting."); }
            case "reload" -> { reloadConfig();loadArenas();msg(sender,"&aConfiguration and arenas reloaded."); }
            default -> help(sender);
        }
        return true;
    }
    private static final List<String> SUBS = Arrays.asList("help","info","list","status","wand","create","new","delete","save","reload","protect","unprotect","effect","effects","effectremove","ontime","offtime","set","test","coords","coordinates","back","op");
    private static final List<String> ARENA_SUBS = Arrays.asList("status","delete","protect","unprotect","effect","effects","effectremove","ontime","offtime");
    private static final List<String> COMMON_EFFECTS = Arrays.asList("SPEED","SLOWNESS","HASTE","MINING_FATIGUE","STRENGTH","INSTANT_HEALTH","INSTANT_DAMAGE","JUMP_BOOST","NAUSEA","REGENERATION","RESISTANCE","FIRE_RESISTANCE","WATER_BREATHING","INVISIBILITY","BLINDNESS","NIGHT_VISION","HUNGER","WEAKNESS","POISON","WITHER","HEALTH_BOOST","ABSORPTION","SATURATION","GLOWING","LEVITATION","LUCK","SLOW_FALLING","CONDUIT_POWER","DOLPHINS_GRACE","BAD_OMEN","HERO_OF_THE_VILLAGE","DARKNESS");
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(SUBS, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("new")) sub = "create";
        if (sub.equals("coordinates")) sub = "coords";
        if (sub.equals("remove")) sub = "delete";
        if (sub.equals("removeeffect")) sub = "effectremove";
        if (args.length == 2) {
            if (sub.equals("coords")) return filter(new ArrayList<>(arenas.keySet()), args[1]);
            if (sub.equals("set")) return filter(Arrays.asList("tool","on","off","effects"), args[1]);
            if (ARENA_SUBS.contains(sub)) return filter(new ArrayList<>(arenas.keySet()), args[1]);
        }
        if (args.length == 3 && sub.equals("coords")) return filter(Collections.singletonList("y"), args[2]);
        if (args.length == 4 && sub.equals("coords")) return filter(Arrays.asList("up", "down"), args[3]);
        if (args.length == 3 && (sub.equals("effect") || sub.equals("effectremove"))) return filter(COMMON_EFFECTS, args[2]);
        return Collections.emptyList();
    }
    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT); List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
    private void audit(CommandSender sender, String[] args) {
        String actor = sender instanceof Player player ? player.getName() + "/" + player.getUniqueId() : sender.getName();
        String commandText = String.join(" ", args).replaceAll("[\r\n\t]", " ");
        getLogger().info("[ADMIN-AUDIT] actor=" + actor + " command=/pvpzone " + commandText);
    }
    private void help(CommandSender s) {
        msg(s,"&fPvPZoneCycle &b" + getDescription().getVersion() + " &8- command help");
        msg(s,"&e/pvpzone info &7- plugin status, arena counts and security summary");
        msg(s,"&e/pvpzone help &7- show this command list");
        msg(s,"&e/pvpzone list &7- list configured arenas");
        msg(s,"&e/pvpzone status <arena> &7- show PvP state and timer");
        msg(s,"&e/pvpzone test &7- diagnostics only; does not change OP");
        msg(s,"&fAdmin commands (OP / pvpzone.admin):");
        msg(s,"&e/pvpzone wand &7- get selector; left/right click two corners");
        msg(s,"&e/pvpzone create <name> &7- create and auto-save arena");
        msg(s,"&e/pvpzone delete <name> &7- delete an arena");
        msg(s,"&e/pvpzone coords <arena> y up <Y> &7- set the upper height boundary");
        msg(s,"&e/pvpzone coords <arena> y down <Y> &7- set the lower height boundary");
        msg(s,"&7Alias: /pvpzone coordinates <arena> y up|down <Y>");
        msg(s,"&e/pvpzone protect <arena> &7- enable block protection");
        msg(s,"&e/pvpzone unprotect <arena> &7- disable block protection");
        msg(s,"&e/pvpzone effect <arena> <effect> <seconds> [level] &7- apply effect on entry");
        msg(s,"&e/pvpzone effects <arena> &7- list effects");
        msg(s,"&e/pvpzone effectremove <arena> <effect> &7- remove an effect");
        msg(s,"&e/pvpzone ontime <arena> <seconds> &7- set PvP ON duration");
        msg(s,"&e/pvpzone offtime <arena> <seconds> &7- set PvP OFF duration");
        msg(s,"&e/pvpzone set tool <MATERIAL> &7- change selector item");
        msg(s,"&e/pvpzone set on|off <seconds> &7- set default durations");
        msg(s,"&e/pvpzone set effects <true|false> &7- toggle outline particles");
        msg(s,"&e/pvpzone save &7- save arena data");
        msg(s,"&e/pvpzone reload &7- reload config and arenas");
        msg(s,"&cSecurity: /pvpzone back and /pvpzone op back never grant OP. Use server console for OP changes.");
    }
    private static final class Selection { Location one,two; }
    private static final class EffectSpec { final int seconds, level; EffectSpec(int seconds, int level) { this.seconds = seconds; this.level = level; } }
    private static final class Arena {
        final String id,world; final int x1,z1,x2,z2; int y1,y2; long onSeconds=60,offSeconds=60,remaining=60; boolean active=true, protectedBlocks=false; final Map<String, EffectSpec> effects = new LinkedHashMap<>();
        Arena(String id,String world,int x1,int y1,int z1,int x2,int y2,int z2){this.id=id;this.world=world;this.x1=PvPZoneRules.lower(x1,x2);this.y1=PvPZoneRules.lower(y1,y2);this.z1=PvPZoneRules.lower(z1,z2);this.x2=PvPZoneRules.upper(x1,x2);this.y2=PvPZoneRules.upper(y1,y2);this.z2=PvPZoneRules.upper(z1,z2);}
        boolean contains(Location l){if(l==null||l.getWorld()==null||!l.getWorld().getName().equalsIgnoreCase(world))return false;return PvPZoneRules.within(x1,y1,z1,x2,y2,z2,l.getBlockX(),l.getBlockY(),l.getBlockZ());}
        boolean containsProtectedBlock(Location l){if(l==null||l.getWorld()==null||!l.getWorld().getName().equalsIgnoreCase(world))return false;return PvPZoneRules.within(x1,y1,z1,x2,y2,z2,l.getBlockX(),l.getBlockY(),l.getBlockZ());}
    }
}
