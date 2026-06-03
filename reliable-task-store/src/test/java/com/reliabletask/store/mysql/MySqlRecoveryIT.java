package com.reliabletask.store.mysql;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlRecoveryIT extends AbstractMySqlIntegrationTest {

    @BeforeEach
    void setUpSchema() {
        initializeSchema();
    }

    @Test
    @DisplayName("MySQL recovery - 未过期 RUNNING 不被扫描或重置")
    void runningTaskWithValidLeaseIsNotRecovered() {
        TaskInstance task = savePendingTask("MYSQL_RECOVERY_VALID", uniqueBizId("VALID"));

        assertThat(taskStore.claimTask(task.getId(), "mysql-valid-worker",
                LocalDateTime.now().plusMinutes(5))).isTrue();
        TaskInstance running = taskStore.getById(task.getId());
        TaskExecutionLease lease = TaskExecutionLease.from(running);

        List<TaskInstance> timeoutTasks = taskStore.findTimeoutTasks(LocalDateTime.now(), 10);

        assertThat(timeoutTasks)
                .extracting(TaskInstance::getId)
                .doesNotContain(task.getId());
        assertThat(taskStore.resetTimeoutTask(lease)).isFalse();
        assertThat(taskStore.getById(task.getId()).getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    @DisplayName("MySQL recovery - 过期 RUNNING 能恢复为 PENDING")
    void expiredRunningTaskIsRecoveredToPending() {
        TaskInstance task = savePendingTask("MYSQL_RECOVERY_EXPIRED", uniqueBizId("EXPIRED"));

        assertThat(taskStore.claimTask(task.getId(), "mysql-expired-worker",
                LocalDateTime.now().minusSeconds(1))).isTrue();
        TaskInstance expired = taskStore.getById(task.getId());
        TaskExecutionLease lease = TaskExecutionLease.from(expired);

        List<TaskInstance> timeoutTasks = taskStore.findTimeoutTasks(LocalDateTime.now(), 10);

        assertThat(timeoutTasks)
                .extracting(TaskInstance::getId)
                .contains(task.getId());
        assertThat(taskStore.resetTimeoutTask(lease)).isTrue();

        TaskInstance recovered = taskStore.getById(task.getId());
        assertThat(recovered.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(recovered.getWorkerId()).isNull();
        assertThat(recovered.getLockedAt()).isNull();
        assertThat(recovered.getLockExpireAt()).isNull();
        assertThat(recovered.getHeartbeatTime()).isNull();
        assertThat(recovered.getVersion()).isEqualTo(expired.getVersion() + 1);
    }

    @Test
    @DisplayName("MySQL recovery - 恢复后旧 Worker 不能覆盖新 Worker 状态")
    void staleWorkerCannotOverwriteAfterRecoveryAndNewClaim() {
        TaskInstance task = savePendingTask("MYSQL_RECOVERY_RACE", uniqueBizId("RACE"));

        assertThat(taskStore.claimTask(task.getId(), "mysql-stale-worker",
                LocalDateTime.now().minusSeconds(1))).isTrue();
        TaskExecutionLease staleLease = TaskExecutionLease.from(taskStore.getById(task.getId()));
        assertThat(taskStore.resetTimeoutTask(staleLease)).isTrue();
        makeImmediatelyExecutable(task.getId());

        assertThat(taskStore.claimTask(task.getId(), "mysql-new-worker",
                LocalDateTime.now().plusMinutes(5))).isTrue();
        TaskInstance newRunning = taskStore.getById(task.getId());
        TaskExecutionLease newLease = TaskExecutionLease.from(newRunning);

        assertThat(taskStore.markSuccess(staleLease)).isFalse();
        assertThat(taskStore.markSuccess(newLease)).isTrue();

        TaskInstance completed = taskStore.getById(task.getId());
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(completed.getWorkerId()).isNull();
        assertThat(completed.getVersion()).isEqualTo(newRunning.getVersion() + 1);
    }

    private TaskInstance savePendingTask(String taskType, String bizId) {
        return taskStore.save(TaskInstance.builder()
                .taskType(taskType)
                .bizType("ORDER")
                .bizId(bizId)
                .bizUniqueKey(taskType + ":ORDER:" + bizId)
                .status(TaskStatus.PENDING)
                .priority(5)
                .payload("{\"source\":\"mysql-recovery-it\"}")
                .executeCount(0)
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.EXPONENTIAL)
                .retryIntervalMs(1000L)
                .nextExecuteTime(LocalDateTime.now().minusSeconds(1))
                .build());
    }

    private void makeImmediatelyExecutable(Long taskId) {
        jdbcTemplate.update(
                "UPDATE reliable_task SET next_execute_time = DATE_SUB(NOW(), INTERVAL 1 SECOND) WHERE id = ?",
                taskId);
    }

    private String uniqueBizId(String suffix) {
        return "MYSQL_RECOVERY_IT_" + suffix + "_" + UUID.randomUUID();
    }
}
