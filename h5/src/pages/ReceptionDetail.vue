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

      <!-- 转给物业：两条不一样的路（0717 用户定），并排两颗不同颜色的按钮，让人自己挑——
             派发工单（橙实心）= 真的 POST 到外部工单系统，有对方单号，派出去撤不回 → 要确认弹窗
             转物业处理（青开关）= 不发任何请求，只在本系统记一笔，随手可反悔 → 不弹窗，点了就切
           两条都不算办结（办结 = 填了处理结果），所以这块和下面的处理结果卡是并列关系、不是前后步骤。
           整块隐藏条件：已办结且两条路都没走过——事情都完了不该再给转办入口；
           走过任一条则保留（留痕）。填完处理结果 rec.done 变 true，按钮当场消失、只剩留痕。 -->
      <div class="sec-card" v-if="rec.ticketPushed || rec.propertyTransferred || !rec.done">
        <div class="sec-title">转给物业</div>

        <!-- 已派单就只留单号，不再给按钮——后端虽是幂等的，
             但给老人一个还能点的按钮，他会以为没成功、反复点 -->
        <div v-if="rec.ticketPushed" class="ticket-done">
          <span class="tk-ico">✓</span>
          <div class="tk-info">
            <div class="tk-no">工单 {{ rec.ticketNo || rec.externalTicketNo }}</div>
            <div class="tk-at">{{ fmtPushedAt(rec.ticketPushedAt) }} 已派发给物业</div>
          </div>
        </div>

        <!-- 派单/转物业/填结果/删除都要 reception.manage：这些是履职动作，不能谁点开谁都能改。
             已办结时也不给按钮，只在下面走留痕分支 -->
        <template v-if="canManage && !rec.done">
          <div class="ho-row">
            <button v-if="!rec.ticketPushed" class="ho-btn ho-ticket" :disabled="pushing" @click="pushTicket">
              {{ pushing ? '正在派发…' : '派发工单' }}
            </button>
            <button class="ho-btn ho-transfer" :class="{ on: rec.propertyTransferred }"
                    :disabled="transferring" @click="toggleTransfer">
              {{ rec.propertyTransferred ? '✓ 已转交物业' : '转物业处理' }}
            </button>
          </div>
          <div class="sec-hint">派工单走物业的工单系统、有单号可查；转物业只在这里记一笔，你自己联系物业。两条都不算办结，等有结果了再填下面的处理结果。</div>
        </template>

        <!-- 留痕：已办结 / 没权限时，转物业标记改成只读一行（按钮的「已转交物业」态就是它的可写版） -->
        <div v-else-if="rec.propertyTransferred" class="tf-trace">
          <span class="tf-ico">✓</span>{{ fmtPushedAt(rec.propertyTransferredAt) }} 已转交物业处理
        </div>
        <!-- 加 !rec.ticketPushed：否则「已派工单 + 没权限」会在单号下面紧跟一句「还没有转给物业」自打嘴巴 -->
        <div v-else-if="!canManage && !rec.ticketPushed" class="sec-hint">还没有转给物业。你没有接待管理权限，如需转办请联系主任。</div>
      </div>

      <!-- 处理结果 = 办结动作 -->
      <div class="sec-card">
        <div class="sec-title">处理结果<span v-if="canManage" class="sec-tip">填写并保存即算办结</span></div>
        <template v-if="canManage">
          <!-- 不放 placeholder（0717 用户定：默认填入的灰色幽灵字全部去除）。
               上面的标题「处理结果 · 填写并保存即算办结」已经说清要填什么，不需要框里再来一遍 -->
          <textarea v-model="resolution" class="res-input" rows="4"></textarea>
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
const transferring = ref(false)
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

/**
 * 转物业。跟 pushTicket 是两条不同的路，交互也故意不一样：
 * 派工单要弹确认（真的推到外部系统、撤不回），这个不弹（只在本系统记一笔、随手可切回来）——
 * 给开关套确认弹窗就不是开关了。点错了再点一下就还原，这才是「开关」该有的样子。
 */
