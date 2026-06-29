<template>
  <div class="committee-page">
    <PageNav title="业主委员会" />

    <div class="target-row" v-if="isChair">
      <div class="target-card">
        <div class="tc-head">
          <span class="tc-label">本期目标</span>
          <span class="tc-badge" :class="stats.periodCount >= stats.periodTarget ? 'ok' : 'warn'">
            {{ stats.periodCount >= stats.periodTarget ? '已完成' : '未达标' }}
          </span>
        </div>
        <div class="tc-num">{{ stats.periodCount }}<span class="tc-unit"> 次</span></div>
        <span class="tc-sub">{{ stats.periodLabel }}已召开</span>
        <div class="tc-progress">
          <div class="tc-fill" :style="{ width: (stats.periodCount >= stats.periodTarget ? 100 : stats.periodCount / stats.periodTarget * 100) + '%' }"></div>
        </div>
        <span class="tc-rule">目标 {{ stats.periodTarget }} 次</span>
      </div>

      <div class="target-card">
        <div class="tc-head">
          <span class="tc-label">年度目标</span>
          <span class="tc-badge" :class="stats.annualCount >= stats.annualTarget ? 'ok' : 'warn'">
            {{ stats.annualCount >= stats.annualTarget ? '已完成' : '还差' + (stats.annualTarget - stats.annualCount) + '次' }}
          </span>
        </div>
        <div class="tc-num">{{ stats.annualCount }}<span class="tc-unit"> 次</span></div>
        <span class="tc-sub">{{ stats.annualYear }}年已召开</span>
        <div class="tc-progress">
          <div class="tc-fill" :style="{ width: (stats.annualCount >= stats.annualTarget ? 100 : stats.annualCount / stats.annualTarget * 100) + '%' }"></div>
        </div>
        <span class="tc-rule">目标 {{ stats.annualTarget }} 次</span>
      </div>
    </div>


    <div v-if="isChair" class="prepare-btn" @click="openNewMeeting">
      <span class="prepare-ico">📝</span>
      <span class="prepare-text">准备会议</span>
    </div>
    <div v-else class="role-intro" :class="{ public: isExternal }">
      <span class="ri-title">{{ roleView.title }}</span>
      <span class="ri-desc">{{ roleView.intro }}</span>
    </div>

    <div class="search-bar" v-if="isChair && currentStage === 'ended'">
      <input class="search-input" :value="keyword" @input="onSearch" placeholder="搜索会议标题..." />
      <span v-if="keyword" class="search-clear" @click="clearSearch">×</span>
    </div>

    <div class="stage-tabs" v-if="isChair">
      <div class="tab" :class="{ active: currentStage == 'preparing' }" @click="switchStage('preparing')">
        准备<span class="tab-count">{{ counts.preparing }}</span>
      </div>
      <div class="tab" :class="{ active: currentStage == 'ongoing' }" @click="switchStage('ongoing')">
        进行中<span class="tab-count">{{ counts.ongoing }}</span>
      </div>
      <div class="tab" :class="{ active: currentStage == 'ended' }" @click="switchStage('ended')">
        已结束<span class="tab-count">{{ counts.ended }}</span>
      </div>
    </div>

    <div class="meeting-list" v-if="isChair">
      <template v-if="meetings.length">
        <div v-for="item in meetings" :key="item.id" class="meeting-card" @click="openMeetingTap(item)">
          <div class="mc-header">
            <span class="mc-title">{{ item.title }}</span>
            <span class="stage-pill" :class="item.compliance === 'invalid' ? 'invalid' : item.stage">
              {{ item.compliance === 'invalid' ? '无效' : item.stage === 'preparing' ? '准备' : item.stage === 'ongoing' ? '进行中' : '已结束' }}
            </span>
          </div>
          <div class="mc-meta">
            <span>{{ item.meetingDate || '日期待定' }}</span>
            <span class="mc-dot">·</span>
            <span>{{ item.location || '地点待定' }}</span>
            <span class="mc-dot" v-if="item.stageDesc">·</span>
            <span class="mc-stage-desc">{{ item.stageDesc }}</span>
          </div>
          <div class="mc-summary">{{ item.summaryLine }}</div>
        </div>
      </template>
      <div v-else class="empty-state"><span>暂无此阶段会议</span></div>
    </div>

    <template v-if="!isChair">
      <div class="section-header">{{ isRecorder ? '流程待处理' : '我的待办与关注' }}</div>
      <div class="meeting-list">
        <template v-if="pending.length">
          <div v-for="item in pending" :key="item.id" class="meeting-card" @click="openDetail(item.id)">
            <div class="mc-header">
              <span class="mc-title">{{ item.title }}</span>
              <span class="stage-pill" :class="item.stage">{{ item.stage === 'preparing' ? '准备' : item.stage === 'ongoing' ? '进行中' : '已结束' }}</span>
            </div>
            <div class="mc-meta">
              <span>{{ item.meetingDate || '日期待定' }}</span>
              <span class="mc-dot">·</span>
              <span>{{ item.location || '地点待定' }}</span>
              <span class="mc-dot" v-if="item.stageDesc">·</span>
              <span class="mc-stage-desc">{{ item.stageDesc }}</span>
              <span class="personal-badge" :class="item.progress >= 100 ? 'done' : 'doing'">{{ item.progressLabel }}</span>
            </div>
            <div class="pp-bar"><div class="pp-fill" :class="{ done: item.progress >= 100 }" :style="{ width: item.progress + '%' }"></div></div>
          </div>
        </template>
        <div v-else class="empty-state"><span>暂无待办</span></div>
      </div>

      <div class="section-header muted">{{ isRecorder ? '其他会议' : '其他相关会议' }}</div>
      <div class="meeting-list">
        <template v-if="others.length">
          <div v-for="item in others" :key="item.id" class="meeting-card muted-card" @click="openDetail(item.id)">
            <div class="mc-header">
              <span class="mc-title">{{ item.title }}</span>
              <span class="stage-pill" :class="item.stage">{{ item.stage === 'preparing' ? '准备' : item.stage === 'ongoing' ? '进行中' : '已结束' }}</span>
            </div>
            <div class="mc-meta">
              <span>{{ item.meetingDate || '日期待定' }}</span>
              <span class="mc-dot">·</span>
              <span>{{ item.location || '地点待定' }}</span>
              <span class="mc-dot" v-if="item.stageDesc">·</span>
              <span class="mc-stage-desc">{{ item.stageDesc }}</span>
              <span class="personal-badge done">已完成</span>
            </div>
          </div>
        </template>
        <div v-else class="empty-state"><span>暂无其他会议</span></div>
      </div>
    </template>

    <div v-if="createVisible" class="modal-mask" @click="closeCreate">
      <div class="create-panel" @click.stop>
        <div class="create-head">
          <div>
            <span class="create-title">新建业委会会议</span>
          </div>
          <span class="sheet-close close-btn" @click="closeCreate">×</span>
        </div>

        <div class="create-body" style="overflow-y:auto;">
          <div class="prefill-entry" @click="toggleMaterialPrefill">
            <div class="prefill-copy">
              <span class="prefill-title">有会议资料？从资料预填</span>
              <span class="prefill-desc">没有资料可直接手动填写</span>
            </div>
            <span class="prefill-toggle">{{ materialPrefillOpen ? '收起' : '展开' }}</span>
          </div>

          <div v-if="materialPrefillOpen" class="create-section material-section">
            <div class="section-title-row">
              <span class="section-title">会议资料</span>
              <span v-if="materialFiles.length" class="required-note">已选{{ materialFiles.length }}份</span>
            </div>
            <div class="assist-row">
              <button class="assist-btn" @click="chooseMaterialFile">上传资料</button>
              <button class="assist-btn primary" @click="scanMaterialPrefill">扫描并预填</button>
            </div>
            <div v-if="materialFiles.length" class="file-list">
              <div v-for="(item, index) in materialFiles" :key="item.name" class="file-item">
                <span class="file-name">{{ item.name }}</span>
                <span class="file-size">{{ item.sizeText }}</span>
                <span class="tp-del" @click="removeMaterialFile(index)">×</span>
              </div>
            </div>
            <textarea class="form-textarea material-textarea" v-model="materialText" @input="onMaterialTextInput" placeholder="粘贴会议通知或资料正文，例如：会议时间、地点、主要议题、是否表决等"></textarea>
            <div v-if="materialScanResult" class="scan-result">
              <span class="scan-line ok">已识别：{{ materialScanResult.matchedText }}</span>
              <span class="scan-line">需补充：{{ materialScanResult.missingText }}</span>
            </div>
            <span class="clear-link" v-if="materialText || materialFiles.length" @click="clearMaterialPrefill">清空资料</span>
          </div>

          <div class="create-section">
            <div class="section-title-row">
              <span class="section-title">基本信息</span>
              <span class="required-note">* 必填</span>
            </div>
            <div class="form-group">
              <span class="form-label">会议标题 *</span>
              <div class="vi-row">
                <div class="title-input-wrap" style="flex:1;min-width:0;">
                  <input class="form-input large" v-model="createForm.title" placeholder="请输入会议标题" />
                  <span v-if="createForm.title" class="title-clear" @click="createForm.title = ''">清空</span>
                </div>
                <button class="vi-btn" :class="{ on: voiceTarget === 'title' }" @click.stop="startStreamingVoice('title')">🎤</button>
              </div>
              <div v-if="suggestedTitle && !createForm.title" class="title-suggest" @click="createForm.title = suggestedTitle">
                <span class="ts-bulb">💡</span>
                <span class="ts-text">推荐：{{ suggestedTitle }}</span>
                <span class="ts-use">用这个</span>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <span class="form-label">会议日期 *</span>
                <div class="picker-field date-field" @click="openDatePicker">
                  <span class="tf-text">{{ createForm.meetingDate || '点击选择' }}</span>
                  <span class="tf-arrow">▾</span>
                </div>
              </div>
              <div class="form-group half">
                <span class="form-label">开始时间 *</span>
                <div class="picker-field time-field" @click="openTimePicker">
                  <span class="tf-text">{{ createForm.meetingTime || '点击选择' }}</span>
                  <span class="tf-arrow">▾</span>
                </div>
              </div>
            </div>
          </div>

          <div class="create-section">
            <span class="section-title">会议地点</span>
            <select class="picker-field loc-select" :value="locationPreset" @change="onLocationPreset">
              <option v-for="loc in commonLocations" :key="loc" :value="loc">{{ loc }}</option>
              <option value="__other__">其他地点（手动填写）</option>
            </select>
            <input v-if="locationPreset === '__other__'" class="form-input loc-other" v-model="createForm.location" placeholder="请输入会议地点" />
          </div>

          <!-- 会议议程项（弹窗逐条添加） -->
          <div class="create-section">
            <div class="section-title-row">
              <span class="section-title">会议议题</span>
              <span class="required-note">* 必填 · {{ createForm.topics.length }}条</span>
            </div>

            <div v-if="!createForm.topics.length" class="topic-empty">
              <span>请添加本次会议议题（至少一条）</span>
            </div>

            <div v-for="(topic, idx) in createForm.topics" :key="idx" class="topic-summary" @click="openEditTopic(idx)">
              <div class="ts-main">
                <span class="ts-num">议题 {{ idx + 1 }}</span>
                <span class="ts-title">{{ topic.title || '未命名议题' }}</span>
              </div>
              <div class="ts-meta">
                <span class="ts-badge">{{ topicTypeLabel(topic) }}</span>
                <span class="tp-del" @click.stop="removeCreateTopic(idx)">×</span>
              </div>
            </div>

            <div class="topic-add-row">
              <button class="btn btn-ghost topic-add-btn" @click="openAddTopic">📝 输入议题</button>
              <button class="btn btn-ghost topic-add-btn" @click="startStreamingVoice('topic')">🎤 语音议题</button>
            </div>
          </div>

          <div class="extra-entry" @click="descOpen = !descOpen">
            <div class="prefill-copy">
              <span class="prefill-title">补充说明</span>
              <span class="prefill-desc">如有其他需记录的事项可展开填写</span>
            </div>
            <span class="prefill-toggle">{{ descOpen ? '收起' : '展开' }}</span>
          </div>
          <div v-if="descOpen" class="create-section">
            <textarea class="form-textarea" style="min-height:72px;height:72px;" v-model="createForm.description" placeholder="其他需要记录的事项（非表决内容）"></textarea>
          </div>
        </div>

        <div class="sheet-actions fixed">
          <button class="btn btn-ghost" @click="closeCreate">取消</button>
          <button class="btn btn-primary" @click="submitNewMeeting">确认创建</button>
        </div>
      </div>
    </div>

    <!-- 日期选择弹窗：年 / 月 / 日 -->
    <div v-if="datePickerOpen" class="picker-pop-mask" @click="datePickerOpen = false">
      <div class="picker-pop" @click.stop>
        <div class="pp-head">选择会议日期</div>
        <div class="pp-cols">
          <div class="pp-col">
            <div class="pp-col-label">年</div>
            <div class="pp-col-scroll">
              <span v-for="y in yearOptions" :key="y" class="pp-item" :class="{ on: y === dpYear }" @click="setDpYear(y)">{{ y }}</span>
            </div>
          </div>
          <div class="pp-col">
            <div class="pp-col-label">月</div>
            <div class="pp-col-scroll">
              <span v-for="mo in monthOptions" :key="mo" class="pp-item" :class="{ on: mo === dpMonth }" @click="setDpMonth(mo)">{{ mo }}</span>
            </div>
          </div>
          <div class="pp-col">
            <div class="pp-col-label">日</div>
            <div class="pp-col-scroll">
              <span v-for="d in dayOptions" :key="d" class="pp-item" :class="{ on: d === dpDay }" @click="dpDay = d">{{ d }}</span>
            </div>
          </div>
        </div>
        <div class="pp-actions">
          <button class="btn btn-ghost" @click="datePickerOpen = false">取消</button>
          <button class="btn btn-primary" @click="confirmDate">确定</button>
        </div>
      </div>
    </div>

    <!-- 时间选择弹窗：小时（左） / 分钟（右，每5分钟） -->
    <div v-if="timePickerOpen" class="picker-pop-mask" @click="timePickerOpen = false">
      <div class="picker-pop" @click.stop>
        <div class="pp-head">选择开始时间</div>
        <div class="pp-cols">
          <div class="pp-col">
            <div class="pp-col-scroll">
              <span v-for="h in hourOptions" :key="h" class="pp-item" :class="{ on: h === tpHour }" @click="tpHour = h">{{ String(h).padStart(2, '0') }}</span>
            </div>
          </div>
          <div class="pp-col">
            <div class="pp-col-scroll">
              <span v-for="m in minuteOptions" :key="m" class="pp-item" :class="{ on: m === tpMinute }" @click="tpMinute = m">{{ String(m).padStart(2, '0') }}</span>
            </div>
          </div>
        </div>
        <div class="pp-actions">
          <button class="btn btn-ghost" @click="timePickerOpen = false">取消</button>
          <button class="btn btn-primary" @click="confirmTime">确定</button>
        </div>
      </div>
    </div>

    <!-- 流式语音输入弹窗（标题 / 议题） -->
    <div v-if="voiceTarget === 'title' || voiceTarget === 'topic'" class="voice-modal-mask">
      <div class="voice-modal">
        <div class="vm-head">
          <span class="vm-title">🎤 语音输入{{ voiceTarget === 'title' ? '标题' : '议题' }}</span>
          <span class="vm-hint">说完后点确认</span>
        </div>
        <div class="vm-body">
          <div class="vm-wave"><span></span><span></span><span></span><span></span><span></span></div>
          <div class="vm-text">
            <span v-if="voiceFinal || voiceInterim">{{ voiceFinal }}<span class="vm-interim">{{ voiceInterim }}</span></span>
            <span v-else class="vm-placeholder">正在听，请说话…</span>
          </div>
        </div>
        <div class="vm-actions">
          <button class="btn btn-ghost" @click="cancelVoice">取消</button>
          <button class="btn btn-primary" @click="confirmVoice">确认</button>
        </div>
      </div>
    </div>

    <!-- 议题编辑弹窗 -->
    <div v-if="topicDialogOpen" class="topic-dialog-mask" @click="topicDialogOpen = false">
      <div class="topic-dialog" @click.stop>
        <div class="td-head">
          <span class="td-title">{{ topicEditIdx >= 0 ? '编辑议题' : '添加议题' }}</span>
          <span class="close-btn" @click="topicDialogOpen = false">×</span>
        </div>
        <div class="td-body">
          <div class="form-group">
            <span class="form-label">议题内容 *</span>
            <input class="form-input large" v-model="topicDraft.title" placeholder="请输入议题内容" />
          </div>
          <div class="form-group">
            <span class="form-label">议题类型 *</span>
            <div class="type-row" style="margin-bottom:0;">
              <span class="type-chip" :class="{ on: topicDraft.type === 'notice' }" @click="draftPickType('notice')">通报事项</span>
              <span class="type-chip" :class="{ on: topicDraft.type === 'discussion' }" @click="draftPickType('discussion')">讨论事项</span>
              <span class="type-chip" :class="{ on: topicDraft.type === 'decision' }" @click="draftPickType('decision')">表决事项</span>
            </div>
          </div>
          <div class="form-group" v-if="topicDraft.type === 'decision'">
            <span class="form-label">表决方式 *</span>
            <div class="type-row" style="margin-bottom:0;">
              <span class="type-chip" :class="{ on: topicDraft.decisionType === 'simple' }" @click="draftPickDecision('simple')">是 / 否</span>
              <span class="type-chip" :class="{ on: topicDraft.decisionType === 'multi_choice' }" @click="draftPickDecision('multi_choice')">多选一</span>
            </div>
          </div>
          <div class="form-group" v-if="topicDraft.type === 'decision' && topicDraft.decisionType === 'multi_choice'">
            <span class="form-label">选项（至少两个）</span>
            <div v-for="(opt, oi) in topicDraft.options" :key="opt.id" class="ct-option-row">
              <span class="ct-opt-num">{{ oi + 1 }}.</span>
              <input class="form-input ct-opt-input" v-model="opt.label" :placeholder="'选项' + (oi + 1)" />
              <span v-if="topicDraft.options.length > 1" class="tp-del" @click="draftRemoveOption(oi)">×</span>
            </div>
            <span class="add-link" @click="draftAddOption" style="display:block;margin-top:12rpx;">+ 添加选项</span>
          </div>
        </div>
        <div class="td-actions">
          <button class="btn btn-ghost" @click="topicDialogOpen = false">取消</button>
          <button class="btn btn-primary" @click="confirmTopic">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { onMounted, onActivated } from 'vue'
