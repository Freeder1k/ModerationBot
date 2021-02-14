package com.tfred.moderationbot.moderation;

import com.tfred.moderationbot.ServerData;
import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.IOException;

public class ModerationListener extends ListenerAdapter {
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member m = event.getMember();
        Guild guild = event.getGuild();
        ServerData serverData = ServerData.get(guild.getIdLong());

        TextChannel channel = guild.getTextChannelById(serverData.getJoinChannel());
        boolean canWrite = true;
        if (channel == null)
            canWrite = false;
        else if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_WRITE, Permission.VIEW_CHANNEL, Permission.MESSAGE_EMBED_LINKS))
            canWrite = false;

        try {
            String response = "";
            for (TimedPunishment p : ModerationData.getActivePunishments(guild.getIdLong())) {
                if (p.userID == m.getIdLong()) {
                    if (p instanceof MutePunishment) {
                        Role mutedRole = guild.getRoleById(serverData.getMutedRole());
                        if (mutedRole == null)
                            response = "Please set a muted role with ``!config mutedrole <@role>``!";
                        else if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES))
                            response = "The bot is missing the manage roles permission!";
                        else {
                            guild.addRoleToMember(m, mutedRole).queue();
                            response = m.getAsMention() + " is currently muted.";
                        }
                    } else if (p instanceof BanPunishment) {
                        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS))
                            response = "The bot is missing the ban members permission!";
                        else
                            response = m.getAsMention() + " should be banned!";
                    } else if (p instanceof ChannelBanPunishment) {
                        GuildChannel bannedChannel = guild.getGuildChannelById(((ChannelBanPunishment) p).channelID);
                        if (bannedChannel != null) {
                            if (!guild.getSelfMember().hasPermission(bannedChannel, Permission.MANAGE_PERMISSIONS))
                                response = "The bot is missing the manage permissions permission in <#" + bannedChannel.getId() + "> in order to ban <@" + p.userID + "> from it!";
                            else {
                                bannedChannel.putPermissionOverride(m).setDeny(Permission.VIEW_CHANNEL).queue();
                                response = m.getAsMention() + " is currently banned from <#" + bannedChannel.getId() + ">.";
                            }
                        }
                    } else if (p instanceof NamePunishment) {
                        Role noNickRole = guild.getRoleById(serverData.getNoNicknameRole());
                        if (noNickRole == null)
                            response = "Please set a no nickname role with ``!config nonickrole <@role>``!";
                        else if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES))
                            response = "The bot is missing the manage roles permission!";
                        else {
                            guild.addRoleToMember(m, noNickRole).queue();
                            response = m.getAsMention() + " is currently blocked from changing their nickname.";
                        }
                    }
                }
                if ((!response.isEmpty()) && canWrite)
                    CommandUtils.sendError(channel, response);
            }
        } catch (IOException ignored) {
            System.out.println("IO ERROR ON ACTIVE.DATA FOR " + guild.getName());
            if (canWrite)
                CommandUtils.sendError(channel, "Failed to read active punishment data <@470696578403794967>.");
        }
    }
}
