package com.tfred.moderationbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Commands {
    public static void process(MessageReceivedEvent event, ServerData serverdata, UserData userData, Leaderboards leaderboards) {
        Guild guild = event.getGuild();
        String guildID = guild.getId();
        TextChannel channel = event.getTextChannel();
        Message message = event.getMessage();
        String msg = message.getContentDisplay();
        Member member = message.getMember();
        if(member == null)
            return;


        if (msg.equals("!help")) {
            channel.sendMessage("Help:\n" +
                    "-``!delreaction <emoji> <amount>``: delete all reactions with a specified emoji <amount> messages back (max 100).\n" +
                    "-``!modrole <add|remove|list> [role]``: add/remove a mod role or list the mod roles for this server.\n" +
                    "-``!nosalt``: toggle no salt mode.\n" +
                    "-``!name <set|remove> [username] @user``: set a mc username of a user or remove a user from the system.\n" +
                    "-``!updatenames``: look for name changes and update the nicknames of users.\n" +
                    "-``!listnames [@role/roleID]``: list the names of members who are/aren't added to the username system with optional role requirement.\n" +
                    "-``!lb <board>``: sends a message with a bh leaderboard corresponding to the lb number that can be updated with !updatelb. (0: hider, 1: hunter, 2: kills).\n" +
                    "-``!updatelb``: updated the lb messages.\n" +
                    "-``!setlogchannel``: set this channel to be the log channel for automatic updates."
            ).queue();
        }

        else if (msg.startsWith("!delreaction ")) {
            if(isModerator(guildID, member, serverdata)) {
                if (checkPerms(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY))
                    return;

                String[] args = msg.split(" ");

                if(args.length != 3) {
                    channel.sendMessage("Invalid amount of arguments!").queue();
                    return;
                }

                String emoji = args[1];

                int amount ;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    channel.sendMessage("Error parsing amount!").queue();
                    return;
                }
                if(amount > 100 || amount < 1) {
                    channel.sendMessage("Amount must be in range 1-100!").queue();
                    return;
                }

                channel.sendMessage("Removing reactions...").complete();

                try {
                    for (Message m : channel.getHistory().retrievePast(amount).complete()) {
                        m.clearReactions(emoji).queue();
                    }
                } catch (ErrorResponseException e) {
                    channel.sendMessage("Unknown emoji: " + emoji + "!\nFor custom emojis please add the ID after the emoji. Example: ``:emoji:123456789``.").queue();
                    return;
                }

                channel.sendMessage("Removed reactions with " + emoji + " on " + amount + " messages.").queue();
            }
        }

        else if (msg.startsWith("!modrole ")) {
            if(member.hasPermission(Permission.ADMINISTRATOR)) {
                String[] args = msg.split(" ");

                if(args.length < 2) {
                    channel.sendMessage("Invalid amount of arguments!").queue();
                    return;
                }

                if(args[1].equals("list")) {
                    List<String> roles = serverdata.getModRoles(guildID);

                    if(roles.isEmpty()) {
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
                    channel.sendMessage("Please mention a member!").complete();
                    return;
                }

                if (args[1].equals("set")) {
                    if(userData.setUserInGuild(guildID, member1.getUser().getId(), args[2]) == 1)
                        channel.sendMessage("Set " + args[2] + " as username of " + member1.getEffectiveName() + ".").queue();
                    else
                        channel.sendMessage(args[2] + " isn't a valid Minecraft username!").queue();
                } else if (args[1].equals("remove")) {
                    userData.removeUserFromGuild(guildID, member1.getUser().getId());
                    channel.sendMessage("Removed " + member1.getEffectiveName() + "'s username.").queue();
                } else
                    channel.sendMessage("Unknown action! Allowed actions: ``set, remove``.").queue();
            }
        }

        else if (msg.equals("!updatenames")) {
            if(isModerator(guildID, member, serverdata))
                updateNames(channel, userData, guild);
        }

        else if (msg.startsWith("!addallmembers ")) {
            if(member.hasPermission(Permission.ADMINISTRATOR)) {
                List<Member> failed = new ArrayList<>();

                Role role;
                try {
                    role = message.getMentionedRoles().get(0);
                } catch (IndexOutOfBoundsException e) {
                    channel.sendMessage("Please mention a role!").complete();
                    return;
                }

                channel.sendMessage("Adding members to internal save data.").complete();

                for(Member m: guild.getMembersWithRoles(role)) {
                    String name = m.getEffectiveName();
                    if(name.endsWith(")")) {
                        Pattern pattern = Pattern.compile("\\((.*?)\\)");
                        Matcher matcher = pattern.matcher(name);
                        if(matcher.find())
                            name = matcher.group(1);
                    }
                    //System.out.println(name);
                    int x = userData.setUserInGuild(guildID, m.getUser().getId(), name);
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
        }

        else if (msg.startsWith("!lb ")) {
            if((member.hasPermission(Permission.ADMINISTRATOR))) {
                if (msg.length() != 5) {
                    channel.sendMessage("Single number argument required.").queue();
                    return;
                }
                char board = msg.charAt(4);
                if ((board != '0') && (board != '1') && (board != '2')) {
                    channel.sendMessage("Board number must be between 0 and 2!").queue();
                    return;
                }

                List<String> lb = leaderboards.lbToString(Character.getNumericValue(board), guildID, userData);
                EmbedBuilder eb = new EmbedBuilder();
                eb.addField("Leaderboard:", lb.remove(0), false); //TODO specify which leaderboard
                for (String s : lb) {
                    eb.addField("", s, false);
                }

                String msgID = channel.sendMessage(eb.build()).complete().getId();
                String channelID = channel.getId();

                serverdata.setLbData(guildID, Character.getNumericValue(board), channelID, msgID);
            }
        }

        else if (msg.equals("!updatelb")) {
            if(member.hasPermission(Permission.ADMINISTRATOR))
                updateLeaderboards(channel, leaderboards, serverdata, userData, guild);
        }

        else if (msg.equals("!setlogchannel")) {
            if(member.hasPermission(Permission.ADMINISTRATOR)) {
                serverdata.setLogChannelID(guildID, channel.getId());
                channel.sendMessage("Set log channel to " + channel.getAsMention() + ".").queue();
            }
        }

        else if (msg.equals("!forceupdate")) {
            if(member.getId().equals("470696578403794967")) {
                System.out.println("Force updating!");
                ModerationBot.autoRunDaily();
            }
        }
    }

    private static boolean isModerator(String serverID, Member member, ServerData serverdata) {
        if(member.hasPermission(Permission.ADMINISTRATOR))
            return true;

        for(Role r: member.getRoles()) {
            if(serverdata.isModRole(serverID, r.getId()))
                return true;
        }
        return false;
    }

    //checks if the bot has the specified perms and send a message to the channel if not (true if doesnt have perms)
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

    public static void updateNames(TextChannel channel, UserData userData, Guild guild) {
        String guildID = guild.getId();

        if(channel != null)
            channel.sendMessage("Updating usernames (please note that the bot cannot change the nicknames of users with a higher role).").complete();

        List<String> changed = userData.updateGuildUserData(guildID);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Results of !updatenames:");

        if(changed.isEmpty())
            eb.setDescription("No users were updated.");

        else if (changed.size() < 100) {
            StringBuilder mentions = new StringBuilder();

            for(String s: changed) {
                Member m = guild.getMemberById(s);
                if(m != null)
                    mentions.append(m.getAsMention()).append(" (").append(m.getNickname()).append(")\n");
            }
            eb.addField("Updated Users:", mentions.toString(), false);
        }
        else
            eb.setDescription(changed.size() + " users were updated.");

        if(channel != null)
            channel.sendMessage(eb.build()).queue();
    }

    public static void updateLeaderboards(TextChannel channel, Leaderboards leaderboards, ServerData serverdata, UserData userData, Guild guild) {
        String guildID = guild.getId();

        leaderboards.updateLeaderboards();

        String[][] data = serverdata.getAllLbData(guildID);
        for(int i = 0; i < 3; i++) {
            if(data[i] == null)
                continue;

            TextChannel editChannel = guild.getTextChannelById(data[i][0]);
            if(editChannel == null)
                continue;

            List<String> lb = leaderboards.lbToString(i, guildID, userData);
            EmbedBuilder eb = new EmbedBuilder();
            eb.addField("Leaderboard:", lb.remove(0), false); //TODO specify which leaderboard
            for (String s : lb) {
                eb.addField("", s, false);
            }
            try {
                editChannel.editMessageById(data[i][1], eb.build()).queue();
                System.out.println("updated lb " + i);
            } catch (IllegalArgumentException ignored) {}
        }
        if(channel != null)
            channel.sendMessage("Updated leaderboards.").queue();
    }
}
