package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Leaderboards;
import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.Date;
import java.util.List;

import static com.tfred.moderationbot.commands.CommandUtils.defaultColor;
import static com.tfred.moderationbot.commands.CommandUtils.sendError;

public class LbCommand extends Command {
    public LbCommand() {
        super(
                "lb",
                new String[]{"leaderboard"},
                "!lb <board>",
                " Sends a message with a Blockhunt leaderboard that gets updated weekly.\n" +
                        "If there is an older message with the same board type that one gets deleted if possible.\n" +
                        "Valid boards are:\n" +
                        "``0`` - Hider wins\n" +
                        "``1`` - Hunter wins\n" +
                        "``2`` - Kills",
                new Permission[]{},
                false,
                true,
                false
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        TextChannel channel = event.channel;
        ServerData serverData = ServerData.get(event.guild.getIdLong());

        if (event.message.length() != 5) {
            sendHelpMessage(channel);
            return;
        }
        char board = event.message.charAt(4);
        if (!((board == '0') || (board == '1') || (board == '2'))) {
            sendError(channel, "Board number must be between 0 and 2!");
            return;
        }
        int boardNum = Character.getNumericValue(board);

        List<String> lb = Leaderboards.lbToString(boardNum, event.guild.getIdLong());
        if (lb == null) {
            sendError(channel, "Failed to fetch leaderboard data!");
            return;
        }

        EmbedBuilder eb = new EmbedBuilder().setColor(defaultColor);
        eb.addField(new String[]{"Hider Wins", "Hunter Wins", "Kills"}[boardNum] + " Leaderboard:", lb.remove(0), false);
        for (String s : lb) {
            eb.addField("", s, false);
        }
        eb.setFooter("Last update ");
        eb.setTimestamp(new Date(Leaderboards.getDate()).toInstant());

        channel.sendMessage(eb.build()).queue((m) -> serverData.setLbMessage(boardNum, channel.getIdLong(), m.getIdLong()));
    }
}
