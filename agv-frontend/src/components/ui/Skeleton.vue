<script setup lang="ts">
import { cn } from '@/lib/utils'

withDefaults(defineProps<{ class?: string }>(), { class: '' })
</script>

<template>
  <div :class="cn('animate-pulse rounded-lg bg-slate-200/70', $props.class)" aria-hidden="true"></div>
</template>
