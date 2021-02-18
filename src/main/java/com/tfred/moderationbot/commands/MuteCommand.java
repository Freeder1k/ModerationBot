package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ModerationException;
import com.tfred.moderationbot.moderation.ModerationHandler;
import com.tfred.moderationbot.moderation.MutePunishment;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class MuteCommand extends Command {
    public MuteCommand() {
        super(
                "mute",
                new String[]{},
                "!mute <user> <severity> reason",
                "Mute a user. Allowed severities: 1-5.\n" +
                        "For info on punishment lengths run !punishmentinfo\n" +
                        "Requires a muted role to be set.",
                new Permission[]{Permission.MANAGE_ROLES},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        String[] args = event.message.split(" ", 4);
        TextChannel channel = event.channel;
        Guild guild = event.guild;

        if (args.length == 1) {
            sendHelpMessage(channel);
            return;
        }

        if (args.length < 3) {
            sendError(channel, "Insufficient amount of arguments!");
            return;
        }

        Member member = parseMember(guild, args[1]);
        if (member == null) {
            sendError(channel, "Invalid user.");
            return;
        }
        if (allowedUser(member)) {
            sendError(channel, "This user is a server moderator!");
            return;
        }

        if (args[2].length() != 1) {
            sendError(channel, "Invalid severity type.");
            return;
        }
        char sev = args[2].charAt(0);
        if ("12345".indexOf(sev) == -1) {
            sendError(channel, "Invalid severity type.");
            return;
        }
        short severity = Short.parseShort(String.valueOf(sev));

        String reason = "None.";
        if (args.length > 3) {
            reason = args[3];
            if (reason.length() > 500) {
                sendError(channel, "Reason can only have a maximum length of 500 characters!");
                return;
            }
        }

        MutePunishment p;

        try {
            p = ModerationHandler.mute(member, severity, reason, event.sender.getIdLong());
        } catch (ModerationException e) {
            sendError(channel, e.getMessage());
            return;
        }

        sendSuccess(channel, "Muted <@" + p.userID + "> for " + parseTime(((long) p.duration) * 60L) + ".");
    }
}
