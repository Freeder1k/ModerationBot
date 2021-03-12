package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import javax.annotation.Nonnull;
import java.util.concurrent.CountDownLatch;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class DelreactionCommand extends Command {
    public DelreactionCommand() {
        super(
                "delreaction",
                new String[]{},
                "!delreaction <emoji> <amount>",
                "Deletes all reactions with a specified emoji <amount> messages back.\n" +
                        "Due to limitations with discord the amount can only have a maximum value of 100.",
                new Permission[]{Permission.MESSAGE_MANAGE},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        String[] args = event.args;
        TextChannel channel = event.channel;

        if (args.length == 1) {
            sendHelpMessage(channel);
            return;
        }

        if (args.length != 3) {
            sendError(channel, "Invalid amount of arguments!");
            return;
        }

        String emoji = args[1];
        if (emoji.charAt(0) == '<') {
            emoji = emoji.substring(1, emoji.length() - 1);
            if (emoji.charAt(0) == 'a')
                emoji = emoji.substring(1);
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendError(channel, "Error parsing amount!");
            return;
        }
        if (amount > 100 || amount < 1) {
            sendError(channel, "Amount must be in range 1-100!");
            return;
        }
        String finalEmoji = emoji;

        channel.getHistory().retrievePast(amount).queue((messages) -> messages.remove(0).clearReactions(finalEmoji).queue((success) -> {
            channel.sendMessage(new EmbedBuilder()
                    .setColor(3901635)
                    .setDescription("ℹ️ Removing reactions with " + finalEmoji + " on " + amount + " messages...")
                    .build()
            ).queue();

            final CountDownLatch latch = new CountDownLatch(messages.size());
            for (Message m : messages) {
                m.clearReactions(finalEmoji).queue((ignored) -> latch.countDown());
            }
            try {
                latch.await();
                sendSuccess(channel, "✅ Removed reactions with " + finalEmoji + " on " + amount + " messages.");
            } catch (InterruptedException e) {
                e.printStackTrace();
                sendException(channel, e);
            }
        }, (failure) -> {
            if (failure instanceof ErrorResponseException) {
                sendError(channel, "Unknown emoji: ``" + finalEmoji + "``!\n If you don't have access to the emoji send it in the format ``:emoji:id``. Example: ``:test:756833424655777842``.");
            } else {
                failure.printStackTrace();
                sendException(channel, failure);
            }
        }));
    }
}
