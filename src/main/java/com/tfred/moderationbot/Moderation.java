package com.tfred.moderationbot;

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

//Note: this isn't meant to be accessed often. To reduce memory usage unlike other classes like userdata the data isn't stored in memory for long times.
public class Moderation {
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

        public long getTimeLeft() {
            return (date + (((long) length) * 60000)) - System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.valueOf(id) + ' ' + severity + ' ' + date + ' ' + length + ' ' + punisherID + ' ' + StringEscapeUtils.escapeJava(reason);
        }

        @Override
        public boolean equals(Object o) {
            if(!(o instanceof Punishment))
                return false;
            Punishment p = (Punishment) o;
            return id == p.id;
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
            s = s.substring(i + 1);

            return new Punishment(id, sev, s, length, date, punisherID);
        }

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
    }

    public static class ActivePunishment {
        public final String memberID;
        public final Punishment punishment;

        ActivePunishment(String memberID, Punishment punishment) {
            this.memberID = memberID;
            this.punishment = punishment;
        }

        public static ActivePunishment fromString(String s) {
            return new ActivePunishment(s.substring(0, s.indexOf(' ') - 1), Punishment.fromString(s.substring(s.indexOf(' ') + 1))); //":" has to be ignored
        }

        @Override
        public String toString() {
            return memberID + ": " + punishment.toString();
        }

        @Override
        public boolean equals(Object o) {
            if(!(o instanceof ActivePunishment))
                return false;
            ActivePunishment p = (ActivePunishment) o;

            if(memberID.equals(p.memberID))
                return punishment.equals(p.punishment);

            return false;
        }
    }

    public static class PunishmentHandler {
        private class EndPunishment {
            private final String memberID;
            private final String guildID;
            private final Punishment p;

            public EndPunishment(String memberID, String guildID, Punishment p) {
                this.memberID = memberID;
                this.guildID = guildID;
                this.p = p;
            }

            public void run() {
                runEndPunishment(memberID, guildID, p);
            }
        }

        private JDA jda;
        private final ScheduledExecutorService scheduler;
        private ServerData serverData;
        private boolean paused;
        private boolean ranWhilePaused;
        private final List<EndPunishment> queued;

        PunishmentHandler(JDA jda, ServerData serverData) {
            this.jda = jda;
            this.serverData = serverData;
            paused = false;
            ranWhilePaused = false;
            queued = new ArrayList<>();

            scheduler = Executors.newScheduledThreadPool(1);
        }

        public void newPunishment(String memberID, String guildID, Punishment p) {
            long time = p.getTimeLeft();
            if(time < 0)
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

        private void runEndPunishment(String memberID, String guildID, Punishment p) {
            String response = "";
            Guild guild = jda.getGuildById(guildID);
            try {
                ActivePunishment ap = removeActivePunishment(guildID, p.id);
                if(ap == null)
                    return; //If this is null then it was already unpunished
                if (guild != null) {
                    try {
                        response = endPunishment(guild, memberID, p, serverData, false);
                    } catch (ModerationException e) {
                        response = e.getMessage();
                    }
                }
            } catch (IOException e) {
                response = "An IO error occured while updating active.data (<@470696578403794967>)! " + e.getMessage();
            }
            if (guild != null) {
                TextChannel pChannel = guild.getTextChannelById(serverData.getPunishmentChannelID(guildID));
                if (pChannel == null) {
                    TextChannel lChannel = guild.getTextChannelById(serverData.getLogChannelID(guildID));
                    if (lChannel != null) {
                        Commands.sendInfo(lChannel, response);//TODO maybe differentiate between fail and success
                    }
                } else
                    Commands.sendInfo(pChannel, response);
            }
        }

        public void stop() {
            try {
                scheduler.shutdown();
            } catch (Exception ignored) {}
        }

        public void pause() {
            paused = true;
        }

        public void resume(JDA jda, ServerData serverData) {
            this.jda = jda;
            this.serverData = serverData;
            paused = false;
            if(ranWhilePaused) {
                queued.forEach(EndPunishment::run);
                queued.clear();
            }
        }
    }

    public static class ModerationException extends Exception {
        public ModerationException(String errorMessage) {
            super(errorMessage);
        }
    }

    /**
     * Get a list containing the active punishments for a guild.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          A {@link LinkedList<ActivePunishment> list} of all active {@link Punishment punishments} in the specified guild.
     */
    public static LinkedList<ActivePunishment> getActivePunishments(String guildID) throws IOException {
        if(Files.exists(Paths.get("moderations/" + guildID + "/active.data"))) {
            return Files.readAllLines(Paths.get("moderations/" + guildID + "/active.data")).stream().map(ActivePunishment::fromString).collect(Collectors.toCollection(LinkedList::new));
        }
        else
            return new LinkedList<>();
    }

    /**
     * Get a list containing the punishment history for a user in a specified guild.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID
     *          The specified {@link net.dv8tion.jda.api.entities.User user's} ID.
     * @return
     *          A {@link List<Punishment> list} of all past {@link Punishment punishments} for that user in the specified guild.
     */
    public static LinkedList<Punishment> getUserPunishments(String guildID, String userID) throws IOException {
        if(Files.exists(Paths.get("moderations/" + guildID + "/" + userID + ".data")))
            return Files.readAllLines(Paths.get("moderations/" + guildID + "/" + userID + ".data")).stream().map(Punishment::fromString).collect(Collectors.toCollection(LinkedList::new));
        else
            return new LinkedList<>();
    }

    /**
     * Get the length of the next punishment of a specified severity for a user.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID
     *          The specified {@link net.dv8tion.jda.api.entities.User user's} ID.
     * @param sev
     *          The severity of the punishment (1-6 or v or n).
     * @return
     *          The length of the next punishment (in minutes).
     */
    private static int getPunishmentLength(String guildID, String userID, char sev) throws IOException {
        List<Punishment> punishments = getUserPunishments(guildID, userID);

        if (punishments.isEmpty()) {
            return Punishment.defaultLength(sev);
        }
        else {
            int last = -1; //Index of last punishment of same severity
            long end_date = 0; //end date of ^
            int p_length = 0;  //duration of ^
            List<Integer> hidden = new LinkedList<>(); //List of punishment ids that were pardoned and marked as hidden
            for(int i = punishments.size() - 1; i >= 0; i--) {
                Punishment p = punishments.get(i);
                if (p.severity == sev) {
                    if(!hidden.contains(p.id)) {
                        last = i;
                        end_date = p.date + (((long) p.length) * 60000);
                        p_length = p.length;
                        break;
                    }
                }
                if (p.severity == 'u') {
                    if(p.reason.charAt(0) == 'y') {
                        String s = p.reason.substring(2);
                        s = s.substring(0, s.indexOf(' '));
                        hidden.add(Integer.parseInt(s));
                    }
                }
            }
            if (last == -1) {
                return Punishment.defaultLength(sev);
            }
            long end_time = (System.currentTimeMillis() - end_date); //time since last punishment ended
            if (end_time < 0)
                return p_length;
            else if (end_time < (((long) Punishment.specialBonusReqTime(sev)) * 60000))
                return p_length + Punishment.specialBonusLength(sev);
            else
                return p_length + Punishment.defaultBonusLength(sev);
        }
    }

    /**
     * Write a punishment to the appropriate userID.data and active.data files.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID
     *          The specified {@link net.dv8tion.jda.api.entities.User user's} ID.
     * @param punishment
     *          The punishment to write.
     * @throws IOException
     *          If some IO error while creating or writing to the file occurs.
     */
    private static void addPunishment(String guildID, String userID, Punishment punishment) throws IOException {
        if(!Files.exists(Paths.get("moderations/" + guildID + "/" + userID + ".data"))) {
            if(!Files.exists(Paths.get("moderations/" + guildID)))
                Files.createDirectories(Paths.get("moderations/" + guildID));
            Files.createFile(Paths.get("moderations/" + guildID + "/" + userID + ".data"));
        }
        Files.write(Paths.get("moderations/" + guildID + "/" + userID + ".data"), (punishment.toString() + '\n').getBytes(), StandardOpenOption.APPEND);

        if(!Files.exists(Paths.get("moderations/" + guildID + "/active.data")))
            Files.createFile(Paths.get("moderations/" + guildID + "/active.data"));
        try {
            Files.write(Paths.get("moderations/" + guildID + "/active.data"), (userID + ": " + punishment.toString() + '\n').getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            //Delete the last entry from the file above since that one must've worked.
            List<String> lines = Files.readAllLines(Paths.get("moderations/" + guildID + "/" + userID + ".data"));
            lines.remove(lines.size()-1);
            Files.write(Paths.get("moderations/" + guildID + "/" + userID + ".data"), lines);

            throw new IOException(e);
        }
    }

    /**
     * Punish a user. Allowed severities are numbers 1-6 or v for a vent channel ban or n to block them from changing their nickname.
     *
     * @param member
     *          The member to punish.
     * @param severity
     *          The severity.
     * @param reason
     *          The specified reason. If there is none please specify "None." as reason. Empty strings might lead to errors later on.
     * @param punisherID
     *          The member ID of the punisher.
     * @param serverData
     *          The serverdata to read config information from.
     * @param punishmentHandler
     *          The punishment handler that handles the unpunish scheduling.
     * @return
     *          The punishment.
     * @throws ModerationException
     *          A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static Punishment punish(Member member, char severity, String reason, long punisherID, ServerData serverData, PunishmentHandler punishmentHandler) throws ModerationException {
        Guild g = member.getGuild();

        String id;
        TextChannel channel = null;
        Role role = null;
        Role role2 = null;

        switch(severity) {
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                id = serverData.getMutedRoleID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Please set a muted role with ``!config mutedrole <@role>``!");
                }
                if(!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("The bot is missing the manage roles permission!");
                }
                role = g.getRoleById(id);
                if(role == null) {
                    throw new ModerationException("Please set a new muted role with ``!config mutedrole <@role>``!");
                }
                break;
            case '6':
                if(!g.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
                    throw new ModerationException("The bot is missing the ban members permission!");
                }
                break;
            case 'v':
                id = serverData.getVentChannelID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Please set a vent channel with ``!config ventchannel <#channel>``!");
                }
                channel = g.getTextChannelById(id);
                if(channel == null) {
                    throw new ModerationException("Vent channel was deleted! Please set a new vent channel with ``!config ventchannel <#channel>``!");
                }
                if(!g.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
                    throw new ModerationException("The bot is missing the manage permissions permission in " + channel.getAsMention() + "!");
                }
                break;
            case 'n':
                id = serverData.getNoNickRoleID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Please set a no nickname role with ``!config nonickrole <@role>``!");
                }
                if(!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("The bot is missing the manage roles permission!");
                }
                role = g.getRoleById(id);
                if(role == null) {
                    throw new ModerationException("noNickname role was deleted! Please set a no nickname role with ``!config nonickrole <@role>``!");
                }
                id = serverData.getMemberRoleID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Please set a member role with ``!config memberrole <@role>``!");
                }
                role2 = g.getRoleById(id);
                if(role2 == null) {
                    throw new ModerationException("Member role was deleted! Please set a no nickname role with ``!config memberrole <@role>``!");
                }
                break;
            default:
                throw new ModerationException("Invalid severity: " + severity);
        }

        Punishment p;
        try {
            int nextID = serverData.nextPunishmentID(g.getId());
            p = new Punishment(nextID, severity, reason, getPunishmentLength(g.getId(), member.getId(), severity), punisherID);
        } catch (IOException ignored) {
            throw new ModerationException("An internal error occurred while calculating punishment length! Please try again in a bit.");
        }

        try {
            addPunishment(g.getId(), member.getId(), p);
        } catch (IOException e) {e.printStackTrace();
            throw new ModerationException("An internal error occurred while logging punishment! Please try again in a bit.");
        }

        switch(severity) {
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                try {
                    g.addRoleToMember(member, role).queue();
                    punishmentHandler.newPunishment(member.getId(), g.getId(), p);
                    return p;
                } catch (Exception e) {
                    throw new ModerationException("Unable to mute <@" + member.getId() + "! " + e.getMessage());
                }
            case '6':
                try {
                    g.ban(member, 0, reason).queue();
                    punishmentHandler.newPunishment(member.getId(), g.getId(), p);
                    return p;
                } catch (Exception e) {
                    throw new ModerationException("Unable to ban <@" + member.getId() + ">! " + e.getMessage());
                }
            case 'v':
                try {
                    channel.putPermissionOverride(member).setDeny(Permission.VIEW_CHANNEL).queue();
                    punishmentHandler.newPunishment(member.getId(), g.getId(), p);
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
                    punishmentHandler.newPunishment(member.getId(), g.getId(), p);
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
     * @param guild
     *          The guild this is in.
     * @param punishmentID
     *          The ID of the punishment to stop.
     * @param reason
     *          The reason for this punishment.
     * @param unpunisherID
     *          The ID of the unpunisher.
     * @param hide
     *          Whether the pardoned punishment should influence the length of future punishments. It will still show up in mod logs.
     * @param serverData
     *          The serverdata to read config data from.
     * @param stopAll
     *          If other active punishments of similar similarity should be ignored while unpunishing. This is mostly to save processing time when pardoning all punishments of a user.
     * @return
     *          The response message. Similar to the one from punish but with unpunish messages.
     * @throws ModerationException
     *          A {@link ModerationException ModerationException} if something went wrong. The error message contains all necessary information.
     */
    public static String stopPunishment(Guild guild, int punishmentID, String reason, long unpunisherID, boolean hide, ServerData serverData, boolean stopAll) throws ModerationException {
        ActivePunishment ap;
        try {
            ap = removeActivePunishment(guild.getId(), punishmentID);
        } catch (IOException e) {
            throw new ModerationException("An IO error occured while updating active.data (<@470696578403794967>)! " + e.getMessage());
        }
        if(ap == null)
            throw new ModerationException("No matching active punishment with id " + punishmentID + "found.");

        String memberID = ap.memberID;

        String hideS = hide? "y ": "n ";
        int nextID = serverData.nextPunishmentID(guild.getId());
        //The extra data for the unpunishment gets stored in the reason
        Punishment p = new Punishment(nextID, 'u', hideS + punishmentID + ' ' + ap.punishment.severity + ' ' + reason, 0, unpunisherID);
        try {
            Files.write(Paths.get("moderations/" + guild.getId() + "/" + memberID + ".data"), (p.toString() + '\n').getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new ModerationException("An IO error occurred while logging the unpunishment (<@470696578403794967>)! " + e.getMessage());
        }

        return p.id + " " + endPunishment(guild, memberID, ap.punishment, serverData, stopAll);
    }

    private static String endPunishment(Guild g, String memberID, Punishment punishment, ServerData serverData, boolean stopAll) throws ModerationException {
        //check if user has other active punishments
        if(!stopAll) {
            try {
                List<ActivePunishment> activePunishments = getActivePunishments(g.getId());
                if (!activePunishments.isEmpty())
                    for (ActivePunishment ap : activePunishments) {
                        if (ap.memberID.equals(memberID)) {
                            if (("12345".indexOf(punishment.severity) != -1) && ("12345".indexOf(ap.punishment.severity) != -1)) {
                                throw new ModerationException("<@" + memberID + "> still has other active punishments of similar severity.");
                            }
                            if (punishment.severity == ap.punishment.severity) {
                                throw new ModerationException("<@" + memberID + "> still has other active punishments of similar severity.");
                            }
                        }
                    }
            } catch (IOException ignored) {
                throw new ModerationException("An IO error occurred while checking for other active punishments! User won't be unpunished. Punishment: " + punishment.toString());
            }
        }

        Member member;
        String id;
        TextChannel channel;
        Role role;

        switch(punishment.severity) {
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                member = g.getMemberById(memberID);
                if(member == null) {
                    return "Unmuted <@" + memberID + ">.\nNote: User not in guild.";
                }
                id = serverData.getMutedRoleID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Unmute of <@" + memberID + "> failed! No muted role set.");
                }
                if(!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("Unmute of <@" + memberID + "> failed! Missing permissions: MANAGE_ROLES. Please remove the role manually.");
                }
                role = g.getRoleById(id);
                if(role == null) {
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
                    g.unban(memberID).complete();
                    return "Unbanned <@" + memberID + ">.";
                } catch (Exception e) {
                    throw new ModerationException("Unable to unban <@" + memberID + ">. " + e.getMessage());
                }
            case 'v':
                member = g.getMemberById(memberID);
                if(member == null) {
                    return "Removed <@" + memberID + ">'s ban from the vent channel.\nNote: User not in guild.";
                }
                id = serverData.getVentChannelID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from the vent channel! No vent channel set.");
                }
                channel = g.getTextChannelById(id);
                if(channel == null) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from the vent channel! Vent channel doesn't exist anymore.");
                }
                if(!g.getSelfMember().hasPermission(channel, Permission.MANAGE_PERMISSIONS)) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from <#" + channel.getId() + ">! The bot is missing the manage permissions permission in this channel!");
                }
                try {
                    PermissionOverride perms = channel.getPermissionOverride(member);
                    if(perms != null)
                        perms.delete().queue();
                    return "Removed <@" + memberID + ">'s ban from the vent channel.";
                } catch (Exception e) {
                    throw new ModerationException("Failed to remove <@" + memberID + ">'s ban from <#" + channel.getId() + ">! " + e.getMessage());
                }
            case 'n':
                member = g.getMemberById(memberID);
                if(member == null) {
                    return "Removed <@" + memberID + ">'s nickname change ban.\nNote: User not in guild.";
                }
                id = serverData.getNoNickRoleID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Failed to re add <@" + memberID + ">'s nickname permissions! No noNick role set.");
                }
                if(!g.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    throw new ModerationException("Failed to add back <@" + memberID + ">'s nickname permissions! Missing manage roles permission.");
                }
                role = g.getRoleById(id);
                if(role == null) {
                    throw new ModerationException("Failed to add back <@" + memberID + ">'s nickname permissions! NoNick role was deleted.");
                }

                id = serverData.getMemberRoleID(g.getId());
                if(id.equals("0")) {
                    throw new ModerationException("Failed to re add <@" + memberID + ">'s nickname permissions! No member role set.");
                }
                Role role2 = g.getRoleById(id);
                if(role2 == null) {
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
     * @param guildID
     *          The specified {@link Guild guild}'s ID.
     * @param punishmentID
     *          The ID of the punishment to remove.
     * @return
     *          The {@link ActivePunishment active punishment} that was removed or null if none was found.
     */
    private static ActivePunishment removeActivePunishment(String guildID, int punishmentID) throws IOException {
        List<ActivePunishment> apList = getActivePunishments(guildID);
        if(apList.isEmpty())
            return null;

        ActivePunishment ap_removed = null;
        Iterator<ActivePunishment> it = apList.iterator();
        while(it.hasNext())
        {
            ActivePunishment ap = it.next();
            if(ap.punishment.id == punishmentID) {
                ap_removed = ap;
                it.remove();
                Files.write(Paths.get("moderations/" + guildID + "/active.data"), apList.stream().map(ActivePunishment::toString).collect(Collectors.toList()));
                break;
            }
        }
        return ap_removed;
    }
}
