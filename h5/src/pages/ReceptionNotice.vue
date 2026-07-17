<template>
  <!-- 根类 .recep-notice 是软路由硬跳兜底的落地哨兵（同 ReceptionDetail 的 .recep-detail）：
       挂在根上，进页即有、不等接口 -->
  <div class="page recep-notice" style="overflow-y:auto;">
    <PageNav title="接待日安排" back-to="/main?tab=reception" />

    <div v-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <template v-else>
      <!-- 填空区。用户定：不给自由文本框，三个空 + 系统套公文模板，
           老人不用自己组织公文语言，格式也才统一得起来 -->
      <div class="sec-card">
        <div class="sec-title">
          接待安排<span v-if="updatedText" class="sec-tip">{{ updatedText }}</span>
        </div>

        <div class="field">
          <label class="f-label">接待时间</label>
          <!-- 不放 placeholder 幽灵字（全站已定的口径）。例子放在标签下方当常驻说明，
               它不是幽灵字：不会随输入消失，也不会被误当成已填内容 -->
          <input v-model="form.timeDesc" class="f-input" maxlength="50" :disabled="!canManage" />
          <div class="f-eg">像这样写：每周日下午 15:00—17:00</div>
        </div>

        <div class="field">
          <label class="f-label">接待地点</label>
          <input v-model="form.place" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>

        <div class="field">
          <label class="f-label">接待人</label>
          <div class="f-pick" :class="{ dim: !canManage }" @click="pickPerson">
            <span :class="form.person ? 'fp-val' : 'fp-none'">{{ form.person || '点这里从委员名单里选' }}</span>
            <span v-if="canManage" class="fp-arrow">›</span>
          </div>
        </div>

        <button v-if="canManage" class="big-action" :disabled="saving || !dirty" @click="save">
          {{ saving ? '保存中…' : (dirty ? '保存' : '已保存') }}
        </button>
        <div v-else class="sec-hint">你没有接待管理权限，只能查看。如需修改请联系主任。</div>
      </div>

      <!-- 导出打印 -->
      <div class="sec-card">
        <div class="sec-title">打印公告</div>
        <div class="preview">
          <div class="pv-title">业主接待日公告</div>
          <div class="pv-org">{{ orgName }}</div>
          <div class="pv-line"></div>
          <div class="pv-body">
            <div class="pv-row"><span class="pv-k">一、接待时间：</span>{{ form.timeDesc || '未填写' }}</div>
            <div class="pv-row"><span class="pv-k">二、接待地点：</span>{{ form.place || '未填写' }}</div>
            <div class="pv-row"><span class="pv-k">三、接 待 人：</span>{{ form.person || '未填写' }}</div>
          </div>
          <div class="pv-sign">{{ orgName }}　{{ todayText }}</div>
        </div>
        <button class="big-action" :disabled="exporting || !form.timeDesc" @click="exportPdf">
          {{ exporting ? '正在生成…' : '导出 PDF，去打印' }}
        </button>
        <div v-if="dirty" class="warn-line">改动还没保存，导出的会是保存前的内容。</div>
      </div>

      <!-- 导出记录（用户定的存档方式：只存当前一份，靠这个证明每个月都公示过） -->
      <div class="sec-card">
        <div class="sec-title">打印记录<span class="sec-count">{{ exportLogs.length }} 次</span></div>
        <div v-if="!exportLogs.length" class="ev-empty">还没有打印过</div>
        <div v-else class="log-list">
          <!-- 显示的是当时的快照，不是现在的设置——这才能证明「7月公示的是7月那个时间」 -->
          <div v-for="lg in exportLogs" :key="lg.id" class="log-item">
            <div class="lg-head">{{ fmtAt(lg.exportedAt) }} · {{ lg.exportedBy || '未知' }}</div>
            <div class="lg-body">{{ lg.timeDesc || '未填写' }}</div>
          </div>
        </div>
      </div>

      <div class="bottom-space"></div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast, showActionSheet } from '@/utils/ui'

const canManage = ref(false)
const loadErr = ref('')
const saving = ref(false)
const exporting = ref(false)
const orgName = ref('业主委员会')
const exportLogs = ref([])
const committeeRoster = ref([])

