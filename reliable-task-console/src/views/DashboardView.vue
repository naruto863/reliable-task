<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'

import EmptyState from '@/components/state/EmptyState.vue'
import ErrorState from '@/components/state/ErrorState.vue'
import LoadingState from '@/components/state/LoadingState.vue'
import { useDashboardStore } from '@/stores/dashboardStore'

const dashboardStore = useDashboardStore()

// 指标卡是 TaskStats 的只读投影，缺失数据统一展示 '-'，避免页面在首次加载前闪烁 undefined。
const metricCards = computed(() => {
  const stats = dashboardStore.stats
  return [
    {
      label: 'Total tasks',
      value: formatNumber(stats?.totalTasks),
    },
    {
      label: 'Backlog',
      value: formatNumber(stats?.pendingTasks),
    },
    {
      label: 'Dead',
      value: formatNumber(stats?.deadTasks),
    },
    {
      label: 'Oldest pending',
      value: formatAge(stats?.oldestPendingAgeSeconds),
    },
    {
      label: 'Today new',
      value: formatNumber(stats?.todayNewTasks),
    },
    {
      label: 'Today failed',
      value: formatNumber(stats?.todayFailedTasks),
    },
  ]
})

const troubleshootingPaths = [
  {
    title: 'Backlog growth',
    summary: 'Check pending backlog, oldest age, worker capacity, and recent failures.',
    to: { name: 'tasks', query: { status: '0' } },
    action: 'Open pending tasks',
  },
  {
    title: 'Dead task spike',
    summary: 'Start from DEAD tasks, then inspect representative detail logs and timeline.',
    to: { name: 'tasks', query: { status: '5' } },
    action: 'Open dead tasks',
  },
  {
    title: 'Retry storm',
    summary: 'Review RETRYING tasks and compare against failure hotspots.',
    to: { name: 'tasks', query: { status: '4' } },
    action: 'Open retrying tasks',
  },
  {
    title: 'Worker stale or missing',
    summary: 'Check heartbeat freshness, capacity, and stale worker list.',
    to: { name: 'workers' },
    action: 'Open workers',
  },
]

function formatNumber(value: number | null | undefined): string {
  return typeof value === 'number' ? new Intl.NumberFormat().format(value) : '-'
}

function formatAge(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) {
    return '0s'
  }
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 0) {
    return `${hours}h ${minutes}m`
  }
  if (minutes > 0) {
    return `${minutes}m`
  }
  return `${Math.floor(seconds)}s`
}

function refresh() {
  void dashboardStore.loadDashboard()
}

function onLimitInput(event: Event) {
  const target = event.target as HTMLInputElement
  dashboardStore.setLimit(Number(target.value))
}

function onWindowChange(event: Event) {
  const target = event.target as HTMLSelectElement
  dashboardStore.setWindowHours(target.value ? Number(target.value) : null)
}

onMounted(() => {
  // 避免重复请求：如果 store 已经有 dashboard 数据，路由回来时直接复用现有快照。
  if (import.meta.env.MODE !== 'test' && !dashboardStore.hasData && !dashboardStore.loading) {
    refresh()
  }
})
</script>

<template>
  <section class="page-shell" aria-labelledby="dashboard-title">
    <div class="page-panel dashboard-hero">
      <div>
        <h2 id="dashboard-title" class="page-panel__title">Dashboard</h2>
        <p class="page-panel__text">Backlog, dead tasks, queue age, and failure hotspots.</p>
      </div>

      <div class="dashboard-controls" aria-label="Dashboard filters">
        <label class="control-field">
          <span>Window</span>
          <select :value="dashboardStore.windowHours ?? ''" @change="onWindowChange">
            <option value="">Default</option>
            <option value="1">1h</option>
            <option value="24">24h</option>
            <option value="72">72h</option>
          </select>
        </label>
        <label class="control-field">
          <span>Limit</span>
          <input
            min="1"
            max="50"
            type="number"
            :value="dashboardStore.limit"
            @input="onLimitInput"
          />
        </label>
        <button class="state__action dashboard-controls__refresh" type="button" @click="refresh">
          Refresh
        </button>
      </div>
    </div>

    <LoadingState v-if="dashboardStore.loading" message="Loading dashboard" />
    <ErrorState v-else-if="dashboardStore.error" :state="dashboardStore.error" @retry="refresh" />

    <div class="status-grid dashboard-metrics">
      <article v-for="card in metricCards" :key="card.label" class="status-card">
        <p class="status-card__label">{{ card.label }}</p>
        <p class="status-card__value">{{ card.value }}</p>
      </article>
    </div>

    <section class="page-panel dashboard-panel" aria-labelledby="troubleshooting-title">
      <div class="dashboard-panel__header">
        <h3 id="troubleshooting-title">Troubleshooting paths</h3>
      </div>
      <div class="troubleshooting-grid">
        <RouterLink
          v-for="path in troubleshootingPaths"
          :key="path.title"
          class="troubleshooting-link"
          :to="path.to"
        >
          <strong>{{ path.title }}</strong>
          <span>{{ path.summary }}</span>
          <small>{{ path.action }}</small>
        </RouterLink>
      </div>
    </section>

    <div class="dashboard-panels">
      <section class="page-panel dashboard-panel" aria-labelledby="failure-top-title">
        <div class="dashboard-panel__header">
          <h3 id="failure-top-title">Failure top</h3>
        </div>
        <EmptyState
          v-if="dashboardStore.failureTop.length === 0"
          title="No failure hotspots"
          description="No grouped failure records are available for the selected window."
        />
        <ol v-else class="data-list">
          <li v-for="item in dashboardStore.failureTop" :key="`${item.taskType}-${item.errorCode}`">
            <div>
              <strong>{{ item.taskType || 'Unknown task' }}</strong>
              <span>{{ item.errorCode || 'Unknown error' }}</span>
            </div>
            <b>{{ formatNumber(item.failureCount) }}</b>
          </li>
        </ol>
      </section>

      <section class="page-panel dashboard-panel" aria-labelledby="recent-failures-title">
        <div class="dashboard-panel__header">
          <h3 id="recent-failures-title">Recent failures</h3>
        </div>
        <EmptyState
          v-if="dashboardStore.recentFailures.length === 0"
          title="No recent failures"
          description="No failed task executions are available for the selected window."
        />
        <ol v-else class="data-list data-list--stacked">
          <li v-for="item in dashboardStore.recentFailures" :key="item.taskId">
            <div>
              <RouterLink :to="{ name: 'taskDetail', params: { id: item.taskId } }">
                <strong>{{ item.taskType }}</strong>
              </RouterLink>
              <span>#{{ item.taskId }} · {{ item.bizId || 'No biz id' }}</span>
              <small>{{ item.errorMsg || item.errorCode || 'No error message' }}</small>
            </div>
            <b>{{ item.statusAfter || 'FAILED' }}</b>
          </li>
        </ol>
      </section>
    </div>
  </section>
</template>
