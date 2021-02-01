package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ModerationException;
import com.tfred.moderationbot.moderation.ModerationHandler;
import com.tfred.moderationbot.moderation.MutePunishment;
import com.tfred.moderationbot.moderation.NamePunishment;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class NamepunishCommand extends Command {
    public NamepunishCommand() {
        super(
                "namepunish",
                new String[]{},
                "!namepunish <user> reason",
                "Remove a users nickname perms.\nRequires a no nickname and a member role to be set.",
                new Permission[]{Permission.MANAGE_ROLES},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        String[] args = event.message.split(" ", 3);
        TextChannel channel = event.channel;
        Guild guild = event.guild;

        if (args.length == 1) {
            sendHelpMessage(channel);
            return;
        }

        if (args.length < 2) {
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

        String reason = "None.";
        if (args.length > 2) {
            reason = args[2];
            if (reason.length() > 500) {
                sendError(channel, "Reason can only have a maximum length of 500 characters!");
                return;
            }
        }

        NamePunishment p;

        try {
            p = ModerationHandler.removeNamePerms(member, reason, event.sender.getIdLong());
        } catch (ModerationException e) {
            sendError(channel, e.getMessage());
            return;
        }

        sendSuccess(channel, "Removed <@" + p.userID + ">'s nickname perms for " + parseTime(((long) p.duration) * 60L) + ".");
    }
}