import api from '@/api'
import perm from '@/utils/perm'
import { toast } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'
import PageNav from '@/components/PageNav.vue'
import { parseMeetingText } from '@/utils/meeting-parser'
import { pickFile, humanSize } from '@/utils/upload'

const isChair = ref(false)
const isRecorder = ref(false)
const isExternal = ref(false)
const currentStage = ref('preparing')
const meetings = ref([])
const pending = ref([])
const others = ref([])
const stats = ref({})
const publishScore = ref({ ontime: 0, overdue: 0, pending: 0 })
const counts = ref({ preparing: 0, ongoing: 0, ended: 0 })
const roleView = ref({ title: '', intro: '' })
const createVisible = ref(false)
const createForm = reactive({
  title: '',
  meetingDate: '',
  meetingTime: '09:00',
  location: '',
  description: '',
  topics: []
})
const materialPrefillOpen = ref(false)
const materialText = ref('')
const materialFiles = ref([])
const materialScanResult = ref(null)
const keyword = ref('')

// 标题推荐 / 补充说明折叠
const suggestedTitle = ref('')
const descOpen = ref(false)
// 会议地点下拉
const commonLocations = ['社区活动室', '社区会议室']
const locationPreset = ref('社区活动室')
// 自定义日期选择器（年/月/日 三列）
const datePickerOpen = ref(false)
const dpYear = ref(2026)
const dpMonth = ref(1)
const dpDay = ref(1)
const _nowYear = new Date().getFullYear()
const yearOptions = [_nowYear - 1, _nowYear, _nowYear + 1]
const monthOptions = Array.from({ length: 12 }, (_, i) => i + 1)
const dayOptions = computed(function () {
  const n = new Date(dpYear.value, dpMonth.value, 0).getDate()
  return Array.from({ length: n }, (_, i) => i + 1)
})
// 自定义时间选择器（小时左 / 分钟右）
const timePickerOpen = ref(false)
const tpHour = ref(9)
const tpMinute = ref(0)
const hourOptions = Array.from({ length: 12 }, (_, i) => i + 9) // 业委会会议时段：9点—20点
const minuteOptions = Array.from({ length: 4 }, (_, i) => i * 15)
// 议题编辑弹窗
const topicDialogOpen = ref(false)
const topicEditIdx = ref(-1)
const topicDraft = reactive({ title: '', type: 'discussion', decisionType: 'none', options: [] })
// 语音输入状态
const voiceTarget = ref('') // 'title' | 'topic' | 'time' | 'location' | ''
const voiceInterim = ref('') // 实时识别中（未定稿）
const voiceFinal = ref('')   // 已定稿文字
let _voiceRec = null

