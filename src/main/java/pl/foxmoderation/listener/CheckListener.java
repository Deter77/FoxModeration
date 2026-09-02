package pl.foxmoderation.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import pl.foxmoderation.service.CheckService;
import pl.foxmoderation.util.ColorUtil;

public final class CheckListener implements Listener {
    private final CheckService checkService;

    public CheckListener(CheckService checkService) {
        this.checkService = checkService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (checkService.isChecked(player.getUniqueId()) && (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getY() != event.getTo().getY() || event.getFrom().getZ() != event.getTo().getZ())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent event) { if (checkService.blocksChat(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (checkService.isChecked(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onPickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player p && checkService.isChecked(p.getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (checkService.isChecked(event.getPlayer().getUniqueId()) && event.getAction() != Action.PHYSICAL) event.setCancelled(true); }
    @EventHandler public void onInventory(InventoryClickEvent event) { if (event.getWhoClicked() instanceof Player p && checkService.isChecked(p.getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) { if (checkService.isChecked(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) { if (!checkService.allowsCommand(event.getPlayer().getUniqueId(), event.getMessage())) { event.setCancelled(true); event.getPlayer().sendMessage(ColorUtil.color("&cPodczas sprawdzania możesz użyć tylko /discord lub /dc.")); } }
    @EventHandler public void onQuit(PlayerQuitEvent event) { checkService.handleQuit(event.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { checkService.handleJoin(event.getPlayer()); }
}
