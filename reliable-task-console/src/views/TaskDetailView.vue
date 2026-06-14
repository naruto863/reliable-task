<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import EmptyState from '@/components/state/EmptyState.vue'
import ErrorState from '@/components/state/ErrorState.vue'
import LoadingState from '@/components/state/LoadingState.vue'
import { useConsoleStore } from '@/stores/consoleStore'
import { useTaskDetailStore } from '@/stores/taskDetailStore'
import type { TaskWriteOperation } from '@/stores/taskDetailStore'

const route = useRoute()
const consoleStore = useConsoleStore()
const taskDetailStore = useTaskDetailStore()

type ConfirmAction = TaskWriteOperation | 'updatePayload'

const statusOptions = [
  { label: 'PENDING', value: 0 },
  { label: 'RUNNING', value: 1 },
  { label: 'SUCCESS', value: 2 },
  { label: 'FAILED', value: 3 },
  { label: 'RETRYING', value: 4 },
  { label: 'DEAD', value: 5 },
  { label: 'CANCELLED', value: 6 },
]

const taskId = computed(() => Number(route.params.id))
const detail = computed(() => taskDetailStore.detail)
const payloadView = computed(() => detail.value?.payloadView)
const confirmAction = ref<ConfirmAction | null>(null)
const confirmationText = ref('')
const payloadDraft = ref('')
const payloadValidationError = ref('')

const writeAvailable = computed(() =>
  Boolean(
    consoleStore.capabilities?.writeEnabled &&
      consoleStore.capabilities.authEnabled &&
      consoleStore.capabilities.auditEnabled,
  ),
)

const writeStatusText = computed(() => {
  if (!consoleStore.capabilities) {
    return 'Write actions unavailable: capabilities are not loaded.'
  }
  if (!consoleStore.capabilities.writeEnabled) {
    return 'Write actions unavailable: write capability is disabled.'
  }
  if (!consoleStore.capabilities.authEnabled) {
    return 'Write actions unavailable: authorization is disabled.'
  }
  if (!consoleStore.capabilities.auditEnabled) {
    return 'Write actions unavailable: audit logging is disabled.'
  }
  return 'Write actions enabled with operator, trace id, confirmation header, and audit trail.'
})

const actionTitle = computed(() => {
  if (!confirmAction.value) {
    return ''
  }
  if (confirmAction.value === 'updatePayload') {
    return 'update payload'
  }
  return confirmAction.value
})

const payloadLabel = computed(() => {
  if (!payloadView.value?.payloadVisible) {
    return 'No payload'
  }
  if (payloadView.value.payloadRevealAllowed && payloadView.value.payloadPlaintext) {
    return 'Plaintext allowed'
  }
  return payloadView.value.payloadMasked ? 'Masked preview' : 'Safe preview'
})

const payloadText = computed(() => {
  if (!payloadView.value?.payloadVisible) {
    return 'No payload is attached to this task.'
  }
  if (payloadView.value.payloadRevealAllowed && payloadView.value.payloadPlaintext) {
    return payloadView.value.payloadPlaintext
  }
  return payloadView.value.payloadPreview || 'Payload preview is unavailable.'
})

const detailRows = computed(() => {
  if (!detail.value) {
    return []
  }
  return [
    ['Biz type', detail.value.bizType],
    ['Biz id', detail.value.bizId],
    ['Unique key', detail.value.bizUniqueKey],
    ['Priority', detail.value.priority],
    ['Retries', `${detail.value.executeCount} / ${detail.value.maxRetryCount}`],
    ['Retry strategy', detail.value.retryStrategy],
    ['Retry interval', formatDuration(detail.value.retryIntervalMs)],
    ['Worker', detail.value.workerId],
    ['Tenant', detail.value.tenantId],
    ['Shard key', detail.value.shardKey],
    ['Trace id', detail.value.traceId],
    ['Created', formatDateTime(detail.value.createTime)],
    ['Updated', formatDateTime(detail.value.updateTime)],
    ['Next run', formatDateTime(detail.value.nextExecuteTime)],
    ['Finished', formatDateTime(detail.value.finishTime)],
  ]
})

