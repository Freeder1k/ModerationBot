package com.tfred.moderationbot.moderation;

import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import java.io.IOException;

public class ModerationHandler {
    /**
     * Mute a user.
     * This gets automatically logged in the guilds punishment channel.
     *
     * @param member              The member to mute.
     * @param severity            The severity (1-5).
     * @param reason              The specified reason.
     * @param moderatorID         The member ID of the moderator.
     * @throws ModerationException A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static MutePunishment mute(Member member, short severity, String reason, long moderatorID) throws ModerationException {
        Guild g = member.getGuild();
        ServerData serverData = ServerData.get(g.getIdLong());

        if (severity <= 0 || severity >= 6)
            throw new ModerationException("Invalid severity: " + severity);

        if (!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES))
            throw new ModerationException("The bot is missing the manage roles permission!");

        Role mutedRole = g.getRoleById(serverData.getMutedRole());
        if (mutedRole == null)
            throw new ModerationException("Please set a new muted role with ``!config mutedrole <@role>``!");

        if (reason.length() == 0)
            reason = "None.";

        MutePunishment p;
        try {
            p = new MutePunishment(g.getIdLong(), member.getIdLong(), moderatorID, severity, reason);
        } catch (IOException ignored) {
            throw new ModerationException("An internal error occurred while calculating punishment length! Please try again in a bit.");
        }

        try {
            ModerationData.savePunishment(g.getIdLong(), p);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ModerationException("An internal error occurred while logging punishment! Please try again in a bit.");
        }

        try {
            PunishmentScheduler.get().schedule(g.getIdLong(), p);
            g.addRoleToMember(member, mutedRole).queue();
            p.log(g);
            return p;
        } catch (Exception e) {
            throw new ModerationException("Unable to mute <@" + member.getId() + "! " + e.getMessage());
        }
    }

    /**
     * Ban a user.
     * This gets automatically logged in the guilds punishment channel.
     *
     * @param guild The guild to ban the user from.
     * @param user              The user to ban.
     * @param severity            The severity (1 or 2).
     * @param reason              The specified reason.
     * @param moderatorID         The member ID of the moderator.
     * @throws ModerationException A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static BanPunishment ban(Guild guild, User user, short severity, String reason, long moderatorID) throws ModerationException {

        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            throw new ModerationException("The bot is missing the ban members permission!");
        }

        if (severity <= 0 || severity >= 3)
            throw new ModerationException("Invalid severity: " + severity);

        if (reason.length() == 0)
            reason = "None.";

        BanPunishment p;
        try {
            p = new BanPunishment(guild.getIdLong(), user.getIdLong(), moderatorID, severity, reason);
        } catch (IOException ignored) {
            throw new ModerationException("An internal error occurred while calculating punishment length! Please try again in a bit.");
        }

        try {
            ModerationData.savePunishment(guild.getIdLong(), p);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ModerationException("An internal error occurred while logging punishment! Please try again in a bit.");
        }

        try {
            try {
                user.openPrivateChannel().queue((pc) -> pc.sendMessage("You were banned from " + guild.getName() + ". Reason:\n" + p.reason).queue());
            } catch (Exception e) {
                e.printStackTrace();
            }

            guild.ban(user, 0, "Punishment id " + p.id + ":\n" + reason).queue();
            p.log(guild);
            return p;
        } catch (Exception e) {
            throw new ModerationException("Unable to ban <@" + user.getId() + ">! " + e.getMessage());
        }
    }

    /**
     * Ban a user from a channel.
     * This gets automatically logged in the guilds punishment channel.
     *
     * @param member              The member to punish.
     * @param channelID           The channel ID.
     * @param reason              The specified reason. If there is none please specify "None." as reason. Empty strings might lead to errors later on.
     * @param moderatorID         The member ID of the moderator.
     * @throws ModerationException A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static ChannelBanPunishment channelBan(Member member, long channelID, String reason, long moderatorID) throws ModerationException {
        Guild g = member.getGuild();

        GuildChannel channel = g.getGuildChannelById(channelID);
        if (channel == null)
            throw new ModerationException("Unknown channel: <#" + channelID + ">!");

        ChannelBanPunishment p;
        try {
            p = new ChannelBanPunishment(g.getIdLong(), member.getIdLong(), moderatorID, channelID, reason);
        } catch (IOException ignored) {
            throw new ModerationException("An internal error occurred while calculating punishment length! Please try again in a bit.");
        }

        try {
            ModerationData.savePunishment(g.getIdLong(), p);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ModerationException("An internal error occurred while logging punishment! Please try again in a bit.");
        }
        try {
            PunishmentScheduler.get().schedule(g.getIdLong(), p);
            channel.putPermissionOverride(member).setDeny(Permission.VIEW_CHANNEL).queue();
            p.log(g);
            return p;
        } catch (Exception e) {
            throw new ModerationException("Unable remove <@" + member.getId() + ">'s access to <#" + channel.getId() + "! " + e.getMessage());
        }
    }

    /**
     * Pardon an active punishment.
     * This gets automatically logged in the guilds punishment channel.
     *
     * @param guild        The guild this is in.
     * @param punishmentID The ID of the punishment to stop.
     * @param reason       The reason for this pardon.
     * @param moderatorID  The ID of the moderator.
     * @param hide         Whether the pardoned punishment should influence the length of future punishments. It will still show up in mod logs.
     * @param stopAll      If other active punishments of similar similarity should be ignored while unpunishing. This is mostly to save processing time when pardoning all punishments of a user.
     * @return The response message. Similar to the one from punish but with unpunish messages.
     * @throws ModerationException A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static String pardon(Guild guild, int punishmentID, String reason, long moderatorID, boolean hide, boolean stopAll) throws ModerationException {
        TimedPunishment old_p;
        try {
            old_p = ModerationData.removeActivePunishment(guild.getIdLong(), punishmentID);
        } catch (IOException e) {
            throw new ModerationException("An IO error occured while updating active.data (<@470696578403794967>)! " + e.getMessage());
        }
        if (old_p == null)
            throw new ModerationException("No matching active punishment with id " + punishmentID + " found.");

        PardonPunishment p = new PardonPunishment(guild.getIdLong(), old_p.userID, moderatorID, hide, punishmentID, reason);
        ModerationData.savePardon(guild.getIdLong(), p);
        p.log(guild);

        return endPunishment(guild, old_p, stopAll);
    }

    protected static String endPunishment(Guild guild, TimedPunishment punishment, boolean stopAll) throws ModerationException {
        //check if user has other active punishments
        if (!stopAll) {
            try {
                TimedPunishment[] activePunishments = ModerationData.getActivePunishments(guild.getIdLong());
                if (!(activePunishments.length == 0))
                    for (TimedPunishment p : activePunishments) {
                        if (p.userID == punishment.userID) {
                            if (punishment.getClass().equals(p.getClass())) {
                                return "<@" + punishment.userID + "> still has other active punishments of similar type.";
                            }
                        }
                    }
            } catch (IOException ignored) {
                throw new ModerationException("An IO error occurred while checking for other active punishments! User won't be unpunished. Punishment: " + punishment.toString());
            }
        }

        return punishment.end(guild);
    }
}
