package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class GetreactionsCommand extends Command {
    public GetreactionsCommand() {
        super(
                "getreactions",
                new String[]{},
                "!getreactions <messageID> [channel]",
                "Gets the reactions on a specified message.\n" +
                        "If the message is in another channel the channel has to be specified too.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        if (event.args.length == 1) {
            sendHelpMessage(event.channel);
            return;
        }
        if (event.args.length < 2) {
            sendError(event.channel, "Invalid amount of arguments!");
            return;
        }

        String msgID = event.args[1];

        TextChannel c;
        if (event.args.length > 2)
            c = event.guild.getTextChannelById(parseID(event.args[2]));
        else
            c = event.channel;
        if (c == null) {
            sendError(event.channel, "Couldn't find the specified channel!");
            return;
        }

        LinkedList<Permission> missingPerms = CommandUtils.missingPerms(c, Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY);
        if (!missingPerms.isEmpty()) {
            event.channel.sendMessage(new EmbedBuilder().setColor(ERROR_COLOR)
                    .addField("To use this command please give me the following permissions in <#" + c.getId() + ">:",
                            missingPerms.stream().map(p -> "• " + p.getName()).collect(Collectors.joining("\n")),
                            false)
                    .build()).queue();
        }

        try {
            c.retrieveMessageById(msgID).queue((m) -> {
                List<MessageReaction> reactions = m.getReactions();
                List<String> emojis = new ArrayList<>();
                for (MessageReaction r : reactions) {
                    MessageReaction.ReactionEmote reactionEmote = r.getReactionEmote();
                    String emoji;
                    if (reactionEmote.isEmote())
                        emoji = ":" + reactionEmote.getName() + ":" + reactionEmote.getId();
                    else
                        emoji = reactionEmote.getName();
                    emojis.add(emoji);
                }
                event.channel.sendMessage(new EmbedBuilder().setColor(DEFAULT_COLOR)
                        .setTitle("Reactions:")
                        .setDescription(emojis.stream().map(e -> "• " + e).collect(Collectors.joining("\n")) + "\n\n[Message link](" + m.getJumpUrl() + ")")
                        .build()).queue();
            }, (failure) -> {
                if (failure instanceof ErrorResponseException) {
                    sendError(event.channel, "Couldn't find the specified message!");
                } else {
                    failure.printStackTrace();
                    sendException(event.channel, failure);
                }
            });
        } catch (InsufficientPermissionException e) {
            sendError(event.channel, "Cannot perform action due to lack of permission in " + c.getAsMention() + "! Missing permission: " + e.getPermission().toString());
        }
    }
}
