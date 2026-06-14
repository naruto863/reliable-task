<script setup lang="ts">
import { computed, onMounted } from 'vue'

import EmptyState from '@/components/state/EmptyState.vue'
import ErrorState from '@/components/state/ErrorState.vue'
import LoadingState from '@/components/state/LoadingState.vue'
import { useWorkerStore } from '@/stores/workerStore'

const workerStore = useWorkerStore()

const capacityText = computed(() => `${workerStore.availableCapacity} / ${workerStore.totalCapacity}`)

function formatDateTime(value: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function formatNullable(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

function workerStatusClass(status: string | null): string {
  const normalized = (status || '').toUpperCase()
  if (normalized === 'ONLINE' || normalized === 'ACTIVE') {
    return 'status-tag--success'
  }
  if (normalized === 'STALE' || normalized === 'DOWN' || normalized === 'OFFLINE') {
    return 'status-tag--danger'
  }
  return 'status-tag--neutral'
}

function loadWorkers() {
  void workerStore.loadWorkers()
}

onMounted(() => {
  if (import.meta.env.MODE !== 'test') {
    loadWorkers()
  }
})
</script>

<template>
  <section class="page-shell" aria-labelledby="workers-title">
    <div class="page-panel dashboard-hero">
      <div>
        <h2 id="workers-title" class="page-panel__title">Workers</h2>
        <p class="page-panel__text">Worker heartbeat, capacity, and stale worker status.</p>
      </div>
      <button class="state__action dashboard-controls__refresh" type="button" @click="loadWorkers">
        Refresh
      </button>
    </div>

    <LoadingState v-if="workerStore.loading" message="Loading workers" />
    <ErrorState v-else-if="workerStore.error" :state="workerStore.error" @retry="loadWorkers" />

    <template v-else>
      <section class="status-grid dashboard-metrics" aria-label="Worker summary">
        <div class="status-card">
          <p class="status-card__label">Workers</p>
          <p class="status-card__value">{{ workerStore.workers.length }}</p>
        </div>
        <div class="status-card">
          <p class="status-card__label">Running tasks</p>
          <p class="status-card__value">{{ workerStore.runningTaskCount }}</p>
        </div>
        <div class="status-card">
          <p class="status-card__label">Available capacity</p>
          <p class="status-card__value">{{ capacityText }}</p>
        </div>
        <div class="status-card">
          <p class="status-card__label">Stale workers</p>
          <p class="status-card__value">{{ workerStore.staleWorkers.length }}</p>
        </div>
      </section>

      <section class="page-panel detail-panel" aria-labelledby="stale-workers-title">
        <div class="detail-panel__header">
          <div>
            <h3 id="stale-workers-title">Stale workers</h3>
            <p>{{ workerStore.staleWorkers.length }} workers missing recent heartbeat</p>
          </div>
        </div>
        <ErrorState v-if="workerStore.staleError" :state="workerStore.staleError" @retry="loadWorkers" />
        <EmptyState
          v-else-if="workerStore.staleWorkers.length === 0"
          title="No stale workers"
          description="No worker is currently reported as stale."
        />
        <ul v-else class="timeline-list">
          <li v-for="worker in workerStore.staleWorkers" :key="worker.workerId">
            <strong>{{ worker.workerId }}</strong>
            <span>{{ formatNullable(worker.appName) }} · {{ formatNullable(worker.status) }}</span>
            <small>Last heartbeat {{ formatDateTime(worker.lastHeartbeatTime) }}</small>
          </li>
        </ul>
      </section>

      <section class="page-panel table-panel" aria-labelledby="worker-list-title">
        <div class="table-panel__header">
          <h3 id="worker-list-title">Worker list</h3>
        </div>
        <EmptyState
          v-if="workerStore.workers.length === 0"
          title="No workers"
          description="No worker heartbeat has been recorded."
        />
        <div v-else class="table-scroll">
          <table class="task-table">
            <thead>
              <tr>
                <th>Worker</th>
                <th>Status</th>
                <th>Load</th>
                <th>Capacity</th>
                <th>Host</th>
                <th>Last heartbeat</th>
                <th>Started</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="worker in workerStore.workers" :key="worker.workerId">
                <td>
                  <strong>{{ worker.workerId }}</strong>
                  <span>{{ formatNullable(worker.appName) }}</span>
                </td>
                <td>
                  <span class="status-tag" :class="workerStatusClass(worker.status)">
                    {{ formatNullable(worker.status) }}
                  </span>
                </td>
                <td>{{ worker.runningTaskCount }} / {{ worker.maxConcurrency }}</td>
                <td>{{ worker.availableCapacity }} available</td>
                <td>
                  <strong>{{ formatNullable(worker.hostName) }}</strong>
                  <span>{{ formatNullable(worker.ipAddress) }} · pid {{ formatNullable(worker.processId) }}</span>
                </td>
                <td>{{ formatDateTime(worker.lastHeartbeatTime) }}</td>
                <td>{{ formatDateTime(worker.startTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </section>
</template>
