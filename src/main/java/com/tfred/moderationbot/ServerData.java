package com.tfred.moderationbot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ServerData {
    private static class LbData {
        int board;
        String channelID;
        String messageID;

        LbData(int board, String channelID, String messageID) {
            this.board = board;
            this.channelID = channelID;
            this.messageID = messageID;
        }

        //input format: "boardNum:channelID:messageID"
        static LbData create(String data) {
            if (data.equals(""))
                return new LbData(-1, null, null);
            String[] temp = data.split(":");
            return new LbData(Integer.parseInt(temp[0]), temp[1], temp[2]);
        }

        @Override
        public String toString() {
            if (this.board == -1)
                return "";

            return board + ":" + channelID + ":" + messageID;
        }
    }

    private static class SingleServer {
        String id;
        boolean noSalt;
        List<String> modRoleIDs;
        List<LbData> lbData;
        String logChannelID, joinChannelID;

        SingleServer(String id, boolean noSalt, List<String> modRoleIDs, List<LbData> lbData, String logChannelID, String joinChannelID) {
            this.id = id;
            this.noSalt = noSalt;
            this.modRoleIDs = modRoleIDs;
            this.lbData = lbData;
            this.logChannelID = logChannelID;
            this.joinChannelID = joinChannelID;
        }

        static SingleServer createDefault(String id) {
            List<LbData> lbData = new ArrayList<>(3);
            for (int i = 0; i < 3; i++)
                lbData.add(new LbData(-1, null, null));

            return new SingleServer(id, false, new ArrayList<>(), lbData, "", "");
        }

        @Override
        public String toString() {
            String noSaltS;
            if (noSalt)
                noSaltS = "1";
            else
                noSaltS = "0";

            return id + " " + noSaltS + " &" + String.join(",", modRoleIDs) + " &" + lbData.stream().map(LbData::toString).collect(Collectors.joining(",")) + " &" + logChannelID + " &" + joinChannelID;
        }
    }

    private final List<SingleServer> serverList = new ArrayList<>();
    private static final Path path = Paths.get("servers.data");

    /**
     * Represents the bots saved server data. This contains mostly data like moderator roles and log channels.
     */
    public ServerData() {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("IO error when reading server data!");
            return;
        }

        //Line format: <Server ID> noSalt(0 or 1) &modroles &lbMessages(boardNum:channelID:messageID) &logchannel &joinchannel

        for (String s : lines) {
            String[] data = s.split(" ");

            List<String> modRoleIDs = new ArrayList<>();
            String[] ids = data[2].substring(1).split(",");
            Collections.addAll(modRoleIDs, ids);

            List<LbData> lbData = new ArrayList<>(3);
            if (data.length >= 4) {
                String[] temp = data[3].substring(1).split(",");
                for (String a : temp) {
                    lbData.add(LbData.create(a));
                }
                if (lbData.size() != 3)
                    while (lbData.size() < 3)
                        lbData.add(new LbData(-1, null, null));
            } else {
                for (int i = 0; i < 3; i++) {
                    lbData.add(new LbData(-1, null, null));
                }
            }

            String logChannelID = "";
            if(data.length >= 5)
                logChannelID = data[4].substring(1);

            String joinChannelID = "";
            if(data.length >= 6)
                logChannelID = data[5].substring(1);


            SingleServer x = new SingleServer(data[0], data[1].equals("1"), modRoleIDs, lbData, logChannelID, joinChannelID);

            serverList.add(x);
        }

        System.out.println("Finished reading saved server data.");
    }

    //Line numbers start at 0
    private void updateFile() {
        try {
            Files.deleteIfExists(path);
            Files.write(path, serverList.stream().map(SingleServer::toString).collect(Collectors.toList()), StandardOpenOption.CREATE);
        } catch (IOException e) {
            System.out.println("IO error when writing server data!");
        }
    }

    /**
     * Returns true if the specified guild has no salt mode enabled. Default is false.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          True if the specified guild has no salt mode enabled.
     */
    public boolean isNoSalt(String serverID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID))
                return s.noSalt;
        }
        return false;
    }

    /**
     * Enable/Disable no salt mode in the specified guild.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param noSalt
     *          True to enable, false to disable.
     */
    public void setNoSalt(String serverID, boolean noSalt) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                s.noSalt = noSalt;
                updateFile();
                return;
            }
        }
        try {
            SingleServer s_new = SingleServer.createDefault(serverID);
            s_new.noSalt = noSalt;
            Files.write(path, s_new.toString().getBytes(), StandardOpenOption.CREATE);

            serverList.add(s_new);
        } catch (IOException e) {
            System.out.println("IO error when writing server data!");
        }
    }

    /**
     * Adds a role to the list of moderator roles in a specified server. Members with moderator roles can use moderator only commands.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param roleID
     *          The specified {@link net.dv8tion.jda.api.entities.Role role's} ID.
     */
    public void addModRole(String serverID, String roleID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                if (!(s.modRoleIDs.contains(roleID))) {
                    s.modRoleIDs.add(roleID);
                    updateFile();
                }
                return;
            }
        }
        try {
            SingleServer s_new = SingleServer.createDefault(serverID);
            s_new.modRoleIDs.add(roleID);
            Files.write(path, s_new.toString().getBytes(), StandardOpenOption.CREATE);

            serverList.add(s_new);
        } catch (IOException e) {
            System.out.println("IO error when writing server data!");
        }
    }

    /**
     * Remove a role from the list of moderator roles in a specified server.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param roleID
     *          The specified {@link net.dv8tion.jda.api.entities.Role role's} ID.
     */
    public void removeModRole(String serverID, String roleID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                s.modRoleIDs.remove(roleID);
                updateFile();
            }
        }
    }

    /**
     * Returns a list of all moderator roles in a specified server.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          possibly-empty {@link List<String> list} of all moderator role IDs in the specified server.
     */
    public List<String> getModRoles(String serverID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                return s.modRoleIDs;
            }
        }
        return new ArrayList<>();
    }

    /**
     * Set a specified message to show a leaderboard in a specified server. This message will be edited in the future when {@link Commands#updateLeaderboards(TextChannel, Leaderboards, ServerData, UserData, Guild) Commands.upateLeaderboards} is called.
     * 
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param board
     *          Which board to use.
     *          0: hider wins, 1: hunter wins, 2: kills.
     * @param channelID
     *          The {@link TextChannel channel's} ID in which the message is.
     * @param messageID
     *          The {@link net.dv8tion.jda.api.entities.Message message's} ID.
     */
    public void setLbData(String serverID, int board, String channelID, String messageID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                LbData lb = s.lbData.get(board);
                lb.board = board;
                lb.channelID = channelID;
                lb.messageID = messageID;
                updateFile();
                return;
            }
        }
        try {
            SingleServer s_new = SingleServer.createDefault(serverID);
            LbData lb = s_new.lbData.get(board);
            lb.board = board;
            lb.channelID = channelID;
            lb.messageID = messageID;
            Files.write(path, s_new.toString().getBytes(), StandardOpenOption.CREATE);

            serverList.add(s_new);
        } catch (IOException e) {
            System.out.println("IO error when writing server data!");
        }
    }

    /**
     * Returns an array with the data pertaining to the leaderboard messages in a specified server.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          An array containing 3 arrays of Strings containing the daa. Each string array contains a channel ID and a message ID or is null if it hasn't been set yet with {@link ServerData#setLbData(String, int, String, String) ServerData.setLbData}.
     */
    public String[][] getAllLbData(String serverID) {
        String[][] temp = new String[3][2];
        for(int i = 0; i < 3; i++) {
            temp[i] = getLbData(serverID, i);
        }
        return temp;
    }

    private String[] getLbData(String serverID, int board) {
        String channelID = null;
        String messageID = null;
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                channelID = s.lbData.get(board).channelID;
                messageID = s.lbData.get(board).messageID;
            }
        }
        if(channelID == null || messageID == null)
            return null;
        return new String[]{channelID, messageID};
    }

    /**
     * Set the specified channel to be the log channel for the specified server.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param logChannelID
     *          The specified {@link TextChannel log channel's} ID.
     */
    public void setLogChannelID(String serverID, String logChannelID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                s.logChannelID = logChannelID;
                updateFile();
            }
        }
    }

    /**
     * Get the ID of the log channel in a specified server.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          The {@link TextChannel log channel's} ID or "0" if none is set.
     */
    public String getLogChannelID(String serverID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                if(!s.logChannelID.equals(""))
                    return s.logChannelID;
            }
        }
        return "0";
    }

    /**
     * Set the specified channel to be the join channel for the specified server.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param joinChannelID
     *          The specified {@link TextChannel log channel's} ID.
     */
    public void setJoinChannelID(String serverID, String joinChannelID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                s.joinChannelID = joinChannelID;
                updateFile();
            }
        }
    }

    /**
     * Get the ID of the join channel in a specified server.
     *
     * @param serverID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          The {@link TextChannel log channel's} ID or "0" if none is set.
     */
    public String getJoinChannelID(String serverID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                if(!s.joinChannelID.equals(""))
                    return s.joinChannelID;
            }
        }
        return "0";
    }
}
