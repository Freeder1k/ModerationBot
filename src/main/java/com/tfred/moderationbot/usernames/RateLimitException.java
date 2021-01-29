package com.tfred.moderationbot.usernames;

public class RateLimitException extends Exception {
    /**
     * The time left in seconds until the rate limit expires.
     */
    public final int timeLeft;

    RateLimitException(int timeLeft) {
        super("Mojang rate limit reached! Please wait " + timeLeft + " seconds before trying again!");
        this.timeLeft = timeLeft;
    }
}