async function toggleTransfer() {
  if (transferring.value) return
  const next = !rec.value.propertyTransferred
  transferring.value = true
  try {
    await api.receptionSetPropertyTransferred(rec.value.id, next)
    rec.value.propertyTransferred = next
    rec.value.propertyTransferredAt = next ? new Date().toISOString() : null
    toast({ title: next ? '已标记转交物业' : '已取消转交标记', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally {
    transferring.value = false
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

/* 保存并办结。白字 16px/700 门槛 4.5:1：#A85800 是 5.17 ✓，--c-primary(#C76A00) 只有 3.83 ✗ */
.big-action { width: 100%; height: 96rpx; border: none; border-radius: 20rpx; font-size: 32rpx; font-weight: 700; color: #fff;
  background: var(--c-primary-dark); }
.big-action:disabled { opacity: 0.5; }
.big-action.save { margin-top: 18rpx; }

/* 转给物业的两条路（0717 用户定）：并排、等宽、两个颜色，让人自己挑一条。
   等宽是有意的——它俩是并列选项不是主次，谁看起来「更该点」都是误导。
   高 96rpx=48px ≥ 44dp；两颗最长文案（✓ 已转交物业 ≈114px）在 157px 半栏里放得下，nowrap 兜底 */
.ho-row { display: flex; gap: 16rpx; }
/* 已派工单时单号留痕在上、转物业按钮在下，要隔开；单号独占（已办结）时不能凭空多出下边距 */
.ticket-done + .ho-row { margin-top: 16rpx; }
.ho-btn { flex: 1; min-width: 0; height: 96rpx; border-radius: 20rpx;
  font-size: 32rpx; font-weight: 700; white-space: nowrap; }
.ho-btn:disabled { opacity: 0.5; }
/* 派发工单 = 蓝实心，跟会议待办页「发工单处理」同一颗蓝（MinutesTodos.vue .primary-ticket #1A4A8A）。
   0717 用户指出原来的深橙太重、且那边不是这么做的——同一个动作（推外部工单系统）
   在两个页面本来就该长一样，蓝在本 App 里也只用于这一件事。白字 8.79:1 ✓。
   阴影跟着换成蓝（rgba(26,74,138,.18) 也照抄那边），橙阴影配蓝底会发脏 */
.ho-ticket { border: none; background: #1A4A8A; color: #fff;
  box-shadow: 0 8rpx 22rpx rgba(26,74,138,0.18); }
/* 转物业 = 橙开关。关态：白底橙描边橙字；开态：橙实心白字「✓ 已转交物业」。
   #A85800 做字/做描边 5.17:1 ✓、做底配白字 5.17:1 ✓。
   为什么改橙不留青：旁边那颗已经是蓝了，青(#0F766E)跟蓝只差 40° 色相，
   老人本来就常伴色觉衰退，两颗深色一摆根本分不开；橙蓝近补色，怎么都不会认错。
   语义也顺：蓝 = 外部工单系统的颜色，橙 = 本系统品牌色 → 转物业只是我们自己记一笔。
   关态比蓝那颗轻是有意的，跟会议待办页「灰描边·业委会自行处理 vs 蓝实心·发工单处理」
   的轻重关系一致：工单是正式路径，另一条是自己消化 */
.ho-transfer { border: 2rpx solid var(--c-primary-dark); background: #fff; color: var(--c-primary-dark); }
.ho-transfer.on { background: var(--c-primary-dark); color: #fff; }

/* 已办结/没权限时，转物业标记的只读版（开关的「已转交物业」态是它的可写版）。
   跟上面的工单留痕(.ticket-done)取同一套绿：它俩是同一类东西——「这一步发生过」的留痕，
   不是可点的路径，所以不跟按钮的蓝/橙走，靠文案区分是哪条路。
   （原来是青的，青随开关一起退场，全页不再出现第四种颜色） */
.tf-trace { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 20rpx;
  background: #F2FBF6; border: 2rpx solid #CDE9D8; border-radius: 16rpx;
  font-size: 30rpx; font-weight: 700; color: var(--c-text-strong); }
.tf-ico { flex-shrink: 0; width: 44rpx; height: 44rpx; border-radius: 50%; background: var(--c-success);
  color: #fff; font-size: 26rpx; display: flex; align-items: center; justify-content: center; }

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
