package com.tfred.moderationbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Commands {
    public static final int defaultColor = 3603854;

    private static void helpMessage(TextChannel channel, String command) {
        String usage;
        String aliases = "";
        String description;
        String perms = "";

        switch (command) {
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
            case "unpunish":
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
                aliases = "pardon, unpunish, absolve, acquit, exculpate, exonerate, vindicate";
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
        if (!aliases.isEmpty())
            eb.addField("Aliases:", aliases, false);
        eb.addField("Description:", description, false);
        if (!perms.isEmpty())
            eb.addField("Required permissions:", perms, false);
        channel.sendMessage(eb.build()).queue();
    }

    /**
     * The command processing function.
     *
     * @param event        An event containing information about a {@link Message Message} that was
     *                     sent in a channel.
     */
    public static void process(MessageReceivedEvent event, Moderation.PunishmentHandler punishmentHandler) {
        Message message = event.getMessage();
        String msg = message.getContentRaw();
        String[] args = msg.substring(1).split(" ");
        Member sender = message.getMember();
        assert sender != null;
        TextChannel channel = event.getTextChannel();
        Guild guild = event.getGuild();

        switch (args[0]) {
            case "help":
                helpCommand(args, channel);
                break;
            case "delreaction":
                delreactionCommand(args, message, sender, channel, guild);
                break;
            case "getreactions":
                getreactionsCommand(args, sender, channel, guild);
                break;
            case "name":
                nameCommand(args, sender, channel, guild);
                break;
            case "updatenames":
                updatenamesCommand(sender, channel, guild);
                break;
            case "listnames":
                listnamesCommand(args, sender, channel, guild);
                break;
            case "config":
                configCommand(args, sender, channel, guild);
                break;
            case "puunish":
                if (isModerator(guild.getId(), sender))
                    channel.sendMessage("*Puunish???*").queue();
                break;
            case "punish":
                punishCommand(msg, punishmentHandler, sender, channel, guild);
                break;
            case "pardon":
            case "unpunish":
            case "absolve":
            case "acquit":
            case "exculpate":
            case "exonerate":
            case "vindicate":
                pardonCommand(msg, sender, channel, guild);
                break;
            case "modlogs":
                modlogsCommand(args, sender, channel, guild);
                break;
            case "moderations":
                moderationsCommand(sender, channel, guild.getId());
                break;
            case "lb":
                lbCommand(msg, sender, channel, guild);
                break;
            case "updatelb":
                updatelbCommand(sender, channel, guild);
                break;
            case "eval":
                evalCommand(event, punishmentHandler);
                break;
            case "ip":
                ipCommand(sender, channel);
                break;
            case "test":
                testCommand(msg, sender, channel);
                break;
            case "shutdown":
                shutdownCommand(event.getJDA(), sender, channel);
        }
    }

    public static void helpCommand(String[] args, TextChannel channel) {
        if (args.length > 1) {
            helpMessage(channel, args[1]);
        } else {
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
    }

    /*
     * Moderator commands
     */
    public static void delreactionCommand(String[] args, Message message, Member sender, TextChannel channel, Guild guild) {
        String guildID = guild.getId();
        if (isModerator(guildID, sender)) {
            if (checkPerms(channel, channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
                return;

            if (args.length == 1) {
                helpMessage(channel, "delreaction");
                return;
            }

            if (args.length != 3) {
                sendError(channel, "Invalid amount of arguments!");
                return;
            }

            String emoji = args[1];
            if (emoji.charAt(0) == '<') {
                emoji = emoji.substring(1, emoji.length() - 1);
                if (emoji.charAt(0) == 'a')
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

    public static void getreactionsCommand(String[] args, Member sender, TextChannel channel, Guild guild) {
        assert channel != null;
        String guildID = guild.getId();
        if (isModerator(guildID, sender)) {
            if (args.length == 1) {
                helpMessage(channel, "getreactions");
                return;
            }
            if (args.length < 2) {
                sendError(channel, "Invalid amount of arguments!");
                return;
            }
            String msgID = args[1];

            TextChannel c;
            if (args.length > 2)
                c = guild.getTextChannelById(parseID(args[2]));
            else
                c = channel;
            if (c == null) {
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

    public static void nameCommand(String[] args, Member sender, TextChannel channel, Guild guild) {
        String guildID = guild.getId();
        UserData userData = UserData.get(guild.getIdLong());
        if (isModerator(guildID, sender)) {
            if (checkPerms(channel, null, Permission.NICKNAME_MANAGE))
                return;

            if (args.length == 1) {
                helpMessage(channel, "name");
                return;
            }

            if (args.length < 3) {
                sendError(channel, "Insufficient amount of arguments!");
                return;
            }

            if (args[1].equals("set")) {
                Member member = parseMember(guild, args[2]);
                if (member == null) {
                    sendError(channel, "Invalid user.");
                    return;
                }

                if (args.length < 4) {
                    sendError(channel, "Insufficient amount of arguments!");
                    return;
                }
                if (args[3].equalsIgnoreCase("e") || args[3].equalsIgnoreCase("none")) {
                    sendError(channel, "Name may not be \"e\" or \"none\"!");
                    return;
                }

                int returned = userData.setUuid(member, args[3]);
                if (returned == 1)
                    sendSuccess(channel, "Set ``" + args[3] + "`` as username of " + member.getAsMention() + ".");
                else if (returned == 0)
                    sendError(channel, "``" + args[3] + "`` isn't a valid Minecraft username!");
                else
                    sendError(channel, "An error occurred. Please try again later.");
            } else if (args[1].equals("remove")) {
                long memberID;
                Member member = parseMember(guild, args[2]);
                if (member == null) {
                    memberID = parseID(args[2]);
                    if (memberID == 0) {
                        sendError(channel, "Invalid user.");
                        return;
                    }
                } else
                    memberID = member.getIdLong();

                userData.removeUser(memberID);
                sendSuccess(channel, "Removed <@" + memberID + ">'s username.");
            } else
                sendError(channel, "Unknown action! Allowed actions: ``set, remove``.");
        }
    }

    public static void updatenamesCommand(Member sender, TextChannel channel, Guild guild) {
        String guildID = guild.getId();
        if (isModerator(guildID, sender)) {
            channel.sendMessage("Updating usernames (please note that the bot cannot change the nicknames of users with a higher role).")
                    .queue((ignored) -> updateNames(channel, guild, false));
        }
    }

    public static void listnamesCommand(String[] args, Member sender, TextChannel channel, Guild guild) {
        String guildID = guild.getId();
        if (isModerator(guildID, sender)) {
            List<Member> members;

            if (args.length > 1) {
                Role r = guild.getRoleById(parseID(args[1]));
                if (r == null)
                    members = guild.getMembers();
                else
                    members = guild.getMembersWithRoles(r);
            } else
                members = guild.getMembers();

            List<String> parts1 = new LinkedList<>();    //all members that are saved
            List<String> parts2 = new LinkedList<>();    //all members that arent saved
            StringBuilder current1 = new StringBuilder();
            StringBuilder current2 = new StringBuilder();
            int length1 = 12;
            int length2 = 33;

            List<Long> ids = UserData.get(guild.getIdLong()).getSavedUserIDs();
            for (Member m : members) {
                String mention = '\n' + m.getAsMention();
                if (ids.contains(m.getUser().getIdLong())) {
                    if (current1.length() + mention.length() > 1024) {
                        parts1.add(current1.toString());
                        length1 += current1.length();
                        current1.setLength(0);
                    }
                    current1.append(mention);
                } else {
                    if (current2.length() + mention.length() > 1024) {
                        parts2.add(current2.toString());
                        length2 += current2.length();
                        current2.setLength(0);
                    }
                    current2.append(mention);
                }
            }
            parts1.add(current1.toString());
            parts2.add(current2.toString());

            if (length1 > 6000 || length2 > 6000) {
                sendError(channel, "Too many members to display! Ask <@470696578403794967> to change something.");
                return;
            }

            EmbedBuilder eb1 = new EmbedBuilder().setColor(defaultColor);
            if (parts1.isEmpty())
                parts1.add("None.");
            eb1.addField("Added users:", parts1.remove(0), true);
            for (String s : parts1) {
                eb1.addField("", s, true);
            }
            channel.sendMessage(eb1.build()).queue();

            EmbedBuilder eb2 = new EmbedBuilder().setColor(defaultColor);
            if (parts2.isEmpty())
                parts2.add("None.");
            eb2.addField("Users who haven't been added yet:", parts2.remove(0), true);
            for (String s : parts2) {
                eb2.addField("", s, true);
            }
            channel.sendMessage(eb2.build()).queue();
        }
    }

    public static void configCommand(String[] args, Member sender, TextChannel channel, Guild guild) {
        if (args.length > 1)
            configCommand2(args, sender, channel, guild);
        else {
            String guildID = guild.getId();
            ServerData serverData = ServerData.get(guild.getIdLong());
            if (isModerator(guildID, sender)) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle("__Settings for " + guild.getName() + ":__").setColor(defaultColor);

                Set<Long> modRoleIds = serverData.getModRoles();
                String modRoles;
                if (modRoleIds.isEmpty())
                    modRoles = "*None*";
                else {
                    StringBuilder stringBuilder = new StringBuilder(modRoleIds.size());
                    for (long id : modRoleIds) {
                        stringBuilder.append("*<@&").append(id).append(">*\n");
                    }
                    modRoles = stringBuilder.toString();
                }
                embedBuilder.addField("**Moderator Roles:**", modRoles, false);

                long memberRole = serverData.getMemberRole();
                if (memberRole == 0)
                    embedBuilder.addField("**Member role:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Member role:**", "<@&" + memberRole + ">", false);

                long mutedRole = serverData.getMutedRole();
                if (mutedRole == 0)
                    embedBuilder.addField("**Muted role:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Muted role:**", "<@&" + mutedRole + ">", false);

                long noNickRole = serverData.getNoNicknameRole();
                if (noNickRole == 0)
                    embedBuilder.addField("**NoNick role:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**NoNick role:**", "<@&" + noNickRole + ">", false);


                StringBuilder leaderboardData = new StringBuilder();
                long[][] lbData = serverData.getAllLbMessages();
                for (int i = 0; i < 3; i++) {
                    leaderboardData.append('*').append(new String[]{"Hider Wins", "Hunter Wins", "Kills"}[i]).append(":* ");
                    if (lbData[i][0] == 0)
                        leaderboardData.append("``Not set yet.``\n");
                    else
                        leaderboardData.append("[<#").append(lbData[i][0]).append(">](https://discordapp.com/channels/").append(guildID).append('/').append(lbData[i][0]).append('/').append(lbData[i][1]).append(" 'Message link')\n");
                }
                embedBuilder.addField("**Leaderboards:**", leaderboardData.toString(), false);


                long logChannel = serverData.getLogChannel();
                if (logChannel == 0)
                    embedBuilder.addField("**Log channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Log channel:**", "<#" + logChannel + ">", false);

                long joinChannel = serverData.getJoinChannel();
                if (joinChannel == 0)
                    embedBuilder.addField("**Join channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Join channel:**", "<#" + joinChannel + ">", false);

                long punishmentChannel = serverData.getPunishmentChannel();
                if (punishmentChannel == 0)
                    embedBuilder.addField("**Punishment channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Punishment channel:**", "<#" + punishmentChannel + ">", false);

                long ventChannel = serverData.getVentChannel();
                if (ventChannel == 0)
                    embedBuilder.addField("**Vent channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Vent channel:**", "<#" + ventChannel + ">", false);

                long nameChannel = serverData.getNameChannel();
                if (nameChannel == 0)
                    embedBuilder.addField("**Name channel:**", "``Not set yet.``", false);
                else
                    embedBuilder.addField("**Name channel:**", "<#" + nameChannel + ">", false);


                channel.sendMessage(embedBuilder.build()).queue();
            }
        }
    }

    public static void punishCommand(String msg, Moderation.PunishmentHandler punishmentHandler, Member sender, TextChannel channel, Guild guild) {
        String guildID = guild.getId();
        ServerData serverData = ServerData.get(guild.getIdLong());
        if (isModerator(guildID, sender)) {
            String[] args = msg.split(" ", 4);

            if (args.length == 1) {
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
            if (isModerator(guildID, member)) {
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
                if (reason.length() > 200) {
                    sendError(channel, "Reason can only have a maximum length of 200 characters!");
                    return;
                }
            }

            if(sev == '6') {
                String finalReason = reason;
                member.getUser().openPrivateChannel().queue((pc) -> pc.sendMessage("You were banned from " + guild.getName() + ". Reason: " + finalReason).queue());
            }

            Moderation.Punishment p;
            try {
                p = Moderation.punish(member, sev, reason, sender.getIdLong(), punishmentHandler);
            } catch (Moderation.ModerationException e) {
                sendError(channel, e.getMessage());
                return;
            }
            String type = "";
            switch (p.severity) {
                case '1':
                case '2':
                case '3':
                case '4':
                case '5': {
                    sendSuccess(channel, "Muted <@" + member.getId() + "> for " + parseTime(((long) p.length) * 60L));
                    type = "Mute (" + p.severity + ')';
                    break;
                }
                case '6': {
                    sendSuccess(channel, "Banned <@" + member.getId() + "> for " + parseTime(((long) p.length) * 60L));
                    type = "Ban";
                    break;
                }
                case 'v': {
                    sendSuccess(channel, "Removed <@" + member.getId() + ">'s access to <#" + channel.getId() + "> for" + parseTime(((long) p.length) * 60L));
                    type = "Vent ban";
                    break;
                }
                case 'n': {
                    sendSuccess(channel, "Removed <@" + member.getId() + ">'s nickname perms for " + parseTime(((long) p.length) * 60L));
                    type = "Nickname mute";
                    break;
                }
            }

            TextChannel pchannel = guild.getTextChannelById(serverData.getPunishmentChannel());
            if (pchannel == null)
                pchannel = guild.getTextChannelById(serverData.getLogChannel());
            if (pchannel != null) {
                pchannel.sendMessage(new EmbedBuilder()
                        .setColor(defaultColor)
                        .setTitle("Case " + p.id)
                        .addField("**User:**", member.getAsMention() + "\n**Type:**\n" + type, true)
                        .addField("**Length:**", parseTime(((long) p.length) * 60L) + "\n**Moderator:**\n" + sender.getAsMention(), true)
                        .addField("**Reason:**", p.reason, true)
                        .setTimestamp(Instant.now())
                        .build()
                ).queue();
            }
        }
    }

    public static void pardonCommand(String msg, Member sender, TextChannel channel, Guild guild) {
        String guildID = guild.getId();
        ServerData serverData = ServerData.get(guild.getIdLong());
        if (isModerator(guildID, sender)) {
            String[] args = msg.split(" ", 4);

            if (args.length == 1) {
                helpMessage(channel, "pardon");
                return;
            }

            if (args.length < 3) {
                sendError(channel, "Insufficient amount of arguments!");
                return;
            }

            if (args[2].length() != 1) {
                sendError(channel, "Invalid hide option.");
                return;
            }
            char hideC = args[2].charAt(0);
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
                            for (Moderation.ActivePunishment ap2 : Moderation.getActivePunishments(guildID)) {
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

    public static void modlogsCommand(String[] args, Member sender, TextChannel channel, Guild guild) {
        String guildID = guild.getId();
        if (isModerator(guildID, sender)) {
            if (args.length == 1) {
                helpMessage(channel, "modlogs");
                return;
            }
            if (args.length != 2) {
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
            } else
                memberID = member.getIdLong();

            List<Moderation.Punishment> pList;
            try {
                pList = Moderation.getUserPunishments(guildID, String.valueOf(memberID));
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
                        reason = StringEscapeUtils.unescapeJava(data.substring(i + 3));
                    } else {
                        sev = p.severity;
                        reason = StringEscapeUtils.unescapeJava(p.reason);
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

    public static void moderationsCommand(Member sender, TextChannel channel, String guildID) {
        if (isModerator(guildID, sender)) {
            List<Moderation.ActivePunishment> apList;
            try {
                apList = Moderation.getActivePunishments(guildID);
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

    /*
     * Admin commands
     */
    public static void configCommand2(String[] args, Member sender, TextChannel channel, Guild guild) {
        ServerData serverData = ServerData.get(guild.getIdLong());
        if (sender.hasPermission(Permission.ADMINISTRATOR)) {

            if (args.length < 3) {
                sendError(channel, "Insufficient amount of arguments!");
                return;
            }

            switch (args[1]) {
                case "modrole": {
                    if (args.length < 4) {
                        sendError(channel, "Insufficient amount of arguments!");
                        return;
                    }
                    long id = parseID(args[2]);
                    if (id == 0) {
                        sendError(channel, "Invalid role!");
                        return;
                    }

                    if (args[3].equals("add")) {
                        Role r = guild.getRoleById(id);
                        if (r == null) {
                            sendError(channel, "Invalid role!");
                            return;
                        }
                        serverData.addModRole(id);
                        sendSuccess(channel, "Added <@&" + id + "> to moderator roles.");
                    } else if (args[3].equals("remove")) {
                        serverData.removeModRole(id);
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
                    serverData.setMemberRole(r.getIdLong());
                    sendSuccess(channel, "Set the member role to <@&" + r.getId() + ">.");
                    break;
                }
                case "mutedrole": {
                    Role r = guild.getRoleById(parseID(args[2]));
                    if (r == null) {
                        sendError(channel, "Invalid role!");
                        return;
                    }
                    serverData.setMutedRole(r.getIdLong());
                    sendSuccess(channel, "Set the muted role to <@&" + r.getId() + ">.");
                    break;
                }
                case "nonickrole": {
                    Role r = guild.getRoleById(parseID(args[2]));
                    if (r == null) {
                        sendError(channel, "Invalid role!");
                        return;
                    }
                    serverData.setNoNicknameRole(r.getIdLong());
                    sendSuccess(channel, "Set the no nickname role to <@&" + r.getId() + ">.");
                    break;
                }
                case "logchannel": {
                    TextChannel c = guild.getTextChannelById(parseID(args[2]));
                    if (c == null) {
                        sendError(channel, "Invalid channel!");
                        return;
                    }
                    serverData.setLogChannel(c.getIdLong());
                    sendSuccess(channel, "Set the log channel to <#" + c.getId() + ">.");
                    break;
                }
                case "joinchannel": {
                    TextChannel c = guild.getTextChannelById(parseID(args[2]));
                    if (c == null) {
                        sendError(channel, "Invalid channel!");
                        return;
                    }
                    serverData.setJoinChannel(c.getIdLong());
                    sendSuccess(channel, "Set the join channel to <#" + c.getId() + ">.");
                    break;
                }
                case "punishmentchannel": {
                    TextChannel c = guild.getTextChannelById(parseID(args[2]));
                    if (c == null) {
                        sendError(channel, "Invalid channel!");
                        return;
                    }
                    serverData.setPunishmentChannel(c.getIdLong());
                    sendSuccess(channel, "Set the punishment channel to <#" + c.getId() + ">.");
                    break;
                }
                case "ventchannel": {
                    TextChannel c = guild.getTextChannelById(parseID(args[2]));
                    if (c == null) {
                        sendError(channel, "Invalid channel!");
                        return;
                    }
                    serverData.setVentChannel(c.getIdLong());
                    sendSuccess(channel, "Set the vent channel to <#" + c.getId() + ">.");
                    break;
                }
                case "namechannel": {
                    TextChannel c = guild.getTextChannelById(parseID(args[2]));
                    if (c == null) {
                        sendError(channel, "Invalid channel!");
                        return;
                    }
                    serverData.setNameChannel(c.getIdLong());
                    sendSuccess(channel, "Set the name channel to <#" + c.getId() + ">.");
                    break;
                }
            }
        }
    }

    public static void lbCommand(String msg, Member sender, TextChannel channel, Guild guild) {
        long guildID = guild.getIdLong();
        ServerData serverData = ServerData.get(guildID);
        if ((sender.hasPermission(Permission.ADMINISTRATOR))) {
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

            List<String> lb = Leaderboards.lbToString(boardNum, guildID);
            if(lb == null) {
                sendError(channel, "Failed to fetch leaderboard data!");
                return;
            }

            EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor);
            eb.addField(new String[]{"Hider Wins", "Hunter Wins", "Kills"}[boardNum] + " Leaderboard:", lb.remove(0), false);
            for (String s : lb) {
                eb.addField("", s, false);
            }
            eb.setFooter("Last update ");
            eb.setTimestamp(new Date(Leaderboards.getDate()).toInstant());

            long[] messageData = serverData.getLbMessage(boardNum);

            channel.sendMessage(eb.build()).queue((m) -> serverData.setLbMessage(boardNum, channel.getIdLong(), m.getIdLong()));

            //Delete the old message
            if (messageData[0] != 0) {
                TextChannel c = guild.getTextChannelById(messageData[0]);
                if (c != null) {
                    Message prevMsg = c.getHistory().getMessageById(messageData[1]);
                    if (prevMsg != null) {
                        try {
                            prevMsg.delete().queue();
                        } catch (UnsupportedOperationException ignored) {
                        }
                    }
                }
            }
        }
    }

    public static void updatelbCommand(Member sender, TextChannel channel, Guild guild) {
        if (sender.hasPermission(Permission.ADMINISTRATOR))
            updateLeaderboards(channel, guild);
    }

    /*
     * Private commands
     */
    private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
    private static boolean ran = false;
    public static void evalCommand(MessageReceivedEvent event, Moderation.PunishmentHandler punishmentHandler) {
        String msg = event.getMessage().getContentRaw();
        User sender = event.getAuthor();
        TextChannel channel = event.getTextChannel();
        if (sender.getId().equals("470696578403794967")) {
            try {
                if(!ran) {
                    ran = true;
                    engine.eval("load(\"nashorn:mozilla_compat.js\"); ");
                }
                engine.put("event", event);
                engine.put("serverdata", ServerData.get(event.getGuild().getIdLong()));
                engine.put("userdata", UserData.get(event.getGuild().getIdLong()));
                engine.put("punishmenthandler", punishmentHandler);

                Object result = engine.eval(msg.substring(6));
                if(result == null)
                    channel.sendMessage("null").queue();
                else
                    channel.sendMessage(result.toString()).queue();
            } catch (Exception e) {
                channel.sendMessage(e.getMessage()).queue();
            }
        } else
            channel.sendMessage("You do not have permission to run this command!").queue();
    }

    public static void ipCommand(Member sender, TextChannel channel) {
        if (sender.getId().equals("470696578403794967")) {
            try {
                channel.sendMessage(new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("hostname -I").getInputStream())).readLine().substring(0, 13)).queue();
            } catch (Exception ignored) {
                channel.sendMessage("Error").queue();
            }
        }
    }

    public static void testCommand(String msg, Member sender, TextChannel channel) {
        if (sender.getId().equals("470696578403794967")) {
            String text = msg.substring(6);
            String[] langs = {"1c", "abnf", "accesslog", "actionscript", "ada", "angelscript", "apache", "applescript", "arcade", "arduino", "armasm", "asciidoc", "aspectj", "autohotkey", "autoit", "avrasm", "awk", "axapta", "bash", "basic", "bnf", "brainfuck", "c-like", "c", "cal", "capnproto", "ceylon", "clean", "clojure-repl", "clojure", "cmake", "coffeescript", "coq", "cos", "cpp", "crmsh", "crystal", "csharp", "csp", "css", "d", "dart", "delphi", "diff", "django", "dns", "dockerfile", "dos", "dsconfig", "dts", "dust", "ebnf", "elixir", "elm", "erb", "erlang-repl", "erlang", "excel", "fix", "flix", "fortran", "fsharp", "gams", "gauss", "gcode", "gherkin", "glsl", "gml", "go", "golo", "gradle", "groovy", "haml", "handlebars", "haskell", "haxe", "hsp", "htmlbars", "http", "hy", "inform7", "ini", "irpf90", "isbl", "java", "javascript", "jboss-cli", "json", "julia-repl", "julia", "kotlin", "lasso", "latex", "ldif", "leaf", "less", "lisp", "livecodeserver", "livescript", "llvm", "lsl", "lua", "makefile", "markdown", "mathematica", "matlab", "maxima", "mel", "mercury", "mipsasm", "mizar", "mojolicious", "monkey", "moonscript", "n1ql", "nginx", "nim", "nix", "nsis", "objectivec", "ocaml", "openscad", "oxygene", "parser3", "perl", "pf", "pgsql", "php-template", "php", "plaintext", "pony", "powershell", "processing", "profile", "prolog", "properties", "protobuf", "puppet", "purebasic", "python-repl", "python", "q", "qml", "r", "reasonml", "rib", "roboconf", "routeros", "rsl", "ruby", "ruleslanguage", "rust", "sas", "scala", "scheme", "scilab", "scss", "shell", "smali", "smalltalk", "sml", "sqf", "sql", "stan", "stata", "step21", "stylus", "subunit", "swift", "taggerscript", "tap", "tcl", "thrift", "tp", "twig", "typescript", "vala", "vbnet", "vbscript-html", "vbscript", "verilog", "vhdl", "vim", "x86asm", "xl", "xml", "xquery", "yaml", "zephir.js"};

            String[] testMessage = {"", "", "", "", "", "", "", "", "", "", "", ""};
            int i = 0;

            for (String lang : langs) {
                String current = lang + "```" + lang + '\n' + text + "```";
                if (testMessage[i].length() + current.length() > 999)
                    i++;
                if (i > 11) {
                    i--;
                    break;
                }
                testMessage[i] += current;
            }
            EmbedBuilder response = new EmbedBuilder();

            response.setTitle("Test:");
            for (int c = 0; c <= i && c < 6; c++)
                response.addField("", testMessage[c], true);
            channel.sendMessage(response.build()).queue();
            if (i > 5) {
                EmbedBuilder response2 = new EmbedBuilder().setTitle("Test:");
                for (int c = 6; c <= i; c++)
                    response2.addField("", testMessage[c], true);
                channel.sendMessage(response2.build()).queue();
            }
        }
    }

    public static void shutdownCommand(JDA jda, Member sender, TextChannel channel) {
        if (sender.getId().equals("470696578403794967")) {
            try {
                channel.sendMessage("⚠️**System shutting down...**⚠️").queue();
                jda.shutdown();
            } catch (Exception ignored) {
                channel.sendMessage("Error").queue();
            }
        }
    }


    /*
     * Other stuff
     */

    /**
     * Check if a member is a moderator in a guild.
     *
     * @param guildID The guild to check.
     * @param member  The member to check.
     * @return True, if the member is an Admin in the guild or has one of the moderator roles.
     */
    public static boolean isModerator(String guildID, Member member) {
        if (member.hasPermission(Permission.ADMINISTRATOR))
            return true;

        List<String> roles = member.getRoles().stream().map(ISnowflake::getId).collect(Collectors.toList());
        Set<Long> modroles = ServerData.get(Long.parseLong(guildID)).getModRoles();

        for (String id : roles) {
            if (modroles.contains(Long.parseLong(id)))
                return true;
        }
        return false;
    }

    /**
     * Checks if the bot has the specified perms in a specific channel and sends a message to another channel if not.
     *
     * @param errorChannel The {@link TextChannel channel} to send an error message to.
     * @param testChannel  The {@link GuildChannel channel} to check the permissions on. If this is null the perms for the guild are checked.
     * @param permissions  The {@link Permission permissions} to check for.
     * @return True, if the bot doesn't have at least one of the permissions.
     */
    public static boolean checkPerms(TextChannel errorChannel, GuildChannel testChannel, Permission... permissions) {
        Member selfMember = errorChannel.getGuild().getSelfMember();

        List<Permission> missingPerms = new ArrayList<>();
        String channeltext = "";

        if (testChannel == null) {
            for (Permission p : permissions) {
                if (!selfMember.hasPermission(p))
                    missingPerms.add(p);
            }
        } else {
            for (Permission p : permissions) {
                if (!selfMember.hasPermission(testChannel, p))
                    missingPerms.add(p);
            }
            channeltext = " in #" + testChannel.getName();
        }

        if (missingPerms.isEmpty())
            return false;
        else {
            errorChannel.sendMessage(new EmbedBuilder().setColor(14495300)
                    .addField("To use this command please give me the following permissions" + channeltext + ":", missingPerms.stream().map(p -> "• " + p.toString()).collect(Collectors.joining("\n")), false)
                    .build()).queue();
            return true;
        }
    }

    /**
     * Send an error message.
     *
     * @param channel The {@link TextChannel channel} to send the message to.
     * @param message The message content.
     */
    public static void sendError(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(14495300).setDescription("❌ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    /**
     * Send a success message.
     *
     * @param channel The {@link TextChannel channel} to send the message to.
     * @param message The message content.
     */
    public static void sendSuccess(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(7844437).setDescription("✅ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    /**
     * Send an info message.
     *
     * @param channel The {@link TextChannel channel} to send the message to.
     * @param message The message content.
     */
    public static void sendInfo(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(3901635).setDescription("ℹ️ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    /**
     * Get the minecraft name from a nickname.
     *
     * @param nickname The nickname. This should be either "name(minecraft name)" or "minecraft name".
     * @return The minecraft name.
     */
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
     * Parse the ID of a string. This string can either be the raw ID or a discord mention.
     *
     * @param input The input sting to be parsed.
     * @return The ID in the string or 0 if none could be parsed.
     */
    public static long parseID(String input) {
        if (input.startsWith("\\<"))
            input = input.substring(1);
        if (input.charAt(0) == '<' && input.charAt(input.length() - 1) == '>') {
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
        if (input.isEmpty())
            return 0;
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Returns a member specified by a String (as mention, raw ID or discord tag) or null if none was found.
     *
     * @param guild The guild the member is represented in.
     * @param input The input string.
     * @return Possibly-null Member.
     */
    public static Member parseMember(Guild guild, String input) {
        Member m = guild.getMemberById(parseID(input));
        if (m == null) {
            try {
                m = guild.getMemberByTag(input);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return m;
    }

    /**
     * Get an easy readable time from ann amount in seconds.
     *
     * @param timeInSec The time in seconds.
     * @return The time in days, hours, minutes, seconds.
     */
    public static String parseTime(long timeInSec) {
        List<String> time = new LinkedList<>();
        time.add(timeInSec / (60 * 60 * 24) + "d");
        time.add((timeInSec / (60 * 60)) % 24 + "h");
        time.add((timeInSec / 60) % 60 + "m");
        time.add(timeInSec % 60 + "s");
        time.removeIf(v -> v.charAt(0) == '0');
        if (time.size() == 0)
            return "0s";
        return String.join(", ", time);
    }

    /**
     * Updates the nicknames of users in a specified guild.
     *
     * @param channel The {@link TextChannel channel} to send the results to (can be null).
     * @param guild   The specified {@link Guild guild}.
     * @param hide    If no message should be sent when no names are updated.
     */
    public static void updateNames(TextChannel channel, Guild guild, boolean hide) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Updated Users:")
                .setColor(defaultColor);

        HashMap<Long, String[]> changed = UserData.get(guild.getIdLong()).updateNames(guild.getMembers());
        if (changed.size() == 1)
            eb.setTitle("Updated User:");

        if (!changed.isEmpty()) {
            StringBuilder updated = new StringBuilder();
            StringBuilder removed = new StringBuilder();
            StringBuilder failed = new StringBuilder();
            for (Map.Entry<Long, String[]> entry : changed.entrySet()) {
                String[] s = entry.getValue();
                if (s[0].equals("-"))
                    removed.append("<@").append(entry.getKey()).append(">\n");
                else if (s[0].equals("e"))
                    failed.append("<@").append(entry.getKey()).append(">\n");
                else
                    updated.append("<@").append(entry.getKey()).append(">").append(" (").append(s[0]).append(" -> ").append(s[1]).append(")\n");
            }
            if (updated.length() != 0) {
                if (updated.length() > 2048)
                    eb.setDescription(updated.length() + " users were updated.");
                else
                    eb.setDescription(updated.toString());
            } else
                eb.setDescription("No users were updated.");
            if (removed.length() != 0) {
                if (removed.length() < 1024)
                    eb.addField("\nRemoved Users:", removed.toString(), false);
                else
                    eb.addField("", removed.length() + " users were removed from the system.", false);
            }
            if (failed.length() != 0) {
                if (failed.length() < 1024)
                    eb.addField("\nFailed Users:", failed.toString(), false);
                else
                    eb.addField("", "Updating failed on " + failed.length() + " users.", false);
            }

            TextChannel namechannel = guild.getTextChannelById(ServerData.get(guild.getIdLong()).getNameChannel());
            try {
                if ((namechannel != null) && (!namechannel.equals(channel)))
                    namechannel.sendMessage(eb.build()).queue();
            } catch (InsufficientPermissionException ignored) {
            }
        } else {
            if (hide)
                return;
            eb.setDescription("No users were updated.");
        }

        if (channel != null)
            channel.sendMessage(eb.build()).queue();
    }

    /**
     * Updates the leaderboard messages in a specified guild.
     *
     * @param channel      The {@link TextChannel channel} to send the results to (can be null).
     * @param guild        The specified {@link Guild guild}.
     */
    public static void updateLeaderboards(TextChannel channel, Guild guild) {
        long guildID = guild.getIdLong();

        try {
            Leaderboards.updateLeaderboards();
        } catch (Leaderboards.LeaderboardFetchFailedException e) {
            System.out.println("Leaderboard update failed! " + e.getMessage());
            if(channel != null)
                sendError(channel, "Leaderboard updating failed! Please try again in a bit or if that doesn't work contact the bot dev. " + e.getMessage());
            return;
        }

        long[][] data = ServerData.get(guildID).getAllLbMessages();
        for (int i = 0; i < 3; i++) {
            if (data[i][0] == 0)
                continue;

            TextChannel editChannel = guild.getTextChannelById(data[i][0]);
            if (editChannel == null)
                continue;

            List<String> lb = Leaderboards.lbToString(i, guildID);
            assert lb != null;

            EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor).setDescription(String.valueOf(System.currentTimeMillis()));
            eb.addField(new String[]{"Hider Wins", "Hunter Wins", "Kills"}[i] + " Leaderboard:", lb.remove(0), false);
            for (String s : lb) {
                eb.addField("", s, false);
            }
            eb.setFooter("Last update: ");
            eb.setTimestamp(Instant.ofEpochMilli(Leaderboards.getDate()));
            try {
                editChannel.editMessageById(data[i][1], eb.build()).queue();
            } catch (IllegalArgumentException ignored) {
            } catch (ErrorResponseException e) {
                if (channel != null)
                    sendError(channel, "An error occurred when updating lb " + i + ": " + e.getMessage());
            }
        }
        if (channel != null)
            sendSuccess(channel, "Updated leaderboards.");
    }
}
