package com.tfred.moderationbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.apache.commons.text.StringEscapeUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Commands {
    private static final String[] leaderboardNames = {"Hider Wins", "Hunter Wins", "Kills"};
    public static final int defaultColor = 3603854;

    /**
     * The command processing function.
     *
     * @param event
     *          An event containing information about a {@link Message Message} that was
     *          sent in a channel.
     * @param serverdata
     *          The {@link ServerData server data} to work with.
     * @param userData
     *          The {@link UserData user data} to work with.
     * @param leaderboards
     *          The {@link Leaderboards leaderboards data} to work with.
     */
    public static void process(MessageReceivedEvent event, ServerData serverdata, UserData userData, Leaderboards leaderboards, Moderation.PunishmentHandler punishmentHandler) {
        Guild guild = event.getGuild();
        String guildID = guild.getId();
        TextChannel channel = event.getTextChannel();
        Message message = event.getMessage();
        String msg = message.getContentRaw();
        Member sender = message.getMember();
        if(sender == null)
            return;

        if (msg.equals("!help")) {
            channel.sendMessage(new EmbedBuilder().setTitle("**HELP:**").setColor(defaultColor)
                    .setDescription("See: **!help <command>** for help on individual commands.\n")
                    .addField("", "**```dsconfig\nMODERATOR COMMANDS:```**", true).addBlankField(true)
                    .addField("**__!config__**", "- Show the current settings for this server.\n\n" +
                            "**__!delreaction <emoji> <amount>__**\n- Delete all reactions with a specified emoji <amount> messages back (max 100).\n\n" +
                            "**__!getreactions <messageID> [channel]__**\n- Get the reactions on a specified message.\n\n" +
                            "**__!name <set|remove> <user> [username]__**\n- Set a mc username of a user or remove a user from the system.\n\n" +
                            "**__!updatenames__**\n- Update the nickname of users who updated their minecraft ign.\n\n" +
                            "**__!listnames [role]__**\n- List members separated by whether they have an associated minecraft username or not.\n\n" +
                            "**__!punish <user> <severity> [reason]__**\n- Punish a user.\n\n" +
                            "**__!pardon <punishment ID|user> <hide> [reason]__**\n- Pardon a user or punishment.\n\n" +
                            "**__!modlogs <user>__**\n- Show a users punishment history.\n\n" +
                            "**__!moderations__**\n- List all currently active punishments.\n", false)
                    .addField("", "**```autohotkey\nADMIN COMMANDS:```**", true).addBlankField(true).addBlankField(true)
                    .addField("**__!config <option> <value> [action]__**", "- Modify a config option.\n\n" +
                            "**__!lb <board>__**\n- Sends a message with a bh leaderboard (deletes any previous ones).\n\n" +
                            "**__!updatelb__**\n- Updates the lb messages.", false)
                    .addBlankField(true).addField("", new String(new char[23]).replace("\0", "\u200B "), true)
                    .build()).queue();
        }

        else if(msg.startsWith("!help ")) {
            String[] args = msg.split(" ");
            if (args.length == 2) {
                helpMessage(channel, args[1]);
            }
            else {
                sendError(channel, "Invalid amount of arguments!");
            }
        }

        /*
         * Moderator commands
         */
        else if (msg.startsWith("!delreaction")) {
            if(isModerator(guildID, sender, serverdata)) {
                if (checkPerms(channel, channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
                    return;

                String[] args = msg.split(" ");

                if(args.length == 1) {
                    helpMessage(channel, "delreaction");
                    return;
                }

                if (args.length != 3) {
                    sendError(channel, "Invalid amount of arguments!");
                    return;
                }

                String emoji = args[1];
                if(emoji.charAt(0) == '<') {
                    emoji = emoji.substring(1, emoji.length()-1);
                    if(emoji.charAt(0) == 'a')
                        emoji = emoji.substring(1);
                }

                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sendError(channel, "Error parsing amount!");
                    return;
                }
                if (amount > 100 || amount < 1) {
                    sendError(channel, "Amount must be in range 1-100!");
                    return;
                }
                String finalEmoji = emoji;
                message.clearReactions(emoji).queue((success) -> {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(3901635)
                            .setDescription("ℹ️ Removing reactions with " + finalEmoji + " on " + amount + " messages...");
                    channel.sendMessage(eb.build()).queue((sentMessage) -> channel.getHistory().retrievePast(amount).queue((messages) -> {
                        int i = 1;
                        for (Message m : messages) {
                            int finalI = i;
                            m.clearReactions(finalEmoji).queue((ignored) -> {
                                if (finalI == amount) {
                                    sentMessage.delete().queue();
                                    sendSuccess(channel, "✅ Removed reactions with " + finalEmoji + " on " + amount + " messages.");
                                }
                            });
                            i++;
                        }
                    }));
                }, (failure) -> {
                    if (failure instanceof ErrorResponseException) {
                        sendError(channel, "Unknown emoji: ``" + finalEmoji + "``!\n If you don't have access to the emoji send it in the format ``:emoji:id``. Example: ``:test:756833424655777842``.");
                    } else
                        sendError(channel, "An internal error occurred! Please try again later.");
                });
            }
        }

        else if (msg.startsWith("!getreactions")) {
            if(isModerator(guildID, sender, serverdata)) {
                String[] args = msg.split(" ");

                if(args.length == 1) {
                    helpMessage(channel, "getreactions");
                    return;
                }
                if(args.length < 2) {
                    sendError(channel, "Invalid amount of arguments!");
                    return;
                }
                String msgID = args[1];

                TextChannel c;
                if(args.length > 2)
                    c = guild.getTextChannelById(parseID(args[2]));
                else
                    c = channel;
                if(c == null) {
                    sendError(channel, "Couldn't find the specified channel!");
                    return;
                }

                if (checkPerms(channel, c, Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY))
                    return;

                try {
                    c.retrieveMessageById(msgID).queue((m) -> {
                        List<MessageReaction> reactions = m.getReactions();
                        List<String> emojis = new ArrayList<>();
                        for (MessageReaction r : reactions) {
                            MessageReaction.ReactionEmote reactionEmote = r.getReactionEmote();
                            String emoji;
                            if (reactionEmote.isEmote())
                                emoji = ":" + reactionEmote.getName() + ":" + reactionEmote.getId();
                            else
                                emoji = reactionEmote.getName();
                            emojis.add(emoji);
                        }
                        channel.sendMessage(new EmbedBuilder().setColor(defaultColor)
                                .setTitle("Reactions:")
                                .setDescription(emojis.stream().map(e -> "• " + e).collect(Collectors.joining("\n")) + "\n\n[Message link](" + m.getJumpUrl() + ")")
                                .build()).queue();
                    }, (failure) -> {
                        if (failure instanceof ErrorResponseException) {
                            sendError(channel, "Couldn't find the specified message!");
                        }
                    });
                } catch (InsufficientPermissionException e) {
                    sendError(channel, "Cannot perform action due to lack of permission in " + c.getAsMention() + "! Missing permission: " + e.getPermission().toString());
                }
            }
        }

        else if (msg.startsWith("!name")) {
            if(isModerator(guildID, sender, serverdata)) {
                if(checkPerms(channel, null, Permission.NICKNAME_MANAGE))
                    return;

                String[] args = msg.split(" ");

                if(args.length == 1) {
                    helpMessage(channel, "name");
                    return;
                }

                if(args.length < 3) {
                    sendError(channel, "Insufficient amount of arguments!");
                    return;
                }

                Member member = parseMember(guild, args[2]);
                if(member == null) {
                    sendError(channel, "Invalid user.");
                    return;
                }

                if (args[1].equals("set")) {
                    if(args.length < 4) {
                        sendError(channel, "Insufficient amount of arguments!");
                        return;
                    }
                    int returned = userData.setUserInGuild(guildID, member, args[3]);
                    if(returned == 1)
                        sendSuccess(channel, "Set ``" + args[3] + "`` as username of " + member.getAsMention() + ".");
                    else if(returned == 0)
                        sendError(channel, "``" + args[3] + "`` isn't a valid Minecraft username!");
                    else
                        sendError(channel, "An error occurred. Please try again later.");
                } else if (args[1].equals("remove")) {
                    userData.removeUserFromGuild(guildID, member.getUser().getId());
                    sendSuccess(channel, "Removed " + member.getAsMention() + "'s username.");
                } else
                    sendError(channel, "Unknown action! Allowed actions: ``set, remove``.");
            }
        }

        else if (msg.equals("!updatenames")) {
            if(isModerator(guildID, sender, serverdata)) {
                channel.sendMessage("Updating usernames (please note that the bot cannot change the nicknames of users with a higher role).").complete();
                updateNames(channel, userData, guild, false);
            }
        }

        else if (msg.startsWith("!listnames")) {
            if(isModerator(guildID, sender, serverdata)) {
                String[] args = msg.split(" ");

                List<Member> members;

                if(args.length > 1) {
                    Role r = guild.getRoleById(parseID(args[1]));
                    if(r == null)
                        members = guild.getMembers();
                    else
                        members = guild.getMembersWithRoles(r);
                }
                else
                    members = guild.getMembers();

                List<String> parts1 = new LinkedList<>();    //all members that are saved
                List<String> parts2 = new LinkedList<>();    //all members that arent saved
                StringBuilder current1 = new StringBuilder();
                StringBuilder current2 = new StringBuilder();
                int length1 = 12;
                int length2 = 33;

                List<String> ids = userData.getGuildSavedUserIds(guildID);
                for(Member m: members) {
                    String mention = '\n' + m.getAsMention();
                    if(ids.contains(m.getUser().getId())) {
                        if(current1.length() + mention.length() > 1024) {
                            parts1.add(current1.toString());
                            length1 += current1.length();
                            current1.setLength(0);
                        }
                        current1.append(mention);
                    }
                    else {
                        if(current2.length() + mention.length() > 1024) {
                            parts2.add(current2.toString());
                            length2 += current2.length();
                            current2.setLength(0);
                        }
                        current2.append(mention);
                    }
                }
                parts1.add(current1.toString());
                parts2.add(current2.toString());

                if(length1 > 6000 || length2 > 6000) {
                    sendError(channel, "Too many members to display! Ask <@470696578403794967> to change something.");
                    return;
                }

                EmbedBuilder eb1 = new EmbedBuilder().setColor(defaultColor);
                if(parts1.isEmpty())
                    parts1.add("None.");
                eb1.addField("Added users:", parts1.remove(0), true);
                for(String s: parts1) {
                    eb1.addField("", s, true);
                }
                channel.sendMessage(eb1.build()).queue();

                EmbedBuilder eb2 = new EmbedBuilder().setColor(defaultColor);
                if(parts2.isEmpty())
                    parts2.add("None.");
                eb2.addField("Users who haven't been added yet:", parts2.remove(0), true);
                for(String s: parts2) {
                    eb2.addField("", s, true);
                }
                channel.sendMessage(eb2.build()).queue();
            }
        }

        else if (msg.equals("!config")) {
            if(isModerator(guildID, sender, serverdata)) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("__Settings for " + guild.getName() + ":__").setColor(defaultColor);


                String saltMode = serverdata.isNoSalt(guildID)? "✅ ``Enabled``": "❌ ``Disabled``";
                embedBuilder.addField("**No Salt Mode:**", saltMode, false);


                List<String> modRoleIds = serverdata.getModRoles(guildID);
                String modRoles;
                if(modRoleIds.isEmpty())
                    modRoles = "*None*";
                else {
                    StringBuilder stringBuilder = new StringBuilder(modRoleIds.size());
                    for (String id : modRoleIds) {
                        stringBuilder.append("*<@&").append(id).append(">*\n");
                    }
                    modRoles = stringBuilder.toString();
                }
                embedBuilder.addField("**Moderator Roles:**", modRoles, false);

                String memberRole = serverdata.getMemberRoleID(guildID);
                if(memberRole.equals("0"))
                    embedBuilder.addField("**Member role:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Member role:**", "<@&" + memberRole + ">", false);

                String mutedRole = serverdata.getMutedRoleID(guildID);
                if(mutedRole.equals("0"))
                    embedBuilder.addField("**Muted role:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Muted role:**", "<@&" + mutedRole + ">", false);

                String noNickRole = serverdata.getNoNickRoleID(guildID);
                if(noNickRole.equals("0"))
                    embedBuilder.addField("**NoNick role:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**NoNick role:**", "<@&" + noNickRole + ">", false);

                
                StringBuilder leaderboardData = new StringBuilder();
                String[][] lbData = serverdata.getAllLbData(guildID);
                for(int i = 0; i< 3; i++) {
                    leaderboardData.append('*').append(leaderboardNames[i]).append(":* ");
                    if (lbData[i] == null)
                        leaderboardData.append("``Not set yet.``\n");
                    else
                            leaderboardData.append("[<#").append(lbData[i][0]).append(">](https://discordapp.com/channels/").append(guildID).append('/').append(lbData[i][0]).append('/').append(lbData[i][1]).append(" 'Message link')\n");
                }
                embedBuilder.addField("**Leaderboards:**", leaderboardData.toString(), false);


                String logChannel = serverdata.getLogChannelID(guildID);
                if(logChannel.equals("0"))
                    embedBuilder.addField("**Log channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Log channel:**", "<#" + logChannel + ">", false);

                String joinChannel = serverdata.getJoinChannelID(guildID);
                if(joinChannel.equals("0"))
                    embedBuilder.addField("**Join channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Join channel:**", "<#" + joinChannel + ">", false);

                String punishmentChannel = serverdata.getPunishmentChannelID(guildID);
                if(punishmentChannel.equals("0"))
                    embedBuilder.addField("**Punishment channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Punishment channel:**", "<#" + punishmentChannel + ">", false);

                String ventChannel = serverdata.getVentChannelID(guildID);
                if(ventChannel.equals("0"))
                    embedBuilder.addField("**Vent channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Vent channel:**", "<#" + ventChannel + ">", false);

                String nameChannel = serverdata.getNameChannelID(guildID);
                if(nameChannel.equals("0"))
                    embedBuilder.addField("**Name channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Name channel:**", "<#" + nameChannel + ">", false);


                channel.sendMessage(embedBuilder.build()).queue();
            }
        }

        else if (msg.startsWith("!puunish ")) {
            if(isModerator(guildID, sender, serverdata)) {
                channel.sendMessage("*Puunish???*").queue();
            }
        }

        else if (msg.startsWith("!punish")) {
            if(isModerator(guildID, sender, serverdata)) {
                String[] args = message.getContentRaw().split(" ", 4);

                if(args.length == 1) {
                    helpMessage(channel, "punish");
                    return;
                }

                if (args.length < 3) {
                    sendError(channel, "Insufficient amount of arguments!");
                    return;
                }

                Member member = parseMember(guild, args[1]);
                if (member == null) {
                    sendError(channel, "Invalid user.");
                    return;
                }
                if(isModerator(guildID, member, serverdata)) {
                    sendError(channel, "This user is a server moderator!");
                    return;
                }

                if (args[2].length() != 1) {
                    sendError(channel, "Invalid severity type.");
                    return;
                }
                char sev = args[2].charAt(0);
                if ("123456vn".indexOf(sev) == -1) {
                    sendError(channel, "Invalid severity type.");
                    return;
                }

                String reason = "None.";
                if (args.length > 3) {
                    reason = args[3];
                    if(reason.length() > 200) {
                        sendError(channel, "Reason can only have a maximum length of 200 characters!");
                        return;
                    }
                }

                Moderation.Punishment p;
                try {
                    p = Moderation.punish(member, sev, reason, sender.getIdLong(), serverdata, punishmentHandler);
                } catch (Moderation.ModerationException e) {
                    sendError(channel, e.getMessage());
                    return;
                }
                String type = "";
                switch(p.severity) {
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5': {
                        sendSuccess(channel, "Muted <@" + member.getId() + "> for " + p.length + " minutes.");
                        type = "Mute (" + p.severity + ')';
                        break;
                    }
                    case '6': {
                        sendSuccess(channel, "Banned <@" + member.getId() + "> for " + p.length/60 + " hours.");
                        type = "Ban";
                        break;
                    }
                    case 'v': {
                        sendSuccess(channel, "Removed <@" + member.getId() + ">'s access to <#" + channel.getId() + "> for" + p.length/1440 + " days.");
                        type = "Vent ban";
                        break;
                    }
                    case 'n': {
                        sendSuccess(channel, "Removed <@" + member.getId() + ">'s nickname perms for " + p.length/1440 + " days.");
                        type = "Nickname mute";
                        break;
                    }
                }

                TextChannel pchannel = guild.getTextChannelById(serverdata.getPunishmentChannelID(guild.getId()));
                if(pchannel == null)
                    pchannel = guild.getTextChannelById(serverdata.getLogChannelID((guild.getId())));
                if(pchannel != null) {
                    pchannel.sendMessage(new EmbedBuilder()
                            .setColor(defaultColor)
                            .setTitle("Case " + p.id)
                            .addField("**User:**", member.getAsMention() + "\n**Type:**\n" + type, true)
                            .addField("**Length:**", p.length + "mins\n**Moderator:**\n" + sender.getAsMention(), true)
                            .addField("**Reason:**", p.reason, true)
                            .setTimestamp(Instant.now())
                            .build()
                    ).queue();
                }
            }
        }

        else if (msg.startsWith("!pardon") || msg.startsWith("!absolve") || msg.startsWith("!acquit")
                || msg.startsWith("!exculpate") || msg.startsWith("!exonerate") || msg.startsWith("!vindicate")) {
            if(isModerator(guildID, sender, serverdata)) {
                String[] args = message.getContentRaw().split(" ", 4);

                if(args.length == 1) {
                    helpMessage(channel, "pardon");
                    return;
                }

                if(args.length < 3) {
                    sendError(channel, "Insufficient amount of arguments!");
                    return;
                }

                if(args[2].length() != 1) {
                    sendError(channel, "Invalid hide option.");
                    return;
                }
                char hideC = args[2].charAt(0);
                if ("yn".indexOf(hideC) == -1) {
                    sendError(channel, "Invalid hide option.");
                    return;
                }

                String reason = "None.";
                if(args.length > 3) {
                    reason = args[3];
                    if(reason.length() > 200) {
                        sendError(channel, "Reason can only have a maximum length of 200 characters!");
                        return;
                    }
                }

                Member member = parseMember(guild, args[1]);
                long memberID;
                if (member == null) {
                    if(args[1].length() > 10) {
                        try {
                            memberID = Long.parseLong(args[1]);
                        } catch (NumberFormatException ignored) {
                            sendError(channel, "Invalid user or punishment ID.");
                            return;
                        }
                    }
                    else {
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
                                for(Moderation.ActivePunishment ap2: Moderation.getActivePunishments(guildID)) {
                                    if(ap2.punishment.id == id) {
                                        ap = ap2;
                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                sendError(channel, "An IO error occured while reading active.data (<@470696578403794967>)! " + e.getMessage());
                                return;
                            }
                            if(ap == null) {
                                sendError(channel, "No matching active punishment with id " + id + " found.");
                                return;
                            }
                            String response = Moderation.stopPunishment(guild, id, reason, sender.getIdLong(), hideC == 'y', serverdata, false);
                            String[] resS = response.split(" ", 2);

                            sendSuccess(channel, resS[1]);

                            String type = "";
                            switch(ap.punishment.severity) {
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

                            TextChannel pchannel = guild.getTextChannelById(serverdata.getPunishmentChannelID(guild.getId()));
                            if(pchannel == null)
                                pchannel = guild.getTextChannelById(serverdata.getLogChannelID((guild.getId())));
                            if(pchannel != null) {
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
                }
                else
                    memberID = member.getIdLong();

                List<Moderation.ActivePunishment> apList;
                try {
                    apList = Moderation.getActivePunishments(guildID);
                } catch (IOException e) {
                    sendError(channel, "An IO exception occurred while trying to read active punishments! " + e.getMessage());
                    channel.sendMessage("<@470696578403794967>").queue();
                    return;
                }
                long finalMemberID = memberID;
                apList.removeIf(ap -> !ap.memberID.equals(String.valueOf(finalMemberID)));
                if (apList.isEmpty()) {
                    sendError(channel, "No active punishments found for <@" + memberID + ">.");
                    return;
                }
                StringBuilder responses = new StringBuilder();
                for (Moderation.ActivePunishment ap : apList) {
                    try {
                        String response = Moderation.stopPunishment(guild, ap.punishment.id, reason, sender.getIdLong(), hideC == 'y', serverdata, true);
                        String[] resS = response.split(" ", 2);

                        responses.append("✅ ").append(resS[1]).append("\n");

                        String type = "";
                        switch(ap.punishment.severity) {
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

                        TextChannel pchannel = guild.getTextChannelById(serverdata.getPunishmentChannelID(guild.getId()));
                        if(pchannel == null)
                            pchannel = guild.getTextChannelById(serverdata.getLogChannelID((guild.getId())));
                        if(pchannel != null) {
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
                responses.setLength(responses.length()-2);

                channel.sendMessage(new EmbedBuilder().setDescription(responses.toString()).setColor(defaultColor).build()).queue();
            }
        }

        else if (msg.startsWith("!modlogs")) {
            if(isModerator(guildID, sender, serverdata)) {
                String[] args = message.getContentRaw().split(" ");

                if(args.length == 1) {
                    helpMessage(channel, "modlogs");
                    return;
                }
                if(args.length != 2) {
                    sendError(channel, "Please specify a user.");
                    return;
                }

                Member member = parseMember(guild, args[1]);
                long memberID;
                if (member == null) {
                    try {
                        memberID = Long.parseLong(args[1]);
                    } catch (NumberFormatException ignored) {
                        sendError(channel, "Invalid user.");
                        return;
                    }
                }
                else
                    memberID = member.getIdLong();

                List<Moderation.Punishment> pList;
                try {
                    pList = Moderation.getUserPunishments(guildID, String.valueOf(memberID));
                } catch (IOException e) {
                    sendError(channel, "An IO error occurred while reading punishment data! " + e.getMessage());
                    channel.sendMessage("<@470696578403794967>").queue();
                    return;
                }
                if(pList.isEmpty()) {
                    sendInfo(channel, "No logs found for <@" + memberID + ">.");
                }
                else {
                    EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor)
                            .setDescription("__**<@" + memberID + ">'s punishment history:**__\n\u200B");
                    List<MessageEmbed.Field> fields = new LinkedList<>();
                    for(Moderation.Punishment p: pList) {
                        String caseS = String.valueOf(p.id);
                        String date  = Instant.ofEpochMilli(p.date).toString();
                        String length = p.length + "mins";
                        String moderator = "<@" + p.punisherID + ">";
                        String type, pardonedID = null, reason;
                        char sev;
                        boolean pardon = false;
                        if(p.severity == 'u') {
                            pardon = true;
                            String data = p.reason;
                            char hide = data.charAt(0);
                            data = data.substring(2);
                            int i = data.indexOf(' ');
                            pardonedID = data.substring(0, i) + " (" + hide + ")";
                            sev = data.charAt(i+1);
                            reason = StringEscapeUtils.unescapeJava(data.substring(i+3));
                        }
                        else {
                            sev = p.severity;
                            reason = StringEscapeUtils.unescapeJava(p.reason);
                        }
                        switch (sev) {
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                                if(pardon)
                                    type = "Unmute (" + sev + ")";
                                else
                                    type = "Mute (" + p.severity + ")";
                                break;
                            case '6':
                                if(pardon)
                                    type = "unban";
                                else
                                    type = "Ban";
                                break;
                            case 'v':
                                if(pardon)
                                    type = "Vent unban";
                                else
                                    type = "Vent ban";
                                break;
                            case 'n':
                                if(pardon)
                                    type = "Nickname unmute";
                                else
                                    type = "Nickname mute";
                                break;
                            default:
                                type = "Unknown (" + sev + ")";
                        }

                        fields.add(new MessageEmbed.Field("**Case:**", caseS + "\n**Type:**\n" + type, true));
                        if(pardon)
                            fields.add(new MessageEmbed.Field("**Date:**", date + "\n**Effected pID:**\n" + pardonedID, true));
                        else
                            fields.add(new MessageEmbed.Field("**Date:**", date + "\n**Length:**\n" + length, true));
                        fields.add(new MessageEmbed.Field("**Moderator:**", moderator + "\n**Reason:**\n" + reason + "\n\u200B", true));
                    }
                    int c = 0;
                    for(MessageEmbed.Field f: fields) {
                        eb.addField(f);
                        c++;
                        if(c == 24) {
                            c = 0;
                            channel.sendMessage(eb.build()).queue();
                            eb.clearFields();
                        }
                    }
                    if(c != 0)
                        channel.sendMessage(eb.build()).queue();
                }
            }
        }

        else if (msg.equals("!moderations")) {
            if(isModerator(guildID, sender, serverdata)) {
                List<Moderation.ActivePunishment> apList;
                try {
                    apList = Moderation.getActivePunishments(guildID);
                } catch (IOException e) {
                    sendError(channel, "An IO error occurred while reading active.data! " + e.getMessage());
                    channel.sendMessage("<@470696578403794967>").queue();
                    return;
                }

                if(apList.isEmpty()) {
                    sendInfo(channel, "No active punishments.");
                }
                else {
                    EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor)
                            .setTitle("__**Currently active punishments:**__\n\u200B");
                    List<MessageEmbed.Field> fields = new LinkedList<>();
                    for(Moderation.ActivePunishment ap: apList) {
                        Moderation.Punishment p = ap.punishment;
                        String caseS = String.valueOf(p.id);
                        String date  = Instant.ofEpochMilli(p.date).toString();
                        String moderator = "<@" + p.punisherID + ">";
                        String type;
                        String reason = StringEscapeUtils.unescapeJava(p.reason);

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
                        long totalTimeLeft = ((p.date + (((long) p.length) * 60000)) - System.currentTimeMillis()) / 1000; //Time left in seconds
                        long s = totalTimeLeft % 60;
                        long m = (totalTimeLeft / 60) % 60;
                        long h = (totalTimeLeft / (60 * 60)) % 24;
                        long d = (totalTimeLeft / (60 * 60 * 24));
                        String timeLeft = (d == 0? "": d + "d, ") + (h == 0? "": h + "h, ") + m + "m, " + s + "s";

                        fields.add(new MessageEmbed.Field("**Case:**", caseS, false));
                        fields.add(new MessageEmbed.Field("**User:**", "<@" + ap.memberID + ">\n**Type:**\n" + type, true));
                        fields.add(new MessageEmbed.Field("**Date:**", date + "\n**Time left:**\n" + timeLeft, true));
                        fields.add(new MessageEmbed.Field("**Moderator:**", moderator + "\n**Reason:**\n" + reason + "\n\u200B", true));
                    }
                    int c = 0;
                    for(MessageEmbed.Field f: fields) {
                        eb.addField(f);
                        c++;
                        if(c == 24) {
                            c = 0;
                            channel.sendMessage(eb.build()).queue();
                            eb.clearFields();
                        }
                    }
                    if(c != 0)
                        channel.sendMessage(eb.build()).queue();
                }
            }
        }

        /*
         * Admin commands
         */
        else if (msg.startsWith("!config ")) {
            if(sender.hasPermission(Permission.ADMINISTRATOR)) {
                String[] args = msg.split(" ");

                if(args.length < 3) {
                    sendError(channel, "Insufficient amount of arguments!");
                    return;
                }

                switch (args[1]) {
                    case "nosalt": {
                        if (checkPerms(channel, null, Permission.MESSAGE_MANAGE))
                            return;

                        boolean value = args[2].equals("y");
                        if (!value && !args[2].equals("n")) {
                            sendError(channel, "Invalid value! Value must be either ``y`` or ``n``.");
                            return;
                        }

                        if (value) {
                            serverdata.setNoSalt(guildID, true);
                            sendSuccess(channel, "No salt mode enabled.");
                        } else {
                            serverdata.setNoSalt(guildID, false);
                            sendSuccess(channel, "No salt mode disabled.");
                        }
                        break;
                    }
                    case "modrole": {
                        if (args.length < 4) {
                            sendError(channel, "Insufficient amount of arguments!");
                            return;
                        }
                        long id = parseID(args[2]);
                        Role r = guild.getRoleById(id);
                        if (r == null) {
                            sendError(channel, "Invalid role!");
                            return;
                        }

                        if (args[2].equals("add")) {
                            serverdata.addModRole(guildID, String.valueOf(id));
                            sendSuccess(channel, "Added <@&" + id + "> to moderator roles.");
                        } else if (args[1].equals("remove")) {
                            serverdata.removeModRole(guildID, r.getId());
                            sendSuccess(channel, "Removed <@&" + id + "> from moderator roles.");
                        } else
                            sendError(channel, "Unknown action! Allowed actions: ``add, remove``.");
                        break;
                    }
                    case "memberrole": {
                        Role r = guild.getRoleById(parseID(args[2]));
                        if (r == null) {
                            sendError(channel, "Invalid role!");
                            return;
                        }
                        serverdata.setMemberRoleID(guildID, r.getId());
                        sendSuccess(channel, "Set the member role to <@&" + r.getId() + ">.");
                        break;
                    }
                    case "mutedrole": {
                        Role r = guild.getRoleById(parseID(args[2]));
                        if (r == null) {
                            sendError(channel, "Invalid role!");
                            return;
                        }
                        serverdata.setMutedRoleID(guildID, r.getId());
                        sendSuccess(channel, "Set the muted role to <@&" + r.getId() + ">.");
                        break;
                    }
                    case "nonickrole": {
                        Role r = guild.getRoleById(parseID(args[2]));
                        if (r == null) {
                            sendError(channel, "Invalid role!");
                            return;
                        }
                        serverdata.setNoNickRoleID(guildID, r.getId());
                        sendSuccess(channel, "Set the no nickname role to <@&" + r.getId() + ">.");
                        break;
                    }
                    case "logchannel": {
                        TextChannel c = guild.getTextChannelById(parseID(args[2]));
                        if (c == null) {
                            sendError(channel, "Invalid channel!");
                            return;
                        }
                        serverdata.setLogChannelID(guildID, c.getId());
                        sendSuccess(channel, "Set the log channel to <#" + c.getId() + ">.");
                        break;
                    }
                    case "joinchannel": {
                        TextChannel c = guild.getTextChannelById(parseID(args[2]));
                        if (c == null) {
                            sendError(channel, "Invalid channel!");
                            return;
                        }
                        serverdata.setJoinChannelID(guildID, c.getId());
                        sendSuccess(channel, "Set the join channel to <#" + c.getId() + ">.");
                        break;
                    }
                    case "punishmentchannel": {
                        TextChannel c = guild.getTextChannelById(parseID(args[2]));
                        if (c == null) {
                            sendError(channel, "Invalid channel!");
                            return;
                        }
                        serverdata.setPunishmentChannelID(guildID, c.getId());
                        sendSuccess(channel, "Set the punishment channel to <#" + c.getId() + ">.");
                        break;
                    }
                    case "ventchannel": {
                        TextChannel c = guild.getTextChannelById(parseID(args[2]));
                        if (c == null) {
                            sendError(channel, "Invalid channel!");
                            return;
                        }
                        serverdata.setVentChannelID(guildID, c.getId());
                        sendSuccess(channel, "Set the vent channel to <#" + c.getId() + ">.");
                        break;
                    }
                    case "namechannel": {
                        TextChannel c = guild.getTextChannelById(parseID(args[2]));
                        if (c == null) {
                            sendError(channel, "Invalid channel!");
                            return;
                        }
                        serverdata.setNameChannelID(guildID, c.getId());
                        sendSuccess(channel, "Set the name channel to <#" + c.getId() + ">.");
                        break;
                    }
                }
            }
        }

        else if (msg.startsWith("!lb")) {
            if((sender.hasPermission(Permission.ADMINISTRATOR))) {
                if (msg.length() != 5) {
                    helpMessage(channel, "lb");
                    return;
                }
                char board = msg.charAt(4);
                if (!((board == '0') || (board == '1') || (board == '2'))) {
                    sendError(channel, "Board number must be between 0 and 2!");
                    return;
                }
                int boardNum = Character.getNumericValue(board);

                if (leaderboards.failed) {
                    sendError(channel, "Leaderboard data invalid! Please try using ``!updatelb`` to fix the data.");
                    return;
                }

                List<String> lb = leaderboards.lbToString(boardNum, guildID, userData);

                EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor);
                eb.addField(leaderboardNames[boardNum] + " Leaderboard:", lb.remove(0), false);
                for (String s : lb) {
                    eb.addField("", s, false);
                }
                eb.setFooter("Last update ");
                eb.setTimestamp(new Date(leaderboards.getDate()).toInstant());

                String[] messageData = serverdata.getAllLbData(guildID)[boardNum];

                channel.sendMessage(eb.build()).queue((m) -> serverdata.setLbData(guildID, boardNum, channel.getId(), m.getId()));

                //Delete the old message
                if(messageData != null) {
                    TextChannel c = guild.getTextChannelById(messageData[0]);
                    if(c != null) {
                        Message prevMsg = c.getHistory().getMessageById(messageData[1]);
                        if(prevMsg != null) {
                            try {
                                prevMsg.delete().queue();
                            } catch (UnsupportedOperationException ignored) {
                            }
                        }
                    }
                }
            }
        }

        else if (msg.equals("!updatelb")) {
            if(sender.hasPermission(Permission.ADMINISTRATOR))
                updateLeaderboards(channel, leaderboards, serverdata, userData, guild);
        }

        /*
         * Hidden commands
         */
        else if (msg.startsWith("!addallmembers ")) {
            if(sender.hasPermission(Permission.ADMINISTRATOR)) {
                List<Member> failed = new ArrayList<>();

                Role role;
                try {
                    role = message.getMentionedRoles().get(0);
                } catch (IndexOutOfBoundsException e) {
                    channel.sendMessage("Please mention a role!").queue();
                    return;
                }

                channel.sendMessage("Adding members to internal save data.").queue();

                for(Member m: guild.getMembersWithRoles(role)) {
                    String name = m.getEffectiveName();
                    if(name.endsWith(")")) {
                        Pattern pattern = Pattern.compile("\\((.*?)\\)");
                        Matcher matcher = pattern.matcher(name);
                        if(matcher.find())
                            name = matcher.group(1);
                    }
                    //System.out.println(name);
                    int x = userData.setUserInGuild(guildID, m, name);
                    if(x == 0)
                        failed.add(m);
                }
                StringBuilder donemessage = new StringBuilder("Done.");
                if(!failed.isEmpty()) {
                    donemessage.append("\nFailed to add following users:\n");
                    for(Member m: failed) {
                        donemessage.append(m.getAsMention()).append("\n");
                    }
                }
                channel.sendMessage(donemessage.toString()).queue();
            }
            else
                channel.sendMessage("You need to be a server admin to use this command!").queue();
        }

        else if (msg.startsWith("!eval ")) {
            if(sender.getId().equals("470696578403794967")) {
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine engine = manager.getEngineByName("js");
                try {
                    engine.put("event", event);
                    engine.put("serverdata", serverdata);
                    engine.put("userData", userData);
                    engine.put("leaderboards", leaderboards);

                    Object result = engine.eval("load(\"nashorn:mozilla_compat.js\"); " + msg.substring(6));
                    try {
                        channel.sendMessage(result.toString()).queue();
                    } catch (NullPointerException ignored) {
                        channel.sendMessage("null").queue();
                    }
                } catch (Exception e) {
                    channel.sendMessage(e.getMessage()).queue();
                }
            }
            else
                channel.sendMessage("You do not have permission to run this command!").queue();
        }
        
        else if (msg.equals("!ip")) {
            if(sender.getId().equals("470696578403794967")) {
                try {
                    channel.sendMessage(new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("hostname -I").getInputStream())).readLine().substring(0, 13)).queue();
                } catch (Exception ignored) {channel.sendMessage("Error").queue();}
            }
        }

        else if (msg.startsWith("!test ")) {
            if(sender.getId().equals("470696578403794967")) {
                String text = msg.substring(msg.indexOf(' ') + 1);
                String[] langs = {"1c", "abnf", "accesslog", "actionscript", "ada", "angelscript", "apache", "applescript", "arcade", "arduino", "armasm", "asciidoc", "aspectj", "autohotkey", "autoit", "avrasm", "awk", "axapta", "bash", "basic", "bnf", "brainfuck", "c-like", "c", "cal", "capnproto", "ceylon", "clean", "clojure-repl", "clojure", "cmake", "coffeescript", "coq", "cos", "cpp", "crmsh", "crystal", "csharp", "csp", "css", "d", "dart", "delphi", "diff", "django", "dns", "dockerfile", "dos", "dsconfig", "dts", "dust", "ebnf", "elixir", "elm", "erb", "erlang-repl", "erlang", "excel", "fix", "flix", "fortran", "fsharp", "gams", "gauss", "gcode", "gherkin", "glsl", "gml", "go", "golo", "gradle", "groovy", "haml", "handlebars", "haskell", "haxe", "hsp", "htmlbars", "http", "hy", "inform7", "ini", "irpf90", "isbl", "java", "javascript", "jboss-cli", "json", "julia-repl", "julia", "kotlin", "lasso", "latex", "ldif", "leaf", "less", "lisp", "livecodeserver", "livescript", "llvm", "lsl", "lua", "makefile", "markdown", "mathematica", "matlab", "maxima", "mel", "mercury", "mipsasm", "mizar", "mojolicious", "monkey", "moonscript", "n1ql", "nginx", "nim", "nix", "nsis", "objectivec", "ocaml", "openscad", "oxygene", "parser3", "perl", "pf", "pgsql", "php-template", "php", "plaintext", "pony", "powershell", "processing", "profile", "prolog", "properties", "protobuf", "puppet", "purebasic", "python-repl", "python", "q", "qml", "r", "reasonml", "rib", "roboconf", "routeros", "rsl", "ruby", "ruleslanguage", "rust", "sas", "scala", "scheme", "scilab", "scss", "shell", "smali", "smalltalk", "sml", "sqf", "sql", "stan", "stata", "step21", "stylus", "subunit", "swift", "taggerscript", "tap", "tcl", "thrift", "tp", "twig", "typescript", "vala", "vbnet", "vbscript-html", "vbscript", "verilog", "vhdl", "vim", "x86asm", "xl", "xml", "xquery", "yaml", "zephir.js"};

                String[] testMessage = {"", "", "", "", "", "", "", "", "", "", "", ""};
                int i = 0;

                for(String lang: langs) {
                    String current = lang + "```" + lang + '\n' + text + "```";
                    if(testMessage[i].length() + current.length() > 999)
                        i++;
                    if(i > 11) {
                        i--;
                        break;
                    }
                    testMessage[i] += current;
                }
                EmbedBuilder response = new EmbedBuilder();

                response.setTitle("Test:");
                for(int c = 0; c <= i && c < 6; c++)
                    response.addField("", testMessage[c], true);
                channel.sendMessage(response.build()).queue();
                if(i > 5) {
                    EmbedBuilder response2 = new EmbedBuilder().setTitle("Test:");
                    for(int c = 6; c <= i; c++)
                        response2.addField("", testMessage[c], true);
                    channel.sendMessage(response2.build()).queue();
                }
            }
        }

        else if (msg.equals("!shutdown")) {
            if(sender.getId().equals("470696578403794967")) {
                try {
                    channel.sendMessage("⚠️**System shutting down...**⚠️").queue();
                    event.getJDA().shutdown();
                } catch (Exception ignored) {channel.sendMessage("Error").queue();}
            }
        }
    }

    private static boolean isModerator(String serverID, Member member, ServerData serverdata) {
        if(member.hasPermission(Permission.ADMINISTRATOR))
            return true;

        for(Role r: member.getRoles()) {
            if(serverdata.getModRoles(serverID).contains(r.getId()))
                return true;
        }
        return false;
    }

    //checks if the bot has the specified perms in an optional channel requirement and sends a message to the channel if not (true if doesn't have perms)
    private static boolean checkPerms(TextChannel errorChannel, GuildChannel testChannel, Permission ... permissions) {
        Member selfMember = errorChannel.getGuild().getSelfMember();

        List<Permission> missingPerms = new ArrayList<>();
        String channeltext = "";

        if(testChannel == null) {
            for (Permission p : permissions) {
                if (!selfMember.hasPermission(p))
                    missingPerms.add(p);
            }
        }
        else {
            for (Permission p : permissions) {
                if (!selfMember.hasPermission(testChannel, p))
                    missingPerms.add(p);
            }
            channeltext = " in #" + testChannel.getName();
        }

        if(missingPerms.isEmpty())
            return false;
        else {
            errorChannel.sendMessage(new EmbedBuilder().setColor(14495300)
                    .addField("To use this command please give me the following permissions" + channeltext + ":", missingPerms.stream().map(p -> "• " + p.toString()).collect(Collectors.joining("\n")), false)
                    .build()).queue();
            return true;
        }
    }

    public static void sendError(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(14495300).setDescription("❌ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if(e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    public static void sendSuccess(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(7844437).setDescription("✅ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if(e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    public static void sendInfo(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(3901635).setDescription("ℹ️ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if(e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    public static String getName(String nickname) {
        String name;
        if (nickname.endsWith(")")) {
            Pattern pattern = Pattern.compile("\\((.*?)\\)");
            Matcher matcher = pattern.matcher(nickname);
            if (matcher.find())
                name = matcher.group(1);
            else
                name = nickname;
        } else
            name = nickname;

        return name;
    }

    /**
     * Returns the ID of a string. This string can either be the raw ID or a discord mention.
     *
     * @param input
     *          The input sting to be parsed.
     * @return
     *          The ID in the string.
     */
    public static long parseID(String input) {
        if(input.startsWith("\\<"))
            input = input.substring(1);
        if(input.charAt(0) == '<' && input.charAt(input.length()-1) == '>') {
            input = input.substring(1, input.length() - 1);
            if (input.charAt(0) == '#')
                input = input.substring(1);
            if (input.charAt(0) == '@') {
                input = input.substring(1);
                if (input.charAt(0) == '!')
                    input = input.substring(1);
                else if (input.charAt(0) == '&')
                    input = input.substring(1);
            }
        }
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Returns a member specified by a String (as mention, raw ID or discord tag) or null if none was found.
     *
     * @param guild
     *          The guild the member is represented in.
     * @param input
     *          The input string.
     * @return
     *          Possibly-null Member.
     */
    public static Member parseMember(Guild guild, String input) {
        Member m = guild.getMemberById(parseID(input));
        if(m == null) {
            try {
                m = guild.getMemberByTag(input);
            } catch (IllegalArgumentException ignored) {}
        }
        return m;
    }

    /**
     * Updates the nicknames of users in a specified guild.
     *
     * @param channel
     *          The {@link TextChannel channel} to send the results to (can be null).
     * @param userData
     *          The {@link UserData user data} to be processed.
     * @param guild
     *          The specified {@link Guild guild}.
     * @param hide
     *          If no message should be sent when no names are updated.
     */
    public static void updateNames(TextChannel channel, UserData userData, Guild guild, boolean hide) {
        String guildID = guild.getId();

        List<Member> members = guild.getMembers();
        List<String> names = members.stream().map(m -> getName(m.getEffectiveName())).collect(Collectors.toList());
        List<String> userIDs = members.stream().map(Member::getId).collect(Collectors.toList());

        List<String[]> changed = userData.updateGuildUserData(guildID, members);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Updated Users:")
                .setColor(defaultColor);

        if(!changed.isEmpty()) {
            StringBuilder updated = new StringBuilder();
            StringBuilder removed = new StringBuilder();
            StringBuilder failed = new StringBuilder();
            for (String[] s : changed) {
                if(s[1].equals("-"))
                    removed.append("<@").append(s[0]).append(">\n");
                else if(s[1].equals("e"))
                    failed.append("<@").append(s[0]).append(">\n");
                else
                    updated.append("<@").append(s[0]).append(">").append(" (").append(names.get(userIDs.indexOf(s[0]))).append(" -> ").append(s[1]).append(")\n");
            }
            if(updated.length() != 0) {
                if(updated.length() > 2048)
                    eb.setDescription(updated.length() + " users were updated.");
                else
                    eb.setDescription(updated.toString());
            }
            else
                eb.setDescription("No users were updated.");
            if(removed.length() != 0) {
                if (removed.length() < 1024)
                    eb.addField("\nRemoved Users:", removed.toString(), false);
                else
                    eb.addField("", removed.length() + " users were removed from the system.", false);
            }
            if(failed.length() != 0) {
                if (failed.length() < 1024)
                    eb.addField("\nFailed Users:", failed.toString(), false);
                else
                    eb.addField("", "Updating failed on " + failed.length() + " users.", false);
            }
        }
        else {
            if(hide)
                return;
            eb.setDescription("No users were updated.");
        }

        if(channel != null)
            channel.sendMessage(eb.build()).queue();
    }

    //TODO if leaderboards.failed == true try updating data
    /**
     * Updates the leaderboard messages in a specified guild.
     *
     * @param channel
     *          The {@link TextChannel channel} to send the results to (can be null).
     * @param leaderboards
     *          The {@link Leaderboards leaderboards data} to be used.
     * @param serverdata
     *          The {@link ServerData server data} to be used.
     * @param userData
     *          The {@link UserData user data} to be used.
     * @param guild
     *          The specified {@link Guild guild}.
     */
    public static void updateLeaderboards(TextChannel channel, Leaderboards leaderboards, ServerData serverdata, UserData userData, Guild guild) {
        String guildID = guild.getId();

        leaderboards.updateLeaderboards();

        if (leaderboards.failed) {
            if(channel != null)
                sendError(channel, "Leaderboard updating failed! Please try again in a bit or if that doesn't work contact the bot dev.");
            return;
        }

        String[][] data = serverdata.getAllLbData(guildID);
        for(int i = 0; i < 3; i++) {
            if(data[i] == null)
                continue;

            TextChannel editChannel = guild.getTextChannelById(data[i][0]);
            if(editChannel == null)
                continue;

            List<String> lb = leaderboards.lbToString(i, guildID, userData);

            EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor);
            eb.addField(leaderboardNames[i] + " Leaderboard:", lb.remove(0), false);
            for (String s : lb) {
                eb.addField("", s, false);
            }
            eb.setFooter("Last update: ");
            eb.setTimestamp(Instant.ofEpochMilli(leaderboards.getDate()));

            try {
                editChannel.editMessageById(data[i][1], eb.build()).queue();
            } catch (IllegalArgumentException ignored) {
            } catch (ErrorResponseException e) {
                if(channel != null)
                    sendError(channel, "An error occurred when updating lb " + i + ": " + e.getMessage());
            }
        }
        if(channel != null)
            sendSuccess(channel, "Updated leaderboards.");
    }

    private static void helpMessage(TextChannel channel, String command) {
        String usage;
        String aliases = "";
        String description;
        String perms = "";

        switch(command) {
            case "help": {
                usage = "!help [command]";
                description = "Displays the command list or info on a command if one is specified.";
                break;
            }
            case "config": {
                usage = "!config [<option> <value> [action]]";
                description = "View or modify the configuration for this guild.\nValid syntax:```\n" +
                        "option:           │ value:  │ action:\n" +
                        "——————————————————│—————————│————————————\n" +
                        "nosalt            │ y|n     │\n" +
                        "modrole           │ role    │ add|remove\n" +
                        "memberrole        │ role    │\n" +
                        "mutedrole         │ role    │\n" +
                        "nonickrole        │ role    │\n" +
                        "logchannel        │ channel │\n" +
                        "joinchannel       │ channel │\n" +
                        "punishmentchannel │ channel │\n" +
                        "ventchannel       │ channel │\n" +
                        "namechannel       │ channel │```";
                break;
            }
            case "delreaction": {
                usage = "!delreaction <emoji> <amount>";
                description = "Deletes all reactions with a specified emoji <amount> messages back.\n" +
                        "Due to limitations with discord the amount can only have a maximum value of 100.";
                perms = Permission.MESSAGE_MANAGE.toString();
                break;
            }
            case "getreactions": {
                usage = "!getreactions <messageID> [channel]";
                description = "Gets the reactions on a specified message.\n" +
                        "If the message is in another channel the channel has to be specified too.";
                break;
            }
            case "name": {
                usage = "!name <set|remove> <user> [username]";
                description = "Set or remove the associated minecraft username.\n" +
                        "The set option requires the username to be specified.";
                perms = Permission.NICKNAME_MANAGE.toString();
                break;
            }
            case "updatenames": {
                usage = "!updatenames";
                description = "Update the nickname of users with an associated minecraft name if it was changed.";
                perms = Permission.NICKNAME_MANAGE.toString();
                break;
            }
            case "listnames": {
                usage = "!listnames [role]";
                description = "List members separated by whether they have an associated minecraft username or not.\n" +
                        "If a role is specified this will only list users with that role.";
                break;
            }
            case "punish": {
                usage = "!punish <user> <severity> [reason]";
                description = "Punish a user.\n" +
                        "Allowed severities are numbers ``1-6`` or ``v`` for a vent channel ban or ``n`` to block them from changing their nickname.\n" +
                        "These require certain config options to be set.";
                perms = Permission.MANAGE_ROLES.toString() + ", " +
                        Permission.BAN_MEMBERS.toString();
                break;
            }
            case "pardon":
            case "absolve":
            case "acquit":
            case "exculpate":
            case "exonerate":
            case "vindicate": {
                usage = "!" + command + " <punishment ID|user> <hide> [reason]";
                description = "Pardon a user or punishment.\n" +
                        "If a user is specified this pardons all active punishments for this user.\n" +
                        "The hide option can be either ``y`` or ``n`` and specifies if the pardoned punishment(s) should impact the length of future punishments.";
                perms = Permission.MANAGE_ROLES.toString() + ", " +
                        Permission.BAN_MEMBERS.toString();
                aliases = "pardon, absolve, acquit, exculpate, exonerate, vindicate";
                break;
            }
            case "modlogs": {
                usage = "!modlogs <user>";
                description = "Show a users punishment history.";
                break;
            }
            case "moderations": {
                usage = "!moderations";
                description = "List all currently active punishments.";
                break;
            }
            case "lb": {
                usage = "!lb <board>";
                description = " Sends a message with a Blockhunt leaderboard that gets updated weekly.\n" +
                        "If there is an older message with the same board type that one gets deleted if possible." +
                        "Valid boards are:" +
                        "``0`` - Hider wins" +
                        "``1`` - Hunter wins" +
                        "``2`` - Kills";
                break;
            }
            case "updatelb": {
                usage = "!updatelb";
                description = "Updates the lb messages.\n" +
                        "This only really does anything if the bot failed to fetch the new leaderboard data or if it failed to edit a leaderboard message during the last weekly update.";
                break;
            }
            default:
                sendError(channel, "Unknown command. See ``!help`` for a list of commands.");
                return;
        }
        EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor)
                .setTitle("!" + command + " info:")
                .addField("Usage:", "``" + usage + "``", false);
        if(!aliases.isEmpty())
            eb.addField("Aliases:", aliases, false);
        eb.addField("Description:", description, false);
        if(!perms.isEmpty())
            eb.addField("Required permissions:", perms, false);
        channel.sendMessage(eb.build()).queue();
    }
}
