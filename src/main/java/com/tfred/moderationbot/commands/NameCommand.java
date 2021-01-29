package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.usernames.RateLimitException;
import com.tfred.moderationbot.usernames.UsernameHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class NameCommand extends Command {
    public NameCommand() {
        super(
                "name",
                new String[]{},
                "!name <set|remove> <user> [username]",
                "Set or remove the associated minecraft username.\n" +
                        "The set option requires the username to be specified.",
                new Permission[]{Permission.NICKNAME_MANAGE},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        UsernameHandler usernameHandler = UsernameHandler.get(event.guild.getIdLong());
        String[] args = event.args;
        TextChannel channel = event.channel;

        if (args.length == 1) {
            sendHelpMessage(event.channel);
            return;
        }

        if (args.length < 3) {
            sendError(event.channel, "Insufficient amount of arguments!");
            return;
        }

        if (args[1].equals("set")) {
            Member member = parseMember(event.guild, args[2]);
            if (member == null) {
                sendError(channel, "Invalid user.");
                return;
            }

            if (args.length < 4) {
                sendError(channel, "Insufficient amount of arguments!");
                return;
            }
            if (args[3].equalsIgnoreCase("e") || args[3].equalsIgnoreCase("none")) {
                sendError(channel, "Name may not be \"e\" or \"none\"!");
                return;
            }

            String result;
            try {
                result = usernameHandler.setUuid(member, args[3]);
            } catch (RateLimitException e) {
                sendError(channel, e.getMessage());
                return;
            }
            if (result.equals("e"))
                sendError(channel, "An error occurred. Please try again later.");
            else if (result.equals(""))
                sendError(channel, "``" + args[3] + "`` isn't a valid Minecraft username!");
            else
                sendSuccess(channel, "Set ``" + result + "`` as username of " + member.getAsMention() + ".");
        } else if (args[1].equals("remove")) {
            long memberID;
            Member member = parseMember(event.guild, args[2]);
            if (member == null) {
                memberID = parseID(args[2]);
                if (memberID == 0) {
                    sendError(channel, "Invalid user.");
                    return;
                }
            } else
                memberID = member.getIdLong();

            usernameHandler.removeUser(memberID);
            sendSuccess(channel, "Removed <@" + memberID + ">'s username.");
        } else
            sendError(channel, "Unknown action! Allowed actions: ``set, remove``.");
    }
}
