package com.tfred.moderationbot.moderation;

import com.tfred.moderationbot.ServerData;
import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
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

public class BanPunishment extends TimedPunishment {
    public final short severity;

    /**
     * Create a new ban punishment. The punishment ID, date and duration get assigned automatically.
     *
     * @param guildID     The guild ID for this ban.
     * @param userID      The user ID of the banned user.
     * @param moderatorID The user ID of the moderator that banned the user.
     * @param severity    The severity of the ban (1 or 2).
     * @param reason      The reason for this ban.
     * @throws IOException If an IO exception occurred while checking previous punishments to calculate the ban duration.
     */
    BanPunishment(long guildID, long userID, long moderatorID, short severity, String reason) throws IOException {
        super(guildID, userID, moderatorID, calculatePunishmentLength(guildID, userID, severity), reason);
        this.severity = severity;
    }

    private BanPunishment(long userId, int id, long date, long moderatorID, short severity, int duration, String reason) {
        super(userId, id, date, moderatorID, duration, reason);
        this.severity = severity;
    }

    private static int calculatePunishmentLength(long guildID, long userID, short sev) throws IOException {
        Punishment[] punishments = ModerationData.getUserPunishments(guildID, userID);

        boolean prev = false; // If there is a previous ban of the same severity
        long endDate = 0;    // time till ^ ends
        int punishmentDuration = 0;     // duration of ^
        Set<Integer> hidden = new HashSet<>(); // Set of punishment ids that were pardoned and marked as hidden.
        Map<Integer, Long> pardoned = new HashMap<>(); // Map of punishment ids that were pardoned and not marked as hidden mapped to the date when pardoned.
        boolean wasPardoned = false;

        for (int i = punishments.length - 1; i >= 0; i--) {
            Punishment p = punishments[i];

            if ((p instanceof BanPunishment) && (((BanPunishment) p).severity == sev)) {
                if (!hidden.contains(p.id)) {
                    prev = true;
                    endDate = p.date + (((long) ((BanPunishment) p).duration) * 60000);
                    punishmentDuration = ((BanPunishment) p).duration;
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
            if (sev == 1)
                return 20160;
            if (sev == 2)
                return 64800;
            else
                return 0;
        }

        long timeSinceEnded = (System.currentTimeMillis() - endDate); //time since last punishment ended

        if (timeSinceEnded < 0 && !wasPardoned)
            return punishmentDuration;
        else if (timeSinceEnded < 172800000L)
            return punishmentDuration * 2;
        else {
            if (sev == 1)
                return punishmentDuration + 20160;
            if (sev == 2)
                return punishmentDuration + 64800;
            else
                return punishmentDuration;
        }
    }

    /**
     * Parse a userID and a string for a BanPunishment.
     *
     * @param userID The user ID for the punishment.
     * @param string The string to parse.
     *               Format: "b <punishmentID> <date> <moderatorID> <severity> <duration> <reason>"
     * @return A BanPunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static BanPunishment parseBanPunishment(long userID, String string) {
        Pattern p = Pattern.compile("b (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        if (m.groupCount() != 6)
            return null;

        try {
            return new BanPunishment(
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
     * Parse a string for a BanPunishment.
     *
     * @param string The string to parse.
     *               Format: "b <userID> <punishmentID> <date> <moderatorID> <severity> <duration> <reason>"
     * @return A BanPunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static BanPunishment parseBanPunishment(String string) {
        Pattern p = Pattern.compile("b (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        if (m.groupCount() != 7)
            return null;

        try {
            return new BanPunishment(
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
                        .addField("**User:**", "<@" + userID + ">\n**Type:**\n" + "ban", true)
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
                        "**Type:**\nban\n" +
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
                "**Type:**\nban\n" +
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
                "**Type:**\nban\n" +
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
     * @return A string of format "b <punishmentID> <date> <moderatorID> <severity> <duration> <reason>".
     */
    @Override
    public String toStringWithoutUserID() {
        return "b " + id + ' ' + date + ' ' + moderatorID + ' ' + severity + ' ' + duration + ' ' + StringEscapeUtils.escapeJava(reason);
    }

    /**
     * Return a string representation of this punishment.
     *
     * @return A string of format "b <userID> <punishmentID> <date> <moderatorID> <severity> <duration> <reason>".
     */
    @Override
    public String toString() {
        return "b " + userID + ' ' + id + ' ' + date + ' ' + moderatorID + ' ' + severity + ' ' + duration + ' ' + StringEscapeUtils.escapeJava(reason);
    }

    @Override
    protected String end(Guild guild) throws ModerationException {
        try {
            guild.unban(String.valueOf(userID)).complete();
            return "Unbanned <@" + userID + ">.";
        } catch (Exception e) {
            throw new ModerationException("Unable to unban <@" + userID + ">. " + e.getMessage());
        }
    }
}
