package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.Permission;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class IpCommand extends Command {
    public IpCommand() {
        super(
                "ip",
                new String[]{},
                "!ip",
                "Prints the local ip of the bot host.",
                new Permission[]{},
                true,
                false,
                false
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            event.channel.sendMessage(new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("hostname -I").getInputStream())).readLine().substring(0, 13)).queue();
        } catch (Exception ignored) {
            event.channel.sendMessage("Error").queue();
        }
    }
}
