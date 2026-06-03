package com.reliabletask.store.mysql;

import com.reliabletask.core.enums.RetryStrategyType;
import com.reliabletask.core.enums.TaskStatus;
import com.reliabletask.core.model.TaskExecutionLease;
import com.reliabletask.core.model.TaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlLeaseCasIT extends AbstractMySqlIntegrationTest {

    @BeforeEach
    void setUpSchema() {
        initializeSchema();
    }

    @Test
    @DisplayName("MySQL lease - 多 Worker 并发 claim 同一任务只有一个成功")
    void concurrentClaimOnlyOneWorkerSucceeds() throws Exception {
        TaskInstance task = savePendingTask("MYSQL_CLAIM", uniqueBizId("CLAIM"));
        int workerCount = 6;
        CyclicBarrier barrier = new CyclicBarrier(workerCount);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Callable<String>> callables = new ArrayList<>();

        for (int i = 0; i < workerCount; i++) {
            String workerId = "mysql-claim-worker-" + i;
            callables.add(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                boolean claimed = taskStore.claimTask(task.getId(), workerId,
                        LocalDateTime.now().plusMinutes(5));
                return claimed ? workerId : null;
            });
        }

        List<String> successfulWorkers = new ArrayList<>();
        try {
            for (Future<String> future : executor.invokeAll(callables)) {
                String workerId = future.get();
                if (workerId != null) {
                    successfulWorkers.add(workerId);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        TaskInstance loaded = taskStore.getById(task.getId());

        assertThat(successfulWorkers).hasSize(1);
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(loaded.getWorkerId()).isEqualTo(successfulWorkers.get(0));
        assertThat(loaded.getExecuteCount()).isEqualTo(1);
        assertThat(loaded.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("MySQL lease - 旧租约回写失败，新租约回写成功")
    void staleLeaseCannotOverwriteNewLease() {
        TaskInstance task = savePendingTask("MYSQL_LEASE_CAS", uniqueBizId("CAS"));
        String oldWorker = "mysql-old-worker";
        String newWorker = "mysql-new-worker";

        assertThat(taskStore.claimTask(task.getId(), oldWorker,
                LocalDateTime.now().minusSeconds(1))).isTrue();
        TaskExecutionLease oldLease = TaskExecutionLease.from(taskStore.getById(task.getId()));

        assertThat(taskStore.markWaitRetry(oldLease, "retryable",
                LocalDateTime.now().minusSeconds(1))).isTrue();
        assertThat(taskStore.claimTask(task.getId(), newWorker,
                LocalDateTime.now().plusMinutes(5))).isTrue();
        TaskInstance newClaimedTask = taskStore.getById(task.getId());
        TaskExecutionLease newLease = TaskExecutionLease.from(newClaimedTask);

        assertThat(taskStore.markSuccess(oldLease)).isFalse();
        assertThat(taskStore.markSuccess(newLease)).isTrue();

        TaskInstance completed = taskStore.getById(task.getId());
        assertThat(completed.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(completed.getWorkerId()).isNull();
        assertThat(completed.getVersion()).isEqualTo(newClaimedTask.getVersion() + 1);
    }

    private TaskInstance savePendingTask(String taskType, String bizId) {
        return taskStore.save(TaskInstance.builder()
                .taskType(taskType)
                .bizType("ORDER")
                .bizId(bizId)
                .bizUniqueKey(taskType + ":ORDER:" + bizId)
                .status(TaskStatus.PENDING)
                .priority(5)
                .payload("{\"source\":\"mysql-lease-it\"}")
                .executeCount(0)
                .maxRetryCount(3)
                .retryStrategy(RetryStrategyType.EXPONENTIAL)
                .retryIntervalMs(1000L)
                .nextExecuteTime(LocalDateTime.now().minusSeconds(1))
                .build());
    }

    private String uniqueBizId(String suffix) {
        return "MYSQL_LEASE_IT_" + suffix + "_" + UUID.randomUUID();
    }
}
