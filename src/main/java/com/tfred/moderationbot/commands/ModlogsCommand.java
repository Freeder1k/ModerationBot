package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ModerationData;
import com.tfred.moderationbot.moderation.Punishment;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.LinkedList;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class ModlogsCommand extends Command {
    public ModlogsCommand() {
        super(
                "modlogs",
                new String[]{},
                "!modlogs <user>",
                "Show a users punishment history.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        TextChannel channel = event.channel;

        if (event.args.length == 1) {
            sendHelpMessage(channel);
            return;
        }
        if (event.args.length != 2) {
            sendError(channel, "Please specify a user.");
            return;
        }

        Member member = parseMember(event.guild, event.args[1]);
        long userID;
        if (member == null) {
            try {
                userID = Long.parseLong(event.args[1]);
            } catch (NumberFormatException ignored) {
                sendError(channel, "Invalid user.");
                return;
            }
        } else
            userID = member.getIdLong();

        Punishment[] punishments;
        try {
            punishments = ModerationData.getUserPunishments(event.guild.getIdLong(), userID);
        } catch (IOException e) {
            e.printStackTrace();
            sendException(channel, e);
            return;
        }

        if (punishments.length == 0) {
            sendInfo(channel, "No punishments found for <@" + userID + ">.");
            return;
        }

        LinkedList<EmbedBuilder> embeds = new LinkedList<>();
        embeds.add(new EmbedBuilder().setColor(DEFAULT_COLOR)
                .setDescription("__**<@" + userID + ">'s punishment history:**__")
        );
        int length = 51;
        for (Punishment p : punishments) {
            length = p.addModlogsEmbedFields(embeds.getLast(), length);
            if (length == 0) {
                embeds.add(new EmbedBuilder().setColor(DEFAULT_COLOR));
                length = p.addModlogsEmbedFields(embeds.getLast(), length);
            }
        }

        for (EmbedBuilder eb : embeds)
            channel.sendMessage(eb.build()).queue();
    }
}
