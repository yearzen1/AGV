<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    padded?: boolean
    hover?: boolean
  }>(),
  { padded: true, hover: false }
)

const classes = computed(() =>
  cn(
    'min-w-0 rounded-2xl border border-border bg-card shadow-card',
    props.padded && 'p-5',
    props.hover && 'transition duration-200 hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-float'
  )
)
</script>

<template>
  <div :class="classes">
    <slot />
  </div>
</template>
