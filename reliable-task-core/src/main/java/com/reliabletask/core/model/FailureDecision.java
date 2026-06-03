package com.reliabletask.core.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务失败分类决策。
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FailureDecision {

    public enum Action {
        RETRY,
        DEAD
    }

    private final Action action;

    private final String reason;

    public static FailureDecision retry(String reason) {
        return new FailureDecision(Action.RETRY, reason);
    }

    public static FailureDecision dead(String reason) {
        return new FailureDecision(Action.DEAD, reason);
    }
}