function show() {
  const role = getStorage('activeRole', null)
  if (!role) { redirectTo('/pages/login/login'); return }
  isChair.value = perm.isChair()
  isRecorder.value = perm.isRecorder()
  isExternal.value = perm.isExternal()
  setupRoleView()
  loadAll()
}

function setupRoleView() {
  if (perm.isChair()) {
    roleView.value = { title: '主任/副主任工作台', intro: '管理会议全流程：新建、推进阶段、核查合规性、公示归档。' }
  } else if (perm.isRecorder()) {
    roleView.value = { title: '委员工作台', intro: '按流程缺口优先展示，补齐送达、代录确认参会和佐证。' }
  } else if (perm.isExternal()) {
    roleView.value = { title: '公开信息', intro: '内部会议流程仅业委会成员可见，可查看已公示的会议纪要与决定。' }
  } else {
    roleView.value = { title: '我的会议工作台', intro: '按你的待办与关注事项优先展示；会议新建、开始、结束由主任/副主任操作。' }
  }
}

async function loadAll() {
  try {
    const [listRes, statsRes, score] = await Promise.all([
      api.committeeList(isChair.value ? currentStage.value : null),
      api.committeeStats(),
      api.committeePublishScore().catch(() => null)
    ])
    let list = listRes || []
    const st = statsRes || {}
    const now = new Date()
    const month = now.getMonth() + 1
    const bimonth = Math.ceil(month / 2)
    const periodStart = (bimonth - 1) * 2 + 1
    const periodEnd = bimonth * 2
    st.periodLabel = periodStart + '-' + periodEnd + '月'
    st.annualYear = now.getFullYear()

    // 非主席：分组为"待办"和"其他"
    let pendingList = [], othersList = []
    if (!isChair.value) {
      pendingList = list.filter(m => m.progress >= 0 && m.progress < 100)
      othersList = list.filter(m => m.progress >= 100 || m.progress < 0)
    }

    // 关键词过滤
    var kw = keyword.value || ''
    if (kw) {
      list = list.filter(function (m) { return (m.title || '').indexOf(kw) >= 0 })
      pendingList = pendingList.filter(function (m) { return (m.title || '').indexOf(kw) >= 0 })
      othersList = othersList.filter(function (m) { return (m.title || '').indexOf(kw) >= 0 })
    }

    meetings.value = list
    pending.value = pendingList
    others.value = othersList
    stats.value = st
    publishScore.value = score || { ontime: 0, overdue: 0, pending: 0 }
    counts.value = { preparing: st.preparing || 0, ongoing: st.ongoing || 0, ended: st.ended || 0 }
  } catch (e) {
    // H5 无离线 mock，加载失败时置空兜底（原小程序此处回落到 mock 数据）
    meetings.value = []
    pending.value = []
    others.value = []
    stats.value = {}
    publishScore.value = { ontime: 0, overdue: 0, pending: 0 }
    counts.value = { preparing: 0, ongoing: 0, ended: 0 }
  }
}

