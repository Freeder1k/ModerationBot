package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ModerationData;
import com.tfred.moderationbot.moderation.ModerationException;
import com.tfred.moderationbot.moderation.ModerationHandler;
import com.tfred.moderationbot.moderation.Punishment;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class PardonCommand extends Command {
    public PardonCommand() {
        super(
                "pardon",
                new String[]{"unpunish", "absolve", "acquit", "exculpate", "exonerate", "vindicate"},
                "!pardon <punishment ID|user> <hide> [reason]",
                "Pardon a user or punishment.\n" +
                        "If a user is specified this pardons all active punishments for this user.\n" +
                        "The hide option can be either ``y`` or ``n`` and specifies if the pardoned punishment(s) should impact the length of future punishments.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        String[] args = event.message.split(" ", 4);
        TextChannel channel = event.channel;
        Guild guild = event.guild;
        Member sender = event.sender;

        if (args.length == 1) {
            sendHelpMessage(channel);
            return;
        }

        if (args.length < 3) {
            sendError(channel, "Insufficient amount of arguments!");
            return;
        }

        if (args[2].length() != 1) {
            if (!(args[2].equals("yes") || args[2].equals("no"))) {
                sendError(channel, "Invalid hide option.");
                return;
            }
        }
        if ("yn".indexOf(args[2].charAt(0)) == -1) {
            sendError(channel, "Invalid hide option.");
            return;
        }
        boolean hide = args[2].charAt(0) == 'y';

        String reason = "None.";
        if (args.length > 3) {
            reason = args[3];
            if (reason.length() > 500) {
                sendError(channel, "Reason can only have a maximum length of 500 characters!");
                return;
            }
        }

        Member member = parseMember(guild, args[1]);
        long userID;
        if (member == null) {
            if (args[1].length() > 10) {
                try {
                    userID = Long.parseLong(args[1]);
                } catch (NumberFormatException ignored) {
                    sendError(channel, "Invalid user or punishment ID.");
                    return;
                }
            } else {
                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    sendError(channel, "Invalid user or punishment ID.");
                    return;
                }
                try {
                    String response = ModerationHandler.pardon(guild, id, reason, sender.getIdLong(), hide, false);

                    sendSuccess(channel, response);

                } catch (ModerationException e) {
                    sendError(channel, e.getMessage());
                }
                return;
            }
        } else
            userID = member.getIdLong();

        List<Punishment> punishments;
        try {
            punishments = Arrays.stream(ModerationData.getActivePunishments(guild.getIdLong())).filter(p -> p.userID == userID).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            sendException(channel, e);
            return;
        }
        if (punishments.isEmpty()) {
            sendError(channel, "No active punishments found for <@" + userID + ">.");
            return;
        }
        StringJoiner responses = new StringJoiner("\n");
        for (Punishment p : punishments) {
            try {
                String response = ModerationHandler.pardon(guild, p.id, reason, sender.getIdLong(), hide, true);
                responses.add("✅ " + response);
            } catch (ModerationException e) {
                responses.add("❌ " + e.getMessage());
            }
        }

        channel.sendMessage(new EmbedBuilder().setDescription(responses.toString()).setColor(DEFAULT_COLOR).build()).queue();
    }
}
