package com.tfred.moderationbot.moderation;

import net.dv8tion.jda.api.entities.Guild;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ModerationData {
    //TODO handle synchronization more optimal
    private static final Object synchronizeObject = new Object();

    /**
     * Get an array containing the active punishments for a guild.
     * Changes to this array don't reflect back.
     *
     * @param guildID The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @return An array of all active punishments in the specified guild.
     */
    public static TimedPunishment[] getActivePunishments(long guildID) throws IOException {
        synchronized (synchronizeObject) {
            if (Files.exists(Paths.get("moderations/" + guildID + "/active.punishments"))) {
                return Files
                        .readAllLines(Paths.get("moderations/" + guildID + "/active.punishments"))
                        .stream()
                        .map(Punishment::parsePunishment)
                        .filter(p -> (p instanceof TimedPunishment))
                        .map(p -> (TimedPunishment) p)
                        .toArray(TimedPunishment[]::new);
            } else
                return new TimedPunishment[]{};
        }
    }

    /**
     * Get an array containing the punishment history for a user in a specified guild.
     * Changes to this list don't reflect back.
     * This array is sorted from oldest to newest.
     *
     * @param guildID The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param userID  The specified {@link net.dv8tion.jda.api.entities.User user's} ID.
     * @return An array of all past {@link Punishment punishments} for that user in the specified guild.
     */
    public static Punishment[] getUserPunishments(long guildID, long userID) throws IOException {
        synchronized (synchronizeObject) {
            if (Files.exists(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"))) {
                return Files.readAllLines(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"))
                        .stream().map(s -> Punishment.parsePunishment(userID, s)).toArray(Punishment[]::new);
            }
            else
                return new Punishment[]{};
        }
    }

    /**
     * Get an array containing all punishments for a guild.
     *
     * @param guildID The guilds ID.
     * @return An array containing all punishments.
     */
    public static Punishment[] getAllPunishments(long guildID) throws IOException {
        LinkedList<Punishment[]> all = new LinkedList<>();
        List<Long> ids;
        synchronized (synchronizeObject) {
            if (Files.exists(Paths.get("moderations/" + guildID))) {
                ids = Files
                        .find(Paths.get("moderations/" + guildID), 1, (p, bfa) -> p.getFileName().toString().matches("\\d+.punishments"))
                        .map(p -> {
                            String str = p.getFileName().toString();
                            return Long.parseLong(str.substring(0, str.length() - 12));
                        })
                        .collect(Collectors.toList());
            } else
                return new Punishment[]{};
        }

        for (Long id : ids) {
            try {
                all.add(getUserPunishments(guildID, id));
            } catch (IOException ignored) {
                System.out.println("Failed to read punishments for user with ID " + id);
            }
        }
        return all.stream().flatMap(Arrays::stream).toArray(Punishment[]::new);
    }

    /**
     * Write a punishment to the appropriate userID.punishments and active.punishments files.
     *
     * @param guildID    The specified {@link net.dv8tion.jda.api.entities.Guild guild's} ID.
     * @param punishment The punishment to write.
     * @throws IOException If some IO error while creating or writing to the file occurs.
     */
    protected static void savePunishment(long guildID, Punishment punishment) throws IOException {
        synchronized (synchronizeObject) {
            long userID = punishment.userID;
            if (!Files.exists(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"))) {
                if (!Files.exists(Paths.get("moderations/" + guildID)))
                    Files.createDirectories(Paths.get("moderations/" + guildID));
                Files.createFile(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"));
            }
            Files.write(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"), (punishment.toStringWithoutUserID() + '\n').getBytes(), StandardOpenOption.APPEND);

            if (!Files.exists(Paths.get("moderations/" + guildID + "/active.punishments")))
                Files.createFile(Paths.get("moderations/" + guildID + "/active.punishments"));
            try {
                Files.write(Paths.get("moderations/" + guildID + "/active.punishments"), (punishment.toString() + '\n').getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                //Delete the last entry from the file above since that one must've worked.
                List<String> lines = Files.readAllLines(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"));
                lines.remove(lines.size() - 1);
                Files.write(Paths.get("moderations/" + guildID + "/" + userID + ".punishments"), lines);

                throw new IOException(e);
            }
        }
    }

    /**
     * Log a pardon punishment.
     *
     * @param guildID          The guild ID of this pardon.
     * @param pardonPunishment The pardon punishment.
     */
    protected static void savePardon(long guildID, PardonPunishment pardonPunishment) throws ModerationException {
        synchronized (synchronizeObject) {
            if (!Files.exists(Paths.get("moderations/" + guildID + "/" + pardonPunishment.userID + ".punishments")))
                throw new ModerationException("No punishment file for user with ID " + pardonPunishment.userID + " found.");

            try {
                Files.write(Paths.get("moderations/" + guildID + "/" + pardonPunishment.userID + ".punishments"), (pardonPunishment.toStringWithoutUserID() + '\n').getBytes(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new ModerationException("An IO error occurred while logging the pardon (<@470696578403794967>)! " + e.getMessage());
            }
        }
    }

    /**
     * Updates the active.data for the specified guild by removing the specified {@link Punishment punishment} from it.
     *
     * @param guildID      The specified {@link Guild guild}'s ID.
     * @param punishmentID The ID of the punishment to remove.
     * @return The {@link Punishment active punishment} that was removed or null if none was found.
     */
    @Nullable
    protected static TimedPunishment removeActivePunishment(long guildID, int punishmentID) throws IOException {
        TimedPunishment[] punishments = getActivePunishments(guildID);
        if (punishments.length == 0)
            return null;

        TimedPunishment removedPunishment = null;
        for (TimedPunishment p : punishments) {
            if (p.id == punishmentID) {
                removedPunishment = p;
                synchronized (synchronizeObject) {
                    Files.write(
                            Paths.get("moderations/" + guildID + "/active.punishments"),
                            Arrays.stream(punishments)
                                    .filter(px -> px.id != punishmentID)
                                    .map(Punishment::toString)
                                    .collect(Collectors.toList())
                    );
                }
                break;
            }
        }

        return removedPunishment;
    }
}
