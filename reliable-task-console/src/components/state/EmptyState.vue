<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: 'No data',
    description: 'There is no data to show for this view.',
  },
)
</script>

<template>
  <div class="state state--empty" role="status">
    <p class="state__title">{{ title }}</p>
    <p class="state__message">{{ description }}</p>
  </div>
</template>
