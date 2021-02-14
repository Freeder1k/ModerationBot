package com.tfred.moderationbot;

import com.tfred.moderationbot.commands.*;
import com.tfred.moderationbot.moderation.ModerationListener;
import com.tfred.moderationbot.moderation.PunishmentScheduler;
import com.tfred.moderationbot.usernames.UsernameHandler;
import com.tfred.moderationbot.usernames.UsernameListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.dv8tion.jda.api.events.ResumedEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ModerationBot extends ListenerAdapter {
    public final long startTime;
    public final CommandListener commandListener = new CommandListener();
    public final BotScheduler scheduler = new BotScheduler();

    private ModerationBot() throws InterruptedException, LoginException {
        try {
            Files.write(Paths.get("blockhunt_backup.txt"), "BOT OFFLINE\n".getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("An IO error occurred while trying to write to blockhunt_backup.txt! " + e.getMessage());
        }


        try {
            Leaderboards.updateLeaderboards();
            System.out.println("Finished initializing leaderboards data.");
        } catch (Leaderboards.LeaderboardFetchFailedException e) {
            System.out.println("Failed to initialize leaderboards data! " + e.getMessage());
        }


        commandListener.addCommand(new HelpCommand(commandListener))
                .addCommand(new ConfigCommand())
                .addCommand(new DelreactionCommand())
                .addCommand(new GetreactionsCommand())
                .addCommand(new NameCommand())
                .addCommand(new UpdatenamesCommand())
                .addCommand(new ListnamesCommand())
                .addCommand(new PardonCommand())
                .addCommand(new ModlogsCommand())
                .addCommand(new ModerationsCommand())
                .addCommand(new CaseCommand())
                .addCommand(new ModstatsCommand())
                .addCommand(new PunishlbCommand())
                .addCommand(new LbCommand())
                .addCommand(new UpdatelbCommand())
                .addCommand(new EvalCommand())
                .addCommand(new IpCommand())
                .addCommand(new EmbedtestCommand())
                .addCommand(new ShutdownCommand())
                .addCommand(new MemCommand())
                .addCommand(new MuteCommand())
                .addCommand(new BanCommand())
                .addCommand(new ChannelBanCommand())
                .addCommand(new NamepunishCommand())
                .addCommand(new UptimeCommand(this));
        System.out.println("Finished loading commands!");


        JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")) // The token of the account that is logging in.
                .setChunkingFilter(ChunkingFilter.ALL)          //
                .setMemberCachePolicy(MemberCachePolicy.ALL)    // These three are needed for some commands to do with the naming system
                .enableIntents(GatewayIntent.GUILD_MEMBERS)     //
                .setActivity(Activity.watching("BlockHunt"))
                .addEventListeners(this, commandListener, new ModerationListener(), new UsernameListener(scheduler))
                .build();
        jda.awaitReady(); // Blocking guarantees that JDA will be completely loaded.
        System.out.println("Finished Building JDA!");


        System.out.println("Guilds: " + jda.getGuilds().stream().map(Guild::getName).collect(Collectors.toList()).toString());
        startTime = System.currentTimeMillis();


        PunishmentScheduler.initialize(jda, scheduler);
        System.out.println("Finished activating punishment handler!");


        AutoRun autoRun = new AutoRun(jda);
        System.out.println("Finished activating AutoRun!");
        //Check for missed autoruns
        try {
            List<String> botdata = Files.readAllLines(Paths.get("bot.data"));
            if (!botdata.isEmpty()) {
                long start = 1603602000000L; // Sun Oct 25 2020 06:00:00 CEST
                long day = 86400000L;
                long week = 604800000L;
                long lastDate = Long.parseLong(botdata.get(0)) - start;
                long current = System.currentTimeMillis() - start;
                if ((lastDate / day) < (current / day)) {
                    boolean weekly = ((lastDate / week) < (current / week));
                    if (weekly)
                        System.out.println("Running daily and weekly update...");
                    else
                        System.out.println("Running daily update...");
                    autoRun.autoRunDaily(weekly);
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to read bot.data! " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("Hello world but Frederik was here!");
        try {
            new ModerationBot();
        } catch (LoginException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        Message message = event.getMessage();
        String msg = message.getContentRaw();

        if (event.isFromType(ChannelType.TEXT)) {
            Guild guild = event.getGuild();
            TextChannel textChannel = event.getTextChannel();
            User user = event.getAuthor();

            String prefix = "";
            if (message.isWebhookMessage()) {
                prefix = "[webhook]";
            } else {
                if (user.isBot())
                    prefix = "[bot]";
            }

            System.out.printf("(%s)[#%s]%s<%s>: %s\n", guild.getName(), textChannel.getName(), prefix, user.getName(), msg);

            //Blockhunt backup
            if (guild.getIdLong() == 265883416036245507L) {
                try {
                    if (!Files.exists(Paths.get("blockhunt_backup.txt")))
                        Files.createFile(Paths.get("blockhunt_backup.txt"));

                    String attachments = message.getAttachments().stream().map(attachment -> '\n' + attachment.getUrl()).collect(Collectors.joining());
                    Files.write(
                            Paths.get("blockhunt_backup.txt"),
                            ("(" + System.currentTimeMillis() + ")" +
                                    "[#" + textChannel.getName() + "]" +
                                    "<@" + user.getId() + "(" + prefix + user.getName() + ")>: " +
                                    msg + attachments + '\n'
                            ).getBytes(),
                            StandardOpenOption.APPEND
                    );
                } catch (IOException ignored) {
                    System.out.println("Backup failed!");
                }
            }
        } else if (event.isFromType(ChannelType.PRIVATE)) {
            System.out.printf("[PRIV]<%s>: %s\n", event.getPrivateChannel().getUser().getAsTag(), msg);
        }
    }

    @Override
    public void onResume(@Nonnull ResumedEvent event) {
        scheduler.resume();
        System.out.println("\n\nRESUMED\n\n");
    }

    @Override
    public void onReconnect(@Nonnull ReconnectedEvent event) {
        scheduler.resume();
        System.out.println("\n\nRECONNECTED\n\n");
    }

    @Override
    public void onDisconnect(@Nonnull DisconnectEvent event) {
        scheduler.pause();
        System.out.println("\n\nDISCONNECTED\n\n");
    }

    @Override
    public void onShutdown(@Nonnull ShutdownEvent event) {
        scheduler.shutdownNow();
        System.out.println("\n\nSHUTDOWN\n\n");
    }

    private class AutoRun {
        private final JDA jda;

        public AutoRun(JDA jda) {
            this.jda = jda;
            startScheduler();
        }

        private void startScheduler() {
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime RunHour = now.withHour(6).withMinute(0).withSecond(0);
            if (now.compareTo(RunHour) > 0)
                RunHour = RunHour.plusDays(1);

            Duration duration = Duration.between(now, RunHour);
            long initialDelay = duration.getSeconds();

            final ScheduledFuture<?>[] autoRunHandle = new ScheduledFuture[1];

            autoRunHandle[0] = scheduler.scheduleAtFixedRate(
                    () -> {
                        if (scheduler.isPaused()) {
                            autoRunHandle[0].cancel(true);
                            scheduler.schedule(() -> autoRunDaily(ZonedDateTime.now().getDayOfWeek().equals(DayOfWeek.SUNDAY)), 0, TimeUnit.SECONDS);
                            scheduler.schedule(this::startScheduler, 0, TimeUnit.SECONDS);
                        } else {
                            try {
                                autoRunDaily();
                            } catch (Exception ex) {
                                ex.printStackTrace(); //or logger would be better
                            }
                        }
                    },
                    initialDelay,
                    TimeUnit.DAYS.toSeconds(1),
                    TimeUnit.SECONDS);

        }

        /**
         * This method gets called daily and handles the daily username and weekly leaderboard updating.
         */
        public void autoRunDaily() {
            autoRunDaily(ZonedDateTime.now().getDayOfWeek().equals(DayOfWeek.SUNDAY));
        }

        public void autoRunDaily(boolean weekly) {
            try {
                Files.write(Paths.get("bot.data"), String.valueOf(System.currentTimeMillis()).getBytes());
            } catch (IOException ignored) {
                System.out.println("Failed to write to bot.data!");
            }
            for (Guild guild : jda.getGuilds()) {
                TextChannel channel = guild.getTextChannelById(ServerData.get(guild.getIdLong()).getLogChannel());
                if (!guild.getSelfMember().hasPermission(Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_READ))
                    channel = null;

                if (channel != null) {
                    channel.sendMessage("Daily update in progress...").queue();
                    TextChannel finalChannel = channel;
                    channel.sendMessage("Updating usernames...")
                            .queue((ignored) -> UsernameHandler.get(guild.getIdLong()).updateNames(finalChannel, jda, false));
                } else
                    UsernameHandler.get(guild.getIdLong()).updateNames(null, jda, false);

                if (weekly)
                    Leaderboards.updateLeaderboards(channel, guild);
            }
        }
    }
}
