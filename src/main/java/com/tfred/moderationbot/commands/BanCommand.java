package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.BanPunishment;
import com.tfred.moderationbot.moderation.ModerationException;
import com.tfred.moderationbot.moderation.ModerationHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class BanCommand extends Command {
    public BanCommand() {
        super(
                "ban",
                new String[]{},
                "!ban <user> <severity> reason",
                "Ban a user. Allowed severities: 1 or 2.\n" +
                        "For info on punishment lengths run !punishmentinfo\n" +
                        "If the user is not in the guild a user Tag (ex: user#1234) won't work.",
                new Permission[]{Permission.BAN_MEMBERS},
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
        User user;
        if (member == null) {
            try {
                user = event.event.getJDA().retrieveUserById(args[1]).complete();
            } catch (ErrorResponseException e) {
                if (e.getErrorResponse() == ErrorResponse.UNKNOWN_USER)
                    sendError(channel, "Invalid user.");
                else {
                    e.printStackTrace();
                    sendException(channel, e);
                }
                return;
            } catch (NumberFormatException e) {
                sendError(channel, "Invalid user.");
                return;
            }
        } else
            user = member.getUser();

        if (allowedUser(member)) {
            sendError(channel, "This user is a server moderator!");
            return;
        }

        if (args[2].length() != 1) {
            sendError(channel, "Invalid severity type.");
            return;
        }
        char sev = args[2].charAt(0);
        if ("12".indexOf(sev) == -1) {
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

        BanPunishment p;

        try {
            p = ModerationHandler.ban(guild, user, severity, reason, event.sender.getIdLong());
        } catch (ModerationException e) {
            sendError(channel, e.getMessage());
            return;
        }

        sendSuccess(channel, "Banned <@" + p.userID + "> for " + parseTime(((long) p.duration) * 60L) + ".");
    }
}
