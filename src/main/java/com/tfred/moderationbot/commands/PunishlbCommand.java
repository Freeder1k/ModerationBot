package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.ModerationData;
import com.tfred.moderationbot.moderation.PardonPunishment;
import com.tfred.moderationbot.moderation.Punishment;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.tfred.moderationbot.commands.CommandUtils.DEFAULT_COLOR;
import static com.tfred.moderationbot.commands.CommandUtils.sendException;

public class PunishlbCommand extends Command {
    public PunishlbCommand() {
        super(
                "punishlb",
                new String[]{"whoisabadboy/girl"},
                "!punishlb",
                "Show the top 10 users by punishments.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        Punishment[] all;
        try {
            all = ModerationData.getAllPunishments(event.guild.getIdLong());
        } catch (IOException e) {
            e.printStackTrace();
            sendException(event.channel, e);
            return;
        }

        Map<Long, Integer> count = new HashMap<>();

        for (Punishment p : all) {
            if (!(p instanceof PardonPunishment)) {
                long ID = p.userID;
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
                .setColor(DEFAULT_COLOR)
                .setTitle("Top 10 punishments leaderboard!")
                .setTimestamp(Instant.now())
                .addField("**User**", mentions.toString(), true)
                .addField("**Punishments**", scores.toString(), true);

        event.channel.sendMessage(eb.build()).queue();
    }
}
