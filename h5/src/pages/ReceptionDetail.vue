<template>
  <!-- 根类 .recep-detail 是软路由硬跳兜底的落地哨兵（0716 页头删除后从 .dh-title 迁来，
       Committee.vue / Todo.vue 的跳转检查同步改了）。挂在根上比原来更稳：进页即有，不等接口返回 -->
  <div class="page recep-detail" style="overflow-y:auto;">
    <PageNav title="接待处理" />
    <div v-if="rec">
      <!-- 自有页头已删（0716 用户定：与 PageNav 两条顶栏重复）。状态胶囊挪进信息卡首行右侧 -->
      <div class="info-card">
        <div class="field-row">
          <span class="field-label">来访人</span>
          <span class="field-val">{{ rec.visitorName || '未填写' }}<span v-if="rec.room" class="room">{{ rec.room }}</span></span>
          <span class="stage-pill" :class="rec.done ? 'done' : 'todo'">{{ rec.done ? '已办结' : '待跟进' }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">时间</span>
          <span class="field-val">{{ fmtDate(rec.date) }} {{ (rec.time || '').slice(0, 5) }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">分类</span>
          <span class="field-val">{{ rec.categoryLabel }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">接待人</span>
          <span class="field-val">{{ rec.receiver || '未填写' }}</span>
        </div>
      </div>

      <!-- 居民诉求：这页最该看的东西，独立成块、字最大 -->
      <div class="sec-card">
        <div class="sec-title">居民诉求</div>
        <div class="appeal">{{ rec.content || '未填写' }}</div>
      </div>

      <!-- 派发工单。已派单就只显示单号，不再给按钮——后端虽是幂等的，
           但给老人一个还能点的按钮，他会以为没成功、反复点。
           已办结且没派过单 → 整块不显示（0717 用户定）：事情都完了，不该再给「派发」入口；
           已办结但派过单仍显示单号（留痕）。填完处理结果 rec.done 变 true，这块当场消失。 -->
      <div class="sec-card" v-if="rec.ticketPushed || !rec.done">
        <div class="sec-title">派发工单</div>
        <template v-if="rec.ticketPushed">
          <div class="ticket-done">
            <span class="tk-ico">✓</span>
            <div class="tk-info">
              <div class="tk-no">工单 {{ rec.ticketNo || rec.externalTicketNo }}</div>
              <div class="tk-at">{{ fmtPushedAt(rec.ticketPushedAt) }} 已派发给物业</div>
            </div>
          </div>
          <div class="sec-hint">物业在工单系统里处理。等你知道结果了，在下面填处理结果即可办结。</div>
        </template>
        <!-- 派单/填结果/删除都要 reception.manage：这些是履职动作，不能谁点开谁都能改 -->
        <template v-else-if="canManage">
          <button class="big-action ticket" :disabled="pushing" @click="pushTicket">
            {{ pushing ? '正在派发…' : '派发工单给物业' }}
          </button>
          <div class="sec-hint">派给物业的工单系统去处理。派单不等于办结——等有结果了再回来填处理结果。</div>
        </template>
        <div v-else class="sec-hint">还没有派发工单。你没有接待管理权限，如需派单请联系主任。</div>
      </div>

      <!-- 处理结果 = 办结动作 -->
      <div class="sec-card">
        <div class="sec-title">处理结果<span v-if="canManage" class="sec-tip">填写并保存即算办结</span></div>
        <template v-if="canManage">
          <textarea v-model="resolution" class="res-input" rows="4"
                    placeholder="这件事最后怎么处理的？例如：已派工单给物业，6月28日已完成维修并回访。"></textarea>
          <button class="big-action save" :disabled="saving || !resolution.trim()" @click="saveResolution">
            {{ saving ? '保存中…' : (rec.done ? '保存修改' : '保存并办结') }}
          </button>
        </template>
        <div v-else-if="rec.resolution" class="appeal">{{ rec.resolution }}</div>
        <div v-else class="sec-hint">还没有填写处理结果。</div>
      </div>

      <!-- 佐证：原先接待页这块是坏的——listRecords 从不返回 evidences 键，
           所以永远显示 0 张、永远空状态，哪怕上传成功已落库。后端 toVO 已补上该键 -->
      <div class="sec-card">
        <div class="sec-title">
          佐证照片<span class="sec-count">{{ (rec.evidences || []).length }} 张</span>
          <span v-if="canManage" class="sec-add" @click="pickEvidence">+ 上传</span>
        </div>
        <div v-if="!(rec.evidences || []).length" class="ev-empty">还没有上传佐证</div>
        <div v-else class="ev-list">
          <div v-for="ev in rec.evidences" :key="ev.id" class="ev-item">
            <img v-if="ev.fileUrl" :src="ev.fileUrl" class="ev-thumb" />
            <span v-else class="ev-thumb ev-noimg">📄</span>
            <span class="ev-name">{{ ev.fileName }}</span>
            <span v-if="canManage" class="ev-del" @click="delEvidence(ev)">删除</span>
          </div>
        </div>
      </div>

      <div v-if="canManage" class="danger-zone">
        <span class="del-record" @click="removeRecord">删除这条接待记录</span>
      </div>

      <!-- 返回：用户要的「发完快速返回继续处理下一件」。做成常驻底栏，不用去够左上角 -->
      <div class="back-bar-space"></div>
      <div class="back-bar">
        <button class="back-btn" @click="goBack">‹ 返回，继续处理下一件</button>
      </div>
    </div>
    <div v-else-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <div v-else class="page-empty">加载中…</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast, showModal } from '@/utils/ui'
import { pickAndUpload } from '@/utils/upload'
import { navigateTo } from '@/utils/navigate'

const canManage = ref(false)

const rec = ref(null)
const loadErr = ref('')
const resolution = ref('')
const pushing = ref(false)
const saving = ref(false)

function recordId() {
  return new URLSearchParams(window.location.search).get('id')
}

async function load() {
  canManage.value = perm.can('reception.manage')
  const id = recordId()
  if (!id) { loadErr.value = '缺少接待记录 ID'; return }
  try {
    const r = await api.receptionRecord(id)
    rec.value = r
    resolution.value = r.resolution || ''
  } catch (e) {
    loadErr.value = (e && e.message) || '接待记录加载失败'
  }
}
onMounted(load)

function fmtDate(s) {
  const p = String(s || '').split('-')
  return p.length === 3 ? (Number(p[1]) + '月' + Number(p[2]) + '日') : String(s || '')
}
function fmtPushedAt(s) {
  if (!s) return ''
  const m = String(s).match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  return m ? (Number(m[2]) + '月' + Number(m[3]) + '日 ' + m[4] + ':' + m[5]) : String(s)
}

async function pushTicket() {
  if (pushing.value) return
  const ok = await showModal({
    title: '派发工单',
    content: '把「' + (rec.value.content || '该诉求').slice(0, 40) + '」派给物业的工单系统处理？',
    confirmText: '派发',
    cancelText: '取消'
  })
  if (!ok || !ok.confirm) return
  pushing.value = true
  try {
    const res = await api.receptionPushTicket(rec.value.id)
    rec.value.ticketPushed = true
    rec.value.ticketNo = (res && res.ticketNo) || (res && res.externalTicketNo)
    rec.value.ticketPushedAt = new Date().toISOString()
    // created===false = 对方按 externalTicketNo 命中了已有工单（幂等），不是新建
    toast({ title: res && res.created === false ? '工单已存在，已关联' : '工单已派发', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '工单派发失败', icon: 'none' })
  } finally {
    pushing.value = false
  }
}

async function saveResolution() {
  if (saving.value || !resolution.value.trim()) return
  saving.value = true
  try {
    await api.receptionUpdateResolution(rec.value.id, resolution.value.trim())
    rec.value.resolution = resolution.value.trim()
    rec.value.done = true
    toast({ title: '已办结', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

// 走全站统一的「选文件+上传一步到位」，返回 { url, fileName, fileType, fileSize }，取消返回 null
async function pickEvidence() {
  try {
    const r = await pickAndUpload('image/*')
    if (!r) return
    const ev = await api.receptionAddEvidence(rec.value.id, r.fileName, r.fileType, r.url)
    rec.value.evidences = (rec.value.evidences || []).concat([ev])
    toast({ title: '已上传', icon: 'success' })
  } catch (err) {
    toast({ title: (err && err.message) || '上传失败', icon: 'none' })
  }
}

async function delEvidence(ev) {
  const ok = await showModal({ title: '删除佐证', content: '确认删除「' + ev.fileName + '」？', confirmText: '删除', cancelText: '取消' })
  if (!ok || !ok.confirm) return
  try {
    await api.receptionRemoveEvidence(rec.value.id, ev.id)
    rec.value.evidences = (rec.value.evidences || []).filter(x => x.id !== ev.id)
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  }
}

async function removeRecord() {
  const ok = await showModal({
    title: '删除接待记录',
    content: '确认删除' + (rec.value.visitorName || '这位居民') + '的这条接待记录？删除后无法恢复。',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!ok || !ok.confirm) return
  try {
    await api.receptionRemove(rec.value.id)
    toast({ title: '已删除', icon: 'success' })
    setTimeout(goBack, 500)
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  }
}

// 回首页接待 tab。软路由偶发不切换页面（见 memory: soft-router-push-intermittent-no-switch），
// 关键跳转一律加硬导航兜底
function goBack() {
  navigateTo('/pages/main/main?tab=reception')
  setTimeout(() => {
    if (!document.querySelector('.plan-switch-card')) window.location.href = '/main?tab=reception'
  }, 300)
}
</script>

<style scoped>
.page { background: var(--c-bg-page); min-height: 100vh; }
.page-empty { padding: 120rpx 40rpx; text-align: center; color: var(--c-text-weak); font-size: 30rpx; }

/* .detail-head/.dh-title 已删（0716 用户定：与 PageNav 重复）。状态胶囊挪进信息卡首行。 */
/* 本页字号一律 ≥28rpx(14px)：首页三个 tab 刚清到零小字，这页别又造一批 */
.stage-pill { flex-shrink: 0; padding: 6rpx 20rpx; border-radius: 999rpx; font-size: 28rpx; font-weight: 700; }
.stage-pill.todo { background: #FFEDD5; color: #9A3412; }
.stage-pill.done { background: #E7F6EC; color: #1E7E4E; }

.info-card, .sec-card { margin: 20rpx 24rpx; padding: 26rpx 28rpx; background: var(--c-bg-card);
  border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); }
.field-row { display: flex; align-items: flex-start; gap: 20rpx; padding: 12rpx 0; }
.field-label { flex-shrink: 0; width: 130rpx; font-size: 28rpx; color: var(--c-text-weak); }
.field-val { flex: 1; min-width: 0; font-size: 30rpx; color: var(--c-text-strong); }
.room { margin-left: 14rpx; color: var(--c-text-mid); font-size: 28rpx; }

.sec-title { display: flex; align-items: center; gap: 12rpx; font-size: 32rpx; font-weight: 700;
  color: var(--c-text-strong); margin-bottom: 16rpx; }
.sec-tip { font-size: 28rpx; font-weight: 400; color: var(--c-text-weak); }
.sec-count { font-size: 28rpx; font-weight: 500; color: var(--c-text-weak); }
.sec-add { margin-left: auto; font-size: 28rpx; font-weight: 700; color: var(--c-primary-dark); }
.sec-hint { margin-top: 14rpx; font-size: 28rpx; line-height: 1.5; color: var(--c-text-weak); }
/* 诉求正文：这页的主角，字号最大 */
.appeal { font-size: 32rpx; line-height: 1.6; color: var(--c-text-strong); white-space: pre-wrap; }

/* 两颗都用 --c-primary-dark(#A85800)：白字 16px/700 门槛 4.5:1，#A85800 是 5.17 ✓，
   而 --c-primary(#C76A00) 只有 3.83 ✗。两颗同色不分主次是有意的——它俩分属不同卡片、
   标题各说各的（派发工单 / 处理结果），不靠颜色区分，靠位置和文案。 */
.big-action { width: 100%; height: 96rpx; border: none; border-radius: 20rpx; font-size: 32rpx; font-weight: 700; color: #fff;
  background: var(--c-primary-dark); }
.big-action:disabled { opacity: 0.5; }
.big-action.ticket { box-shadow: 0 8rpx 22rpx rgba(168,88,0,0.26); }
.big-action.save { margin-top: 18rpx; }

.ticket-done { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 20rpx;
  background: #F2FBF6; border: 2rpx solid #CDE9D8; border-radius: 16rpx; }
.tk-ico { flex-shrink: 0; width: 44rpx; height: 44rpx; border-radius: 50%; background: var(--c-success);
  color: #fff; font-size: 26rpx; display: flex; align-items: center; justify-content: center; }
.tk-no { font-size: 30rpx; font-weight: 700; color: var(--c-text-strong); }
.tk-at { margin-top: 4rpx; font-size: 28rpx; color: var(--c-text-weak); }

.res-input { width: 100%; box-sizing: border-box; padding: 18rpx 20rpx; border: 2rpx solid #E3E8EB;
  border-radius: 16rpx; background: #FCFDFD; font-size: 30rpx; line-height: 1.5; color: var(--c-text-strong); outline: none; }

.ev-empty { padding: 20rpx 0; text-align: center; font-size: 28rpx; color: var(--c-text-weak); }
.ev-list { display: flex; flex-direction: column; gap: 12rpx; }
.ev-item { display: flex; align-items: center; gap: 16rpx; padding: 12rpx; background: #F8FAFB; border-radius: 14rpx; }
.ev-thumb { width: 88rpx; height: 88rpx; border-radius: 10rpx; object-fit: cover; background: #EEF1F3; }
.ev-noimg { display: flex; align-items: center; justify-content: center; font-size: 40rpx; }
.ev-name { flex: 1; min-width: 0; font-size: 28rpx; color: var(--c-text-mid);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ev-del { flex-shrink: 0; font-size: 28rpx; color: #B02A1E; padding: 8rpx 12rpx; }

.danger-zone { padding: 10rpx 24rpx 0; text-align: center; }
.del-record { display: inline-block; padding: 18rpx 40rpx; font-size: 28rpx; color: var(--c-text-mid);
  border: 2rpx dashed #C9D0D6; border-radius: 18rpx; background: #F5F6F8; }

/* 占位高必须 ≥ 固定返回栏的实际高度（96rpx 按钮 + 上下 16rpx padding + 安全区），
   180rpx 不够，会把「删除这条记录」压在栏底下点不着 */
.back-bar-space { height: 260rpx; }
.back-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 24rpx calc(env(safe-area-inset-bottom) + 16rpx);
  background: rgba(255,255,255,0.96); border-top: 2rpx solid #EEF2F4; }
.back-btn { width: 100%; height: 96rpx; border: 2rpx solid var(--c-primary); border-radius: 20rpx;
  background: #fff; color: var(--c-primary); font-size: 32rpx; font-weight: 700; }
</style>
