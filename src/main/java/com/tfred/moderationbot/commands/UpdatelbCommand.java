package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.Leaderboards;
import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;

public class UpdatelbCommand extends Command {
    public UpdatelbCommand() {
        super(
                "updatelb",
                new String[]{"updateleaderboards"},
                "!updatelb",
                "Updates the lb messages.\n" +
                        "This only really does anything if the bot failed to fetch the new leaderboard data " +
                        "or if it failed to edit a leaderboard message during the last weekly update.",
                new Permission[]{},
                false,
                true,
                false
        );
    }

    @Override
    protected void execute(@Nonnull CommandEvent event) {
        Leaderboards.updateLeaderboards(event.channel, event.guild);
    }
}
