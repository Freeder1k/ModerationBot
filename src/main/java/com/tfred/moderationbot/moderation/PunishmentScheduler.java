package com.tfred.moderationbot.moderation;


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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class PunishmentScheduler {
    private static PunishmentScheduler punishmentScheduler = null;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentLinkedQueue<scheduledEndPunishment> queued;
    private final AtomicBoolean paused;
    private JDA jda;

    private PunishmentScheduler(JDA jda, ScheduledExecutorService scheduler) {
        this.jda = jda;
        paused = new AtomicBoolean(false);
        queued = new ConcurrentLinkedQueue<>();

        this.scheduler = scheduler;
    }

    public static PunishmentScheduler get() throws NotInitializedException {
        if (punishmentScheduler == null)
            throw new NotInitializedException();

        return punishmentScheduler;
    }

    public static synchronized void initialize(@Nonnull JDA jda, @Nonnull ScheduledExecutorService scheduler) {
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

    public static void pause() {
        try {
            get().paused.set(true);
        } catch (NotInitializedException ignored) {
        }
    }

    public static void resume(@Nonnull JDA jda) {
        try {
            PunishmentScheduler pS = get();
            pS.jda = jda;
            pS.paused.set(false);
            while (!pS.queued.isEmpty())
                pS.queued.remove().run();
        } catch (NotInitializedException ignored) {
        }
    }

    protected void schedule(long guildID, TimedPunishment p) {
        long time = p.getTimeLeft();
        if (time < 0)
            runEndPunishment(guildID, p);
        else {
            scheduler.schedule(
                    () -> runEndPunishment(guildID, p),
                    time,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void runEndPunishment(long guildID, TimedPunishment p) {
        if (paused.get()) {
            queued.add(new scheduledEndPunishment(guildID, p));
            return;
        }

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

    private class scheduledEndPunishment {
        private final long guildID;
        private final TimedPunishment p;

        public scheduledEndPunishment(long guildID, TimedPunishment p) {
            this.guildID = guildID;
            this.p = p;
        }

        public void run() {
            runEndPunishment(guildID, p);
        }
    }
}