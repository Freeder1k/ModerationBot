package com.tfred.moderationbot.moderation;

import org.apache.commons.text.StringEscapeUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OldPunishment {
    public static Punishment parseOldPunishment(String string) {
        Pattern p = Pattern.compile("(\\d+): (\\d+) (.) (\\d+) (-?\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        try {
            switch (m.group(3)) {
                case "1":
                case "2":
                case "3":
                case "4":
                case "5":
                    return new MutePunishment(
                            Long.parseLong(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Long.parseLong(m.group(4)),
                            Long.parseLong(m.group(6)),
                            Short.parseShort(m.group(3)),
                            Integer.parseInt(m.group(5)),
                            StringEscapeUtils.unescapeJava(m.group(7))
                    );
                case "6":
                    return new BanPunishment(
                            Long.parseLong(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Long.parseLong(m.group(4)),
                            Long.parseLong(m.group(6)),
                            (short) 2,
                            Integer.parseInt(m.group(5)),
                            StringEscapeUtils.unescapeJava(m.group(7))
                    );
                case "v":
                    return new ChannelBanPunishment(
                            Long.parseLong(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Long.parseLong(m.group(4)),
                            Long.parseLong(m.group(6)),
                            719221573944344637L, //Note that this is guild specific
                            Integer.parseInt(m.group(5)),
                            StringEscapeUtils.unescapeJava(m.group(7))
                    );
                case "n":
                    return new NamePunishment(
                            Long.parseLong(m.group(1)),
                            Integer.parseInt(m.group(2)),
                            Long.parseLong(m.group(4)),
                            Long.parseLong(m.group(6)),
                            Integer.parseInt(m.group(5)),
                            StringEscapeUtils.unescapeJava(m.group(7))
                    );
                default:
                    return null;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Punishment parseOldPunishment(long userID, String string)  {
        Pattern p = Pattern.compile("(\\d+) (.) (\\d+) (-?\\d+) (\\d+) (.*)");
        Matcher m = p.matcher(string);

        if (!m.find())
            return null;

        try {
            switch (m.group(2)) {
                case "1":
                case "2":
                case "3":
                case "4":
                case "5":
                    return new MutePunishment(
                            userID,
                            Integer.parseInt(m.group(1)),
                            Long.parseLong(m.group(3)),
                            Long.parseLong(m.group(5)),
                            Short.parseShort(m.group(2)),
                            Integer.parseInt(m.group(4)),
                            StringEscapeUtils.unescapeJava(m.group(6))
                    );
                case "6":
                    return new BanPunishment(
                            userID,
                            Integer.parseInt(m.group(1)),
                            Long.parseLong(m.group(3)),
                            Long.parseLong(m.group(5)),
                            (short) 2,
                            Integer.parseInt(m.group(4)),
                            StringEscapeUtils.unescapeJava(m.group(6))
                    );
                case "v":
                    return new ChannelBanPunishment(
                            userID,
                            Integer.parseInt(m.group(1)),
                            Long.parseLong(m.group(3)),
                            Long.parseLong(m.group(5)),
                            719221573944344637L, //Note that this is guild specific
                            Integer.parseInt(m.group(4)),
                            StringEscapeUtils.unescapeJava(m.group(6))
                    );
                case "n":
                    return new NamePunishment(
                            userID,
                            Integer.parseInt(m.group(1)),
                            Long.parseLong(m.group(3)),
                            Long.parseLong(m.group(5)),
                            Integer.parseInt(m.group(4)),
                            StringEscapeUtils.unescapeJava(m.group(6))
                    );
                case "u":
                    Pattern p2 = Pattern.compile("([y,n]) (\\d+) (.) (.*)");
                    Matcher m2 = p.matcher(m.group(6));
                    if (!m2.find())
                        return null;

                    return new PardonPunishment(
                            userID,
                            Integer.parseInt(m.group(1)),
                            Long.parseLong(m.group(3)),
                            Long.parseLong(m.group(5)),
                            m2.group(1).charAt(0) == 'y',
                            Integer.parseInt(m2.group(2)),
                            m2.group(3).charAt(0),
                            StringEscapeUtils.unescapeJava(m2.group(4))
                    );
                default:
                    return null;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}