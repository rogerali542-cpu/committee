<template>
  <!-- 根类 .recep-notice 是软路由硬跳兜底的落地哨兵（同 ReceptionDetail 的 .recep-detail）：
       挂在根上，进页即有、不等接口 -->
  <div class="page recep-notice" style="overflow-y:auto;">
    <PageNav title="接待安排" back-to="/main?tab=reception" />

    <div v-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <template v-else>
      <!-- 填空区。用户定：不给自由文本框，三个空 + 系统套公文模板，
           老人不用自己组织公文语言，格式也才统一得起来。
           0717 用户定：卡头「接待安排+上次修改」删了（页面标题已经叫接待安排，卡里重复一遍是噪音）；
           接待时间下的示例小字同删。 -->
      <div class="sec-card">
        <div class="field">
          <label class="f-label">接待时间</label>
          <input v-model="form.timeDesc" class="f-input" maxlength="50" :disabled="!canManage" />
        </div>

        <div class="field">
          <label class="f-label">接待地点</label>
          <input v-model="form.place" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>

        <div class="field">
          <label class="f-label">接待人</label>
          <!-- 0717 用户定：底部弹单改成原生下拉框（右侧向下箭头、名单贴着框展开），
               未选择时不放占位字。已存值不在名单里（如「业委会委员轮值」）时补成首项，防止显示空白 -->
          <select class="f-select" v-model="form.person" :disabled="!canManage">
            <option value=""></option>
            <option v-if="form.person && !rosterItems.includes(form.person)" :value="form.person">{{ form.person }}</option>
            <option v-for="it in rosterItems" :key="it" :value="it">{{ it }}</option>
          </select>
        </div>

        <button v-if="canManage" class="big-action" :disabled="saving || !dirty" @click="save">
          {{ saving ? '保存中…' : (dirty ? '保存' : '已保存') }}
        </button>
        <div v-else class="sec-hint">你没有接待管理权限，只能查看。如需修改请联系主任。</div>
      </div>

      <!-- 导出打印。0717 用户定：纸样缩略图不再直接铺开，收进「查看样张」折叠——
           它只在第一次用时有认知价值，之后每次进页都占半屏 -->
      <div class="sec-card">
        <div class="sec-title">打印公告</div>
        <div class="pv-toggle" @click="showPreview = !showPreview">
          {{ showPreview ? '收起样张 ▴' : '看看印出来的样子 ▾' }}
        </div>
        <div v-if="showPreview" class="preview">
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
import { toast } from '@/utils/ui'

const canManage = ref(false)
const loadErr = ref('')
const saving = ref(false)
const exporting = ref(false)
// 纸样默认收起（0717 用户定）
const showPreview = ref(false)
const orgName = ref('业主委员会')
const exportLogs = ref([])
const committeeRoster = ref([])

const form = reactive({ timeDesc: '', place: '', person: '' })
// saved 是「服务端当前值」的镜像，用来算 dirty。不能拿 form 跟空字符串比：
// 那样一进页面就显示「未保存」，而且保存后按钮不会变回已保存态
const saved = reactive({ timeDesc: '', place: '', person: '' })

const dirty = computed(() =>
  form.timeDesc !== saved.timeDesc || form.place !== saved.place || form.person !== saved.person)

// 接待人下拉框的选项：委员名单（姓名+角色）
const rosterItems = computed(() =>
  committeeRoster.value.map(m => m.name + (m.role ? '（' + m.role + '）' : '')))

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
  // 下拉框要开页就有选项，名单跟着页面一起拉；拉不到就只剩已存值，不挡编辑其他两项
  try { committeeRoster.value = (await api.committeeMembers()) || [] } catch (e) { /* 静默，选项为空 */ }
}
onMounted(load)

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
.sec-count { font-size: 28rpx; font-weight: 500; color: var(--c-text-weak); }
.sec-hint { margin-top: 14rpx; font-size: 28rpx; line-height: 1.5; color: var(--c-text-weak); }

.field { margin-bottom: 24rpx; }
.f-label { display: block; margin-bottom: 10rpx; font-size: 28rpx; font-weight: 700; color: var(--c-text-mid); }
.f-input { width: 100%; box-sizing: border-box; height: 88rpx; padding: 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background: #FCFDFD;
  font-size: 30rpx; color: var(--c-text-strong); outline: none; }
.f-input:focus { border-color: var(--c-border-focus); }
.f-input:disabled { background: #F4F5F7; color: var(--c-text-weak); }

/* 接待人下拉框：原生 select（0717 用户定，替代底部弹单），压掉系统箭头换成统一的向下 chevron */
.f-select { width: 100%; box-sizing: border-box; height: 88rpx; padding: 0 68rpx 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background-color: #FCFDFD;
  font-size: 30rpx; color: var(--c-text-strong); outline: none; cursor: pointer;
  -webkit-appearance: none; appearance: none;
  background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M6 9l6 6 6-6' fill='none' stroke='%2362676F' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat; background-position: right 20rpx center; background-size: 36rpx; }
.f-select:focus { border-color: var(--c-border-focus); }
.f-select:disabled { background-color: #F4F5F7; color: var(--c-text-weak); cursor: default; }

/* 0717 用户定：保存/导出按钮缩小 20%（高 96→76）、宽度 60% 居中。
   字号 32→28 没砍满 20%——28rpx 是本页字号下限，破线老人看不清 */
.big-action { display: block; width: 60%; height: 76rpx; margin: 12rpx auto 0; border: none; border-radius: 18rpx;
  font-size: 28rpx; font-weight: 700; color: #fff; background: var(--c-primary-dark); }
.big-action:disabled { opacity: 0.5; }

/* 纸样折叠开关：文字链分量，不跟主按钮抢 */
.pv-toggle { padding: 8rpx 0 14rpx; font-size: 28rpx; font-weight: 600; color: var(--c-primary-dark); cursor: pointer; }

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
