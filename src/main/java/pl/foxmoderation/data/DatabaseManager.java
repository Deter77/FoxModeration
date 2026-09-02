package pl.foxmoderation.data;

import pl.foxmoderation.model.CheckState;
import pl.foxmoderation.model.NoteEntry;
import pl.foxmoderation.model.PunishmentRecord;
import pl.foxmoderation.model.PunishmentStatus;
import pl.foxmoderation.model.PunishmentType;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class DatabaseManager {
    private final File file;
    private Connection connection;

    public DatabaseManager(File dataFolder) {
        this.file = new File(dataFolder, "foxmoderation.db");
    }

    public void connect() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        actor_uuid TEXT,
                        actor_name TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        duration_seconds INTEGER,
                        start_at INTEGER NOT NULL,
                        end_at INTEGER,
                        cancelled_at INTEGER,
                        cancelled_by TEXT,
                        linked_punishment_id TEXT,
                        important_note INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_punishments_player ON punishments(player_uuid, start_at DESC)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS counters (prefix TEXT PRIMARY KEY, value INTEGER NOT NULL)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS checks (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        check_id TEXT NOT NULL,
                        countdown_running INTEGER NOT NULL,
                        countdown_ends_at INTEGER
                    )
                    """);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    public synchronized String nextId(PunishmentType type) {
        try {
            connection.setAutoCommit(false);
            long current;
            try (PreparedStatement select = connection.prepareStatement("SELECT value FROM counters WHERE prefix = ?")) {
                select.setString(1, type.prefix());
                try (ResultSet rs = select.executeQuery()) {
                    current = rs.next() ? rs.getLong(1) : 0L;
                }
            }
            long next = current + 1;
            try (PreparedStatement upsert = connection.prepareStatement("INSERT INTO counters(prefix, value) VALUES(?, ?) ON CONFLICT(prefix) DO UPDATE SET value = excluded.value")) {
                upsert.setString(1, type.prefix());
                upsert.setLong(2, next);
                upsert.executeUpdate();
            }
            connection.commit();
            connection.setAutoCommit(true);
            return type.prefix() + next;
        } catch (SQLException exception) {
            throw new IllegalStateException("Nie udało się wygenerować ID", exception);
        }
    }

    public synchronized void savePunishment(PunishmentRecord record) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO punishments(id, type, status, player_uuid, player_name, actor_uuid, actor_name, reason,
                duration_seconds, start_at, end_at, cancelled_at, cancelled_by, linked_punishment_id, important_note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, record.id());
            ps.setString(2, record.type().name());
            ps.setString(3, record.status().name());
            ps.setString(4, record.playerUuid().toString());
            ps.setString(5, record.playerName());
            ps.setString(6, record.actorUuid() == null ? null : record.actorUuid().toString());
            ps.setString(7, record.actorName());
            ps.setString(8, record.reason());
            if (record.durationSeconds() == null) ps.setNull(9, Types.BIGINT); else ps.setLong(9, record.durationSeconds());
            ps.setLong(10, record.startAt().getEpochSecond());
            if (record.endAt() == null) ps.setNull(11, Types.BIGINT); else ps.setLong(11, record.endAt().getEpochSecond());
            if (record.cancelledAt() == null) ps.setNull(12, Types.BIGINT); else ps.setLong(12, record.cancelledAt().getEpochSecond());
            ps.setString(13, record.cancelledBy());
            ps.setString(14, record.linkedPunishmentId());
            ps.setInt(15, record.importantNote() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Nie udało się zapisać kary", exception);
        }
    }

    public synchronized void updateStatus(String id, PunishmentStatus status, Instant cancelledAt, String cancelledBy) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE punishments SET status = ?, cancelled_at = ?, cancelled_by = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            if (cancelledAt == null) ps.setNull(2, Types.BIGINT); else ps.setLong(2, cancelledAt.getEpochSecond());
            ps.setString(3, cancelledBy);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Nie udało się zaktualizować kary", exception);
        }
    }

    public synchronized Optional<PunishmentRecord> getPunishment(String id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM punishments WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapPunishment(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Nie udało się pobrać kary", exception);
        }
    }

    public synchronized List<PunishmentRecord> getPunishments(UUID playerUuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM punishments WHERE player_uuid = ? ORDER BY start_at DESC")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<PunishmentRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(mapPunishment(rs));
                }
                return records;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Nie udało się pobrać historii", exception);
        }
    }

    public synchronized List<PunishmentRecord> getPunishmentsByActor(String actorName, int limit) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM punishments WHERE actor_name = ? ORDER BY start_at DESC LIMIT ?")) {
            ps.setString(1, actorName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<PunishmentRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(mapPunishment(rs));
                }
                return records;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Nie udało się pobrać staff logu", exception);
        }
    }

    public synchronized Optional<UUID> findKnownPlayerUuid(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT player_uuid FROM punishments WHERE lower(player_name) = lower(?) ORDER BY start_at DESC LIMIT 1")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized List<String> getKnownPlayerNames() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT DISTINCT player_name FROM punishments WHERE player_name IS NOT NULL AND player_name != '' ORDER BY player_name ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
                return names;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized Optional<PunishmentRecord> getLatestActive(UUID playerUuid, PunishmentType type) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM punishments WHERE player_uuid = ? AND type = ? AND status = ? ORDER BY start_at DESC LIMIT 1")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, type.name());
            ps.setString(3, PunishmentStatus.ACTIVE.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapPunishment(rs)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized long countPunishmentsSince(UUID playerUuid, PunishmentType type, Instant since) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM punishments WHERE player_uuid = ? AND type = ? AND (start_at >= ? OR end_at >= ?)")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, type.name());
            ps.setLong(3, since.getEpochSecond());
            ps.setLong(4, since.getEpochSecond());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized long countActiveWarns(UUID playerUuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM punishments WHERE player_uuid = ? AND type = ? AND status = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, PunishmentType.WARN.name());
            ps.setString(3, PunishmentStatus.ACTIVE.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized List<NoteEntry> getNotes(UUID playerUuid, boolean importantOnly) {
        String sql = "SELECT * FROM punishments WHERE player_uuid = ? AND type = ? AND important_note = ? AND status != ? ORDER BY start_at ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, PunishmentType.NOTE.name());
            ps.setInt(3, importantOnly ? 1 : 0);
            ps.setString(4, PunishmentStatus.DELETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<NoteEntry> notes = new ArrayList<>();
                while (rs.next()) {
                    PunishmentRecord punishment = mapPunishment(rs);
                    notes.add(new NoteEntry(punishment.id(), punishment.actorName(), punishment.reason(), punishment.startAt(), punishment.importantNote()));
                }
                return notes;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized void saveCheck(CheckState state) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO checks(player_uuid, player_name, check_id, countdown_running, countdown_ends_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name, check_id=excluded.check_id, countdown_running=excluded.countdown_running, countdown_ends_at=excluded.countdown_ends_at")) {
            ps.setString(1, state.playerUuid().toString());
            ps.setString(2, state.playerName());
            ps.setString(3, state.checkId());
            ps.setInt(4, state.countdownRunning() ? 1 : 0);
            if (state.countdownEndsAt() == null) ps.setNull(5, Types.BIGINT); else ps.setLong(5, state.countdownEndsAt().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized void deleteCheck(UUID playerUuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM checks WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public synchronized List<CheckState> getChecks() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM checks")) {
            try (ResultSet rs = ps.executeQuery()) {
                List<CheckState> states = new ArrayList<>();
                while (rs.next()) {
                    states.add(new CheckState(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getString("check_id"),
                            rs.getInt("countdown_running") == 1,
                            rs.getObject("countdown_ends_at") == null ? null : Instant.ofEpochSecond(rs.getLong("countdown_ends_at"))
                    ));
                }
                return states;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PunishmentRecord mapPunishment(ResultSet rs) throws SQLException {
        return new PunishmentRecord(
                rs.getString("id"),
                PunishmentType.valueOf(rs.getString("type")),
                PunishmentStatus.valueOf(rs.getString("status")),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("actor_uuid") == null ? null : UUID.fromString(rs.getString("actor_uuid")),
                rs.getString("actor_name"),
                rs.getString("reason"),
                rs.getObject("duration_seconds") == null ? null : rs.getLong("duration_seconds"),
                Instant.ofEpochSecond(rs.getLong("start_at")),
                rs.getObject("end_at") == null ? null : Instant.ofEpochSecond(rs.getLong("end_at")),
                rs.getObject("cancelled_at") == null ? null : Instant.ofEpochSecond(rs.getLong("cancelled_at")),
                rs.getString("cancelled_by"),
                rs.getString("linked_punishment_id"),
                rs.getInt("important_note") == 1
        );
    }
}
