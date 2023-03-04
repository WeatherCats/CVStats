package org.cubeville.cvstats.leaderboards;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.cubeville.cvstats.CVStats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SerializableAs("Leaderboard")
public class Leaderboard implements ConfigurationSerializable {
    public String id, metric, key, value, title;
    public LeaderboardSortBy sortBy;
    public Integer size, refreshRate;
    private List<Location> displays;
    private Map<String, String> filters;

    private List<String> displayText = List.of("§c§lLeaderboard is not set up");
    TextComponent titleTextComponent;
    String titleString;
    int leaderboardReloadTask = -1;
    private final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");


    public Leaderboard(String id) {
        this.id = id;
        this.displays = new ArrayList<>();
        this.filters = new HashMap<>();
        this.value = "count";
        this.refreshRate = 0;
        this.sortBy = LeaderboardSortBy.DESC;
        setTitle("&#FFF200&lLeaderboard \"" + id + "\"");
    }

    @SuppressWarnings("unchecked")
    public Leaderboard(Map<String, Object> config) {
        this.id = (String) config.get("id");
        this.metric = (String) config.get("metric");
        this.key = (String) config.get("key");
        this.value = (String) config.get("value");
        this.size = (Integer) config.get("size");
        this.refreshRate = (Integer) config.get("refreshrate");
        setTitle((String) config.get("title"));
        List<Location> displays = (List<Location>) config.get("displays");
        this.displays = displays == null ? new ArrayList<>() : displays;
        Map<String, String> filters = (Map<String, String>) config.get("filters");
        this.filters = filters == null ? new HashMap<>() : filters;
        this.sortBy = LeaderboardSortBy.valueOf((String) config.get("sortby"));
        reload();
    }

    @Override
    public Map<String, Object> serialize() {
        return new HashMap<>() {{
            put("id", id);
            put("metric", metric);
            put("key", key);
            put("value", value);
            put("sortby", sortBy.name());
            put("size", size);
            put("refreshrate", refreshRate);
            put("displays", displays);
            put("filters", filters);
            put("title", title);
        }};
    }

    public void addDisplay(Location location) {
        displays.add(location);
        reload();
    }

    public void removeDisplay(int i) {
        clear();
        displays.remove(i);
        reload();
    }

    public List<Location> getDisplays() {
        return displays;
    }

    public void addFilter(String key, String value) {
        filters.put(key, value);
        reload();
    }
    public void removeFilter(String key) {
        filters.remove(key);
        reload();
    }
    public boolean hasFilter(String key) { return filters.containsKey(key); }
    public Map<String, String> getFilters() { return filters; }

    public void setTitle(String input) {
        this.title = input;
        this.titleString = createColorString(input);
        this.titleTextComponent = createColorTextComponent(input);
    }

    private TextComponent createColorTextComponent(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        String[] inBetweens = input.split(HEX_PATTERN.pattern());
        TextComponent tc = new TextComponent(ChatColor.translateAlternateColorCodes('&', inBetweens[0]));
        int i = 1;
        while (matcher.find()) {
            TextComponent colorArea = new TextComponent(ChatColor.translateAlternateColorCodes('&', inBetweens[i]));
            colorArea.setColor(ChatColor.of("#" + matcher.group(1)));
            tc.addExtra(colorArea);
            i++;
        }
        return tc;
    }

    private String createColorString(String input) {
        String inputReplaced = input.replaceAll("(\\&\\#)([0-9A-Fa-f])([0-9A-Fa-f])([0-9A-Fa-f])([0-9A-Fa-f])([0-9A-Fa-f])([0-9A-Fa-f])", "&x&$2&$3&$4&$5&$6&$7");
        String output = org.bukkit.ChatColor.translateAlternateColorCodes('&', inputReplaced);
        return output;
    }

    public TextComponent getTitleTextComponent() {
        return titleTextComponent;
    }

    public String getTitleString() {
        return titleString;
    }

    public boolean isValid() {
        return id != null &&
            metric != null &&
            key != null &&
            title != null &&
            size != null &&
            displays != null &&
            value != null &&
            refreshRate != null &&
            displays.size() > 0;
    }

    private ArmorStand spawnArmorStand(Location loc, String text) {
        ArmorStand as = (ArmorStand) Objects.requireNonNull(loc.getWorld()).spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setGravity(false);
        as.setVisible(false);
        as.setCanPickupItems(false);
        as.setMarker(true);
        as.setCustomName(text);
        as.setCustomNameVisible(true);
        as.addScoreboardTag("CVStats-LeaderboardArmorStand");
        return as;
    }

    public void reload() {
        if (!isValid()) { return; }
        if (leaderboardReloadTask != -1) {
            Bukkit.getScheduler().cancelTask(leaderboardReloadTask);
            leaderboardReloadTask = -1;
        }
        if (this.refreshRate == 0) {
            singleReload();
        } else {
            this.leaderboardReloadTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(CVStats.getInstance(), this::singleReload, 0, (this.refreshRate * 20));
        }
    }

    private void singleReload() {
        updateDisplayText();
        display();
    }

    public void display() {
        for (Location location : displays) {
            if (!location.getChunk().isEntitiesLoaded() && !location.getChunk().isLoaded()) {
                return;
            }
            clear();
            Location loc = location.clone();
            for (String line : displayText) {
                double LINE_SPACE = .3;
                loc.setY(loc.getY() - LINE_SPACE);
                spawnArmorStand(loc, line);
            }
        }
    }

    public void clear() {
        for (Location location : displays) {
            if (!location.getChunk().isEntitiesLoaded() && !location.getChunk().isLoaded()) {
                return;
            }
            List<Entity> nearbyEntities = (List<Entity>) Objects.requireNonNull(location.getWorld())
                .getNearbyEntities(location, 2, 8, 2);
            for (Entity ent : nearbyEntities) {
                if (ent.getScoreboardTags().contains("CVStats-LeaderboardArmorStand")) {
                    ent.remove();
                }
            }
        }
    }

    private List<String> getLeaderboardLines() {
        List<String> leaderboardLines = new ArrayList<>();
        try {
            ResultSet leaderboardResults = CVStats.getDatabase().fetchLeaderboard(this);
            if (leaderboardResults == null) { return List.of("§e§lLeaderboard is empty"); }
            int i = 1;
            while (leaderboardResults.next()) {
                String key = leaderboardResults.getString("key");
                String value = leaderboardResults.getString("value");
                if (this.key.equals("player")) {
                    key = Bukkit.getOfflinePlayer(UUID.fromString(key)).getName();
                }
                leaderboardLines.add(
                    String.format(
                        createColorString(
                        "&#ff7700&l#%d &#ffc012%s&f: %s"
                        ), i, key, value
                    )
                );
                i++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of("§c§lError getting leaderboard values");
        }
        return leaderboardLines;
    }

    public void updateDisplayText() {
        List<String> result = new ArrayList<>();
        List<String> contents = getLeaderboardLines();
        if (contents.size() == 0) contents.add("§e§lLeaderboard is empty");
        String divider = "§f§l---------------";
        result.add(this.titleString);
        result.add(divider);
        result.addAll(getLeaderboardLines());
        result.add(divider);
        this.displayText = result;
    }
}
