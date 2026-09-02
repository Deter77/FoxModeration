package pl.foxmoderation.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pl.foxmoderation.model.NoteEntry;
import pl.foxmoderation.service.NoteService;
import pl.foxmoderation.service.PunishmentService;
import pl.foxmoderation.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

public final class ChatListener implements Listener {
    private final NoteService noteService;
    private final PunishmentService punishmentService;

    public ChatListener(NoteService noteService, PunishmentService punishmentService) {
        this.noteService = noteService;
        this.punishmentService = punishmentService;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        if (punishmentService.isMuted(sender.getUniqueId())) {
            event.setCancelled(true);
            sender.sendMessage(ColorUtil.color("&cJesteś wyciszony i nie możesz pisać na czacie."));
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component display = sourceDisplayName;
            if (viewer instanceof Player player && player.hasPermission("foxmoderation.hovers")) {
                display = display.hoverEvent(HoverEvent.showText(buildHover(source)));
            }
            return Component.text().append(display).append(Component.text(": ")).append(message).build();
        });
    }

    private Component buildHover(Player player) {
        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.color("&9Gracz: " + player.getName()));
        lines.add(Component.empty());
        lines.add(ColorUtil.color("&eWarny:"));
        punishmentService.history(player.getUniqueId()).stream()
                .filter(p -> p.type() == pl.foxmoderation.model.PunishmentType.WARN && p.status() == pl.foxmoderation.model.PunishmentStatus.ACTIVE)
                .forEach(p -> lines.add(ColorUtil.color("&f- " + p.reason())));
        lines.add(Component.empty());
        lines.add(ColorUtil.color("&cWażna notatka:"));
        List<NoteEntry> important = noteService.important(player.getUniqueId());
        lines.add(ColorUtil.color("&f" + (important.isEmpty() ? "brak" : important.getFirst().content())));
        lines.add(ColorUtil.color("&6Notatki:"));
        for (NoteEntry note : noteService.notes(player.getUniqueId())) {
            lines.add(ColorUtil.color("&f- " + note.content()));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.newline()), lines);
    }
}
