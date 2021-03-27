package com.tfred.moderationbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;

public class PunishmentInfo extends Command {
    public PunishmentInfo() {
        super(
                "punishmentinfo",
                new String[]{},
                "!punishmentinfo",
                "Show info on punishment lengths.",
                new Permission[]{},
                false,
                false,
                false
        );
    }

    @Override
    public void execute(@Nonnull CommandEvent event) {
        event.channel.sendMessage(new EmbedBuilder()
                .setColor(CommandUtils.DEFAULT_COLOR)
                .setTitle("**Punishment info:**")
                .setDescription("If within a 2-day period after the last punishment ended the length is double the old one.")
                .addField("**Mutes:**\n" +
                                "**Sev 1:**",
                        "First mute: 1 hour.\n" +
                                "Following mutes: 0.75 hours added on to last severity one mute UNLESS within a 7-day period after the most recent severity one mute, in which case it will be + 1.75 hours.",
                        false)
                .addField("**Sev 2:**",
                        "First mute: 2 hours.\n" +
                                "Following mutes: 1.5 hours added on to last severity two mute UNLESS within a 7-day period after the most recent severity two mute, in which case it will be + 3.5 hours.",
                        false)
                .addField("**Sev 3:**",
                        "First mute: 4 hours.\n" +
                                "Following mutes: 3 hours added on to last severity three mute UNLESS within a 14-day period after the most recent severity three mute, in which case it will be + 7 hours.",
                        false)
                .addField("**Sev 4:**",
                        "First mute: 8 hours.\n" +
                                "Following mutes: 6 hours added on to last severity four mute UNLESS within a 35-day period after most recent severity four mute, in which case it will be + 14 hours.",
                        false)
                .addField("**Sev 5:**",
                        "First mute: 1 day.\n" +
                                "Following mutes: 18 hours added on to last severity five mute UNLESS within a 42-day period after the most recent severity four mute, in which case it will be + 42 hours.\n\u200B",
                        false)
                .addField("**Bans**:\n" +
                                "**Sev 1:**",
                        "First ban: 2 weeks.\n" +
                                "Following bans: 2 weeks added on to last severity one ban.",
                        false)
                .addField("**Sev 2:**",
                        "First ban: 45 days.\n" +
                                "Following bans: 45 days added on to last severity two ban.\n\u200B",
                        false)
                .addField("**Sev 3:**",
                        "Permanent ban.\n" +
                                "Due to java integer limits this isn't actually permanent but its a pretty long time.\n\u200B",
                        false)
                .addField("**Channel bans:**",
                        "First punishment: 1 week.\n" +
                                "Following punishments: 1 week added on to last channel ban effecting same channel.\n\u200B",
                        false)
                .addField("**Name punishments:**",
                        "First punishment: 1 week.\n" +
                                "Following punishments: 1 week added on to last name punishment.",
                        false)
                .build()).queue();
    }
}