function onSearch(e) { keyword.value = e.target.value; loadAll() }
function clearSearch() { keyword.value = ''; loadAll() }

function switchStage(stage) {
  currentStage.value = stage
  keyword.value = ''
  loadAll()
}

function openDetail(id) {
  navigateTo('/pages/committee-detail/committee-detail?id=' + id)
}

function openMeetingTap(item) {
  if (item.compliance === 'invalid') {
    openMinutes(item.id)
    return
  }
  openDetail(item.id)
}

function openMinutes(id) {
  navigateTo('/pages/minutes/minutes?meetingId=' + id + '&from=committee')
}

async function openNewMeeting() {
  createVisible.value = true
  materialPrefillOpen.value = false
  materialText.value = ''
  materialFiles.value = []
  materialScanResult.value = null
  descOpen.value = false
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
  createForm.title = ''
  createForm.meetingDate = todayStr()
  createForm.meetingTime = '09:00'
  createForm.location = '社区活动室'
  locationPreset.value = '社区活动室'
  createForm.description = ''
  createForm.topics = []
  suggestedTitle.value = ''
  // 基于历史会议推荐下一次标题（大多数会议为统一格式）
  try {
    const all = await api.committeeList(null)
    const sug = computeSuggestedTitle(all || [])
    suggestedTitle.value = sug
    if (!createForm.title) createForm.title = sug // 默认选中推荐，可点清空手填
  } catch (e) { /* 推荐失败则留空手填 */ }
}

// 从历史标题里找最大的"第N次"，推荐 N+1
function computeSuggestedTitle(list) {
  const year = new Date().getFullYear()
  let maxN = 0
  ;(list || []).forEach(function (m) {
    const t = (m && m.title) || ''
    const ym = t.match(/(\d{4})\s*年第\s*(\d+)\s*次/)
    if (ym) {
      const y = Number(ym[1]), n = Number(ym[2])
      if (y === year && n > maxN) maxN = n
    } else {
      const nm = t.match(/第\s*(\d+)\s*次/)
      if (nm) { const n = Number(nm[1]); if (n > maxN) maxN = n }
    }
  })
  return year + '年第' + (maxN + 1) + '次业委会例会'
}

function closeCreate() {
  createVisible.value = false
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
}

function toggleMaterialPrefill() {
  materialPrefillOpen.value = !materialPrefillOpen.value
}

function onMaterialTextInput() {
  materialScanResult.value = null
}

async function chooseMaterialFile() {
  // 新建会议阶段尚无会议 id，不能直接上传后端。
  // 沿用原"资料预填"语义：仅选文件、记下文件名（供扫描预填用），并提示创建后到详情页上传。
  const file = await pickFile()
  if (!file) return // 用户取消
  materialFiles.value = materialFiles.value.concat([{
    name: file.name,
    sizeText: humanSize(file.size)
  }])
  materialScanResult.value = null
  toast({ title: '会议创建后可在详情页上传文件', icon: 'none' })
}

function removeMaterialFile(idx) {
  materialFiles.value = materialFiles.value.filter((_, i) => i !== idx)
  materialScanResult.value = null
}

function clearMaterialPrefill() {
  materialText.value = ''
  materialFiles.value = []
  materialScanResult.value = null
}

function scanMaterialPrefill() {
  const fileText = materialFiles.value.map(file => file.name).join('\n')
  const sourceText = [materialText.value, fileText].filter(Boolean).join('\n')
  if (!sourceText.trim()) {
    toast({ title: '请先粘贴资料正文或上传文件', icon: 'none' })
    return
  }

  const result = parseMeetingText(sourceText, { kind: 'committee' })
  materialScanResult.value = result
  Object.keys(result.fields).forEach((key) => {
    if (Object.prototype.hasOwnProperty.call(createForm, key)) {
      createForm[key] = result.fields[key]
    }
  })
  syncLocationPreset(createForm.location)
  toast({ title: result.matched.length ? '已预填，请确认' : '未识别到字段', icon: 'none' })
}

// 预填/历史地点回填时同步下拉选中态：命中常用项→选它；非预设值→切"其他"并在手填框回显
function syncLocationPreset(val) {
  if (val && commonLocations.indexOf(val) >= 0) {
    locationPreset.value = val
  } else if (val) {
    locationPreset.value = '__other__'
  } else {
    locationPreset.value = '社区活动室'
  }
}

// 地点下拉：选预设直接用，选"其他"则清空等手填
function onLocationPreset(e) {
  const v = e.target.value
  locationPreset.value = v
  createForm.location = (v === '__other__') ? '' : v
}

// 日期选择器：点击字段任意位置弹出，年/月/日三列
function openDatePicker() {
  const parts = (createForm.meetingDate || todayStr()).split('-')
  dpYear.value = Number(parts[0]) || _nowYear
  dpMonth.value = Number(parts[1]) || 1
  dpDay.value = Number(parts[2]) || 1
  clampDpDay()
  datePickerOpen.value = true
  scrollPickerToSelected()
}
function setDpYear(y) { dpYear.value = y; clampDpDay() }
function setDpMonth(m) { dpMonth.value = m; clampDpDay() }
function clampDpDay() {
  const n = new Date(dpYear.value, dpMonth.value, 0).getDate()
  if (dpDay.value > n) dpDay.value = n
}
function confirmDate() {
  createForm.meetingDate = dpYear.value + '-' + String(dpMonth.value).padStart(2, '0') + '-' + String(dpDay.value).padStart(2, '0')
  datePickerOpen.value = false
}