const form = reactive({ timeDesc: '', place: '', person: '' })
// saved 是「服务端当前值」的镜像，用来算 dirty。不能拿 form 跟空字符串比：
// 那样一进页面就显示「未保存」，而且保存后按钮不会变回已保存态
const saved = reactive({ timeDesc: '', place: '', person: '' })
const updatedAt = ref('')

const dirty = computed(() =>
  form.timeDesc !== saved.timeDesc || form.place !== saved.place || form.person !== saved.person)

const updatedText = computed(() => updatedAt.value ? ('上次修改 ' + fmtAt(updatedAt.value)) : '')

const todayText = computed(() => {
  const d = new Date()
  return d.getFullYear() + ' 年 ' + (d.getMonth() + 1) + ' 月 ' + d.getDate() + ' 日'
})

function fmtAt(s) {
  if (!s) return ''
  const m = String(s).match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  return m ? (Number(m[2]) + '月' + Number(m[3]) + '日 ' + m[4] + ':' + m[5]) : String(s)
}

async function load() {
  canManage.value = perm.can('reception.manage')
  try {
    const sys = await api.receptionSystem()
    form.timeDesc = saved.timeDesc = (sys && sys.timeDesc) || ''
    form.place = saved.place = (sys && sys.place) || ''
    form.person = saved.person = (sys && sys.person) || ''
    updatedAt.value = (sys && sys.updatedAt) || ''
    // 抬头直接用后端算好的整串，不在这儿拼。后端 ReceptionService.noticeOrgName() 是唯一实现，
    // PDF 也调它 —— 预览和印出来的纸因此不可能不一致。
    // （别改回「取 communityName 自己拼」：库里那个名字现在是坏的，存着 4 个 '?'，
    //   前端拼就会显示「????业主委员会」，而 PDF 那边被兜底成了「业主委员会」）
    if (sys && sys.orgName) orgName.value = sys.orgName
  } catch (e) {
    loadErr.value = (e && e.message) || '接待安排加载失败'
    return
  }
  try { exportLogs.value = (await api.receptionNoticeExports()) || [] } catch (e) { /* 记录拉不到不挡主流程 */ }
}
onMounted(load)

async function pickPerson() {
  if (!canManage.value) return
  if (!committeeRoster.value.length) {
    try { committeeRoster.value = (await api.committeeMembers()) || [] } catch (e) { /* 下面统一提示 */ }
  }
  const items = committeeRoster.value.map(m => m.name + (m.role ? '（' + m.role + '）' : ''))
  if (!items.length) { toast({ title: '没拿到委员名单，请稍后再试', icon: 'none' }); return }
  const res = await showActionSheet({ title: '选择接待人', itemList: items })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  form.person = items[res.tapIndex]
}

