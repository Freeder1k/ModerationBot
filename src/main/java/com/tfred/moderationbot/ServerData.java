package com.tfred.moderationbot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ServerData {
    private static class SingleServer {
        String id;
        boolean noSalt;

        SingleServer(String id, String noSalt) {
            this.id = id;
            this.noSalt = !noSalt.equals("0");
        }

        SingleServer(String id, boolean noSalt) {
            this.id = id;
            this.noSalt = noSalt;
        }
    }

    private List<SingleServer> serverList = new ArrayList<SingleServer>();
    private final SingleServer defaultS = new SingleServer(null, true);
    Path path = Paths.get("servers.data");

    public ServerData() {
        List<String> list;
        try {
            list = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("IO error when reading server data!");
            return;
        }

        //Line format: <Server ID> noSalt(0 or 1)

        for(String s: list) {
            String[] data = s.split(" ");
            SingleServer x = new SingleServer(data[0], data[1]);

            serverList.add(x);
        }
    }

    public boolean isNoSalt(String serverID) {
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID))
                return s.noSalt;
        }
        return defaultS.noSalt;
    }

    public void setNoSalt(String serverID, boolean noSalt) {
        for(SingleServer s: serverList) {
            if(s.id.equals(serverID)) {
                s.noSalt = noSalt;
                return;
            }
        }
        try {
            String noSaltV;
            if(noSalt)
                noSaltV = "1";
            else
                noSaltV = "0";

            String content = serverID + " " + noSaltV;

            Files.write(path, content.getBytes(), StandardOpenOption.CREATE);

            serverList.add(new SingleServer(serverID, noSaltV));
        } catch (IOException e) {
            System.out.println("IO error when writing server data!");
        }
    }
}
