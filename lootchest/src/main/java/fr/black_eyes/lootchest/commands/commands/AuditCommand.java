package fr.black_eyes.lootchest.commands.commands;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import fr.black_eyes.lootchest.Lootchest;
import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Messages;
import fr.black_eyes.lootchest.commands.ArgType;
import fr.black_eyes.lootchest.commands.SubCommand;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ChestReport;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.ChestSnapshot;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.Finding;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.Report;
import fr.black_eyes.lootchest.lifecycle.LifecycleAuditor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

/** Reports lifecycle inconsistencies without changing any LootChest state. */
public final class AuditCommand extends SubCommand {
    private static final int MAX_FINDINGS = 50;
    private static final String TELEPORT_COMMAND = "/lc tp ";

    public AuditCommand() {
        super("audit", List.of(), List.of(ArgType.STRING));
    }

    @Override
    public String getUsage() {
        return "/lc audit [chestName]";
    }

    @Override
    protected void onCommand(CommandSender sender, String[] args) {
        Main plugin = Main.getInstance();
        if (plugin.isChestWorkInProgress()) {
            Messages.msg(sender, "ChestOperationInProgress");
            return;
        }

        if (args.length == 2) {
            String chestName = args[1];
            Lootchest chest = plugin.getLootChest().get(chestName);
            if (plugin.getConfigFiles().getRejectedChestDefinitions().containsKey(chestName)) {
                sendRejectedDefinitionReport(
                        sender,
                        chestName,
                        plugin.getConfigFiles().getRejectedChestDefinitions().get(chestName));
            } else if (plugin.getUnavailableChestDefinitions().contains(chestName)) {
                sendInactiveDefinitionReport(sender, chestName, "world-unavailable",
                        "The saved world is not currently loaded.");
            } else if (plugin.getFailedChestDefinitions().containsKey(chestName)) {
                sendInactiveDefinitionReport(sender, chestName, "activation-failed",
                        plugin.getFailedChestDefinitions().get(chestName));
            } else if (chest != null) {
                sendTargetReport(sender, chest);
            } else {
                Messages.msg(sender, "chestDoesntExist", "[Chest]", chestName);
            }
            return;
        }

        sendFullReport(sender, plugin, LifecycleAuditor.inspect(plugin));
    }

    @Override
    public List<String> getTabList(String[] args) {
        if (args.length != 2) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>(Main.getInstance().getConfigFiles().getSavedChestNames());
        names.addAll(Main.getInstance().getLootChest().keySet());
        return names.stream().filter(AuditCommand::isSafeCommandArgument).toList();
    }

    private void sendFullReport(CommandSender sender, Main plugin, Report report) {
        Map<String, List<String>> rejected = plugin.getConfigFiles().getRejectedChestDefinitions();
        int definitionFindings = rejected.values().stream().mapToInt(List::size).sum()
                + plugin.getUnavailableChestDefinitions().size()
                + plugin.getFailedChestDefinitions().size();
        int allFindings = report.findings().size() + definitionFindings;
        Messages.msg(sender, "audit.title");
        Messages.msg(
                sender,
                "audit.summary",
                "[Total]", Integer.toString(report.total()),
                "[Present]", Integer.toString(report.present()),
                "[Absent]", Integer.toString(report.absent()),
                "[Wrong]", Integer.toString(report.wrongType()),
                "[Unavailable]", Integer.toString(report.unavailable()),
                "[Issues]", Integer.toString(allFindings));
        Messages.msg(
                sender,
                "audit.index",
                "[Indexed]", Integer.toString(report.indexedEntries()),
                "[Total]", Integer.toString(report.total()));
        Messages.msg(
                sender,
                "audit.definitions",
                "[Saved]", Integer.toString(plugin.getConfigFiles().getSavedChestDefinitionCount()),
                "[Loadable]", Integer.toString(plugin.getConfigFiles().getLoadableChestNames().size()),
                "[Rejected]", Integer.toString(rejected.size()),
                "[Deferred]", Integer.toString(plugin.getUnavailableChestDefinitions().size()),
                "[Failed]", Integer.toString(plugin.getFailedChestDefinitions().size()));

        if (allFindings == 0) {
            Messages.msg(sender, "audit.clean");
        } else {
            List<Finding> findings = report.findings();
            findings.stream().limit(MAX_FINDINGS).forEach(finding ->
                    sender.sendMessage(renderFinding(
                            Messages.get("audit.finding"),
                            Messages.get("audit.click_to_tp"),
                            finding,
                            sender instanceof Player)));
            int sent = Math.min(findings.size(), MAX_FINDINGS);
            for (Map.Entry<String, List<String>> entry : rejected.entrySet()) {
                for (String problem : entry.getValue()) {
                    if (sent == MAX_FINDINGS) {
                        break;
                    }
                    sender.sendMessage(renderDefinitionFinding(entry.getKey(), problem));
                    sent++;
                }
                if (sent == MAX_FINDINGS) {
                    break;
                }
            }
            if (sent < MAX_FINDINGS) {
                for (String chestName : plugin.getUnavailableChestDefinitions()) {
                    if (sent == MAX_FINDINGS) {
                        break;
                    }
                    sender.sendMessage(renderDefinitionFinding(
                            chestName,
                            "world-unavailable: the saved world is not currently loaded"));
                    sent++;
                }
            }
            if (sent < MAX_FINDINGS) {
                for (Map.Entry<String, String> entry : plugin.getFailedChestDefinitions().entrySet()) {
                    if (sent == MAX_FINDINGS) {
                        break;
                    }
                    sender.sendMessage(renderDefinitionFinding(
                            entry.getKey(),
                            "activation-failed: " + entry.getValue()));
                    sent++;
                }
            }
            if (allFindings > MAX_FINDINGS) {
                Messages.msg(
                        sender,
                        "audit.truncated",
                        "[Remaining]", Integer.toString(allFindings - MAX_FINDINGS));
            }
        }
        Messages.msg(sender, "audit.read_only");
    }

