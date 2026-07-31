<template>
  <!-- Toast -->
  <div class="ui-toast-wrap">
    <div v-for="t in uiState.toasts" :key="t.id" class="ui-toast">
      <span v-if="t.icon === 'success'" class="ui-toast-icon">✓</span>
      <span class="ui-toast-text">{{ t.title }}</span>
    </div>
  </div>

  <!-- Loading -->
  <div v-if="uiState.loading.show" class="ui-mask">
    <div class="ui-loading">
      <div class="ui-spinner"></div>
      <div v-if="uiState.loading.title" class="ui-loading-text">{{ uiState.loading.title }}</div>
    </div>
  </div>

  <!-- Modal -->
  <div v-if="uiState.modal" class="ui-mask" @click.self="onCancel">
    <div class="ui-modal" :class="uiState.modal.size">
      <span v-if="uiState.modal.showClose" class="ui-modal-x" @click="onClose">×</span>
      <div v-if="uiState.modal.title" class="ui-modal-title">{{ uiState.modal.title }}</div>
      <!-- 正文为空则整行不渲染（0731：允许"标题即问题"的一行式弹窗，不留空行占位） -->
      <div v-if="!uiState.modal.editable && uiState.modal.content" class="ui-modal-content" :class="{ bold: uiState.modal.contentBold }">{{ uiState.modal.content }}</div>
      <div v-if="uiState.modal.meta && !uiState.modal.editable" class="ui-modal-meta">{{ uiState.modal.meta }}</div>
      <textarea v-if="uiState.modal.editable" class="ui-modal-input" v-model="editText" :placeholder="uiState.modal.placeholderText"></textarea>
      <div class="ui-modal-actions" :class="{ 'emphasize-confirm': uiState.modal.emphasizeConfirm, 'emphasize-cancel': uiState.modal.emphasizeCancel }">
        <button v-if="uiState.modal.showCancel" class="ui-modal-btn cancel" @click="onCancel">{{ uiState.modal.cancelText }}</button>
        <button class="ui-modal-btn confirm" @click="onConfirm">{{ uiState.modal.confirmText }}</button>
      </div>
    </div>
  </div>

  <!-- ActionSheet -->
  <div v-if="uiState.actionSheet" class="ui-mask sheet-mask" @click.self="onSheetCancel">
    <div class="ui-sheet" :class="uiState.actionSheet.variant">
      <div v-if="uiState.actionSheet.title || uiState.actionSheet.description" class="ui-sheet-head">
        <div v-if="uiState.actionSheet.title" class="ui-sheet-title">{{ uiState.actionSheet.title }}</div>
        <div v-if="uiState.actionSheet.description" class="ui-sheet-desc">{{ uiState.actionSheet.description }}</div>
      </div>
      <button v-for="(item, idx) in uiState.actionSheet.itemList" :key="idx" class="ui-sheet-item" :class="[typeof item === 'object' ? item.tone : '', { selected: typeof item === 'object' && item.selected }]" @click="onSheetTap(idx)">
        <span v-if="typeof item === 'object' && item.icon" class="ui-sheet-icon">{{ item.icon }}</span>
        <span class="ui-sheet-copy">
          <b>{{ typeof item === 'object' ? item.label : item }}</b>
          <small v-if="typeof item === 'object' && item.description">{{ item.description }}</small>
        </span>
        <span v-if="typeof item === 'object' && item.selected" class="ui-sheet-check">✓</span>
        <!-- picker 变体默认不带箭头；item.arrow=true 的行是「去别处」的入口（如 ＋填写其他地点），照常给 › -->
        <span v-else-if="typeof item === 'object' && (item.arrow || uiState.actionSheet.variant !== 'picker')" class="ui-sheet-arrow">›</span>
      </button>
      <button class="ui-sheet-item cancel" @click="onSheetCancel">{{ uiState.actionSheet.cancelText }}</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { uiState, resolveModal, resolveActionSheet } from '@/utils/ui'

const editText = ref('')
watch(() => uiState.modal, (m) => { editText.value = m && m.editable ? (m.content || '') : '' })

