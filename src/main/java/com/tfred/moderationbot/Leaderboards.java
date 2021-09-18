package com.tfred.moderationbot;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.tfred.moderationbot.commands.CommandUtils;
import com.tfred.moderationbot.usernames.UsernameHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

//TODO store latest data in json format
public class Leaderboards {
    private static final Path path = Paths.get("leaderboards.data");
    private static long date = 0; // Date of the latest leaderboard in milliseconds
    private static List<LbSpot> hiderLb = new ArrayList<>(50);
    private static List<LbSpot> hunterLb = new ArrayList<>(50);
    private static List<LbSpot> killsLb = new ArrayList<>(50);

    private static void initialize(long newDate) throws LeaderboardFetchFailedException {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException ignored) {
            date = 1;
            updateLeaderboards();
            return;
        }

        long oldDate = Long.parseLong(lines.remove(0));

        if (newDate - oldDate > 600000000) {
            date = oldDate;
            updateLeaderboards();
            return;
        }
        date = newDate;
        fetchNewLeaderboards(date);
        int lineNum = 0;
        if (lines.size() == 6)
            lineNum = 3;

        if (lines.size() > 2) {
            for (int i = 0; i < 3; i++)
                lbListSetChanges(lines.get(i + lineNum).split(":"));
        } else {
            hiderLb.forEach(s -> s.setChange(0));
            hunterLb.forEach(s -> s.setChange(0));
            killsLb.forEach(s -> s.setChange(0));
        }
    }

    private static String[] lbURLs(long dateMillis) {
        String time = LocalDateTime.ofEpochSecond(dateMillis / 1000, 0, ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH%3'A'mm%3'A'ss"));
        return new String[]{
                "https://mpstats.timmi6790.de/v1/java/leaderboard/blockhunt/hiderwins/all/save?saveTime=" + time + "Z&filterReasons=GLITCHED&filterReasons=GIVEN&filterReasons=BOOSTED&filterReasons=HACKED",
                "https://mpstats.timmi6790.de/v1/java/leaderboard/blockhunt/hunterwins/all/save?saveTime=" + time + "Z&filterReasons=GLITCHED&filterReasons=GIVEN&filterReasons=BOOSTED&filterReasons=HACKED",
                "https://mpstats.timmi6790.de/v1/java/leaderboard/blockhunt/kills/all/save?saveTime=" + time + "Z&filterReasons=GLITCHED&filterReasons=GIVEN&filterReasons=BOOSTED&filterReasons=HACKED"
        };
    }

    private static void fetchNewLeaderboards(long date2) throws LeaderboardFetchFailedException {
        JsonElement[] leaderboard = getJsonLbData(date2);
        try {
            Type listType = new TypeToken<ArrayList<LbSpot>>() {
            }.getType();

            hiderLb = new Gson().fromJson(leaderboard[0], listType);
            hiderLb = hiderLb.subList(0, 50);
            hunterLb = new Gson().fromJson(leaderboard[1], listType);
            hunterLb = hunterLb.subList(0, 50);
            killsLb = new Gson().fromJson(leaderboard[2], listType);
            killsLb = killsLb.subList(0, 50);
        } catch (JsonSyntaxException e) {
            throw new LeaderboardFetchFailedException(e.getMessage());
        }
    }

    private static JsonElement[] getJsonLbData(long date2) throws LeaderboardFetchFailedException {
        JsonElement[] data = new JsonElement[3];
        try {
            String[] lbUrls = lbURLs(date2);
            for (int i = 0; i < 3; i++) {
                URL urlForGetRequest = new URL(lbUrls[i]);

                HttpURLConnection connection = (HttpURLConnection) urlForGetRequest.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Rigor/1.0.0; http://rigor.com)");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String response = in.readLine();
                    in.close();

                    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

                    data[i] = jsonObject.get("entries");
                    if (data[i] == null) {
                        System.out.println("Leaderboard null! Url: " + lbUrls[i] + "\nServer response: " + response);
                        throw new LeaderboardFetchFailedException("Leaderboard null! Url: " + lbUrls[i] + "\nServer response: " + response);
                    }
                } else {
                    System.out.println("Http error when fetching leaderboard: " + responseCode + lbUrls[i]);
                    throw new LeaderboardFetchFailedException("Http error when fetching leaderboard: " + responseCode + lbUrls[i]);
                }
            }
        } catch (IOException e) {
            System.out.println("IO Error when fetching leaderboards!");
            throw new LeaderboardFetchFailedException("IO Error when fetching leaderboards!");
        }
        return data;
    }

    private static void lbListSetChanges(String[] data) {
        List<String> uuidsold = new ArrayList<>();
        Collections.addAll(uuidsold, data);
        List<LbSpot> lb;

        String board = uuidsold.remove(0);

        switch (board) {
            case "Hider":
                lb = hiderLb;
                break;
            case "Hunter":
                lb = hunterLb;
                break;
            case "Kills":
                lb = killsLb;
                break;
            default:
                return;
        }

        List<String> uuidsNew = lb.stream().map(LbSpot::getUuid).collect(Collectors.toList());

        List<Integer> changes = listChanges(uuidsold, uuidsNew);

        for (int i = 0; i < 50; i++) {
            LbSpot spot = lb.get(i);
            spot.setChange(changes.get(i));
        }
    }

    private static List<Integer> listChanges(List<String> before, List<String> after) {
        List<Integer> changes = new ArrayList<>(50);

        for (int i = 0; i < 50; i++) {
            String uuid = after.get(i);
            int oldIndex = before.indexOf(uuid);
            if (oldIndex == -1)
                changes.add(100);
            else
                changes.add(-(i - oldIndex));
        }

        return changes;
    }

    private static void updateFile(List<String> lines) {
        int lineNum = lines.size() == 6 ? 3 : 0; //If the file only had 3 lines it reuses those
        try {
            List<String> data = new ArrayList<>(3);
            data.add(Long.toString(date));
            data.add(lines.get(lineNum));
            data.add(lines.get(lineNum + 1));
            data.add(lines.get(lineNum + 2));
            data.add("Hider:" + hiderLb.stream().map(LbSpot::getUuid).collect(Collectors.joining(":")));
            data.add("Hunter:" + hunterLb.stream().map(LbSpot::getUuid).collect(Collectors.joining(":")));
            data.add("Kills:" + killsLb.stream().map(LbSpot::getUuid).collect(Collectors.joining(":")));

            Files.write(path, data);

            System.out.println("Updated leaderboards.data.");
        } catch (IOException e) {
            System.out.println("IO error when writing leaderboards data!");
        }
    }

    /**
     * Updates the saved leaderboards data. If one week has passed since the reference data for the change has been updated this gets updated too with data from 1 week ago.
     */
    public static synchronized void updateLeaderboards() throws LeaderboardFetchFailedException {
        long newDate;
        { // Get the time in Milliseconds of the last Sunday at 6 am CEST
            long start = 1603602000000L; // Sun Oct 25 2020 06:00:00 CEST
            long week = 604800000L;
            long current = System.currentTimeMillis() - start;
            long weeks = current / week;
            newDate = weeks * week + start;
        }
        if (date == 0) {
            initialize(newDate);
            return;
        }
        boolean empty = false;

        List<String> lines = new ArrayList<>();
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("IO error when reading leaderboards data! Creating new leaderboards.data file.");
            empty = true;
        }
        if (lines.size() == 0) {
            System.out.println("Leaderboards.data is empty! Change will be set to 0.");
            empty = true;
        }

        if (empty)
            date = 0;
        else
            lines.remove(0);

        int lineNum = 0;
        if (newDate - date > 600000000) {
            System.out.println("Updating leaderboards.");
            date = newDate;
            fetchNewLeaderboards(date);

            updateFile(lines);
            if (lines.size() == 6)
                lineNum = 3;
        }

        if (!empty) {
            for (int i = 0; i < 3; i++)
                lbListSetChanges(lines.get(i + lineNum).split(":"));
        } else {
            hiderLb.forEach(s -> s.setChange(0));
            hunterLb.forEach(s -> s.setChange(0));
            killsLb.forEach(s -> s.setChange(0));
        }
    }

    /**
     * Returns a list containing strings each with 10 positions of a specified leaderboard with optional user mentions.
     *
     * @param board   The board to be used. 0: hider wins, 1: hunter wins, 2: kills.
     * @param guildID The ID of the {@link net.dv8tion.jda.api.entities.Guild guild} to get saved user data from. If null the string doesn't have mentions.
     * @return A {@link List<String> list} of strings. Or null if there is no data.
     * @throws IllegalArgumentException If the specified board isn't in the range of 0-2.
     */
    public static List<String> lbToString(int board, long guildID) {
        if (date == 0) {
            try {
                updateLeaderboards();
            } catch (LeaderboardFetchFailedException ignored) {
                return null;
            }
        }
        List<LbSpot> lb;
        switch (board) {
            case 0:
                lb = hiderLb;
                break;
            case 1:
                lb = hunterLb;
                break;
            case 2:
                lb = killsLb;
                break;
            default:
                throw new IllegalArgumentException("board must be in range 0-2");
        }

        List<String> savedUuids = null;
        UsernameHandler usernameHandler = null;

        boolean noMentions = false;
        if (guildID == 0)
            noMentions = true;
        else {
            usernameHandler = UsernameHandler.get(guildID);
            savedUuids = usernameHandler.getSavedUuids();
        }

        List<String> output = new ArrayList<>(5);
        StringBuilder temp = new StringBuilder();

        int i = 1;
        for (LbSpot s : lb) {
            String userID = "";
            if (!noMentions) {
                String uuid = s.getUuid().replace("-", "");
                if (savedUuids.contains(uuid))
                    userID = String.valueOf(usernameHandler.getUserID(uuid));
            }

            temp.append(s.toString(userID));

            if ((i) % 10 == 0) {
                output.add(temp.toString());
                temp.setLength(0);
            } else
                temp.append("\n");

            i++;
        }

        return output;
    }

    /**
     * Get the date of the current leaderboard.
     *
     * @return the date in milliseconds since epoch.
     */
    public static long getDate() {
        return date;
    }

    /**
     * Updates the leaderboard messages in a specified guild.
     *
     * @param channel The {@link TextChannel channel} to send the results to (can be null).
     * @param guild   The specified {@link Guild guild}.
     */
    public static void updateLeaderboards(TextChannel channel, Guild guild) {
        long guildID = guild.getIdLong();

        try {
            Leaderboards.updateLeaderboards();
        } catch (Leaderboards.LeaderboardFetchFailedException e) {
            System.out.println("Leaderboard update failed! " + e.getMessage());
            if (channel != null)
                CommandUtils.sendError(channel, "Leaderboard updating failed! Please try again in a bit or if that doesn't work contact the bot dev. " + e.getMessage());
            return;
        }

        long[][] data = ServerData.get(guildID).getAllLbMessages();
        for (int i = 0; i < 3; i++) {
            if (data[i][0] == 0)
                continue;

            TextChannel editChannel = guild.getTextChannelById(data[i][0]);
            if (editChannel == null)
                continue;

            List<String> lb = Leaderboards.lbToString(i, guildID);
            assert lb != null;

            EmbedBuilder eb = new EmbedBuilder().setColor(CommandUtils.DEFAULT_COLOR);
            eb.addField(new String[]{"Hider Wins", "Hunter Wins", "Kills"}[i] + " Leaderboard:", lb.remove(0), false);
            for (String s : lb) {
                eb.addField("", s, false);
            }
            eb.setFooter("Last update: ");
            eb.setTimestamp(Instant.ofEpochMilli(Leaderboards.getDate()));
            try {
                editChannel.editMessageEmbedsById(data[i][1], eb.build()).queue();
            } catch (IllegalArgumentException ignored) {
            } catch (ErrorResponseException e) {
                if (channel != null)
                    CommandUtils.sendError(channel, "An error occurred when updating lb " + i + ": " + e.getMessage());
            }
        }
        if (channel != null)
            CommandUtils.sendSuccess(channel, "Updated leaderboards.");
    }

    private static class Player {
        public final String name; //MC username
        public final String uuid; //MC uuid

        public Player(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

    private static class LbSpot {
        private final Player player;
        private final int position;
        private final int score;
        private transient String change = ""; //change in position since last time step

        LbSpot(Player player, int position, int score) {
            this.player = player;
            this.position = position;
            this.score = score;
        }

        public String getUuid() {
            return player.uuid;
        }

        public void setChange(int change) {
            if (change == 0)
                this.change = "";
            else if (change == 100)
                this.change = ":new:";
            else if (change > 0)
                this.change = "**" + change + "**:arrow_up:";
            else
                this.change = "**" + (-change) + "**:arrow_down:";
        }

        @Override
        public String toString() {
            return this.toString("");
        }

        public String toString(String userID) {
            String special_emoji = "";
            if (position <= 10) {
                switch (position) {
                    case 1:
                        special_emoji = " :first_place:";
                        break;
                    case 2:
                        special_emoji = " :second_place:";
                        break;
                    case 3:
                        special_emoji = " :third_place:";
                        break;
                    default:
                        special_emoji = " :trophy:";
                }
            }

            String mention = "";
            if (!userID.isEmpty())
                mention = " **[<@" + userID + ">]**";

            return position + "." + special_emoji + " **" + (player.name.replaceAll("_", "\\\\_")) + "**" + mention + " - " + score + "    " + change + "\u200B";
        }
    }

    public static class LeaderboardFetchFailedException extends Exception {
        public LeaderboardFetchFailedException(String errorMessage) {
            super(errorMessage);
        }
    }
}
