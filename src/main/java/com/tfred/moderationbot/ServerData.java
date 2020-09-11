package com.tfred.moderationbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ServerData {
    private static class SingleServer {
        private static final SingleServer defaultS = new SingleServer(null, true, null);

        String id;
        boolean noSalt;
        List<String> modRoleIDs;

        SingleServer(String id, boolean noSalt, List<String> modRoleIDs) {
            this.id = id;
            this.noSalt = noSalt;
            this.modRoleIDs = modRoleIDs;
        }

        static SingleServer createDefault(String id) {
            return new SingleServer(id, defaultS.noSalt, new ArrayList<>());
        }

        @Override
        public String toString() {
            String noSaltS;
            if(noSalt)
                noSaltS = "1";
            else
                noSaltS = "0";

            return id + " " + noSaltS + " " + modRoleIDs.toString().replaceAll(" ", "");
        }
    }

    private final List<SingleServer> serverList = new ArrayList<>();
    private static final Path path = Paths.get("servers.data");



    public ServerData() {
        List<String> list;
        try {
            list = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("IO error when reading server data!");
            return;
        }

        //Line format: <Server ID> noSalt(0 or 1) [modroles] [lbMessages(lbNum:channelID:messageID)] //TODO implement lbMessages

        for(String s: list) {
            String[] data = s.split(" ");

            List<String> modRoleIDs = new ArrayList<>();

            if(data.length >= 3) {
                String[] ids = (data[2].substring(1, data[2].length() - 1)).split(",");
                Collections.addAll(modRoleIDs, ids);
            }

            SingleServer x = new SingleServer(data[0], data[1].equals("1"), modRoleIDs);

            serverList.add(x);
        }

        System.out.println("Finished reading saved server data.");
    }

    //Line numbers start at 0
    public void updateFile() {
        try {
            Files.deleteIfExists(path);
            for(SingleServer s: serverList) {
                Files.write(path, s.toString().getBytes(), StandardOpenOption.CREATE);
            }
        } catch (IOException e) {
            System.out.println("IO error when writing server data!");
        }
    }

    public boolean isNoSalt(String serverID) {
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID))
                return s.noSalt;
        }
        return SingleServer.defaultS.noSalt;
    }

    public void setNoSalt(String serverID, boolean noSalt) {
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID)) {
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
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID))
                return s.modRoleIDs.contains(roleID);
        }
        return false;
    }

    public void addModRole(String serverID, String roleID) {
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID)) {
                if(!(s.modRoleIDs.contains(roleID))) {
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
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID)) {
                s.modRoleIDs.remove(roleID);
                updateFile();
            }
        }
    }

    public List<String> getModRoles(String serverID) {
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID)) {
                return s.modRoleIDs;
            }
        }
        return new ArrayList<>();
    }
}