function onConfirm() { resolveModal({ confirm: true, content: editText.value }) }
function onCancel() { resolveModal({ confirm: false, cancel: true }) }
function onClose() { resolveModal({ confirm: false, close: true }) }
function onSheetTap(idx) { resolveActionSheet({ tapIndex: idx }) }
function onSheetCancel() { resolveActionSheet({ tapIndex: -1, cancel: true }) }
</script>

<style scoped>
.ui-toast-wrap { position: fixed; top: 0; left: 0; right: 0; bottom: 0; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 16rpx; pointer-events: none; z-index: 3000; }
.ui-toast { max-width: 70vw; background: rgba(0,0,0,0.75); color: #fff; padding: 20rpx 32rpx; border-radius: 12rpx; font-size: 28rpx; display: flex; align-items: center; gap: 12rpx; }
.ui-toast-icon { font-weight: 700; }

.ui-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 3100; }
.sheet-mask { align-items: flex-end; }

.ui-loading { background: rgba(0,0,0,0.75); color: #fff; padding: 40rpx; border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; gap: 20rpx; }
.ui-spinner { width: 56rpx; height: 56rpx; border: 6rpx solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: ui-spin .8s linear infinite; }
.ui-loading-text { font-size: 28rpx; }
@keyframes ui-spin { to { transform: rotate(360deg); } }

.ui-modal { position: relative; width: 600rpx; max-width: 84vw; background: #fff; border-radius: 24rpx; padding: 44rpx 40rpx 0; }
.ui-modal-x { position: absolute; top: 16rpx; right: 20rpx; width: 68rpx; height: 68rpx; display: flex; align-items: center; justify-content: center; font-size: 52rpx; line-height: 1; color: #999; }
.ui-modal-title { font-size: 34rpx; font-weight: 700; text-align: center; color: #1a1a1a; }
.ui-modal-content { font-size: 30rpx; color: #555; line-height: 1.7; margin-top: 24rpx; text-align: center; white-space: pre-wrap; }
/* 正文加粗加深加大（contentBold）：需强调的确认弹窗用 */
.ui-modal-content.bold { color: #1a1a1a; font-weight: 700; font-size: 38rpx; }
.ui-modal-input { width: 100%; min-height: 160rpx; margin-top: 24rpx; background: #f6f6f8; border-radius: 12rpx; padding: 20rpx; font-size: 30rpx; border: none; resize: none; box-sizing: border-box; }
.ui-modal-actions { display: flex; margin-top: 40rpx; border-top: 1rpx solid #eee; }
.ui-modal-btn { flex: 1; padding: 28rpx 0; font-size: 32rpx; background: none; }
.ui-modal-btn.cancel { color: #666; border-right: 1rpx solid #eee; }
.ui-modal-btn.confirm { color: var(--c-primary-dark); font-weight: 600; }
/* 突出确认（emphasizeConfirm）：取消收窄、确认占大头，并加粗强调 */
.ui-modal-actions.emphasize-confirm .ui-modal-btn.cancel { flex: 0 0 34%; }
.ui-modal-actions.emphasize-confirm .ui-modal-btn.confirm { flex: 1; font-weight: 700; }
/* 突出取消（emphasizeCancel）：按钮位置保持通用习惯——取消类(继续等待)在左、确认动作(确认关闭)在右，
   突出只靠颜色字重：安全选项主色加粗，破坏性确认灰化为次要（0723 用户定：不再 row-reverse 换位） */
.ui-modal-actions.emphasize-cancel .ui-modal-btn.cancel { color: var(--c-primary-dark); font-weight: 700; }
.ui-modal-actions.emphasize-cancel .ui-modal-btn.confirm { color: #999; font-weight: 400; }
/* 加大版弹窗（size:'large'）：识别结果等重要确认框——大字、纯黑、选项加粗 */
.ui-modal.large { width: 660rpx; max-width: 92vw; border-radius: 28rpx; padding: 52rpx 44rpx 0; }
.ui-modal.large .ui-modal-title { font-size: 44rpx; color: #000; font-weight: 600; }
/* 大号编辑弹窗（修改/重写意见）：文本框多留几行，便于阅读长意见 */
.ui-modal.large .ui-modal-input { min-height: 320rpx; }
.ui-modal.large .ui-modal-content { font-size: 36rpx; color: #000; line-height: 1.8; margin-top: 28rpx; }
.ui-modal.large .ui-modal-actions { margin-top: 48rpx; }
.ui-modal.large .ui-modal-btn { padding: 34rpx 0; font-size: 38rpx; font-weight: 700; }
/* 表决二次确认（size:'vote'）：轻量确认，避免抢过投票主界面 */
.ui-modal.vote .ui-modal-content { font-size: 36rpx; font-weight: 400; color: #333; margin-top: 8rpx; line-height: 1.55; }
.ui-modal.vote .ui-modal-btn { padding: 28rpx 0; font-size: 34rpx; }
.ui-modal.vote .ui-modal-btn.confirm { font-weight: 600; }
/* 参会方式确认：无标题、两个清晰选项；确认深蓝实色，取消透明灰色。 */
.ui-modal.attendance { padding:46rpx 40rpx 34rpx; }
.ui-modal.attendance .ui-modal-content { margin-top:0; color:#30343A; font-size:34rpx; line-height:1.65; }
.ui-modal.attendance .ui-modal-actions { gap:20rpx; margin-top:38rpx; border-top:0; }
.ui-modal.attendance .ui-modal-btn { box-sizing:border-box; padding:23rpx 0; border-radius:16rpx; font-size:32rpx; }
.ui-modal.attendance .ui-modal-btn.cancel { border:2rpx solid #C9CDD3; background:transparent; color:#747A82; font-weight:600; }
.ui-modal.attendance .ui-modal-btn.confirm { border:2rpx solid #A85800; background:#A85800; color:#fff; font-weight:700; }
.ui-modal.attendance .ui-modal-btn.confirm:active { background:#8F4A06; border-color:#8F4A06; }
/* 常规动作确认（size:'action'，0731 设计师定）：确认=模块蓝实心（暖橙只留异常态）、高 54px，
   取消=浅底放左边；两钮并排大触区，替代默认的纯文字按钮 */
.ui-modal.action { padding: 44rpx 40rpx 34rpx; }
.ui-modal.action .ui-modal-content { color: #30343A; font-size: 33rpx; }
.ui-modal.action .ui-modal-actions { gap: 20rpx; margin-top: 40rpx; border-top: 0; }
.ui-modal.action .ui-modal-btn { box-sizing: border-box; min-height: 108rpx; padding: 0; border-radius: 16rpx; font-size: 33rpx; }
.ui-modal.action .ui-modal-btn.cancel { border: 0; background: #F1F3F6; color: #4A5560; font-weight: 600; }
.ui-modal.action .ui-modal-btn.cancel:active { background: #E5E9EE; }
.ui-modal.action .ui-modal-btn.confirm { background: #2b5589; color: #fff; font-weight: 700; }
.ui-modal.action .ui-modal-btn.confirm:active { background: #244a79; }

/* AI 帮写/润色完成卡（size:'aicard'）：轻提示，短句+清晰确认 */
.ui-modal.aicard { width: 560rpx; max-width: 82vw; border-radius: 22rpx; padding: 36rpx 36rpx 0; box-shadow: 0 18rpx 54rpx rgba(31,35,41,0.18); }
.ui-modal.aicard .ui-modal-content { font-size: 32rpx; font-weight: 500; color: #1f2329; line-height: 1.45; margin-top: 0; }
.ui-modal-meta { text-align: center; white-space: pre-wrap; }
.ui-modal.aicard .ui-modal-meta { margin-top: 8px; font-size: 26rpx; color: #8A8F98; line-height: 1.45; }
.ui-modal.aicard .ui-modal-actions { margin-top: 30rpx; }
.ui-modal.aicard .ui-modal-btn { padding: 24rpx 0; font-size: 32rpx; }
.ui-modal.aicard .ui-modal-btn.confirm { font-weight: 700; color: #4F8B34; }

.ui-sheet { width: 100%; background: #f4f4f6; padding-bottom: env(safe-area-inset-bottom); }
.ui-sheet-item { display: block; width: 100%; padding: 32rpx 0; font-size: 32rpx; background: #fff; border-bottom: 1rpx solid #eee; color: #1a1a1a; }
.ui-sheet-item.cancel { margin-top: 14rpx; color: #666; font-weight: 600; border-bottom: none; }
.ui-sheet.opinion-change { padding: 0 24rpx calc(20rpx + env(safe-area-inset-bottom)); background: #F5F3EF; border-radius: 32rpx 32rpx 0 0; box-shadow: 0 -12rpx 40rpx rgba(31,35,41,.12); }
/* 底部选择单（variant:'picker'，0731 设计师定）：>5 项/需滚动的选择用底部弹层——选项落在拇指区；
   列表弹层内滚动、选中项浅绿底+绿勾、取消胶囊 sticky 常驻 */
.ui-sheet.picker { max-height: 78vh; overflow-y: auto; -webkit-overflow-scrolling: touch; background: #fff; border-radius: 32rpx 32rpx 0 0; box-shadow: 0 -12rpx 40rpx rgba(31,35,41,.14); }
.ui-sheet.picker .ui-sheet-head { padding: 32rpx 32rpx 24rpx; border-bottom: 2rpx solid #F0F2F5; }
.ui-sheet.picker .ui-sheet-title { font-size: 33rpx; }
.ui-sheet.picker .ui-sheet-item { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-height: 112rpx; padding: 0 32rpx; text-align: left; font-size: 33rpx; color: #1F2937; border-bottom: 2rpx solid #F0F2F5; }
.ui-sheet.picker .ui-sheet-item .ui-sheet-copy b { font-size: 33rpx; font-weight: 500; color: #1F2937; }
.ui-sheet.picker .ui-sheet-item.selected { background: #E8F4EC; }
.ui-sheet.picker .ui-sheet-item.selected .ui-sheet-copy b { font-weight: 750; }
.ui-sheet-check { flex-shrink: 0; font-size: 38rpx; font-weight: 800; color: #2f6b45; }
.ui-sheet.picker .ui-sheet-item.cancel { position: sticky; bottom: 0; margin: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom)); min-height: 96rpx; justify-content: center; border-radius: 18rpx; background: #F1F3F6; color: #4A5560; font-weight: 650; border-bottom: 0; }
.ui-sheet-head { padding: 34rpx 20rpx 26rpx; text-align: left; }
.ui-sheet-title { font-size: 36rpx; line-height: 1.35; font-weight: 700; color: #1F2329; }
.ui-sheet-desc { margin-top: 10rpx; font-size: 27rpx; line-height: 1.55; color: #7A7F87; }
.ui-sheet.opinion-change .ui-sheet-item { min-height: 112rpx; padding: 20rpx 22rpx; margin-bottom: 14rpx; border: 2rpx solid #E9E5DE; border-radius: 18rpx; display: flex; align-items: center; gap: 18rpx; text-align: left; box-shadow: 0 4rpx 14rpx rgba(31,35,41,.04); }
.ui-sheet.opinion-change .ui-sheet-item:active { transform: scale(.99); background: #FAF9F7; }
.ui-sheet-icon { flex: 0 0 64rpx; height: 64rpx; border-radius: 18rpx; display: flex; align-items: center; justify-content: center; background: #F1EEE8; font-size: 34rpx; }
.ui-sheet-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5rpx; }
.ui-sheet-copy b { font-size: 31rpx; line-height: 1.35; color: #25282D; }
.ui-sheet-copy small { font-size: 24rpx; line-height: 1.35; color: #8A8F98; }
.ui-sheet-arrow { color: #B0B4BA; font-size: 42rpx; line-height: 1; }
.ui-sheet.opinion-change .ui-sheet-item.ai { border-color: #D8E6CE; background: #F8FCF5; }
.ui-sheet.opinion-change .ui-sheet-item.ai .ui-sheet-icon { background: #E7F2DF; }
.ui-sheet.opinion-change .ui-sheet-item.danger { border-color: #F1DDDA; background: #FFF9F8; }
.ui-sheet.opinion-change .ui-sheet-item.danger .ui-sheet-copy b { color: #B64B42; }
.ui-sheet.opinion-change .ui-sheet-item.danger .ui-sheet-icon { background: #FBEAE7; }
.ui-sheet.opinion-change .ui-sheet-item.cancel { min-height: 88rpx; justify-content: center; margin: 4rpx 0 0; padding: 22rpx; text-align: center; color: #666B73; box-shadow: none; }
</style>
