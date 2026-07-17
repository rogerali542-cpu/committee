<template>
  <!-- 根类 .recep-notice 是软路由硬跳兜底的落地哨兵（同 ReceptionDetail 的 .recep-detail）：
       挂在根上，进页即有、不等接口 -->
  <div class="page recep-notice" style="overflow-y:auto;">
    <PageNav title="接待安排" back-to="/main?tab=reception" />

    <div v-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <template v-else>
      <!-- 0717 用户重定位：接待时间/地点在制度里有基本值（首页卡片展示的就是它），
           本页只干一件事——临时调整时改时间/地点，预览生成的公告并导出去张贴。
           因此砍掉了：接待人字段（制度里写的是主任/副主任或委员，不用每次挑人）、
           独立保存按钮（导出前自动保存，少一步）、打印记录展示（数据还在后端，页面不摆了）。 -->
      <div class="sec-card">
        <div class="field">
          <label class="f-label">接待时间</label>
          <input v-model="form.timeDesc" class="f-input" maxlength="50" :disabled="!canManage" />
        </div>

        <div class="field">
          <label class="f-label">接待地点</label>
          <input v-model="form.place" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>
        <div v-if="!canManage" class="sec-hint">你没有接待管理权限，只能查看。如需修改请联系主任。</div>
      </div>

      <!-- 公告预览：本页的主体。跟着上面两个框实时变，所见即所出 -->
      <div class="sec-card">
        <div class="sec-title">公告预览</div>
        <div class="preview">
          <div class="pv-title">业主接待日公告</div>
          <div class="pv-org">{{ orgName }}</div>
          <div class="pv-line"></div>
          <div class="pv-body">
            <div class="pv-row"><span class="pv-k">一、接待时间：</span>{{ form.timeDesc || '未填写' }}</div>
            <div class="pv-row"><span class="pv-k">二、接待地点：</span>{{ form.place || '未填写' }}</div>
          </div>
          <div class="pv-sign">{{ orgName }}　{{ todayText }}</div>
        </div>
        <!-- 导出前自动保存改动：填完直接导出是老人最自然的路径，不该被「先保存」拦一道 -->
        <button v-if="canManage" class="big-action" :disabled="exporting || !form.timeDesc.trim()" @click="exportPdf">
          {{ exporting ? '正在生成…' : '导出公告 PDF，去打印' }}
        </button>
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
const exporting = ref(false)
const orgName = ref('业主委员会')

const form = reactive({ timeDesc: '', place: '' })
// saved 是「服务端当前值」的镜像，导出前判断有没有改动需要先落库
const saved = reactive({ timeDesc: '', place: '' })

const dirty = computed(() => form.timeDesc !== saved.timeDesc || form.place !== saved.place)

const todayText = computed(() => {
  const d = new Date()
  return d.getFullYear() + ' 年 ' + (d.getMonth() + 1) + ' 月 ' + d.getDate() + ' 日'
})

async function load() {
  canManage.value = perm.can('reception.manage')
  try {
    const sys = await api.receptionSystem()
    form.timeDesc = saved.timeDesc = (sys && sys.timeDesc) || ''
    form.place = saved.place = (sys && sys.place) || ''
    // 抬头直接用后端算好的整串，不在这儿拼。后端 ReceptionService.noticeOrgName() 是唯一实现，
    // PDF 也调它 —— 预览和印出来的纸因此不可能不一致。
    // （别改回「取 communityName 自己拼」：库里那个名字现在是坏的，存着 4 个 '?'，
    //   前端拼就会显示「????业主委员会」，而 PDF 那边被兜底成了「业主委员会」）
    if (sys && sys.orgName) orgName.value = sys.orgName
  } catch (e) {
    loadErr.value = (e && e.message) || '接待安排加载失败'
  }
}
onMounted(load)

/** 导出 = （有改动先自动保存）+ 生成下载。保存失败就不导出，防止印出旧内容 */
async function exportPdf() {
  if (exporting.value) return
  if (!form.timeDesc.trim()) { toast({ title: '请先填接待时间', icon: 'none' }); return }
  exporting.value = true
  try {
    if (dirty.value) {
      // 不传 person：后端 updateSystem 按 containsKey 更新，历史存的接待人保持原样
      await api.receptionUpdateSystem({ timeDesc: form.timeDesc.trim(), place: form.place.trim(), published: true })
      saved.timeDesc = form.timeDesc = form.timeDesc.trim()
      saved.place = form.place = form.place.trim()
    }
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
.sec-title { font-size: 32rpx; font-weight: 700; color: var(--c-text-strong); margin-bottom: 16rpx; }
.sec-hint { margin-top: 4rpx; font-size: 28rpx; line-height: 1.5; color: var(--c-text-weak); }

.field { margin-bottom: 24rpx; }
.field:last-of-type { margin-bottom: 6rpx; }
.f-label { display: block; margin-bottom: 10rpx; font-size: 28rpx; font-weight: 700; color: var(--c-text-mid); }
.f-input { width: 100%; box-sizing: border-box; height: 88rpx; padding: 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background: #FCFDFD;
  font-size: 30rpx; color: var(--c-text-strong); outline: none; }
.f-input:focus { border-color: var(--c-border-focus); }
.f-input:disabled { background: #F4F5F7; color: var(--c-text-weak); }

/* 0717 用户定：导出按钮缩小 20%（高 96→76）、宽度 60% 居中。
   字号 32→28 没砍满 20%——28rpx 是本页字号下限，破线老人看不清 */
.big-action { display: block; width: 60%; height: 76rpx; margin: 18rpx auto 0; border: none; border-radius: 18rpx;
  font-size: 28rpx; font-weight: 700; color: #fff; background: var(--c-primary-dark); }
.big-action:disabled { opacity: 0.5; }

/* 纸样预览：让委员在按下导出前就知道印出来长什么样。
   白底+衬线感的居中排版，刻意跟 App 的卡片风格不一样——它代表"那张纸" */
.preview { padding: 28rpx 24rpx; background: #fff;
  border: 2rpx solid #E3E8EB; border-radius: 12rpx; }
.pv-title { text-align: center; font-size: 34rpx; font-weight: 800; color: #1F2329; letter-spacing: 2rpx; }
.pv-org { margin-top: 8rpx; text-align: center; font-size: 28rpx; color: var(--c-text-mid); }
.pv-line { margin: 14rpx 0 18rpx; height: 2rpx; background: #1F2329; }
.pv-body { display: flex; flex-direction: column; gap: 12rpx; }
.pv-row { font-size: 29rpx; line-height: 1.5; color: var(--c-text-strong); }
.pv-k { font-weight: 700; }
.pv-sign { margin-top: 20rpx; text-align: right; font-size: 28rpx; color: var(--c-text-mid); }

.bottom-space { height: 60rpx; }
</style>
