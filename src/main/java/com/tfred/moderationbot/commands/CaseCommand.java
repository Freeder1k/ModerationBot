package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Moderation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.time.Instant;

import static com.tfred.moderationbot.commands.CommandUtils.*;

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

        Moderation.UserPunishment[] all;
        try {
            all = Moderation.getAllUserPunishments(event.guild.getIdLong());
        } catch (IOException e) {
            sendError(channel, "An IO exception occured! " + e.getMessage());
            return;
        }
        Moderation.UserPunishment userPunishment = null;
        for (Moderation.UserPunishment p : all) {
            if (p.p.id == pID)
                userPunishment = p;
        }
        if (userPunishment == null)
            sendError(channel, "No punishment with ID " + pID + " found.");
        else {
            String type = "";
            switch (userPunishment.p.severity) {
                case '1':
                case '2':
                case '3':
                case '4':
                case '5': {
                    type = "Mute (" + userPunishment.p.severity + ')';
                    break;
                }
                case '6': {
                    type = "Ban";
                    break;
                }
                case 'v': {
                    type = "Vent ban";
                    break;
                }
                case 'n': {
                    type = "Nickname mute";
                    break;
                }
            }

            channel.sendMessage(new EmbedBuilder()
                    .setColor(defaultColor)
                    .setTitle("Case " + pID)
                    .addField("**User:**", "<@" + userPunishment.userID + ">\n**Type:**\n" + type, true)
                    .addField("**Date:**", Instant.ofEpochMilli(userPunishment.p.date).toString() + "\n**Length:**\n" + parseTime(((long) userPunishment.p.length) * 60L), true)
                    .addField("**Moderator:**", "<@" + userPunishment.p.punisherID + ">\n**Reason:**\n" + userPunishment.p.reason, true)
                    .build()
            ).queue();
        }
    }
}