function statusName(statusCode: number, fallback: string): string {
  return statusOptions.find((item) => item.value === statusCode)?.label || fallback || String(statusCode)
}

function statusClass(statusCode: number): string {
  if (statusCode === 2) {
    return 'status-tag--success'
  }
  if (statusCode === 5 || statusCode === 3) {
    return 'status-tag--danger'
  }
  if (statusCode === 4 || statusCode === 0) {
    return 'status-tag--warning'
  }
  return 'status-tag--neutral'
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function formatDuration(value: number | null): string {
  if (value === null || value === undefined) {
    return '-'
  }
  return `${value} ms`
}

function formatNullable(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

function formatError(value: string | null): string {
  if (!value) {
    return '-'
  }
  return value.length > 120 ? `${value.slice(0, 120)}...` : value
}

function isActionStatusAllowed(action: ConfirmAction): boolean {
  const statusCode = detail.value?.statusCode
  if (statusCode === undefined) {
    return false
  }
  if (action === 'retry' || action === 'requeue') {
    return statusCode === 5 || statusCode === 6
  }
  if (action === 'cancel') {
    return statusCode === 0 || statusCode === 1 || statusCode === 3 || statusCode === 4
  }
  return statusCode === 0 || statusCode === 4
}

function actionDisabled(action: ConfirmAction): boolean {
  return !writeAvailable.value || !isActionStatusAllowed(action) || taskDetailStore.operationLoading
}

function actionReason(action: ConfirmAction): string {
  if (!writeAvailable.value) {
    return writeStatusText.value
  }
  if (!isActionStatusAllowed(action)) {
    return 'Task status does not allow this operation.'
  }
  return 'Requires confirmation.'
}

function openConfirm(action: ConfirmAction) {
  confirmAction.value = action
  confirmationText.value = ''
  payloadValidationError.value = ''
  payloadDraft.value = ''
}

function closeConfirm() {
  confirmAction.value = null
  confirmationText.value = ''
  payloadValidationError.value = ''
  payloadDraft.value = ''
}

function validatePayloadDraft(): string | null {
  try {
    return JSON.stringify(JSON.parse(payloadDraft.value))
  } catch {
    payloadValidationError.value = 'Payload must be valid JSON'
    return null
  }
}

async function confirmOperation() {
  if (!confirmAction.value || confirmationText.value !== 'CONFIRM') {
    return
  }
  payloadValidationError.value = ''

  if (confirmAction.value === 'updatePayload') {
    const normalizedPayload = validatePayloadDraft()
    if (!normalizedPayload) {
      return
    }
    await taskDetailStore.updatePayload(normalizedPayload)
  } else {
    await taskDetailStore.runTaskOperation(confirmAction.value)
  }

  if (!taskDetailStore.operationError) {
    closeConfirm()
  }
}

function loadDetail() {
  if (Number.isFinite(taskId.value) && taskId.value > 0) {
    void taskDetailStore.loadTask(taskId.value)
  }
}

onMounted(() => {
  if (import.meta.env.MODE !== 'test') {
    loadDetail()
  }
})
</script>

<template>
  <section class="page-shell" aria-labelledby="task-detail-title">
    <div class="page-panel dashboard-hero">
      <div>
        <RouterLink class="back-link" :to="{ name: 'tasks' }">Back to tasks</RouterLink>
        <h2 id="task-detail-title" class="page-panel__title">
          Task #{{ Number.isFinite(taskId) ? taskId : '-' }}
        </h2>
        <p class="page-panel__text">Console-safe task detail, execution evidence, and audit trail.</p>
      </div>
      <button class="state__action dashboard-controls__refresh" type="button" @click="loadDetail">
        Refresh
      </button>
    </div>

    <LoadingState v-if="taskDetailStore.loading" message="Loading task detail" />
    <ErrorState v-else-if="taskDetailStore.error" :state="taskDetailStore.error" @retry="loadDetail" />

    <template v-else-if="detail">
      <div class="detail-grid">
        <section class="page-panel detail-panel" aria-labelledby="task-summary-title">
          <div class="detail-panel__header">
            <div>
              <h3 id="task-summary-title">Summary</h3>
              <p>{{ detail.taskType }}</p>
            </div>
            <span class="status-tag" :class="statusClass(detail.statusCode)">
              {{ statusName(detail.statusCode, detail.statusDesc) }}
            </span>
          </div>
          <dl class="detail-list">
            <template v-for="[label, value] in detailRows" :key="label">
              <dt>{{ label }}</dt>
              <dd>{{ formatNullable(value) }}</dd>
            </template>
          </dl>
          <div class="error-summary" v-if="detail.errorMsg">
            <strong>Error summary</strong>
            <p>{{ formatError(detail.errorMsg) }}</p>
          </div>
        </section>

        <section class="page-panel detail-panel" aria-labelledby="payload-title">
          <div class="detail-panel__header">
            <div>
              <h3 id="payload-title">Payload</h3>
              <p>{{ payloadLabel }}</p>
            </div>
            <button
              class="state__action state__action--secondary"
              type="button"
              :disabled="!payloadView?.payloadRevealAllowed"
            >
              {{ payloadView?.payloadRevealAllowed ? 'Reveal allowed' : 'Reveal disabled' }}
            </button>
          </div>
          <pre class="payload-preview">{{ payloadText }}</pre>
          <p class="payload-meta">
            Length {{ payloadView?.payloadLength ?? 0 }} chars ·
            {{ payloadView?.payloadMasked ? 'masked' : 'not masked' }}
          </p>
        </section>
      </div>

      <section class="page-panel detail-panel" aria-labelledby="task-actions-title">
        <div class="detail-panel__header">
          <div>
            <h3 id="task-actions-title">Task Actions</h3>
            <p>{{ writeStatusText }}</p>
          </div>
        </div>
        <div class="action-grid">
          <button
            class="state__action"
            type="button"
            :disabled="actionDisabled('retry')"
            :title="actionReason('retry')"
            @click="openConfirm('retry')"
          >
            Retry
          </button>
          <button
            class="state__action"
            type="button"
            :disabled="actionDisabled('cancel')"
            :title="actionReason('cancel')"
            @click="openConfirm('cancel')"
          >
            Cancel
          </button>
          <button
            class="state__action"
            type="button"
            :disabled="actionDisabled('requeue')"
            :title="actionReason('requeue')"
            @click="openConfirm('requeue')"
          >
            Requeue
          </button>
          <button
            class="state__action"
            type="button"
            :disabled="actionDisabled('updatePayload')"
            :title="actionReason('updatePayload')"
            @click="openConfirm('updatePayload')"
          >
            Update payload
          </button>
        </div>
        <div v-if="taskDetailStore.operationSuccess" class="operation-message operation-message--success">
          {{ taskDetailStore.operationSuccess }}
        </div>
        <ErrorState
          v-if="taskDetailStore.operationError"
          :state="taskDetailStore.operationError"
          @retry="confirmOperation"
        />

        <div v-if="confirmAction" class="confirm-dialog" role="dialog" aria-modal="true">
          <h4>Confirm {{ actionTitle }}</h4>
          <p>
            This operation will send a write request with operator, trace id, and confirmation header.
          </p>
          <label v-if="confirmAction === 'updatePayload'" class="control-field">
            <span>New payload JSON</span>
            <textarea
              v-model="payloadDraft"
              aria-label="New payload JSON"
              rows="7"
              placeholder='{"key":"value"}'
            />
          </label>
          <p v-if="payloadValidationError" class="operation-message operation-message--error">
            {{ payloadValidationError }}
          </p>
          <label class="control-field">
            <span>Type CONFIRM</span>
            <input
              v-model="confirmationText"
              aria-label="Confirmation text"
              autocomplete="off"
              placeholder="CONFIRM"
            />
          </label>
          <div class="confirm-dialog__actions">
            <button
              class="state__action"
              type="button"
              :disabled="confirmationText !== 'CONFIRM' || taskDetailStore.operationLoading"
              @click="confirmOperation"
            >
              Confirm operation
            </button>
            <button class="state__action state__action--secondary" type="button" @click="closeConfirm">
              Close
            </button>
          </div>
        </div>
      </section>

      <section class="page-panel detail-panel" aria-labelledby="logs-title">
        <div class="detail-panel__header">
          <div>
            <h3 id="logs-title">Execution Logs</h3>
            <p>{{ taskDetailStore.logs.length }} records</p>
          </div>
        </div>
        <ErrorState v-if="taskDetailStore.logsError" :state="taskDetailStore.logsError" @retry="loadDetail" />
        <EmptyState
          v-else-if="taskDetailStore.logs.length === 0"
          title="No execution logs"
          description="No execution history has been recorded for this task."
        />
        <ul v-else class="timeline-list">
          <li v-for="log in taskDetailStore.logs" :key="log.id">
            <strong>Attempt {{ log.attemptNo }}</strong>
            <span>{{ formatNullable(log.statusBefore) }} -> {{ formatNullable(log.statusAfter) }}</span>
            <small>
              {{ formatDateTime(log.executeTime) }} · {{ formatDuration(log.durationMs) }} ·
              {{ formatNullable(log.workerId) }}
            </small>
            <span v-if="log.errorCode || log.errorMsg">
              {{ formatNullable(log.errorCode) }}: {{ formatError(log.errorMsg) }}
            </span>
          </li>
        </ul>
      </section>

      <section class="page-panel detail-panel" aria-labelledby="timeline-title">
        <div class="detail-panel__header">
          <div>
            <h3 id="timeline-title">Timeline</h3>
            <p>{{ taskDetailStore.timeline.length }} events</p>
          </div>
        </div>
        <ErrorState v-if="taskDetailStore.timelineError" :state="taskDetailStore.timelineError" @retry="loadDetail" />
        <EmptyState
          v-else-if="taskDetailStore.timeline.length === 0"
          title="No timeline events"
          description="No lifecycle event is available for this task."
        />
        <ul v-else class="timeline-list">
          <li v-for="item in taskDetailStore.timeline" :key="`${item.source}-${item.sourceId}`">
            <strong>{{ item.eventType }}</strong>
            <span>{{ formatNullable(item.statusBefore) }} -> {{ formatNullable(item.statusAfter) }}</span>
            <small>
              {{ formatDateTime(item.eventTime) }} · {{ formatNullable(item.source) }} ·
              {{ formatNullable(item.traceId) }}
            </small>
            <span v-if="item.errorCode || item.errorMsg">
              {{ formatNullable(item.errorCode) }}: {{ formatError(item.errorMsg) }}
            </span>
          </li>
        </ul>
      </section>

      <section class="page-panel detail-panel" aria-labelledby="audit-title">
        <div class="detail-panel__header">
          <div>
            <h3 id="audit-title">Task Audit Logs</h3>
            <p>{{ taskDetailStore.auditLogs.length }} records</p>
          </div>
          <RouterLink class="back-link" :to="{ name: 'auditLogs' }">Open audit search</RouterLink>
        </div>
        <ErrorState v-if="taskDetailStore.auditError" :state="taskDetailStore.auditError" @retry="loadDetail" />
        <EmptyState
          v-else-if="taskDetailStore.auditLogs.length === 0"
          title="No task audit logs"
          description="No manual operation has been recorded for this task."
        />
        <ul v-else class="timeline-list">
          <li v-for="audit in taskDetailStore.auditLogs" :key="audit.id">
            <strong>{{ audit.operationType }}</strong>
            <span>{{ formatNullable(audit.operator) }} · {{ formatNullable(audit.result) }}</span>
            <small>{{ formatDateTime(audit.createTime) }} · {{ formatNullable(audit.traceId) }}</small>
            <span>{{ formatNullable(audit.requestSummary) }}</span>
          </li>
        </ul>
      </section>
    </template>

    <EmptyState
      v-else
      title="Task detail not loaded"
      description="Open a task from the list or refresh this page."
    />
  </section>
</template>
