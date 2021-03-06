package com.tfred.moderationbot;

import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ServerData {
    private static final HashMap<Long, SoftReference<ServerData>> allServerData = new HashMap<>();
    private static final HashMap<Long, Set<Long>> allServerModRoles = new HashMap<>();

    public final long guildID;
    private final long[][] lbMessages = new long[][]{{0, 0}, {0, 0}, {0, 0}}; //channelID:messageID x3
    private Set<Long> modRoles;
    private long memberRole = 0;
    private long mutedRole = 0;
    private long noNicknameRole = 0;
    private long logChannel = 0;
    private long joinChannel = 0;
    private long punishmentChannel = 0;
    private long joinMsgChannel = 0;
    private long nameChannel = 0;
    private int currentPunishmentID = 0;
    private String joinMsg = "";

    private ServerData(long guildID) {
        this.guildID = guildID;

        // Read the server data
        Path filepath = Paths.get("serverdata/" + guildID + ".serverdata");
        if (Files.exists(filepath)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(filepath);
            } catch (IOException e) {
                System.out.println("IO error when reading server data!");
                return;
            }
            try {
                String[] temp = lines.get(0).split(" ");
                try {
                    for (int i = 0; i < 3; i++) {
                        String[] temp2 = temp[i].split(":");
                        lbMessages[i][0] = Long.parseLong(temp2[0]);
                        lbMessages[i][1] = Long.parseLong(temp2[1]);
                    }
                } catch (IndexOutOfBoundsException ignored) {
                    System.out.println("Formatting error in " + guildID + ".serverdata!");
                }

                modRoles = allServerModRoles.get(guildID);
                if (modRoles == null) {
                    modRoles = Collections.synchronizedSet(new HashSet<>(4));
                    for (String id : lines.get(1).split(" ")) {
                        if (!id.equals(""))
                            modRoles.add(Long.parseLong(id));
                    }
                    allServerModRoles.put(guildID, modRoles);
                }

                memberRole = Long.parseLong(lines.get(2));

                mutedRole = Long.parseLong(lines.get(3));

                noNicknameRole = Long.parseLong(lines.get(4));

                logChannel = Long.parseLong(lines.get(5));

                joinChannel = Long.parseLong(lines.get(6));

                punishmentChannel = Long.parseLong(lines.get(7));

                joinMsgChannel = Long.parseLong(lines.get(8));

                nameChannel = Long.parseLong(lines.get(9));

                currentPunishmentID = Integer.parseInt(lines.get(10));

                joinMsg = StringEscapeUtils.unescapeJava(lines.get(11));
            } catch (IndexOutOfBoundsException e) {/*this can be ignored*/} catch (NumberFormatException e) {
                System.out.println("Formatting error in " + guildID + ".serverdata!");
                e.printStackTrace();
            }
        } else {
            modRoles = Collections.synchronizedSet(new HashSet<>(4));
            if (!Files.isDirectory(Paths.get("serverdata"))) {
                try {
                    Files.createDirectory(Paths.get("serverdata"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                Files.createFile(filepath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the server data of a guild.
     *
     * @param guildID The {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return The server data.
     */
    public static ServerData get(long guildID) {
        if (allServerData.containsKey(guildID)) {
            ServerData serverData = allServerData.get(guildID).get();
            if (serverData != null)
                return serverData;
        }
        synchronized (ServerData.class) {
            if (allServerData.containsKey(guildID)) {
                ServerData serverData = allServerData.get(guildID).get();
                if (serverData != null)
                    return serverData;
            }
            ServerData newSD = new ServerData(guildID);
            allServerData.put(guildID, new SoftReference<>(newSD));
            return newSD;
        }
    }

    public static Set<Long> getModRoles(long guildID) {
        Set<Long> roles = allServerModRoles.get(guildID);
        if (roles != null)
            return Collections.unmodifiableSet(roles);

        return ServerData.get(guildID).getModRoles();
    }

    private synchronized void updateFile() {
        try {
            List<String> lines = new ArrayList<>(11);
            lines.add(String.valueOf(lbMessages[0][0]) + ':' + lbMessages[0][1] + ' ' +
                    lbMessages[1][0] + ':' + lbMessages[1][1] + ' ' +
                    lbMessages[2][0] + ':' + lbMessages[2][1]);
            lines.add(modRoles.stream().map(Object::toString).collect(Collectors.joining(" ")));
            lines.add(String.valueOf(memberRole));
            lines.add(String.valueOf(mutedRole));
            lines.add(String.valueOf(noNicknameRole));
            lines.add(String.valueOf(logChannel));
            lines.add(String.valueOf(joinChannel));
            lines.add(String.valueOf(punishmentChannel));
            lines.add(String.valueOf(joinMsgChannel));
            lines.add(String.valueOf(nameChannel));
            lines.add(String.valueOf(currentPunishmentID));
            lines.add(StringEscapeUtils.escapeJava(joinMsg));

            Files.write(Paths.get("serverdata/" + guildID + ".serverdata"), lines);
        } catch (IOException e) {
            System.out.println("IO error when updating server data!");
        }
    }

    public Set<Long> getModRoles() {
        return Collections.unmodifiableSet(modRoles);
    }

    public void addModRole(long modRole) {
        modRoles.add(modRole);
        updateFile();
    }

    public void removeModRole(long modRole) {
        modRoles.remove(modRole);
        updateFile();
    }

    public long getMemberRole() {
        return memberRole;
    }

    public synchronized void setMemberRole(long memberRole) {
        this.memberRole = memberRole;
        updateFile();
    }

    public long getMutedRole() {
        return mutedRole;
    }

    public synchronized void setMutedRole(long mutedRole) {
        this.mutedRole = mutedRole;
        updateFile();
    }

    public long getNoNicknameRole() {
        return noNicknameRole;
    }

    public synchronized void setNoNicknameRole(long noNicknameRole) {
        this.noNicknameRole = noNicknameRole;
        updateFile();
    }

    public long[][] getAllLbMessages() {
        // Create a new array so the private one can't be changed
        return new long[][]{{lbMessages[0][0], lbMessages[0][1]}, {lbMessages[1][0], lbMessages[1][1]}, {lbMessages[2][0], lbMessages[2][1]}};
    }

    /**
     * Set the leaderboard message for a specific board.
     *
     * @param board     The board ID. Throws an IndexOutOfBoundsException if this isn't between 0 and 2
     * @param channelID The ID of the {@link net.dv8tion.jda.api.entities.TextChannel channel} the message is in.
     * @param messageID The ID of the {@link net.dv8tion.jda.api.entities.Message message}.
     */
    public synchronized void setLbMessage(int board, long channelID, long messageID) {
        lbMessages[board][0] = channelID;
        lbMessages[board][1] = messageID;
        updateFile();
    }

    public synchronized long getLogChannel() {
        return logChannel;
    }

    public void setLogChannel(long logChannel) {
        this.logChannel = logChannel;
        updateFile();
    }

    public long getJoinChannel() {
        return joinChannel;
    }

    public synchronized void setJoinChannel(long joinChannel) {
        this.joinChannel = joinChannel;
        updateFile();
    }

    public long getPunishmentChannel() {
        return punishmentChannel;
    }

    public synchronized void setPunishmentChannel(long punishmentChannel) {
        this.punishmentChannel = punishmentChannel;
        updateFile();
    }

    public long getJoinMsgChannel() {
        return joinMsgChannel;
    }

    public synchronized void setJoinMsgChannel(long joinMsgChannel) {
        this.joinMsgChannel = joinMsgChannel;
        updateFile();
    }

    public long getNameChannel() {
        return nameChannel;
    }

    public synchronized void setNameChannel(long nameChannel) {
        this.nameChannel = nameChannel;
        updateFile();
    }

    /**
     * Increments the current punishment ID by 1 and returns this value.
     *
     * @return The next punishment ID.
     */
    public synchronized int getNextPunishmentID() {
        currentPunishmentID++;
        updateFile();
        return currentPunishmentID;
    }

    public String getJoinMsg() {
        return joinMsg;
    }

    public synchronized void setJoinMsg(String joinMsg) {
        this.joinMsg = joinMsg;
        updateFile();
    }
}
