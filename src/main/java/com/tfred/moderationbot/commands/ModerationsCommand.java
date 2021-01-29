package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Moderation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

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

        List<Moderation.ActivePunishment> apList;
        try {
            apList = Moderation.getActivePunishments(event.guild.getIdLong());
        } catch (IOException e) {
            sendError(channel, "An IO error occurred while reading active.data! " + e.getMessage());
            channel.sendMessage("<@470696578403794967>").queue();
            return;
        }

        if (apList.isEmpty()) {
            sendInfo(channel, "No active punishments.");
        } else {
            EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor)
                    .setTitle("__**Currently active punishments:**__\n\u200B");
            List<MessageEmbed.Field> fields = new LinkedList<>();
            for (Moderation.ActivePunishment ap : apList) {
                Moderation.Punishment p = ap.punishment;
                String caseS = String.valueOf(p.id);
                String date = Instant.ofEpochMilli(p.date).toString();
                String moderator = "<@" + p.punisherID + ">";
                String type;
                String reason = p.reason;

                switch (p.severity) {
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                        type = "Mute (" + p.severity + ")";
                        break;
                    case '6':
                        type = "Ban";
                        break;
                    case 'v':
                        type = "Vent ban";
                        break;
                    case 'n':
                        type = "Nickname mute";
                        break;
                    default:
                        type = "Unknown (" + p.severity + ")";
                }
                String timeLeft = parseTime(((p.date + (((long) p.length) * 60000L)) - System.currentTimeMillis()) / 1000L);

                fields.add(new MessageEmbed.Field("**Case:**", caseS, false));
                fields.add(new MessageEmbed.Field("**User:**", "<@" + ap.memberID + ">\n**Type:**\n" + type, true));
                fields.add(new MessageEmbed.Field("**Date:**", date + "\n**Time left:**\n" + timeLeft, true));
                fields.add(new MessageEmbed.Field("**Moderator:**", moderator + "\n**Reason:**\n" + reason + "\n\u200B", true));
            }
            int c = 0;
            for (MessageEmbed.Field f : fields) {
                eb.addField(f);
                c++;
                if (c == 24) {
                    c = 0;
                    channel.sendMessage(eb.build()).queue();
                    eb.clearFields();
                }
            }
            if (c != 0)
                channel.sendMessage(eb.build()).queue();
        }
    }
}
