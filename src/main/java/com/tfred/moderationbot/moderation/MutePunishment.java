package com.tfred.moderationbot.moderation;

import com.tfred.moderationbot.ServerData;
import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import org.apache.commons.text.StringEscapeUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MutePunishment extends TimedPunishment {
    public final short severity;

    /**
     * Create a new mute punishment. The punishment ID, date and duration get assigned automatically.
     *
     * @param guildID     The guild ID for this mute.
     * @param userID      The user ID of the muted user.
     * @param moderatorID The user ID of the moderator that muted the user.
     * @param severity    The severity of the muted (1-5).
     * @param reason      The reason for this mute.
     * @throws IOException If an IO exception occurred while checking previous punishments to calculate the mute duration.
     */
    MutePunishment(long guildID, long userID, long moderatorID, short severity, String reason) throws IOException {
        super(guildID, userID, moderatorID, calculatePunishmentLength(guildID, userID, severity), reason);
        this.severity = severity;
    }

    private MutePunishment(long userId, int id, long date, long moderatorID, short severity, int duration, String reason) {
        super(userId, id, date, moderatorID, duration, reason);
        this.severity = severity;
    }

    private static int calculatePunishmentLength(long guildID, long userID, short sev) throws IOException {
        Punishment[] punishments = ModerationData.getUserPunishments(guildID, userID);

        boolean prev = false; // If there is a previous Mute of the same severity
        long endDate = 0;    // time till ^ ends
        int punishmentDuration = 0;     // duration of ^
        Set<Integer> hidden = new HashSet<>(); // Set of punishment ids that were pardoned and marked as hidden.
        Map<Integer, Long> pardoned = new HashMap<>(); // Map of punishment ids that were pardoned and not marked as hidden mapped to the date when pardoned.
        boolean wasPardoned = false;

        for (int i = punishments.length - 1; i >= 0; i--) {
            Punishment p = punishments[i];

            if ((p instanceof MutePunishment) && (((MutePunishment) p).severity == sev)) {
                if (!hidden.contains(p.id)) {
                    prev = true;
                    endDate = p.date + (((long) ((MutePunishment) p).duration) * 60000);
                    punishmentDuration = ((MutePunishment) p).duration;
                    if (pardoned.containsKey(p.id)) {
                        wasPardoned = true;
                        endDate = pardoned.get(p.id);
                    }
                }
                break;
            } else if (p instanceof PardonPunishment) {
                if (((PardonPunishment) p).hide)
                    hidden.add(((PardonPunishment) p).pardonedPunishmentID);
                else
                    pardoned.put(((PardonPunishment) p).pardonedPunishmentID, p.date);
            }
        }

        if (!prev) {
            switch (sev) {
                case 1:
                    return 60;
                case 2:
                    return 120;
                case 3:
                    return 240;
                case 4:
                    return 480;
                case 5:
                    return 1440;
                default:
                    return 0;
            }
        }

        long timeSinceEnded = (System.currentTimeMillis() - endDate); //time since last punishment ended

        if (timeSinceEnded < 0 && !wasPardoned)
            return punishmentDuration;

        if (timeSinceEnded < 172800000L)
            return punishmentDuration * 2;

        long bonusReqTime;
        int bonusAdditionalTime;
        int normalAdditionalTime;
        switch (sev) {
            case 1:
                bonusReqTime = 604800000L;
                bonusAdditionalTime = 105;
                normalAdditionalTime = 45;
                break;
            case 2:
                bonusReqTime = 604800000L;
                bonusAdditionalTime = 210;
                normalAdditionalTime = 90;
                break;
            case 3:
                bonusReqTime = 1209600000L;
                bonusAdditionalTime = 420;
                normalAdditionalTime = 180;
                break;
            case 4:
                bonusReqTime = 3024000000L;
                bonusAdditionalTime = 840;
                normalAdditionalTime = 360;
                break;
            case 5:
                bonusReqTime = 3628800000L;
                bonusAdditionalTime = 2520;
                normalAdditionalTime = 1080;
                break;
            default:
                bonusReqTime = 0L;
                bonusAdditionalTime = 0;
                normalAdditionalTime = 0;
        }

        if (timeSinceEnded < bonusReqTime)
            return punishmentDuration + bonusAdditionalTime;
        else
            return punishmentDuration + normalAdditionalTime;
    }

    /**
     * Parse a userID and a string for a MutePunishment.
     *
     * @param userID The user ID for the punishment.
     * @param string The string to parse.
     *               Format: "m <punishmentID> <date> <moderatorID> <severity> <duration> <reason>"
     * @return A MutePunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static MutePunishment parseMutePunishment(long userID, String string) {
        Pattern p = Pattern.compile("m (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        if (m.groupCount() != 6)
            return null;

        try {
            return new MutePunishment(
                    userID,
                    Integer.parseInt(m.group(1)),
                    Long.parseLong(m.group(2)),
                    Long.parseLong(m.group(3)),
                    Short.parseShort(m.group(4)),
                    Integer.parseInt(m.group(5)),
                    StringEscapeUtils.unescapeJava(m.group(6))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Parse a string for a MutePunishment.
     *
     * @param string The string to parse.
     *               Format: "m <userID> <punishmentID> <date> <moderatorID> <severity> <duration> <reason>"
     * @return A MutePunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static MutePunishment parseMutePunishment(String string) {
        Pattern p = Pattern.compile("m (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        if (m.groupCount() != 7)
            return null;

        try {
            return new MutePunishment(
                    Long.parseLong(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Long.parseLong(m.group(3)),
                    Long.parseLong(m.group(4)),
                    Short.parseShort(m.group(5)),
                    Integer.parseInt(m.group(6)),
                    StringEscapeUtils.unescapeJava(m.group(7))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    protected void log(Guild guild) {
        ServerData serverData = ServerData.get(guild.getIdLong());
        TextChannel punishmentChannel = guild.getTextChannelById(serverData.getPunishmentChannel());
        if (punishmentChannel == null) {
            punishmentChannel = guild.getTextChannelById(serverData.getLogChannel());
            if (punishmentChannel == null)
                return;
        }

        if (!guild.getSelfMember().hasPermission(punishmentChannel, Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS))
            return;

        punishmentChannel.sendMessage(
                new EmbedBuilder()
                        .setTitle("Case " + id)
                        .setColor(CommandUtils.DEFAULT_COLOR)
                        .addField("**User:**", "<@" + userID + ">\n**Type:**\n" + "mute", true)
                        .addField("Severity:", severity + "\n**Duration:**\n" + CommandUtils.parseTime(((long) duration) * 60L), true)
                        .addField("**Moderator:**", "<@" + moderatorID + ">\n**Reason:**\n" + reason, true)
                        .setTimestamp(Instant.now())
                        .build()
        ).queue();
    }

    @Override
    public MessageEmbed getAsCaseEmbed() {
        return new EmbedBuilder()
                .setColor(CommandUtils.DEFAULT_COLOR)
                .setTitle("Case " + id)
                .addField("**User:**", "<@" + userID + ">\n" +
                        "**Type:**\nmute\n" +
                        "**Severity:**\n" + severity, true)
                .addField("**Moderator:**", "<@" + moderatorID + ">\n" +
                        "**Date:**\n" + Instant.ofEpochMilli(date).toString() + "\n" +
                        "**Duration:**\n" + CommandUtils.parseTime(((long) duration) * 60L), true)
                .addField("**Reason:**", reason, true)
                .build();
    }

    @Override
    public int addModlogsEmbedFields(EmbedBuilder embedBuilder, int characterCount) {
        if (embedBuilder.getFields().size() + 3 > 25)
            return 0;

        int partLength = 0;

        String part1 = id + "\n" +
                "**Type:**\nmute\n" +
                "**Severity:**\n" + severity + "\n\u200B";
        partLength += 9 + part1.length();

        String part2 = "<@" + moderatorID + ">\n" +
                "**Date:**\n" + Instant.ofEpochMilli(date).toString() + "\n" +
                "**Duration:**\n" + CommandUtils.parseTime(((long) duration) * 60L) + "\n\u200B";
        partLength += 14 + part2.length();

        partLength += 11 + reason.length() + 7;

        if (characterCount + partLength > 5900)
            return 0;

        embedBuilder
                .addField("**Case:**", part1, true)
                .addField("**Moderator:**", part2, true)
                .addField("**Reason:**", reason + "\n\u200B", true);
        return characterCount + partLength;
    }

    @Override
    public int addModerationsEmbedFields(EmbedBuilder embedBuilder, int characterCount) {
        if (embedBuilder.getFields().size() + 4 > 25)
            return 0;

        int partLength = 0;

        partLength += 9 + String.valueOf(id).length();

        String part1 = "<@" + userID + ">\n" +
                "**Type:**\nmute\n" +
                "**Severity:**\n" + severity;
        partLength += 9 + part1.length();

        String part2 = "<@" + moderatorID + ">\n" +
                "**Date:**\n" + Instant.ofEpochMilli(date).toString() + "\n" +
                "**Time left:**\n" + CommandUtils.parseTime(getTimeLeft() / 1000);
        partLength += 14 + part2.length();

        partLength += 11 + reason.length();

        if (characterCount + partLength > 5900)
            return 0;

        embedBuilder
                .addField("**Case**:", String.valueOf(id), false)
                .addField("**User:**", part1, true)
                .addField("**Moderator:**", part2, true)
                .addField("**Reason:**", reason, true);
        return characterCount + partLength;
    }

    /**
     * Return a string representation of this punishment without user ID.
     *
     * @return A string of format "m <punishmentID> <date> <moderatorID> <severity> <duration> <reason>".
     */
    @Override
    public String toStringWithoutUserID() {
        return "m " + id + ' ' + date + ' ' + moderatorID + ' ' + severity + ' ' + duration + ' ' + StringEscapeUtils.escapeJava(reason);
    }

    /**
     * Return a string representation of this punishment.
     *
     * @return A string of format "m <userID> <punishmentID> <date> <moderatorID> <severity> <duration> <reason>".
     */
    @Override
    public String toString() {
        return "m " + userID + ' ' + id + ' ' + date + ' ' + moderatorID + ' ' + severity + ' ' + duration + ' ' + StringEscapeUtils.escapeJava(reason);
    }

    @Override
    protected String end(Guild guild) throws ModerationException {
        Member member = guild.getMemberById(userID);
        if (member == null) {
            return "Unmuted <@" + userID + ">.\nNote: User not in guild.";
        }
        long id = ServerData.get(guild.getIdLong()).getMutedRole();
        if (id == 0L) {
            throw new ModerationException("Unmute of <@" + userID + "> failed! No muted role set.");
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            throw new ModerationException("Unmute of <@" + userID + "> failed! Missing permissions: MANAGE_ROLES. Please remove the role manually.");
        }
        Role mutedRole = guild.getRoleById(id);
        if (mutedRole == null) {
            throw new ModerationException("Unmute of <@" + userID + "> failed! Muted role doesn't exist anymore.");
        }
        try {
            guild.removeRoleFromMember(member, mutedRole).queue();
            return "Unmuted <@" + userID + ">.";
        } catch (Exception e) {
            throw new ModerationException("Unmute of <@" + userID + "> failed! " + e.getMessage());
        }
    }
}
