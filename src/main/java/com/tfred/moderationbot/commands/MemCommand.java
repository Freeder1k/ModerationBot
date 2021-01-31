package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import java.util.Locale;

public class MemCommand extends Command {
    public MemCommand() {
        super(
                "mem",
                new String[]{"memoryusage"},
                "!mem",
                "Shows the current memory usage.",
                new Permission[]{},
                true,
                false,
                false
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            Runtime rt = Runtime.getRuntime();
            double total = rt.totalMemory() / 1048576.;
            double free = rt.freeMemory() / 1048576.;
            double max = rt.maxMemory() / 1048576.;

            double usedP = (1. - (free / total)) * 100.;

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(CommandUtils.DEFAULT_COLOR)
                    .setTitle("Memory usage (in MB):")
                    .setDescription(String.format(Locale.US, "**Used:** ``%.2f / %.2f`` ``(%.2f%%)``\n**Max:** ``%.2f``", (total - free), total, usedP, max));

            event.channel.sendMessage(eb.build()).queue();
        } catch (Exception ignored) {
            event.channel.sendMessage("Error").queue();
        }
    }
}
