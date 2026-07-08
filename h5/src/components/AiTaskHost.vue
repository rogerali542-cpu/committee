<template>
  <!-- 生成中：仅在「已切离发起页」时显示悬浮胶囊（发起页有全屏遮罩，避免重复） -->
  <div v-if="aiTask.active && curPath !== aiTask.originPath" class="ait-pill running">
    <span class="ait-spin"></span>
    <span class="ait-txt">{{ aiTask.label }}</span>
  </div>
  <!-- 完成：可点直达目标页 -->
  <div v-else-if="aiTask.done" class="ait-pill done" @click="openAiTaskTarget">
    <span class="ait-ic">✓</span>
    <span class="ait-txt">{{ aiTask.doneLabel }}</span>
    <span class="ait-go">查看 ›</span>
  </div>
  <!-- 失败：可点回发起页重试 -->
  <div v-else-if="aiTask.failed" class="ait-pill failed" @click="onRetry">
    <span class="ait-ic">⚠</span>
    <span class="ait-txt">{{ aiTask.failLabel }}</span>
    <span class="ait-go">重试 ›</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { aiTask, openAiTaskTarget, clearAiTask } from '@/composables/aiTask'
import { navigateTo } from '@/utils/navigate'

const route = useRoute()
const curPath = computed(() => route.path)

function onRetry() {
  const origin = aiTask.originPath
  clearAiTask()
  if (origin) navigateTo(origin)
}
</script>

<style scoped>
.ait-pill {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(74px + env(safe-area-inset-bottom, 0px)); /* TabBar 之上 */
  z-index: 9000;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 86%;
  padding: 10px 18px;
  border-radius: 22px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.18);
  font-size: 14px;
  font-weight: 600;
  animation: ait-in 0.24s ease;
}
@keyframes ait-in { from { opacity: 0; transform: translate(-50%, 10px); } to { opacity: 1; transform: translate(-50%, 0); } }
.ait-pill.running { background: #333; color: #fff; }
.ait-pill.done { background: #1F9E5A; color: #fff; cursor: pointer; }
.ait-pill.failed { background: #C0141B; color: #fff; cursor: pointer; }
.ait-txt { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ait-go { opacity: 0.9; flex-shrink: 0; }
.ait-ic { font-weight: 700; flex-shrink: 0; }
.ait-spin {
  width: 14px; height: 14px; flex-shrink: 0;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: ait-spin 0.8s linear infinite;
}
@keyframes ait-spin { to { transform: rotate(360deg); } }
</style>
