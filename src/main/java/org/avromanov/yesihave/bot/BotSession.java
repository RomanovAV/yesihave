package org.avromanov.yesihave.bot;

public class BotSession {
    private TelegramSessionState state = TelegramSessionState.IDLE;
    private BotAction action = BotAction.NONE;
    private byte[] frontImage;
    private byte[] backImage;

    public TelegramSessionState state() {
        return state;
    }

    public void setState(TelegramSessionState state) {
        this.state = state;
    }

    public BotAction action() {
        return action;
    }

    public void setAction(BotAction action) {
        this.action = action;
    }

    public byte[] frontImage() {
        return frontImage;
    }

    public void setFrontImage(byte[] frontImage) {
        this.frontImage = frontImage;
    }

    public byte[] backImage() {
        return backImage;
    }

    public void setBackImage(byte[] backImage) {
        this.backImage = backImage;
    }

    public void reset() {
        this.state = TelegramSessionState.IDLE;
        this.action = BotAction.NONE;
        this.frontImage = null;
        this.backImage = null;
    }
}
