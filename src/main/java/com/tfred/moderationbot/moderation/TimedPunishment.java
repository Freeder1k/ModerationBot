package com.tfred.moderationbot.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

import java.io.IOException;

public abstract class TimedPunishment extends Punishment {
    /**
     * Length of the punishment in minutes.
     */
    public final int duration;

    protected TimedPunishment(long guildID, long userID, long moderatorID, int duration, String reason) throws IOException {
        super(guildID, userID, moderatorID, reason);
        this.duration = duration;
    }

    protected TimedPunishment(long userId, int id, long date, long moderatorID, int duration, String reason) {
        super(userId, id, date, moderatorID, reason);
        this.duration = duration;
    }

    /**
     * Add the required fields to a moderations embed from the !moderations command.
     * This returns the new characterCount or 0 if the new characterCount would've been too long (over discord limits) or there are too many fields, in which case it doesn't get added.
     *
     * @param embedBuilder   The embedbuilder to add it to.
     * @param characterCount The current amount of characters of the embedbuilder.
     * @return The new amount of characters of the embedbuilder or 0.
     */
    public abstract int addModerationsEmbedFields(EmbedBuilder embedBuilder, int characterCount);

    /**
     * Get the time left until the punishment ends.
     *
     * @return The time in milliseconds.
     */
    public long getTimeLeft() {
        return (date + (((long) duration) * 60000)) - System.currentTimeMillis();
    }

    protected abstract String end(Guild guild) throws ModerationException;
}
