package com.tfred.moderationbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//this all isn't coded to be efficient with a lot of servers!
public class UserData {
    private static class SingleUser {
        private static final SingleUser defaultUser = new SingleUser(null, null);

        String userID, uuid;

        SingleUser(String userID, String uuid) {
            this.userID = userID;
            this.uuid = uuid;
        }

        static SingleUser createDefault(String id) {
            return new SingleUser(id, defaultUser.uuid);
        }

        @Override
        public String toString() {
            return userID + ":" + uuid;
        }

        //DO NOT CHANGE! Important for addUser, removeUser and editUser!!!
        @Override
        public boolean equals(Object o) {
            if(!(o instanceof SingleUser))
                return false;

            return userID.equals(((SingleUser) o).userID);
        }
    }

    private static class SingleGuildUserData {
        private final Guild guild;
        private final List<SingleUser> userList;
        private final int lineNumber;

        SingleGuildUserData(Guild guild, List<SingleUser> userList, int lineNumber) {
            this.guild = guild;
            this.userList = userList;
            this.lineNumber = lineNumber;

            updateGuild();
        }

        //Line numbers start at 0
        void updateFile() {
            if(lineNumber == -1)
                return;
            try {
                String data = "";
                data = data.concat(guild.getId());
                for(SingleUser u: userList) {
                    data = data.concat(" " + u.toString());
                }

                List<String> lines = Files.readAllLines(path);
                if(lineNumber >= lines.size()) {
                    lines.add(data);
                }
                else
                    lines.set(lineNumber, data);
                Files.delete(path);
                Files.write(path, lines, StandardOpenOption.CREATE);
            } catch (IOException e) {
                System.out.println("IO error when writing server data!");
            }
        }

        void setUser(String userID, String name) {
            String uuid = getUUID(name);
            if((uuid == null) || (uuid.equals("!")))
                return;

            SingleUser u = new SingleUser(userID, uuid);
            userList.remove(u);
            userList.add(u);

            updateMember(guild.retrieveMemberById(userID).complete(), getUUID(name));

            updateFile();
        }

        void removeUser(String userID) {
            userList.remove(SingleUser.createDefault(userID));

            updateFile();
        }

        void updateGuild() {
            for(int i = 0; i < userList.size(); i++) {
                SingleUser user = userList.get(i);
                Member member = guild.retrieveMemberById(user.userID).complete();
                if(member == null) {
                    userList.remove(user);
                    i--;
                }
                else {
                    if(updateMember(member, user.uuid) == 2) {
                        userList.remove(user);
                        i--;
                    }
                }
            }
            updateFile();
        }

        //returns 1 if succesful, 2 if entry should be deleted, 0 if other error
        private int updateMember(Member m, String uuid) {
            String currentName = getName(uuid);

            if(currentName == null)
                return 0;

            if(currentName.equals("!")) {
                return 2;
            }

            if(m.getNickname() == null) {
                m.modifyNickname(currentName).queue();
                return 1;
            }

            String nickname;
            if(m.getNickname().endsWith(")")) {
                Pattern pattern = Pattern.compile("\\((.*?)\\)");
                Matcher matcher = pattern.matcher(m.getNickname());
                if(matcher.find())
                    nickname = matcher.group();
                else
                    nickname = "";
            }
            else
                nickname = m.getNickname();

            if(!(nickname.equals(currentName))) {
                if(m.getNickname().endsWith(")")) {
                    Pattern pattern = Pattern.compile(".*?\\(");
                    Matcher matcher = pattern.matcher(m.getNickname());
                    if(matcher.find()) {
                        m.modifyNickname(matcher.group() + currentName + ")").queue();
                        return 1;
                    }
                }
                m.modifyNickname(currentName).queue();
            }
            return 1;
        }

        static String getName(String uuid) {
            try {
                URL urlForGetRequest = new URL("https://api.mojang.com/user/profiles/" + uuid + "/names");
                //String readLine = null;

                HttpURLConnection connection = (HttpURLConnection) urlForGetRequest.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String response = in.readLine();
                    in.close();

                    Pattern p = Pattern.compile(".*\"name\":\"(.*?)\"");
                    Matcher m = p.matcher(response);
                    if (m.find())
                        return m.group(1);
                    else
                        return "!"; //UUID invalid
                } else {
                    System.out.println("GET NOT WORKED");
                    if(responseCode == HttpURLConnection.HTTP_BAD_REQUEST) //UUID invalid
                        return "!";
                }
                return null;
            } catch (IOException  e) {
                return null;
            }
        }

        static String getUUID(String name) {
            try {
                URL urlForGetRequest = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
                //String readLine = null;

                HttpURLConnection connection = (HttpURLConnection) urlForGetRequest.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String response = in.readLine();
                    in.close();

                    Pattern p = Pattern.compile(".*\"id\":\"(.*?)\"");
                    Matcher m = p.matcher(response);
                    if (m.find())
                        return m.group(1);
                    else
                        return "!"; //name invalid
                } else {
                    System.out.println("GET NOT WORKED");
                    if(responseCode == HttpURLConnection.HTTP_BAD_REQUEST) //name invalid
                        return "!";
                }
                return null;
            } catch (IOException  e) {
                return null;
            }
        }
    }

    private final List<SingleGuildUserData> userData = new ArrayList<>();
    private static final Path path = Paths.get("users.data");
    private final JDA jda;

    public UserData(JDA jda) {
        List<String> list;
        this.jda = jda;
        try {
            list = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("IO error when reading user data!");
            return;
        }

        //Line format: serverID userid:uuid userid2:uuid2...

        int c = 0;
        for(String s: list) {
            List<SingleUser> userList = new ArrayList<>();

            String[] data = s.split(" ");

            //TODO existing data of guild that was joined again
            Guild guild = jda.getGuildById(data[0]);
            if (guild != null) {
                for(int i = 1; i < data.length; i++) {
                    String[] user = data[i].split(":");

                    userList.add(new SingleUser(user[0], user[1]));
                }
                userData.add(new SingleGuildUserData(guild, userList, c));
            }
            c++;
        }
    }

    //TODO error handling stuff
    public void setUserInGuild(String guildID, String userID, String name) {
        for(SingleGuildUserData data: userData) {
            if(data.guild.getId().equals(guildID)) {
                data.setUser(userID, name);
                return;
            }
        }
        //add new guild to the userdata if it doesn't exist yet
        Guild guild = jda.getGuildById(guildID);

        if(guild != null) {
            SingleGuildUserData newGuild = new SingleGuildUserData(guild, new ArrayList<>(), userData.size());
            userData.add(newGuild);
            newGuild.setUser(userID, name);
        }
    }

    public void removeUserFromGuild(String guildID, String userID) {
        for(SingleGuildUserData data: userData) {
            if(data.guild.getId().equals(guildID))
                data.removeUser(userID);
        }
    }

    public void updateGuildUserData(String guildID) {
        for(SingleGuildUserData data: userData) {
            if(data.guild.getId().equals(guildID))
                data.updateGuild();
        }
    }
}
