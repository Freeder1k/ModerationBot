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
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
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
    private final JDA jda;

    private ModerationBot() throws InterruptedException, LoginException {
        try {
            Files.write(Paths.get("blockhunt_backup.txt"), "BOT OFFLINE\n".getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("[ModerationBot] ERROR - An IO error occurred while trying to write to blockhunt_backup.txt! " + e.getMessage());
        }


        try {
            Leaderboards.updateLeaderboards();
            System.out.println("[ModerationBot] INFO - Finished initializing leaderboards data.");
        } catch (Leaderboards.LeaderboardFetchFailedException e) {
            System.out.println("[ModerationBot] ERROR - Failed to initialize leaderboards data! " + e.getMessage());
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
                .addCommand(new UptimeCommand(this))
                .addCommand(new PunishmentInfo())
                .addCommand(new JoinMessageCommand());
        System.out.println("[ModerationBot] INFO - Finished loading commands!");


        jda = JDABuilder.createDefault(System.getenv("TOKEN"))
                .setChunkingFilter(ChunkingFilter.ALL)          //
                .setMemberCachePolicy(MemberCachePolicy.ALL)    // These three are needed for some commands to do with the naming system
                .enableIntents(GatewayIntent.GUILD_MEMBERS)     //
                .setActivity(Activity.watching("BlockHunt"))
                .addEventListeners(this, commandListener, new ModerationListener(), new UsernameListener(scheduler))
                .build();
        jda.awaitReady(); // Blocking guarantees that JDA will be completely loaded.
        System.out.println("[ModerationBot] INFO - Finished building JDA!");

        System.out.println("[ModerationBot] INFO - Guilds: " + jda.getGuilds().stream().map(Guild::getName).collect(Collectors.toList()).toString());
        startTime = System.currentTimeMillis();


        PunishmentScheduler.initialize(jda, scheduler);
        System.out.println("[ModerationBot] INFO - Finished initializing punishment handler!");


        startDailyScheduler();
        System.out.println("[ModerationBot] INFO - Activated daily scheduler!");
        //Check if a daily update was missed
        try {
            List<String> botData = Files.readAllLines(Paths.get("bot.data"));
            if (!botData.isEmpty()) {
                long start = 1603602000000L; // Sun Oct 25 2020 06:00:00 CEST
                long day = 86400000L;
                long week = 604800000L;
                long lastDate = Long.parseLong(botData.get(0)) - start;
                long current = System.currentTimeMillis() - start;
                if ((lastDate / day) < (current / day)) {
                    boolean weekly = ((lastDate / week) < (current / week));
                    if (weekly)
                        System.out.println("[ModerationBot] INFO - Running daily and weekly update...");
                    else
                        System.out.println("[ModerationBot] INFO - Running daily update...");
                    runDailyUpdate(weekly);
                }
            }
        } catch (IOException e) {
            System.out.println("[ModerationBot] ERROR - Failed to read bot.data! " + e.getMessage());
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
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        long guildID = event.getGuild().getIdLong();

        ServerData serverData = ServerData.get(guildID);

        TextChannel channel = guild.getTextChannelById(serverData.getJoinMsgChannel());
        if (channel != null && channel.canTalk()) {
            String msg = serverData.getJoinMsg();
            if (!msg.isEmpty())
                channel.sendMessage(msg.replace("{user}", event.getMember().getAsMention())).queue();
        }
    }

    @Override
    public void onResumed(@Nonnull ResumedEvent event) {
        scheduler.resume();
        System.out.println("\n\nRESUMED\n\n");
    }

    @Override
    public void onReconnected(@Nonnull ReconnectedEvent event) {
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

    private void startDailyScheduler() {
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
                        scheduler.schedule(() -> runDailyUpdate(ZonedDateTime.now().getDayOfWeek().equals(DayOfWeek.SUNDAY)), 0, TimeUnit.SECONDS);
                        scheduler.schedule(this::startDailyScheduler, 0, TimeUnit.SECONDS);
                    } else {
                        try {
                            this.runDailyUpdate();
                        } catch (Exception e) {
                            e.printStackTrace();
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
    private void runDailyUpdate() {
        runDailyUpdate(ZonedDateTime.now().getDayOfWeek().equals(DayOfWeek.SUNDAY));
    }

    private void runDailyUpdate(boolean weekly) {
        try {
            Files.write(Paths.get("bot.data"), String.valueOf(System.currentTimeMillis()).getBytes());
        } catch (IOException e) {
            System.out.println("Failed to write to bot.data!");
            e.printStackTrace();
        }
        for (Guild guild : jda.getGuilds()) {
            TextChannel channel = guild.getTextChannelById(ServerData.get(guild.getIdLong()).getLogChannel());
            if (channel != null)
                if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS))
                    channel = null;

            if (channel != null) {
                channel.sendMessage("Daily update in progress...").queue();
                TextChannel finalChannel = channel;
                channel.sendMessage("Updating usernames...")
                        .queue((ignored) -> UsernameHandler.get(guild.getIdLong()).updateAllNames(finalChannel, jda, false));
            } else
                UsernameHandler.get(guild.getIdLong()).updateAllNames(null, jda, false);

            if (weekly)
                Leaderboards.updateLeaderboards(channel, guild);
        }
    }
}
