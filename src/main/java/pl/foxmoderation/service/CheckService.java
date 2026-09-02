package pl.foxmoderation.service;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.foxmoderation.FoxModerationPlugin;
import pl.foxmoderation.data.DatabaseManager;
import pl.foxmoderation.model.Actor;
import pl.foxmoderation.model.CheckState;
import pl.foxmoderation.model.PunishmentRecord;
import pl.foxmoderation.model.PunishmentType;
import pl.foxmoderation.util.ColorUtil;
import pl.foxmoderation.util.DurationUtil;

import java.time.Instant;
import java.util.*;

public final class CheckService {
    private final FoxModerationPlugin plugin;
    private final DatabaseManager database;
    private final PunishmentService punishmentService;
    private final Map<UUID, RuntimeCheck> runtimeChecks = new HashMap<>();
    private BukkitTask ticker;

    public CheckService(FoxModerationPlugin plugin, DatabaseManager database, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.database = database;
        this.punishmentService = punishmentService;
    }

    public void startTicker() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (CheckState state : database.getChecks()) {
            runtimeChecks.put(state.playerUuid(), new RuntimeCheck(state.checkId(), state.countdownRunning(), state.countdownEndsAt(), null, null));
        }
    }

    public void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
        }
        for (UUID uuid : new ArrayList<>(runtimeChecks.keySet())) {
            destroyBars(Bukkit.getPlayer(uuid));
        }
    }

    public boolean isChecked(UUID uuid) {
        return runtimeChecks.containsKey(uuid);
    }

    public PunishmentRecord beginCheck(Actor actor, Player target) {
        String checkId = database.nextId(PunishmentType.CHECK);
        PunishmentRecord created = new PunishmentRecord(
                checkId,
                PunishmentType.CHECK,
                pl.foxmoderation.model.PunishmentStatus.NONE,
                target.getUniqueId(),
                target.getName(),
                actor.uuid(),
                actor.name(),
                "-",
                null,
                Instant.now(),
                null,
                null,
                null,
                null,
                false
        );
        database.savePunishment(created);
        Instant ends = Instant.now().plusSeconds(plugin.getConfig().getLong("check.countdown-seconds", 300));
        runtimeChecks.put(target.getUniqueId(), new RuntimeCheck(created.id(), true, ends, null, null));
        database.saveCheck(new CheckState(target.getUniqueId(), target.getName(), created.id(), true, ends));
        teleportToCheckLocation(target);
        applyFreeze(target);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (target.isOnline() && isChecked(target.getUniqueId())) {
                target.showTitle(net.kyori.adventure.title.Title.title(
                        ColorUtil.color("&4&lSprawdzanie"),
                        ColorUtil.color("&cJesteś sprawdzany!"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(5), java.time.Duration.ZERO)
                ));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isOnline() && isChecked(target.getUniqueId())) {
                        target.showTitle(net.kyori.adventure.title.Title.title(
                                ColorUtil.color("&cMasz 5min"),
                                ColorUtil.color("&faby wejść na kanał pomoc-głosowa na naszym &9Discord"),
                                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(10), java.time.Duration.ZERO)
                        ));
                    }
                }, 100L);
            }
        }, 100L);
        return created;
    }

    public void startManualInspection(Player target) {
        RuntimeCheck check = runtimeChecks.get(target.getUniqueId());
        if (check == null) {
            return;
        }
        check.countdownRunning = false;
        check.endsAt = null;
        database.saveCheck(new CheckState(target.getUniqueId(), target.getName(), check.checkId, false, null));
        destroyBars(target);
    }

    public PunishmentRecord clearCheck(Actor actor, Player target) {
        RuntimeCheck runtime = runtimeChecks.remove(target.getUniqueId());
        database.deleteCheck(target.getUniqueId());
        destroyBars(target);
        releaseFreeze(target);
        teleportToReleaseLocation(target);
        target.sendMessage(ColorUtil.color("&aPrzepraszamy za niedogodności, w ramach rekompensaty otrzymujesz &61x &x&6&5&0&D&7&E&lK&x&6&A&1&1&8&3&ll&x&6&E&1&4&8&9&lu&x&7&3&1&8&8&E&lc&x&7&7&1&C&9&3&lz &x&8&0&2&3&9&E&lE&x&8&5&2&6&A&3&lp&x&8&9&2&A&A&8&li&x&8&E&2&E&A&D&lc&x&9&2&3&1&B&3&lk&x&9&7&3&5&B&8&li."));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kluczepicki " + target.getName() + " sklepgive");
        PunishmentRecord record = new PunishmentRecord(database.nextId(PunishmentType.CHECK_CLEAR), PunishmentType.CHECK_CLEAR, pl.foxmoderation.model.PunishmentStatus.NONE, target.getUniqueId(), target.getName(), actor.uuid(), actor.name(), "-", null, Instant.now(), null, null, null, runtime == null ? null : runtime.checkId, false);
        database.savePunishment(record);
        return record;
    }

    public PunishmentRecord cheatCheck(Actor actor, Player target, String reason, Long durationSeconds) {
        RuntimeCheck runtime = runtimeChecks.remove(target.getUniqueId());
        database.deleteCheck(target.getUniqueId());
        destroyBars(target);
        releaseFreeze(target);
        teleportToReleaseLocation(target);
        PunishmentRecord ban = punishmentService.ban(actor, target, reason, durationSeconds, runtime == null ? null : runtime.checkId);
        PunishmentRecord record = new PunishmentRecord(database.nextId(PunishmentType.CHECK_CHEAT), PunishmentType.CHECK_CHEAT, pl.foxmoderation.model.PunishmentStatus.ACTIVE, target.getUniqueId(), target.getName(), actor.uuid(), actor.name(), reason, durationSeconds, Instant.now(), durationSeconds == null ? null : Instant.now().plusSeconds(durationSeconds), null, null, runtime == null ? null : runtime.checkId, false);
        database.savePunishment(record);
        return ban;
    }

    public boolean blocksChat(UUID uuid) {
        return runtimeChecks.containsKey(uuid);
    }

    public boolean allowsCommand(UUID uuid, String command) {
        if (!runtimeChecks.containsKey(uuid)) {
            return true;
        }
        String base = command.toLowerCase(Locale.ROOT).split(" ")[0];
        return base.equals("/discord") || base.equals("/dc") || base.equals("/login");
    }

    public void handleQuit(Player player) {
        if (runtimeChecks.containsKey(player.getUniqueId())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + player.getName() + " nocheck");
        }
    }

    public void handleJoin(Player player) {
        RuntimeCheck runtime = runtimeChecks.get(player.getUniqueId());
        if (runtime == null) {
            return;
        }
        applyFreeze(player);
        teleportToCheckLocation(player);
        if (runtime.countdownRunning) {
            runtime.endsAt = Instant.now().plusSeconds(plugin.getConfig().getLong("check.countdown-seconds", 300));
            database.saveCheck(new CheckState(player.getUniqueId(), player.getName(), runtime.checkId, true, runtime.endsAt));
        }
        createBars(player, runtime);
    }

    public void moveStaffToCheck(Player staff) {
        teleportToCheckLocation(staff);
        staff.performCommand("v");
    }

    public void applyFreeze(Player player) {
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setAllowFlight(false);
    }

    public void releaseFreeze(Player player) {
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
    }

    private void tick() {
        for (Iterator<Map.Entry<UUID, RuntimeCheck>> iterator = runtimeChecks.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, RuntimeCheck> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            RuntimeCheck runtime = entry.getValue();
            if (player == null) {
                continue;
            }
            createBars(player, runtime);
            if (runtime.countdownRunning && runtime.endsAt != null) {
                long secondsLeft = Math.max(0, Instant.now().until(runtime.endsAt, java.time.temporal.ChronoUnit.SECONDS));
                updateTimerBar(runtime, player, secondsLeft);
                if (secondsLeft <= 0) {
                    iterator.remove();
                    destroyBars(player);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + player.getName() + " nocheck");
                    database.deleteCheck(player.getUniqueId());
                }
            }
        }
    }

    private void createBars(Player player, RuntimeCheck runtime) {
        if (!runtime.countdownRunning) {
            return;
        }
        if (runtime.discordBar == null) {
            runtime.discordBar = BossBar.bossBar(ColorUtil.color("&fNasz &9discord: &e" + plugin.getConfig().getString("check.discord-url")), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            player.showBossBar(runtime.discordBar);
        }
        if (runtime.timerBar == null) {
            runtime.timerBar = BossBar.bossBar(ColorUtil.color("&fZostało Ci &a5min 0s &fżeby wejść na sprawdzanie"), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            player.showBossBar(runtime.timerBar);
        }
    }

    private void updateTimerBar(RuntimeCheck runtime, Player player, long secondsLeft) {
        if (runtime.timerBar == null) {
            return;
        }
        long total = plugin.getConfig().getLong("check.countdown-seconds", 300);
        float progress = Math.max(0f, Math.min(1f, (float) secondsLeft / (float) total));
        BossBar.Color color = secondsLeft <= 30 ? BossBar.Color.RED : secondsLeft <= 60 ? BossBar.Color.YELLOW : secondsLeft <= 120 ? BossBar.Color.YELLOW : BossBar.Color.GREEN;
        String code = secondsLeft <= 30 ? "&c" : secondsLeft <= 60 ? "&6" : secondsLeft <= 120 ? "&e" : "&a";
        runtime.timerBar.name(ColorUtil.color("&fZostało Ci " + code + DurationUtil.formatDuration(secondsLeft) + " &fżeby wejść na sprawdzanie"));
        runtime.timerBar.progress(progress);
        runtime.timerBar.color(color);
        player.showBossBar(runtime.timerBar);
    }

    private void destroyBars(Player player) {
        if (player == null) {
            return;
        }
        RuntimeCheck runtime = runtimeChecks.get(player.getUniqueId());
        if (runtime == null) {
            return;
        }
        if (runtime.discordBar != null) {
            player.hideBossBar(runtime.discordBar);
            runtime.discordBar = null;
        }
        if (runtime.timerBar != null) {
            player.hideBossBar(runtime.timerBar);
            runtime.timerBar = null;
        }
    }

    private void teleportToCheckLocation(Player player) {
        player.teleport(location("check.location"));
    }

    private void teleportToReleaseLocation(Player player) {
        player.teleport(location("check.release-location"));
    }

    private Location location(String path) {
        FileConfiguration config = plugin.getConfig();
        World world = Bukkit.getWorld(config.getString(path + ".world", "world"));
        return new Location(world, config.getDouble(path + ".x"), config.getDouble(path + ".y"), config.getDouble(path + ".z"), (float) config.getDouble(path + ".yaw"), (float) config.getDouble(path + ".pitch"));
    }

    private static final class RuntimeCheck {
        private final String checkId;
        private boolean countdownRunning;
        private Instant endsAt;
        private BossBar discordBar;
        private BossBar timerBar;

        private RuntimeCheck(String checkId, boolean countdownRunning, Instant endsAt, BossBar discordBar, BossBar timerBar) {
            this.checkId = checkId;
            this.countdownRunning = countdownRunning;
            this.endsAt = endsAt;
            this.discordBar = discordBar;
            this.timerBar = timerBar;
        }
    }
}
