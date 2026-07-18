package fr.black_eyes.lootchest.commands.commands;

import java.util.List;

import org.bukkit.command.CommandSender;

import fr.black_eyes.lootchest.Main;
import fr.black_eyes.lootchest.Messages;
import fr.black_eyes.lootchest.commands.SubCommand;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.Finding;
import fr.black_eyes.lootchest.lifecycle.LifecycleAudit.Report;
import fr.black_eyes.lootchest.lifecycle.LifecycleAuditor;

/** Reports lifecycle inconsistencies without changing any LootChest state. */
public final class AuditCommand extends SubCommand {
    private static final int MAX_FINDINGS = 50;

    public AuditCommand() {
        super("audit");
    }

    @Override
    protected void onCommand(CommandSender sender, String[] args) {
        Main plugin = Main.getInstance();
        if (plugin.isChestWorkInProgress()) {
            Messages.msg(sender, "ChestOperationInProgress");
            return;
        }

        Report report = LifecycleAuditor.inspect(plugin);
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
            findings.stream().limit(MAX_FINDINGS).forEach(finding -> Messages.msg(
                    sender,
                    "audit.finding",
                    "[Code]", finding.code().name().toLowerCase().replace('_', '-'),
                    "[Chest]", finding.chest(),
                    "[Detail]", finding.detail()));
            if (findings.size() > MAX_FINDINGS) {
                Messages.msg(
                        sender,
                        "audit.truncated",
                        "[Remaining]", Integer.toString(findings.size() - MAX_FINDINGS));
            }
        }
        Messages.msg(sender, "audit.read_only");
    }
}
