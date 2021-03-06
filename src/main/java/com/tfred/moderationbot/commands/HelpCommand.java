package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;
import java.util.StringJoiner;

public class HelpCommand extends Command {
    private final CommandListener commandListener;

    public HelpCommand(CommandListener commandListener) {
        super(
                "help",
                new String[]{"?", "h"},
                "!help [command]",
                "Displays the command list or info on a command if one is specified.",
                new Permission[]{},
                false,
                false,
                false
        );
        this.commandListener = commandListener;
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        if (event.args.length == 1) {
            StringJoiner anyone = new StringJoiner("\n");
            StringJoiner moderator = new StringJoiner("\n");
            StringJoiner admin = new StringJoiner("\n");

            for (Command c : commandListener.getCommands()) {
                if (!c.devCommand) {
                    if (c.adminCommand)
                        admin.add("``" + c.usage + "``");
                    else if (c.moderatorCommand)
                        moderator.add("``" + c.usage + "``");
                    else
                        anyone.add("``" + c.usage + "``");
                }
            }


            EmbedBuilder eb = new EmbedBuilder().setTitle("**Help:**").setColor(CommandUtils.DEFAULT_COLOR)
                    .setDescription("See: **!help <command>** for help on individual commands.\n");
            if (anyone.length() != 0)
                eb.addField("**General commands:**", anyone.toString(), false);
            if (moderator.length() != 0)
                eb.addField("**Moderator commands**", moderator.toString(), false);
            if (admin.length() != 0)
                eb.addField("**Admin commands:**", admin.toString(), false);

            event.channel.sendMessage(eb.build()).queue();
        } else {
            for (Command c : commandListener.getCommands()) {
                if (c.isCommand(event.args[1])) {
                    c.sendHelpMessage(event.channel);
                    return;
                }
            }
            CommandUtils.sendError(event.channel, "Unknown command: ``" + event.args[1] + "``");
        }
    }
}
