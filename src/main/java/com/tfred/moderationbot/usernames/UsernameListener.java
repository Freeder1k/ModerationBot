package com.tfred.moderationbot.usernames;

import com.tfred.moderationbot.BotScheduler;
import com.tfred.moderationbot.ServerData;
import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UsernameListener extends ListenerAdapter {
    private final BotScheduler scheduler;
    private final AtomicBoolean isNameCheckSchedulerActive = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<ScheduledNameCheck> scheduledNameChecks = new ConcurrentLinkedQueue<>();

    /**
     * Create a new UsernameListener that listens for actions related to usernames.
     */
    public UsernameListener(BotScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Checks whether a user that joins a server is saved in the username system and if they are it
     * updates their nickname accordingly and sends a message to the join channel.
     *
     * @param event An event containing information about a new join.
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member m = event.getMember();
        Guild guild = event.getGuild();
        UsernameHandler usernameHandler = UsernameHandler.get(guild.getIdLong());

        TextChannel channel = guild.getTextChannelById(ServerData.get(guild.getIdLong()).getJoinChannel());
        boolean canWrite = true;
        if (channel == null)
            canWrite = false;
        else if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_WRITE, Permission.VIEW_CHANNEL, Permission.MESSAGE_EMBED_LINKS))
            canWrite = false;

        String mcName;
        try {
            mcName = usernameHandler.getUsername(m.getIdLong());
        } catch (RateLimitException e) {
            if (canWrite)
                CommandUtils.sendError(channel, "Failed to get <@" + m.getId() + ">'s minecraft name: " + e.getMessage());
            scheduleNameCheck(null, m, e.timeLeft + 10);
            return;
        }

        if (mcName.isEmpty())
            return;

        if (canWrite)
            CommandUtils.sendInfo(channel, "<@" + m.getId() + ">'s minecraft name is saved as " + mcName.replaceAll("_", "\\_") + ".");

        try {
            usernameHandler.addIgnoredUser(m.getIdLong());
            m.modifyNickname(mcName).queue();
        } catch (HierarchyException | InsufficientPermissionException ignored) {
            usernameHandler.removeIgnoredUser(m.getIdLong());
        }
    }

    /**
     * When a user updates their nickname the bot tests to see if their new nickname is compliant with the username system.
     *
     * @param event An event containing information about a nickname change.
     */
    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
        Member m = event.getMember();
        long mID = m.getIdLong();
        if (!UsernameHandler.get(event.getGuild().getIdLong()).isIgnoredUser(mID))
            checkNameChange(event.getOldNickname(), event.getNewNickname(), m);
        else
            UsernameHandler.get(event.getGuild().getIdLong()).removeIgnoredUser(mID);
    }

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event) {
        for (Guild g : event.getJDA().getGuilds()) {
            Member m = g.getMember(event.getUser());
            if (m != null)
                if (m.getNickname() == null)
                    checkNameChange(event.getOldName(), event.getNewName(), m);
        }
    }

    private void checkNameChange(String old_n, String new_n, Member m) {
        try {
            UsernameHandler.get(m.getGuild().getIdLong()).checkNameChange(old_n, new_n, m);
        } catch (RateLimitException e) {
            scheduleNameCheck(old_n, m, e.timeLeft + 10);
        }
    }

    /**
     * Schedule a members nickname to be checked once the rate limit timer is over.
     *
     * @param old_n       The old nickname.
     * @param m           The member to check.
     * @param timeSeconds The time in seconds until the rate limit is over.
     */
    private void scheduleNameCheck(String old_n, Member m, int timeSeconds) {
        if (isNameCheckSchedulerActive.compareAndSet(false, true)) {
            JDA jda = m.getJDA();
            scheduler.schedule(() -> {
                isNameCheckSchedulerActive.set(false);

                while (!scheduledNameChecks.isEmpty()) {
                    ScheduledNameCheck snc = scheduledNameChecks.poll();

                    Guild g = jda.getGuildById(snc.guildID);
                    if (g != null) {
                        Member member = g.getMemberById(snc.memberID);
                        if (member != null) {
                            try {
                                UsernameHandler.get(member.getGuild().getIdLong()).checkNameChange(snc.old_n, member.getEffectiveName(), member);
                            } catch (RateLimitException e) {
                                scheduleNameCheck(old_n, m, e.timeLeft + 10);
                                break;
                            }
                        }
                    }
                }
            }, timeSeconds, TimeUnit.SECONDS);
        }

        ScheduledNameCheck newSnc = new ScheduledNameCheck(old_n, m.getGuild().getIdLong(), m.getIdLong());
        if (!scheduledNameChecks.contains(newSnc))
            scheduledNameChecks.add(newSnc);
    }

    private static class ScheduledNameCheck {
        public final String old_n;
        public final long guildID;
        public final long memberID;

        public ScheduledNameCheck(String old_n, long guildID, long memberID) {
            this.old_n = old_n;
            this.guildID = guildID;
            this.memberID = memberID;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ScheduledNameCheck))
                return false;
            ScheduledNameCheck snc = (ScheduledNameCheck) o;
            return snc.guildID == guildID && snc.memberID == memberID;
        }
    }
}
