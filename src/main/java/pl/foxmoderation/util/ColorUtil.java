package pl.foxmoderation.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.stream.Collectors;

public final class ColorUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private ColorUtil() {
    }

    public static Component color(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static Component guiColor(String text) {
        return color(text).decoration(TextDecoration.ITALIC, false);
    }

    public static String plainLegacy(String text) {
        return text == null ? "" : text;
    }

    public static List<Component> colorLines(List<String> lines) {
        return lines.stream().map(ColorUtil::color).collect(Collectors.toList());
    }

    public static List<Component> guiLines(List<String> lines) {
        return lines.stream().map(ColorUtil::guiColor).collect(Collectors.toList());
    }

    public static String stripTags(String input) {
        return MINI.stripTags(input == null ? "" : input);
    }
}
