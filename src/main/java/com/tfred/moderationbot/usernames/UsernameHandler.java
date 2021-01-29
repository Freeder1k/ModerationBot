package com.tfred.moderationbot.usernames;

import com.tfred.moderationbot.ServerData;
import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class UsernameHandler {
    private static final HashMap<Long, UsernameHandler> allUsernameHandlers = new HashMap<>();

    public final long guildID;
    private final UsernameData usernameData;
    private final AtomicLong lastUpdatenamesTime = new AtomicLong(0);

    private final ConcurrentLinkedQueue<Long> ignoredUsers = new ConcurrentLinkedQueue<>();

    private UsernameHandler(long guildID) {
        this.guildID = guildID;
        usernameData = new UsernameData(guildID);
    }

    /**
     * Get the user data of a guild.
     *
     * @param guildID The guild ID of the guild.
     * @return The userdata.
     */
    public static UsernameHandler get(long guildID) {
        if (allUsernameHandlers.containsKey(guildID))
            return allUsernameHandlers.get(guildID);

        else {
            synchronized (UsernameData.class) {
                if (allUsernameHandlers.containsKey(guildID))
                    return allUsernameHandlers.get(guildID);

                UsernameHandler newHandler = new UsernameHandler(guildID);
                allUsernameHandlers.put(guildID, newHandler);
                return newHandler;
            }
        }
    }

    /**
     * Returns the specified member's associated minecraft ign or an empty string if this member doesn't have one or there was an error.
     *
     * @param userID The specified {@link Member member's} ID.
     * @return possibly-empty string containing a minecraft ign.
     */
    public String getUsername(long userID) throws RateLimitException {
        return usernameData.getUsername(userID);
    }

    /**
     * Get the latest minecraft name of a uuid and the previous name if one exists.
     *
     * @param userID The user ID to get the name for.
     * @return The name(s) or {} if the users uuid doesn't exist anymore or {"e"} if an error occured.
     */
    public String[] getUsernames(long userID) throws RateLimitException {
        return usernameData.getUsernames(userID);
    }

    /**
     * Returns the user ID associated with a minecraft uuid.
     *
     * @param uuid The uuid to search the associated user of.
     * @return The {@link Member member's} ID or 0 if none was found.
     */
    public long getUserID(String uuid) {
        return usernameData.getUserID(uuid);
    }

    /**
     * Sets a specified member's associated minecraft ign.
     *
     * @param member The specified {@link Member member}.
     * @param name   This minecraft ign to be associated with this member.
     * @return a String containing the case-corrected username or "e" if an error occured or an empty string if that name doesnt exist
     */
    public String setUuid(Member member, String name) throws RateLimitException {
        return usernameData.setUuid(member, name);
    }

    /**
     * Removes a specified user's associated minecraft ign.
     *
     * @param userID The specified {@link Member member's} ID.
     */
    public void removeUser(long userID) {
        usernameData.removeUser(userID);
    }

    /**
     * Returns an unmodifiable list of all user's IDs who have an associated minecraft account.
     *
     * @return possibly-empty list of user IDs.
     */
    public List<Long> getSavedUserIDs() {
        return usernameData.getSavedUserIDs();
    }

    /**
     * Returns an unmodifiable list of all saved minecraft uuids.
     *
     * @return possibly-empty list of minecraft uuids.
     */
    public List<String> getSavedUuids() {
        return usernameData.getSavedUuids();
    }

    /**
     * Updates the nicknames of users in this guild.
     *
     * @param channel               The {@link TextChannel channel} to send the results to (can be null).
     * @param jda                   The bot jda.
     * @param bypassTimeRestriction If the 10 min cooldown should be bypassed.
     */
    public void updateNames(TextChannel channel, JDA jda, boolean bypassTimeRestriction) {
        Guild guild = jda.getGuildById(guildID);
        if (guild == null)
            return;
        if (!bypassTimeRestriction) {
            if (System.currentTimeMillis() - lastUpdatenamesTime.getAndUpdate(x -> System.currentTimeMillis() - x < 600000 ? x : System.currentTimeMillis()) < 600000) {
                CommandUtils.sendError(channel, "This command can only be ran once every 10 minutes per guild!");
                return;
            }
        }

        Map<Long, String[]> changed;
        try {
            changed = usernameData.updateNames(guild.getMembers());
        } catch (RateLimitException e) {
            CommandUtils.sendError(channel, e.getMessage());
            return;
        }
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Updated Users:")
                .setColor(CommandUtils.defaultColor);

        if (changed.size() == 1)
            eb.setTitle("Updated User:");

        if (!changed.isEmpty()) {
            StringBuilder updated = new StringBuilder();
            StringBuilder removed = new StringBuilder();
            StringBuilder failed = new StringBuilder();
            for (Map.Entry<Long, String[]> entry : changed.entrySet()) {
                String[] s = entry.getValue();
                if (s[0].equals("-"))
                    removed.append("<@").append(entry.getKey()).append(">\n");
                else if (s[0].equals("e"))
                    failed.append("<@").append(entry.getKey()).append(">\n");
                else
                    updated.append("<@").append(entry.getKey()).append(">").append(" (").append(s[0]).append(" -> ").append(s[1]).append(")\n");
            }
            if (updated.length() != 0) {
                if (updated.length() > 2048)
                    eb.setDescription(updated.length() + " users were updated.");
                else
                    eb.setDescription(updated.toString());
            } else
                eb.setDescription("No users were updated.");
            if (removed.length() != 0) {
                if (removed.length() < 1024)
                    eb.addField("\nRemoved Users:", removed.toString(), false);
                else
                    eb.addField("", removed.length() + " users were removed from the system.", false);
            }
            if (failed.length() != 0) {
                if (failed.length() < 1024)
                    eb.addField("\nFailed Users:", failed.toString(), false);
                else
                    eb.addField("", "Updating failed on " + failed.length() + " users.", false);
            }

            TextChannel namechannel = guild.getTextChannelById(ServerData.get(guildID).getNameChannel());
            try {
                if ((namechannel != null) && (!namechannel.equals(channel)))
                    namechannel.sendMessage(eb.build()).queue();
            } catch (InsufficientPermissionException ignored) {
            }
        } else {
            eb.setDescription("No users were updated.");
        }

        if (channel != null)
            channel.sendMessage(eb.build()).queue();
    }

    /**
     * Check if a members nickname changed. Resets it or sends an update message in the name/log channel if necessary.
     *
     * @param old_n The old nickname of the member.
     * @param new_n The new nickname.
     * @param m     The member to check. If this member is from the wrong guild the method returns without doing anything.
     */
    public void checkNameChange(String old_n, String new_n, Member m) throws RateLimitException {
        Guild g = m.getGuild();
        if (g.getIdLong() != guildID)
            return;

        String[] mc_n = getUsernames(m.getIdLong());
        if (mc_n.length == 0)
            return;

        ServerData serverData = ServerData.get(g.getIdLong());

        String newMcName, oldMcName;
        if (mc_n.length == 1) {
            if (mc_n[0].equals("e") || mc_n[0].equals("-"))
                return;
            newMcName = mc_n[0];
            oldMcName = null;
        } else {
            oldMcName = mc_n[0];
            newMcName = mc_n[1];
        }

        if (new_n == null)
            new_n = m.getEffectiveName();
        new_n = CommandUtils.parseName(new_n);

        if (!new_n.equals(newMcName)) {
            try {
                ignoredUsers.add(m.getIdLong());
                if (CommandUtils.parseName(old_n).equals(newMcName))
                    m.modifyNickname(old_n).queue();
                else
                    m.modifyNickname(newMcName).queue();
            } catch (HierarchyException | InsufficientPermissionException ignored) {
                ignoredUsers.remove(m.getIdLong());
            }

            m.getUser().openPrivateChannel().queue((channel) -> channel.sendMessage("Your nickname in " + g.getName() + " was reset due to it being incompatible with the username system.").queue());
        } else {
            if (old_n == null)
                old_n = m.getUser().getName();
            old_n = CommandUtils.parseName(old_n);
            if (!old_n.equals(new_n)) {
                TextChannel namechannel = g.getTextChannelById(serverData.getNameChannel());
                if (namechannel == null) {
                    namechannel = g.getTextChannelById(serverData.getLogChannel());
                }
                if (namechannel != null)
                    if (oldMcName != null)
                        if (oldMcName.equals(old_n))
                            namechannel.sendMessage(new EmbedBuilder().setColor(CommandUtils.defaultColor).setTitle("Updated user:").setDescription(m.getAsMention() + " (" + old_n + "->" + new_n + ")").build()).queue();
            }
        }
    }

    public void addIgnoredUser(long userID) {
        ignoredUsers.add(userID);
    }

    public void removeIgnoredUser(long userID) {
        ignoredUsers.remove(userID);
    }

    public boolean isIgnoredUser(long userID) {
        return ignoredUsers.contains(userID);
    }
}
