package com.tfred.moderationbot;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import java.util.List;

public class Commands {
    public static void process(MessageReceivedEvent event, ServerData serverdata) {
        Guild guild = event.getGuild();;
        MessageChannel channel = event.getChannel();
        Message message = event.getMessage();
        String msg = message.getContentDisplay();
        Member member = message.getMember();


        if (msg.equals("!help")) {
            channel.sendMessage("Help:\n-``!delreaction <emoji> <amount>``: delete all reactions with a specified emoji <amount> messages back (max 100).\n-``!modrole <add|remove|list> [role]``: add/remove a modrole or list the mod roles for this server.\n-``!nosalt``: toggle no salt mode.").queue();
        }

        else if (msg.startsWith("!delreaction ")) {
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
    }

    private static boolean isModerator(String serverID, Member member, ServerData serverdata) {
        for(Role r: member.getRoles()) {
            if(serverdata.isModRole(serverID, r.getId()))
                return true;
        }
        return false;
    }
}
