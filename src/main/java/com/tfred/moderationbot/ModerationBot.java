package com.tfred.moderationbot;

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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ModerationBot extends ListenerAdapter {
    public static final List<String> ignoredUsers = new LinkedList<>();
    private static ServerData serverData;
    private static Leaderboards leaderboards;
    private static AutoRun autoRun;
    private static Moderation.PunishmentHandler punishmentHandler;

    public static void main(String[] args) {
        System.out.println("Hello world but Frederik was here!");

        serverData = new ServerData();

        leaderboards = new Leaderboards();
        if (leaderboards.failed) {
            System.out.println("Trying to initialize leaderboards again.");
            leaderboards.updateLeaderboards();
            if (!leaderboards.failed)
                System.out.println("Finished reading saved leaderboards data!");
            else
                System.out.println("Failed reading saved leaderboards data!");
        }

        JDA jda;
        try {
            jda = JDABuilder.createDefault(System.getenv("TOKEN")) // The token of the account that is logging in.
                    .addEventListeners(new ModerationBot())   // An instance of a class that will handle events.
                    .setChunkingFilter(ChunkingFilter.ALL)          //
                    .setMemberCachePolicy(MemberCachePolicy.ALL)    // These three are needed for some commands to do with the naming system
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)     //
                    .setActivity(Activity.watching("BlockHunt"))
                    .build();
            jda.awaitReady(); // Blocking guarantees that JDA will be completely loaded.
            System.out.println("Finished Building JDA!");
        } catch (LoginException | InterruptedException e) {
            //If anything goes wrong in terms of authentication, this is the exception that will represent it

            //Due to the fact that awaitReady is a blocking method, one which waits until JDA is fully loaded,
            // the waiting can be interrupted. This is the exception that would fire in that situation.
            //As a note: in this extremely simplified example this will never occur. In fact, this will never occur unless
            // you use awaitReady in a thread that has the possibility of being interrupted (async thread usage and interrupts)
            e.printStackTrace();
            return;
        }
        System.out.println("Guilds: " + jda.getGuilds().stream().map(Guild::getName).collect(Collectors.toList()).toString());

        punishmentHandler = new Moderation.PunishmentHandler(jda, serverData);
        for (Guild g : jda.getGuilds()) {
            try {
                List<Moderation.ActivePunishment> apList = Moderation.getActivePunishments(g.getId());
                if (!apList.isEmpty()) {
                    for (Moderation.ActivePunishment ap : apList) {
                        punishmentHandler.newPunishment(ap.memberID, g.getId(), ap.punishment);
                    }
                }
            } catch (IOException ignored) {
                System.out.println("Failed to read active punishments in " + g.getName());
            }

            UserData.get(g.getIdLong()); // Initialize userdata
        }
        System.out.println("Finished activating punishment handler and initializing userdata!");

        autoRun = new AutoRun(jda);
        System.out.println("Finished activating autoRun!");
        try {
            List<String> botdata = Files.readAllLines(Paths.get("bot.data"));
            if (!botdata.isEmpty()) {
                long start = 1603602000000L;
                long delay = 86400000L;
                long lastDate = Long.parseLong(botdata.get(0)) - start;
                long current = System.currentTimeMillis() - start;
                if ((lastDate / delay) < (current / delay)) {
                    System.out.println("Running daily update...");
                    autoRun.autoRunDaily();
                }
            }
        } catch (IOException ignored) {
            System.out.println("Failed to read bot data!");
        }
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

            boolean isPerson;

            String name;
            if (message.isWebhookMessage()) {
                name = "[webhook]" + user.getName();                //If this is a Webhook message, then there is no Member associated
                isPerson = false;
            } else {
                assert member != null;
                name = member.getEffectiveName();
                isPerson = !user.isBot();
                if (!isPerson)
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
            if (msg.startsWith("!") && guild.getSelfMember().hasPermission(textChannel, Permission.MESSAGE_WRITE) && isPerson) {
                if (guild.getSelfMember().hasPermission(textChannel, Permission.MESSAGE_EMBED_LINKS))
                    Commands.process(event, serverData, leaderboards, punishmentHandler);
                else
                    textChannel.sendMessage("Please give me the Embed Links permission to run commands.").queue();

            }

            //Delete messages with salt emoji if nosalt is enabled
            else if (msg.contains("\uD83E\uDDC2"))
                if (isPerson && serverData.isNoSalt(guild.getId()))
                    if (guild.getSelfMember().hasPermission(textChannel, Permission.MESSAGE_MANAGE))
                        message.delete().queue();

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
        //Manages associated mc names
        TextChannel channel = guild.getTextChannelById(serverData.getJoinChannelID(guild.getId()));
        boolean canWrite = true;
        if (channel == null)
            canWrite = false;
        else if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_WRITE, Permission.VIEW_CHANNEL, Permission.MESSAGE_EMBED_LINKS))
            canWrite = false;

        String mcName = UserData.get(guild.getIdLong()).getUsername(m.getIdLong());

        if (!mcName.isEmpty() && canWrite)
            channel.sendMessage("<@" + m.getId() + ">'s minecraft name is saved as " + mcName.replaceAll("_", "\\_") + ".").queue();

        try {
            ignoredUsers.add(m.getId());
            m.modifyNickname(mcName).queue((ignored) -> ignoredUsers.remove(m.getId()));
        } catch (HierarchyException | InsufficientPermissionException ignored) {
            ignoredUsers.remove(m.getId());
        }

        //Manages punished users
        try {
            String response = "";
            for (Moderation.ActivePunishment ap : Moderation.getActivePunishments(guild.getId())) {
                if (ap.memberID.equals(m.getId())) {
                    String id;
                    Role role;
                    TextChannel channel2;

                    switch (ap.punishment.severity) {
                        case '1':
                        case '2':
                        case '3':
                        case '4':
                        case '5': {
                            id = serverData.getMutedRoleID(guild.getId());
                            if (id.equals("0")) {
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
                            id = serverData.getVentChannelID(guild.getId());
                            if (id.equals("0")) {
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
                            id = serverData.getNoNickRoleID(guild.getId());
                            if (id.equals("0")) {
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
                    Commands.sendError(channel, response);
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
        if (!ignoredUsers.contains(m.getId()))
            checkNameChange(event.getOldNickname(), event.getNewNickname(), m);
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

    private void checkNameChange(String old_n, String new_n, Member m) {//TODO cache some data and update name if necessary
        Guild g = m.getGuild();
        String mc_n = UserData.get(g.getIdLong()).getUsername(m.getIdLong());
        if (mc_n.isEmpty())
            return;

        if (new_n == null)
            new_n = m.getUser().getName();
        new_n = Commands.getName(new_n);

        if (!mc_n.equals(new_n)) {
            try {
                ignoredUsers.add(m.getId());
                m.modifyNickname(old_n).queue((ignored) -> ignoredUsers.remove(m.getId()));
            } catch (HierarchyException | InsufficientPermissionException ignored) {
                ignoredUsers.remove(m.getId());
            }

            m.getUser().openPrivateChannel().queue((channel) -> channel.sendMessage("Your nickname in " + g.getName() + " was reset due to it being incompatible with the username system.").queue());
        } else {
            if (old_n == null)
                old_n = m.getUser().getName();
            old_n = Commands.getName(old_n);
            if (!old_n.equals(new_n)) {
                TextChannel namechannel = g.getTextChannelById(serverData.getNameChannelID(g.getId()));
                if (namechannel == null) {
                    namechannel = g.getTextChannelById(serverData.getLogChannelID((g.getId())));
                }
                if (namechannel != null)
                    namechannel.sendMessage(new EmbedBuilder().setColor(Commands.defaultColor).setTitle("Updated user:").setDescription(m.getAsMention() + " (" + old_n + "->" + new_n + ")").build()).queue();
            }
        }
    }

    @Override
    public void onResume(ResumedEvent event) {
        autoRun.resume(event.getJDA());
        punishmentHandler.resume(event.getJDA(), serverData);
        System.out.println("\n\nRESUMED\n\n");
    }

    @Override
    public void onReconnect(ReconnectedEvent event) {
        autoRun.resume(event.getJDA());
        punishmentHandler.resume(event.getJDA(), serverData);
        System.out.println("\n\nRECONNECTED\n\n");
    }

    @Override
    public void onDisconnect(@NotNull DisconnectEvent event) {
        autoRun.pause();
        punishmentHandler.pause();
        System.out.println("\n\nDISCONNECTED\n\n");
        try {
            Files.write(Paths.get("blockhunt_backup.txt"), "BOT OFFLINE".getBytes(), StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        autoRun.stop();
        punishmentHandler.stop();
        System.out.println("\n\nSHUTDOWN\n\n");
        try {
            Files.write(Paths.get("blockhunt_backup.txt"), "BOT OFFLINE".getBytes(), StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    private static class AutoRun {
        private final ScheduledExecutorService scheduler;
        private JDA jda;
        private boolean paused;
        private boolean ranWhilePaused;

        AutoRun(JDA jda) {
            this.jda = jda;
            paused = false;
            ranWhilePaused = false;

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
                    autoRunDaily();
                } catch (Exception ex) {
                    ex.printStackTrace(); //or logger would be better
                }
            }
        }

        /**
         * This method gets called daily and handles the daily username and weekly leaderboard updating.
         */
        public void autoRunDaily() {
            try {
                Files.write(Paths.get("bot.data"), String.valueOf(System.currentTimeMillis()).getBytes());
            } catch (IOException ignored) {
                System.out.println("Failed to write to bot.data!");
            }
            boolean weekly = false;
            if (ZonedDateTime.now().getDayOfWeek().equals(DayOfWeek.SUNDAY))
                weekly = true;

            for (Guild guild : jda.getGuilds()) {
                TextChannel channel = guild.getTextChannelById(serverData.getLogChannelID(guild.getId()));
                if (!guild.getSelfMember().hasPermission(Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_READ))
                    channel = null;

                if (channel != null) {
                    channel.sendMessage("Daily update in progress...").queue();
                    TextChannel finalChannel = channel;
                    channel.sendMessage("Updating usernames...")
                            .queue((ignored) -> Commands.updateNames(finalChannel, serverData, guild, true));
                } else
                    Commands.updateNames(null, serverData, guild, true);

                if (weekly)
                    Commands.updateLeaderboards(channel, leaderboards, serverData, guild);
            }
        }
    }
}
