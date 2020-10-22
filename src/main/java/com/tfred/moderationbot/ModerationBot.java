package com.tfred.moderationbot;

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
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

//TODO update modroles (and other stuff) on deletion
public class ModerationBot extends ListenerAdapter
{
    private static ServerData serverdata;
    private static UserData userdata;
    private static Leaderboards leaderboards;
    private static AutoRun autoRun;
    private static Moderation.PunishmentHandler punishmentHandler;

    public static void main(String[] args)
    {
        System.out.println("Hello world but Frederik was here!");

        serverdata = new ServerData();
        userdata = new UserData();

        leaderboards = new Leaderboards();
        if(leaderboards.failed) {
            System.out.println("Trying to initialize leaderboards again.");
            leaderboards.updateLeaderboards();
            if(!leaderboards.failed)
                System.out.println("Finished reading saved leaderboards data!");
        }

        JDA jda;
        //We construct a builder for a BOT account. If we wanted to use a CLIENT account
        // we would use AccountType.CLIENT
        try
        {
            jda = JDABuilder.createDefault(System.getenv("TOKEN")) // The token of the account that is logging in.
                    .addEventListeners(new ModerationBot())   // An instance of a class that will handle events.
                    .setChunkingFilter(ChunkingFilter.ALL)          //
                    .setMemberCachePolicy(MemberCachePolicy.ALL)    // These three are needed for !addallmembers so that all members are effected
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)     //
                    .setActivity(Activity.watching("BlockHunt"))
                    .build();
            jda.awaitReady(); // Blocking guarantees that JDA will be completely loaded.
            System.out.println("Finished Building JDA!");
        }
        catch (LoginException | InterruptedException e)
        {
            //If anything goes wrong in terms of authentication, this is the exception that will represent it

            //Due to the fact that awaitReady is a blocking method, one which waits until JDA is fully loaded,
            // the waiting can be interrupted. This is the exception that would fire in that situation.
            //As a note: in this extremely simplified example this will never occur. In fact, this will never occur unless
            // you use awaitReady in a thread that has the possibility of being interrupted (async thread usage and interrupts)
            e.printStackTrace();
            return;
        }

        System.out.println("Guilds: " + jda.getGuilds().stream().map(Guild::getName).collect(Collectors.toList()).toString());

        punishmentHandler = new Moderation.PunishmentHandler(jda, serverdata);
        //TODO check active punishments

