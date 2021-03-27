package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.Permission;

import static com.tfred.moderationbot.commands.CommandUtils.sendError;

public class JoinMessageCommand extends Command {
    public JoinMessageCommand() {
        super(
                "joinmessage",
                new String[]{"joinmsg"},
                "!joinmessage <set <msg>|remove> ",
                "Set or remove the join message. The message can contain the {user} argument which translates to a user mention.",
                new Permission[]{},
                false,
                true,
                false
        );
    }
//TODO limits
    @Override
    public void execute(CommandEvent event) {
        if (event.args.length == 1) {
            sendHelpMessage(event.channel);
            return;
        }

        if (event.args[1].equals("set")) {
            if (event.args.length < 3) {
                sendError(event.channel, "Insufficient amount of arguments!");
                return;
            }

            String joinMsg = event.message.split(" ", 3)[2];

            ServerData.get(event.guild.getIdLong()).setJoinMsg(joinMsg);

            CommandUtils.sendSuccess(event.channel, "Set the join message to \"``" + joinMsg + "``\".");
        } else if (event.args[1].equals("remove")) {
            ServerData.get(event.guild.getIdLong()).setJoinMsg("");

            CommandUtils.sendSuccess(event.channel, "Removed the join message.");
        } else
            CommandUtils.sendError(event.channel, "Invalid option: " + event.args[1]);
    }
}
