package com.tfred.moderationbot;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserData {
    private static final HashMap<Long, UserData> allUserData = new HashMap<>();
    private final LoadingCache<Long, String[]> usernameCache;
    private final HashMap<Long, String> uuidMap;
    public long guildID;

    private UserData(long guildID) {
        this.guildID = guildID;
        usernameCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<Long, String[]>() {
                            @Override
                            public String[] load(@NotNull Long userID) {
                                String uuid = uuidMap.get(userID);
                                if (uuid == null)
                                    return new String[]{};
                                return getName(uuid);
                            }
                        }
                );

        // Initialize the user data
        uuidMap = new HashMap<>();
        Path filepath = Paths.get("userdata/" + guildID + ".userdata");
        if (Files.exists(filepath)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(filepath);
            } catch (IOException e) {
                System.out.println("IO error when reading user data!");
                return;
            }
            for (String line : lines) {
                String[] data = line.split(" ");
                try {
                    uuidMap.put(Long.parseLong(data[0]), data[1]);
                } catch (IndexOutOfBoundsException ignored) {
                    System.out.println("Formatting error in file " + guildID + ".userdata!");
                }
            }
        } else {
            if (!Files.isDirectory(Paths.get("userdata"))) {
                try {
                    Files.createDirectory(Paths.get("userdata"));
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

    public static UserData get(long guildID) {
        if (allUserData.containsKey(guildID))
            return allUserData.get(guildID);

        else {
            UserData newUD = new UserData(guildID);
            allUserData.put(guildID, newUD);
            return newUD;
        }
    }

    /**
     * Get the latest minecraft name of a uuid and the previous name if one exists.
     *
     * @param uuid The uuid to get the name for.
     * @return The name(s) or {"-1"} if the uuid doesn't exist or {"e"} if an error occured.
     */
    public static String[] getName(String uuid) {
        try {
            URL urlForGetRequest = new URL("https://api.mojang.com/user/profiles/" + uuid + "/names");

            HttpURLConnection connection = (HttpURLConnection) urlForGetRequest.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = in.readLine();
                in.close();

                Matcher m = Pattern.compile("\"name\":\"(.*?)\"").matcher(response);
                List<String> matches = new ArrayList<>();
                while (m.find())
                    matches.add(m.group(1));
                if (matches.isEmpty()) // uuid invalid
                    return new String[]{"-"};
                else if (matches.size() == 1)
                    return new String[]{matches.get(0)};
                else
                    return new String[]{matches.get(matches.size() - 2), matches.get(matches.size() - 1)};
            } else {
                System.out.println("GET NOT WORKED");
                if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) //UUID invalid
                    return new String[]{"!"};
            }
            return new String[]{"e"};
        } catch (IOException ignored) {
            return new String[]{"e"};
        }
    }

    public static String getUUID(String name) {
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
                if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST || responseCode == HttpURLConnection.HTTP_NO_CONTENT) //name invalid
                    return "!";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Update a members nickname if they changed their minecraft name.
     *
     * @param m The {@link Member member} to update.
     * @return an empty array if nothing changed or their previous nickname didn't match their old username, {oldUsername, newUsername} if changed, {"-"} if entry should be deleted, {"e"} if other error.
     */
    private String[] updateMember(Member m) {
        try {
            String[] nameChange = usernameCache.get(m.getIdLong());
            if (nameChange.length == 1) {
                if (nameChange[0].equals("-") || nameChange[0].equals("e"))
                    return nameChange;
                nameChange = new String[]{"none", nameChange[0]};
            }
            String newName = nameChange[1];

            String name = m.getEffectiveName();
            if (name.endsWith(")")) {
                Pattern pattern = Pattern.compile("\\((.*?)\\)");
                Matcher matcher = pattern.matcher(name);
                if (matcher.find())
                    name = matcher.group(1);
                else
                    name = "";
            }

            // If the old nickname wasn't that persons previous minecraft ign then the name should be updated but an empty array returned.
            boolean hide = !name.equals(nameChange[0]);

            if (!(name.equals(newName))) {
                if (m.getEffectiveName().endsWith(")")) {
                    Pattern pattern = Pattern.compile(".*?\\(");
                    Matcher matcher = pattern.matcher(m.getEffectiveName());
                    if (matcher.find()) {
                        String newNick = matcher.group() + newName + ")";
                        if (newNick.length() > 32)
                            newNick = newName;
                        try {
                            ModerationBot.ignoredUsers.add(m.getId());
                            m.modifyNickname(newNick).queue((ignored) -> ModerationBot.ignoredUsers.remove(m.getId()));
                        } catch (HierarchyException | InsufficientPermissionException ignored) {
                            ModerationBot.ignoredUsers.remove(m.getId());
                        }
                    }
                } else {
                    try {
                        ModerationBot.ignoredUsers.add(m.getId());
                        m.modifyNickname(newName).queue((ignored) -> ModerationBot.ignoredUsers.remove(m.getId()));
                    } catch (HierarchyException | InsufficientPermissionException ignored) {
                        ModerationBot.ignoredUsers.remove(m.getId());
                    }
                }
                if (hide)
                    return new String[]{};
                else
                    return nameChange;
            }
            return new String[]{};
        } catch (HierarchyException | InsufficientPermissionException | ExecutionException e) {
            return new String[]{};
        }
    }

    /**
     * Returns the specified member's associated minecraft ign or an empty string if this member doesn't have one or there was an error.
     *
     * @param userID The specified {@link Member member's} ID.
     * @return possibly-empty string containing a minecraft ign.
     */
    public String getUsername(long userID) {
        try {
            String[] names = usernameCache.get(userID);
            if (names.length == 1) {
                if (names[0].equals("-") || names[0].equals("e"))
                    return "";
                else
                    return names[0];
            } else
                return names[1];
        } catch (ExecutionException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Returns the user ID associated with a minecraft uuid.
     *
     * @param uuid The uuid to search the associated user of.
     * @return The {@link Member member's} ID or 0 if none was found.
     */
    public long getUserID(String uuid) {
        for (Map.Entry<Long, String> entry : uuidMap.entrySet()) {
            if (entry.getValue().equals(uuid))
                return entry.getKey();
        }
        return 0;
    }

    /**
     * Sets a specified member's associated minecraft ign.
     *
     * @param member The specified {@link Member member}.
     * @param name   This minecraft ign to be associated with this member.
     * @return 1 if successful, 0 if invalid mc name, -1 if an error occurred
     */
    public int setUuid(Member member, String name) {
        long userID = member.getIdLong();
        removeUser(userID
        );
        String uuid = getUUID(name);
        if (uuid == null)
            return -1;
        if (uuid.equals("!"))
            return 0;

        uuidMap.put(userID, uuid);

        updateMember(member); // Return value can be ignored since nothing should go wrong

        try {
            Files.write(Paths.get("userdata/" + guildID + ".userdata"), (userID + " " + uuid).getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        return 1;
    }

    /**
     * Removes a specified user's associated minecraft ign.
     *
     * @param userID The specified {@link Member member's} ID.
     */
    public void removeUser(long userID) {
        String previous = uuidMap.remove(userID);
        usernameCache.invalidate(userID);

        // Update the file
        if (previous != null) {
            List<String> lines;
            try {
                lines = Files.readAllLines(Paths.get("userdata/" + guildID + ".userdata"));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            lines.removeIf(s -> s.startsWith(String.valueOf(userID)));
            try {
                Files.write(Paths.get("userdata/" + guildID + ".userdata"), lines);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Userdata: " + lines.toString());
            }
        }
    }

    /**
     * Updates the nicknames of all members that changed their minecraft ign.
     *
     * @param members A list of all the members to be checked.
     * @return A hashmap of all updated user's IDs and their new username. If the associated uuid doesn't exist anymore the string array is {"-"} and if an error occurred it is {"e"}.
     */
    public HashMap<Long, String[]> updateNames(List<Member> members) {
        HashMap<Long, String[]> updated = new HashMap<>();
        for (Member member : members) {
            String uuid = uuidMap.get(member.getIdLong());
            long userID = member.getIdLong();
            if (uuid != null) {
                String[] res = updateMember(member);
                if (res.length == 1) {
                    if (res[0].equals("-")) {
                        removeUser(userID); //This should be ok since minecraft accounts don't get deleted often.
                        updated.put(userID, res);
                    } else if (res[0].equals("e"))
                        updated.put(userID, res);
                } else if (res.length == 2)
                    updated.put(userID, res);
            }
        }
        return updated;
    }

    /**
     * Returns an unmodifiable list of all user's IDs who have an associated minecraft account.
     *
     * @return possibly-empty list of user IDs.
     */
    public List<Long> getSavedUserIDs() {
        return Collections.unmodifiableList(new ArrayList<>(uuidMap.keySet()));
    }

    /**
     * Returns an unmodifiable list of all saved minecraft uuids.
     *
     * @return possibly-empty list of minecraft uuids.
     */
    public List<String> getSavedUuids() {
        return Collections.unmodifiableList(new ArrayList<>(uuidMap.values()));
    }
}