async function save() {
  if (saving.value || !dirty.value) return
  if (!form.timeDesc.trim()) { toast({ title: '请先填接待时间', icon: 'none' }); return }
  saving.value = true
  try {
    await api.receptionUpdateSystem({
      timeDesc: form.timeDesc.trim(), place: form.place.trim(), person: form.person.trim(), published: true
    })
    saved.timeDesc = form.timeDesc.trim()
    saved.place = form.place.trim()
    saved.person = form.person.trim()
    form.timeDesc = saved.timeDesc; form.place = saved.place; form.person = saved.person
    updatedAt.value = new Date().toISOString()
    toast({ title: '已保存', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function exportPdf() {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await api.receptionExportNotice()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = orgName.value + '-业主接待日公告.pdf'
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    toast({ title: '已导出，去打印吧', icon: 'success' })
    // 后端在导出成功时同时留痕，所以这里要重拉，否则记录要等下次进页才出现
    try { exportLogs.value = (await api.receptionNoticeExports()) || [] } catch (e) { /* 已导出成功，记录晚点刷也行 */ }
  } catch (e) {
    toast({ title: (e && e.message) || '导出失败', icon: 'none' })
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.page { background: var(--c-bg-page); min-height: 100vh; }
.page-empty { padding: 120rpx 40rpx; text-align: center; color: var(--c-text-weak); font-size: 30rpx; }
/* 本页字号一律 ≥28rpx(14px)，跟接待处理页同口径 */
.sec-card { margin: 20rpx 24rpx; padding: 26rpx 28rpx; background: var(--c-bg-card);
  border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); }
.sec-title { display: flex; align-items: center; gap: 12rpx; font-size: 32rpx; font-weight: 700;
  color: var(--c-text-strong); margin-bottom: 16rpx; }
.sec-tip { font-size: 28rpx; font-weight: 400; color: var(--c-text-weak); }
.sec-count { font-size: 28rpx; font-weight: 500; color: var(--c-text-weak); }
.sec-hint { margin-top: 14rpx; font-size: 28rpx; line-height: 1.5; color: var(--c-text-weak); }

.field { margin-bottom: 24rpx; }
.f-label { display: block; margin-bottom: 10rpx; font-size: 28rpx; font-weight: 700; color: var(--c-text-mid); }
.f-input { width: 100%; box-sizing: border-box; height: 88rpx; padding: 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background: #FCFDFD;
  font-size: 30rpx; color: var(--c-text-strong); outline: none; }
.f-input:focus { border-color: var(--c-border-focus); }
.f-input:disabled { background: #F4F5F7; color: var(--c-text-weak); }
/* 常驻示例：跟 placeholder 的区别是它不会消失、也不占输入框，不会被当成已填内容 */
.f-eg { margin-top: 8rpx; font-size: 28rpx; color: var(--c-text-weak); }

/* 接待人跟登记接待弹窗一致：点选，不让老人打字 */
.f-pick { display: flex; align-items: center; height: 88rpx; padding: 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background: #FCFDFD; cursor: pointer; }
.f-pick.dim { background: #F4F5F7; cursor: default; }
.fp-val { flex: 1; font-size: 30rpx; color: var(--c-text-strong); }
.fp-none { flex: 1; font-size: 30rpx; color: var(--c-text-weak); }
.fp-arrow { flex-shrink: 0; font-size: 34rpx; color: var(--c-text-weak); }

.big-action { width: 100%; height: 96rpx; margin-top: 6rpx; border: none; border-radius: 20rpx;
  font-size: 32rpx; font-weight: 700; color: #fff; background: var(--c-primary-dark); }
.big-action:disabled { opacity: 0.5; }

/* 纸样预览：让委员在按下导出前就知道印出来长什么样。
   白底+衬线感的居中排版，刻意跟 App 的卡片风格不一样——它代表"那张纸" */
.preview { padding: 28rpx 24rpx; margin-bottom: 18rpx; background: #fff;
  border: 2rpx solid #E3E8EB; border-radius: 12rpx; }
.pv-title { text-align: center; font-size: 34rpx; font-weight: 800; color: #1F2329; letter-spacing: 2rpx; }
.pv-org { margin-top: 8rpx; text-align: center; font-size: 28rpx; color: var(--c-text-mid); }
.pv-line { margin: 14rpx 0 18rpx; height: 2rpx; background: #1F2329; }
.pv-body { display: flex; flex-direction: column; gap: 12rpx; }
.pv-row { font-size: 29rpx; line-height: 1.5; color: var(--c-text-strong); }
.pv-k { font-weight: 700; }
.pv-sign { margin-top: 20rpx; text-align: right; font-size: 28rpx; color: var(--c-text-mid); }

.warn-line { margin-top: 12rpx; font-size: 28rpx; color: #9A3412; }

.ev-empty { padding: 20rpx 0; text-align: center; font-size: 28rpx; color: var(--c-text-weak); }
.log-list { display: flex; flex-direction: column; gap: 12rpx; }
.log-item { padding: 14rpx 18rpx; background: #F8FAFB; border-radius: 14rpx; }
.lg-head { font-size: 28rpx; font-weight: 700; color: var(--c-text-strong); }
.lg-body { margin-top: 4rpx; font-size: 28rpx; color: var(--c-text-weak); }

.bottom-space { height: 60rpx; }
</style>
