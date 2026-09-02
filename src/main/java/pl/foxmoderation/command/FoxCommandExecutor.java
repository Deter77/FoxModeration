package pl.foxmoderation.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.foxmoderation.FoxModerationPlugin;
import pl.foxmoderation.gui.GuiService;
import pl.foxmoderation.model.Actor;
import pl.foxmoderation.model.NoteEntry;
import pl.foxmoderation.model.Preset;
import pl.foxmoderation.model.PunishmentRecord;
import pl.foxmoderation.model.PunishmentType;
import pl.foxmoderation.service.*;
import pl.foxmoderation.util.ColorUtil;
import pl.foxmoderation.util.DurationUtil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FoxCommandExecutor implements CommandExecutor, TabCompleter {
    private final FoxModerationPlugin plugin;
    private final HierarchyService hierarchyService;
    private final PresetService presetService;
    private final PunishmentService punishmentService;
    private final NoteService noteService;
    private final CheckService checkService;
    private final GuiService guiService;

    public FoxCommandExecutor(FoxModerationPlugin plugin, HierarchyService hierarchyService, PresetService presetService, PunishmentService punishmentService, NoteService noteService, CheckService checkService, GuiService guiService) {
        this.plugin = plugin;
        this.hierarchyService = hierarchyService;
        this.presetService = presetService;
        this.punishmentService = punishmentService;
        this.noteService = noteService;
        this.checkService = checkService;
        this.guiService = guiService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String base = command.getName().toLowerCase(Locale.ROOT);
        boolean grim = label.toLowerCase(Locale.ROOT).endsWith("grimac");
        if (!hasPermission(sender, base, grim)) {
            sender.sendMessage(ColorUtil.color("&cBrak permisji."));
            return true;
        }
        try {
            return switch (base) {
                case "warn" -> handleWarn(sender, args, grim);
                case "mute" -> handleMute(sender, args, grim);
                case "kick" -> handleKick(sender, args, grim);
                case "ban" -> handleBan(sender, args, grim);
                case "unmute" -> handleUnmute(sender, args, grim);
                case "unban" -> handleUnban(sender, args, grim);
                case "check" -> handleCheck(sender, args, grim);
                case "checkstart" -> handleCheckStart(sender, args);
                case "checkclear" -> handleCheckClear(sender, args, grim);
                case "checkcheat" -> handleCheckCheat(sender, args, grim);
                case "note" -> handleNote(sender, args, grim, false);
                case "importantnote" -> handleNote(sender, args, grim, true);
                case "notes" -> handleNotes(sender, args);
                case "punishment" -> handlePunishment(sender, args);
                case "history" -> handleHistory(sender, args);
                case "deletepunishment" -> handleDeletePunishment(sender, args, grim);
                case "checkinfo" -> handleCheckInfo(sender, args);
                case "stafflog" -> handleStaffLog(sender, args);
                case "discord" -> handleDiscord(sender);
                default -> false;
            };
        } catch (IllegalStateException ex) {
            String message = switch (ex.getMessage()) {
                case "already-muted" -> plugin.getConfig().getString("messages.already-muted");
                case "already-banned" -> plugin.getConfig().getString("messages.already-banned");
                case "too-many-notes" -> "&cTen gracz ma już maksymalną ilość notatek.";
                default -> "&c" + ex.getMessage();
            };
            sender.sendMessage(ColorUtil.color(message));
            return true;
        } catch (Exception exception) {
            sender.sendMessage(ColorUtil.color("&cBłąd: " + exception.getMessage()));
            exception.printStackTrace();
            return true;
        }
    }

    private boolean handleWarn(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 2) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return offline(sender);
        if (!canAffect(sender, target)) return true;
        String reason = join(args, 1);
        if (reason.isBlank()) return false;
        punishmentService.warn(actor(sender, grim), target, reason);
        sender.sendMessage(ColorUtil.color("&aNadano ostrzeżenie graczowi &f" + target.getName()));
        return true;
    }

    private boolean handleMute(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 2) return false;
        OfflinePlayer target = offlineTarget(args[0]);
        if (!canAffect(sender, target)) return true;
        ResolvedReason resolved = resolveReasonAndDuration(args, 1, PunishmentType.MUTE);
        PunishmentRecord record = punishmentService.mute(actor(sender, grim), target, resolved.reason(), resolved.durationSeconds(), null);
        sender.sendMessage(ColorUtil.color("&aNadano mute #" + record.id() + " graczowi &f" + displayName(target, args[0])));
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 2) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return offline(sender);
        if (!canAffect(sender, target)) return true;
        String reason = join(args, 1);
        if (reason.isBlank()) return false;
        punishmentService.kick(actor(sender, grim), target, reason);
        sender.sendMessage(ColorUtil.color("&aWyrzucono gracza &f" + target.getName()));
        return true;
    }

    private boolean handleBan(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 2) return false;
        OfflinePlayer target = offlineTarget(args[0]);
        if (!canAffect(sender, target)) return true;
        ResolvedReason resolved = resolveReasonAndDuration(args, 1, PunishmentType.BAN);
        PunishmentRecord record = punishmentService.ban(actor(sender, grim), target, resolved.reason(), resolved.durationSeconds(), null);
        sender.sendMessage(ColorUtil.color("&aNadano bana #" + record.id() + " graczowi &f" + displayName(target, args[0])));
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 2) return false;
        OfflinePlayer target = offlineTarget(args[0]);
        if (!canAffect(sender, target)) return true;
        PunishmentRecord record = punishmentService.unmute(actor(sender, grim), target, join(args, 1));
        sender.sendMessage(ColorUtil.color("&aZdjęto mute #" + record.id()));
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 2) return false;
        OfflinePlayer target = offlineTarget(args[0]);
        if (!canAffect(sender, target)) return true;
        PunishmentRecord record = punishmentService.unban(actor(sender, grim), target, join(args, 1));
        sender.sendMessage(ColorUtil.color("&aZdjęto bana #" + record.id()));
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return offline(sender);
        if (!canAffect(sender, target)) return true;
        if (checkService.isChecked(target.getUniqueId())) {
            sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.player-already-checked")));
            return true;
        }
        punishmentService.findById(checkService.beginCheck(actor(sender, grim), target).id());
        if (sender instanceof Player staff) {
            checkService.moveStaffToCheck(staff);
        }
        sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.check-started").replace("<player>", target.getName())));
        return true;
    }

    private boolean handleCheckStart(CommandSender sender, String[] args) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return offline(sender);
        checkService.startManualInspection(target);
        sender.sendMessage(ColorUtil.color("&aWstrzymano licznik dla sprawdzania gracza &f" + target.getName()));
        return true;
    }

    private boolean handleCheckClear(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 1) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return offline(sender);
        checkService.clearCheck(actor(sender, grim), target);
        sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.check-finished").replace("<player>", target.getName())));
        return true;
    }

    private boolean handleCheckCheat(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 2) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return offline(sender);
        ResolvedReason resolved = resolveReasonAndDuration(args, 1, PunishmentType.BAN);
        checkService.cheatCheck(actor(sender, grim), target, resolved.reason(), resolved.durationSeconds());
        sender.sendMessage(ColorUtil.color("&cGracz &f" + target.getName() + " &czostał uznany za cheatera."));
        return true;
    }

    private boolean handleNote(CommandSender sender, String[] args, boolean grim, boolean important) {
        if (important) {
            if (args.length < 2) return false;
            String action = args[0].toLowerCase(Locale.ROOT);
            OfflinePlayer target = offlineTarget(args[1]);
            return switch (action) {
                case "add" -> { noteService.add(actor(sender, grim), target, join(args, 2), true); sender.sendMessage(ColorUtil.color("&aDodano ważną notatkę.")); yield true; }
                case "remove" -> { noteService.removeImportant(actor(sender, grim), target.getUniqueId()); sender.sendMessage(ColorUtil.color("&aUsunięto ważną notatkę.")); yield true; }
                case "edit" -> { noteService.edit(actor(sender, grim), target, 1, join(args, 2), true); sender.sendMessage(ColorUtil.color("&aZedytowano ważną notatkę.")); yield true; }
                default -> false;
            };
        }
        if (args.length < 3) return false;
        String action = args[0].toLowerCase(Locale.ROOT);
        OfflinePlayer target = offlineTarget(args[1]);
        return switch (action) {
            case "add" -> { noteService.add(actor(sender, grim), target, join(args, 2), false); sender.sendMessage(ColorUtil.color("&aDodano notatkę.")); yield true; }
            case "remove" -> {
                String selector = args[2].toLowerCase(Locale.ROOT);
                if (selector.equals("all")) noteService.removeAll(actor(sender, grim), target.getUniqueId());
                else if (selector.equals("oldest")) noteService.removeOldest(actor(sender, grim), target.getUniqueId());
                else noteService.remove(actor(sender, grim), target.getUniqueId(), Integer.parseInt(selector));
                sender.sendMessage(ColorUtil.color("&aUsunięto notatkę."));
                yield true;
            }
            case "edit" -> { noteService.edit(actor(sender, grim), target, Integer.parseInt(args[2]), join(args, 3), false); sender.sendMessage(ColorUtil.color("&aZedytowano notatkę.")); yield true; }
            default -> false;
        };
    }

    private boolean handleNotes(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) return false;
        guiService.openNotes(player, offlineTarget(args[0]));
        return true;
    }

    private boolean handlePunishment(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) return false;
        punishmentService.findById(args[0].replace("#", "")).ifPresent(record -> guiService.openPunishment(player, record));
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) return false;
        guiService.openHistory(player, offlineTarget(args[0]), 0);
        return true;
    }

    private boolean handleDeletePunishment(CommandSender sender, String[] args, boolean grim) {
        if (args.length < 1) return false;
        PunishmentRecord record = punishmentService.deletePunishment(actor(sender, grim), args[0].replace("#", ""));
        sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.punishment-deleted").replace("<id>", record.id())));
        return true;
    }

    private boolean handleCheckInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 1) return false;
        guiService.openCheckInfo(player, offlineTarget(args[0]));
        return true;
    }

    private boolean handleStaffLog(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return false;
        String actor = args.length > 0 ? args[0] : sender.getName();
        guiService.openStaffLog(player, actor);
        return true;
    }

    private boolean handleDiscord(CommandSender sender) {
        sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.discord-command").replace("<discord>", plugin.getConfig().getString("check.discord-url"))));
        return true;
    }

    private boolean hasPermission(CommandSender sender, String base, boolean grim) {
        if (grim) {
            return sender.hasPermission("foxmoderation.grim") || !(sender instanceof Player);
        }
        return switch (base) {
            case "warn" -> sender.hasPermission("foxmoderation.warn") || !(sender instanceof Player);
            case "mute" -> sender.hasPermission("foxmoderation.mute") || !(sender instanceof Player);
            case "kick" -> sender.hasPermission("foxmoderation.kick") || !(sender instanceof Player);
            case "ban" -> sender.hasPermission("foxmoderation.ban") || !(sender instanceof Player);
            case "unmute" -> sender.hasPermission("foxmoderation.unmute") || !(sender instanceof Player);
            case "unban" -> sender.hasPermission("foxmoderation.unban") || !(sender instanceof Player);
            case "check", "checkstart", "checkclear", "checkcheat" -> sender.hasPermission("foxmoderation.check") || !(sender instanceof Player);
            case "note" -> sender.hasPermission("foxmoderation.notes") || !(sender instanceof Player);
            case "importantnote" -> sender.hasPermission("foxmoderation.notesimportant") || !(sender instanceof Player);
            case "notes" -> sender.hasPermission("foxmoderation.notes") || !(sender instanceof Player);
            case "punishment" -> sender.hasPermission("foxmoderation.punishmentinfo") || !(sender instanceof Player);
            case "history" -> sender.hasPermission("foxmoderation.history") || !(sender instanceof Player);
            case "deletepunishment" -> sender.hasPermission("foxmoderation.deletepunishments") || !(sender instanceof Player);
            case "checkinfo" -> sender.hasPermission("foxmoderation.checkinfo") || !(sender instanceof Player);
            case "stafflog" -> sender.hasPermission("foxmoderation.stafflog") || !(sender instanceof Player);
            case "discord" -> true;
            default -> false;
        };
    }

    private boolean canAffect(CommandSender sender, OfflinePlayer target) {
        if (sender instanceof Player player && target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-self-action")));
            return false;
        }
        if (!hierarchyService.canAct(sender, target)) {
            sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.hierarchy-blocked")));
            return false;
        }
        return true;
    }

    private boolean offline(CommandSender sender) {
        sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.player-offline")));
        return true;
    }

    private Actor actor(CommandSender sender, boolean grim) {
        return hierarchyService.resolveActor(sender, grim);
    }

    private OfflinePlayer offlineTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return plugin.database().findKnownPlayerUuid(name).<OfflinePlayer>map(Bukkit::getOfflinePlayer).orElseGet(() -> Bukkit.getOfflinePlayer(name));
    }

    private ResolvedReason resolveReasonAndDuration(String[] args, int fromIndex, PunishmentType type) {
        String first = args[fromIndex];
        Optional<Preset> preset = presetService.find(first, type);
        if (preset.isPresent()) {
            if (args.length > fromIndex + 1) {
                throw new IllegalArgumentException(plugin.getConfig().getString("messages.usage-duration-forbidden"));
            }
            return new ResolvedReason(preset.get().description(), DurationUtil.parseDurationSeconds(preset.get().duration()));
        }
        if (args.length <= fromIndex + 1) {
            throw new IllegalArgumentException(plugin.getConfig().getString("messages.usage-duration-required"));
        }
        String durationInput = args[args.length - 1];
        String reason = String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length - 1));
        return new ResolvedReason(reason, DurationUtil.parseDurationSeconds(durationInput));
    }

    private String join(String[] args, int from) {
        if (from >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String base = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (Set.of("warn", "kick", "check", "checkstart", "checkclear", "checkcheat").contains(base)) {
                return complete(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName));
            }
            if (Set.of("mute", "ban", "unmute", "unban", "notes", "history", "checkinfo").contains(base)) {
                return complete(args[0], knownPlayerNames().stream());
            }
            if (base.equals("note") || base.equals("importantnote")) {
                return complete(args[0], Stream.of("add", "remove", "edit"));
            }
        }
        if ((base.equals("mute") || base.equals("ban")) && args.length == 2) {
            return complete(args[1], presetService.keysByType(base.equals("mute") ? PunishmentType.MUTE : PunishmentType.BAN).stream());
        }
        if (base.equals("checkcheat") && args.length == 2) {
            return complete(args[1], presetService.keysByType(PunishmentType.BAN).stream());
        }
        if ((base.equals("unmute") || base.equals("unban")) && args.length == 1) {
            return complete(args[0], knownPlayerNames().stream());
        }
        if ((base.equals("mute") || base.equals("ban") || base.equals("checkcheat")) && args.length >= 3) {
            return complete(args[args.length - 1], Stream.of("10min", "30min", "1h", "1d", "7d", "perm"));
        }
        if ((base.equals("note") || base.equals("importantnote")) && args.length == 2) {
            return complete(args[1], knownPlayerNames().stream());
        }
        if (base.equals("note") && args.length == 3 && args[0].equalsIgnoreCase("remove")) {
            return complete(args[2], Stream.of("all", "oldest", "1", "2", "3", "4", "5"));
        }
        if (base.equals("note") && args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            return complete(args[2], Stream.of("1", "2", "3", "4", "5"));
        }
        return List.of();
    }

    private List<String> knownPlayerNames() {
        return Stream.concat(
                        Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName).filter(Objects::nonNull),
                        plugin.database().getKnownPlayerNames().stream())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(100)
                .toList();
    }

    private List<String> complete(String current, Stream<String> values) {
        String lower = current.toLowerCase(Locale.ROOT);
        return values.filter(Objects::nonNull)
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String displayName(OfflinePlayer target, String fallback) {
        return target.getName() != null ? target.getName() : fallback;
    }

    private record ResolvedReason(String reason, Long durationSeconds) {
    }
}
