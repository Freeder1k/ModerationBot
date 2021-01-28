package com.tfred.moderationbot;

import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Moderation {
    /**
     * Get a modifiable list containing the active punishments for a guild. Changes to this list don't reflect back.
     *
     * @param guildID The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return A {@link LinkedList<ActivePunishment> list} of all active {@link Punishment punishments} in the specified guild.
     */
    public static LinkedList<ActivePunishment> getActivePunishments(long guildID) throws IOException {
        if (Files.exists(Paths.get("moderations/" + guildID + "/active.punishments"))) {
            return Files.readAllLines(Paths.get("moderations/" + guildID + "/active.punishments"))
                    .stream().map(ActivePunishment::fromString).collect(Collectors.toCollection(LinkedList::new));
        } else
            return new LinkedList<>();
    }

    /**
     * Get a modifiable list containing the punishment history for a user in a specified guild. Changes to this list don't reflect back.
     *
     * @param guildID The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID  The specified {@link net.dv8tion.jda.api.entities.User user's} ID.
     * @return A {@link List<Punishment> list} of all past {@link Punishment punishments} for that user in the specified guild.
     */
    public static LinkedList<Punishment> getUserPunishments(long guildID, long userID) throws IOException {
        if (Files.exists(Paths.get("moderations/" + guildID + "/" + userID + ".punishments")))
            return Files.readAllLines(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"))
                    .stream().map(Punishment::fromString).collect(Collectors.toCollection(LinkedList::new));
        else
            return new LinkedList<>();
    }

    /**
     * Get an array containing all user punishments for a guild.
     *
     * @param guildID
     *          The guilds ID.
     * @return
     *          An array containing user punishments.
     */
    public static UserPunishment[] getAllUserPunishments(long guildID) throws IOException {
        LinkedList<UserPunishment> res = new LinkedList<>();
        if (Files.exists(Paths.get("moderations/" + guildID))) {
            List<Long> ids = Files.find(Paths.get("moderations/" + guildID), 1, (p, bfa) -> bfa.isRegularFile() && p.getFileName().toString().matches("\\d+.punishments"))
                    .map(p -> {
                        String str = p.getFileName().toString();
                        return Long.parseLong(str.substring(0, str.length() - 12));
                    })
                    .collect(Collectors.toList());

            for (long id : ids) {
                try {
                    getUserPunishments(guildID, id).forEach((p) -> res.add(new UserPunishment(id, p)));
                } catch (IOException ignored) {
                    System.out.println("Failed to read punishments for user with ID " + id);
                }
            }
            return res.toArray(new UserPunishment[0]);
        }
        else
            return new UserPunishment[]{};
    }

    /**
     * Get the length of the next punishment of a specified severity for a user.
     *
     * @param guildID The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID  The specified {@link net.dv8tion.jda.api.entities.User user's} ID.
     * @param sev     The severity of the punishment (1-6 or v or n).
     * @return The length of the next punishment (in minutes).
     */
    private static int getPunishmentLength(long guildID, long userID, char sev) throws IOException {
        LinkedList<Punishment> punishments = getUserPunishments(guildID, userID);

        boolean prev = false; // If there is a previous punishment of same severity
        long end_date = 0;    // time till ^ ends
        int p_length = 0;     // duration of ^
        Set<Integer> hidden = new HashSet<>(); // Set of punishment ids that were pardoned and marked as hidden
        Set<Integer> pardoned = new HashSet<>(); // Set of punishment ids that were pardoned and not marked as hidden
        boolean was_pardoned = false;

        while (!punishments.isEmpty()) {
            Punishment p = punishments.removeLast();
            if (p.severity == sev) {
                if (!hidden.contains(p.id)) {
                    if (pardoned.contains(p.id))
                        was_pardoned = true;
                    prev = true;
                    end_date = p.date + (((long) p.length) * 60000);
                    p_length = p.length;
                    break;
                }
            }
            if (p.severity == 'u') {
                if (p.reason.charAt(0) == 'y') {
                    String s = p.reason.substring(2);
                    s = s.substring(0, s.indexOf(' '));
                    hidden.add(Integer.parseInt(s));
                } else {
                    String s = p.reason.substring(2);
                    s = s.substring(0, s.indexOf(' '));
                    pardoned.add(Integer.parseInt(s));
                }
            }
        }

        if (!prev)
            return Punishment.defaultLength(sev);

        long end_time = (System.currentTimeMillis() - end_date); //time since last punishment ended

        if (end_time < 0 && !was_pardoned)
            return p_length;
        else if (end_time < (((long) Punishment.specialBonusReqTime(sev)) * 60000))
            return p_length + Punishment.specialBonusLength(sev);
        else
            return p_length + Punishment.defaultBonusLength(sev);
    }

    /**
     * Write a punishment to the appropriate userID.data and active.data files.
     *
     * @param guildID    The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID     The specified {@link net.dv8tion.jda.api.entities.User user's} ID.
     * @param punishment The punishment to write.
     * @throws IOException If some IO error while creating or writing to the file occurs.
     */
    private static void addPunishment(long guildID, long userID, Punishment punishment) throws IOException {
        if (!Files.exists(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"))) {
            if (!Files.exists(Paths.get("moderations/" + guildID)))
                Files.createDirectories(Paths.get("moderations/" + guildID));
            Files.createFile(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"));
        }
        Files.write(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"), (punishment.toString() + '\n').getBytes(), StandardOpenOption.APPEND);

        if (!Files.exists(Paths.get("moderations/" + guildID + "/active.punishments")))
            Files.createFile(Paths.get("moderations/" + guildID + "/active.punishments"));
        try {
            Files.write(Paths.get("moderations/" + guildID + "/active.punishments"), (userID + ": " + punishment.toString() + '\n').getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            //Delete the last entry from the file above since that one must've worked.
            List<String> lines = Files.readAllLines(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"));
            lines.remove(lines.size() - 1);
            Files.write(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"), lines);

            throw new IOException(e);
        }
    }

    /**
     * Punish a user. Allowed severities are numbers 1-6 or v for a vent channel ban or n to block them from changing their nickname.
     *
     * @param member            The member to punish.
     * @param severity          The severity.
     * @param reason            The specified reason. If there is none please specify "None." as reason. Empty strings might lead to errors later on.
     * @param punisherID        The member ID of the punisher.
     * @param punishmentHandler The punishment handler that handles the unpunish scheduling.
     * @return The punishment.
     * @throws ModerationException A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static Punishment punish(Member member, char severity, String reason, long punisherID, PunishmentHandler punishmentHandler) throws ModerationException {
        Guild g = member.getGuild();
        ServerData serverData = ServerData.get(g.getIdLong());

        long id;
        TextChannel channel = null;
        Role role = null;
        Role role2 = null;

        switch (severity) {
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                id = serverData.getMutedRole();
                if (id == 0) {
                    throw new ModerationException("Please set a muted role with ``!config mutedrole <@role>``!");
                }
                if (!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("The bot is missing the manage roles permission!");
                }
                role = g.getRoleById(id);
                if (role == null) {
                    throw new ModerationException("Please set a new muted role with ``!config mutedrole <@role>``!");
                }
                break;
            case '6':
                if (!g.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
                    throw new ModerationException("The bot is missing the ban members permission!");
                }
                break;
            case 'v':
                id = serverData.getVentChannel();
                if (id == 0) {
                    throw new ModerationException("Please set a vent channel with ``!config ventchannel <#channel>``!");
                }
                channel = g.getTextChannelById(id);
                if (channel == null) {
                    throw new ModerationException("Vent channel was deleted! Please set a new vent channel with ``!config ventchannel <#channel>``!");
                }
                if (!g.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
                    throw new ModerationException("The bot is missing the manage permissions permission in " + channel.getAsMention() + "!");
                }
                break;
            case 'n':
                id = serverData.getNoNicknameRole();
                if (id == 0) {
                    throw new ModerationException("Please set a no nickname role with ``!config nonickrole <@role>``!");
                }
                if (!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("The bot is missing the manage roles permission!");
                }
                role = g.getRoleById(id);
                if (role == null) {
                    throw new ModerationException("noNickname role was deleted! Please set a no nickname role with ``!config nonickrole <@role>``!");
                }
                id = serverData.getMemberRole();
                if (id == 0) {
                    throw new ModerationException("Please set a member role with ``!config memberrole <@role>``!");
                }
                role2 = g.getRoleById(id);
                if (role2 == null) {
                    throw new ModerationException("Member role was deleted! Please set a no nickname role with ``!config memberrole <@role>``!");
                }
                break;
            default:
                throw new ModerationException("Invalid severity: " + severity);
        }

        Punishment p;
        try {
            int nextID = serverData.getNextPunishmentID();
            p = new Punishment(nextID, severity, reason, getPunishmentLength(g.getIdLong(), member.getIdLong(), severity), punisherID);
        } catch (IOException ignored) {
            throw new ModerationException("An internal error occurred while calculating punishment length! Please try again in a bit.");
        }

        try {
            addPunishment(g.getIdLong(), member.getIdLong(), p);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ModerationException("An internal error occurred while logging punishment! Please try again in a bit.");
        }

        switch (severity) {
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                try {
                    g.addRoleToMember(member, role).queue();
                    punishmentHandler.newPunishment(member.getIdLong(), g.getIdLong(), p);
                    return p;
                } catch (Exception e) {
                    throw new ModerationException("Unable to mute <@" + member.getId() + "! " + e.getMessage());
                }
            case '6':
                try {
                    g.ban(member, 0, reason).queue();
                    punishmentHandler.newPunishment(member.getIdLong(), g.getIdLong(), p);
                    return p;
                } catch (Exception e) {
                    throw new ModerationException("Unable to ban <@" + member.getId() + ">! " + e.getMessage());
                }
            case 'v':
                try {
                    channel.putPermissionOverride(member).setDeny(Permission.VIEW_CHANNEL).queue();
                    punishmentHandler.newPunishment(member.getIdLong(), g.getIdLong(), p);
                    return p;
                } catch (Exception e) {
                    throw new ModerationException("Unable remove <@" + member.getId() + ">'s access to <#" + channel.getId() + "! " + e.getMessage());
                }
            case 'n':
                try {
                    g.removeRoleFromMember(member, role2).queue();
                } catch (Exception e) {
                    throw new ModerationException("Unable to add the " + role2.getName() + " role to <@" + member.getId() + "! " + e.getMessage());
                }
                try {
                    g.addRoleToMember(member, role).queue();
                    punishmentHandler.newPunishment(member.getIdLong(), g.getIdLong(), p);
                    return p;
                } catch (Exception e) {
                    throw new ModerationException("Unable to add the " + role.getName() + " role to <@" + member.getId() + "! " + e.getMessage());
                }
            default:
                throw new ModerationException("Encountered an invalid severity whilst trying to punish <@" + member.getId() + ">. Punishment: " + p.toString());
        }
    }

    /**
     * Stop an active punishment.
     *
     * @param guild        The guild this is in.
     * @param punishmentID The ID of the punishment to stop.
     * @param reason       The reason for this punishment.
     * @param unpunisherID The ID of the unpunisher.
     * @param hide         Whether the pardoned punishment should influence the length of future punishments. It will still show up in mod logs.
     * @param stopAll      If other active punishments of similar similarity should be ignored while unpunishing. This is mostly to save processing time when pardoning all punishments of a user.
     * @return The response message. Similar to the one from punish but with unpunish messages.
     * @throws ModerationException A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static String stopPunishment(Guild guild, int punishmentID, String reason, long unpunisherID, boolean hide, boolean stopAll) throws ModerationException {
        ActivePunishment ap;
        try {
            ap = removeActivePunishment(guild.getIdLong(), punishmentID);
        } catch (IOException e) {
            throw new ModerationException("An IO error occured while updating active.data (<@470696578403794967>)! " + e.getMessage());
        }
        if (ap == null)
            throw new ModerationException("No matching active punishment with id " + punishmentID + " found.");

        long memberID = ap.memberID;

        String hideS = hide ? "y " : "n ";
        int nextID = ServerData.get(guild.getIdLong()).getNextPunishmentID();
        //The extra data for the unpunishment gets stored in the reason
        Punishment p = new Punishment(nextID, 'u', hideS + punishmentID + ' ' + ap.punishment.severity + ' ' + reason, 0, unpunisherID);
        try {
            Files.write(Paths.get("moderations/" + guild.getId() + "/" + memberID + ".punishments"), (p.toString() + '\n').getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new ModerationException("An IO error occurred while logging the unpunishment (<@470696578403794967>)! " + e.getMessage());
        }

        return p.id + " " + endPunishment(guild, memberID, ap.punishment, stopAll);
    }

    private static String endPunishment(Guild g, long memberID, Punishment punishment, boolean stopAll) throws ModerationException {
        //check if user has other active punishments
        if (!stopAll) {
            try {
                List<ActivePunishment> activePunishments = getActivePunishments(g.getIdLong());
                if (!activePunishments.isEmpty())
                    for (ActivePunishment ap : activePunishments) {
                        if (ap.memberID == memberID) {
                            if (("12345".indexOf(punishment.severity) != -1) && ("12345".indexOf(ap.punishment.severity) != -1)) {
                                return "<@" + memberID + "> still has other active punishments of similar severity.";
                            }
                            if (punishment.severity == ap.punishment.severity) {
                                return "<@" + memberID + "> still has other active punishments of similar severity.";
                            }
                        }
                    }
            } catch (IOException ignored) {
                throw new ModerationException("An IO error occurred while checking for other active punishments! User won't be unpunished. Punishment: " + punishment.toString());
            }
        }

        ServerData serverData = ServerData.get(g.getIdLong());

        Member member;
        long id;
        TextChannel channel;
        Role role;

        switch (punishment.severity) {
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                member = g.getMemberById(memberID);
                if (member == null) {
                    return "Unmuted <@" + memberID + ">.\nNote: User not in guild.";
                }
                id = serverData.getMutedRole();
                if (id == 0) {
                    throw new ModerationException("Unmute of <@" + memberID + "> failed! No muted role set.");
                }
                if (!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("Unmute of <@" + memberID + "> failed! Missing permissions: MANAGE_ROLES. Please remove the role manually.");
                }
                role = g.getRoleById(id);
                if (role == null) {
                    throw new ModerationException("Unmute of <@" + memberID + "> failed! Muted role doesn't exist anymore.");
                }
                try {
                    g.removeRoleFromMember(member, role).queue();
                    return "Unmuted <@" + memberID + ">.";
                } catch (Exception e) {
                    throw new ModerationException("Unmute of <@" + memberID + "> failed! " + e.getMessage());
                }
            case '6':
                try {
                    g.unban(String.valueOf(memberID)).complete();
                    return "Unbanned <@" + memberID + ">.";
                } catch (Exception e) {
                    throw new ModerationException("Unable to unban <@" + memberID + ">. " + e.getMessage());
                }
            case 'v':
                member = g.getMemberById(memberID);
                if (member == null) {
                    return "Removed <@" + memberID + ">'s ban from the vent channel.\nNote: User not in guild.";
                }
                id = serverData.getVentChannel();
                if (id == 0) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from the vent channel! No vent channel set.");
                }
                channel = g.getTextChannelById(id);
                if (channel == null) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from the vent channel! Vent channel doesn't exist anymore.");
                }
                if (!g.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from <#" + channel.getId() + ">! The bot is missing the manage permissions permission in this channel!");
                }
                try {
                    PermissionOverride perms = channel.getPermissionOverride(member);
                    if (perms != null)
                        perms.delete().queue();
                    return "Removed <@" + memberID + ">'s ban from the vent channel.";
                } catch (Exception e) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from <#" + channel.getId() + ">! " + e.getMessage());
                }
            case 'n':
                member = g.getMemberById(memberID);
                if (member == null) {
                    return "Removed <@" + memberID + ">'s nickname change ban.\nNote: User not in guild.";
                }
                id = serverData.getNoNicknameRole();
                if (id == 0) {
                    throw new ModerationException("Failed to re add <@" + memberID + ">'s nickname permissions! No noNick role set.");
                }
                if (!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("Failed to add back <@" + memberID + ">'s nickname permissions! Missing manage roles permission.");
                }
                role = g.getRoleById(id);
                if (role == null) {
                    throw new ModerationException("Failed to add back <@" + memberID + ">'s nickname permissions! NoNick role was deleted.");
                }

                id = serverData.getMemberRole();
                if (id == 0) {
                    throw new ModerationException("Failed to re add <@" + memberID + ">'s nickname permissions! No member role set.");
                }
                Role role2 = g.getRoleById(id);
                if (role2 == null) {
                    throw new ModerationException("Failed to add back <@" + memberID + ">'s nickname permissions! Member role was deleted.");
                }
                try {
                    g.removeRoleFromMember(member, role).queue();
                } catch (Exception e) {
                    throw new ModerationException("Failed to remove noNick role from <@" + memberID + ">! " + e.getMessage());
                }

                try {
                    g.addRoleToMember(member, role2).queue();
                    return "Added back <@" + memberID + ">'s nickname permissions.";
                } catch (Exception e) {
                    throw new ModerationException("Failed to add member role to <@" + memberID + ">! " + e.getMessage());
                }
            default:
                throw new ModerationException("Encountered an invalid severity whilst trying to unpunish <@" + memberID + ">. Punishment: " + punishment.toString());
        }
    }

    /**
     * Updates the active.data for the specified guild by removing the specified {@link ActivePunishment punishment} from it.
     *
     * @param guildID      The specified {@link Guild guild}'s ID.
     * @param punishmentID The ID of the punishment to remove.
     * @return The {@link ActivePunishment active punishment} that was removed or null if none was found.
     */
    private static synchronized ActivePunishment removeActivePunishment(long guildID, int punishmentID) throws IOException {
        List<ActivePunishment> apList = getActivePunishments(guildID);
        if (apList.isEmpty())
            return null;

        ActivePunishment ap_removed = null;
        Iterator<ActivePunishment> it = apList.iterator();
        while (it.hasNext()) {
            ActivePunishment ap = it.next();
            if (ap.punishment.id == punishmentID) {
                ap_removed = ap;
                it.remove();
                Files.write(Paths.get("moderations/" + guildID + "/active.punishments"), apList.stream().map(ActivePunishment::toString).collect(Collectors.toList()));
                break;
            }
        }
        return ap_removed;
    }

    public static class Punishment {
        public final int id;
        public final char severity;
        public final long date;
        public final int length; //in minutes
        public final String reason;
        public final long punisherID;

        Punishment(int id, char severity, String reason, int length, long punisherID) {
            this(id, severity, reason, length, System.currentTimeMillis(), punisherID);
        }

        private Punishment(int id, char severity, String reason, int length, long date, long punisherID) {
            this.id = id;
            this.severity = severity;
            this.reason = reason;
            this.length = length;
            this.date = date;
            this.punisherID = punisherID;
        }

        public static Punishment fromString(String s) {
            int i = s.indexOf(" ");
            int id = Integer.parseInt(s.substring(0, i));
            s = s.substring(i + 1);
            char sev = s.charAt(0);
            s = s.substring(2);
            i = s.indexOf(" ");
            long date = Long.parseLong(s.substring(0, i));
            s = s.substring(i + 1);
            i = s.indexOf(" ");
            int length = Integer.parseInt(s.substring(0, i));
            s = s.substring(i + 1);
            i = s.indexOf(" ");
            long punisherID = Long.parseLong(s.substring(0, i));
            String reason = StringEscapeUtils.unescapeJava(s.substring(i + 1));

            return new Punishment(id, sev, reason, length, date, punisherID);
        }

        // in mins
        public static int defaultLength(char sev) {
            switch (sev) {
                case '1':
                    return 60;
                case '2':
                    return 120;
                case '3':
                    return 240;
                case '4':
                    return 480;
                case '5':
                    return 1440;
                case '6':
                    return 64800;
                case 'v':
                case 'n':
                    return 10080;
            }
            return 0;
        }

        public static int defaultBonusLength(char sev) {
            switch (sev) {
                case '1':
                    return 45;
                case '2':
                    return 90;
                case '3':
                    return 180;
                case '4':
                    return 360;
                case '5':
                    return 1080;
                case '6':
                    return 65700;
                case 'v':
                case 'n':
                    return 10080;
            }
            return 0;
        }

        public static int specialBonusLength(char sev) {
            switch (sev) {
                case '1':
                    return 105;
                case '2':
                    return 210;
                case '3':
                    return 420;
                case '4':
                    return 840;
                case '5':
                    return 2520;
                case '6':
                case 'v':
                case 'n':
                    return 0;
            }
            return 0;
        }

        public static int specialBonusReqTime(char sev) {
            switch (sev) {
                case '1':
                case '2':
                    return 10080;
                case '3':
                    return 20160;
                case '4':
                    return 50400;
                case '5':
                    return 60480;
                case '6':
                case 'v':
                case 'n':
                    return 0;
            }
            return 0;
        }

        // in milliseconds
        public long getTimeLeft() {
            return (date + (((long) length) * 60000)) - System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.valueOf(id) + ' ' + severity + ' ' + date + ' ' + length + ' ' + punisherID + ' ' + StringEscapeUtils.escapeJava(reason);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Punishment))
                return false;
            Punishment p = (Punishment) o;
            return id == p.id;
        }
    }

    public static class UserPunishment {
        public final long userID;
        public final Punishment p;

        public UserPunishment(long userID, Punishment p) {
            this.p = p;
            this.userID = userID;
        }

        @Override
        public String toString() {
            return String.valueOf(userID) + ' ' + p.toString();
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof UserPunishment)
                return p.id == (((UserPunishment) o).p.id);
            else
                return false;
        }
    }

    public static class ActivePunishment {
        public final long memberID;
        public final Punishment punishment;

        ActivePunishment(long memberID, Punishment punishment) {
            this.memberID = memberID;
            this.punishment = punishment;
        }

        public static ActivePunishment fromString(String s) {
            return new ActivePunishment(Long.parseLong(s.substring(0, s.indexOf(' ') - 1)), Punishment.fromString(s.substring(s.indexOf(' ') + 1))); //":" should be ignored
        }

        @Override
        public String toString() {
            return memberID + ": " + punishment.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ActivePunishment))
                return false;
            ActivePunishment ap = (ActivePunishment) o;

            return memberID == ap.memberID && punishment.equals(ap.punishment);
        }
    }

    public static class PunishmentHandler {
        private static PunishmentHandler punishmentHandler;
        private final ScheduledExecutorService scheduler;
        private final LinkedList<EndPunishment> queued;
        private boolean paused;
        private boolean ranWhilePaused;
        private JDA jda;
        private PunishmentHandler(JDA jda) {
            this.jda = jda;
            paused = false;
            ranWhilePaused = false;
            queued = new LinkedList<>();

            scheduler = Executors.newScheduledThreadPool(0);
        }

        public static PunishmentHandler get() throws NotInitializedException {
            if (punishmentHandler == null)
                throw new NotInitializedException();

            return punishmentHandler;
        }

        public static synchronized void initialize(JDA jda) {
            assert jda != null;
            if (punishmentHandler == null) {
                punishmentHandler = new PunishmentHandler(jda);

                try {
                    List<Long> ids = Files.find(Paths.get("moderations"), 1, (p, bfa) -> bfa.isDirectory() && p.getFileName().toString().matches("\\d+"))
                            .map(p -> Long.parseLong(p.getFileName().toString()))
                            .collect(Collectors.toList());

                    for (long id : ids) {
                        try {
                            Moderation.getActivePunishments(id).forEach((ap) -> punishmentHandler.newPunishment(ap.memberID, id, ap.punishment));
                        } catch (IOException ignored) {
                            System.out.println("Failed to read active punishments for guild with ID " + id);
                        }
                    }
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }

        public static void stop() {
            try {
                get().scheduler.shutdownNow();
            } catch (Exception ignored) {
            }
        }

        public static void pause() {
            try {
                get().paused = true;
            } catch (NotInitializedException ignored) {
            }
        }

        public static void resume(JDA jda) {
            try {
                PunishmentHandler pH = get();
                pH.jda = jda;
                pH.paused = false;
                if (pH.ranWhilePaused) {
                    while (!pH.queued.isEmpty())
                        pH.queued.remove().run();
                }
            } catch (NotInitializedException ignored) {
            }
        }

        public void newPunishment(long memberID, long guildID, Punishment p) {
            long time = p.getTimeLeft();
            if (time < 0)
                runEndPunishment(memberID, guildID, p);
            else {
                scheduler.schedule(
                        () -> {
                            if (paused) {
                                ranWhilePaused = true;
                                queued.add(new EndPunishment(memberID, guildID, p));
                            } else
                                runEndPunishment(memberID, guildID, p);
                        },
                        time,
                        TimeUnit.MILLISECONDS
                );
            }
        }

        private void runEndPunishment(long memberID, long guildID, Punishment p) {
            String response = "";
            Guild guild = jda.getGuildById(guildID);
            try {
                ActivePunishment ap = removeActivePunishment(guildID, p.id);
                if (ap == null)
                    return; //If this is null then it was already unpunished
                if (guild != null) {
                    try {
                        response = endPunishment(guild, memberID, p, false);
                    } catch (ModerationException e) {
                        response = e.getMessage();
                    }
                }
            } catch (IOException e) {
                response = "An IO error occured while updating active.data (<@470696578403794967>)! " + e.getMessage();
            }
            if (guild != null) {
                ServerData serverData = ServerData.get(guildID);
                TextChannel pChannel = guild.getTextChannelById(serverData.getPunishmentChannel());
                if (pChannel == null) {
                    TextChannel lChannel = guild.getTextChannelById(serverData.getLogChannel());
                    if (lChannel != null) {
                        CommandUtils.sendInfo(lChannel, response);
                    }
                } else
                    CommandUtils.sendInfo(pChannel, response);
            }
        }

        public static class NotInitializedException extends Exception {
            public NotInitializedException() {
                super("Please initialize the punishment handler first by running PunishmentHandler.initialize(jda)!");
            }
        }

        private class EndPunishment {
            private final long memberID;
            private final long guildID;
            private final Punishment p;

            public EndPunishment(long memberID, long guildID, Punishment p) {
                this.memberID = memberID;
                this.guildID = guildID;
                this.p = p;
            }

            public void run() {
                runEndPunishment(memberID, guildID, p);
            }
        }
    }

    public static class ModerationException extends Exception {
        public ModerationException(String errorMessage) {
            super(errorMessage);
        }
    }
}
