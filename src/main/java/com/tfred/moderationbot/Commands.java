package com.tfred.moderationbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Commands {
    public static void process(MessageReceivedEvent event, ServerData serverdata, UserData userData) {
        Guild guild = event.getGuild();;
        MessageChannel channel = event.getChannel();
        Message message = event.getMessage();
        String msg = message.getContentDisplay();
        Member member = message.getMember();


        if (msg.equals("!help")) {
            channel.sendMessage("Help:\n-``!delreaction <emoji> <amount>``: delete all reactions with a specified emoji <amount> messages back (max 100).\n-``!modrole <add|remove|list> [role]``: add/remove a modrole or list the mod roles for this server.\n-``!nosalt``: toggle no salt mode.\n-``!name <set|remove> [username] @user``: set a mc username of a user or remove a user from the system.\n-``!updatenames``: look for name changes and update the nicknames of users.\n-``!listnames``: list the names of members who are/aren't added to the username system.").queue();
        }

        else if (msg.startsWith("!delreaction")) {
            if((member.hasPermission(Permission.ADMINISTRATOR) || (isModerator(guild.getId(), member, serverdata)))) {
                Member selfMember = guild.getSelfMember();

                if (!selfMember.hasPermission(Permission.MESSAGE_MANAGE)) {
                    channel.sendMessage("Please make sure to give me the manage messages permission!").queue();
                    return; //We jump out of the method instead of using cascading if/else
                }

                String[] args = msg.split(" ");

                if(args.length != 3) {
                    channel.sendMessage("Invalid amount of arguments!").complete();
                    return;
                }

                String emoji = args[1];

                int amount ;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    channel.sendMessage("Error parsing amount!").complete();
                    return;
                }
                if(amount > 100 || amount < 1) {
                    channel.sendMessage("Amount must be in range 1-100").complete();
                    return;
                }

                channel.sendMessage("Removing reactions...").complete();

                try {
                    for (Message m : message.getChannel().getHistory().retrievePast(amount).complete()) {
                        m.clearReactions(emoji).complete();
                    }
                } catch (ErrorResponseException e) {
                    channel.sendMessage("Unknown emoji: " + emoji + ".\nFor custom emojis please add the ID after the emoji. Example: ``:emoji:123456789``.").queue();
                    return;
                }

                channel.sendMessage("Finished removing reactions with " + emoji + ".").queue();
            }
        }

        else if (msg.startsWith("!modrole ")) {
            String guildID = guild.getId();
            if((message.getMember().hasPermission(Permission.ADMINISTRATOR))) {
                String[] args = msg.split(" ");

                if(args.length < 2) {
                    channel.sendMessage("Invalid amount of arguments!").complete();
                    return;
                }

                if(args[1].equals("list")) {
                    List<String> roles = serverdata.getModRoles(guildID);

                    if(roles.isEmpty()) {
                        channel.sendMessage("There are no moderator roles.").queue();
                        return;
                    }

                    String res = "";
                    for(String s: roles) {
                        Role r = event.getGuild().getRoleById(s);
                        if(r != null)
                            res = res.concat(" ``" + r.getName() + "``,");
                    }
                    res = res.substring(0, res.length() - 1);

                    channel.sendMessage("Moderator roles:" + res + ".").queue();
                    return;
                }

                Role role;
                try {
                    role = message.getMentionedRoles().get(0);
                } catch (IndexOutOfBoundsException e) {
                    channel.sendMessage("Please mention a role!").complete();
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
            String guildID = guild.getId();
            if((member.hasPermission(Permission.ADMINISTRATOR) || (isModerator(guildID, member, serverdata)))) {
                if (serverdata.isNoSalt(guildID)) {
                    serverdata.setNoSalt(guildID, false);
                    channel.sendMessage("No salt mode disabled.").queue();
                } else {
                    serverdata.setNoSalt(guildID, true);
                    channel.sendMessage("No salt mode enabled!").queue();
                }
            }
        }

        else if (msg.startsWith("!name")) {
            String guildID = guild.getId();
            if((member.hasPermission(Permission.ADMINISTRATOR) || (isModerator(guildID, member, serverdata)))) {
                String[] args = msg.split(" ");

                if(args.length < 3) {
                    channel.sendMessage("Insufficient amount of arguments!").complete();
                    return;
                }

                Member member1;
                try {
                    member1 = message.getMentionedMembers().get(0);
                } catch (IndexOutOfBoundsException e) {
                    channel.sendMessage("Please mention a member!").complete();
                    return;
                }

                //try {
                    if (args[1].equals("set")) {
                        if(userData.setUserInGuild(guildID, member1.getUser().getId(), args[2]) == 1)
                            channel.sendMessage("Set " + args[2] + " as username of " + member1.getEffectiveName() + ".").queue();
                        else
                            channel.sendMessage(args[2] + " isn't a valid Minecraft username.").queue();
                    } else if (args[1].equals("remove")) {
                        userData.removeUserFromGuild(guildID, member1.getUser().getId());
                        channel.sendMessage("Removed " + member1.getEffectiveName() + "'s username.").queue();
                    } else
                        channel.sendMessage("Unknown action. Allowed actions: ``set, remove``.").queue();
                //} catch (IndexOutOfBoundsException e) {
                //    channel.sendMessage("Insufficient amount of arguments 2!").queue();
                //}
            }
        }

        else if (msg.equals("!updatenames")) {
            String guildID = guild.getId();
            if((member.hasPermission(Permission.ADMINISTRATOR) || (isModerator(guildID, member, serverdata)))) {
                channel.sendMessage("Updating usernames (please note that the bot cannot change the nicknames of users with a higher role).").complete();
                userData.updateGuildUserData(guildID);
                channel.sendMessage("Finished updating usernames.").queue();
            }
        }

        else if (msg.startsWith("!addallmembers")) {
            String guildID = guild.getId();
            if((member.hasPermission(Permission.ADMINISTRATOR))) {
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
                    System.out.println(name);
                    int x = userData.setUserInGuild(guildID, m.getUser().getId(), name);
                    if(x == 0)
                        failed.add(m);
                }
                String donemessage = "Done.";
                if(!failed.isEmpty()) {
                    donemessage += "\nFailed to add following users:\n";
                    for(Member m: failed) {
                        donemessage += m.getAsMention() + "\n";
                    }
                }
                channel.sendMessage(donemessage).queue();
            }
        }

        else if (msg.equals("!listnames")) {
            String guildID = guild.getId();
            if((member.hasPermission(Permission.ADMINISTRATOR) || (isModerator(guildID, member, serverdata)))) {
                String output1 = "**Saved users:**";
                String output2 = "\n\n**Users who haven't beed added yet:**";
                if(userData == null) {
                    channel.sendMessage("UserData is null!").queue();
                    return;
                }
                List<String> ids = userData.getGuildSavedUserIds(guildID);
                for(Member m: guild.getMembers()) {
                    if(ids.contains(m.getUser().getId()))
                        output1 += "\n" + m.getEffectiveName();
                    else
                        output2 += "\n" + m.getEffectiveName();
                }
                channel.sendMessage(output1 + output2).queue();
            }
        }
    }

    private static boolean isModerator(String serverID, Member member, ServerData serverdata) {
        for(Role r: member.getRoles()) {
            if(serverdata.isModRole(serverID, r.getId()))
                return true;
        }
        return false;
    }
}