// 时间选择器：常规小时（左）+ 分钟（右，每5分钟），点确定回填
function openTimePicker() {
  const parts = (createForm.meetingTime || '09:00').split(':')
  tpHour.value = Math.min(20, Math.max(9, Number(parts[0]) || 9)) // 夹到 9—20 点
  tpMinute.value = (Math.round((Number(parts[1]) || 0) / 15) * 15) % 60
  timePickerOpen.value = true
  scrollPickerToSelected()
}
function confirmTime() {
  createForm.meetingTime = String(tpHour.value).padStart(2, '0') + ':' + String(tpMinute.value).padStart(2, '0')
  timePickerOpen.value = false
}

// 打开选择器后把已选项滚到列中部（只滚动列内部，不影响页面）
function scrollPickerToSelected() {
  nextTick(function () {
    const scrolls = document.querySelectorAll('.picker-pop .pp-col-scroll')
    scrolls.forEach(function (scroll) {
      const on = scroll.querySelector('.pp-item.on')
      if (on) scroll.scrollTop = on.offsetTop - scroll.clientHeight / 2 + on.clientHeight / 2
    })
  })
}

// ── 创建表单：议题管理（弹窗式） ──

function removeCreateTopic(idx) {
  createForm.topics = createForm.topics.filter(function (_, i) { return i !== idx })
}

function topicTypeLabel(t) {
  if (!t) return ''
  if (t.type === 'notice') return '通报'
  if (t.type === 'discussion') return '讨论'
  if (t.type === 'decision') return t.decisionType === 'multi_choice' ? '表决·多选一' : '表决·是否'
  return ''
}

function openAddTopic() {
  topicEditIdx.value = -1
  topicDraft.title = ''
  topicDraft.type = 'discussion'
  topicDraft.decisionType = 'none'
  topicDraft.options = []
  topicDialogOpen.value = true
}

function openEditTopic(idx) {
  const t = createForm.topics[idx]
  topicEditIdx.value = idx
  topicDraft.title = t.title || ''
  topicDraft.type = t.type || 'discussion'
  topicDraft.decisionType = t.decisionType || 'none'
  topicDraft.options = (t.options || []).map(function (o) { return { id: o.id, label: o.label } })
  topicDialogOpen.value = true
}

function draftPickType(type) {
  topicDraft.type = type
  topicDraft.decisionType = type === 'decision' ? 'simple' : 'none'
  topicDraft.options = []
}

function draftPickDecision(type) {
  topicDraft.decisionType = type
  topicDraft.options = type === 'multi_choice' ? [{ id: 1, label: '' }, { id: 2, label: '' }] : []
}

function draftAddOption() {
  const options = topicDraft.options || []
  const newId = options.length ? Math.max.apply(null, options.map(function (o) { return o.id })) + 1 : 1
  topicDraft.options = options.concat([{ id: newId, label: '' }])
}

function draftRemoveOption(i) {
  topicDraft.options = topicDraft.options.filter(function (_, idx) { return idx !== i })
}

function confirmTopic() {
  if (!topicDraft.title.trim()) { toast({ title: '请输入议题内容', icon: 'none' }); return }
  if (topicDraft.type === 'decision' && topicDraft.decisionType === 'multi_choice') {
    const valid = (topicDraft.options || []).filter(function (o) { return o.label.trim() })
    if (valid.length < 2) { toast({ title: '多选一议题至少需要两个选项', icon: 'none' }); return }
  }
  const nt = {
    title: topicDraft.title.trim(),
    type: topicDraft.type,
    decisionType: topicDraft.decisionType,
    options: (topicDraft.options || []).map(function (o) { return { id: o.id, label: o.label } })
  }
  if (topicEditIdx.value >= 0) {
    const arr = createForm.topics.slice()
    arr[topicEditIdx.value] = nt
    createForm.topics = arr
  } else {
    createForm.topics = createForm.topics.concat([nt])
  }
  topicDialogOpen.value = false
}

async function submitNewMeeting() {
  var form = createForm
  if (!form.title || !form.meetingDate || !form.meetingTime || !form.location) {
    toast({ title: '请补全标题、时间和地点', icon: 'none' })
    return
  }
  // 过滤掉空标题的议题
  var topics = (form.topics || []).filter(function (t) { return t.title.trim() })
  // 对 multi_choice 过滤空选项；非表决项不带表决方式
  topics = topics.map(function (t) {
    // 拷贝一份，避免直接改响应式对象引发副作用
    var nt = { ...t }
    if (nt.type !== 'decision') {
      nt.decisionType = 'none'
      delete nt.options
    } else if (nt.decisionType === 'multi_choice') {
      nt.options = (nt.options || []).filter(function (o) { return o.label.trim() })
    } else {
      delete nt.options
    }
    return nt
  })
  if (!topics.length) {
    toast({ title: '请至少添加一个会议议题', icon: 'none' })
    return
  }
  var invalidMulti = topics.find(function (t) {
    return t.type === 'decision' && t.decisionType === 'multi_choice' && (!t.options || t.options.length < 2)
  })
  if (invalidMulti) {
    toast({ title: '多选一议题至少需要两个选项', icon: 'none' })
    return
  }
  try {
    const created = await api.committeeCreate({
      title: form.title,
      meetingDate: form.meetingDate,
      meetingTime: form.meetingTime,
      location: form.location,
      description: form.description,
      topics: topics
    })
    createVisible.value = false
    currentStage.value = 'preparing'
    if (created && created.id) {
      navigateTo('/pages/committee-detail/committee-detail?id=' + created.id)
    } else {
      loadAll()
    }
  } catch (e) {
    toast({ title: e.message, icon: 'none' })
  }
}

function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

// ——— 语音输入 ———
function _stopVoice() {
  if (_voiceRec) {
    try { _voiceRec.stop() } catch (e) {}
    _voiceRec = null
  }
}

function _buildRec(continuous, interimResults) {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRec) {
    toast({ title: '当前浏览器不支持语音识别', icon: 'none' })
    return null
  }
  const rec = new SpeechRec()
  rec.lang = 'zh-CN'
  rec.continuous = continuous
  rec.interimResults = interimResults
  return rec
}

// 流式模式：标题 / 议题（连续识别，显示弹窗，点确认后应用）
function startStreamingVoice(target) {
  _stopVoice()
  voiceFinal.value = ''
  voiceInterim.value = ''
  voiceTarget.value = target

  const rec = _buildRec(true, true)
  if (!rec) { voiceTarget.value = ''; return }
  _voiceRec = rec

  rec.onresult = function (e) {
    let fin = '', inter = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const t = e.results[i][0].transcript
      if (e.results[i].isFinal) fin += t
      else inter += t
    }
    if (fin) voiceFinal.value += fin
    voiceInterim.value = inter
  }
  rec.onerror = function () { _stopVoice() }
  rec.start()
}

// 直接模式：时间 / 地点（单次识别，无弹窗，自动写入）
function startDirectVoice(target) {
  _stopVoice()
  voiceTarget.value = target

  const rec = _buildRec(false, false)
  if (!rec) { voiceTarget.value = ''; return }
  _voiceRec = rec

  rec.onresult = function (e) {
    const text = (e.results[0] && e.results[0][0] && e.results[0][0].transcript) || ''
    voiceTarget.value = ''
    _voiceRec = null
    if (target === 'time') parseAndSetTime(text)
    else if (target === 'location') parseAndSetLocation(text)
  }
  rec.onerror = function () { voiceTarget.value = ''; _voiceRec = null }
  rec.onend = function () { if (voiceTarget.value === target) voiceTarget.value = '' }
  rec.start()
}

