package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ChannelBanPunishment;
import com.tfred.moderationbot.moderation.ModerationException;
import com.tfred.moderationbot.moderation.ModerationHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class ChannelBanCommand extends Command {
    public ChannelBanCommand() {
        super(
                "channelban",
                new String[]{},
                "!channelban <user> <channel> reason",
                "Ban a user from a channel.\n" +
                        "For info on punishment lengths run !punishmentinfo\n" +
                        "Requires the manage permissions permission in the specified channel.",
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

        GuildChannel banChannel = guild.getGuildChannelById(parseID(args[2]));
        if (banChannel == null) {
            sendError(channel, "Couldn't find channel <#" + args[2] + ">.");
            return;
        }

        String reason = "None.";
        if (args.length > 3) {
            reason = args[3];
            if (reason.length() > 500) {
                sendError(channel, "Reason can only have a maximum length of 500 characters!");
                return;
            }
        }

        ChannelBanPunishment p;

        try {
            p = ModerationHandler.channelBan(member, banChannel.getIdLong(), reason, event.sender.getIdLong());
        } catch (ModerationException e) {
            sendError(channel, e.getMessage());
            return;
        }

        sendSuccess(channel, "Banned <@" + p.userID + "> from <#" + p.channelID + "> for " + parseTime(((long) p.duration) * 60L) + ".");
    }
}
