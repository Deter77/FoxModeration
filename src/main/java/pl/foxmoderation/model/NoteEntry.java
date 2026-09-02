package pl.foxmoderation.model;

import java.time.Instant;

public record NoteEntry(String punishmentId, String author, String content, Instant createdAt, boolean important) {
}
