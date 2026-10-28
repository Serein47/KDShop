<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ name: string; image?: string; seed?: number; ratio?: string }>()

/** 没有实拍图时，用标题首字与暖色渐变生成一张"纸样"，保证版面不塌。 */
const palette = [
  ['#e6d3b3', '#c9a97e'],
  ['#ded3c0', '#b9a888'],
  ['#e3cabf', '#c39a86'],
  ['#d8d8c6', '#a8ac90'],
  ['#e8d8c6', '#caa98d'],
]

const style = computed(() => {
  const pair = palette[(props.seed ?? 0) % palette.length]
  return { background: `linear-gradient(145deg, ${pair[0]}, ${pair[1]})` }
})

const glyph = computed(() => (props.name ?? '手').trim().charAt(0))
</script>

<template>
  <div class="media" :style="[{ ...style }, ratio ? { aspectRatio: ratio } : {}]">
    <img v-if="image" :src="image" :alt="name" class="media__img" />
    <span v-else class="media__glyph">{{ glyph }}</span>
    <span class="media__grain" />
  </div>
</template>

<style scoped>
.media__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
