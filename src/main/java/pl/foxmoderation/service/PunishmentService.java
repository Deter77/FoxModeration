package pl.foxmoderation.service;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import pl.foxmoderation.FoxModerationPlugin;
import pl.foxmoderation.data.DatabaseManager;
import pl.foxmoderation.model.*;
import pl.foxmoderation.util.DurationUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class PunishmentService {
    private final FoxModerationPlugin plugin;
    private final DatabaseManager database;

    public PunishmentService(FoxModerationPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public PunishmentRecord warn(Actor actor, OfflinePlayer target, String reason) {
        PunishmentRecord record = createRecord(actor, target, PunishmentType.WARN, reason, 90L * 86400L, null, false);
        database.savePunishment(record);
        if (database.countActiveWarns(target.getUniqueId()) >= 5) {
            ban(actor, target, "warns", null, null);
        }
        Player player = target.getPlayer();
        if (player != null) {
            player.showTitle(net.kyori.adventure.title.Title.title(pl.foxmoderation.util.ColorUtil.color("&4&lOstrzeżenie!"), pl.foxmoderation.util.ColorUtil.color("&c" + reason)));
        }
        return record;
    }

    public PunishmentRecord kick(Actor actor, Player target, String reason) {
        PunishmentRecord record = createRecord(actor, target, PunishmentType.KICK, reason, null, null, false);
        database.savePunishment(record);
        target.kick(pl.foxmoderation.util.ColorUtil.color("&cZostałeś wyrzucony z serwera za: " + reason));
        return record;
    }

    public PunishmentRecord mute(Actor actor, OfflinePlayer target, String reason, Long durationSeconds, String linkedId) {
        if (isActive(target.getUniqueId(), PunishmentType.MUTE)) {
            throw new IllegalStateException("already-muted");
        }
        Escalation escalation = escalateMute(target.getUniqueId(), durationSeconds, reason);
        if (escalation.convertToBan()) {
            return ban(actor, target, reason, durationSeconds, linkedId);
        }
        PunishmentRecord record = createRecord(actor, target, PunishmentType.MUTE, reason, escalation.durationSeconds(), linkedId, false);
        database.savePunishment(record);
        Player player = target.getPlayer();
        if (player != null) {
            player.showTitle(net.kyori.adventure.title.Title.title(pl.foxmoderation.util.ColorUtil.color("&4&lMute!"), pl.foxmoderation.util.ColorUtil.color("&cZostałeś wyciszony")));
            player.sendMessage(pl.foxmoderation.util.ColorUtil.color("&fPowód wyciszenia: &c" + reason));
            player.sendMessage(pl.foxmoderation.util.ColorUtil.color("&eCzas wyciszenia: " + DurationUtil.formatDuration(escalation.durationSeconds())));
        }
        return record;
    }

    public PunishmentRecord ban(Actor actor, OfflinePlayer target, String reason, Long durationSeconds, String linkedId) {
        if (isActive(target.getUniqueId(), PunishmentType.BAN)) {
            throw new IllegalStateException("already-banned");
        }
        Escalation escalation = escalateBan(target.getUniqueId(), durationSeconds);
        PunishmentRecord record = createRecord(actor, target, PunishmentType.BAN, reason, escalation.durationSeconds(), linkedId, false);
        database.savePunishment(record);
        String reasonWithId = reason + " | ID: #" + record.id();
        Date until = escalation.durationSeconds() == null ? null : Date.from(record.endAt());
        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Zostałeś zbanowany! Powód: " + reasonWithId, until, actor.name());
        Player player = target.getPlayer();
        if (player != null) {
            player.kick(pl.foxmoderation.util.ColorUtil.color("&cZostałeś zbanowany!\n&fPowód: &c" + reasonWithId + "\n&fCzas do końca bana: &e" + DurationUtil.formatDuration(escalation.durationSeconds())));
        }
        return record;
    }

    public PunishmentRecord unmute(Actor actor, OfflinePlayer target, String reason) {
        PunishmentRecord active = database.getLatestActive(target.getUniqueId(), PunishmentType.MUTE).orElseThrow();
        database.updateStatus(active.id(), PunishmentStatus.REVOKED, Instant.now(), actor.name());
        PunishmentRecord record = createRecord(actor, target, PunishmentType.UNMUTE, reason, null, active.id(), false);
        database.savePunishment(record);
        Player player = target.getPlayer();
        if (player != null) {
            player.showTitle(net.kyori.adventure.title.Title.title(pl.foxmoderation.util.ColorUtil.color("&aUnmute"), pl.foxmoderation.util.ColorUtil.color("&aNie jesteś już wyciszony")));
            player.sendMessage(pl.foxmoderation.util.ColorUtil.color("&fPowód: &a" + reason));
        }
        return record;
    }

    public PunishmentRecord unban(Actor actor, OfflinePlayer target, String reason) {
        PunishmentRecord active = database.getLatestActive(target.getUniqueId(), PunishmentType.BAN).orElseThrow();
        database.updateStatus(active.id(), PunishmentStatus.REVOKED, Instant.now(), actor.name());
        Bukkit.getBanList(BanList.Type.NAME).pardon(active.playerName());
        PunishmentRecord record = createRecord(actor, target, PunishmentType.UNBAN, reason, null, active.id(), false);
        database.savePunishment(record);
        return record;
    }

    public PunishmentRecord note(Actor actor, OfflinePlayer target, String content, boolean important) {
        PunishmentRecord record = createRecord(actor, target, PunishmentType.NOTE, content, null, null, important);
        database.savePunishment(record);
        return record;
    }

    public PunishmentRecord deletePunishment(Actor actor, String id) {
        PunishmentRecord punishment = database.getPunishment(id).orElseThrow();
        if (!(punishment.type() == PunishmentType.WARN || punishment.type() == PunishmentType.MUTE || punishment.type() == PunishmentType.BAN || punishment.type() == PunishmentType.CHECK_CHEAT)) {
            throw new IllegalArgumentException("invalid-type");
        }
        database.updateStatus(id, PunishmentStatus.REVOKED, Instant.now(), actor.name());
        return database.getPunishment(id).orElseThrow();
    }

    public void tickExpirations() {
        // lazy expiration via reads/checks; active punishment GUI still recalculated below
    }

    public boolean isMuted(UUID uuid) {
        Optional<PunishmentRecord> mute = database.getLatestActive(uuid, PunishmentType.MUTE);
        if (mute.isEmpty()) {
            return false;
        }
        return !isExpired(mute.get());
    }

    public boolean isBanned(UUID uuid) {
        Optional<PunishmentRecord> ban = database.getLatestActive(uuid, PunishmentType.BAN);
        if (ban.isEmpty()) {
            return false;
        }
        return !isExpired(ban.get());
    }

    public List<PunishmentRecord> history(UUID uuid) {
        refreshStatuses(uuid);
        return database.getPunishments(uuid);
    }

    public Optional<PunishmentRecord> findById(String id) {
        return database.getPunishment(id);
    }

    public List<PunishmentRecord> staffLog(String actorName) {
        return database.getPunishmentsByActor(actorName, 10);
    }

    public long count(UUID uuid, PunishmentType type) {
        return history(uuid).stream().filter(p -> p.type() == type).count();
    }

    public long activeWarns(UUID uuid) {
        refreshStatuses(uuid);
        return database.countActiveWarns(uuid);
    }

    public Optional<PunishmentRecord> activeMute(UUID uuid) {
        refreshStatuses(uuid);
        return database.getLatestActive(uuid, PunishmentType.MUTE);
    }

    public Optional<PunishmentRecord> activeBan(UUID uuid) {
        refreshStatuses(uuid);
        return database.getLatestActive(uuid, PunishmentType.BAN);
    }

    private void refreshStatuses(UUID uuid) {
        for (PunishmentRecord record : database.getPunishments(uuid)) {
            if (record.status() == PunishmentStatus.ACTIVE && isExpired(record)) {
                database.updateStatus(record.id(), PunishmentStatus.EXPIRED, null, null);
            }
        }
    }

    private boolean isActive(UUID uuid, PunishmentType type) {
        Optional<PunishmentRecord> existing = database.getLatestActive(uuid, type);
        if (existing.isPresent() && isExpired(existing.get())) {
            database.updateStatus(existing.get().id(), PunishmentStatus.EXPIRED, null, null);
            return false;
        }
        return existing.isPresent();
    }

    private boolean isExpired(PunishmentRecord record) {
        return record.endAt() != null && Instant.now().isAfter(record.endAt());
    }

    private PunishmentRecord createRecord(Actor actor, OfflinePlayer target, PunishmentType type, String reason, Long durationSeconds, String linkedId, boolean important) {
        Instant start = Instant.now();
        Instant end = durationSeconds == null ? null : start.plusSeconds(durationSeconds);
        return new PunishmentRecord(
                database.nextId(type),
                type,
                type == PunishmentType.KICK || type == PunishmentType.UNBAN || type == PunishmentType.UNMUTE || type == PunishmentType.CHECK || type == PunishmentType.CHECK_CLEAR ? PunishmentStatus.NONE : PunishmentStatus.ACTIVE,
                target.getUniqueId(),
                target.getName() == null ? target.getUniqueId().toString() : target.getName(),
                actor.uuid(),
                actor.name(),
                reason,
                durationSeconds,
                start,
                end,
                null,
                null,
                linkedId,
                important
        );
    }

    private Escalation escalateMute(UUID uuid, Long durationSeconds, String reason) {
        if (database.countPunishmentsSince(uuid, PunishmentType.MUTE, Instant.now().minus(30, ChronoUnit.DAYS)) >= 5) {
            return new Escalation(true, durationSeconds);
        }
        double multiplier = 1.0;
        if (database.countPunishmentsSince(uuid, PunishmentType.MUTE, Instant.now().minus(96, ChronoUnit.HOURS)) >= 3) {
            multiplier = 5.0;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.MUTE, Instant.now().minus(48, ChronoUnit.HOURS)) >= 2) {
            multiplier = 3.0;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.MUTE, Instant.now().minus(24, ChronoUnit.HOURS)) >= 1) {
            multiplier = 2.0;
        }
        Long result = durationSeconds == null ? null : Math.round(durationSeconds * multiplier);
        return new Escalation(false, result);
    }

    private Escalation escalateBan(UUID uuid, Long durationSeconds) {
        boolean perm = false;
        double multiplier = 1.0;

        if (database.countPunishmentsSince(uuid, PunishmentType.BAN, Instant.now().minus(1440, ChronoUnit.DAYS)) >= 4) {
            perm = true;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.BAN, Instant.now().minus(360, ChronoUnit.DAYS)) >= 3) {
            perm = true;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.BAN, Instant.now().minus(720, ChronoUnit.DAYS)) >= 3) {
            multiplier = 4.0;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.BAN, Instant.now().minus(180, ChronoUnit.DAYS)) >= 2) {
            multiplier = 3.0;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.BAN, Instant.now().minus(360, ChronoUnit.DAYS)) >= 2) {
            multiplier = 2.5;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.BAN, Instant.now().minus(15, ChronoUnit.DAYS)) >= 1) {
            multiplier = 2.0;
        } else if (database.countPunishmentsSince(uuid, PunishmentType.BAN, Instant.now().minus(30, ChronoUnit.DAYS)) >= 1) {
            multiplier = 1.5;
        }

        Long result = perm || durationSeconds == null ? null : Math.round(durationSeconds * multiplier);
        return new Escalation(false, result);
    }


    private record Escalation(boolean convertToBan, Long durationSeconds) {
    }
}
