package pl.foxmoderation.service;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.foxmoderation.model.Actor;

import java.util.Comparator;
import java.util.Optional;

public final class HierarchyService {
    private final LuckPerms luckPerms;

    public HierarchyService() {
        this.luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
    }

    public Actor resolveActor(CommandSender sender, boolean grimVariant) {
        if (grimVariant) {
            return Actor.forGrim();
        }
        if (!(sender instanceof Player player)) {
            return Actor.forConsole();
        }
        return new Actor(player.getName(), player.getUniqueId(), false, false, weight(player));
    }

    public boolean canAct(CommandSender sender, OfflinePlayer target) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (player.hasPermission("foxmoderation.bypasshierarchy")) {
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        if (target.getPlayer() != null && target.getPlayer().hasPermission("foxmoderation.bypasshierarchy")) {
            return false;
        }
        return weight(player) >= weight(target);
    }

    private int weight(OfflinePlayer player) {
        if (luckPerms == null || player.getUniqueId() == null) {
            return 0;
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            user = luckPerms.getUserManager().loadUser(player.getUniqueId()).join();
        }
        return topWeight(user).orElse(0);
    }

    private int weight(Player player) {
        if (luckPerms == null) {
            return 0;
        }
        PlayerAdapter<Player> adapter = luckPerms.getPlayerAdapter(Player.class);
        User user = adapter.getUser(player);
        return topWeight(user).orElse(0);
    }

    private Optional<Integer> topWeight(User user) {
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getWeight)
                .filter(optional -> optional.isPresent())
                .map(optional -> optional.getAsInt())
                .max(Comparator.naturalOrder());
    }
}
