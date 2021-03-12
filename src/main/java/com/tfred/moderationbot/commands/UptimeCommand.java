package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.ModerationBot;
import net.dv8tion.jda.api.Permission;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

public class UptimeCommand extends Command {
    private final ModerationBot bot;

    public UptimeCommand(@Nonnull ModerationBot bot) {
        super(
                "uptime",
                new String[]{},
                "!uptime",
                "Show the bots uptime",
                new Permission[]{},
                false,
                false,
                false
        );

        this.bot = bot;
    }

    @Override
    public void execute(@NotNull CommandEvent event) {
        long time = System.currentTimeMillis() - bot.startTime;
        CommandUtils.sendInfo(event.channel, "The bot has been up for " + time + "ms or " + CommandUtils.parseTime(time / 1000));
    }
}
