package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.ServerData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class Command {
    public final String name;
    public final String[] aliases;
    public final String usage;
    public final String description;
    public final Permission[] permissions;
    public final boolean devCommand;
    public final boolean adminCommand;
    public final boolean moderatorCommand;

    public Command(String name, String[] aliases, String usage, String description, Permission[] permissions, boolean devCommand, boolean adminCommand, boolean moderatorCommand) {
        this.name = name;
        this.aliases = aliases;
        this.usage = usage;
        this.description = description;
        this.permissions = permissions;
        this.devCommand = devCommand;
        this.adminCommand = adminCommand;
        this.moderatorCommand = moderatorCommand;
    }

    /**
     * Run the command.
     *
     * @param event A {@link CommandEvent CommandEvent} containing all necessary information.
     */
    protected abstract void execute(CommandEvent event);

    /**
     * Check all necessary details and run the command.
     *
     * @param event The message event of the command.
     */
    public void run(MessageReceivedEvent event) {
        if (!event.isFromType(ChannelType.TEXT))
            return;

        if (event.getMessage().isWebhookMessage())
            return;

        CommandEvent commandEvent = new CommandEvent(event);

        if (commandEvent.sender.getUser().isBot())
            return;

        if (!commandEvent.guild.getSelfMember().hasPermission(commandEvent.channel, Permission.MESSAGE_WRITE))
            return;

        if (!commandEvent.guild.getSelfMember().hasPermission(commandEvent.channel, Permission.MESSAGE_EMBED_LINKS)) {
            commandEvent.channel.sendMessage("Please give me the Embed Links permission to run commands.").queue();
            return;
        }

        if (allowedUser(commandEvent.sender)) {
            LinkedList<Permission> missingPerms = missingPerms(commandEvent.channel);

            if (missingPerms.isEmpty())
                execute(commandEvent);
            else {
                commandEvent.channel.sendMessage(new EmbedBuilder().setColor(CommandUtils.ERROR_COLOR)
                        .setTitle("**To use this command please give me the following permissions:**")
                        .setDescription(missingPerms.stream().map(p -> "• " + p.getName()).collect(Collectors.joining("\n")))
                        .build()).queue();
            }
        }
    }

    /**
     * Send the help message for this command to the specified channel.
     *
     * @param channel A {@link TextChannel channel} to send the message to.
     */
    public void sendHelpMessage(TextChannel channel) {
        EmbedBuilder eb = new EmbedBuilder().setColor(CommandUtils.DEFAULT_COLOR)
                .setTitle("!" + name + " info:")
                .addField("Usage:", "``" + usage + "``", false);
        if (aliases.length != 0)
            eb.addField("Aliases:", String.join(", ", aliases), false);
        eb.addField("Allowed users:", (devCommand ? "bot dev" : (adminCommand ? "admins" : (moderatorCommand ? "moderators" : "anyone"))), false);
        eb.addField("Description:", description, false);
        if (permissions.length != 0)
            eb.addField("Required permissions:", Arrays.stream(permissions).map(Permission::getName).collect(Collectors.joining(", ")), false);
        channel.sendMessage(eb.build()).queue();
    }

    /**
     * Checks whether a member has permissions to run this command.
     *
     * @param member The {@link Member member} to check.
     * @return True, if the member can use this command.
     */
    protected boolean allowedUser(Member member) {
        if (devCommand)
            return member.getIdLong() == 470696578403794967L;

        if (adminCommand)
            return member.hasPermission(Permission.ADMINISTRATOR);

        if (moderatorCommand) {
            if (member.hasPermission(Permission.ADMINISTRATOR))
                return true;

            List<Long> roles = member.getRoles().stream().map(ISnowflake::getIdLong).collect(Collectors.toList());
            Set<Long> modroles = ServerData.getModRoles(member.getGuild().getIdLong());

            for (long id : roles) {
                if (modroles.contains(id))
                    return true;
            }
            return false;
        }

        return true;
    }

    /**
     * Checks if the provided name is this this command (case-insensitive).
     *
     * @param name The name to check. Example: "help".
     * @return True, if this command has this name.
     */
    public boolean isCommand(String name) {
        name = name.toLowerCase();

        return (this.name.equals(name) || Arrays.asList(aliases).contains(name));
    }

    /**
     * Returns a list containing all permissions the command is missing in a channel to run.
     *
     * @param channel The channel to check.
     * @return A list containing the missing permissions.
     */
    protected LinkedList<Permission> missingPerms(TextChannel channel) {
        return CommandUtils.missingPerms(channel, permissions);
    }
}
