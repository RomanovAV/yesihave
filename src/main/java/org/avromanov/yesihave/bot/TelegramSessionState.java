package org.avromanov.yesihave.bot;

public enum TelegramSessionState {
    IDLE,
    WAIT_FRONT,
    WAIT_BACK,
    WAIT_NAME,
    WAIT_CHECKED_NAME,
    WAIT_FEEDBACK
}
