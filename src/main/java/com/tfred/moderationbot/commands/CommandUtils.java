package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandUtils {
    public static final int DEFAULT_COLOR = 3603854;
    public static final int SUCCESS_COLOR = 7844437;
    public static final int ERROR_COLOR = 14495300;
    public static final int INFO_COLOR = 3901635;

    /**
     * Send a success message.
     *
     * @param channel The {@link TextChannel channel} to send the message to.
     * @param message The message content.
     */
    public static void sendSuccess(@Nonnull TextChannel channel, @Nonnull String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(SUCCESS_COLOR).setDescription("✅ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    /**
     * Send an error message.
     *
     * @param channel The {@link TextChannel channel} to send the message to.
     * @param message The message content.
     */
    public static void sendError(@Nonnull TextChannel channel, @Nonnull String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(ERROR_COLOR).setDescription("❌ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    /**
     * Send an info message.
     *
     * @param channel The {@link TextChannel channel} to send the message to.
     * @param message The message content.
     */
    public static void sendInfo(@Nonnull TextChannel channel, @Nonnull String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(INFO_COLOR).setDescription("ℹ️ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    /**
     * Send an exception message.
     *
     * @param channel   The {@link TextChannel channel} to send the message to.
     * @param throwable The throwable that should be included in the message.
     */
    public static void sendException(@Nonnull TextChannel channel, @Nonnull Throwable throwable) {
        try {
            channel.sendMessage(new EmbedBuilder()
                    .setColor(ERROR_COLOR)
                    .setTitle("A " + throwable.getClass().getSimpleName() + " occured!")
                    .setDescription(throwable.getMessage()).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("A " + throwable.getClass().getSimpleName() + " occured:\n" + throwable.getMessage()).queue();
        }
    }

    /**
     * Send an exception message to the guilds log channel.
     *
     * @param guild The guild to log to.
     * @param throwable The throwable that should be included in the message.
     */
    public static void logException(@Nonnull Guild guild, @Nonnull Throwable throwable) {
        TextChannel channel = guild.getTextChannelById(ServerData.get(guild.getIdLong()).getLogChannel());
        if(channel != null) {
            if(guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage(new EmbedBuilder()
                        .setColor(ERROR_COLOR)
                        .setTitle("A " + throwable.getClass().getSimpleName() + " occured!")
                        .setDescription(throwable.getMessage()).build()).queue();
        }
    }

    /**
     * Parse the ID of a string. This string can either be the raw ID or a discord mention.
     *
     * @param input The input sting to be parsed.
     * @return The ID in the string or 0 if none could be parsed.
     */
    public static long parseID(@Nonnull String input) {
        if (input.startsWith("\\<"))
            input = input.substring(1);
        if (input.charAt(0) == '<' && input.charAt(input.length() - 1) == '>') {
            input = input.substring(1, input.length() - 1);
            if (input.charAt(0) == '#')
                input = input.substring(1);
            if (input.charAt(0) == '@') {
                input = input.substring(1);
                if (input.charAt(0) == '!')
                    input = input.substring(1);
                else if (input.charAt(0) == '&')
                    input = input.substring(1);
            }
        }
        if (input.isEmpty())
            return 0;
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Returns a member specified by a String (as mention, raw ID or discord tag) or null if none was found.
     *
     * @param guild The guild the member is represented in.
     * @param input The input string.
     * @return Possibly-null Member.
     */
    public static Member parseMember(@Nonnull Guild guild, @Nonnull String input) {
        Member m = guild.getMemberById(parseID(input));
        if (m == null) {
            try {
                m = guild.getMemberByTag(input);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return m;
    }

    /**
     * Get the minecraft name from a nickname.
     *
     * @param nickname The nickname. This should be either "name(minecraft name)" or "minecraft name".
     * @return The minecraft name.
     */
    public static String parseName(@Nonnull String nickname) {
        String name;
        if (nickname.endsWith(")")) {
            Pattern pattern = Pattern.compile(".*\\((.+)\\)$");
            Matcher matcher = pattern.matcher(nickname);
            if (matcher.find())
                name = matcher.group(1);
            else
                name = nickname;
        } else
            name = nickname;

        return name;
    }

    /**
     * Get an easy readable time from ann amount in seconds.
     *
     * @param timeInSec The time in seconds.
     * @return The time in days, hours, minutes, seconds.
     */
    public static String parseTime(long timeInSec) {
        List<String> time = new LinkedList<>();
        time.add(timeInSec / (60 * 60 * 24) + "d");
        time.add((timeInSec / (60 * 60)) % 24 + "h");
        time.add((timeInSec / 60) % 60 + "m");
        time.add(timeInSec % 60 + "s");
        time.removeIf(v -> v.charAt(0) == '0');
        if (time.size() == 0)
            return "0s";
        return String.join(", ", time);
    }

    /**
     * Returns a list containing all specified permissions the bot is missing in a channel.
     *
     * @param channel The channel to check.
     * @return A list containing the missing permissions.
     */
    public static LinkedList<Permission> missingPerms(@Nonnull TextChannel channel, @Nonnull Permission... permissions) {
        LinkedList<Permission> missingPerms = new LinkedList<>();

        for (Permission p : permissions) {
            if (!channel.getGuild().getSelfMember().hasPermission(channel, p))
                missingPerms.add(p);
        }
        return missingPerms;
    }
}
