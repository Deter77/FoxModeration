package pl.foxmoderation.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pl.foxmoderation.FoxModerationPlugin;
import pl.foxmoderation.model.NoteEntry;
import pl.foxmoderation.model.PunishmentRecord;
import pl.foxmoderation.model.PunishmentStatus;
import pl.foxmoderation.model.PunishmentType;
import pl.foxmoderation.service.NoteService;
import pl.foxmoderation.service.PunishmentService;
import pl.foxmoderation.util.ColorUtil;
import pl.foxmoderation.util.DurationUtil;
import pl.foxmoderation.util.InventoryUtil;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GuiService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm").withZone(ZoneId.systemDefault());

    private final FoxModerationPlugin plugin;
    private final PunishmentService punishmentService;
    private final NoteService noteService;

    public GuiService(FoxModerationPlugin plugin, PunishmentService punishmentService, NoteService noteService) {
        this.plugin = plugin;
        this.punishmentService = punishmentService;
        this.noteService = noteService;
    }

    public void openNotes(Player viewer, OfflinePlayer target) {
        Inventory inventory = Bukkit.createInventory(null, 36, ColorUtil.guiColor(plugin.getConfig().getString("guis.notes-title")));
        inventory.setItem(31, skull(target, "&fNotatki: " + target.getName()));
        List<NoteEntry> important = noteService.important(target.getUniqueId());
        if (!important.isEmpty()) {
            NoteEntry note = important.getFirst();
            inventory.setItem(13, InventoryUtil.item(Material.EMERALD, "&6&lWażna Notatka", List.of(
                    "&f" + note.content(),
                    "&3Autor: &f" + note.author(),
                    "&bData utworzenia: &f" + FORMATTER.format(note.createdAt())
            )));
        }
        List<NoteEntry> notes = noteService.notes(target.getUniqueId());
        int[] slots = {20, 21, 22, 23, 24};
        for (int i = 0; i < Math.min(notes.size(), slots.length); i++) {
            NoteEntry note = notes.get(i);
            inventory.setItem(slots[i], InventoryUtil.item(Material.EMERALD, "&7Notatka", List.of(
                    "&f" + note.content(),
                    "&3Autor: &f" + note.author(),
                    "&bData utworzenia: &f" + FORMATTER.format(note.createdAt())
            )));
        }
        viewer.openInventory(inventory);
    }

    public void openPunishment(Player viewer, PunishmentRecord record) {
        Inventory inventory = Bukkit.createInventory(null, 27, ColorUtil.guiColor(plugin.getConfig().getString("guis.punishment-title")));
        inventory.setItem(13, InventoryUtil.item(Material.EMERALD, "&x&1&9&5&1&8&B&lI&x&1&C&5&A&9&B&ln&x&2&0&6&4&A&B&lf&x&2&3&6&D&B&B&lo", punishmentLore(record)));
        viewer.openInventory(inventory);
    }

    public void openHistory(Player viewer, OfflinePlayer target, int page) {
        Inventory inventory = Bukkit.createInventory(null, 54, ColorUtil.guiColor(plugin.getConfig().getString("guis.history-title")));
        inventory.setItem(45, skull(target, "&fHistoria: " + target.getName()));
        inventory.setItem(48, InventoryUtil.item(Material.EMERALD, "&aPoprzednia strona", List.of("&7Kliknij aby wrócić")));
        inventory.setItem(49, InventoryUtil.item(Material.EMERALD, "&x&1&9&5&1&8&B&lI&x&1&C&5&A&9&B&ln&x&2&0&6&4&A&B&lf&x&2&3&6&D&B&B&lo", List.of("&6Statusy kar:", "&fAktywny &7- &aZielony", "&fWygasły &7- &6Pomarańczowy", "&fAnulowany &7- &cCzerwony", "&fUsunięty &7- &8Szary")));
        inventory.setItem(50, InventoryUtil.item(Material.EMERALD, "&aNastępna strona", List.of("&7Kliknij aby przejść dalej")));
        List<PunishmentRecord> history = punishmentService.history(target.getUniqueId());
        int from = page * 45;
        int to = Math.min(history.size(), from + 45);
        for (int i = from; i < to; i++) {
            PunishmentRecord record = history.get(i);
            inventory.setItem(i - from, historyItem(record));
        }
        viewer.openInventory(inventory);
    }

    public void openCheckInfo(Player viewer, OfflinePlayer target) {
        Inventory inventory = Bukkit.createInventory(null, 27, ColorUtil.guiColor(plugin.getConfig().getString("guis.checkinfo-title")));
        inventory.setItem(22, skull(target, "&fInfo: " + target.getName()));
        String ban = punishmentService.activeBan(target.getUniqueId()).map(p -> DurationUtil.formatDuration(p.endAt() == null ? null : java.time.Instant.now().until(p.endAt(), java.time.temporal.ChronoUnit.SECONDS))).orElse("brak");
        String mute = punishmentService.activeMute(target.getUniqueId()).map(p -> DurationUtil.formatDuration(p.endAt() == null ? null : java.time.Instant.now().until(p.endAt(), java.time.temporal.ChronoUnit.SECONDS))).orElse("brak");
        inventory.setItem(13, InventoryUtil.item(Material.EMERALD, "&x&1&9&5&1&8&B&lI&x&1&C&5&A&9&B&ln&x&2&0&6&4&A&B&lf&x&2&3&6&D&B&B&lo", List.of(
                "&bAktywne kary:",
                "&cBan: &f" + ban,
                "&6Mute: &f" + mute,
                "&eWarny: &f" + punishmentService.activeWarns(target.getUniqueId()) + " aktywnych",
                " ",
                "&6Historia:",
                "&cBany: &f" + punishmentService.count(target.getUniqueId(), PunishmentType.BAN),
                "&4Kicki: &f" + punishmentService.count(target.getUniqueId(), PunishmentType.KICK),
                "&6Mute: &f" + punishmentService.count(target.getUniqueId(), PunishmentType.MUTE),
                "&eWarny: &f" + punishmentService.count(target.getUniqueId(), PunishmentType.WARN),
                "&aChecki: &f" + punishmentService.count(target.getUniqueId(), PunishmentType.CHECK)
        )));
        viewer.openInventory(inventory);
    }

    public void openStaffLog(Player viewer, String actorName) {
        Inventory inventory = Bukkit.createInventory(null, 27, ColorUtil.guiColor(plugin.getConfig().getString("guis.stafflog-title")));
        List<String> lore = new ArrayList<>();
        for (PunishmentRecord record : punishmentService.staffLog(actorName)) {
            lore.add("&f" + FORMATTER.format(record.startAt()) + " &7- &e#" + record.id() + " &7- &f" + record.type().displayName());
        }
        if (lore.isEmpty()) {
            lore.add("&7Brak akcji.");
        }
        inventory.setItem(13, InventoryUtil.item(Material.EMERALD, "&fOstatnie 10 akcji: " + actorName, lore));
        viewer.openInventory(inventory);
    }

    private ItemStack historyItem(PunishmentRecord record) {
        Material material = switch (record.status()) {
            case ACTIVE -> Material.LIME_CONCRETE;
            case EXPIRED -> Material.ORANGE_CONCRETE;
            case REVOKED -> Material.RED_CONCRETE;
            case DELETED -> Material.GRAY_CONCRETE;
            default -> Material.WHITE_CONCRETE;
        };
        return InventoryUtil.item(material, "&f&l" + record.type().displayName(), List.of(
                "&9ID: &f#" + record.id(),
                "&6Powód: &f" + record.reason(),
                "&5Czas Trwania: &f" + (record.durationSeconds() == null ? "-" : DurationUtil.formatDuration(record.durationSeconds())),
                "&dCzas Startu: &f" + FORMATTER.format(record.startAt()),
                " ",
                "&eKliknij, aby zobaczyć więcej"
        ));
    }

    private List<String> punishmentLore(PunishmentRecord record) {
        return List.of(
                "&9ID: &f#" + record.id(),
                "&eTyp: &f" + record.type().displayName(),
                "&2UUID Gracza: &f" + record.playerUuid(),
                "&aNick Gracza: &f" + record.playerName(),
                " ",
                "&4UUID Admina: &f" + (record.actorUuid() == null ? "-" : record.actorUuid()),
                "&cNick Admina: &f" + record.actorName(),
                " ",
                "&6Powód: &f" + record.reason(),
                "&5Czas Trwania: &f" + (record.durationSeconds() == null ? "-" : DurationUtil.formatDuration(record.durationSeconds())),
                "&dCzas Startu: &f" + FORMATTER.format(record.startAt()),
                "&dCzas Końca: &f" + (record.endAt() == null ? "-" : FORMATTER.format(record.endAt())),
                " ",
                "&fStatus: " + switch (record.status()) {
                    case ACTIVE -> "&aAktywny";
                    case EXPIRED -> "&6Wygasły";
                    case REVOKED -> "&6Anulowany";
                    case DELETED -> "&8Usunięty";
                    case NONE -> "&f-";
                },
                " ",
                "&dCzas Anulowania: &f" + (record.cancelledAt() == null ? "-" : FORMATTER.format(record.cancelledAt())),
                "&cNick Admina: &f" + (record.cancelledBy() == null ? "-" : record.cancelledBy()),
                " ",
                "&1ID Kary Wyjściowej: &f" + (record.linkedPunishmentId() == null ? "-" : "#" + record.linkedPunishmentId())
        );
    }

    private ItemStack skull(OfflinePlayer target, String name) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(ColorUtil.guiColor(name));
        stack.setItemMeta(meta);
        return stack;
    }
}
