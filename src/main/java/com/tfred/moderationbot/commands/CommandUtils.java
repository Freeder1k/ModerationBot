package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandUtils {
    public static final int defaultColor = 3603854;
    public static final int successColor = 7844437;
    public static final int errorColor = 14495300;
    public static final int infoColor = 3901635;

    /**
     * Send a success message.
     *
     * @param channel The {@link TextChannel channel} to send the message to.
     * @param message The message content.
     */
    public static void sendSuccess(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(successColor).setDescription("✅ " + message).build()).queue();
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
    public static void sendError(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(errorColor).setDescription("❌ " + message).build()).queue();
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
    public static void sendInfo(TextChannel channel, String message) {
        try {
            channel.sendMessage(new EmbedBuilder().setColor(infoColor).setDescription("ℹ️ " + message).build()).queue();
        } catch (InsufficientPermissionException e) {
            if (e.getPermission().equals(Permission.MESSAGE_EMBED_LINKS))
                channel.sendMessage("Please give me the embed links permission.\n" + message).queue();
        }
    }

    /**
     * Parse the ID of a string. This string can either be the raw ID or a discord mention.
     *
     * @param input The input sting to be parsed.
     * @return The ID in the string or 0 if none could be parsed.
     */
    public static long parseID(String input) {
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
    public static Member parseMember(Guild guild, String input) {
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
    public static String parseName(String nickname) {
        String name;
        if (nickname.endsWith(")")) {
            Pattern pattern = Pattern.compile("\\((.*?)\\)");
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
     * @param channel
     *          The channel to check.
     * @return
     *          A list containing the missing permissions.
     */
    public static LinkedList<Permission> missingPerms(TextChannel channel, Permission... permissions) {
        LinkedList<Permission> missingPerms = new LinkedList<>();

        for (Permission p : permissions) {
            if (!channel.getGuild().getSelfMember().hasPermission(channel, p))
                missingPerms.add(p);
        }
        return missingPerms;
    }
}
