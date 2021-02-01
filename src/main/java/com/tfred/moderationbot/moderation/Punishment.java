package com.tfred.moderationbot.moderation;

import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;

public abstract class Punishment {
    public final long userID;
    public final int id;
    /**
     * Time in millis when the punishment started.
     */
    public final long date;
    public final long moderatorID;
    public final String reason;

    protected Punishment(long guildID, long userID, long moderatorID, String reason) {
        this(
                userID,
                ServerData.get(guildID).getNextPunishmentID(),
                System.currentTimeMillis(),
                moderatorID,
                reason
        );
    }

    protected Punishment(long userId, int id, long date, long moderatorID, String reason) {
        this.userID = userId;
        this.id = id;
        this.reason = reason;
        this.date = date;
        this.moderatorID = moderatorID;
    }

    /**
     * Parse a string for a Punishment.
     *
     * @param string The string to parse.
     *               To see the formats check the individual subclasses.
     * @return A Punishment. If the format is invalid null is returned.
     */
    protected static Punishment parsePunishment(String string) {
        switch (string.charAt(0)) {
            case 'm':
                return MutePunishment.parseMutePunishment(string);
            case 'b':
                return BanPunishment.parseBanPunishment(string);
            case 'c':
                return ChannelBanPunishment.parseChannelBanPunishment(string);
            case 'n':
                return NamePunishment.parseNamePunishment(string);
            case 'x':
                return PardonPunishment.parsePardonPunishment(string);
            default:
                return OldPunishment.parseOldPunishment(string);
        }
    }

    /**
     * Parse a userID and a string for a Punishment.
     *
     * @param userID The user ID for the punishment.
     * @param string The string to parse.
     *               To see the formats check the individual subclasses.
     * @return A Punishment. If the format is invalid null is returned.
     */
    protected static Punishment parsePunishment(long userID, String string) {
        switch (string.charAt(0)) {
            case 'm':
                return MutePunishment.parseMutePunishment(userID, string);
            case 'b':
                return BanPunishment.parseBanPunishment(userID, string);
            case 'c':
                return ChannelBanPunishment.parseChannelBanPunishment(userID, string);
            case 'n':
                return NamePunishment.parseNamePunishment(userID, string);
            case 'x':
                return PardonPunishment.parsePardonPunishment(userID, string);
            default:
                return OldPunishment.parseOldPunishment(userID, string);
        }
    }

    /**
     * Log this punishment to the guilds punishment channel.
     *
     * @param guild The guild of this punishment.
     */
    protected abstract void log(Guild guild);

    /**
     * Get this punishment represented as an embed used by the !case command.
     */
    public abstract MessageEmbed getAsCaseEmbed();

    /**
     * Add the required fields to a modlogs embed from the !modlogs command.
     * This returns the new characterCount or 0 if the new characterCount would've been too long (over discord limits) or there are too many fields, in which case it doesn't get added.
     *
     * @param embedBuilder   The embedbuilder to add it to.
     * @param characterCount The current amount of characters of the embedbuilder.
     * @return The new amount of characters of the embedbuilder or 0.
     */
    public abstract int addModlogsEmbedFields(EmbedBuilder embedBuilder, int characterCount);

    public abstract String toStringWithoutUserID();

    public abstract String toString();

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Punishment))
            return false;

        return id == ((Punishment) o).id;
    }
}