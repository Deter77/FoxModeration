package pl.foxmoderation.model;

public enum PunishmentType {
    WARN("WR", "Warn"),
    MUTE("MT", "Mute"),
    KICK("KC", "Kick"),
    BAN("BN", "Ban"),
    UNMUTE("UM", "Unmute"),
    UNBAN("UB", "Unban"),
    CHECK("CH", "Check"),
    CHECK_CLEAR("CL", "Checkclear"),
    CHECK_CHEAT("CC", "Checkcheat"),
    NOTE("NT", "Note");

    private final String prefix;
    private final String displayName;

    PunishmentType(String prefix, String displayName) {
        this.prefix = prefix;
        this.displayName = displayName;
    }

    public String prefix() {
        return prefix;
    }

    public String displayName() {
        return displayName;
    }
}
