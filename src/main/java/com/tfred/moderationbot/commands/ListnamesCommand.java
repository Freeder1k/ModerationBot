package com.tfred.moderationbot.commands;

import com.tfred.moderationbot.usernames.UsernameHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

import java.util.LinkedList;
import java.util.List;

import static com.tfred.moderationbot.commands.CommandUtils.*;

public class ListnamesCommand extends Command {
    public ListnamesCommand() {
        super(
                "listnames",
                new String[]{},
                "!listnames [role]",
                "List members separated by whether they have an associated minecraft username or not.\n" +
                        "If a role is specified this will only list users with that role.",
                new Permission[]{},
                false,
                false,
                true
        );
    }

    @Override
    protected void execute(CommandEvent event) {
        List<Member> members;
        TextChannel channel = event.channel;
        Guild guild = event.guild;

        if (event.args.length > 1) {
            Role r = guild.getRoleById(parseID(event.args[1]));
            if (r == null)
                members = guild.getMembers();
            else
                members = guild.getMembersWithRoles(r);
        } else
            members = guild.getMembers();

        List<String> parts1 = new LinkedList<>();    //all members that are saved
        List<String> parts2 = new LinkedList<>();    //all members that arent saved
        StringBuilder current1 = new StringBuilder();
        StringBuilder current2 = new StringBuilder();
        int length1 = 12;
        int length2 = 33;

        List<Long> ids = UsernameHandler.get(guild.getIdLong()).getSavedUserIDs();
        for (Member m : members) {
            String mention = '\n' + m.getAsMention();
            if (ids.contains(m.getUser().getIdLong())) {
                if (current1.length() + mention.length() > 1024) {
                    parts1.add(current1.toString());
                    length1 += current1.length();
                    current1.setLength(0);
                }
                current1.append(mention);
            } else {
                if (current2.length() + mention.length() > 1024) {
                    parts2.add(current2.toString());
                    length2 += current2.length();
                    current2.setLength(0);
                }
                current2.append(mention);
            }
        }
        parts1.add(current1.toString());
        parts2.add(current2.toString());

        if (length1 > 6000 || length2 > 6000) {
            sendError(channel, "Too many members to display! Ask <@470696578403794967> to change something.");
            return;
        }

        EmbedBuilder eb1 = new EmbedBuilder().setColor(DEFAULT_COLOR);
        if (parts1.isEmpty())
            parts1.add("None.");
        eb1.addField("Added users:", parts1.remove(0), true);
        for (String s : parts1) {
            eb1.addField("", s, true);
        }
        channel.sendMessage(eb1.build()).queue();

        EmbedBuilder eb2 = new EmbedBuilder().setColor(DEFAULT_COLOR);
        if (parts2.isEmpty())
            parts2.add("None.");
        eb2.addField("Users who haven't been added yet:", parts2.remove(0), true);
        for (String s : parts2) {
            eb2.addField("", s, true);
        }
        channel.sendMessage(eb2.build()).queue();
    }
}