function confirmVoice() {
  const text = (voiceFinal.value + voiceInterim.value).trim()
  const target = voiceTarget.value
  _stopVoice()
  voiceTarget.value = ''
  voiceFinal.value = ''
  voiceInterim.value = ''
  if (!text) return
  if (target === 'title') {
    createForm.title = text
  } else if (target === 'topic') {
    topicDraft.title = text
    topicDraft.type = 'discussion'
    topicDraft.decisionType = 'none'
    topicDraft.options = []
    topicEditIdx.value = -1
    topicDialogOpen.value = true
  }
}

function cancelVoice() {
  _stopVoice()
  voiceTarget.value = ''
  voiceFinal.value = ''
  voiceInterim.value = ''
}

// 解析时间文字，例如 "上午十点" / "下午两点半" / "9点15分"
function parseAndSetTime(text) {
  if (!text) return
  let hour = null, minute = 0
  const pm = /下午|傍晚|晚上/.test(text)
  const am = /上午|早上|早晨/.test(text)
  // 中文数字映射
  const cnNum = { '零': 0, '一': 1, '两': 2, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '七': 7, '八': 8, '九': 9, '十': 10, '十一': 11, '十二': 12 }
  // 先尝试阿拉伯数字 "9点" / "9:30"
  const arM = text.match(/(\d{1,2})[点:](\d{0,2})/)
  if (arM) {
    hour = parseInt(arM[1])
    minute = arM[2] ? parseInt(arM[2]) : 0
  } else {
    // 中文 "十点" / "两点" / "十一点"
    for (const [k, v] of Object.entries(cnNum)) {
      if (text.indexOf(k + '点') >= 0) { hour = v; break }
    }
  }
  // 分钟
  const minM = text.match(/(\d{1,2})分/)
  if (minM) minute = parseInt(minM[1])
  const halfM = /半/.test(text)
  if (halfM) minute = 30
  // 修正 AM/PM
  if (hour !== null) {
    if (pm && hour < 12) hour += 12
    if (am && hour === 12) hour = 0
    // 没有明确上午/下午时，业委会场景默认：1-8 当下午
    if (!am && !pm && hour >= 1 && hour <= 8) hour += 12
    hour = Math.max(9, Math.min(20, hour))
    // 对齐到 0/15/30/45
    minute = [0, 15, 30, 45].reduce((p, c) => Math.abs(c - minute) < Math.abs(p - minute) ? c : p)
    tpHour.value = hour
    tpMinute.value = minute
    createForm.meetingTime = String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0')
    toast({ title: '时间已设为 ' + createForm.meetingTime, icon: 'success' })
  } else {
    toast({ title: '未能识别时间，请重试', icon: 'none' })
  }
}

// 解析地点文字，匹配预设选项或直接写入
function parseAndSetLocation(text) {
  if (!text) return
  const matched = commonLocations.find(loc => text.indexOf(loc) >= 0 || loc.indexOf(text) >= 0)
  if (matched) {
    locationPreset.value = matched
    createForm.location = matched
  } else {
    locationPreset.value = '__other__'
    createForm.location = text
  }
  toast({ title: '地点已设为 ' + (matched || text), icon: 'success' })
}

onMounted(show)
onActivated(show)
</script>

