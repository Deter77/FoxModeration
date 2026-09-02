package pl.foxmoderation.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import pl.foxmoderation.FoxModerationPlugin;
import pl.foxmoderation.gui.GuiService;
import pl.foxmoderation.service.PunishmentService;

import java.util.List;

public final class InventoryListener implements Listener {
    private final FoxModerationPlugin plugin;
    private final GuiService guiService;
    private final PunishmentService punishmentService;

    public InventoryListener(FoxModerationPlugin plugin, GuiService guiService, PunishmentService punishmentService) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.punishmentService = punishmentService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isFoxGui(event.getView().title())) {
            return;
        }
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || item.getItemMeta().lore() == null) {
            return;
        }
        item.getItemMeta().lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .filter(line -> line.startsWith("ID: #"))
                .findFirst()
                .ifPresent(line -> punishmentService.findById(line.substring("ID: #".length()))
                        .ifPresent(record -> guiService.openPunishment(player, record)));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isFoxGui(event.getView().title())) {
            event.setCancelled(true);
        }
    }

    private boolean isFoxGui(net.kyori.adventure.text.Component title) {
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        List<String> knownTitles = List.of(
                plain(plugin.getConfig().getString("guis.notes-title")),
                plain(plugin.getConfig().getString("guis.punishment-title")),
                plain(plugin.getConfig().getString("guis.history-title")),
                plain(plugin.getConfig().getString("guis.checkinfo-title")),
                plain(plugin.getConfig().getString("guis.stafflog-title"))
        );
        return knownTitles.stream().anyMatch(plain::startsWith);
    }

    private String plain(String input) {
        return PlainTextComponentSerializer.plainText().serialize(pl.foxmoderation.util.ColorUtil.guiColor(input));
    }
}
