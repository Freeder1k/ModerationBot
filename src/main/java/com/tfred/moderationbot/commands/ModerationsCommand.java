package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ModerationData;
import com.tfred.moderationbot.moderation.TimedPunishment;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.util.LinkedList;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class ModerationsCommand extends Command {
    public ModerationsCommand() {
        super(
                "moderations",
                new String[]{},
                "!moderations",
                "List all currently active punishments.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        TextChannel channel = event.channel;

        TimedPunishment[] activePunishments;
        try {
            activePunishments = ModerationData.getActivePunishments(event.guild.getIdLong());
        } catch (IOException e) {
            e.printStackTrace();
            sendException(channel, e);
            return;
        }

        if (activePunishments.length == 0) {
            sendInfo(channel, "No active punishments.");
            return;
        }
        LinkedList<EmbedBuilder> embeds = new LinkedList<>();
        embeds.add(new EmbedBuilder().setColor(DEFAULT_COLOR)
                .setTitle("__**Currently active punishments:**__")
        );
        int length = 37;
        for (TimedPunishment p : activePunishments) {

            length = p.addModerationsEmbedFields(embeds.getLast(), length);
            if (length == 0) {
                embeds.add(new EmbedBuilder().setColor(DEFAULT_COLOR));
                length = p.addModerationsEmbedFields(embeds.getLast(), length);
            }
        }

        for (EmbedBuilder eb : embeds)
            channel.sendMessage(eb.build()).queue();
    }
}
