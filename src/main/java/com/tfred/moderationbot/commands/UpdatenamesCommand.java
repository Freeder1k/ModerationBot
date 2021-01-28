package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.UserData;
import net.dv8tion.jda.api.Permission;

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
    public void execute(CommandEvent event) {
        event.channel.sendMessage("Updating usernames (please note that the bot cannot change the nicknames of users with a higher role).")
                .queue((ignored) -> UserData.updateNames(event.channel, event.guild, event.sender.getIdLong() == 470696578403794967L));
    }
}
