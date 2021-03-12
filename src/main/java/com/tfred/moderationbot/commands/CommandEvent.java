package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;
import java.util.Objects;

public class CommandEvent {
    public final MessageReceivedEvent event;
    public final String message;
    public final String[] args;
    @Nonnull
    public final Member sender;
    public final TextChannel channel;
    public final Guild guild;

    /**
     * Create a new CommandEvent based on a MessageRecievedEvent. Make sure that event.getMember() doesn't return null.
     *
     * @param event
     *          The event containing information.
     */
    protected CommandEvent(@Nonnull MessageReceivedEvent event) {
        this.event = event;
        this.message = event.getMessage().getContentRaw();
        this.args = message.substring(1).split(" ");
        this.sender = Objects.requireNonNull(event.getMember());
        this.channel = event.getTextChannel();
        this.guild = event.getGuild();
    }
}
