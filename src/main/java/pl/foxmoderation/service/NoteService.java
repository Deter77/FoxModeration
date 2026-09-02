package pl.foxmoderation.service;

import org.bukkit.OfflinePlayer;
import pl.foxmoderation.data.DatabaseManager;
import pl.foxmoderation.model.Actor;
import pl.foxmoderation.model.NoteEntry;
import pl.foxmoderation.model.PunishmentRecord;
import pl.foxmoderation.model.PunishmentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NoteService {
    private final DatabaseManager database;
    private final PunishmentService punishmentService;

    public NoteService(DatabaseManager database, PunishmentService punishmentService) {
        this.database = database;
        this.punishmentService = punishmentService;
    }

    public PunishmentRecord add(Actor actor, OfflinePlayer target, String content, boolean important) {
        if (!important && database.getNotes(target.getUniqueId(), false).size() >= 5) {
            throw new IllegalStateException("too-many-notes");
        }
        if (important && !database.getNotes(target.getUniqueId(), true).isEmpty()) {
            removeImportant(actor, target.getUniqueId());
        }
        return punishmentService.note(actor, target, content, important);
    }

    public void remove(Actor actor, UUID playerUuid, int index) {
        List<NoteEntry> notes = database.getNotes(playerUuid, false);
        NoteEntry note = notes.get(index - 1);
        database.updateStatus(note.punishmentId(), PunishmentStatus.DELETED, Instant.now(), actor.name());
    }

    public void removeOldest(Actor actor, UUID playerUuid) {
        remove(actor, playerUuid, 1);
    }

    public void removeAll(Actor actor, UUID playerUuid) {
        for (NoteEntry entry : database.getNotes(playerUuid, false)) {
            database.updateStatus(entry.punishmentId(), PunishmentStatus.DELETED, Instant.now(), actor.name());
        }
    }

    public PunishmentRecord edit(Actor actor, OfflinePlayer target, int index, String content, boolean important) {
        if (important) {
            removeImportant(actor, target.getUniqueId());
            return punishmentService.note(actor, target, content, true);
        }
        remove(actor, target.getUniqueId(), index);
        return punishmentService.note(actor, target, content, false);
    }

    public void removeImportant(Actor actor, UUID uuid) {
        for (NoteEntry note : database.getNotes(uuid, true)) {
            database.updateStatus(note.punishmentId(), PunishmentStatus.DELETED, Instant.now(), actor.name());
        }
    }

    public List<NoteEntry> notes(UUID uuid) {
        return database.getNotes(uuid, false);
    }

    public List<NoteEntry> important(UUID uuid) {
        return database.getNotes(uuid, true);
    }
}
