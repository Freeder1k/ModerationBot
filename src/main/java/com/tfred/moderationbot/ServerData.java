package com.tfred.moderationbot;

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

        SingleServer(String id, boolean noSalt, List<String> modRoleIDs, List<LbData> lbData) {
            this.id = id;
            this.noSalt = noSalt;
            this.modRoleIDs = modRoleIDs;
            this.lbData = lbData;
        }

        static SingleServer createDefault(String id) {
            List<LbData> lbData = new ArrayList<>(3);
            for (int i = 0; i < 3; i++)
                lbData.add(new LbData(-1, null, null));

            return new SingleServer(id, true, new ArrayList<>(), lbData);
        }

        @Override
        public String toString() {
            String noSaltS;
            if (noSalt)
                noSaltS = "1";
            else
                noSaltS = "0";

            return id + " " + noSaltS + " &" + String.join(",", modRoleIDs) + " &" + lbData.stream().map(LbData::toString).collect(Collectors.joining(","));
        }
    }

    private final List<SingleServer> serverList = new ArrayList<>();
    private static final Path path = Paths.get("servers.data");


    public ServerData() {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("IO error when reading server data!");
            return;
        }

        //Line format: <Server ID> noSalt(0 or 1) &modroles &lbMessages(boardNum:channelID:messageID)

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
            } else
                for (int i = 0; i < 3; i++)
                    lbData.add(new LbData(-1, null, null));

            SingleServer x = new SingleServer(data[0], data[1].equals("1"), modRoleIDs, lbData);

            serverList.add(x);
        }

        System.out.println("Finished reading saved server data.");
    }

    //Line numbers start at 0
    public void updateFile() {
        try {
            Files.deleteIfExists(path);
            for (SingleServer s : serverList) {
                Files.write(path, s.toString().getBytes(), StandardOpenOption.CREATE);
            }
        } catch (IOException e) {
            System.out.println("IO error when writing server data!");
        }
    }

    public boolean isNoSalt(String serverID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID))
                return s.noSalt;
        }
        return true;
    }

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

    public boolean isModRole(String serverID, String roleID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID))
                return s.modRoleIDs.contains(roleID);
        }
        return false;
    }

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

    public void removeModRole(String serverID, String roleID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                s.modRoleIDs.remove(roleID);
                updateFile();
            }
        }
    }

    public List<String> getModRoles(String serverID) {
        for (SingleServer s : serverList) {
            if (s.id.equals(serverID)) {
                return s.modRoleIDs;
            }
        }
        return new ArrayList<>();
    }

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

    public String[] getLbData(String serverID, int board) {
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

    public String[][] getAllLbData(String serverID) {
        String[][] temp = new String[3][2];
        for(int i = 0; i < 3; i++) {
            temp[i] = getLbData(serverID, i);
        }
        return temp;
    }
}
