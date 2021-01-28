package com.tfred.moderationbot;

import com.tfred.moderationbot.commands.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.dv8tion.jda.api.events.ResumedEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ModerationBot extends ListenerAdapter {
    /**
     * IDs of members of which a {@link GuildMemberUpdateNicknameEvent nickname update event} should be ignored
     */
    private final ConcurrentHashMap<Long, Set<Long>> ignoredUsers = new ConcurrentHashMap<>();
    private final AutoRun autoRun;

    private final ArrayList<Command> commands;

    private ModerationBot(JDA jda) {
        autoRun = new AutoRun(jda);
        commands = new ArrayList<>();
        System.out.println("Finished activating autoRun!");
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
        } catch (IOException ignored) {
            System.out.println("Failed to read bot data!");
        }
    }

    public static void main(String[] args) {
        System.out.println("Hello world but Frederik was here!");

        try {
            Files.write(Paths.get("blockhunt_backup.txt"), "BOT OFFLINE\n".getBytes(), StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }

        try {
            Leaderboards.updateLeaderboards();
            System.out.println("Finished initializing leaderboards data.");
        } catch (Leaderboards.LeaderboardFetchFailedException e) {
            System.out.println("Failed to initialize leaderboards data! " + e.getMessage());
        }

        JDA jda;
        try {
            jda = JDABuilder.createDefault(System.getenv("TOKEN")) // The token of the account that is logging in.
                    .setChunkingFilter(ChunkingFilter.ALL)          //
                    .setMemberCachePolicy(MemberCachePolicy.ALL)    // These three are needed for some commands to do with the naming system
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)     //
                    .setActivity(Activity.watching("BlockHunt"))
                    .build();
            jda.awaitReady(); // Blocking guarantees that JDA will be completely loaded.
            System.out.println("Finished Building JDA!");
        } catch (LoginException | InterruptedException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Guilds: " + jda.getGuilds().stream().map(Guild::getName).collect(Collectors.toList()).toString());

        Moderation.PunishmentHandler.initialize(jda);
        System.out.println("Finished activating punishment handler!");

        ModerationBot bot = new ModerationBot(jda);
        bot.addCommand(new HelpCommand(bot))
                .addCommand(new ConfigCommand())
                .addCommand(new DelreactionCommand())
                .addCommand(new GetreactionsCommand())
                .addCommand(new NameCommand())
                .addCommand(new UpdatenamesCommand())
                .addCommand(new ListnamesCommand())
                .addCommand(new PunishCommand())
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
        ;
        System.out.println("Finished loading commands!");

        jda.addEventListener(bot);   // An instance of a class that will handle events.
    }

    public void addIgnoredUser(long userID, long guildID) {
        Set<Long> guildSet = ignoredUsers.computeIfAbsent(guildID, k -> ConcurrentHashMap.newKeySet());
        guildSet.add(userID);
    }

    public void removeIgnoredUser(long userID, long guildID) {
        Set<Long> guildSet = ignoredUsers.get(guildID);
        if (guildSet != null)
            guildSet.remove(userID);
    }

    private boolean isIgnoredUser(long userID, long guildID) {
        Set<Long> guildSet = ignoredUsers.get(guildID);
        if (guildSet != null)
            return guildSet.contains(userID);
        return false;
    }

    public ModerationBot addCommand(Command command) {
        commands.add(command);
        return this;
    }

    public Command[] getCommands() {
        return commands.toArray(new Command[]{});
    }

    /**
     * NOTE THE @Override!
     * This method is actually overriding a method in the ListenerAdapter class! We place an @Override annotation
     * right before any method that is overriding another to guarantee to ourselves that it is actually overriding
     * a method from a super class properly. You should do this every time you override a method!
     * <p>
     * As stated above, this method is overriding a hook method in the
     * {@link net.dv8tion.jda.api.hooks.ListenerAdapter ListenerAdapter} class. It has convenience methods for all JDA events!
     * Consider looking through the events it offers if you plan to use the ListenerAdapter.
     * <p>
     * In this example, when a message is received it is printed to the console.
     *
     * @param event An event containing information about a {@link net.dv8tion.jda.api.entities.Message Message} that was
     *              sent in a channel.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        //Event specific information
        Message message = event.getMessage();
        String msg = message.getContentRaw();

        if (event.isFromType(ChannelType.TEXT))         //If this message was sent to a Guild TextChannel
        {
            Guild guild = event.getGuild();
            TextChannel textChannel = event.getTextChannel();
            Member member = event.getMember();
            User user = event.getAuthor();

            String name;
            if (message.isWebhookMessage()) {
                name = "[webhook]" + user.getName();                //If this is a Webhook message, then there is no Member associated
            } else {
                assert member != null;
                name = member.getEffectiveName();
                if (user.isBot())
                    name = "[bot]" + name;
            }

            System.out.printf("(%s)[%s]<%s>: %s\n", guild.getName(), textChannel.getName(), name, msg);
            //Blockhunt backup
            if (guild.getIdLong() == 265883416036245507L) {
                try {
                    if (!Files.exists(Paths.get("blockhunt_backup.txt")))
                        Files.createFile(Paths.get("blockhunt_backup.txt"));

                    String attachments = message.getAttachments().stream().map(attachment -> '\n' + attachment.getUrl()).collect(Collectors.joining());
                    Files.write(Paths.get("blockhunt_backup.txt"), ("(" + System.currentTimeMillis() + ")[#" + textChannel.getName() + "]<@" + user.getId() + "(" + name + ")>: " + msg + attachments + '\n').getBytes(), StandardOpenOption.APPEND);
                } catch (IOException ignored) {
                    System.out.println("Backup failed!");
                }
            }

            //Process commands
            /*
            if (msg.startsWith("!") && guild.getSelfMember().hasPermission(textChannel, Permission.MESSAGE_WRITE) && isPerson) {
                if (guild.getSelfMember().hasPermission(textChannel, Permission.MESSAGE_EMBED_LINKS))
                    CommandUtils.process(event);
                else
                    textChannel.sendMessage("Please give me the Embed Links permission to run commands.").queue();

            }*/
            if (!msg.isEmpty() && msg.charAt(0) == '!') {
                for (Command command : commands) {
                    if (command.isCommand(msg)) {
                        CompletableFuture.runAsync(() -> command.run(event));
                        return;
                    }
                }
            }
        } else if (event.isFromType(ChannelType.PRIVATE)) //If this message was sent to a PrivateChannel
        {
            System.out.printf("[PRIV]<%s>: %s\n", event.getAuthor().getName(), msg);
        }
    }

    /**
     * Checks whether a user that joins a server is saved in the username system and if they are it
     * updates their nickname accordingly and sends a message to the join channel.
     *
     * @param event An event containing information about a new join.
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member m = event.getMember();
        Guild guild = event.getGuild();
        ServerData serverData = ServerData.get(guild.getIdLong());

        //Manages associated mc names
        TextChannel channel = guild.getTextChannelById(serverData.getJoinChannel());
        boolean canWrite = true;
        if (channel == null)
            canWrite = false;
        else if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_WRITE, Permission.VIEW_CHANNEL, Permission.MESSAGE_EMBED_LINKS))
            canWrite = false;

        String mcName;
        try {
            mcName = UserData.get(guild.getIdLong()).getUsername(m.getIdLong());
        } catch (UserData.RateLimitException e) {
            if (canWrite)
                CommandUtils.sendError(channel, "Failed to get <@" + m.getId() + ">'s minecraft name: " + e.getMessage());
            mcName = "";
        }

        if (!mcName.isEmpty() && canWrite)
            channel.sendMessage("<@" + m.getId() + ">'s minecraft name is saved as " + mcName.replaceAll("_", "\\_") + ".").queue();

        try {
            addIgnoredUser(m.getIdLong(), guild.getIdLong());
            m.modifyNickname(mcName).queue();
        } catch (HierarchyException | InsufficientPermissionException ignored) {
            removeIgnoredUser(m.getIdLong(), guild.getIdLong());
        }

        //Manages punished users
        try {
            String response = "";
            for (Moderation.ActivePunishment ap : Moderation.getActivePunishments(guild.getIdLong())) {
                if (ap.memberID == m.getIdLong()) {
                    long id;
                    Role role;
                    TextChannel channel2;

                    switch (ap.punishment.severity) {
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5': {
                            id = serverData.getMutedRole();
                            if (id == 0) {
                                response = "Please set a muted role with ``!config mutedrole <@role>``!";
                            } else if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                                response = "The bot is missing the manage roles permission!";
                            } else {
                                role = guild.getRoleById(id);
                                if (role == null) {
                                    response = "Please set a new muted role with ``!config mutedrole <@role>``!";
                                } else
                                    guild.addRoleToMember(m, role).queue();
                            }
                            break;
                        }
                        case '6': {
                            if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
                                response = "The bot is missing the ban members permission!";
                            } else
                                response = m.getAsMention() + " should be banned!";
                            break;
                        }
                        case 'v': {
                            id = serverData.getVentChannel();
                            if (id == 0) {
                                response = "Please set a vent channel with ``!config ventchannel <#channel>``!";
                            } else {
                                channel2 = guild.getTextChannelById(id);
                                if (channel2 == null) {
                                    response = "Vent channel was deleted! Please set a new vent channel with ``!config ventchannel <#channel>``!";
                                } else if (!guild.getSelfMember().hasPermission(channel2, Permission.MANAGE_PERMISSIONS)) {
                                    response = "The bot is missing the manage permissions permission in " + channel2.getAsMention() + "!";
                                } else
                                    channel2.putPermissionOverride(m).setDeny(Permission.VIEW_CHANNEL).queue();
                            }
                            break;
                        }
                        case 'n': {
                            id = serverData.getNoNicknameRole();
                            if (id == 0) {
                                response = "Please set a no nickname role with ``!config nonickrole <@role>``!";
                            } else if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                                response = "The bot is missing the manage roles permission!";
                            } else {
                                role = guild.getRoleById(id);
                                if (role == null) {
                                    response = "noNickname role was deleted! Please set a no nickname role with ``!config nonickrole <@role>``!";
                                } else
                                    guild.addRoleToMember(m, role).queue();
                            }
                            break;
                        }
                    }
                }
                if ((!response.isEmpty()) && canWrite)
                    CommandUtils.sendError(channel, response);
            }
        } catch (IOException ignored) {
            System.out.println("IO ERROR ON ACTIVE.DATA FOR " + guild.getName());
        }
    }

    /**
     * When a user updates their nickname the bot tests to see if their new nickname is compliant with the username system.
     *
     * @param event An event containing information about a nickname change.
     */
    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        Member m = event.getMember();
        long mID = m.getIdLong();
        if (!isIgnoredUser(mID, m.getGuild().getIdLong()))
            checkNameChange(event.getOldNickname(), event.getNewNickname(), m);
        else
            removeIgnoredUser(mID, m.getGuild().getIdLong());
    }

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event) {
        User u = event.getUser();

        for (Guild g : event.getJDA().getGuilds()) {
            Member m = g.getMember(u);
            if (m != null)
                if (m.getNickname() == null)
                    checkNameChange(event.getOldName(), event.getNewName(), m);
        }
    }

    private void checkNameChange(String old_n, String new_n, Member m) {
        Guild g = m.getGuild();
        long guildID = g.getIdLong();
        String[] mc_n;
        try {
            mc_n = UserData.get(guildID).getUsernames(m.getIdLong());
        } catch (UserData.RateLimitException e) {
            autoRun.scheduleNameCheck(old_n, m, e.timeLeft + 10);
            return;
        }
        ServerData serverData = ServerData.get(g.getIdLong());
        if (mc_n.length == 0)
            return;

        String newMcName, oldMcName;

        if (mc_n.length == 1) {
            if (mc_n[0].equals("e") || mc_n[0].equals("-"))
                return;
            newMcName = mc_n[0];
            oldMcName = null;
        } else {
            oldMcName = mc_n[0];
            newMcName = mc_n[1];
        }

        if (new_n == null)
            new_n = m.getEffectiveName();
        new_n = CommandUtils.parseName(new_n);

        if (!newMcName.equals(new_n)) {
            try {
                addIgnoredUser(m.getIdLong(), m.getGuild().getIdLong());
                m.modifyNickname(old_n).queue();
            } catch (HierarchyException | InsufficientPermissionException ignored) {
                removeIgnoredUser(m.getIdLong(), m.getGuild().getIdLong());
            }

            m.getUser().openPrivateChannel().queue((channel) -> channel.sendMessage("Your nickname in " + g.getName() + " was reset due to it being incompatible with the username system.").queue());
        } else {
            if (old_n == null)
                old_n = m.getUser().getName();
            old_n = CommandUtils.parseName(old_n);
            if (!old_n.equals(new_n)) {
                TextChannel namechannel = g.getTextChannelById(serverData.getNameChannel());
                if (namechannel == null) {
                    namechannel = g.getTextChannelById(serverData.getLogChannel());
                }
                if (namechannel != null)
                    if (oldMcName != null)
                        if (oldMcName.equals(old_n))
                            namechannel.sendMessage(new EmbedBuilder().setColor(CommandUtils.defaultColor).setTitle("Updated user:").setDescription(m.getAsMention() + " (" + old_n + "->" + new_n + ")").build()).queue();
            }
        }
    }

    @Override
    public void onResume(ResumedEvent event) {
        autoRun.resume(event.getJDA());
        Moderation.PunishmentHandler.resume(event.getJDA());
        System.out.println("\n\nRESUMED\n\n");
    }

    @Override
    public void onReconnect(ReconnectedEvent event) {
        autoRun.resume(event.getJDA());
        Moderation.PunishmentHandler.resume(event.getJDA());
        System.out.println("\n\nRECONNECTED\n\n");
    }

    @Override
    public void onDisconnect(@NotNull DisconnectEvent event) {
        autoRun.pause();
        Moderation.PunishmentHandler.pause();
        System.out.println("\n\nDISCONNECTED\n\n");
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        autoRun.stop();
        Moderation.PunishmentHandler.stop();
        System.out.println("\n\nSHUTDOWN\n\n");
    }

    private class AutoRun {
        private final ScheduledExecutorService scheduler;
        private final AtomicBoolean isNameCheckSchedulerActive = new AtomicBoolean(false);
        private final LinkedBlockingDeque<ScheduledNameCheck> scheduledNameChecks = new LinkedBlockingDeque<>();
        private JDA jda;
        private boolean paused;
        private boolean ranWhilePaused;
        private boolean weeklyWhilePaused;

        AutoRun(JDA jda) {
            this.jda = jda;
            paused = false;
            ranWhilePaused = false;
            weeklyWhilePaused = false;

            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime RunHour = now.withHour(6).withMinute(0).withSecond(0);
            if (now.compareTo(RunHour) > 0)
                RunHour = RunHour.plusDays(1);

            Duration duration = Duration.between(now, RunHour);
            long initialDelay = duration.getSeconds();

            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(
                    () -> {
                        if (paused) {
                            ranWhilePaused = true;
                            if (ZonedDateTime.now().getDayOfWeek().equals(DayOfWeek.SUNDAY))
                                weeklyWhilePaused = true;
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

        public void stop() {
            try {
                scheduler.shutdownNow();
            } catch (Exception ignored) {
            }
        }

        public void pause() {
            paused = true;
        }

        public void resume(JDA jda) {
            this.jda = jda;
            paused = false;
            if (ranWhilePaused) {
                ranWhilePaused = false;
                try {
                    if (weeklyWhilePaused)
                        autoRunDaily(true);
                    else
                        autoRunDaily();
                } catch (Exception ex) {
                    ex.printStackTrace(); //or logger would be better
                }
            }
        }

        /**
         * Schedule a members nickname to be checked once the rate limit timer is over.
         *
         * @param old_n       The old nickname.
         * @param m           The member to check.
         * @param timeSeconds The time in seconds until the rate limit is over.
         */
        public void scheduleNameCheck(String old_n, Member m, int timeSeconds) {
            System.out.println("a");
            if (isNameCheckSchedulerActive.compareAndSet(false, true)) {
                scheduler.schedule(() -> {
                    System.out.println(scheduledNameChecks.size());
                    scheduledNameChecks.forEach(snc -> {
                        System.out.println("c");
                        Guild g = jda.getGuildById(snc.guildID);
                        if (g != null) {
                            Member member = g.getMemberById(snc.memberID);
                            if (member != null)
                                checkNameChange(snc.old_n, member.getEffectiveName(), member);
                        }
                    });
                    scheduledNameChecks.clear();
                    isNameCheckSchedulerActive.set(false);
                }, timeSeconds, TimeUnit.SECONDS);
            }
            long guildID = m.getGuild().getIdLong();
            long memberID = m.getIdLong();
            if (scheduledNameChecks.stream().noneMatch(snc -> snc.guildID == guildID && snc.memberID == memberID))
                scheduledNameChecks.add(new ScheduledNameCheck(old_n, guildID, memberID));
            System.out.println("b");
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
                            .queue((ignored) -> UserData.updateNames(finalChannel, guild, false));
                } else
                    UserData.updateNames(null, guild, false);

                if (weekly)
                    Leaderboards.updateLeaderboards(channel, guild);
            }
        }

        private class ScheduledNameCheck {
            public final String old_n;
            public final long guildID;
            public final long memberID;

            public ScheduledNameCheck(String old_n, long guildID, long memberID) {
                this.old_n = old_n;
                this.guildID = guildID;
                this.memberID = memberID;
            }
        }
    }
}
