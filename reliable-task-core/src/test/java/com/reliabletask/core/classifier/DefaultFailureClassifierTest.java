package com.reliabletask.core.classifier;

import com.reliabletask.core.exception.NonRetryableException;
import com.reliabletask.core.model.FailureDecision;
import com.reliabletask.core.model.TaskInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DefaultFailureClassifier 测试")
class DefaultFailureClassifierTest {

    private final DefaultFailureClassifier classifier = new DefaultFailureClassifier();

    @Test
    @DisplayName("classify - NonRetryableException 判定为 DEAD")
    void classify_nonRetryableException_returnsDead() {
        FailureDecision decision = classifier.classify(TaskInstance.builder().id(1L).build(),
                new NonRetryableException("invalid"));

        assertEquals(FailureDecision.Action.DEAD, decision.getAction());
    }

    @Test
    @DisplayName("classify - 普通异常判定为 RETRY")
    void classify_normalException_returnsRetry() {
        FailureDecision decision = classifier.classify(TaskInstance.builder().id(1L).build(),
                new RuntimeException("temporary"));

        assertEquals(FailureDecision.Action.RETRY, decision.getAction());
    }
}
