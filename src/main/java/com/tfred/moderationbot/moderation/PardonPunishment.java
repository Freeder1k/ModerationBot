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
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PardonPunishment extends Punishment {
    public final boolean hide;
    public final int pardonedPunishmentID;
    public final char pardonedPunishmentType;

    /**
     * Create a new pardon. The punishment ID and date get assigned automatically.
     *
     * @param guildID     The guild ID for this channel ban.
     * @param moderatorID The user ID of the moderator that channel banned the user.
     * @param hide        Whether to hide the pardoned punishment from future punishments.
     * @param punishment  The punishment to pardon.
     * @param reason      The reason for this channel ban.
     */
    PardonPunishment(long guildID, long moderatorID, boolean hide, TimedPunishment punishment, String reason) {
        super(guildID, punishment.userID, moderatorID, reason);
        this.hide = hide;
        this.pardonedPunishmentID = punishment.id;
        this.pardonedPunishmentType = punishment.toString().charAt(0);
    }

    protected PardonPunishment(long userID, int id, long date, long moderatorID, boolean hide, int pardonedPunishmentID, char pardonedPunishmentType, String reason) {
        super(userID, id, date, moderatorID, reason);
        this.hide = hide;
        this.pardonedPunishmentID = pardonedPunishmentID;
        this.pardonedPunishmentType = pardonedPunishmentType;
    }

    /**
     * Parse a userID and a string for a PardonPunishment.
     *
     * @param userID The user ID for the pardon.
     * @param string The string to parse.
     *               Format: "x <punishmentID> <date> <moderatorID> <hide> <pardonedID> <reason>"
     * @return A PardonPunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static PardonPunishment parsePardonPunishment(long userID, String string) {
        Pattern p = Pattern.compile("x (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (.) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        try {
            return new PardonPunishment(
                    userID,
                    Integer.parseInt(m.group(1)),
                    Long.parseLong(m.group(2)),
                    Long.parseLong(m.group(3)),
                    m.group(4).equals("1"),
                    Integer.parseInt(m.group(5)),
                    m.group(6).charAt(0),
                    StringEscapeUtils.unescapeJava(m.group(7))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Parse a string for a PardonPunishment
     *
     * @param string The string to parse..
     *               Format: "x <userID> <punishmentID> <date> <moderatorID> <hide> <pardonedID> <reason>"
     * @return A PardonPunishment. If the format is invalid null is returned.
     */
    @Nullable
    public static PardonPunishment parsePardonPunishment(String string) {
        Pattern p = Pattern.compile("x (\\d+) (\\d+) (\\d+) (\\d+) (\\d) (\\d+) (.) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        try {
            return new PardonPunishment(
                    Long.parseLong(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Long.parseLong(m.group(3)),
                    Long.parseLong(m.group(4)),
                    m.group(5).equals("1"),
                    Integer.parseInt(m.group(6)),
                    m.group(7).charAt(0),
                    StringEscapeUtils.unescapeJava(m.group(8))
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    //TODO type
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
                        .addField("**User:**", "<@" + userID + ">\n**Type:**\n" + "pardon", true)
                        .addField("**Effected pID:**", pardonedPunishmentID + "\n**Hide:**\n" + (hide ? "yes" : "no"), true)
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
                        "**Type:**\npardon\n" +
                        "**Hide:**\n" + (hide ? "yes" : "no"), true)
                .addField("**Moderator:**", "<@" + moderatorID + ">\n" +
                        "**Pardoned punishment ID:**\n" + pardonedPunishmentID +
                        "**Date:**\n" + Instant.ofEpochMilli(date).toString() + "\n", true)
                .addField("**Reason:**", reason, true)
                .build();
    }

    @Override
    public int addModlogsEmbedFields(EmbedBuilder embedBuilder, int characterCount) {
        if (embedBuilder.getFields().size() + 3 > 25)
            return 0;

        int partLength = 0;

        String part1 = id + "\n" +
                "**Type:**\npardon\n" +
                "**Hide:**\n" + (hide ? "yes" : "no") + "\n\u200B";
        partLength += 9 + part1.length();

        String part2 = "<@" + moderatorID + ">\n" +
                "**Pardoned punishment ID:**\n" + pardonedPunishmentID + "\n" +
                "**Date:**\n" + Instant.ofEpochMilli(date).toString() + "\n\u200B";
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

    /**
     * Return a string representation of this punishment without user ID.
     *
     * @return A string of format "x <punishmentID> <date> <moderatorID> <hide> <pardonedID> <reason>".
     */
    @Override
    public String toStringWithoutUserID() {
        return "x " + id + ' ' + date + ' ' + moderatorID + ' ' + (hide ? 1 : 0) + ' ' + pardonedPunishmentID + ' ' + pardonedPunishmentType + ' ' + StringEscapeUtils.escapeJava(reason);
    }

    /**
     * Return a string representation of this punishment.
     *
     * @return A string of format "x <userID> <punishmentID> <date> <moderatorID> <hide> <pardonedID> <reason>".
     */
    @Override
    public String toString() {
        return "x " + userID + ' ' + id + ' ' + date + ' ' + moderatorID + ' ' + (hide ? 1 : 0) + ' ' + pardonedPunishmentID + ' ' + pardonedPunishmentType + ' ' + StringEscapeUtils.escapeJava(reason);
    }
}
