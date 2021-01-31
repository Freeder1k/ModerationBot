package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.Set;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super(
                "config",
                new String[]{"settings"},
                "!config [<option> <value> [action]]",
                "View or modify the configuration for this guild.\nValid syntax:```\n" +
                        "option:           │ value:  │ action:\n" +
                        "——————————————————│—————————│————————————\n" +
                        "modrole           │ role    │ add|remove\n" +
                        "memberrole        │ role    │\n" +
                        "mutedrole         │ role    │\n" +
                        "nonickrole        │ role    │\n" +
                        "logchannel        │ channel │\n" +
                        "joinchannel       │ channel │\n" +
                        "punishmentchannel │ channel │\n" +
                        "ventchannel       │ channel │\n" +
                        "namechannel       │ channel │```",
                new Permission[]{},
                false,
                true,
                false
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        ServerData serverData = ServerData.get(event.guild.getIdLong());
        if (event.args.length == 1) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("__Settings for " + event.guild.getName() + ":__").setColor(DEFAULT_COLOR);

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
                    leaderboardData.append("[<#").append(lbData[i][0]).append(">](https://discordapp.com/channels/").append(event.guild.getId()).append('/').append(lbData[i][0]).append('/').append(lbData[i][1]).append(" 'Message link')\n");
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


            event.channel.sendMessage(embedBuilder.build()).queue();
        } else {
            TextChannel channel = event.channel;
            String[] args = event.args;
            Guild guild = event.guild;

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
                default: {
                    sendError(channel, "Invalid option: " + args[1] + "!");
                    break;
                }
            }
        }
    }
}
