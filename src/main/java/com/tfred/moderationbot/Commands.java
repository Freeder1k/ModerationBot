package com.tfred.moderationbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Commands {
    private static final String[] leaderboardNames = {"Hider Wins", "Hunter Wins", "Kills"};

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
        String msg = message.getContentDisplay();
        Member member = message.getMember();
        if(member == null)
            return;
        //TODO confid (and autoconfig)
        //TODO embeds
        if (msg.equals("!help")) {
            channel.sendMessage("Help:\n" +
                    "__Moderator commands:__\n" +
                    "-``!settings``: show the current settings for this server.\n" +
                    "-``!delreaction <emoji> <amount>``: delete all reactions with a specified emoji <amount> messages back (max 100).\n" +
                    "-``!getreactions <messageID> [channelID|channel Mention]``: get the reactions on a specified message. Please specify the channel ID or mention the channel if the message is in another channel.\n" +
                    "-``!nosalt``: toggle no salt mode.\n" +
                    "-``!name <set|remove> [username] <@user>``: set a mc username of a user or remove a user from the system.\n" +
                    "-``!updatenames``: look for name changes and update the nicknames of users.\n" +
                    "-``!listnames [role]``: list the names of members who are/aren't added to the username system with optional role requirement.\n" +
                    "-``!punish <user> <severity> [reason]``: see ``!help punish`` for more info.\n" +
                    "-``!pardon <punishment ID|user> <y|n> [reason]``: see ``!help pardon`` for more info.\n" +
                    "-``!modlogs <user>``: show a users punishment history.\n" +
                    "\n__Admin commands:__\n" +
                    "-``!modrole <add|remove|list> [role]``: add/remove a mod role or list the mod roles for this server.\n" +
                    "-``!lb <board>``: sends a message with a bh leaderboard corresponding to the lb number that can be updated with !updatelb. (0: hider, 1: hunter, 2: kills).\n" +
                    "-``!updatelb``: updated the lb messages.\n" +
                    "-``!setlogchannel``: set this channel to be the log channel for automatic updates.\n" +
                    "-``!setjoinchannel``: set this channel to be the join channel for info on new joins."
            ).queue();
        }

        /*
         * Moderator commands
         */
        else if (msg.startsWith("!delreaction ")) {
            if(isModerator(guildID, member, serverdata)) {
                if (checkPerms(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
                    return;

                String[] args = msg.split(" ");

                if (args.length != 3) {
                    channel.sendMessage("Invalid amount of arguments!").queue();
                    return;
                }

                String emoji = args[1];

                List<Emote> customEmojis = message.getEmotes();
                final Emote customEmoji;
                if (!customEmojis.isEmpty())
                    customEmoji = customEmojis.get(0);
                else
                    customEmoji = null;

                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    channel.sendMessage("Error parsing amount!").queue();
                    return;
                }
                if (amount > 100 || amount < 1) {
                    channel.sendMessage("Amount must be in range 1-100!").queue();
                    return;
                }

                if (customEmoji == null) {
                    message.clearReactions(emoji).queue((a) -> {
                        System.out.println(a);
                        channel.getHistory().retrievePast(amount).queue((messages) -> {
                            for(Message m: messages)
                                m.clearReactions(emoji).queue();
                        });
                        channel.sendMessage("Removing reactions with " + emoji + " on " + amount + " messages.").queue();
                    }, (failure) -> {
                        if (failure instanceof ErrorResponseException) {
                            channel.sendMessage("Unknown emoji: ``" + emoji + "``! If you don't have access to the emoji send it in the format ``:emoji:id``. Example: ``:test:756833424655777842``.").queue();
                        }
                    });
                }
                else {
                    channel.getHistory().retrievePast(amount).queue((messages) -> {
                        for(Message m: messages)
                            m.clearReactions(customEmoji).queue();
                    });
                    channel.sendMessage("Removing reactions with ``" + emoji + customEmoji.getId() + "`` on " + amount + " messages.").queue();
                }
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.startsWith("!getreactions ")) {
            if(isModerator(guildID, member, serverdata)) {
                String[] args = msg.split(" ");
                if(args.length < 2) {
                    channel.sendMessage("Invalid amount of arguments!").queue();
                    return;
                }
                String msgID = args[1];

                TextChannel c = null;
                if(args.length > 2) {
                    if(args[2].startsWith("#")) {
                        try {
                            c = message.getMentionedChannels().get(0);
                        } catch (IndexOutOfBoundsException e) {
                            channel.sendMessage("Invalid channel mention!").queue();
                        }
                    }
                    else {
                        try {
                            c = guild.getTextChannelById(args[2]);
                        } catch (NumberFormatException e) {
                            channel.sendMessage("Couldn't find the specified channel!").queue();
                            return;
                        }
                    }
                }
                else
                    c = channel;
                if(c == null) {
                    channel.sendMessage("Couldn't find the specified channel!").queue();
                    return;
                }

                try {
                    c.retrieveMessageById(msgID).queue((m) -> {
                        // use the message here, its an async callback
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
                        channel.sendMessage("Reactions: " + emojis + ".").queue();
                    }, (failure) -> {
                        // if the retrieve request failed this will be called (also async)
                        if (failure instanceof ErrorResponseException) {
                            channel.sendMessage("Couldn't find the specified message!").queue();
                        }
                    });
                } catch (InsufficientPermissionException e) {
                    channel.sendMessage("Cannot perform action due to lack of permission in " + c.getAsMention() + "! Missing permission: " + e.getPermission().toString()).queue();
                }
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.equals("!nosalt")) {
            if(isModerator(guildID, member, serverdata)) {
                if(checkPerms(channel, Permission.MESSAGE_MANAGE))
                    return;

                if (serverdata.isNoSalt(guildID)) {
                    serverdata.setNoSalt(guildID, false);
                    channel.sendMessage("No salt mode disabled.").queue();
                } else {
                    serverdata.setNoSalt(guildID, true);
                    channel.sendMessage("No salt mode enabled!").queue();
                }
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.startsWith("!name ")) {
            if(isModerator(guildID, member, serverdata)) {
                String[] args = msg.split(" ");

                if(args.length < 3) {
                    channel.sendMessage("Insufficient amount of arguments!").queue();
                    return;
                }

                Member member1;
                try {
                    member1 = message.getMentionedMembers().get(0);
                } catch (IndexOutOfBoundsException e) {
                    channel.sendMessage("Please mention a member!").queue();
                    return;
                }

                if (args[1].equals("set")) {
                    if(userData.setUserInGuild(guildID, member1, args[2]) == 1)
                        channel.sendMessage("Set " + args[2] + " as username of " + member1.getEffectiveName() + ".").queue();
                    else
                        channel.sendMessage(args[2] + " isn't a valid Minecraft username!").queue();
                } else if (args[1].equals("remove")) {
                    userData.removeUserFromGuild(guildID, member1.getUser().getId());
                    channel.sendMessage("Removed " + member1.getEffectiveName() + "'s username.").queue();
                } else
                    channel.sendMessage("Unknown action! Allowed actions: ``set, remove``.").queue();
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.equals("!updatenames")) {
            if(isModerator(guildID, member, serverdata))
                updateNames(channel, userData, guild);
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.startsWith("!listnames")) {
            if(isModerator(guildID, member, serverdata)) {
                String[] args = msg.split(" ");

                List<Member> members;

                if(args.length > 1) {
                    Role r;
                    try {
                        r = message.getMentionedRoles().get(0);
                        members = guild.getMembersWithRoles(r);
                    } catch (IndexOutOfBoundsException e1) {
                        try {
                            r = guild.getRoleById(args[1]);
                            if(r == null)
                                members = guild.getMembers();
                            else
                                members = guild.getMembersWithRoles(r);
                        } catch (NumberFormatException e2) {
                            members = guild.getMembers();
                        }
                    }
                }
                else
                    members = guild.getMembers();

                List<String> parts1 = new ArrayList<>();    //all members that are saved
                List<String> parts2 = new ArrayList<>();    //all members that arent saved
                StringBuilder current1 = new StringBuilder();
                StringBuilder current2 = new StringBuilder();

                List<String> ids = userData.getGuildSavedUserIds(guildID);
                for(Member m: members) {
                    if(ids.contains(m.getUser().getId())) {
                        if(current1.length() > 950) {
                            parts1.add(current1.toString());
                            current1.setLength(0);
                        }
                        current1.append("\n").append(m.getAsMention());
                    }
                    else {
                        if(current2.length() > 950) {
                            parts2.add(current2.toString());
                            current2.setLength(0);
                        }
                        current2.append("\n").append(m.getAsMention());
                    }
                }
                parts1.add(current1.toString());
                parts2.add(current2.toString());

                if((parts1.size() > 6) || (parts2.size() > 6)) {
                    channel.sendMessage("Too many members to display! Ask the bot dev to change something.").queue();
                    return;
                }

                EmbedBuilder eb1 = new EmbedBuilder();
                if(parts1.isEmpty())
                    parts1.add("**none**");
                eb1.addField("Saved users:", parts1.remove(0), true);
                for(String s: parts1) {
                    eb1.addField("", s, true);
                }
                channel.sendMessage(eb1.build()).queue();

                EmbedBuilder eb2 = new EmbedBuilder();
                if(parts2.isEmpty())
                    parts2.add("**none**");
                eb2.addField("Users who haven't been added yet:", parts2.remove(0), true);
                for(String s: parts2) {
                    eb2.addField("", s, true);
                }
                channel.sendMessage(eb2.build()).queue();
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.equals("!settings")) {
            if(isModerator(guildID, member, serverdata)) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("__Settings for " + guild.getName() + ":__");


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


                channel.sendMessage(embedBuilder.build()).queue();
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.startsWith("!puunish ")) {
            if(isModerator(guildID, member, serverdata)) {
                channel.sendMessage("*Puunish???*").queue();
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.startsWith("!punish ")) {
            if(isModerator(guildID, member, serverdata)) {
                String[] data = message.getContentRaw().split(" ");

                if(data[1].equals("help")) {
                    channel.sendMessage("Punish command help:\n" +
                            "**Syntax:** ``!punish <@user> <severity> [reason]``.\n" +
                            "-``@user`` can be either a user ping or a user id.\n" +
                            "-``severity`` ranges from 1-6 or can be ``v`` for a vent punishment or ``n`` for a nickname punishment."
                    ).queue();
                    return;
                }

                if(data.length < 3) {
                    channel.sendMessage("Insufficient amount of arguments!").queue();
                    return;
                }

                Member m = guild.getMemberById(parseID(data[1]));
                if(m == null) {
                    channel.sendMessage("Invalid user.").queue();
                    return;
                }

                if(data[2].length() != 1) {
                    channel.sendMessage("Invalid severity type.").queue();
                    return;
                }
                char sev = data[2].charAt(0);
                if("123456vn".indexOf(sev) == -1){
                    channel.sendMessage("Invalid severity type.").queue();
                    return;
                }

                String reason = "None.";
                if(data.length > 3) {
                    StringBuilder r = new StringBuilder();
                    for(int i = 3; i < data.length; i++) {
                        r.append(data[i]);
                    }
                    reason = r.toString();
                }

                channel.sendMessage(Moderation.punish(m, sev, reason, member.getIdLong(), serverdata, punishmentHandler)).queue();

            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();
        }

        else if (msg.startsWith("!pardon ") || msg.startsWith("!absolve ") || msg.startsWith("!acquit ")
                || msg.startsWith("!exculpate ") || msg.startsWith("!exonerate ") || msg.startsWith("!vindicate ")) {
            if(isModerator(guildID, member, serverdata)) {
                String[] data = message.getContentRaw().split(" ");

                if(data.length < 3) {
                    channel.sendMessage("Insufficient amount of arguments!").queue();
                    return;
                }

                String reason = "None.";
                if(data.length > 3) {
                    StringBuilder r = new StringBuilder();
                    for(int i = 3; i < data.length; i++) {
                        r.append(data[i]);
                    }
                    reason = r.toString();
                }

                long memberID = parseID(data[1]);
                if(memberID == 0) {
                    int id;
                    try {
                        id = Integer.parseInt(data[1]);
                    } catch (NumberFormatException ignored) {
                        channel.sendMessage("Invalid user or punishment ID.").queue();
                        return;
                    }
                    channel.sendMessage(Moderation.stopPunishment(guild, id, reason, member.getIdLong(), serverdata)).queue();
                }
                else {
                    List<Moderation.ActivePunishment> apList;
                    try {
                        apList = Moderation.getActivePunishments(guildID);
                    } catch (IOException e) {
                        channel.sendMessage("An IO exception occured while trying to read active punishments (<@470696578403794967>)! " + e.getMessage()).queue();
                        return;
                    }
                    if(apList == null) {
                        channel.sendMessage("No active punishments found for <@" + memberID + ">.").queue();
                        return;
                    }
                    apList.removeIf(ap -> !ap.memberID.equals(String.valueOf(memberID)));
                    if(apList.isEmpty()) {
                        channel.sendMessage("No active punishments found for <@" + memberID + ">.").queue();
                        return;
                    }
                    StringBuilder responses = new StringBuilder();
                    for(Moderation.ActivePunishment ap: apList) {
                        responses.append(Moderation.stopPunishment(guild, ap.punishment.id, reason, member.getIdLong(), serverdata)).append('\n');
                    }
                    channel.sendMessage(responses.toString()).queue();
                }

            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();

        }

        else if (msg.startsWith("!modlogs ")) {
            if(isModerator(guildID, member, serverdata)) {
                String[] data = message.getContentRaw().split(" ");
                if(data.length != 2) {
                    channel.sendMessage("Insufficient amount of arguments!").queue();
                    return;
                }

                long memberID = parseID(data[1]);
                if(memberID == 0) {
                    channel.sendMessage("Invalid user.").queue();
                    return;
                }

                List<Moderation.Punishment> pList;
                try {
                    pList = Moderation.getUserPunishments(guildID, String.valueOf(memberID));
                } catch (IOException e) {
                    channel.sendMessage("An IO error occurred while reading active.data (<@470696578403794967>)! " + e.getMessage()).queue();
                    return;
                }
                if(pList == null) {
                    channel.sendMessage("<@" + memberID + "> has not been punished yet.").queue();
                }
                else {
                    StringBuilder sb = new StringBuilder().append("<@").append(memberID).append(">'s punishment history:\n");
                    for(Moderation.Punishment p: pList) {
                        sb.append(p.toString()).append('\n');
                    }
                    if(sb.length() > 2000)
                        channel.sendMessage("Too many punishments to display (<@470696578403794967>)!").queue();
                    else
                        channel.sendMessage(sb.toString()).queue();
                }
            }
            else
                channel.sendMessage("You need to be a server moderator to use this command!").queue();

        }

        /*
         * Admin commands
         */
        else if (msg.startsWith("!modrole ")) {
            if(member.hasPermission(Permission.ADMINISTRATOR)) {
                String[] args = msg.split(" ");

                if(args.length < 2) {
                    channel.sendMessage("Invalid amount of arguments!").queue();
                    return;
                }

                if(args[1].equals("list")) {
                    List<String> roles = serverdata.getModRoles(guildID);

                    if(roles == null || roles.isEmpty()) {
                        channel.sendMessage("There are no moderator roles.").queue();
                        return;
                    }

                    StringBuilder res = new StringBuilder();
                    for(String s: roles) {
                        Role r = event.getGuild().getRoleById(s);
                        if(r != null)
                            res.append(", ``").append(r.getName()).append("``");
                    }
                    res.deleteCharAt(0);    //delete first comma since its abundant

                    channel.sendMessage("Moderator roles:" + res.toString() + ".").queue();
                    return;
                }

                Role role;
                try {
                    role = message.getMentionedRoles().get(0);
                } catch (IndexOutOfBoundsException e) {
                    channel.sendMessage("Please mention a role!").queue();
                    return;
                }

                if (args[1].equals("add")) {
                    serverdata.addModRole(guildID, role.getId());
                    channel.sendMessage("Added " + role.getName() + " to moderator roles.").queue();
                } else if(args[1].equals("remove")) {
                    serverdata.removeModRole(guildID, role.getId());
                    channel.sendMessage("Removed " + role.getName() + " from moderator roles.").queue();
                } else
                    channel.sendMessage("Unknown action. Allowed actions: ``add, remove, list``.").queue();
            }
            else
                channel.sendMessage("You need to be a server admin to use this command!").queue();
        }

        else if (msg.startsWith("!lb ")) {
            if((member.hasPermission(Permission.ADMINISTRATOR))) {
                if (msg.length() != 5) {
                    channel.sendMessage("Single number argument required.").queue();
                    return;
                }
                char board = msg.charAt(4);
                if (!((board == '0') || (board == '1') || (board == '2'))) {
                    channel.sendMessage("Board number must be between 0 and 2!").queue();
                    return;
                }
                int boardNum = Character.getNumericValue(board);

                if (leaderboards.failed) {
                    channel.sendMessage("Leaderboard data invalid! Please try using ``!updatelb`` to fix the data.").queue();
                    return;
                }

                List<String> lb = leaderboards.lbToString(boardNum, guildID, userData);

                EmbedBuilder eb = new EmbedBuilder();
                eb.addField(leaderboardNames[boardNum] + " Leaderboard:", lb.remove(0), false);
                for (String s : lb) {
                    eb.addField("", s, false);
                }
                eb.setFooter("Last update ");
                eb.setTimestamp(new Date(leaderboards.getDate()).toInstant());

                channel.sendMessage(eb.build()).queue((m) -> serverdata.setLbData(guildID, boardNum, channel.getId(), m.getId()));
            }
            else
                channel.sendMessage("You need to be a server admin to use this command!").queue();
        }

        else if (msg.equals("!updatelb")) {
            if(member.hasPermission(Permission.ADMINISTRATOR))
                updateLeaderboards(channel, leaderboards, serverdata, userData, guild);
            else
                channel.sendMessage("You need to be a server admin to use this command!").queue();
        }

        else if (msg.equals("!setlogchannel")) {
            if(member.hasPermission(Permission.ADMINISTRATOR)) {
                serverdata.setLogChannelID(guildID, channel.getId());
                channel.sendMessage("Set log channel to " + channel.getAsMention() + ".").queue();
            }
            else
                channel.sendMessage("You need to be a server admin to use this command!").queue();
        }

        else if (msg.equals("!setjoinchannel")) {
            if(member.hasPermission(Permission.ADMINISTRATOR)) {
                serverdata.setJoinChannelID(guildID, channel.getId());
                channel.sendMessage("Set join channel to " + channel.getAsMention() + ".").queue();
            }
            else
                channel.sendMessage("You need to be a server admin to use this command!").queue();
        }

        /*
         * Hidden commands
         */
        else if (msg.startsWith("!addallmembers ")) {
            if(member.hasPermission(Permission.ADMINISTRATOR)) {
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
            if(member.getId().equals("470696578403794967")) {
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
        
        else if (msg.startsWith("!ip")) {
            if(member.getId().equals("470696578403794967")) {
                try {
                    channel.sendMessage(new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("hostname -I").getInputStream())).readLine().substring(0, 13)).queue();
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

    //checks if the bot has the specified perms and send a message to the channel if not (true if doesn't have perms)
    private static boolean checkPerms(TextChannel channel, Permission ... permissions) {
        Member selfMember = channel.getGuild().getSelfMember();

        List<Permission> missingPerms = new ArrayList<>();

        for(Permission p: permissions) {
            if(!selfMember.hasPermission(channel, p))
                missingPerms.add(p);
        }

        if(missingPerms.isEmpty())
            return false;
        else {
            channel.sendMessage("To use this command please give me the following permissions: " + missingPerms.toString()).queue();
            return true;
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
     * Updates the nicknames of users in a specified guild.
     *
     * @param channel
     *          The {@link TextChannel channel} to send the results to (can be null).
     * @param userData
     *          The {@link UserData user data} to be processed.
     * @param guild
     *          The specified {@link Guild guild}.
     */
    public static void updateNames(TextChannel channel, UserData userData, Guild guild) {
        String guildID = guild.getId();

        if(channel != null)
            channel.sendMessage("Updating usernames (please note that the bot cannot change the nicknames of users with a higher role).").complete();

        List<Member> members = guild.getMembers();
        List<String> names = members.stream().map(m -> getName(m.getEffectiveName())).collect(Collectors.toList());
        List<String> userIDs = members.stream().map(Member::getId).collect(Collectors.toList());

        List<String[]> changed = userData.updateGuildUserData(guildID, members);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Results of !updatenames:");

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
            if(updated.length() != 0)
                eb.addField("Updated Users:", updated.length() < 1024? updated.toString(): updated.length() + " users were updated.", false);
            else
                eb.addField("No users were updated.", "", false);
            if(removed.length() != 0)
                eb.addField("Removed Users:", removed.length() < 1024? removed.toString(): removed.length() + " users were removed.", false);
            if(failed.length() != 0)
                eb.addField("Failed Users:", failed.length() < 1024? failed.toString(): "Updating failed on " + failed.length() + " users.", false);
        }
        else
            eb.addField("No users were updated.", "", false);

        if(channel != null)
            channel.sendMessage(eb.build()).queue();
    }

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
                channel.sendMessage("Leaderboard updating failed! Please try again in a bit or if that doesn't work contact the bot dev.").queue();
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

            EmbedBuilder eb = new EmbedBuilder();
            eb.addField(leaderboardNames[i] + " Leaderboard:", lb.remove(0), false);
            for (String s : lb) {
                eb.addField("", s, false);
            }
            eb.setFooter("Last update: ");
            eb.setTimestamp(new Date(leaderboards.getDate()).toInstant());

            try {
                editChannel.editMessageById(data[i][1], eb.build()).queue();
            } catch (IllegalArgumentException ignored) {
            } catch (ErrorResponseException e) {
                if(channel != null)
                    channel.sendMessage("An error occurred when updating lb " + i + ": " + e.getMessage()).queue();
            }
        }
        if(channel != null)
            channel.sendMessage("Updated leaderboards.").queue();
    }

}
