<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import EmptyState from '@/components/state/EmptyState.vue'

const route = useRoute()

const title = computed(() => String(route.meta.title || 'ReliableTask Console'))
const description = computed(() => String(route.meta.description || 'View shell'))
</script>

<template>
  <section class="page-shell" :aria-labelledby="`${String(route.name)}-title`">
    <EmptyState :title="title" :description="description" />
  </section>
</template>
