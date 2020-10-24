package com.tfred.moderationbot;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

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
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//this isn't coded to be efficient with a lot of servers!
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
        private final String guildID;
        private final List<SingleUser> userList;
        private final int lineNumber;

        SingleGuildUserData(String guildID, List<SingleUser> userList, int lineNumber) {
            this.guildID = guildID;
            this.userList = userList;
            this.lineNumber = lineNumber;
        }

        //Line numbers start at 0
        void updateFile() {
            if(lineNumber == -1)
                return;
            try {
                String data = "";
                data = data.concat(guildID);
                for(SingleUser u: userList) {
                    data = data.concat(" " + u.toString());
                }

                List<String> lines = Files.readAllLines(path);
                if(lineNumber >= lines.size()) {
                    lines.add(data);
                }
                else
                    lines.set(lineNumber, data);
                Files.deleteIfExists(path);
                Files.write(path, lines, StandardOpenOption.CREATE);
            } catch (IOException e) {
                System.out.println("IO error when writing server data!");
            }
        }

        String getUser(String userID) {
            String name = "";
            for(SingleUser u: userList) {
                if(u.userID.equals(userID))
                    name = getName(u.uuid);
            }
            if(name == null || name.equals("!"))
                return "";

            return name;
        }

        //returns 1 if successful, 0 if invalid name, -1 if failed
        int setUser(Member member, String name) {
            String uuid = getUUID(name);
            if(uuid == null)
                return -1;
            if(uuid.equals("!"))
                return 0;

            SingleUser u = new SingleUser(member.getId(), uuid);
            userList.remove(u);
            userList.add(u);

            String s = updateMember(member, getUUID(name));

            if(!s.equals("-1")) {
                updateFile();
                return 1;
            }
            else
                return 0;
        }

        void removeUser(String userID) {
            userList.remove(SingleUser.createDefault(userID));

            updateFile();
        }

        List<String[]> updateGuild(List<Member> members) {
            List<String[]> updated = new LinkedList<>();
            List<String> userIDs = members.stream().map(ISnowflake::getId).collect(Collectors.toList());
            for(int i = 0; i < userList.size(); i++) {
                SingleUser user = userList.get(i);
                int index = userIDs.indexOf(user.userID);
                if(index != -1) {
                    Member member = members.get(index);
                    String res = updateMember(member, user.uuid);
                    if(res.equals("-")) {
                        userList.remove(user);
                        i--;
                        updated.add(new String[]{user.userID, res});
                    }
                    else if(!res.isEmpty())
                        updated.add(new String[]{user.userID, res});
                }
            }
            updateFile();
            return updated;
        }

        //returns "" if nothing changed, new username if changed, "-" if entry should be deleted, "e" if other error
        private String updateMember(Member m, String uuid) {
            try {
                String currentName = getName(uuid);

                if (currentName == null)
                    return "e";

                if (currentName.equals("!")) {
                    return "-1";
                }

                if (m.getNickname() == null) {
                    if(m.getEffectiveName().equals(currentName))
                        return "";
                    else {
                        m.modifyNickname(currentName).queue();
                        return currentName;
                    }
                }

                String nickname;
                if (m.getNickname().endsWith(")")) {
                    Pattern pattern = Pattern.compile("\\((.*?)\\)");
                    Matcher matcher = pattern.matcher(m.getNickname());
                    if (matcher.find())
                        nickname = matcher.group(1);
                    else
                        nickname = "";
                } else
                    nickname = m.getNickname();

                if (!(nickname.equals(currentName))) {
                    if (m.getNickname().endsWith(")")) {
                        Pattern pattern = Pattern.compile(".*?\\(");
                        Matcher matcher = pattern.matcher(m.getNickname());
                        if (matcher.find()) {
                            m.modifyNickname(matcher.group() + currentName + ")").queue();
                        }
                    }
                    else
                        m.modifyNickname(currentName).queue();
                    return currentName;
                }
                return "";
            } catch(HierarchyException | InsufficientPermissionException e) {
                return "";
            }
        }

        static String getName(String uuid) {
            try {
                URL urlForGetRequest = new URL("https://api.mojang.com/user/profiles/" + uuid + "/names");

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
                    if(responseCode == HttpURLConnection.HTTP_BAD_REQUEST || responseCode == HttpURLConnection.HTTP_NO_CONTENT) //name invalid
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

    /**
     * Represents the bots saved user data for each server. This contains member IDs and their associated minecraft uuid.
     */
    public UserData() {
        List<String> list;
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

            String guildId = data[0];
            for(int i = 1; i < data.length; i++) {
                String[] user = data[i].split(":");

                userList.add(new SingleUser(user[0], user[1]));
            }
            userData.add(new SingleGuildUserData(guildId, userList, c));
            c++;
        }

        System.out.println("Finished reading saved user data.");
    }

    /**
     * Returns the specified member's associated minecraft ign in the specified guild or an empty string if this member doesn't have one or there was an error.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID
     *          The specified {@link Member member's} ID.
     * @return
     *          possibly-empty string containing a minecraft ign.
     * @see SingleGuildUserData#getUser(String)
     */
    public String getUserInGuild(String guildID, String userID) {
        for(SingleGuildUserData data: userData) {
            if(data.guildID.equals(guildID)) {
                return data.getUser(userID);
            }
        }
        return "";
    }

    /**
     * Sets a specified member's associated minecraft ign in a specified guild.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param member
     *          The specified {@link Member member}.
     * @param name
     *          This minecraft ign to be associated with this member.
     * @return
     *          1 if successful, 0 if invalid mc name, -1 if an error occurred
     */
    public int setUserInGuild(String guildID, Member member, String name) {
        for(SingleGuildUserData data: userData) {
            if(data.guildID.equals(guildID)) {
                return data.setUser(member, name);

            }
        }
        SingleGuildUserData newGuild = new SingleGuildUserData(guildID, new ArrayList<>(), userData.size());
        userData.add(newGuild);
        return newGuild.setUser(member, name);
    }

    /**
     * Removes a specified user's associated minecraft ign in a specified guild.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID
     *          The specified {@link Member member's} ID.
     */
    public void removeUserFromGuild(String guildID, String userID) {
        for(SingleGuildUserData data: userData) {
            if(data.guildID.equals(guildID))
                data.removeUser(userID);
        }
    }

    /**
     * Updates the nicknames of members in a specified guild if they changed their minecraft ign.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param members
     *          A list of all the members to be checked.
     * @return
     *          possibly-empty list of all updated user's IDs and their new username. If the associated uuid doesn't exist anymore the second value is "-" and if an error occurred it's "e".
     */
    public List<String[]> updateGuildUserData(String guildID, List<Member> members) {
        for(SingleGuildUserData data: userData) {
            if(data.guildID.equals(guildID))
                return data.updateGuild(members);
        }
        return new ArrayList<>();
    }

    /**
     * Returns a list of all user's IDs who have an associated minecraft account.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          possibly-empty list of user IDs.
     */
    public List<String> getGuildSavedUserIds(String guildID) {
        List<String> output = new ArrayList<>();
        for(SingleGuildUserData data: userData) {
            if(data.guildID.equals(guildID)) {
                for (SingleUser u: data.userList) {
                    output.add(u.userID);
                }
                return output;
            }
        }
        return output;
    }

    /**
     * Returns a list of all saved minecraft uuids.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return
     *          possibly-empty list of minecraft uuids.
     */
    public List<String> getGuildSavedUuids(String guildID) {
        List<String> output = new ArrayList<>();
        for(SingleGuildUserData data: userData) {
            if(data.guildID.equals(guildID)) {
                for (SingleUser u: data.userList) {
                    output.add(u.uuid);
                }
                return output;
            }
        }
        return output;
    }

    /**
     * Returns the user ID associated with a minecraft uuid.
     *
     * @param guildID
     *          The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param uuid
     *          The uuid to search the associated user of.
     * @return
     *          possibly-empty string containing a {@link Member member's} ID.
     */
    public String getGuildSavedUuidUserID(String guildID, String uuid) {
        for(SingleGuildUserData data: userData) {
            if(data.guildID.equals(guildID)) {
                for (SingleUser u: data.userList) {
                    if(u.uuid.equals(uuid))
                        return u.userID;
                }
            }
        }
        return "";
    }
}
