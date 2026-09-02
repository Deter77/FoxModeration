package pl.foxmoderation.model;

import java.util.UUID;

public record Actor(String name, UUID uuid, boolean consoleActor, boolean grimActor, int hierarchyWeight) {
    public static Actor forConsole() {
        return new Actor("Console", null, true, false, Integer.MAX_VALUE);
    }

    public static Actor forGrim() {
        return new Actor("GrimAC", null, false, true, Integer.MAX_VALUE - 1);
    }
}
