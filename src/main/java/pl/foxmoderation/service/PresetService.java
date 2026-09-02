package pl.foxmoderation.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.foxmoderation.model.Preset;
import pl.foxmoderation.model.PunishmentType;

import java.util.*;

public final class PresetService {
    private final Map<String, Preset> presets = new HashMap<>();

    public PresetService(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("presets");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String description = section.getString(key + ".description", "Brak opisu");
            String duration = section.getString(key + ".duration", "10min");
            PunishmentType type = PunishmentType.valueOf(section.getString(key + ".type", "MUTE").toUpperCase(Locale.ROOT));
            presets.put(key.toLowerCase(Locale.ROOT), new Preset(key.toLowerCase(Locale.ROOT), description, duration, type));
        }
    }

    public Optional<Preset> find(String key, PunishmentType type) {
        Preset preset = presets.get(key.toLowerCase(Locale.ROOT));
        if (preset == null || preset.type() != type) {
            return Optional.empty();
        }
        return Optional.of(preset);
    }

    public List<String> keysByType(PunishmentType type) {
        return presets.values().stream().filter(p -> p.type() == type).map(Preset::key).sorted().toList();
    }
}
