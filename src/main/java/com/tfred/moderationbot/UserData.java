package com.tfred.moderationbot;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;

//TODO try creating a new client every time
public class UserData {
    private static final HashMap<Long, UserData> allUserData = new HashMap<>();
    private static final CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
            .setMaxConnPerRoute(1000)
            .setMaxConnTotal(1000)
            .build();
    public final long guildID;
    private final LoadingCache<Long, String[]> usernameCache;
    private SoftReference<HashMap<Long, String>> uuidMapReference;

    private UserData(long guildID) {
        this.guildID = guildID;
        usernameCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<Long, String[]>() {
                            @Override
                            public String[] load(@NotNull Long userID) {
                                HashMap<Long, String> uuidMap = uuidMapReference.get();
                                if (uuidMap == null) {
                                    loadData();
                                    uuidMap = uuidMapReference.get();
                                }
                                assert uuidMap != null;
                                String uuid = uuidMap.get(userID);
                                if (uuid == null)
                                    return new String[]{};
                                try {
                                    return getName(uuid);
                                } catch (Exception e) {
                                    return new String[]{"e"};
                                }
                            }
                        }
                );
        uuidMapReference = new SoftReference<>(null);
    }

    /**
     * Get the user data of a guild.
     *
     * @param guildID The guild ID of the guild.
     * @return The userdata.
     */
    public static UserData get(long guildID) {
        if (allUserData.containsKey(guildID))
            return allUserData.get(guildID);

        else {
            synchronized (UserData.class) {
                if (allUserData.containsKey(guildID))
                    return allUserData.get(guildID);

                UserData newUD = new UserData(guildID);
                allUserData.put(guildID, newUD);
                return newUD;
            }
        }
    }

    /**
     * Get the latest minecraft name of a uuid and the previous name if one exists.
     *
     * @param uuid the uuid to get the name for.
     * @return a {@link CompletableFuture completable future} with a value that is either an array containing the name(s) or {"-1"} if the uuid doesn't exist or {"e"} if an error occured.
     */
    public static String[] getName(String uuid) {
        HttpGet request = new HttpGet("https://api.mojang.com/user/profiles/" + uuid + "/names");
        try {
            Future<HttpResponse> future = httpclient.execute(request, null);
            HttpResponse response;
            try {
                response = future.get();
            } catch (InterruptedException | ExecutionException Ignored) {
                System.out.println("GET NOT WORKED");
                return new String[]{"e"};
            }

            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (IOException ignored) {
                    System.out.println("GET NOT WORKED");
                    return new String[]{"e"};
                }

                Matcher m = Pattern.compile("\"name\":\"(.*?)\"").matcher(responseBody);
                List<String> matches = new ArrayList<>();
                while (m.find())
                    matches.add(m.group(1));
                if (matches.isEmpty()) // uuid invalid
                    return new String[]{"-"};
                else if (matches.size() == 1)
                    return new String[]{matches.get(0)};
                else
                    return new String[]{matches.get(matches.size() - 2), matches.get(matches.size() - 1)};
            } else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) //UUID invalid
                return new String[]{"!"};
            else {
                System.out.println("GET NOT WORKED");
                return new String[]{"e"};
            }
        } finally {
            request.releaseConnection();
        }
    }

    /**
     * Get the minecraft uuid of a minecraft ign
     *
     * @param name The name to get the uuid for.
     * @return a {@link CompletableFuture completable future} with a value that is the uuid if successful, "!" if the name is invalid or null if an error occurred.
     */
    public static String getUUID(String name) {
        HttpGet request = new HttpGet("https://api.mojang.com/users/profiles/minecraft/" + name);
        try {
            Future<HttpResponse> future = httpclient.execute(request, null);
            HttpResponse response;
            try {
                response = future.get();
            } catch (InterruptedException | ExecutionException Ignored) {
                System.out.println("GET NOT WORKED");
                return null;
            }

            int responseCode = response.getStatusLine().getStatusCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String responseBody;
                try {
                    responseBody = EntityUtils.toString(response.getEntity());
                } catch (IOException ignored) {
                    System.out.println("GET NOT WORKED");
                    return null;
                }

                Pattern p = Pattern.compile(".*\"id\":\"(.*?)\"");
                Matcher m = p.matcher(responseBody);
                if (m.find())
                    return m.group(1);
                else
                    return "!"; //name invalid
            } else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST || responseCode == HttpURLConnection.HTTP_NO_CONTENT) //name invalid
                return "!";
            else
                return null;
        } finally {
            request.releaseConnection();
        }
    }

    public static void start() {
        httpclient.start();
    }

    public static void shutdown() {
        try {
            httpclient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadData() {
        Path filepath = Paths.get("userdata/" + guildID + ".userdata");
        if (Files.exists(filepath)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(filepath);
            } catch (IOException e) {
                System.out.println("IO error when reading user data!");
                uuidMapReference = new SoftReference<>(new HashMap<>());
                return;
            }
            int v = lines.size();
            { // Compute the next highest power of 2 (http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2)
                v--;
                v |= v >> 1;
                v |= v >> 2;
                v |= v >> 4;
                v |= v >> 8;
                v |= v >> 16;
                v++;
            }
            HashMap<Long, String> uuidMap = new HashMap<>(v);
            for (String line : lines) {
                String[] data = line.split(" ");
                try {
                    uuidMap.put(Long.parseLong(data[0]), data[1]);
                } catch (IndexOutOfBoundsException ignored) {
                    System.out.println("Formatting error in file " + guildID + ".userdata!");
                }
            }
            uuidMapReference = new SoftReference<>(uuidMap);
        } else {
            uuidMapReference = new SoftReference<>(new HashMap<>());
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
                            ModerationBot.ignoredUsers.add(m.getIdLong());
                            m.modifyNickname(newNick).queue();
                        } catch (HierarchyException | InsufficientPermissionException ignored) {
                            ModerationBot.ignoredUsers.remove(m.getIdLong());
                        }
                    }
                } else {
                    try {
                        ModerationBot.ignoredUsers.add(m.getIdLong());
                        m.modifyNickname(newName).queue();
                    } catch (HierarchyException | InsufficientPermissionException ignored) {
                        ModerationBot.ignoredUsers.remove(m.getIdLong());
                    }
                }
                if (hide)
                    return new String[]{};
                else
                    return nameChange;
            }
            return new String[]{};
        } catch (HierarchyException | InsufficientPermissionException | ExecutionException e) {
            e.printStackTrace();
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
            if (names.length == 0)
                return "";
            else if (names.length == 1) {
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
        HashMap<Long, String> uuidMap = uuidMapReference.get();
        if (uuidMap == null) {
            synchronized (this) {
                uuidMap = uuidMapReference.get();
                if (uuidMap == null) {
                    loadData();
                    uuidMap = uuidMapReference.get();
                }
            }
        }
        assert uuidMap != null;
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
     * @return a {@link CompletableFuture completable future} with value 1 if successful, 0 if invalid mc name or -1 if an error occurred
     */
    public int setUuid(Member member, String name) {
        String uuid = getUUID(name);
        if (uuid == null)
            return -1;
        if (uuid.equals("!"))
            return 0;

        synchronized (this) {
            long userID = member.getIdLong();
            removeUser(userID);
            try {
                Files.write(Paths.get("userdata/" + guildID + ".userdata"), (userID + " " + uuid).getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }

            HashMap<Long, String> uuidMap = uuidMapReference.get();
            if (uuidMap != null)
                uuidMap.put(userID, uuid);

            usernameCache.put(userID, new String[]{"none", name});
            updateMember(member); // Return value can be ignored since nothing should go wrong

            return 1;
        }
    }

    /**
     * Removes a specified user's associated minecraft ign.
     *
     * @param userID The specified {@link Member member's} ID.
     */
    public synchronized void removeUser(long userID) {
        HashMap<Long, String> uuidMap = uuidMapReference.get();
        if (uuidMap != null)
            uuidMap.remove(userID);
        usernameCache.invalidate(userID);

        // Update the file
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

    /**
     * Updates the nicknames of all members that changed their minecraft ign.
     *
     * @param members A list of all the members to be checked.
     * @return a {@link CompletableFuture completable future} with a value that is a hashmap of all updated user's IDs and their old and new username. If the associated uuid doesn't exist anymore the string array is {"-"} and if an error occurred it is {"e"}.
     */
    public Map<Long, String[]> updateNames(List<Member> members) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000).build();
        CloseableHttpAsyncClient httpclient1 = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnPerRoute(1000)
                .setMaxConnTotal(1000)
                .build();
        httpclient1.start();
        HashMap<Long, String> uuidMap = uuidMapReference.get();
        if (uuidMap == null) {
            synchronized (this) {
                uuidMap = uuidMapReference.get();
                if (uuidMap == null) {
                    loadData();
                    uuidMap = uuidMapReference.get();
                }
            }
        }
        assert uuidMap != null;

        Map<Long, Member> memberMap = members.stream().collect(Collectors.toMap(ISnowflake::getIdLong, m -> m));

        HashMap<Long, String> toChange = new HashMap<>(uuidMap);
        toChange.keySet().retainAll(memberMap.keySet());
        Map<Long, String[]> updated = new ConcurrentHashMap<>();

        try {
            httpclient1.start();
            final ArrayList<HttpGet> req = new ArrayList<>(toChange.size());
            for (Map.Entry<Long, String> entry : toChange.entrySet()) {
                req.add(new HttpGet("https://api.mojang.com/user/profiles/" + entry.getValue() + "/names"));
            }

            final CountDownLatch latch = new CountDownLatch(req.size());
            for (final HttpGet request: req) {
                httpclient1.execute(request, new FutureCallback<HttpResponse>() {

                    @Override
                    public void completed(final HttpResponse response) {
                        latch.countDown();
                        System.out.println(request.getRequestLine() + "->" + response.getStatusLine());
                    }

                    @Override
                    public void failed(final Exception ex) {
                        latch.countDown();
                        System.out.println(request.getRequestLine() + "->" + ex);
                    }

                    @Override
                    public void cancelled() {
                        latch.countDown();
                        System.out.println(request.getRequestLine() + " cancelled");
                    }

                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Shutting down");
        } finally {
            try {
                httpclient1.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Done");



        return new HashMap<>();









/*
        ArrayList<Future<HttpResponse>> fl = new ArrayList<>(toChange.size());
        try {
            final CountDownLatch latch = new CountDownLatch(toChange.size());
            for (Map.Entry<Long, String> entry : toChange.entrySet()) {
                HttpGet request = new HttpGet("https://api.mojang.com/user/profiles/" + entry.getValue() + "/names");

                fl.add(httpclient1.execute(request, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(final HttpResponse response) {
                        try {
                            request.releaseConnection();
                            int responseCode = response.getStatusLine().getStatusCode();
                            if (responseCode == HttpURLConnection.HTTP_OK) {
                                String responseBody;
                                try {
                                    responseBody = EntityUtils.toString(response.getEntity());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    updated.put(entry.getKey(), new String[]{"e"});
                                    return;
                                }

                                Matcher m = Pattern.compile("\"name\":\"(.*?)\"").matcher(responseBody);
                                List<String> matches = new ArrayList<>();
                                while (m.find())
                                    matches.add(m.group(1));

                                if (matches.isEmpty()) {// uuid invalid
                                    removeUser(entry.getKey());
                                    updated.put(entry.getKey(), new String[]{"!"});
                                } else if (matches.size() == 1) {
                                    usernameCache.put(entry.getKey(), new String[]{matches.get(0)});
                                } else {
                                    String[] res = new String[]{matches.get(matches.size() - 2), matches.get(matches.size() - 1)};
                                    usernameCache.put(entry.getKey(), res);
                                    if (updateMember(memberMap.get(entry.getKey())).length == 2) {
                                        updated.put(entry.getKey(), res);
                                    }
                                }
                            } else if (responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {//UUID invalid
                                removeUser(entry.getKey());
                                updated.put(entry.getKey(), new String[]{"!"});
                            } else {
                                System.out.println("GET NOT WORKED");
                                updated.put(entry.getKey(), new String[]{"e"});
                            }
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        request.releaseConnection();
                        System.out.println("GET NOT WORKED");
                        updated.put(entry.getKey(), new String[]{"e"});
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        request.releaseConnection();
                        System.out.println("Request cancelled");
                        latch.countDown();
                    }

                }));
            }
            latch.await();
            fl.forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            httpclient1.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return updated;*/
    }

    /**
     * Returns an unmodifiable list of all user's IDs who have an associated minecraft account.
     *
     * @return possibly-empty list of user IDs.
     */
    public List<Long> getSavedUserIDs() {
        HashMap<Long, String> uuidMap = uuidMapReference.get();
        if (uuidMap == null) {
            synchronized (this) {
                uuidMap = uuidMapReference.get();
                if (uuidMap == null) {
                    loadData();
                    uuidMap = uuidMapReference.get();
                }
            }
        }
        assert uuidMap != null;
        return Collections.unmodifiableList(new ArrayList<>(uuidMap.keySet()));
    }

    /**
     * Returns an unmodifiable list of all saved minecraft uuids.
     *
     * @return possibly-empty list of minecraft uuids.
     */
    public List<String> getSavedUuids() {
        HashMap<Long, String> uuidMap = uuidMapReference.get();
        if (uuidMap == null) {
            synchronized (this) {
                uuidMap = uuidMapReference.get();
                if (uuidMap == null) {
                    loadData();
                    uuidMap = uuidMapReference.get();
                }
            }
        }
        assert uuidMap != null;
        return Collections.unmodifiableList(new ArrayList<>(uuidMap.values()));
    }
}
