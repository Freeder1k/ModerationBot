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

public class NamePunishment extends TimedPunishment {
    /**
     * Create a new name punishment. The punishment ID, date and duration get assigned automatically.
     *
     * @param guildID     The guild ID for this channel ban.
     * @param userID      The user ID of the channel banned user.
     * @param moderatorID The user ID of the moderator that channel banned the user.
     * @param reason      The reason for this channel ban.
     * @throws IOException If an IO exception occurred while checking previous punishments to calculate the channel ban duration.
     */
    NamePunishment(long guildID, long userID, long moderatorID, String reason) throws IOException {
        super(guildID, userID, moderatorID, calculatePunishmentLength(guildID, userID), reason);
    }

    protected NamePunishment(long userId, int id, long date, long moderatorID, int duration, String reason) {
        super(userId, id, date, moderatorID, duration, reason);
    }

    private static int calculatePunishmentLength(long guildID, long userID) throws IOException {
        Punishment[] punishments = ModerationData.getUserPunishments(guildID, userID);

        boolean prev = false; // If there is a previous name punishment of the same severity
        long endDate = 0;    // time till ^ ends
        int punishmentDuration = 0;     // duration of ^
        Set<Integer> hidden = new HashSet<>(); // Set of punishment ids that were pardoned and marked as hidden.
        Map<Integer, Long> pardoned = new HashMap<>(); // Map of punishment ids that were pardoned and not marked as hidden mapped to the date when pardoned.
        boolean wasPardoned = false;

        for (int i = punishments.length - 1; i >= 0; i--) {
            Punishment p = punishments[i];

            if (p instanceof NamePunishment) {
                if (!hidden.contains(p.id)) {
                    prev = true;
                    endDate = p.date + (((long) ((NamePunishment) p).duration) * 60000);
                    punishmentDuration = ((NamePunishment) p).duration;
                    if (pardoned.containsKey(p.id)) {
                        wasPardoned = true;
                        endDate = pardoned.get(p.id);
                    }
                    break;
                }
            } else if ((p instanceof PardonPunishment) && (((PardonPunishment) p).pardonedPunishmentType == 'n')) {
                if (((PardonPunishment) p).hide)
                    hidden.add(((PardonPunishment) p).pardonedPunishmentID);
                else
                    pardoned.put(((PardonPunishment) p).pardonedPunishmentID, p.date);
            }
        }

        if (!prev) {
            return 10080;
        }

        long timeSinceEnded = (System.currentTimeMillis() - endDate); //time since last punishment ended

        if (timeSinceEnded < 0 && !wasPardoned)
            return punishmentDuration;
        else if (timeSinceEnded < 172800000L)
            return punishmentDuration * 2;
        else {
            return punishmentDuration + 10080;
        }
    }

    /**
     * Parse a userID and a string for a NamePunishment.
     *
     * @param userID The user ID for the punishment.
     * @param string The string to parse.
     *               Format: "n <punishmentID> <date> <moderatorID> <duration> <reason>"
     * @return A NamePunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static NamePunishment parseNamePunishment(long userID, String string) {
        Pattern p = Pattern.compile("n (\\d+) (\\d+) (\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        try {
            return new NamePunishment(
                    userID,
                    Integer.parseInt(m.group(1)),
                    Long.parseLong(m.group(2)),
                    Long.parseLong(m.group(3)),
                    Integer.parseInt(m.group(4)),
                    StringEscapeUtils.unescapeJava(m.group(5))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Parse a string for a NamePunishment.
     *
     * @param string The string to parse.
     *               Format: "n <userID> <punishmentID> <date> <moderatorID> <duration> <reason>"
     * @return A NamePunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static NamePunishment parseNamePunishment(String string) {
        Pattern p = Pattern.compile("n (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        try {
            return new NamePunishment(
                    Long.parseLong(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Long.parseLong(m.group(3)),
                    Long.parseLong(m.group(4)),
                    Integer.parseInt(m.group(5)),
                    StringEscapeUtils.unescapeJava(m.group(6))
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
                        .setTitle("**Case " + id + "**")
                        .setColor(CommandUtils.DEFAULT_COLOR)
                        .addField("**User:**", "<@" + userID + ">\n**Type:**\n" + "removed\nnickname\nperms", true)
                        .addField("**Duration:**", CommandUtils.parseTime(((long) duration) * 60L) + "\n**Moderator:**\n<@" + moderatorID + ">", true)
                        .addField("**Reason:**", reason, true)
                        .setTimestamp(Instant.now())
                        .build()
        ).queue();
    }

    @Override
    public MessageEmbed getAsCaseEmbed() {
        return new EmbedBuilder()
                .setColor(CommandUtils.DEFAULT_COLOR)
                .setTitle("**Case " + id + "**")
                .addField("**User:**", "<@" + userID + ">\n" +
                        "**Type:**\nremoved\nnickname\nperms", true)
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
                "**Type:**\nremoved\nnickname\nperms\n\u200B";
        partLength += 9 + part1.length();

        String part2 = "<@" + moderatorID + ">\n" +
                "**Date:**\n" + Instant.ofEpochMilli(date).toString() + "\n" +
                "**Duration:**\n" + CommandUtils.parseTime(((long) duration) * 60L) + "\n\u200B";
        partLength += 14 + part2.length();

        partLength += 11 + reason.length() + 1;

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
                "**Type:**\nremoved\nnickname\nperms";
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
     * @return A string of format "n <punishmentID> <date> <moderatorID> <duration> <reason>".
     */
    @Override
    public String toStringWithoutUserID() {
        return "n " + id + ' ' + date + ' ' + moderatorID + ' ' + duration + ' ' + StringEscapeUtils.escapeJava(reason);
    }

    /**
     * Return a string representation of this punishment.
     *
     * @return A string of format "n <userID> <punishmentID> <date> <moderatorID> <duration> <reason>".
     */
    @Override
    public String toString() {
        return "n " + userID + ' ' + id + ' ' + date + ' ' + moderatorID + ' ' + duration + ' ' + StringEscapeUtils.escapeJava(reason);
    }

    @Override
    protected String end(Guild guild) throws ModerationException {
        Member member = guild.getMemberById(userID);
        if (member == null) {
            return "Added back <@" + userID + ">'s nickname perms.\nNote: User not in guild.";
        }
        Role nonickrole = guild.getRoleById(ServerData.get(guild.getIdLong()).getNoNicknameRole());
        if (nonickrole == null) {
            throw new ModerationException("Failed to add back <@" + userID + ">'s nickname perms! No nickname role not set.");
        }
        Role memberrole = guild.getRoleById(ServerData.get(guild.getIdLong()).getMemberRole());
        if (memberrole == null) {
            throw new ModerationException("Failed to add back <@" + userID + ">'s nickname perms! Member role not set.");
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            throw new ModerationException("Failed to add back <@" + userID + ">'s nickname perms! The bot is missing the manage roles permission!");
        }
        try {
            guild.addRoleToMember(member, memberrole).queue();
            guild.removeRoleFromMember(member, nonickrole).queue();
            return "Added back <@" + userID + ">'s nickname perms.";
        } catch (Exception e) {
            throw new ModerationException("Failed to add back <@" + userID + ">'s nickname perms! " + e.getMessage());
        }
    }
}
