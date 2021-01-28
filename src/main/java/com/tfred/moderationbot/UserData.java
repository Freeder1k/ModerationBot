package com.tfred.moderationbot;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.tfred.moderationbot.commands.CommandUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UserData {
    private static final HashMap<Long, UserData> allUserData = new HashMap<>();
    private static int requestCount = 0;
    private static long lastRatelimitReset = 0L;
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
                            public String[] load(@NotNull Long userID) throws RateLimitException {
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
                                    if (e instanceof RateLimitException)
                                        throw e;
                                    return new String[]{"e"};
                                }
                            }
                        }
                );
        uuidMapReference = new SoftReference<>(null);
    }

    /**
     * Checks whether a certain amount of requests can be sent and increases the request counter if possible.
     *
     * @param extraRequestCount The amount of requests we want to send.
     * @throws RateLimitException If the extra amount of requests would exceed the request limit.
     */
    private static synchronized void increaseRequestCount(int extraRequestCount) throws RateLimitException {
        if (System.currentTimeMillis() - 601000L > lastRatelimitReset) {
            requestCount = extraRequestCount;
            lastRatelimitReset = System.currentTimeMillis();
            return;
        }
        if (requestCount + extraRequestCount > 600)
            throw new RateLimitException((int) ((lastRatelimitReset + 601000L - System.currentTimeMillis()) / 1000));

        requestCount += extraRequestCount;
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
     * @param uuid The uuid to get the name for.
     * @return The name(s) or {"-1"} if the uuid doesn't exist or {"e"} if an error occured.
     */
    public static String[] getName(String uuid) throws RateLimitException {
        increaseRequestCount(1);
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

    /**
     * Get the minecraft uuid of a minecraft ign
     *
     * @param name The name to get the uuid for.
     * @return The uuid if successful, "!" if the name is invalid, null if an error occured.
     */
    public static String getUUID(String name) throws RateLimitException {
        increaseRequestCount(1);
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
    public String[] updateMember(Member m) throws RateLimitException {
        try {
            List<ModerationBot> botListenerList = m.getJDA().getRegisteredListeners()
                    .stream().filter(o -> o instanceof ModerationBot).map(o -> (ModerationBot) o).collect(Collectors.toList());

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
                            botListenerList.forEach(b -> b.addIgnoredUser(m.getIdLong(), m.getGuild().getIdLong()));
                            //ModerationBot.ignoredUsers.add(m.getIdLong());
                            m.modifyNickname(newNick).queue();
                        } catch (HierarchyException | InsufficientPermissionException ignored) {
                            botListenerList.forEach(b -> b.removeIgnoredUser(m.getIdLong(), m.getGuild().getIdLong()));
                            //ModerationBot.ignoredUsers.remove(m.getIdLong());
                        }
                    }
                } else {
                    try {
                        botListenerList.forEach(b -> b.addIgnoredUser(m.getIdLong(), m.getGuild().getIdLong()));
                        //ModerationBot.ignoredUsers.add(m.getIdLong());
                        m.modifyNickname(newName).queue();
                    } catch (HierarchyException | InsufficientPermissionException ignored) {
                        botListenerList.forEach(b -> b.removeIgnoredUser(m.getIdLong(), m.getGuild().getIdLong()));
                        //ModerationBot.ignoredUsers.remove(m.getIdLong());
                    }
                }
                if (hide)
                    return new String[]{};
                else
                    return nameChange;
            }
            return new String[]{};
        } catch (HierarchyException | InsufficientPermissionException e) {
            e.printStackTrace();
            return new String[]{};
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RateLimitException)
                throw (RateLimitException) e.getCause();
            else {
                e.printStackTrace();
                return new String[]{};
            }
        }
    }

    /**
     * Returns the specified member's associated minecraft ign or an empty string if this member doesn't have one or there was an error.
     *
     * @param userID The specified {@link Member member's} ID.
     * @return possibly-empty string containing a minecraft ign.
     */
    public String getUsername(long userID) throws RateLimitException {
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
            if (e.getCause() instanceof RateLimitException)
                throw (RateLimitException) e.getCause();
            else {
                e.printStackTrace();
                return "";
            }
        }
    }

    /**
     * Get the latest minecraft name of a uuid and the previous name if one exists.
     *
     * @param userID The user ID to get the name for.
     * @return The name(s) or {} if the users uuid doesn't exist anymore or {"e"} if an error occured.
     * @throws RateLimitException If the rate limit got reached.
     */
    public String[] getUsernames(long userID) throws RateLimitException {
        try {
            return usernameCache.get(userID);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RateLimitException)
                throw (RateLimitException) e.getCause();
            else {
                e.printStackTrace();
                return new String[]{"e"};
            }
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
     * @return a String containing the case-corrected username or "e" if an error occured or an empty string if that name doesnt exist
     */
    public String setUuid(Member member, String name) throws RateLimitException {
        String uuid = getUUID(name);
        if (uuid == null)
            return "e";
        if (uuid.equals("!"))
            return "";

        synchronized (this) {
            long userID = member.getIdLong();
            removeUser(userID);
            try {
                Files.write(Paths.get("userdata/" + guildID + ".userdata"), (userID + " " + uuid).getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
                return "e";
            }

            HashMap<Long, String> uuidMap = uuidMapReference.get();
            if (uuidMap != null)
                uuidMap.put(userID, uuid);

            updateMember(member);

            String[] mcname;
            try {
                mcname = usernameCache.get(userID);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RateLimitException)
                    throw (RateLimitException) e.getCause();
                else
                    return "e";
            }
            if (mcname.length == 1) {
                if (mcname[0].equals("-1"))
                    return "";
                return mcname[0];
            }
            if (mcname.length == 2)
                return mcname[1];
            return "e";
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
     * @return a map of all updated user's IDs and their old and new username. If the associated uuid doesn't exist anymore the string array is {"-"} and if an error occurred it is {"e"}.
     */
    public Map<Long, String[]> updateNames(List<Member> members) throws RateLimitException {
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

        increaseRequestCount(toChange.size() + 20);

        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
                .setMaxConnPerRoute(1000)
                .setMaxConnTotal(1000)
                .build();

        try {
            httpclient.start();
            final CountDownLatch latch = new CountDownLatch(toChange.size());
            for (Map.Entry<Long, String> entry : toChange.entrySet()) {
                HttpGet request = new HttpGet("https://api.mojang.com/user/profiles/" + entry.getValue() + "/names");
                httpclient.execute(request, new FutureCallback<HttpResponse>() {
                    @Override
                    public void completed(final HttpResponse response) {
                        try {
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
                                    try {
                                        if (updateMember(memberMap.get(entry.getKey())).length == 2) {
                                            updated.put(entry.getKey(), res);
                                        }
                                    } catch (RateLimitException e) {
                                        e.printStackTrace();
                                        updated.put(entry.getKey(), new String[]{"e"});
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
                        System.out.println(ex.toString());
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        System.out.println(" cancelled");
                        latch.countDown();
                    }

                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
                e.printStackTrace();
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

    public static class RateLimitException extends Exception {
        /**
         * The time left in seconds until the rate limit expires.
         */
        public final int timeLeft;

        public RateLimitException(int timeLeft) {
            super("Mojang rate limit reached! Please wait " + timeLeft + " seconds before trying again!");
            this.timeLeft = timeLeft;
        }
    }



    private static final Map<Long, Long> updateNamesTimes = Collections.synchronizedMap(new HashMap<>());


    /**
     * Updates the nicknames of users in a specified guild.
     *
     * @param channel The {@link TextChannel channel} to send the results to (can be null).
     * @param guild   The specified {@link Guild guild}.
     */
    public static void updateNames(TextChannel channel, Guild guild, boolean bypassTimeRestriction) {
        long guildID = guild.getIdLong();
        if (!bypassTimeRestriction) {
            Long lastTimeRan = updateNamesTimes.get(guildID);
            if (lastTimeRan != null) {
                if (System.currentTimeMillis() - lastTimeRan < 600000) {
                    CommandUtils.sendError(channel, "This command can only be ran once every 10 minutes per guild!");
                    return;
                }
            }
        }
        updateNamesTimes.put(guildID, System.currentTimeMillis());

        Map<Long, String[]> changed;
        try {
            changed = UserData.get(guildID).updateNames(guild.getMembers());
        } catch (UserData.RateLimitException e) {
            CommandUtils.sendError(channel, e.getMessage());
            return;
        }
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Updated Users:")
                .setColor(CommandUtils.defaultColor);

        if (changed.size() == 1)
            eb.setTitle("Updated User:");

        if (!changed.isEmpty()) {
            StringBuilder updated = new StringBuilder();
            StringBuilder removed = new StringBuilder();
            StringBuilder failed = new StringBuilder();
            for (Map.Entry<Long, String[]> entry : changed.entrySet()) {
                String[] s = entry.getValue();
                if (s[0].equals("-"))
                    removed.append("<@").append(entry.getKey()).append(">\n");
                else if (s[0].equals("e"))
                    failed.append("<@").append(entry.getKey()).append(">\n");
                else
                    updated.append("<@").append(entry.getKey()).append(">").append(" (").append(s[0]).append(" -> ").append(s[1]).append(")\n");
            }
            if (updated.length() != 0) {
                if (updated.length() > 2048)
                    eb.setDescription(updated.length() + " users were updated.");
                else
                    eb.setDescription(updated.toString());
            } else
                eb.setDescription("No users were updated.");
            if (removed.length() != 0) {
                if (removed.length() < 1024)
                    eb.addField("\nRemoved Users:", removed.toString(), false);
                else
                    eb.addField("", removed.length() + " users were removed from the system.", false);
            }
            if (failed.length() != 0) {
                if (failed.length() < 1024)
                    eb.addField("\nFailed Users:", failed.toString(), false);
                else
                    eb.addField("", "Updating failed on " + failed.length() + " users.", false);
            }

            TextChannel namechannel = guild.getTextChannelById(ServerData.get(guildID).getNameChannel());
            try {
                if ((namechannel != null) && (!namechannel.equals(channel)))
                    namechannel.sendMessage(eb.build()).queue();
            } catch (InsufficientPermissionException ignored) {
            }
        } else {
            eb.setDescription("No users were updated.");
        }

        if (channel != null)
            channel.sendMessage(eb.build()).queue();
    }
}
