package com.tfred.moderationbot.moderation;

public class ModerationException extends Exception {
    public ModerationException(String errorMessage) {
        super(errorMessage);
    }
}