    private void sendRejectedDefinitionReport(
            CommandSender sender,
            String chestName,
            List<String> problems) {
        Messages.msg(sender, "audit.definition_target", "[Chest]", chestName, "[Status]", "rejected");
        problems.forEach(problem -> sender.sendMessage(renderDefinitionFinding(chestName, problem)));
        Messages.msg(sender, "audit.definition_preserved");
        Messages.msg(sender, "audit.read_only");
    }

    private void sendInactiveDefinitionReport(
            CommandSender sender,
            String chestName,
            String status,
            String detail) {
        Messages.msg(sender, "audit.definition_target", "[Chest]", chestName, "[Status]", status);
        sender.sendMessage(renderDefinitionFinding(chestName, detail));
        Messages.msg(sender, "audit.definition_preserved");
        Messages.msg(sender, "audit.read_only");
    }

    private static Component renderDefinitionFinding(String chestName, String detail) {
        return Messages.component(
                Messages.get("audit.definition_finding"),
                "[Chest]", chestName,
                "[Detail]", detail);
    }

    private void sendTargetReport(CommandSender sender, Lootchest chest) {
        ChestReport report = LifecycleAuditor.inspect(Main.getInstance(), chest);
        ChestSnapshot snapshot = report.snapshot();
        boolean interactive = sender instanceof Player;

        sender.sendMessage(renderChestTemplate(
                Messages.get("audit.target_title"),
                Messages.get("audit.click_to_tp"),
                chest.getName(),
                interactive));
        Messages.msg(
                sender,
                "audit.target_container",
                "[Expected]", snapshot.expectedType(),
                "[Actual]", snapshot.actualType(),
                "[State]", display(snapshot.containerState().name()));
        Messages.msg(
                sender,
                "audit.target_location",
                "[Location]", snapshot.locationDisplay(),
                "[Index]", snapshot.indexMatches() ? "matched" : "mismatch");
        Messages.msg(
                sender,
                "audit.target_effects",
                "[HologramExpected]", yesNo(snapshot.hologramExpected()),
                "[HologramActive]", yesNo(snapshot.hologramActive()),
                "[ParticleExpected]", yesNo(snapshot.particleExpected()),
                "[ParticleActive]", yesNo(snapshot.particleActive()));
        Messages.msg(
                sender,
                "audit.target_task",
                "[TaskExpected]", yesNo(snapshot.respawnTaskExpected()),
                "[TaskActive]", yesNo(snapshot.respawnTaskActive()));

        if (report.clean()) {
            Messages.msg(sender, "audit.clean");
        } else {
            report.findings().forEach(finding -> sender.sendMessage(renderFinding(
                    Messages.get("audit.finding"),
                    Messages.get("audit.click_to_tp"),
                    finding,
                    interactive)));
        }
        Messages.msg(sender, "audit.read_only");
    }

    static Component renderFinding(
            String template,
            String hoverTemplate,
            Finding finding,
            boolean interactive) {
        String code = display(finding.code().name());
        Component message = Messages.component(
                template,
                "[Code]", code,
                "[Detail]", finding.detail());
        return replaceChest(message, hoverTemplate, finding.chest(), interactive);
    }

    static Component renderChestTemplate(
            String template,
            String hoverTemplate,
            String chestName,
            boolean interactive) {
        return replaceChest(Messages.component(template), hoverTemplate, chestName, interactive);
    }

    private static Component replaceChest(
            Component message,
            String hoverTemplate,
            String chestName,
            boolean interactive) {
        Component replacement = Component.text(chestName);
        if (interactive && isSafeCommandArgument(chestName)) {
            replacement = replacement
                    .clickEvent(ClickEvent.runCommand(TELEPORT_COMMAND + chestName))
                    .hoverEvent(HoverEvent.showText(
                            Messages.component(hoverTemplate, "[Chest]", chestName)));
        }
        return message.replaceText(TextReplacementConfig.builder()
                .matchLiteral("[Chest]")
                .replacement(replacement)
                .build());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String display(String enumName) {
        return enumName.toLowerCase().replace('_', '-');
    }

    private static boolean isSafeCommandArgument(String chestName) {
        return !chestName.isBlank()
                && chestName.codePoints().noneMatch(character ->
                        Character.isWhitespace(character) || Character.isISOControl(character));
    }
}
