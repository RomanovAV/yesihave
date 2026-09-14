package org.avromanov.yesihave.bot;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class BotSession {
    private TelegramSessionState state = TelegramSessionState.IDLE;
    private BotAction action = BotAction.NONE;
    private byte[] frontImage;
    private byte[] backImage;
    private UUID checkRequestId;
    private Set<UUID> candidateIds = Set.of();
    private Instant lastTouched = Instant.now();

    public TelegramSessionState state() {
        return state;
    }

    public void setState(TelegramSessionState state) {
        this.state = state;
        touch();
    }

    public BotAction action() {
        return action;
    }

    public void setAction(BotAction action) {
        this.action = action;
        touch();
    }

    public byte[] frontImage() {
        return frontImage;
    }

    public void setFrontImage(byte[] frontImage) {
        this.frontImage = frontImage;
        touch();
    }

    public byte[] backImage() {
        return backImage;
    }

    public void setBackImage(byte[] backImage) {
        this.backImage = backImage;
        touch();
    }

    public UUID checkRequestId() {
        return checkRequestId;
    }

    public Set<UUID> candidateIds() {
        return candidateIds;
    }

    public void prepareFeedback(UUID checkRequestId, Set<UUID> candidateIds) {
        this.state = TelegramSessionState.WAIT_FEEDBACK;
        this.action = BotAction.NONE;
        this.frontImage = null;
        this.backImage = null;
        this.checkRequestId = checkRequestId;
        this.candidateIds = Set.copyOf(candidateIds);
        touch();
    }

    public boolean isExpired(Instant now, Duration ttl) {
        return !lastTouched.plus(ttl).isAfter(now);
    }

    public void reset() {
        this.state = TelegramSessionState.IDLE;
        this.action = BotAction.NONE;
        this.frontImage = null;
        this.backImage = null;
        this.checkRequestId = null;
        this.candidateIds = Set.of();
        touch();
    }

    private void touch() {
        lastTouched = Instant.now();
    }
}
