package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.moderation.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
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
    protected void execute(@Nonnull CommandEvent event) {
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

        Punishment[] all;
        try {
            all = ModerationData.getAllPunishments(event.guild.getIdLong());
        } catch (IOException e) {
            e.printStackTrace();
            sendException(channel, e);
            return;
        }

        int[] last7Days = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int[] last30Days = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        int[] allTime = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        long userID = moderator.getIdLong();

        long currentTime = System.currentTimeMillis();

        for (Punishment p : all) {
            if (p.moderatorID == userID) {
                boolean week = false;
                boolean month = false;
                if (currentTime - p.date < 604800000L) {
                    week = true;
                    month = true;
                } else if (currentTime - p.date < 2592000000L)
                    month = true;

                short type = -1;
                if (p instanceof MutePunishment) {
                    switch (((MutePunishment) p).severity) {
                        case 1:
                            type = 0;
                            break;
                        case 2:
                            type = 1;
                            break;
                        case 3:
                            type = 2;
                            break;
                        case 4:
                            type = 3;
                            break;
                        case 5:
                            type = 4;
                            break;
                    }
                } else if (p instanceof BanPunishment) {
                    switch (((BanPunishment) p).severity) {
                        case 1:
                            type = 5;
                            break;
                        case 2:
                            type = 6;
                            break;
                    }
                } else if (p instanceof ChannelBanPunishment)
                    type = 7;
                else if (p instanceof NamePunishment)
                    type = 8;
                else if (p instanceof PardonPunishment)
                    type = 9;

                if (type != -1) {
                    if (week)
                        last7Days[type]++;
                    if (month)
                        last30Days[type]++;
                    allTime[type]++;
                }
            }
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(DEFAULT_COLOR)
                .setTitle("Moderation statistics for " + moderator.getUser().getAsTag())
                .setFooter("ID: " + moderator.getId())
                .setTimestamp(Instant.now())
                .addField("**Last 7 days**",
                        "**Mute(1):**\n" + last7Days[0] +
                                "\n**Mute(2):**\n" + last7Days[1] +
                                "\n**Mute(3:**\n" + last7Days[2] +
                                "\n**Mute(4):**\n" + last7Days[3] +
                                "\n**Mute(5):**\n" + last7Days[4] +
                                "\n**Ban(1):**\n" + last7Days[5] +
                                "\n**Ban(2):**\n" + last7Days[6] +
                                "\n**Channel ban:**\n" + last7Days[7] +
                                "\n**Name punishment:**\n" + last7Days[8] +
                                "\n**Pardon:**\n" + last7Days[9] +
                                "\n**Total:**\n" + Arrays.stream(last7Days).sum(), true)
                .addField("**Last 30 days**",
                        "**Mute(1):**\n" + last30Days[0] +
                                "\n**Mute(2):**\n" + last30Days[1] +
                                "\n**Mute(3:**\n" + last30Days[2] +
                                "\n**Mute(4):**\n" + last30Days[3] +
                                "\n**Mute(5):**\n" + last30Days[4] +
                                "\n**Ban(1):**\n" + last30Days[5] +
                                "\n**Ban(2):**\n" + last30Days[6] +
                                "\n**Channel ban:**\n" + last30Days[7] +
                                "\n**Name punishment:**\n" + last30Days[8] +
                                "\n**Pardon:**\n" + last30Days[9] +
                                "\n**Total:**\n" + Arrays.stream(last30Days).sum(), true)
                .addField("**All time**",
                        "**Mute(1):**\n" + allTime[0] +
                                "\n**Mute(2):**\n" + allTime[1] +
                                "\n**Mute(3:**\n" + allTime[2] +
                                "\n**Mute(4):**\n" + allTime[3] +
                                "\n**Mute(5):**\n" + allTime[4] +
                                "\n**Ban(1):**\n" + allTime[5] +
                                "\n**Ban(2):**\n" + allTime[6] +
                                "\n**Channel ban:**\n" + allTime[7] +
                                "\n**Name punishment:**\n" + allTime[8] +
                                "\n**Pardon:**\n" + allTime[9] +
                                "\n**Total:**\n" + Arrays.stream(allTime).sum(), true);

        channel.sendMessage(eb.build()).queue();
    }
}
