package pl.foxmoderation.model;

import java.time.Instant;
import java.util.UUID;

public record PunishmentRecord(
        String id,
        PunishmentType type,
        PunishmentStatus status,
        UUID playerUuid,
        String playerName,
        UUID actorUuid,
        String actorName,
        String reason,
        Long durationSeconds,
        Instant startAt,
        Instant endAt,
        Instant cancelledAt,
        String cancelledBy,
        String linkedPunishmentId,
        boolean importantNote
) {
}
