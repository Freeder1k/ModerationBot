package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class CommandListener extends ListenerAdapter {
    private final ArrayList<Command> commands;

    /**
     * Create a new listener for commands. The commands to listen for have to be added with addCommand(command).
     */
    public CommandListener() {
        commands = new ArrayList<>();
    }

    /**
     * Add a {@link Command command} to listen for.
     *
     * @param command The command to add.
     * @return this.
     */
    public CommandListener addCommand(@Nonnull Command command) {
        commands.add(command);
        return this;
    }

    /**
     * Get all added commands.
     */
    public Command[] getCommands() {
        return commands.toArray(new Command[]{});
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        String msg = event.getMessage().getContentRaw();

        if (!msg.isEmpty() && msg.charAt(0) == '!') {
            for (Command command : commands) {
                int space = msg.indexOf(' ');
                String commandName;
                if (space > 0)
                    commandName = msg.substring(1, space);
                else
                    commandName = msg.substring(1);

                if (command.isCommand(commandName)) {
                    CompletableFuture.runAsync(() -> command.run(event));
                    return;
                }
            }
        }
    }
}
