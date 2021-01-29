package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.Permission;

public class ShutdownCommand extends Command {
    public ShutdownCommand() {
        super(
                "shutdown",
                new String[]{},
                "!shutdown",
                "Shuts down the bot and all its processes in an orderly manner.",
                new Permission[]{},
                true,
                false,
                false
        );
    }

    @Override
    public void execute(CommandEvent event) {
        try {
            event.channel.sendMessage("⚠️**System shutting down...**⚠️").queue();
            event.event.getJDA().shutdown();
        } catch (Exception ignored) {
            event.channel.sendMessage("Error").queue();
        }
    }
}