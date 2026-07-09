<template>
  <!-- 生成中：只要没有全屏遮罩正盖着它，就显示可点悬浮胶囊（切走了/遮罩没恢复/落到无遮罩的详情页都算）。
       点它直达目标页看进度/结果 —— 保证「重进会议也有入口」。 -->
  <div v-if="aiTask.active && !aiTask.overlayShown" class="ait-pill running" @click="peekAiTask">
    <span class="ait-spin"></span>
    <span class="ait-txt">{{ aiTask.label }}</span>
    <span class="ait-go">查看 ›</span>
  </div>
  <!-- 完成：可点直达目标页（发起页全屏遮罩正显示完成态时先不重复显示，切走/关遮罩后再兜底） -->
  <div v-else-if="aiTask.done && !aiTask.overlayShown" class="ait-pill done" @click="openAiTaskTarget">
    <span class="ait-ic">✓</span>
    <span class="ait-txt">{{ aiTask.doneLabel }}</span>
    <span class="ait-go">查看 ›</span>
  </div>
  <!-- 失败：可点回发起页重试（同样让位给发起页遮罩） -->
  <div v-else-if="aiTask.failed && !aiTask.overlayShown" class="ait-pill failed" @click="onRetry">
    <span class="ait-ic">⚠</span>
    <span class="ait-txt">{{ aiTask.failLabel }}</span>
    <span class="ait-go">重试 ›</span>
  </div>
</template>

<script setup>
import { aiTask, openAiTaskTarget, clearAiTask, peekAiTask } from '@/composables/aiTask'
import { navigateTo } from '@/utils/navigate'

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
.ait-pill.running { background: #333; color: #fff; cursor: pointer; }
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
