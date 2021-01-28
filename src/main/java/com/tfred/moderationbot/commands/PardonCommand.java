package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Moderation;
import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class PardonCommand extends Command {
    public PardonCommand() {
        super(
                "pardon",
                new String[]{"unpunish", "absolve", "acquit", "exculpate", "exonerate", "vindicate"},
                "!pardon <punishment ID|user> <hide> [reason]",
                "Pardon a user or punishment.\n" +
                        "If a user is specified this pardons all active punishments for this user.\n" +
                        "The hide option can be either ``y`` or ``n`` and specifies if the pardoned punishment(s) should impact the length of future punishments.",
                new Permission[]{Permission.MANAGE_ROLES, Permission.BAN_MEMBERS},
                false,
                false,
                true
        );
    }

    @Override
    public void execute(CommandEvent event) {
        String[] args = event.message.split(" ", 4);
        TextChannel channel = event.channel;
        Guild guild = event.guild;
        Member sender = event.sender;
        ServerData serverData = ServerData.get(guild.getIdLong());

        if (args.length == 1) {
            sendHelpMessage(channel);
            return;
        }

        if (args.length < 3) {
            sendError(channel, "Insufficient amount of arguments!");
            return;
        }


        char hideC;
        if (args[2].length() != 1) {
            if (!(args[2].equals("yes") || args[2].equals("no"))) {
                sendError(channel, "Invalid hide option.");
                return;
            }
        }
        hideC = args[2].charAt(0);
        if ("yn".indexOf(hideC) == -1) {
            sendError(channel, "Invalid hide option.");
            return;
        }

        String reason = "None.";
        if (args.length > 3) {
            reason = args[3];
            if (reason.length() > 200) {
                sendError(channel, "Reason can only have a maximum length of 200 characters!");
                return;
            }
        }

        Member member = parseMember(guild, args[1]);
        long memberID;
        if (member == null) {
            if (args[1].length() > 10) {
                try {
                    memberID = Long.parseLong(args[1]);
                } catch (NumberFormatException ignored) {
                    sendError(channel, "Invalid user or punishment ID.");
                    return;
                }
            } else {
                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    sendError(channel, "Invalid user or punishment ID.");
                    return;
                }
                try {
                    Moderation.ActivePunishment ap = null;
                    try {
                        for (Moderation.ActivePunishment ap2 : Moderation.getActivePunishments(guild.getIdLong())) {
                            if (ap2.punishment.id == id) {
                                ap = ap2;
                                break;
                            }
                        }
                    } catch (IOException e) {
                        sendError(channel, "An IO error occured while reading active.data (<@470696578403794967>)! " + e.getMessage());
                        return;
                    }
                    if (ap == null) {
                        sendError(channel, "No matching active punishment with id " + id + " found.");
                        return;
                    }
                    String response = Moderation.stopPunishment(guild, id, reason, sender.getIdLong(), hideC == 'y', false);
                    String[] resS = response.split(" ", 2);

                    sendSuccess(channel, resS[1]);

                    String type = "";
                    switch (ap.punishment.severity) {
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5': {
                            type = "Unmute (" + ap.punishment.severity + ')';
                            break;
                        }
                        case '6': {
                            type = "Unban";
                            break;
                        }
                        case 'v': {
                            type = "Vent unban";
                            break;
                        }
                        case 'n': {
                            type = "Nickname unmute";
                            break;
                        }
                    }

                    TextChannel pchannel = guild.getTextChannelById(serverData.getPunishmentChannel());
                    if (pchannel == null)
                        pchannel = guild.getTextChannelById(serverData.getLogChannel());
                    if (pchannel != null) {
                        pchannel.sendMessage(new EmbedBuilder()
                                .setColor(defaultColor)
                                .setTitle("Case " + Integer.parseInt(resS[0]))
                                .addField("**User:**", "<@" + ap.memberID + ">\n**Type:**\n" + type, true)
                                .addField("**Effected pID:**", ap.punishment.id + "\n**Hide:**\n" + hideC, true)
                                .addField("**Moderator:**", sender.getAsMention() + "\n**Reason:**\n" + reason, true)
                                .setTimestamp(Instant.now())
                                .build()
                        ).queue();
                    }
                } catch (Moderation.ModerationException e) {
                    sendError(channel, e.getMessage());
                }
                return;
            }
        } else
            memberID = member.getIdLong();

        List<Moderation.ActivePunishment> apList;
        try {
            apList = Moderation.getActivePunishments(guild.getIdLong());
        } catch (IOException e) {
            sendError(channel, "An IO exception occurred while trying to read active punishments! " + e.getMessage());
            channel.sendMessage("<@470696578403794967>").queue();
            return;
        }
        apList.removeIf(ap -> !(ap.memberID == memberID));
        if (apList.isEmpty()) {
            sendError(channel, "No active punishments found for <@" + memberID + ">.");
            return;
        }
        StringBuilder responses = new StringBuilder();
        for (Moderation.ActivePunishment ap : apList) {
            try {
                String response = Moderation.stopPunishment(guild, ap.punishment.id, reason, sender.getIdLong(), hideC == 'y', true);
                String[] resS = response.split(" ", 2);

                responses.append("✅ ").append(resS[1]).append("\n");

                String type = "";
                switch (ap.punishment.severity) {
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5': {
                        type = "Unmute (" + ap.punishment.severity + ')';
                        break;
                    }
                    case '6': {
                        type = "Unban";
                        break;
                    }
                    case 'v': {
                        type = "Vent unban";
                        break;
                    }
                    case 'n': {
                        type = "Nickname unmute";
                        break;
                    }
                }

                TextChannel pchannel = guild.getTextChannelById(serverData.getPunishmentChannel());
                if (pchannel == null)
                    pchannel = guild.getTextChannelById(serverData.getLogChannel());
                if (pchannel != null) {
                    pchannel.sendMessage(new EmbedBuilder()
                            .setColor(defaultColor)
                            .setTitle("Case " + Integer.parseInt(resS[0]))
                            .addField("**User:**", "<@" + memberID + ">\n**Type:**\n" + type, true)
                            .addField("**Effected pID:**", ap.punishment.id + "\n**Hide:**\n" + hideC, true)
                            .addField("**Moderator:**", sender.getAsMention() + "\n**Reason:**\n" + reason, true)
                            .setTimestamp(Instant.now())
                            .build()
                    ).queue();
                }
            } catch (Moderation.ModerationException e) {
                responses.append("❌ ").append(e).append("\n");
            }
        }
        responses.setLength(responses.length() - 2);

        channel.sendMessage(new EmbedBuilder().setDescription(responses.toString()).setColor(defaultColor).build()).queue();
    }
}
