package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Moderation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

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
    protected void execute(CommandEvent event) {
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
        long memberID;
        if (member == null) {
            try {
                memberID = Long.parseLong(event.args[1]);
            } catch (NumberFormatException ignored) {
                sendError(channel, "Invalid user.");
                return;
            }
        } else
            memberID = member.getIdLong();

        List<Moderation.Punishment> pList;
        try {
            pList = Moderation.getUserPunishments(event.guild.getIdLong(), memberID);
        } catch (IOException e) {
            sendError(channel, "An IO error occurred while reading punishment data! " + e.getMessage());
            channel.sendMessage("<@470696578403794967>").queue();
            return;
        }
        if (pList.isEmpty()) {
            sendInfo(channel, "No logs found for <@" + memberID + ">.");
        } else {
            EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor)
                    .setDescription("__**<@" + memberID + ">'s punishment history:**__");
            List<MessageEmbed.Field> fields = new LinkedList<>();
            for (Moderation.Punishment p : pList) {
                String caseS = String.valueOf(p.id);
                String date = Instant.ofEpochMilli(p.date).toString();
                String moderator = "<@" + p.punisherID + ">";
                String type, pardonedID = null, reason;
                char sev;
                boolean pardon = false;
                if (p.severity == 'u') {
                    pardon = true;
                    String data = p.reason;
                    char hide = data.charAt(0);
                    data = data.substring(2);
                    int i = data.indexOf(' ');
                    pardonedID = data.substring(0, i) + " (" + hide + ")";
                    sev = data.charAt(i + 1);
                    reason = data.substring(i + 3);
                } else {
                    sev = p.severity;
                    reason = p.reason;
                }
                switch (sev) {
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                        if (pardon)
                            type = "Unmute (" + sev + ")";
                        else
                            type = "Mute (" + p.severity + ")";
                        break;
                    case '6':
                        if (pardon)
                            type = "unban";
                        else
                            type = "Ban";
                        break;
                    case 'v':
                        if (pardon)
                            type = "Vent unban";
                        else
                            type = "Vent ban";
                        break;
                    case 'n':
                        if (pardon)
                            type = "Nickname unmute";
                        else
                            type = "Nickname mute";
                        break;
                    default:
                        type = "Unknown (" + sev + ")";
                }

                fields.add(new MessageEmbed.Field("**Case:**", caseS + "\n**Type:**\n" + type, true));
                if (pardon)
                    fields.add(new MessageEmbed.Field("**Date:**", date + "\n**Effected pID:**\n" + pardonedID, true));
                else
                    fields.add(new MessageEmbed.Field("**Date:**", date + "\n**Length:**\n" + parseTime(((long) p.length) * 60L), true));
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
