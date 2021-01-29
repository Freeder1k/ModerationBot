package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Moderation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.tfred.moderationbot.commands.CommandUtils.defaultColor;
import static com.tfred.moderationbot.commands.CommandUtils.sendError;

public class PunishlbCommand extends Command {
    public PunishlbCommand() {
        super(
                "punishlb",
                new String[]{"whoisbadboy/girl"},
                "!punishlb",
                "Show the top 10 users by punishments.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    public void execute(CommandEvent event) {
        Moderation.UserPunishment[] all;
        try {
            all = Moderation.getAllUserPunishments(event.guild.getIdLong());
        } catch (IOException e) {
            sendError(event.channel, "An IO exception occurred! " + e.getMessage());
            return;
        }

        Map<Long, Integer> count = new HashMap<>();

        for (Moderation.UserPunishment up : all) {
            if (up.p.severity != 'u') {
                long ID = up.userID;
                count.merge(ID, 1, Integer::sum);
            }
        }

        ArrayList<Map.Entry<Long, Integer>> sortedCount = count
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .collect(Collectors.toCollection(ArrayList::new));

        StringJoiner mentions = new StringJoiner("\n");
        StringJoiner scores = new StringJoiner("\n");

        for (int i = 0; i < sortedCount.size(); i++) {
            Map.Entry<Long, Integer> e = sortedCount.get(i);
            mentions.add("**" + (i + 1) + "**  <@" + e.getKey() + ">");
            scores.add(String.valueOf(e.getValue()));
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(defaultColor)
                .setTitle("Top 10 punishments leaderboard!")
                .setTimestamp(Instant.now())
                .addField("**User**", mentions.toString(), true)
                .addField("**Punishments**", scores.toString(), true);

        event.channel.sendMessage(eb.build()).queue();
    }
}