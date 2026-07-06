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
      <div v-if="!uiState.modal.editable" class="ui-modal-content" :class="{ bold: uiState.modal.contentBold }">{{ uiState.modal.content }}</div>
      <textarea v-else class="ui-modal-input" v-model="editText" :placeholder="uiState.modal.placeholderText"></textarea>
      <div class="ui-modal-actions" :class="{ 'emphasize-confirm': uiState.modal.emphasizeConfirm }">
        <button v-if="uiState.modal.showCancel" class="ui-modal-btn cancel" @click="onCancel">{{ uiState.modal.cancelText }}</button>
        <button class="ui-modal-btn confirm" @click="onConfirm">{{ uiState.modal.confirmText }}</button>
      </div>
    </div>
  </div>

  <!-- ActionSheet -->
  <div v-if="uiState.actionSheet" class="ui-mask sheet-mask" @click.self="onSheetCancel">
    <div class="ui-sheet">
      <button v-for="(item, idx) in uiState.actionSheet.itemList" :key="idx" class="ui-sheet-item" @click="onSheetTap(idx)">{{ item }}</button>
      <button class="ui-sheet-item cancel" @click="onSheetCancel">取消</button>
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
/* 加大版弹窗（size:'large'）：识别结果等重要确认框——大字、纯黑、选项加粗 */
.ui-modal.large { width: 660rpx; max-width: 92vw; border-radius: 28rpx; padding: 52rpx 44rpx 0; }
.ui-modal.large .ui-modal-title { font-size: 44rpx; color: #000; }
.ui-modal.large .ui-modal-content { font-size: 36rpx; color: #000; line-height: 1.8; margin-top: 28rpx; }
.ui-modal.large .ui-modal-actions { margin-top: 48rpx; }
.ui-modal.large .ui-modal-btn { padding: 34rpx 0; font-size: 38rpx; font-weight: 700; }
/* 表决二次确认（size:'vote'）：文案精简、字体加大两号，方便老人看清投的是哪项 */
.ui-modal.vote .ui-modal-content { font-size: 38rpx; color: #1a1a1a; margin-top: 8rpx; }
.ui-modal.vote .ui-modal-btn { padding: 32rpx 0; font-size: 40rpx; }
.ui-modal.vote .ui-modal-btn.confirm { font-weight: 700; }

.ui-sheet { width: 100%; background: #f4f4f6; padding-bottom: env(safe-area-inset-bottom); }
.ui-sheet-item { display: block; width: 100%; padding: 32rpx 0; font-size: 32rpx; background: #fff; border-bottom: 1rpx solid #eee; color: #1a1a1a; }
.ui-sheet-item.cancel { margin-top: 14rpx; color: #666; font-weight: 600; border-bottom: none; }
</style>
