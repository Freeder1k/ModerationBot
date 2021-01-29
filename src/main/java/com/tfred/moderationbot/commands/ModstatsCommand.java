package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Moderation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class ModstatsCommand extends Command {
    public ModstatsCommand() {
        super(
                "modstats",
                new String[]{"isgoodmod"},
                "!modstats [user]",
                "Show moderator statistics for a user.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        TextChannel channel = event.channel;

        Member moderator;
        if (event.args.length == 1)
            moderator = event.sender;
        else if (event.args.length == 2) {
            moderator = parseMember(event.guild, event.args[1]);
            if (moderator == null) {
                sendError(channel, "Couldn't find the specified user!");
                return;
            }
        } else {
            sendError(channel, "Invalid amount of arguments.");
            return;
        }

        Moderation.UserPunishment[] all;
        try {
            all = Moderation.getAllUserPunishments(event.guild.getIdLong());
        } catch (IOException e) {
            sendError(channel, "An IO exception occurred! " + e.getMessage());
            return;
        }

        int[] last7Days = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
        int[] last30Days = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
        int[] allTime = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};

        long userID = moderator.getIdLong();

        long currentTime = System.currentTimeMillis();

        for (Moderation.UserPunishment up : all) {
            if (up.p.punisherID == userID) {
                boolean week = false;
                boolean month = false;
                if (currentTime - up.p.date < 604800000L) {
                    week = true;
                    month = true;
                } else if (currentTime - up.p.date < 2592000000L)
                    month = true;

                short sevType;
                switch (up.p.severity) {
                    case '1':
                        sevType = 0;
                        break;
                    case '2':
                        sevType = 1;
                        break;
                    case '3':
                        sevType = 2;
                        break;
                    case '4':
                        sevType = 3;
                        break;
                    case '5':
                        sevType = 4;
                        break;
                    case '6':
                        sevType = 5;
                        break;
                    case 'v':
                        sevType = 6;
                        break;
                    case 'n':
                        sevType = 7;
                        break;
                    case 'u':
                        sevType = 8;
                        break;
                    default:
                        sevType = -1;
                }
                if (sevType != -1) {
                    if (week)
                        last7Days[sevType]++;
                    if (month)
                        last30Days[sevType]++;
                    allTime[sevType]++;
                }
            }
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(defaultColor)
                .setTitle("Moderation statistics for " + moderator.getUser().getAsTag())
                .setFooter("ID: " + moderator.getId())
                .setTimestamp(Instant.now())
                .addField("**Last 7 days**",
                        "**Sev 1:**\n" + last7Days[0] +
                                "\n**Sev 2:**\n" + last7Days[1] +
                                "\n**Sev 3:**\n" + last7Days[2] +
                                "\n**Sev 4:**\n" + last7Days[3] +
                                "\n**Sev 5:**\n" + last7Days[4] +
                                "\n**Sev 6:**\n" + last7Days[5] +
                                "\n**Sev v:**\n" + last7Days[6] +
                                "\n**Sev n:**\n" + last7Days[7] +
                                "\n**Pardon:**\n" + last7Days[8] +
                                "\n**Total:**\n" + Arrays.stream(last7Days).sum(), true)
                .addField("**Last 30 days**",
                        "**Sev 1:**\n" + last30Days[0] +
                                "\n**Sev 2:**\n" + last30Days[1] +
                                "\n**Sev 3:**\n" + last30Days[2] +
                                "\n**Sev 4:**\n" + last30Days[3] +
                                "\n**Sev 5:**\n" + last30Days[4] +
                                "\n**Sev 6:**\n" + last30Days[5] +
                                "\n**Sev v:**\n" + last30Days[6] +
                                "\n**Sev n:**\n" + last30Days[7] +
                                "\n**Pardon:**\n" + last30Days[8] +
                                "\n**Total:**\n" + Arrays.stream(last30Days).sum(), true)
                .addField("**All time**",
                        "**Sev 1:**\n" + allTime[0] +
                                "\n**Sev 2:**\n" + allTime[1] +
                                "\n**Sev 3:**\n" + allTime[2] +
                                "\n**Sev 4:**\n" + allTime[3] +
                                "\n**Sev 5:**\n" + allTime[4] +
                                "\n**Sev 6:**\n" + allTime[5] +
                                "\n**Sev v:**\n" + allTime[6] +
                                "\n**Sev n:**\n" + allTime[7] +
                                "\n**Pardon:**\n" + allTime[8] +
                                "\n**Total:**\n" + Arrays.stream(allTime).sum(), true);

        channel.sendMessage(eb.build()).queue();
    }
}
