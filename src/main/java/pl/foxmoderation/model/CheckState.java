package pl.foxmoderation.model;

import java.time.Instant;
import java.util.UUID;

public record CheckState(UUID playerUuid, String playerName, String checkId, boolean countdownRunning, Instant countdownEndsAt) {
}
