package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.usernames.UsernameHandler;
import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;

public class UpdatenamesCommand extends Command {
    public UpdatenamesCommand() {
        super(
                "updatenames",
                new String[]{},
                "!updatenames",
                "Update the nickname of users with an associated minecraft name if it was changed.",
                new Permission[]{Permission.NICKNAME_MANAGE},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        event.channel.sendMessage("Updating usernames (please note that the bot cannot change the nicknames of users with a higher role).")
                .queue((ignored) -> UsernameHandler.get(event.guild.getIdLong())
                        .updateAllNames(event.channel, event.event.getJDA(), event.sender.getIdLong() == 470696578403794967L));
    }
}
