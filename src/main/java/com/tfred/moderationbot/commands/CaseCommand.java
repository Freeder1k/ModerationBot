package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ModerationData;
import com.tfred.moderationbot.moderation.Punishment;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;

import static com.tfred.moderationbot.commands.CommandUtils.sendError;
import static com.tfred.moderationbot.commands.CommandUtils.sendException;

public class CaseCommand extends Command {
    public CaseCommand() {
        super(
                "case",
                new String[]{},
                "!case <punishment ID>",
                "Show a punishment for a specified ID.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        TextChannel channel = event.channel;

        if (event.args.length == 1) {
            sendHelpMessage(channel);
            return;
        }
        if (event.args.length != 2) {
            sendError(channel, "Please specify a punishment ID.");
            return;
        }
        int pID;
        try {
            pID = Integer.parseInt(event.args[1]);
        } catch (NumberFormatException ignored) {
            sendError(channel, "Invalid punishment ID.");
            return;
        }

        Punishment[] all;
        try {
            all = ModerationData.getAllPunishments(event.guild.getIdLong());
        } catch (IOException e) {
            e.printStackTrace();
            sendException(channel, e);
            return;
        }

        for (Punishment p : all) {
            if (p.id == pID) {
                channel.sendMessage(p.getAsCaseEmbed()).queue();
                return;
            }
        }
        sendError(channel, "No punishment with ID " + pID + " found.");
    }
}
