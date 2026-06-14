<script setup lang="ts">
withDefaults(
  defineProps<{
    message?: string
  }>(),
  {
    message: 'Loading',
  },
)
</script>

<template>
  <div class="state state--loading" role="status" aria-live="polite">
    <span class="state__spinner" aria-hidden="true" />
    <span class="state__message">{{ message }}</span>
  </div>
</template>
