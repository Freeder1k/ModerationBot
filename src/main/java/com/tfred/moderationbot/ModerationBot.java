package com.tfred.moderationbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ModerationBot extends ListenerAdapter
{
    private static ServerData serverdata;

    public static void main(String[] args)
    {
        System.out.println("Hello world but Frederik was here!");

        //We construct a builder for a BOT account. If we wanted to use a CLIENT account
        // we would use AccountType.CLIENT
        try
        {
            JDA jda = JDABuilder.createDefault(System.getenv("TOKEN")) // The token of the account that is logging in.
                    .addEventListeners(new ModerationBot())   // An instance of a class that will handle events.
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
        }

        //Set up server data
        serverdata = new ServerData();

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
        JDA jda = event.getJDA();                       //JDA, the core of the api.
        long responseNumber = event.getResponseNumber();//The amount of discord events that JDA has received since the last reconnect.

        //Event specific information
        User author = event.getAuthor();                //The user that sent the message
        Message message = event.getMessage();           //The message that was received.
        MessageChannel channel = event.getChannel();    //This is the MessageChannel that the message was sent to.
        //  This could be a TextChannel, PrivateChannel, or Group!

        String msg = message.getContentDisplay();              //This returns a human readable version of the Message. Similar to
        // what you would see in the client.

        boolean bot = author.isBot();                    //This boolean is useful to determine if the User that
        // sent the Message is a BOT or not!

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
                name = member.getEffectiveName();       //This will either use the Member's nickname if they have one,
            }                                           // otherwise it will default to their username. (User#getName())

            System.out.printf("(%s)[%s]<%s>: %s\n", guild.getName(), textChannel.getName(), name, msg);
        } else if (event.isFromType(ChannelType.PRIVATE)) //If this message was sent to a PrivateChannel
        {
            //The message was sent in a PrivateChannel.
            //In this example we don't directly use the privateChannel, however, be sure, there are uses for it!
            PrivateChannel privateChannel = event.getPrivateChannel();

            System.out.printf("[PRIV]<%s>: %s\n", author.getName(), msg);
        }

        //Now that you have a grasp on the things that you might see in an event, specifically MessageReceivedEvent,
        // we will look at sending / responding to messages!
        //This will be an extremely simplified example of command processing.

        //Remember, in all of these .equals checks it is actually comparing
        // message.getContentDisplay().equals, which is comparing a string to a string.
        // If you did message.equals() it will fail because you would be comparing a Message to a String!
        if (msg.equals("!help")) {
            //This will send a message, "pong!", by constructing a RestAction and "queueing" the action with the Requester.
            // By calling queue(), we send the Request to the Requester which will send it to discord. Using queue() or any
            // of its different forms will handle ratelimiting for you automatically!

            channel.sendMessage("Help:\n-``!delreaction <emoji>``: delete all reactions with a specified emoji.\n-``!nosalt``: toggle no salt mode.").queue();
        }

        else if (msg.startsWith("!delreaction")) {
            if(message.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                Guild guild = event.getGuild();
                Member selfMember = guild.getSelfMember();

                if (!selfMember.hasPermission(Permission.MESSAGE_MANAGE)) {
                    channel.sendMessage("Please make sure to give me the manage messages permission!").queue();
                    return; //We jump out of the method instead of using cascading if/else
                }

                if(msg.length() < 14) {
                    channel.sendMessage("Please provide an emoji!").complete();
                    return;
                }

                String emoji = msg.substring(13);
                //List<Emote> emotes = message.getEmotes();

                channel.sendMessage("Removing reactions...").complete();

                //channel.sendTyping().complete();

                for(Message m:message.getChannel().getHistory().retrievePast(100).complete()) {
                    //m.deleteReaction(emoji).queue();
                    //m.removeReaction(emoji).queue();
                    m.clearReactions(emoji).complete();
                    //m.addReaction(emoji).queue();
                }

                /*
                for(Message m: channel.getHistoryBefore(message, 100).complete().getRetrievedHistory()) {
                    m.addReaction(emoji);
                }
                */

                //message.addReaction(emoji).queue();

                //channel.sendMessage(emoji).queue();

                channel.sendMessage("Finished removing reactions with " + emoji + ".").queue();
            }
        }

        else if (msg.equals("!nosalt")) {
            String guildID = event.getGuild().getId();
            if((message.getMember().hasPermission(Permission.ADMINISTRATOR))) {
                if (serverdata.isNoSalt(guildID)) {
                    serverdata.setNoSalt(guildID, false);
                    channel.sendMessage("No salt mode disabled.").queue();
                } else {
                    serverdata.setNoSalt(guildID, true);
                    channel.sendMessage("No salt mode enabled!").queue();
                }
            }
        }

        else if (msg.contains("\uD83E\uDDC2") || msg.contains("⏰")) {
            //Deletes messages with salt emoji
            Guild guild = event.getGuild();
            if((! message.getAuthor().isBot()) && serverdata.isNoSalt(guild.getId())) {
                Member selfMember = guild.getSelfMember();

                if (selfMember.hasPermission(Permission.MESSAGE_MANAGE))
                    message.delete().queue();
            }
        }
    }
}
