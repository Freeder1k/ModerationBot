package com.tfred.moderationbot;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class Leaderboards {
    private static class LbSpot {
        private final String uuid; //MC uuid
        private final String name; //MC username
        private final int position;
        private final int score;
        private transient String change = ""; //change in position since last time step

        LbSpot(String name, String uuid, int position, int score) {
            this.name = name;
            this.uuid = uuid;
            this.position = position;
            this.score = score;
        }
        public String getUuid() {
            return uuid;
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

            return position + "." + special_emoji + " **" + (name.replaceAll("_", "\\\\_")) + "**" + mention + " - " + score + "    " + change + "\u200B";
        }
    }

    private static final Path path = Paths.get("leaderboards.data");

    private long date;
    private List<LbSpot> hiderLb = new ArrayList<>(50);
    private List<LbSpot> hunterLb = new ArrayList<>(50);
    private List<LbSpot> killsLb = new ArrayList<>(50);

    public Leaderboards() {
        updateLeaderboards();
        System.out.println("Finished reading saved leaderboards data!");
    }

    private String[] lbURLs(long date) {
        String time = LocalDateTime.ofEpochSecond(date/1000, 0, ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH%3'A'mm%3'A'ss"));
        return new String[]{
                "https://mpstats.timmi6790.de/java/leaderboards/leaderboard?game=blockhunt&stat=hider%20wins&board=all&date=" + time + "&filtering=true&startPosition=1&endPosition=50",
                "https://mpstats.timmi6790.de/java/leaderboards/leaderboard?game=blockhunt&stat=hunterwins&board=all&date=" + time + "&filtering=true&startPosition=1&endPosition=50",
                "https://mpstats.timmi6790.de/java/leaderboards/leaderboard?game=blockhunt&stat=kills&board=all&date=" + time + "&filtering=true&startPosition=1&endPosition=50"
        };
    }

    //returns 1 if unsuccessful
    private int fetchNewLeaderboards() {
        JsonElement[] leaderboard = getJsonLbData(date);
        if (leaderboard == null)
            return 1;
        try {
            Type listType = new TypeToken<ArrayList<LbSpot>>() {
            }.getType();

            hiderLb = new Gson().fromJson(leaderboard[0], listType);
            hunterLb = new Gson().fromJson(leaderboard[1], listType);
            killsLb = new Gson().fromJson(leaderboard[2], listType);
        } catch (JsonSyntaxException e) {
            return 1;
        }
        return 0;
    }

    private JsonElement[] getJsonLbData(long date) {
        JsonElement[] data = new JsonElement[3];
        try {
            String[] lbUrls = lbURLs(date);
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

                    data[i] = jsonObject.get("leaderboard");
                } else {
                    System.out.println("BLAH" + responseCode + lbUrls[i]);
                    return null;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return data;
    }

    private void lbListSetChanges(String[] data) {
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

    private List<Integer> listChanges(List<String> before, List<String> after) {
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

    private void updateFile() {
        List<LbSpot> hiderLbOld;
        List<LbSpot> hunterLbOld;
        List<LbSpot> killsLbOld;

        JsonElement[] leaderboard = getJsonLbData(date - 604800000);
        if (leaderboard == null) {
            System.out.println("Error fetching leaderboards!");
            return;
        }
        try {
            Type listType = new TypeToken<ArrayList<LbSpot>>(){}.getType();

            hiderLbOld = new Gson().fromJson(leaderboard[0], listType);
            hunterLbOld = new Gson().fromJson(leaderboard[1], listType);
            killsLbOld = new Gson().fromJson(leaderboard[2], listType);
        } catch (JsonSyntaxException e) {
            System.out.println("Json error while parsing old leaderboard data!");
            return;
        }
        try {
            Files.deleteIfExists(path);

            List<String> data = new ArrayList<>(3);
            data.add(Long.toString(date));
            data.add("Hider:" + hiderLbOld.stream().map(LbSpot::getUuid).collect(Collectors.joining(":")));
            data.add("Hunter:" + hunterLbOld.stream().map(LbSpot::getUuid).collect(Collectors.joining(":")));
            data.add("Kills:" + killsLbOld.stream().map(LbSpot::getUuid).collect(Collectors.joining(":")));

            Files.write(path, data, StandardOpenOption.CREATE);

            System.out.println("Updated leaderboards.data.");
        } catch (IOException e) {
            System.out.println("IO error when writing leaderboards data!");
        }
    }

    public void updateLeaderboards() {
        date = ZonedDateTime.now().toInstant().toEpochMilli();

        List<String> lines = new ArrayList<>();
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            System.out.println("IO error when reading leaderboards data! Creating new leaderboards.data file.");
        }

        long date_old;
        if (!lines.isEmpty())
            date_old = Long.parseLong(lines.remove(0));
        else
            date_old = date;

        if(date - date_old > 594800000) {
            updateFile();
            try {
                lines = Files.readAllLines(path);
            } catch (IOException e) {
                System.out.println("IO error! Change will be set to 0 and a new leaderboards.data file weill be created next time.");
                lines.clear();
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ioException) {
                    System.out.println("Couldn't delete old leaderboards.data file!");
                }
            }
        }

        if (fetchNewLeaderboards() == 1) {
            System.out.println("Error reading new Leaderboards!");
            return;
        }

        if (!lines.isEmpty()) {
            for (String s : lines)
                lbListSetChanges(s.split(":"));
        }
        else {
            hiderLb.forEach(s -> s.setChange(0));
            hunterLb.forEach(s -> s.setChange(0));
            killsLb.forEach(s -> s.setChange(0));
        }
    }

    /*public List<String> lbToString(int board) {
        return lbToString(board, null, null);
    }*/

    // 0 = hider, 1 = hunter, 2 = kills
    public List<String> lbToString(int board, String guildID, UserData userData) {
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

        boolean noMentions = false;
        if (guildID == null || userData == null)
            noMentions = true;
        else
            savedUuids = userData.getGuildSavedUuids(guildID);

        List<String> output = new ArrayList<>(5);
        StringBuilder temp = new StringBuilder();

        int i = 1;
        for (LbSpot s : lb) {
            String userID = "";
            if (!noMentions) {
                String uuid = s.getUuid().replace("-", "");
                if (savedUuids.contains(uuid))
                    userID = userData.getGuildSavedUuidUserID(guildID, uuid);
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
}