        autoRun = new AutoRun(jda);
    }


    /**
     * NOTE THE @Override!
     * This method is actually overriding a method in the ListenerAdapter class! We place an @Override annotation
     *  right before any method that is overriding another to guarantee to ourselves that it is actually overriding
     *  a method from a super class properly. You should do this every time you override a method!
     *
     * As stated above, this method is overriding a hook method in the
     * {@link net.dv8tion.jda.api.hooks.ListenerAdapter ListenerAdapter} class. It has convenience methods for all JDA events!
     * Consider looking through the events it offers if you plan to use the ListenerAdapter.
     *
     * In this example, when a message is received it is printed to the console.
     *
     * @param event
     *          An event containing information about a {@link net.dv8tion.jda.api.entities.Message Message} that was
     *          sent in a channel.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        //These are provided with every event in JDA
        //JDA jda = event.getJDA();                       //JDA, the core of the api.
        //long responseNumber = event.getResponseNumber();//The amount of discord events that JDA has received since the last reconnect.

        //Event specific information
        User author = event.getAuthor();                //The user that sent the message
        Message message = event.getMessage();           //The message that was received.
        //MessageChannel channel = event.getChannel();    //This is the MessageChannel that the message was sent to. This could be a TextChannel, PrivateChannel, or Group!

        String msg = message.getContentDisplay();       //This returns a human readable version of the Message. Similar to what you would see in the client.

        boolean bot = author.isBot();                   //This boolean is useful to determine if the User that sent the Message is a BOT or not!

        if (event.isFromType(ChannelType.TEXT))         //If this message was sent to a Guild TextChannel
        {
            //Because we now know that this message was sent in a Guild, we can do guild specific things
            // Note, if you don't check the ChannelType before using these methods, they might return null due
            // the message possibly not being from a Guild!

            Guild guild = event.getGuild();             //The Guild that this message was sent in. (note, in the API, Guilds are Servers)
            TextChannel textChannel = event.getTextChannel(); //The TextChannel that this message was sent to.
            Member member = event.getMember();          //This Member that sent the message. Contains Guild specific information about the User!

            String name;
            if (message.isWebhookMessage()) {
                name = author.getName();                //If this is a Webhook message, then there is no Member associated
            }                                           // with the User, thus we default to the author for name.
            else {
                assert member != null;
                name = member.getEffectiveName();       //This will either use the Member's nickname if they have one,
            }                                           // otherwise it will default to their username. (User#getName())

            System.out.printf("(%s)[%s]<%s>: %s\n", guild.getName(), textChannel.getName(), name, msg);

            //Process commands
            if (msg.startsWith("!") && guild.getSelfMember().hasPermission(textChannel, Permission.MESSAGE_WRITE) && !author.isBot())
                Commands.process(event, serverdata, userdata, leaderboards, punishmentHandler);

            //Delete messages with salt emoji if nosalt is enabled
            else if (msg.contains("\uD83E\uDDC2"))
                if((!bot) && serverdata.isNoSalt(guild.getId()))
                    if (guild.getSelfMember().hasPermission(textChannel, Permission.MESSAGE_MANAGE))
                        message.delete().queue();

        } else if (event.isFromType(ChannelType.PRIVATE)) //If this message was sent to a PrivateChannel
        {
            //The message was sent in a PrivateChannel.
            //In this example we don't directly use the privateChannel, however, be sure, there are uses for it!

            //PrivateChannel privateChannel = event.getPrivateChannel();

            System.out.printf("[PRIV]<%s>: %s\n", author.getName(), msg);
        }
    }

    /**
     * Checks whether every user that joins a server is saved in the username system and if they are it
     * updates their nickname accordingly and sends a message to the join channel.
     *
     * @param event
     *          An event containing information about a new join.
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member user = event.getMember();
        Guild guild = event.getGuild();
        TextChannel channel = guild.getTextChannelById(serverdata.getJoinChannelID(guild.getId()));
        if(channel == null)
            return;
        if(!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_WRITE))
            return;

        String mcName = userdata.getUserInGuild(guild.getId(), user.getId());

        if(!mcName.isEmpty())
            channel.sendMessage("<@" + user.getId() + ">'s minecraft name is saved as " + mcName.replaceAll("_", "\\_") + ".").queue();

        if(guild.getSelfMember().hasPermission(Permission.NICKNAME_MANAGE))
            user.modifyNickname(mcName).queue();

        //TODO check if punished
    }

    /**
     * When a user updates their nickname the bot tests to see if their new nickname is compliant with the username system.
     *
     * @param event
     *          An event containing information about a nickname change.
     */
    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        String mc_n = userdata.getUserInGuild(event.getGuild().getId(), event.getMember().getId());
        if(mc_n.isEmpty())
            return;

        String new_n = event.getNewNickname();

        if(new_n == null)
            new_n = event.getMember().getEffectiveName();

        if(!mc_n.equals(Commands.getName(new_n))) {
            try {
                event.getMember().modifyNickname(event.getOldNickname()).queue();
            } catch (HierarchyException | InsufficientPermissionException ignored) {}

            event.getUser().openPrivateChannel().queue((channel) -> channel.sendMessage("Your nickname in " + event.getGuild().getName() + " was reset due to it being incompatible with the username system.").queue());
        }
    }

    @Override
    public void onResume(ResumedEvent event) {
        autoRun.resume(event.getJDA());
        punishmentHandler.resume(event.getJDA(), serverdata);
        //TODO deal with stuff
    }

    @Override
    public void onReconnect(ReconnectedEvent event) {
        autoRun.resume(event.getJDA());
        punishmentHandler.resume(event.getJDA(), serverdata);
        //TODO deal with stuff
    }

    @Override
    public void onDisconnect(@NotNull DisconnectEvent event) {
        autoRun.pause();
        punishmentHandler.pause();
        //TODO deal with stuff
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        autoRun.stop();
        punishmentHandler.stop();
        //TODO stop scheduled punishment stops
        System.out.println("Shutdown!");
    }

    private static class AutoRun {
        private final ScheduledExecutorService scheduler;
        private JDA jda;

        AutoRun(JDA jda) {
            this.jda = jda;

            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime RunHour = now.withHour(6).withMinute(0).withSecond(0);
            if (now.compareTo(RunHour) > 0)
                RunHour = RunHour.plusDays(1);

            Duration duration = Duration.between(now, RunHour);
            long initialDelay = duration.getSeconds();

            scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            autoRunDaily();
                        } catch (Exception ex) {
                            ex.printStackTrace(); //or logger would be better
                        }
                    },
                    initialDelay,
                    TimeUnit.DAYS.toSeconds(1),
                    TimeUnit.SECONDS);
        }

        public void stop() {
            try {
                scheduler.shutdownNow(); //TODO deal with this better somehow
            } catch (Exception ignored) {}
        }

        public void pause() {
            try {
                scheduler.wait();
            } catch (InterruptedException ignored) {}
        }

        public void resume(JDA jda) {
            this.jda = jda;
            scheduler.notifyAll();
        }

        /**
         * This method gets called daily and handles the daily username and weekly leaderboard updating.
         */
        private void autoRunDaily() {
            boolean weekly = false;
            if (ZonedDateTime.now().getDayOfWeek().equals(DayOfWeek.SUNDAY))
                weekly = true;

            for (Guild guild : jda.getGuilds()) {
                TextChannel channel;
                try {
                    channel = guild.getTextChannelById(serverdata.getLogChannelID(guild.getId()));
                } catch (IllegalArgumentException e) {
                    channel = null;
                }

                if (channel != null)
                    channel.sendMessage("Daily update in progress...").complete();

                Commands.updateNames(channel, userdata, guild);

                if (weekly)
                    Commands.updateLeaderboards(channel, leaderboards, serverdata, userdata, guild);
            }
        }
    }
}
