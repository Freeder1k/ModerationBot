package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Moderation;
import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import java.time.Instant;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class PunishCommand extends Command {
    public PunishCommand() {
        super(
                "punish",
                new String[]{},
                "!punish <user> <severity> [reason]",
                "Punish a user.\n" +
                        "Allowed severities are numbers ``1-6`` or ``v`` for a vent channel ban or ``n`` to block them from changing their nickname.\n" +
                        "These require certain config options to be set.",
                new Permission[]{Permission.MANAGE_ROLES, Permission.BAN_MEMBERS},
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
        ServerData serverData = ServerData.get(guild.getIdLong());

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
        if ("123456vn".indexOf(sev) == -1) {
            sendError(channel, "Invalid severity type.");
            return;
        }

        String reason = "None.";
        if (args.length > 3) {
            reason = args[3];
            if (reason.length() > 200) {
                sendError(channel, "Reason can only have a maximum length of 200 characters!");
                return;
            }
        }

        if (sev == '6') {
            String finalReason = reason;
            member.getUser().openPrivateChannel().queue((pc) -> pc.sendMessage("You were banned from " + guild.getName() + ". Reason: " + finalReason).queue());
        }

        Moderation.Punishment p;

        try {
            Moderation.PunishmentHandler punishmentHandler;
            try {
                punishmentHandler = Moderation.PunishmentHandler.get();
            } catch (Moderation.PunishmentHandler.NotInitializedException e) {
                sendError(channel, "Punishment handler not initialized (<@470696578403794967>)!");
                if (sev == '6')
                    member.getUser().openPrivateChannel().queue((pc) -> pc.sendMessage("nvm").queue());
                return;
            }

            p = Moderation.punish(member, sev, reason, event.sender.getIdLong(), punishmentHandler);
        } catch (Moderation.ModerationException e) {
            sendError(channel, e.getMessage());
            return;
        }
        String type = "";
        switch (p.severity) {
            case '1':
            case '2':
            case '3':
            case '4':
            case '5': {
                sendSuccess(channel, "Muted <@" + member.getId() + "> for " + parseTime(((long) p.length) * 60L));
                type = "Mute (" + p.severity + ')';
                break;
            }
            case '6': {
                sendSuccess(channel, "Banned <@" + member.getId() + "> for " + parseTime(((long) p.length) * 60L));
                type = "Ban";
                break;
            }
            case 'v': {
                sendSuccess(channel, "Removed <@" + member.getId() + ">'s access to <#" + serverData.getVentChannel() + "> for" + parseTime(((long) p.length) * 60L));
                type = "Vent ban";
                break;
            }
            case 'n': {
                sendSuccess(channel, "Removed <@" + member.getId() + ">'s nickname perms for " + parseTime(((long) p.length) * 60L));
                type = "Nickname mute";
                break;
            }
        }

        TextChannel pchannel = guild.getTextChannelById(serverData.getPunishmentChannel());
        if (pchannel == null)
            pchannel = guild.getTextChannelById(serverData.getLogChannel());
        if (pchannel != null) {
            pchannel.sendMessage(new EmbedBuilder()
                    .setColor(defaultColor)
                    .setTitle("Case " + p.id)
                    .addField("**User:**", member.getAsMention() + "\n**Type:**\n" + type, true)
                    .addField("**Length:**", parseTime(((long) p.length) * 60L) + "\n**Moderator:**\n" + event.sender.getAsMention(), true)
                    .addField("**Reason:**", p.reason, true)
                    .setTimestamp(Instant.now())
                    .build()
            ).queue();
        }
    }
}
