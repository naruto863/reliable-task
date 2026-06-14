<script setup lang="ts">
import type { AppErrorState } from '@/utils/errorState'

defineProps<{
  state: AppErrorState
}>()

defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="state state--error" role="alert">
    <p class="state__title">{{ state.title }}</p>
    <p class="state__message">{{ state.message }}</p>
    <button class="state__action" type="button" @click="$emit('retry')">Retry</button>
  </div>
</template>
