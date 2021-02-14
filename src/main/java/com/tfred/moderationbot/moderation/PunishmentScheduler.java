package com.tfred.moderationbot.moderation;

import com.tfred.moderationbot.BotScheduler;
import com.tfred.moderationbot.ServerData;
import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PunishmentScheduler {
    private static PunishmentScheduler punishmentScheduler = null;
    private final BotScheduler scheduler;
    private final JDA jda;

    private PunishmentScheduler(JDA jda, BotScheduler scheduler) {
        this.jda = jda;
        this.scheduler = scheduler;
    }

    /**
     * Get an instance of the punishment scheduler.
     *
     * @throws NotInitializedException
     *          If the punishment scheduler hasn't been initialized yet with PunishmentScheduler.initialize.
     */
    @Nonnull
    public static PunishmentScheduler get() throws NotInitializedException {
        if (punishmentScheduler == null)
            throw new NotInitializedException();

        return punishmentScheduler;
    }

    /**
     * Initialize the punishment scheduler. This also loads all active punishments for each guild.
     */
    public static synchronized void initialize(@Nonnull JDA jda, @Nonnull BotScheduler scheduler) {
        if (punishmentScheduler == null) {
            punishmentScheduler = new PunishmentScheduler(jda, scheduler);

            //Load active punishments
            try {
                List<Long> ids = Files.find(Paths.get("moderations"), 1, (p, bfa) -> bfa.isDirectory() && p.getFileName().toString().matches("\\d+"))
                        .map(p -> Long.parseLong(p.getFileName().toString()))
                        .collect(Collectors.toList());

                for (long id : ids) {
                    try {
                        TimedPunishment[] bla = ModerationData.getActivePunishments(id);
                        for (TimedPunishment p : bla) {
                            punishmentScheduler.schedule(id, p);
                        }
                    } catch (IOException ignored) {
                        System.out.println("Failed to read active punishments for guild with ID " + id);
                    }
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    protected void schedule(long guildID, @Nonnull TimedPunishment p) {
        scheduler.schedule(
                () -> runEndPunishment(guildID, p),
                p.getTimeLeft(),
                TimeUnit.MILLISECONDS
        );
    }

    private void runEndPunishment(long guildID, TimedPunishment p) {
        String response = "";
        Guild guild = jda.getGuildById(guildID);

        try {
            TimedPunishment ap = ModerationData.removeActivePunishment(guildID, p.id);
            if (ap == null)
                return;

            if (guild != null) {
                try {
                    response = ModerationHandler.endPunishment(guild, p, false);
                } catch (ModerationException e) {
                    response = e.getMessage();
                }
            }
        } catch (IOException e) {
            response = "An IO error occured while updating active.data (<@470696578403794967>)! " + e.getMessage();
        }

        if (guild != null) {
            ServerData serverData = ServerData.get(guildID);
            TextChannel pChannel = guild.getTextChannelById(serverData.getPunishmentChannel());
            if (pChannel == null) {
                pChannel = guild.getTextChannelById(serverData.getLogChannel());
                if (pChannel == null)
                    return;
            }
            CommandUtils.sendInfo(pChannel, response);
        }
    }

    public static class NotInitializedException extends Exception {
        public NotInitializedException() {
            super("Please initialize the punishment handler first by running PunishmentHandler.initialize(jda, scheduler)!");
        }
    }
}