<style scoped>
.committee-page { min-height: 100vh; background: #f4f5f7; padding-bottom: 160rpx; }

/* 目标卡片 */
.target-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20rpx; padding: 24rpx 24rpx 0; }
.target-card { background: #fff; border-radius: 24rpx; padding: 34rpx 28rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.tc-head { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; margin-bottom: 16rpx; }
.tc-label { font-size: 28rpx; color: #666; font-weight: 500; line-height: 1.35; word-break: break-all; }
.tc-badge { font-size: 28rpx; font-weight: 600; padding: 4rpx 14rpx; border-radius: 10rpx; line-height: 1.35; white-space: nowrap; }
.tc-badge.ok { background: #E8F7EE; color: #27AE60; }
.tc-badge.warn { background: #FFF3E0; color: #E67E22; }
.tc-num { font-size: 68rpx; font-weight: 700; color: #1f2329; line-height: 1; margin-bottom: 10rpx; }
.tc-unit { font-size: 28rpx; font-weight: 400; color: #666; }
.tc-sub { font-size: 28rpx; color: #777; margin-bottom: 14rpx; display: block; line-height: 1.45; word-break: break-all; }
.tc-progress { background: #f0f0f0; border-radius: 6rpx; height: 12rpx; overflow: hidden; margin-bottom: 10rpx; }
.tc-fill { height: 100%; border-radius: 6rpx; background: #FFA800; }
.tc-rule { font-size: 28rpx; color: #666; line-height: 1.45; word-break: break-all; }

.role-intro { margin: 24rpx 24rpx 16rpx; padding: 20rpx 24rpx; background: #fff; border-radius: 18rpx; border-left: 8rpx solid #FFA800; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); }
.role-intro.public { border-left-color: #27AE60; }
.ri-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; line-height: 1.45; word-break: break-all; }
.ri-desc { font-size: 28rpx; color: #666; margin-top: 8rpx; line-height: 1.55; display: block; word-break: break-all; }

.stage-tabs { display: flex; margin: 0 24rpx 16rpx; background: #fff; border-radius: 18rpx; padding: 8rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); }
.tab { flex: 1; padding: 16rpx 8rpx; text-align: center; font-size: 30rpx; color: #666; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; min-width: 0; }
.tab.active { background: #FFA800; color: #fff; font-weight: 700; }
.tab-count { font-size: 28rpx; opacity: 0.85; }

.meeting-list { padding: 0 24rpx; display: flex; flex-direction: column; gap: 20rpx; }
.meeting-card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.meeting-card:active { background: #fafbfc; }
.muted-card { opacity: 0.92; }
.mc-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; margin-bottom: 12rpx; }
.mc-title { font-size: 36rpx; font-weight: 700; color: #1f2329; line-height: 1.45; flex: 1; min-width: 0; word-break: break-all; }
.mc-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8rpx 12rpx; font-size: 28rpx; color: #666; margin-bottom: 10rpx; line-height: 1.45; }
.mc-dot { color: #666; }
.mc-summary { font-size: 28rpx; color: #C77800; line-height: 1.55; word-break: break-all; }
.mc-stage-desc { font-size: 28rpx; color: #2980B9; }
.personal-badge { font-size: 28rpx; font-weight: 600; padding: 4rpx 16rpx; border-radius: 12rpx; background: #f0f0f0; color: #666; flex-shrink: 0; line-height: 1.35; white-space: nowrap; }
.stage-pill { line-height: 1.35; flex-shrink: 0; }
.personal-badge.doing { background: #FFF3E0; color: #E67E22; }
.personal-badge.done { background: #E8F7EE; color: #27AE60; }
.pp-bar { height: 8rpx; background: #f0f0f0; border-radius: 4rpx; margin-top: 14rpx; overflow: hidden; }
.pp-fill { height: 100%; background: #FFA800; border-radius: 4rpx; }
.pp-fill.done { background: #27AE60; }
.section-header { padding: 24rpx 28rpx 8rpx; font-size: 28rpx; color: #666; font-weight: 500; line-height: 1.45; word-break: break-all; }
.section-header.muted { color: #777; }
.empty, .empty-state { padding: 56rpx 24rpx; text-align: center; font-size: 30rpx; color: #777; line-height: 1.6; word-break: break-all; }

/* 准备会议主按钮（替代原工作台卡片 + 右下角 FAB） */
.prepare-btn { display: flex; align-items: center; justify-content: center; gap: 16rpx; margin: 28rpx 24rpx 12rpx; height: 120rpx; background: linear-gradient(135deg, #FFB740, #FFA800); border-radius: 24rpx; box-shadow: 0 12rpx 28rpx rgba(255,168,0,0.34); }
.prepare-ico { font-size: 46rpx; }
.prepare-text { font-size: 40rpx; font-weight: 700; color: #fff; letter-spacing: 2rpx; }

/* 新建会议弹窗 */
.modal-mask { position: fixed; inset: 0; z-index: 50; background: rgba(0,0,0,0.36); display: flex; align-items: flex-end; }
.create-panel { width: 100%; max-height: 94vh; background: #fff; border-radius: 28rpx 28rpx 0 0; display: flex; flex-direction: column; overflow: hidden; }
.create-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; padding: 32rpx 28rpx 20rpx; border-bottom: 2rpx solid #f2f2f2; }
.create-title { display: block; font-size: 36rpx; font-weight: 700; color: #1f2329; line-height: 1.35; word-break: break-all; }
.close-btn { width: 60rpx; height: 60rpx; line-height: 56rpx; text-align: center; border-radius: 30rpx; color: #666; background: #f5f5f5; font-size: 40rpx; flex-shrink: 0; }
.create-body { height: 64vh; max-height: 64vh; padding: 24rpx 26rpx; box-sizing: border-box; }
.create-section { background: #fafbfc; border-radius: 18rpx; padding: 24rpx; margin-bottom: 20rpx; border: 2rpx solid #f0f0f0; }

.prefill-entry { background: #FFF8EA; border: 2rpx solid #FFE0A3; border-radius: 18rpx; padding: 22rpx; margin-bottom: 20rpx; display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.prefill-copy { flex: 1; min-width: 0; }
.prefill-title { display: block; font-size: 28rpx; color: #5C3D00; font-weight: 700; line-height: 1.45; word-break: break-all; }
.prefill-desc { display: block; font-size: 28rpx; color: #9A7600; margin-top: 6rpx; line-height: 1.45; word-break: break-all; }
.prefill-toggle { flex-shrink: 0; min-width: 88rpx; height: 56rpx; line-height: 56rpx; text-align: center; border-radius: 28rpx; background: #fff; color: #C77800; font-size: 28rpx; font-weight: 600; }
.material-section { background: #fffdf7; border-color: #FFE8B8; }
.assist-row { display: flex; gap: 18rpx; margin-bottom: 18rpx; }
.assist-btn { flex: 1; height: 80rpx; line-height: 80rpx; border-radius: 40rpx; background: #fff; color: #C77800; border: 2rpx solid #FFE0A3; font-size: 28rpx; font-weight: 600; padding: 0 20rpx; margin: 0; box-sizing: border-box; display: flex; align-items: center; justify-content: center; }
.assist-btn.primary { background: #FFA800; color: #fff; border-color: #FFA800; }
.file-list { display: flex; flex-direction: column; gap: 12rpx; margin-bottom: 18rpx; }
.file-item { background: #fff; border: 2rpx solid #f0f0f0; border-radius: 14rpx; padding: 16rpx 18rpx; display: flex; align-items: flex-start; justify-content: space-between; gap: 14rpx; }
.file-name { flex: 1; min-width: 0; font-size: 28rpx; color: #333; line-height: 1.45; word-break: break-all; }
.file-size { flex-shrink: 0; font-size: 28rpx; color: #666; line-height: 1.45; }
.material-textarea { min-height: 200rpx; height: 200rpx; margin-bottom: 18rpx; }
.scan-result { background: #fff; border-radius: 14rpx; padding: 18rpx 20rpx; border: 2rpx solid #f0f0f0; }
.scan-line { display: block; font-size: 28rpx; color: #666; line-height: 1.6; word-break: break-all; }
.scan-line.ok { color: #27AE60; }
.clear-link { display: block; margin-top: 14rpx; font-size: 28rpx; color: #666; line-height: 1.5; }
.section-title-row { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 14rpx; }
.section-title { display: block; font-size: 28rpx; color: #1f2329; font-weight: 700; margin-bottom: 14rpx; line-height: 1.45; word-break: break-all; }
.section-title-row .section-title { margin-bottom: 0; }
.required-note { font-size: 28rpx; color: #E67E22; line-height: 1.35; white-space: nowrap; }

.form-row { display: flex; gap: 18rpx; align-items: flex-start; }
.form-group { margin-bottom: 20rpx; min-width: 0; }
.form-group:last-child { margin-bottom: 0; }
.form-group.half { flex: 1; min-width: 0; }
.form-label { display: block; font-size: 28rpx; color: #777; margin-bottom: 12rpx; line-height: 1.45; word-break: break-all; }
.form-input, .form-textarea { width: 100%; box-sizing: border-box; background: #fff; border-radius: 14rpx; font-size: 32rpx; color: #1f2329; border: 2rpx solid #eeeeee; }
.form-input { height: 88rpx; min-height: 88rpx; line-height: normal; padding: 0 20rpx; }
.form-input.large { height: 96rpx; min-height: 96rpx; line-height: normal; font-size: 34rpx; font-weight: 600; }
.form-input::placeholder, .form-textarea::placeholder { color: #666; font-size: 30rpx; line-height: 1.5; }
.picker-field { width: 100%; min-height: 88rpx; box-sizing: border-box; background: #fff; border: 2rpx solid #eeeeee; border-radius: 14rpx; padding: 0 20rpx; font-size: 32rpx; color: #1f2329; line-height: normal; word-break: break-all; display: flex; align-items: center; }
.form-textarea { min-height: 200rpx; height: 200rpx; line-height: 1.5; padding: 18rpx 20rpx; }

.sheet-actions { display: flex; gap: 18rpx; justify-content: space-between; padding-top: 12rpx; }
.sheet-actions.fixed { padding: 20rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 2rpx solid #f2f2f2; }
.btn, .btn-ghost, .btn-primary { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; border-radius: 44rpx; text-align: center; font-size: 32rpx; font-weight: 600; box-sizing: border-box; white-space: nowrap; padding: 0 24rpx; margin: 0; border: 0; display: flex; align-items: center; justify-content: center; }
.btn-ghost { color: #777; background: #f5f5f5; }
.btn-primary { color: #fff; background: #FFA800; }

/* 议题构建 */
.topic-empty { text-align: center; padding: 28rpx 0; font-size: 28rpx; color: #666; line-height: 1.6; }
.ct-option-row { display: flex; align-items: center; gap: 14rpx; margin-top: 12rpx; }
.ct-opt-num { font-size: 28rpx; color: #666; width: 40rpx; text-align: right; flex-shrink: 0; }
.ct-opt-input { flex: 1; min-width: 0; height: 72rpx; min-height: 72rpx; line-height: normal; font-size: 28rpx; }
.type-row { display: flex; flex-wrap: wrap; gap: 14rpx; margin-bottom: 18rpx; }
.type-chip { min-height: 60rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: #666; background: #f5f5f5; padding: 10rpx 22rpx; border-radius: 28rpx; }
.type-chip.on { color: #fff; background: #FFA800; font-weight: 600; }
.add-link { font-size: 28rpx; color: #FFA800; font-weight: 600; }
.tp-del { font-size: 38rpx; color: #666; padding: 0 8rpx; flex-shrink: 0; }

/* 搜索 */
.search-bar { position: relative; margin: 16rpx 24rpx; }
.search-input { width: 100%; height: 72rpx; background: #fff; border-radius: 36rpx; padding: 0 72rpx 0 28rpx; font-size: 30rpx; box-sizing: border-box; border: 0; }
.search-clear { position: absolute; right: 24rpx; top: 50%; transform: translateY(-50%); font-size: 34rpx; color: #666; }

/* 标题推荐 + 清空 */
.title-input-wrap { position: relative; display: flex; align-items: center; }
.title-input-wrap .form-input { padding-right: 120rpx; }
.title-clear { position: absolute; right: 14rpx; top: 50%; transform: translateY(-50%); font-size: 26rpx; color: #C77800; background: #FFF1DA; padding: 10rpx 22rpx; border-radius: 24rpx; line-height: 1; }
.title-suggest { display: flex; align-items: center; gap: 12rpx; margin-top: 16rpx; background: #FFF8EA; border: 2rpx solid #FFE0A3; border-radius: 16rpx; padding: 18rpx 20rpx; }
.ts-bulb { font-size: 32rpx; flex-shrink: 0; }
.ts-text { flex: 1; min-width: 0; font-size: 28rpx; color: #8A6500; line-height: 1.45; word-break: break-all; }
.ts-use { flex-shrink: 0; font-size: 26rpx; color: #fff; background: #FFA800; padding: 10rpx 22rpx; border-radius: 24rpx; font-weight: 600; }

/* 会议地点下拉 */
.loc-select { width: 100%; appearance: none; -webkit-appearance: none; padding-right: 60rpx; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='22' height='22' viewBox='0 0 20 20'%3E%3Cpath fill='%23999' d='M5 7l5 5 5-5z'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 20rpx center; }
.loc-other { margin-top: 16rpx; }

/* 日期/时间选择字段 */
.time-field, .date-field { justify-content: space-between; }
.tf-text { font-size: 32rpx; color: #1f2329; }
.tf-arrow { color: #999; font-size: 28rpx; }

/* 列选择器弹窗（日期/时间共用） */
.picker-pop-mask { position: fixed; inset: 0; z-index: 60; background: rgba(0,0,0,0.42); display: flex; align-items: center; justify-content: center; padding: 48rpx; box-sizing: border-box; }
.picker-pop { width: 100%; max-width: 660rpx; background: #fff; border-radius: 26rpx; padding: 28rpx 26rpx 24rpx; box-sizing: border-box; }
.pp-head { text-align: center; font-size: 34rpx; font-weight: 700; color: #1f2329; margin-bottom: 20rpx; }
.pp-cols { display: flex; gap: 16rpx; }
.pp-col { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.pp-col-label { text-align: center; font-size: 28rpx; color: #666; margin-bottom: 10rpx; }
.pp-col-scroll { height: 460rpx; overflow-y: auto; background: #f7f8fa; border-radius: 16rpx; padding: 8rpx; box-sizing: border-box; -webkit-overflow-scrolling: touch; }
.pp-item { display: flex; align-items: center; justify-content: center; height: 76rpx; font-size: 34rpx; color: #333; border-radius: 12rpx; margin: 4rpx 0; }
.pp-item.on { color: #fff; background: #FFA800; font-weight: 700; }
.pp-actions { display: flex; gap: 18rpx; margin-top: 24rpx; }

/* 补充说明折叠入口 */
.extra-entry { background: #f7f8fa; border: 2rpx solid #ececec; border-radius: 18rpx; padding: 22rpx; margin-bottom: 20rpx; display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.extra-entry .prefill-title { display: block; font-size: 30rpx; color: #555; font-weight: 700; line-height: 1.45; }
.extra-entry .prefill-desc { display: block; font-size: 28rpx; color: #999; margin-top: 6rpx; line-height: 1.45; }
.extra-entry .prefill-toggle { color: #666; }

/* 议题摘要行 */
.topic-summary { background: #fff; border: 2rpx solid #eee; border-radius: 18rpx; padding: 22rpx; margin-top: 16rpx; display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.ts-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.ts-num { font-size: 26rpx; color: #C77800; font-weight: 600; }
.ts-title { font-size: 30rpx; color: #1f2329; line-height: 1.4; word-break: break-all; }
.ts-meta { display: flex; align-items: center; gap: 10rpx; flex-shrink: 0; }
.ts-badge { font-size: 26rpx; color: #C77800; background: #FFF6E5; padding: 8rpx 16rpx; border-radius: 20rpx; white-space: nowrap; }

/* 议题编辑弹窗 */
.topic-dialog-mask { position: fixed; inset: 0; z-index: 60; background: rgba(0,0,0,0.42); display: flex; align-items: flex-end; }
.topic-dialog { width: 100%; background: #fff; border-radius: 28rpx 28rpx 0 0; max-height: 88vh; display: flex; flex-direction: column; }
.td-head { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 28rpx 18rpx; border-bottom: 2rpx solid #f2f2f2; }
.td-title { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.td-body { padding: 26rpx 28rpx; overflow-y: auto; }
.td-actions { display: flex; gap: 18rpx; padding: 18rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid #f2f2f2; }

/* 语音按钮行 */
.vi-row { display: flex; align-items: center; gap: 14rpx; }
.vi-btn {
  flex-shrink: 0;
  width: 72rpx; height: 72rpx;
  border-radius: 50%;
  border: 2rpx solid #d0d0d0;
  background: #f7f8fa;
  font-size: 32rpx;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.vi-btn.on {
  background: #e8f0ff;
  border-color: #0051FF;
  animation: vi-pulse 1.2s ease-in-out infinite;
}
@keyframes vi-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(0, 81, 255, 0.35); }
  50%       { box-shadow: 0 0 0 10rpx rgba(0, 81, 255, 0); }
}

/* 议题双按钮行 */
.topic-add-row { display: flex; gap: 20rpx; margin-top: 8rpx; }
.topic-add-btn { flex: 1; height: 80rpx; font-size: 30rpx; border-radius: 16rpx; }

/* 流式语音输入弹窗 */
.voice-modal-mask { position: fixed; inset: 0; z-index: 80; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; }
.voice-modal {
  width: 100%; background: #fff; border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom));
  display: flex; flex-direction: column; gap: 24rpx;
}
.vm-head { display: flex; align-items: baseline; justify-content: space-between; }
.vm-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.vm-hint { font-size: 26rpx; color: #999; }
.vm-body {
  background: #f7f8fa; border-radius: 20rpx;
  padding: 24rpx 20rpx; min-height: 160rpx;
  display: flex; flex-direction: column; align-items: center; gap: 18rpx;
}
.vm-wave { display: flex; align-items: flex-end; gap: 8rpx; height: 48rpx; }
.vm-wave span {
  width: 8rpx; border-radius: 4rpx; background: #0051FF;
  animation: vm-bar 1.1s ease-in-out infinite;
}
.vm-wave span:nth-child(1) { height: 20rpx; animation-delay: 0s; }
.vm-wave span:nth-child(2) { height: 36rpx; animation-delay: 0.15s; }
.vm-wave span:nth-child(3) { height: 48rpx; animation-delay: 0.3s; }
.vm-wave span:nth-child(4) { height: 36rpx; animation-delay: 0.45s; }
.vm-wave span:nth-child(5) { height: 20rpx; animation-delay: 0.6s; }
@keyframes vm-bar {
  0%, 100% { transform: scaleY(0.4); opacity: 0.6; }
  50%       { transform: scaleY(1);   opacity: 1;   }
}
.vm-text { font-size: 32rpx; color: #1f2329; line-height: 1.6; text-align: center; width: 100%; word-break: break-all; }
.vm-interim { color: #888; }
.vm-placeholder { color: #aaa; }
.vm-actions { display: flex; gap: 20rpx; }
.vm-actions .btn { flex: 1; height: 88rpx; font-size: 34rpx; border-radius: 18rpx; }
</style>
