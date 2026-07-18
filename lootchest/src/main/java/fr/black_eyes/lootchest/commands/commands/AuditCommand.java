package fr.black_eyes.lootchest.commands.commands;

import java.util.List;

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
        super("audit", List.of(), List.of(ArgType.LOOTCHEST));
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
            sendTargetReport(sender, plugin.getLootChest().get(args[1]));
            return;
        }

        sendFullReport(sender, LifecycleAuditor.inspect(plugin));
    }

    private void sendFullReport(CommandSender sender, Report report) {
        Messages.msg(sender, "audit.title");
        Messages.msg(
                sender,
                "audit.summary",
                "[Total]", Integer.toString(report.total()),
                "[Present]", Integer.toString(report.present()),
                "[Absent]", Integer.toString(report.absent()),
                "[Wrong]", Integer.toString(report.wrongType()),
                "[Unavailable]", Integer.toString(report.unavailable()),
                "[Issues]", Integer.toString(report.findings().size()));
        Messages.msg(
                sender,
                "audit.index",
                "[Indexed]", Integer.toString(report.indexedEntries()),
                "[Total]", Integer.toString(report.total()));

        if (report.clean()) {
            Messages.msg(sender, "audit.clean");
        } else {
            List<Finding> findings = report.findings();
            findings.stream().limit(MAX_FINDINGS).forEach(finding ->
                    sender.sendMessage(renderFinding(
                            Messages.get("audit.finding"),
                            Messages.get("audit.click_to_tp"),
                            finding,
                            sender instanceof Player)));
            if (findings.size() > MAX_FINDINGS) {
                Messages.msg(
                        sender,
                        "audit.truncated",
                        "[Remaining]", Integer.toString(findings.size() - MAX_FINDINGS));
            }
        }
        Messages.msg(sender, "audit.read_only");
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
