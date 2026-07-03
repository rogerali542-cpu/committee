<template>
  <div class="home">
    <!-- 顶栏：标题 + 通知铃 -->
    <div class="hd">
      <div class="hd-left">
        <span class="hd-title">业委会</span>
        <span class="hd-sub">{{ activeRole.realName }} · {{ activeRole.role }}</span>
      </div>
      <div class="hd-bell" @click="goNotifications">
        <span class="hd-bell-ico">🔔</span>
        <div v-if="unread > 0" class="hd-badge">{{ unread > 99 ? '99+' : unread }}</div>
      </div>
    </div>

    <template v-if="isChair">
      <!-- 业委会综合评分（占位分数，计算规则待定后接后端） -->
      <div class="score-line">
        <span class="score-ico">🏅</span>
        <span>业委会综合评分</span>
        <span class="score-num">{{ score }}</span>
        <span class="score-unit">分</span>
      </div>

      <div class="target-row">
        <div class="target-card">
          <div class="tc-head"><span class="tc-label">本期会议</span></div>
          <div class="tc-num">{{ monthNeed }}<span class="tc-unit">次</span></div>
          <span class="tc-sub">{{ curMonth }}月需召开</span>
        </div>

        <div class="target-card">
          <div class="tc-head">
            <span class="tc-label">逾期会议</span>
            <span v-if="overdueCount > 0" class="tc-badge warn">需补开</span>
          </div>
          <div class="tc-num">{{ overdueCount }}<span class="tc-unit">次</span></div>
          <span v-if="overdueCount > 0" class="tc-sub tc-sub-warn">{{ overdueMonths }}的会还没开，请尽快补上！</span>
          <span v-else class="tc-sub">近期都按时开了</span>
        </div>
      </div>
    </template>


    <!-- 当前会议卡片（进行中/准备中；主任另含已结束未公示），点继续进入流程 -->
    <template v-if="currents && currents.length > 0">
      <div v-for="cur in currents" :key="cur.id" class="meet-card">
        <span class="meet-title">{{ cur.title }}</span>

        <div class="steps">
          <template v-for="(item, index) in cur.steps" :key="item.no">
            <div v-if="index > 0" class="step-line" :class="item.state === 'todo' ? 'todo' : 'done'"></div>
            <div class="step" :class="item.state">
              <div class="step-dot">
                <span v-if="item.state === 'done'">✓</span>
                <span v-else>{{ item.no }}</span>
              </div>
              <span class="step-label">{{ item.label }}</span>
            </div>
          </template>
        </div>

        <div class="big-btn" @click="goCurrent(cur)">
          <span class="big-btn-ico">{{ cur.ctaIcon }}</span>
          <span class="big-btn-text">{{ cur.ctaLabel }}</span>
        </div>

        <div v-if="isChair" class="meet-del" @click="removeCurrent(cur)">删除会议</div>
      </div>
    </template>

    <!-- 委员且无相关会议：空闲提示 -->
    <div v-if="!isChair && (!currents || !currents.length)" class="idle">
      <span class="idle-emoji">☕</span>
      <span class="idle-hint">暂时没有需要您处理的会议</span>
      <span class="idle-sub">有新会议时，会在这里提醒您</span>
    </div>

    <!-- 去开会：在"更多功能"上方的拇指区；主任点了当场弹出"新建会议"，不再跳页 -->
    <div v-if="isChair" class="big-btn primary go-meeting" @click="openNewMeeting">
      <span class="big-btn-ico">📝</span>
      <span class="big-btn-text">去通知</span>
    </div>

    <!-- 更多功能 -->
    <div class="more" :class="{ 'more-sink': !isChair }">
      <span class="more-title">更多功能</span>
      <div class="more-grid">
        <div class="more-item" @click="goReception">
          <span class="more-ico">🤝</span>
          <span class="more-label">接待记录</span>
        </div>
        <div class="more-item" @click="goLearning">
          <span class="more-ico">📖</span>
          <span class="more-label">学习培训</span>
        </div>
        <div v-if="canViewInternal" class="more-item" @click="goLibrary">
          <span class="more-ico">📚</span>
          <span class="more-label">历史会议</span>
        </div>
      </div>
    </div>

    <div v-if="createVisible" class="modal-mask" @click="closeCreate">
      <div class="create-panel" @click.stop>
        <div class="create-head">
          <span class="create-back" @click="closeCreate">‹</span>
          <span class="create-title">发起业委会</span>
          <span class="create-nav-ph"></span>
        </div>

        <div class="create-body" style="overflow-y:auto;">
          <!-- 会议标题（置顶） -->
          <div class="create-section title-card">
            <div class="form-group">
              <div class="title-input-wrap">
                <textarea ref="titleEl" class="form-input large title-ta" rows="1" v-model="createForm.title" :placeholder="suggestedTitle ? '' : '请输入会议名称'" @input="autoGrowTitle" @keydown.enter.prevent></textarea>
                <!-- 推荐标题：半透明显示在文本框内，点文字直接填入 -->
                <span v-if="suggestedTitle && !createForm.title" class="title-ghost" @click="createForm.title = suggestedTitle">{{ suggestedTitle }}</span>
                <span class="title-clear" :class="{ dim: !createForm.title && !suggestedTitle }" @click="clearTitleOrGhost">×</span>
              </div>
              <div class="title-voice-row">
                <button class="voice-input-btn" :class="{ on: voiceTarget === 'title' }" @click.stop="startStreamingVoice('title')">🎤 语音输入</button>
              </div>
            </div>
          </div>

          <div class="create-section info-card">
            <span class="section-title">会议时间 / 地点</span>
            <div class="form-row">
              <div class="form-group half">
                <div class="picker-field date-field" @click="openDatePicker">
                  <span class="tf-text">{{ createForm.meetingDate || '选择日期' }}</span>
                  <span class="tf-arrow">▾</span>
                </div>
              </div>
              <div class="form-group half">
                <div class="picker-field time-field" @click="openTimePicker">
                  <span class="tf-text">{{ createForm.meetingTime || '选择时间' }}</span>
                  <span class="tf-arrow">▾</span>
                </div>
              </div>
            </div>
            <div class="loc-row">
              <select v-if="locationPreset !== '__other__'" class="picker-field loc-select" :value="locationPreset" @change="onLocationPreset">
                <option v-for="loc in commonLocations" :key="loc" :value="loc">{{ loc }}</option>
                <option value="__other__">其他地点（手动填写）</option>
              </select>
              <input v-else class="form-input loc-input" v-model="createForm.location" placeholder="请输入会议地点" />
              <button class="loc-map-btn" @click="pickLocationOnMap" aria-label="在地图上选择地点"><svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#1A73E8" d="M12 2C8.1 2 5 5.1 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.9-3.1-7-7-7zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5z"/></svg></button>
            </div>
            <span v-if="locationPreset === '__other__'" class="loc-back" @click="resetLocPreset">选常用地点</span>
          </div>

          <!-- 会议议程项（弹窗逐条添加） -->
          <div class="create-section">
            <span class="section-title">会议议题</span>
            <div v-for="(topic, idx) in createForm.topics" :key="idx" class="topic-line">
              <span class="topic-line-text"><b>{{ idx + 1 }}.</b> {{ topic.title }}</span>
              <span class="topic-line-del" @click="removeCreateTopic(idx)">×</span>
            </div>
            <div class="vi-row topic-input-row">
              <input class="form-input topic-input" v-model="topicInput" placeholder="输入一条议题" @keyup.enter="addTopicFromInput" />
              <button class="topic-confirm-btn" @click="addTopicFromInput">确定</button>
            </div>
            <div class="title-voice-row">
              <button class="voice-input-btn" :class="{ on: voiceTarget === 'topic' }" @click.stop="startStreamingVoice('topic')">🎤 语音输入</button>
            </div>
          </div>

          <!-- 会议材料（拍照/上传判类为材料的文件；建会后自动挂到会议供委员传阅） -->
          <div v-if="pendingMaterials.length" class="create-section">
            <span class="section-title">会议材料（{{ pendingMaterials.length }}）</span>
            <div v-for="(m, idx) in pendingMaterials" :key="m.url" class="mat-line">
              <span class="mat-ico">{{ matIcon(m) }}</span>
              <span class="mat-name" @click="openMaterialViewer(m)">{{ m.fileName }}</span>
              <span class="mat-size" v-if="m.sizeText">{{ m.sizeText }}</span>
              <span class="topic-line-del" @click="removePendingMaterial(idx)">×</span>
            </div>
          </div>

          <!-- 居委会见证（选项，非卡片）：勾选则建会后标记为重大事项 -->
          <label class="juwei-row">
            <span class="juwei-label">居委会见证</span>
            <input type="checkbox" class="juwei-check" v-model="createForm.juweiWitness" />
          </label>

        </div>

        <!-- 拍照/上传（双卡片）：选介质即可，AI 识别后自动判定是会议通知(预填)还是会议材料(传阅) -->
        <div class="doc-scan-bar">
          <div class="ds-cards">
            <button class="ds-card" :class="{ busy: scanBusy === 'camera' }" :disabled="!!scanBusy" @click="startDocScan('camera')">
              <span v-if="scanBusy === 'camera'" class="ds-spin"></span>
              <span v-else class="ds-ico or">📷</span>
              <span class="ds-t">{{ scanBusy === 'camera' ? '识别中 ' + docProgress + '%' : '拍照' }}</span>
              <span class="ds-s">纸质文件</span>
            </button>
            <button class="ds-card" :class="{ busy: scanBusy === 'file' }" :disabled="!!scanBusy" @click="startDocScan('file')">
              <span v-if="scanBusy === 'file'" class="ds-spin"></span>
              <span v-else class="ds-ico bl">📁</span>
              <span class="ds-t">{{ scanBusy === 'file' ? '识别中 ' + docProgress + '%' : '上传文件' }}</span>
              <span class="ds-s">电子文件</span>
            </button>
          </div>
          <span v-if="lastScanTokens > 0 && !scanBusy" class="ds-tokens">本次识别消耗 {{ lastScanTokens.toLocaleString() }} token</span>
        </div>

        <div class="sheet-actions fixed">
          <button class="btn btn-ghost" @click="closeCreate">取消</button>
          <button class="btn btn-primary" @click="submitNewMeeting">生成通知</button>
        </div>
      </div>
    </div>

    <!-- 模拟系统相机权限弹窗（真机由系统弹出，这里演示流程） -->
    <div v-if="camPermVisible" class="perm-mask">
      <div class="perm-box">
        <span class="perm-ico">📷</span>
        <span class="perm-title">“业委专区”想使用您的相机</span>
        <span class="perm-sub">用于拍摄纸质文件进行 AI 识别</span>
        <div class="perm-btns">
          <button class="perm-btn deny" @click="denyCamPerm">不允许</button>
          <button class="perm-btn allow" @click="allowCamPerm">允许</button>
        </div>
      </div>
    </div>

    <!-- 识别中：居中小弹窗（文档扫描动画 + 三步流程 + 动态文案；不做整页进度页） -->
    <div v-if="scanBusy" class="scan-pop-mask">
      <div class="scan-pop">
        <!-- 文档扫描动画：纸面 + 橙色扫描线来回扫 -->
        <div class="sp-doc">
          <span class="sp-doc-line w80"></span>
          <span class="sp-doc-line w95"></span>
          <span class="sp-doc-line w70"></span>
          <span class="sp-doc-line w90"></span>
          <span class="sp-doc-line w60"></span>
          <span class="sp-scanline"></span>
        </div>
        <span class="sp-title">AI 智能识别中</span>
        <span class="sp-say">{{ scanSay }}</span>
        <!-- 三步流程点：上传 → 提取文字 → AI 理解 -->
        <div class="sp-steps">
          <div class="sp-step" :class="scanStage > 1 ? 'done' : 'active'"><span class="sp-dot">{{ scanStage > 1 ? '✓' : '1' }}</span><span class="sp-slabel">上传</span></div>
          <div class="sp-step-line" :class="{ done: scanStage > 1 }"></div>
          <div class="sp-step" :class="scanStage > 2 ? 'done' : (scanStage === 2 ? 'active' : '')"><span class="sp-dot">{{ scanStage > 2 ? '✓' : '2' }}</span><span class="sp-slabel">提取文字</span></div>
          <div class="sp-step-line" :class="{ done: scanStage > 2 }"></div>
          <div class="sp-step" :class="scanStage === 3 ? 'active' : ''"><span class="sp-dot">3</span><span class="sp-slabel">AI 理解</span></div>
        </div>
        <div class="sp-prog-row">
          <div class="sp-bar"><div class="sp-fill" :style="{ width: docProgress + '%' }"></div></div>
          <span class="sp-pct">{{ docProgress }}%</span>
        </div>
        <span class="sp-foot">豆包大模型 · 已用时 {{ scanSec }}s</span>
      </div>
    </div>

    <!-- 模拟手机相机（测试用）：取景框对着纸质通知；拍后先进照片预览（重拍/使用照片），确认后才走识别；接真机后整块可删 -->
    <div v-if="mockCameraVisible" class="mock-cam" :class="{ preview: !!mockShotUrl }">
      <div class="mc-top">
        <span class="mc-badge">{{ mockShotUrl ? '照片预览' : '模拟相机 · 测试' }}</span>
        <span class="mc-close" @click="closeMockCamera">×</span>
      </div>
      <!-- 取景模式 -->
      <template v-if="!mockShotUrl">
        <div class="mc-viewport">
          <img class="mc-paper" :src="mockViewfinderUrl" alt="取景中的纸质文件" />
          <span class="mc-corner tl"></span><span class="mc-corner tr"></span>
          <span class="mc-corner bl"></span><span class="mc-corner br"></span>
          <span class="mc-tip">将文件对准取景框</span>
        </div>
        <div class="mc-bottom">
          <button class="mc-shutter" @click="mockShoot" aria-label="拍照"><span class="mc-shutter-core"></span></button>
        </div>
      </template>
      <!-- 拍后预览：像系统相机一样先看照片，确认后才开始 AI 识别 -->
      <template v-else>
        <div class="mc-viewport">
          <img class="mc-paper shot" :src="mockShotUrl" alt="刚拍的照片" />
        </div>
        <div class="mc-confirm-tip">拍清楚了吗？点「使用照片」开始识别</div>
        <div class="mc-bottom confirm">
          <button class="mc-btn retake" @click="mockRetake">重拍</button>
          <button class="mc-btn use" @click="mockUsePhoto">使用照片</button>
        </div>
      </template>
      <div class="mc-flash" :class="{ on: mockCamFlash }"></div>
    </div>

    <!-- 日期选择弹窗：小日历（月历网格），点日期即选 -->
    <div v-if="datePickerOpen" class="picker-pop-mask" @click="datePickerOpen = false">
      <div class="cal-pop" @click.stop>
        <div class="pop-close"><span class="close-btn" @click="datePickerOpen = false">×</span></div>
        <div class="cal-head">
          <span class="cal-nav" @click="calPrevMonth">‹</span>
          <span class="cal-title">{{ dpYear }}年{{ dpMonth }}月</span>
          <span class="cal-nav" @click="calNextMonth">›</span>
        </div>
        <div class="cal-week">
          <span v-for="w in ['日','一','二','三','四','五','六']" :key="w" class="cal-wd">{{ w }}</span>
        </div>
        <div class="cal-grid">
          <span v-for="(cell, i) in calCells" :key="i" class="cal-cell"
                :class="{ empty: !cell, on: cell && isSelectedDay(cell), today: cell && !isSelectedDay(cell) && isToday(cell) }"
                @click="cell && pickCalDay(cell)">{{ cell || '' }}</span>
        </div>
        <div class="pp-actions">
          <button class="btn btn-ghost" @click="datePickerOpen = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 时间选择弹窗：大按钮点选（时 + 分），免滚动，点选即生效 -->
    <div v-if="timePickerOpen" class="picker-pop-mask" @click="timePickerOpen = false">
      <div class="picker-pop" @click.stop>
        <div class="pop-close"><span class="close-btn" @click="timePickerOpen = false">×</span></div>
        <div class="pp-head">选择开始时间</div>
        <div class="tg-cur">{{ String(tpHour).padStart(2, '0') }}:{{ String(tpMinute).padStart(2, '0') }}</div>
        <div class="tg-label">时</div>
        <div class="tg-grid">
          <span v-for="h in hourOptions" :key="h" class="tg-cell" :class="{ on: h === tpHour }" @click="setTpHour(h)">{{ String(h).padStart(2, '0') }}</span>
        </div>
        <div class="tg-label">分</div>
        <div class="tg-grid tg-grid-m">
          <span v-for="m in minuteOptions" :key="m" class="tg-cell" :class="{ on: m === tpMinute }" @click="setTpMinute(m)">{{ String(m).padStart(2, '0') }}</span>
        </div>
        <div class="pp-actions">
          <button class="btn btn-primary" @click="timePickerOpen = false">完成</button>
        </div>
      </div>
    </div>

    <!-- 流式语音输入弹窗（标题 / 议题） -->
    <div v-if="voiceTarget === 'title' || voiceTarget === 'topic'" class="voice-modal-mask">
      <div class="voice-modal">
        <div class="vm-body">
          <div class="vm-wave"><span></span><span></span><span></span><span></span><span></span></div>
          <div class="vm-text">
            <span v-if="voiceFinal || voiceInterim">{{ voiceFinal }}<span class="vm-interim">{{ voiceInterim }}</span></span>
            <span v-else class="vm-placeholder">请说话输入{{ voiceTarget === 'title' ? '标题' : '议题' }}</span>
          </div>
        </div>
        <div class="vm-actions">
          <button class="btn btn-ghost" @click="cancelVoice">取消</button>
          <button class="btn btn-ghost" @click="retryVoice">重新输入</button>
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
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { onMounted, onActivated } from 'vue'
import api from '@/api'
import perm from '@/utils/perm'
import { showModal, showActionSheet, toast } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'
import PageNav from '@/components/PageNav.vue'
import { parseMeetingText } from '@/utils/meeting-parser'
import { pickFile, humanSize } from '@/utils/upload'
import { applyHotwords } from '@/utils/helpers'
import { openMaterialViewer } from '@/composables/materialViewer'

const isChair = ref(false)
const isRecorder = ref(false)
const isExternal = ref(false)
// 合并后的主页：顶栏 + 当前会议卡 所需状态
const activeRole = ref({})
const canCreate = ref(false)
const canViewInternal = ref(false)
const unread = ref(0)
const currents = ref([])            // 进行中/准备中的会议卡片
const STEP_BY_STAGE = { preparing: 1, ongoing: 2, ended: 3 }
const STEP_LABELS = ['', '准备开会', '正式开会', '会后总结']
// —— 首页指标（占位数字，计算规则待定后再接后端，届时替换这三个值即可）——
const score = ref(92)        // 业委会综合评分
const monthNeed = ref(1)     // 本期会议：当月需召开
const overdueCount = ref(2)  // 逾期会议：往月该开未开
const curMonth = new Date().getMonth() + 1
// 逾期月份文案（规定每两月须开一次；占位取当前双月期的前一期，真实值待后端给出）
const _ovBm = Math.ceil(curMonth / 2) > 1 ? Math.ceil(curMonth / 2) - 1 : 6
const overdueMonths = ((_ovBm - 1) * 2 + 1) + '-' + (_ovBm * 2) + '月'
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
  topics: [],
  topicsText: '',
  juweiWitness: false // 居委会见证：勾选则建会后标记 hasMajorIssue（重大事项需居委会见证）
})
const materialPrefillOpen = ref(false)
const materialText = ref('')
const materialFiles = ref([])
const materialScanResult = ref(null)
const keyword = ref('')

// 标题推荐
const suggestedTitle = ref('')
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
const topicInput = ref('') // 议题输入框当前内容（打字/语音），点"确定"加入 topics 列表

// 会议名称：自增高文本框（空/短=一行，超长自动到两行，max-height 封顶）
const titleEl = ref(null)
function autoGrowTitle() {
  const el = titleEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = el.scrollHeight + 'px'
}
// 标题叉：有内容先清内容（推荐字重新浮现）；空但有推荐字（幽灵态）→ 把推荐字也清掉
function clearTitleOrGhost() {
  if (createForm.title) createForm.title = ''
  else suggestedTitle.value = ''
}
watch(() => createForm.title, () => nextTick(autoGrowTitle))
// 语音输入状态
const voiceTarget = ref('') // 'title' | 'topic' | 'time' | 'location' | ''
const voiceInterim = ref('') // 实时识别中（未定稿）
const voiceFinal = ref('')   // 已定稿文字
let _voiceRec = null

function show() {
  const role = getStorage('activeRole', null)
  if (!role) { redirectTo('/pages/login/login'); return }
  activeRole.value = role
  isChair.value = perm.isChair()
  isRecorder.value = perm.isRecorder()
  isExternal.value = perm.isExternal()
  canCreate.value = perm.can('committee.create')
  canViewInternal.value = perm.can('view.internal')
  setupRoleView()
  loadUnread()
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
    const [listRes, statsRes] = await Promise.all([
      api.committeeList(null),
      api.committeeStats()
    ])
    const all = Array.isArray(listRes) ? listRes : []
    const st = statsRes || {}
    const now = new Date()
    const bimonth = Math.ceil((now.getMonth() + 1) / 2)
    st.periodLabel = ((bimonth - 1) * 2 + 1) + '-' + (bimonth * 2) + '月'
    st.annualYear = now.getFullYear()
    stats.value = st

    // 当前会议卡片：进行中/准备中；主任另含"已结束未公示"（待整理纪要）
    const chair = isChair.value || isRecorder.value
    let actives = all.filter((m) => m.stage === 'preparing' || m.stage === 'ongoing')
    if (chair) {
      const pend = all.filter((m) => m.stage === 'ended'
        && m.compliance !== 'invalid'
        && (!m.publish || !m.publish.published))
      actives = [...actives, ...pend]
    }
    currents.value = actives.map((m) => decorateCurrent(m, chair))
  } catch (e) {
    stats.value = {}
    currents.value = []
  }
}

async function loadUnread() {
  try {
    const res = await api.notificationList()
    unread.value = (res && res.unread) || 0
  } catch (e) { /* 离线可忽略 */ }
}

// 给当前会议附加：三步进度、主操作按钮文案/图标
function decorateCurrent(m, chair) {
  const step = STEP_BY_STAGE[m.stage] || 1
  const steps = [1, 2, 3].map((no) => ({
    no: no,
    label: STEP_LABELS[no],
    state: no < step ? 'done' : (no === step ? 'active' : 'todo')
  }))
  let ctaLabel, ctaIcon, tag
  if (m.stage === 'preparing') {
    if (chair) {
      ctaLabel = m.allReplied ? '会议已就绪' : '继续通知'
      ctaIcon = m.allReplied ? '✅' : '📣'
    } else {
      ctaLabel = '查看会议通知'
      ctaIcon = '📋'
    }
    tag = '会议通知'
  } else if (m.stage === 'ongoing') {
    ctaLabel = chair ? '进入会议' : '查看会议'
    ctaIcon = chair ? '🎙️' : '👀'
    tag = '正在开的会'
  } else {
    ctaLabel = '整理会议记录'
    ctaIcon = '📝'
    tag = '会后总结'
  }
  return {
    id: m.id, title: m.title, meetingDate: m.meetingDate, meetingTime: (m.meetingTime || '').slice(0, 5),
    location: m.location, step: step, steps: steps,
    ctaLabel: ctaLabel, ctaIcon: ctaIcon, tag: tag
  }
}

function goCurrent(cur) {
  const chair = perm.isChair() || perm.isRecorder()
  const url = chair
    ? '/pages/committee-detail/committee-detail?id=' + cur.id
    : '/pages/my-meeting/my-meeting?id=' + cur.id
  navigateTo(url)
}

async function removeCurrent(cur) {
  const res = await showModal({
    title: '删除会议',
    content: '确定删除「' + cur.title + '」？删除后无法恢复。',
    confirmText: '删除',
    confirmColor: '#E74C3C'
  })
  if (!res.confirm) return
  try {
    await api.committeeRemove(cur.id)
    toast({ title: '已删除', icon: 'success' })
    loadAll()
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  }
}

function goNotifications() { navigateTo('/pages/notifications/notifications') }
function goReception() { navigateTo('/pages/reception/reception') }
function goLearning() { navigateTo('/pages/learning/learning') }
function goLibrary() { navigateTo('/pages/library/library') }

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
  docPrefilled.value = false
  materialPrefillOpen.value = false
  materialText.value = ''
  materialFiles.value = []
  materialScanResult.value = null
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
  createForm.juweiWitness = false
  topicInput.value = ''
  suggestedTitle.value = ''
  pendingMaterials.value = []
  scanBusy.value = ''
  lastScanTokens.value = 0
  // 基于历史会议推荐下一次标题（大多数会议为统一格式）
  try {
    const all = await api.committeeList(null)
    const sug = computeSuggestedTitle(all || [])
    suggestedTitle.value = sug // 不自动填入标题框，仅在下方作为推荐展示，点"用这个"才填入
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

// —— 拍照/上传文档 → 后端 OCR + 大模型识别 → 回填表单（OCR/AI 未开或失败则提示并回退手填） ——
const docPrefilled = ref(false)   // 已成功预填过一次（保留状态位，供后续提示用）

// ——— 待挂载的会议材料（供委员传阅：如上级文件精神、报价单等）———
// 「去通知」建会成功后再逐份挂到会议（届时触发 OCR）。来源：拍照/上传 AI 判类为 material 的文件。
const pendingMaterials = ref([])      // [{ url, fileName, fileType, fileSize, sizeText }]
function removePendingMaterial(idx) {
  pendingMaterials.value = pendingMaterials.value.filter((_, i) => i !== idx)
}
// 材料行小图标：按文件类型区分（图片/PDF/其他）
function matIcon(m) {
  const t = String((m && m.fileType) || '').toLowerCase()
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(t)) return '🖼️'
  if (t === 'pdf') return '📄'
  return '📎'
}

// ——— 拍照/上传 → OCR 识别 → AI 判类（通知/材料）→ 用户确认 ———
const scanBusy = ref('')          // '' | 'camera' | 'file'：正在识别的入口卡片
const lastScanTokens = ref(0)     // 上次识别消耗的 token（低调显示在卡片下方）
async function startDocScan(source) {
  if (scanBusy.value) return
  if (source === 'camera') {
    // 测试阶段：先模拟系统相机权限申请，允许后弹模拟相机（取景+快门）。后续接真机时改回：
    // const file = await pickFile('image/*', 'environment')
    if (camPermGranted.value) openMockCamera()
    else camPermVisible.value = true
    return
  }
  const file = await pickFile('image/*,application/pdf')
  if (!file) return // 用户取消
  await scanFile(file, source)
}

// ——— 模拟系统相机权限弹窗（真机上由系统/微信弹出，这里演示流程；每次会话只问一次） ———
const camPermVisible = ref(false)
const camPermGranted = ref(false)
function allowCamPerm() {
  camPermGranted.value = true
  camPermVisible.value = false
  openMockCamera()
}
function denyCamPerm() {
  camPermVisible.value = false
  toast({ title: '未获得相机权限，无法拍照', icon: 'none' })
}
// 识别弹窗的阶段与文案：按进度分 上传→OCR提取→AI理解 三步（模拟节奏，后端一次性返回）
const scanSec = ref(0)
let _scanSecTimer = null
const scanStage = computed(() => (docProgress.value < 25 ? 1 : docProgress.value < 70 ? 2 : 3))
const scanSay = computed(() => {
  if (docProgress.value < 25) return '正在安全上传文件…'
  if (docProgress.value < 70) return '正在逐行提取文字…'
  return '正在理解内容、判断文件类型…'
})

// 拿到文件（拍照/选文件）后的统一识别链路
async function scanFile(file, source) {
  scanBusy.value = source
  startDocProgress()
  scanSec.value = 0
  clearInterval(_scanSecTimer)
  _scanSecTimer = setInterval(() => { scanSec.value += 1 }, 1000)
  try {
    const res = await api.committeeParseDocument(file)
    stopDocProgress()
    docProgress.value = 100
    await new Promise((r) => setTimeout(r, 350))
    if (res && res.tokens > 0) lastScanTokens.value = res.tokens
    // 先收起识别弹窗，再弹结果确认框（否则确认框会压在识别弹窗上）
    clearInterval(_scanSecTimer)
    _scanSecTimer = null
    scanBusy.value = ''
    docProgress.value = 0
    await handleScanResult(res)
  } catch (e) {
    stopDocProgress()
    toast({ title: '识别失败，请重试或手动填写', icon: 'none' })
  } finally {
    clearInterval(_scanSecTimer)
    _scanSecTimer = null
    scanBusy.value = ''
    docProgress.value = 0
  }
}

// ——— 模拟手机相机（测试用）：全屏取景框对着一张"纸质会议通知"，按快门出照片走真实识别 ———
const mockCameraVisible = ref(false)
const mockCamFlash = ref(false)
const mockViewfinderUrl = ref('')
const mockShotUrl = ref('')       // 拍后照片预览图（非空 = 预览确认模式）
let _mockShotCanvas = null
let _mockShotFile = null          // 预览中待确认的照片文件，点「使用照片」才送识别
function buildSampleNoticeCanvas() {
  const cv = document.createElement('canvas'); cv.width = 750; cv.height = 940
  const ctx = cv.getContext('2d')
  ctx.fillStyle = '#fdfcf8'; ctx.fillRect(0, 0, 750, 940)
  ctx.fillStyle = '#111'; ctx.textAlign = 'center'
  ctx.font = 'bold 46px serif'
  ctx.fillText('会 议 通 知', 375, 96)
  ctx.font = '32px serif'; ctx.textAlign = 'left'
  const lines = [
    '各位业主委员会委员：',
    '',
    '兹定于2026年7月28日上午10:00，',
    '在小区党群服务中心二楼会议室，',
    '召开2026年第8次业委会例会。',
    '',
    '会议议题：',
    '一、审议消防设施维修改造方案；',
    '二、讨论电动车充电桩扩建选址；',
    '三、通报小区公共收益使用明细。',
    '',
    '请各位委员准时出席，不能到会者',
    '请提前向主任请假。',
    '',
    '                    业主委员会',
    '                  2026年7月2日'
  ]
  let y = 180; for (const l of lines) { ctx.fillText(l, 64, y); y += 46 }
  return cv
}
function openMockCamera() {
  _mockShotCanvas = buildSampleNoticeCanvas()
  mockViewfinderUrl.value = _mockShotCanvas.toDataURL('image/png')
  mockShotUrl.value = ''
  _mockShotFile = null
  mockCameraVisible.value = true
}
function closeMockCamera() { mockCameraVisible.value = false; mockShotUrl.value = ''; _mockShotFile = null }
// 模拟真实相机成片：按取景框比例出竖幅照片（纸张摆在深色桌面上），预览时能铺满屏幕
function buildShotPhotoCanvas(paperCanvas) {
  const vp = document.querySelector('.mc-viewport')
  const ratio = Math.min(2.4, Math.max(1.2, vp ? vp.clientHeight / vp.clientWidth : 16 / 9))
  const W = 750, H = Math.round(W * ratio)
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H
  const ctx = cv.getContext('2d')
  const g = ctx.createLinearGradient(0, 0, 0, H)
  g.addColorStop(0, '#4A4640'); g.addColorStop(1, '#38352F')
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H)
  const pw = Math.round(W * 0.9), ph = Math.round(pw * paperCanvas.height / paperCanvas.width)
  ctx.shadowColor = 'rgba(0,0,0,0.45)'; ctx.shadowBlur = 26; ctx.shadowOffsetY = 10
  ctx.drawImage(paperCanvas, (W - pw) / 2, (H - ph) / 2, pw, ph)
  return cv
}
// 按快门：只拍照进预览确认（像系统相机），不直接识别
async function mockShoot() {
  if (!_mockShotCanvas) return
  mockCamFlash.value = true
  setTimeout(() => { mockCamFlash.value = false }, 180)
  const shot = buildShotPhotoCanvas(_mockShotCanvas)
  const blob = await new Promise((r) => shot.toBlob(r, 'image/png'))
  _mockShotFile = new File([blob], '拍照-纸质文件.png', { type: 'image/png' })
  await new Promise((r) => setTimeout(r, 260)) // 让"咔嚓"闪一下再切预览
  mockShotUrl.value = shot.toDataURL('image/png')
}
function mockRetake() { mockShotUrl.value = ''; _mockShotFile = null }
// 「使用照片」：用户确认照片没问题，收起相机开始识别
async function mockUsePhoto() {
  const file = _mockShotFile
  if (!file) return
  mockCameraVisible.value = false
  mockShotUrl.value = ''
  _mockShotFile = null
  await scanFile(file, 'camera')
}
// 识别结果 → 按 AI 判类让用户确认；确认错了可一键改成另一类
async function handleScanResult(res) {
  if (!res) { toast({ title: '未识别到内容，请手动填写', icon: 'none' }); return }
  const canAttach = !!res.fileUrl // 文件已存服务器，可直接作为材料挂载
  // token 消耗放进结果弹窗括号里：让用户直观感到 AI 真干活了
  const tokenNote = res.tokens > 0 ? '（本次智能识别消耗 ' + Number(res.tokens).toLocaleString() + ' token）' : ''
  if (res.available && res.category === 'notice') {
    // 已填过会议信息（识别或手填）→ 改问“是否覆盖”，避免再次拍照悄悄冲掉上一次的内容
    const dirty = formHasUserContent()
    const r = await showModal({
      title: dirty ? '覆盖已填写的信息？' : 'AI 智能识别：会议通知',
      content: dirty
        ? '当前已填写会议信息。要用这次识别的内容覆盖吗？' + tokenNote
        : '已深度识别文件内容，判定为会议通知。要按它自动填写会议信息吗？' + tokenNote,
      confirmText: dirty ? '覆盖填写' : '自动填写',
      cancelText: dirty ? '保留原信息' : (canAttach ? '改为会议材料' : '取消'),
      size: 'large',
      showClose: true
    })
    if (r.close) return // 右上角 ×：只关弹窗，什么都不做
    if (r.confirm) applyPrefill(res, { skipOverwriteConfirm: true }) // 覆盖已在上面确认过
    else if (!dirty && canAttach) addScannedMaterial(res)
    // dirty 且取消 = 保留原信息，什么都不做
    return
  }
  if (res.available && res.category === 'material') {
    const r = await showModal({
      title: 'AI 智能识别：会议材料',
      content: '已深度识别文件内容，判定为会议材料，将在通知发出后发给委员传阅。' + tokenNote,
      confirmText: '添加为材料',
      cancelText: '改为自动填表',
      size: 'large',
      showClose: true
    })
    if (r.close) return
    if (r.confirm) addScannedMaterial(res)
    else applyPrefill(res) // 强制按通知抽取；字段可能为空，applyPrefill 会提示
    return
  }
  // 识别失败/未判出类别：文件已存的话，问要不要直接作为材料
  if (canAttach) {
    const r = await showModal({
      title: '未能识别内容',
      content: (res.message || '未能识别出文件内容') + '。要把它直接作为会议材料添加吗？',
      confirmText: '添加为材料',
      cancelText: '不用了',
      size: 'large',
      showClose: true
    })
    if (r.confirm) addScannedMaterial(res)
  } else {
    toast({ title: res.message || '未能识别，请手动填写', icon: 'none' })
  }
}
function addScannedMaterial(res) {
  pendingMaterials.value = pendingMaterials.value.concat([{
    url: res.fileUrl, fileName: res.fileName || '识别文件', fileType: res.fileType || '',
    fileSize: res.fileSize || 0, sizeText: humanSize(res.fileSize || 0)
  }])
  toast({ title: '已加入会议材料', icon: 'success' })
}
const docProgress = ref(0)
let docProgTimer = null
// 模拟进度：后端一次性返回拿不到真实百分比，定时器渐进逼近 90%（越近越慢），完成时跳 100%
function startDocProgress() {
  docProgress.value = 0
  clearInterval(docProgTimer)
  docProgTimer = setInterval(() => {
    const p = docProgress.value
    if (p >= 90) return
    const step = p < 60 ? 7 : p < 80 ? 3 : 1
    docProgress.value = Math.min(90, p + step)
  }, 350)
}
function stopDocProgress() {
  clearInterval(docProgTimer)
  docProgTimer = null
}
// 表单是否已有“实质内容”：日期/时间/地点开窗时就带默认值不算数，
// 只认 识别预填过(docPrefilled) / 手填了标题 / 加了议题 —— 用来决定再次识别时是否要问“覆盖”
function formHasUserContent() {
  return docPrefilled.value
    || !!(createForm.title && createForm.title.trim())
    || (Array.isArray(createForm.topics) && createForm.topics.length > 0)
}
async function applyPrefill(data, opts = {}) {
  if (!data) { toast({ title: '未识别到内容，请手动填写', icon: 'none' }); return }
  const hasFields = !!(data.title || data.meetingDate || data.meetingTime || data.location || (Array.isArray(data.topics) && data.topics.length))
  if (data.available && hasFields) {
    // 已有会议信息且外层没先确认过 → 先问是否覆盖（“改为自动填表”等入口也会走到这里）
    if (!opts.skipOverwriteConfirm && formHasUserContent()) {
      const c = await showModal({
        title: '覆盖已填写的信息？',
        content: '当前已填写会议信息。要用这次识别的内容覆盖吗？',
        confirmText: '覆盖填写',
        cancelText: '保留原信息',
        showClose: true
      })
      if (!c.confirm) return // 保留原信息 / × ：不覆盖
    }
    if (data.title) createForm.title = data.title
    if (data.meetingDate) createForm.meetingDate = data.meetingDate
    if (data.meetingTime) createForm.meetingTime = data.meetingTime
    if (data.location) { createForm.location = data.location; syncLocationPreset(data.location) }
    if (Array.isArray(data.topics) && data.topics.length) {
      createForm.topics = data.topics.map((t) => ({ title: String(t), type: 'decision', decisionType: 'simple', options: [] }))
    }
    docPrefilled.value = true
    toast({ title: '已自动填写，请核对', icon: 'none' })
  } else if (data.available) {
    toast({ title: '未识别出会议信息，请手动填写', icon: 'none' })
  } else {
    toast({ title: data.message || '未能识别，请手动填写', icon: 'none' })
  }
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

// 地图选点（接口预留）：点地图图标 → 选高德/百度 → 真实接入时打开地图 app 选点回传地址。
// 现为演示桩：未接真实地图，仅模拟从地图选回一个地址，便于测试展示。
async function pickLocationOnMap() {
  const res = await showActionSheet({ itemList: ['高德地图', '百度地图'] })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  const app = res.tapIndex === 0 ? '高德地图' : '百度地图'
  // TODO 接入真实地图：经 URL scheme / 地图 JS-SDK 打开 app 选点，回传经纬度+地址后回填 createForm.location
  locationPreset.value = '__other__'
  createForm.location = '阳光家园·活动中心（' + app + '选点·演示）'
  toast({ title: '已从' + app + '选择地点（演示）', icon: 'none' })
}

// 从"其他/地图选点"切回常用地点下拉
function resetLocPreset() {
  locationPreset.value = '社区活动室'
  createForm.location = '社区活动室'
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

// —— 小日历：用 dpYear/dpMonth 作当前查看月，点某天即选并关闭 ——
const calCells = computed(() => {
  const first = new Date(dpYear.value, dpMonth.value - 1, 1).getDay() // 0=周日
  const days = new Date(dpYear.value, dpMonth.value, 0).getDate()
  const cells = []
  for (let i = 0; i < first; i++) cells.push(0) // 月初空格
  for (let d = 1; d <= days; d++) cells.push(d)
  return cells
})
function calPrevMonth() {
  if (dpMonth.value <= 1) { dpMonth.value = 12; dpYear.value -= 1 } else { dpMonth.value -= 1 }
}
function calNextMonth() {
  if (dpMonth.value >= 12) { dpMonth.value = 1; dpYear.value += 1 } else { dpMonth.value += 1 }
}
function calDateStr(day) {
  return dpYear.value + '-' + String(dpMonth.value).padStart(2, '0') + '-' + String(day).padStart(2, '0')
}
function isSelectedDay(day) { return createForm.meetingDate === calDateStr(day) }
function isToday(day) { return todayStr() === calDateStr(day) }
function pickCalDay(day) {
  createForm.meetingDate = calDateStr(day)
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
// 大按钮点选：点即更新并实时写入会议时间（免"确定"那一步）
function applyTime() {
  createForm.meetingTime = String(tpHour.value).padStart(2, '0') + ':' + String(tpMinute.value).padStart(2, '0')
}
function setTpHour(h) { tpHour.value = h; applyTime() }
function setTpMinute(m) { tpMinute.value = m; applyTime() }

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

function topicTypeClass(t) {
  if (!t) return 'badge-discussion'
  if (t.type === 'notice') return 'badge-notice'
  if (t.type === 'decision') return 'badge-decision'
  return 'badge-discussion'
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
  // OCR 还在识别时先别提交：此刻 createForm 可能是中间态，等识别完再去通知
  if (scanBusy.value) { toast({ title: '正在识别中，请稍候…', icon: 'none' }); return }
  if (!form.title || !form.meetingDate || !form.meetingTime || !form.location) {
    toast({ title: '请补全标题、时间和地点', icon: 'none' })
    return
  }
  // 议题：逐条添加在 createForm.topics（过滤空标题）
  var topics = (form.topics || []).filter(function (t) { return t.title && t.title.trim() })
  if (!topics.length) {
    toast({ title: '请至少添加一个会议议题', icon: 'none' })
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
    // 居委会见证：勾选则标记本次为重大事项（hasMajorIssue）。失败不阻断建会流程。
    if (created && created.id && form.juweiWitness) {
      try { await api.committeeToggleFlag(created.id, 'hasMajorIssue') } catch (e) {}
    }
    // 会议材料：弹窗里传好的文件逐份挂到会议（供委员传阅，后端顺带触发 OCR）。单份失败不阻断建会。
    if (created && created.id && pendingMaterials.value.length) {
      for (const m of pendingMaterials.value) {
        try { await api.committeeAddMaterial(created.id, m.fileName, m.sizeText, m.fileType, m.url) } catch (e) { console.error('[材料] 挂载失败：', m.fileName, e) }
      }
    }
    console.log('[去通知] created =', JSON.stringify(created))
    createVisible.value = false
    currentStage.value = 'preparing'
    if (created && created.id) {
      // 竞态兜底：navigateTo(router.push) 偶发被取消/重复导航会 reject，或"URL变了却不切换视图"，
      // 都会把用户留在主页（弹窗已关）。软跳后延时校验通知页(.detail-page)是否真的挂上，没挂上就
      // window.location 硬跳过去，确保必达（对齐本 app 其它关键跳转的硬导航兜底做法）。
      const target = '/pages/committee-detail/committee-detail?id=' + created.id
      const browserUrl = '/committee-detail?id=' + created.id
      console.log('[去通知] 跳转 →', target)
      try { await navigateTo(target) } catch (navErr) { console.error('[去通知] 软跳 reject：', navErr) }
      setTimeout(() => {
        if (!document.querySelector('.detail-page')) {
          console.warn('[去通知] 软跳未挂载通知页，硬导航兜底 →', browserUrl)
          window.location.href = browserUrl
        }
      }, 500)
    } else {
      console.warn('[去通知] created 无 id，回列表', created)
      toast({ title: '会议已创建，请在列表中打开', icon: 'none' })
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

// ——— 语音输入 ———（热词纠错 applyHotwords 已抽到 @/utils/helpers 与 MeetingLiveQuick 共用）

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
function addTopicFromInput() {
  const t = topicInput.value.trim()
  if (!t) { toast({ title: '请输入议题内容', icon: 'none' }); return }
  createForm.topics = createForm.topics.concat([{ title: t, type: 'decision', decisionType: 'simple', options: [] }])
  topicInput.value = ''
}

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
    if (fin) voiceFinal.value += applyHotwords(fin)
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
    topicInput.value = text
  }
}

function retryVoice() {
  const target = voiceTarget.value
  startStreamingVoice(target)
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
.home {
  min-height: 100vh; background: var(--c-bg-page);
  /* 底部留足空间：清开固定 TabBar(100rpx) 再多留 ~80rpx，避免有进行中会议内容超一屏时"更多功能"被导航栏挡住 */
  padding-bottom: calc(180rpx + env(safe-area-inset-bottom));
  display: flex; flex-direction: column; box-sizing: border-box;
}
/* 顶栏 */
.hd { display: flex; align-items: flex-end; justify-content: space-between; padding: env(safe-area-inset-top) 32rpx 10rpx; background: var(--c-primary-dark); }
.hd-left { display: flex; flex-direction: column; padding-top: 4rpx; }
.hd-title { font-size: 52rpx; font-weight: 700; color: #fff; }
.hd-sub { font-size: 34rpx; color: #fff; margin-top: 8rpx; }
.hd-bell { position: relative; padding: 8rpx; align-self: center; }
.hd-bell-ico { font-size: 52rpx; }
.hd-badge { position: absolute; top: -2rpx; right: -6rpx; min-width: 34rpx; height: 34rpx; padding: 0 8rpx; background: var(--c-danger); color: #fff; font-size: 28rpx; border-radius: 17rpx; line-height: 34rpx; text-align: center; }
/* 当前会议主卡片 */
.meet-card { margin: 28rpx 24rpx 32rpx; background: var(--c-bg-card); border-radius: 22rpx; padding: 28rpx 30rpx 30rpx; box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.05); }
.meet-tag { font-size: 30rpx; color: var(--c-primary-dark); font-weight: 600; }
.meet-title { display: block; font-size: 42rpx; font-weight: 700; color: var(--c-text-strong); margin-top: 0; line-height: 1.35; }
.meet-meta { display: block; font-size: 30rpx; color: var(--c-text-mid); margin-top: 16rpx; }
/* 三步进度 */
.steps { display: flex; align-items: flex-start; justify-content: space-between; margin: 28rpx 8rpx 28rpx; }
.step { display: flex; flex-direction: column; align-items: center; width: 140rpx; }
.step-dot { width: 62rpx; height: 62rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 32rpx; font-weight: 700; background: #E3E5E9; color: var(--c-text-weak); }
.step-label { font-size: 28rpx; color: var(--c-text-weak); margin-top: 12rpx; }
.step.done .step-dot { background: var(--c-primary-soft); color: var(--c-primary-dark); }
.step.done .step-label { color: var(--c-text-mid); }
.step.active .step-dot { background: var(--c-primary-dark); color: #fff; }
.step.active .step-label { color: var(--c-text-strong); font-weight: 700; }
.step-line { flex: 1; height: 6rpx; border-radius: 3rpx; margin-top: 28rpx; }
.step-line.done { background: var(--c-primary); }
.step-line.todo { background: #E3E5E9; }
/* 大按钮（描边幽灵：白底 + 橙边橙字；去开会与卡片"继续通知"同款同大小、字略放大） */
.big-btn { display: flex; align-items: center; justify-content: center; height: 140rpx; border-radius: 22rpx; background: var(--c-bg-card); border: 3rpx solid var(--c-primary-dark); margin-top: 8rpx; }
.big-btn:active { background: var(--c-primary-soft); }
.big-btn-ico { font-size: 54rpx; margin-right: 14rpx; }
.big-btn-text { font-size: 52rpx; font-weight: 700; color: var(--c-primary-dark); }
/* 去开会主按钮：缩窄并居中（比卡片按钮收得更多，两者看起来差不多宽） */
.go-meeting { margin: auto auto 16rpx; width: 84%; }
/* 卡片内"继续通知"：略收窄并居中 */
.meet-card .big-btn { width: 90%; margin-left: auto; margin-right: auto; }
/* 删除会议（测试用，弱化） */
.meet-del { text-align: center; color: var(--c-danger); font-size: 30rpx; margin-top: 28rpx; padding: 8rpx; }
.meet-del:active { opacity: 0.6; }
/* 空闲态 */
.idle { margin: 64rpx 24rpx 0; display: flex; flex-direction: column; align-items: center; }
.idle-emoji { font-size: 104rpx; margin-bottom: 24rpx; }
.idle-hint { font-size: 38rpx; color: var(--c-text-mid); margin-bottom: 8rpx; }
.idle-sub { font-size: 30rpx; color: var(--c-text-weak); margin-top: 6rpx; }
/* 更多功能（主任视图紧跟卡片；委员视图沉底） */
.more { margin: 0 28rpx; padding-top: 76rpx; }
.more-sink { margin-top: auto; }
.more-title { font-size: 28rpx; color: var(--c-text-weak); padding-left: 6rpx; }
.more-grid { display: flex; margin-top: 14rpx; gap: 16rpx; }
.more-item { flex: 1; background: var(--c-bg-card); border-radius: 18rpx; padding: 36rpx 0 32rpx; display: flex; flex-direction: column; align-items: center; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04); }
.more-item:active { background: #FAFBFC; }
.more-ico { font-size: 44rpx; line-height: 1.1; }
.more-label { font-size: 28rpx; color: var(--c-text-mid); margin-top: 14rpx; }

/* 综合评分小字（占位分数） */
.score-line { display: flex; align-items: center; gap: 10rpx; margin: 24rpx 28rpx 0; font-size: 32rpx; color: var(--c-text-mid); }
.score-ico { font-size: 38rpx; }
.score-num { font-size: 46rpx; font-weight: 700; color: var(--c-primary-dark); margin-left: 6rpx; }
.score-unit { font-size: 30rpx; color: var(--c-text-weak); }
/* 目标卡片（放大一些） */
.target-row { display: grid; grid-template-columns: 1fr 1fr; gap: 24rpx; padding: 24rpx 24rpx 0; }
.target-card { background: var(--c-bg-card); border-radius: 24rpx; padding: 44rpx 32rpx; box-shadow: 0 6rpx 20rpx rgba(0,0,0,0.06); }
.tc-head { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; margin-bottom: 20rpx; }
.tc-label { font-size: 36rpx; color: var(--c-text-mid); font-weight: 500; line-height: 1.35; word-break: break-all; }
.tc-badge { font-size: 28rpx; font-weight: 600; padding: 4rpx 14rpx; border-radius: 10rpx; line-height: 1.35; white-space: nowrap; }
.tc-badge.ok { background: var(--c-success-soft); color: var(--c-success); }
.tc-badge.warn { background: var(--c-warning-soft); color: var(--c-warning); }
.tc-num { font-size: 84rpx; font-weight: 700; color: var(--c-text-strong); line-height: 1; margin-bottom: 16rpx; font-variant-numeric: tabular-nums; }
.tc-unit { font-size: 34rpx; font-weight: 500; color: var(--c-text-mid); margin-left: 10rpx; }
.tc-sub { font-size: 30rpx; font-weight: 600; color: var(--c-text-weak); margin-bottom: 4rpx; display: block; line-height: 1.55; word-break: break-all; }
.tc-sub-warn { color: var(--c-warning); font-weight: 500; }
.tc-progress { background: #E3E5E9; border-radius: 6rpx; height: 12rpx; overflow: hidden; margin-bottom: 10rpx; }
.tc-fill { height: 100%; border-radius: 6rpx; background: var(--c-primary); }
.tc-rule { font-size: 28rpx; color: var(--c-text-mid); line-height: 1.45; word-break: break-all; }

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
.modal-mask { position: fixed; inset: 0; z-index: 200; background: #fff; display: flex; align-items: stretch; }
.create-panel { width: 100%; height: 100%; background: #fff; display: flex; flex-direction: column; overflow: hidden; }
.create-head { display: flex; align-items: center; background: var(--c-primary-dark); flex-shrink: 0; padding-top: env(safe-area-inset-top); box-sizing: border-box; height: calc(120rpx + env(safe-area-inset-top)); }
.create-back { width: 96rpx; height: 120rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 64rpx; font-weight: 700; flex-shrink: 0; }
.create-title { flex: 1; text-align: center; color: #fff; font-size: 38rpx; font-weight: 700; line-height: 1.35; }
.create-nav-ph { width: 96rpx; flex-shrink: 0; }
.close-btn { width: 60rpx; height: 60rpx; line-height: 56rpx; text-align: center; border-radius: 30rpx; color: #666; background: #f5f5f5; font-size: 40rpx; flex-shrink: 0; }
.create-body { flex: 1; min-height: 0; overflow-y: auto; padding: 24rpx 26rpx; box-sizing: border-box; }
.create-section { background: #fafbfc; border-radius: 18rpx; padding: 24rpx; margin-bottom: 20rpx; border: 2rpx solid #f0f0f0; }
.create-section.title-card { margin-top: 20rpx; margin-bottom: 30px; }
.create-section.info-card { margin-bottom: 30px; }

.prefill-entry { background: #FFF8EA; border: 2rpx solid #FFE0A3; border-radius: 18rpx; padding: 22rpx; margin-bottom: 20rpx; display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.prefill-copy { flex: 1; min-width: 0; }
.prefill-title { display: block; font-size: 28rpx; color: #5C3D00; font-weight: 700; line-height: 1.45; word-break: break-all; }
.prefill-desc { display: block; font-size: 28rpx; color: #9A7600; margin-top: 6rpx; line-height: 1.45; word-break: break-all; }
.prefill-toggle { flex-shrink: 0; min-width: 88rpx; height: 56rpx; line-height: 56rpx; text-align: center; border-radius: 28rpx; background: #fff; color: #C77800; font-size: 28rpx; font-weight: 600; }
.material-section { background: #fffdf7; border-color: #FFE8B8; }
/* 快速填表按钮（窄·居中·描边药丸：明显但不抢"确认创建"，深字高对比） */
.ai-fill-btn { width: 64%; margin: 44rpx auto 18rpx; display: flex; align-items: center; justify-content: center; gap: 12rpx; padding: 26rpx 28rpx; background: var(--c-primary-dark); border: none; border-radius: 999rpx; box-shadow: 0 6rpx 16rpx rgba(168,88,0,0.22); box-sizing: border-box; }
.ai-fill-btn:active { background: var(--c-primary-strong); }
.ai-fill-ico { font-size: 38rpx; line-height: 1; }
.ai-fill-text { font-size: 32rpx; font-weight: 700; color: #fff; }
.ai-fill-btn:disabled, .ai-fill-btn.busy { opacity: 0.65; }
.ai-fill-hint { display: block; text-align: center; font-size: 26rpx; color: #999; margin: -8rpx 0 16rpx; }
/* 快速填表固定条：底部拇指区（创建/取消上方），进入即见、不随表单滚动 */
.quick-fill-bar { flex-shrink: 0; padding: 16rpx 26rpx 10rpx; background: var(--c-bg-page); border-top: 1rpx solid #ececec; }
.quick-fill-bar .ai-fill-btn { margin: 0 auto 10rpx; }
.quick-fill-bar .ai-fill-hint { margin: 0; }
/* 拍照/上传 双卡片（政务风功能入口）：图标圆 + 标题 + 两行说明整合在卡片内 */
.doc-scan-bar { flex-shrink: 0; padding: 16rpx 26rpx 10rpx; background: var(--c-bg-page); border-top: 1rpx solid #ececec; }
.ds-cards { display: flex; gap: 18rpx; }
.ds-card { flex: 1; min-width: 0; background: #fff; border: 2rpx solid #eee; border-radius: 20rpx; padding: 20rpx 10rpx 16rpx; display: flex; flex-direction: column; align-items: center; gap: 6rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.05); }
.ds-card:active { background: #FFF8EE; border-color: #FFD79A; }
.ds-card:disabled { opacity: 0.75; }
.ds-ico { width: 76rpx; height: 76rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 40rpx; margin-bottom: 4rpx; }
.ds-ico.or { background: #FFF3E0; }
.ds-ico.bl { background: #EAF2FF; }
.ds-t { font-size: 32rpx; font-weight: 700; color: #1f2329; line-height: 1.3; }
.ds-s { font-size: 24rpx; color: #999; text-align: center; line-height: 1.45; }
.ds-spin { width: 68rpx; height: 68rpx; border-radius: 50%; border: 6rpx solid rgba(168,88,0,0.2); border-top-color: var(--c-primary-dark); box-sizing: border-box; animation: aiSpin 0.7s linear infinite; margin-bottom: 4rpx; }
/* token 消耗：低调小字，识别完成后显示 */
.ds-tokens { display: block; text-align: center; font-size: 22rpx; color: #bbb; margin-top: 10rpx; }

/* 模拟系统相机权限弹窗（仿系统样式：居中白盒 + 细线分隔双按钮） */
.perm-mask { position: fixed; inset: 0; z-index: 3100; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; }
.perm-box { width: 540rpx; background: #fff; border-radius: 28rpx; padding: 44rpx 36rpx 0; display: flex; flex-direction: column; align-items: center; text-align: center; box-shadow: 0 20rpx 60rpx rgba(0,0,0,0.25); }
.perm-ico { font-size: 64rpx; line-height: 1; margin-bottom: 18rpx; }
.perm-title { font-size: 34rpx; font-weight: 700; color: #1a1a1a; line-height: 1.45; }
.perm-sub { font-size: 28rpx; color: #888; line-height: 1.5; margin: 10rpx 0 34rpx; }
.perm-btns { display: flex; width: calc(100% + 72rpx); margin: 0 -36rpx; border-top: 1rpx solid #e5e5e5; }
.perm-btn { flex: 1; height: 100rpx; background: none; border: none; font-size: 34rpx; }
.perm-btn.deny { color: #666; border-right: 1rpx solid #e5e5e5; }
.perm-btn.allow { color: #0A7CFF; font-weight: 700; }
.perm-btn:active { background: #f5f5f5; }

/* 识别中：居中小弹窗（文档扫描动画 + 三步流程 + 动态文案） */
.scan-pop-mask { position: fixed; inset: 0; z-index: 3050; background: rgba(10, 8, 4, 0.42); backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; }
.scan-pop { width: 640rpx; max-width: 92%; background: linear-gradient(180deg, #FFFDF9 0%, #fff 30%); border: 1rpx solid rgba(255, 168, 0, 0.25); border-radius: 30rpx; padding: 48rpx 44rpx 38rpx; display: flex; flex-direction: column; align-items: center; box-shadow: 0 20rpx 60rpx rgba(120, 70, 0, 0.28), 0 0 0 6rpx rgba(255, 168, 0, 0.06); box-sizing: border-box; }
/* 文档扫描动画：白纸 + 灰色文字行 + 橙色扫描线上下来回 */
.sp-doc { position: relative; width: 240rpx; height: 288rpx; background: #fff; border: 2rpx solid #F0E6D6; border-radius: 14rpx; box-shadow: 0 6rpx 18rpx rgba(160, 110, 20, 0.12); padding: 30rpx 26rpx; box-sizing: border-box; display: flex; flex-direction: column; gap: 26rpx; overflow: hidden; margin-bottom: 28rpx; }
.sp-doc-line { height: 12rpx; border-radius: 6rpx; background: #EAE4D8; }
.sp-doc-line.w80 { width: 80%; } .sp-doc-line.w95 { width: 95%; } .sp-doc-line.w70 { width: 70%; } .sp-doc-line.w90 { width: 90%; } .sp-doc-line.w60 { width: 60%; }
.sp-scanline { position: absolute; left: 6rpx; right: 6rpx; top: 0; height: 50rpx; border-radius: 6rpx; background: linear-gradient(180deg, rgba(255,168,0,0) 0%, rgba(255,168,0,0.28) 55%, rgba(199,106,0,0.55) 100%); border-bottom: 3rpx solid #FFA800; box-shadow: 0 6rpx 14rpx rgba(255,168,0,0.35); animation: spScan 2.2s ease-in-out infinite; }
@keyframes spScan { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(238rpx); } }
.sp-title { font-size: 42rpx; font-weight: 700; color: #1f2329; letter-spacing: 1rpx; }
.sp-say { font-size: 32rpx; color: #B07400; margin: 12rpx 0 30rpx; min-height: 44rpx; }
/* 三步流程点 */
.sp-steps { display: flex; align-items: center; width: 100%; margin-bottom: 30rpx; padding: 0 8rpx; box-sizing: border-box; }
.sp-step { display: flex; flex-direction: column; align-items: center; gap: 8rpx; flex-shrink: 0; }
.sp-dot { width: 56rpx; height: 56rpx; border-radius: 50%; background: #F2F2F4; border: 2rpx solid #E3E3E6; color: #999; font-size: 30rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.sp-step.active .sp-dot { background: #FFA800; border-color: #FFA800; color: #fff; box-shadow: 0 0 0 8rpx rgba(255,168,0,0.15); animation: spPulse 1.6s ease-out infinite; }
.sp-step.done .sp-dot { background: #27AE60; border-color: #27AE60; color: #fff; }
@keyframes spPulse { 0% { box-shadow: 0 0 0 0 rgba(255,168,0,0.35); } 70% { box-shadow: 0 0 0 14rpx rgba(255,168,0,0); } 100% { box-shadow: 0 0 0 0 rgba(255,168,0,0); } }
.sp-slabel { font-size: 28rpx; color: #999; white-space: nowrap; }
.sp-step.active .sp-slabel { color: #C76A00; font-weight: 600; }
.sp-step.done .sp-slabel { color: #27AE60; }
.sp-step-line { flex: 1; height: 4rpx; background: #EDEDEF; margin: 0 12rpx 38rpx; border-radius: 2rpx; }
.sp-step-line.done { background: #27AE60; }
/* 进度条 + 百分比同排 */
.sp-prog-row { display: flex; align-items: center; gap: 18rpx; width: 100%; }
.sp-bar { flex: 1; height: 18rpx; border-radius: 10rpx; background: #F0F0F2; overflow: hidden; }
.sp-fill { height: 100%; border-radius: 10rpx; background: linear-gradient(90deg, #FFA800, #C76A00); transition: width .35s ease; }
.sp-pct { font-size: 38rpx; color: #C76A00; font-weight: 800; font-variant-numeric: tabular-nums; min-width: 96rpx; text-align: right; }
.sp-foot { font-size: 26rpx; color: #C0B49E; margin-top: 24rpx; }

/* 模拟手机相机（测试用） */
.mock-cam { position: fixed; inset: 0; z-index: 3000; background: #000; display: flex; flex-direction: column; }
.mc-top { flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; padding: calc(20rpx + env(safe-area-inset-top)) 28rpx 16rpx; }
.mc-badge { font-size: 24rpx; color: #FFD79A; background: rgba(255, 215, 154, 0.14); border: 1rpx solid rgba(255, 215, 154, 0.4); padding: 6rpx 18rpx; border-radius: 999rpx; }
.mc-close { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 52rpx; line-height: 1; background: rgba(255,255,255,0.14); border-radius: 50%; }
.mc-viewport { flex: 1; min-height: 0; position: relative; display: flex; align-items: center; justify-content: center; padding: 30rpx 44rpx; }
.mc-paper { max-width: 100%; max-height: 100%; border-radius: 6rpx; box-shadow: 0 10rpx 50rpx rgba(0,0,0,0.8); transform: rotate(-1.2deg); }
/* 拍后预览：背景放柔（深灰替代纯黑），照片尽量占满取景区 */
.mock-cam.preview { background: #2F3136; }
.mock-cam.preview .mc-viewport { padding: 12rpx 16rpx; }
.mc-paper.shot { width: 100%; height: 100%; object-fit: contain; transform: none; box-shadow: none; border-radius: 0; }
.mc-confirm-tip { flex-shrink: 0; text-align: center; font-size: 28rpx; color: rgba(255,255,255,0.85); padding-top: 14rpx; }
.mock-cam.preview .mc-bottom { padding-top: 20rpx; }
.mc-corner { position: absolute; width: 52rpx; height: 52rpx; border: 5rpx solid #FFD34D; }
.mc-corner.tl { top: 18rpx; left: 26rpx; border-right: none; border-bottom: none; border-radius: 10rpx 0 0 0; }
.mc-corner.tr { top: 18rpx; right: 26rpx; border-left: none; border-bottom: none; border-radius: 0 10rpx 0 0; }
.mc-corner.bl { bottom: 18rpx; left: 26rpx; border-right: none; border-top: none; border-radius: 0 0 0 10rpx; }
.mc-corner.br { bottom: 18rpx; right: 26rpx; border-left: none; border-top: none; border-radius: 0 0 10rpx 0; }
.mc-tip { position: absolute; bottom: 34rpx; left: 0; right: 0; text-align: center; font-size: 26rpx; color: rgba(255,255,255,0.75); }
.mc-bottom { flex-shrink: 0; display: flex; justify-content: center; padding: 30rpx 0 calc(46rpx + env(safe-area-inset-bottom)); }
.mc-shutter { width: 140rpx; height: 140rpx; border-radius: 50%; background: transparent; border: 8rpx solid #fff; display: flex; align-items: center; justify-content: center; padding: 0; }
.mc-shutter-core { width: 104rpx; height: 104rpx; border-radius: 50%; background: #fff; transition: transform .12s ease; }
.mc-shutter:active .mc-shutter-core { transform: scale(0.85); }
.mc-bottom.confirm { gap: 28rpx; padding-left: 44rpx; padding-right: 44rpx; }
.mc-btn { flex: 1; height: 96rpx; border-radius: 48rpx; font-size: 34rpx; font-weight: 700; border: none; }
.mc-btn.retake { background: rgba(255,255,255,0.16); color: #fff; }
.mc-btn.use { background: var(--c-primary-dark); color: #fff; }
.mc-flash { position: absolute; inset: 0; background: #fff; opacity: 0; pointer-events: none; transition: opacity .1s ease; }
.mc-flash.on { opacity: 0.9; }
/* 会议材料行：小图标 + 可点文件名（点开全屏预览）+ 大小 + 删除 */
.mat-line { display: flex; align-items: center; gap: 14rpx; padding: 18rpx 4rpx; border-bottom: 1rpx solid #f0f0f0; }
.mat-line:last-child { border-bottom: none; }
.mat-ico { flex-shrink: 0; font-size: 36rpx; line-height: 1; }
.mat-name { flex: 1; min-width: 0; font-size: 30rpx; color: #0051FF; text-decoration: underline; text-underline-offset: 6rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.mat-name:active { opacity: 0.6; }
.mat-size { flex-shrink: 0; font-size: 26rpx; color: #999; font-weight: 400; }
/* 转圈圈：按钮内白色细环旋转（识别中显示，模拟进度数字在按钮文字里） */
.ai-fill-spin { width: 34rpx; height: 34rpx; border-radius: 50%; border: 5rpx solid rgba(255,255,255,0.45); border-top-color: #fff; box-sizing: border-box; animation: aiSpin 0.7s linear infinite; }
@keyframes aiSpin { to { transform: rotate(360deg); } }
/* 居委会见证（创建页，移自通知页）：白卡 + 标题/说明 + 适老化大复选框 */
/* 居委会见证：普通选项行（非卡片），标题比 section-title 小一号、无灰字注释 */
.juwei-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; cursor: pointer; padding: 10rpx 24rpx; margin-top: 40rpx; margin-bottom: 24rpx; }
.juwei-label { font-size: 32rpx; color: #1f2329; font-weight: 600; }
.juwei-check { width: 44rpx; height: 44rpx; flex-shrink: 0; accent-color: var(--c-primary-dark); }
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
.section-title { display: block; font-size: 36rpx; color: #1f2329; font-weight: 700; margin-bottom: 14rpx; line-height: 1.45; word-break: break-all; }
.section-title-row .section-title { margin-bottom: 0; }
.required-note { font-size: 28rpx; color: #E67E22; line-height: 1.35; white-space: nowrap; }

.form-row { display: flex; gap: 18rpx; align-items: flex-start; }
.form-group { margin-bottom: 20rpx; min-width: 0; }
.form-group:last-child { margin-bottom: 0; }
.form-group.half { flex: 1; min-width: 0; }
.form-label { display: block; font-size: 28rpx; color: #777; margin-bottom: 12rpx; line-height: 1.45; word-break: break-all; }
.form-input, .form-textarea { width: 100%; box-sizing: border-box; background: #fff; border-radius: 14rpx; font-size: 32rpx; color: #1f2329; border: 2rpx solid #eeeeee; }
.form-input { height: 88rpx; min-height: 88rpx; line-height: normal; padding: 0 20rpx; }
.form-input.large { height: 112rpx; min-height: 112rpx; line-height: normal; font-size: 40rpx; font-weight: 600; }
.form-input::placeholder, .form-textarea::placeholder { color: #666; font-size: 30rpx; line-height: 1.5; }
/* 会议标题输入框：占位用更淡的灰 + 常规字重（"请输入会议名称"作浅提示） */
.form-input.large::placeholder { color: #9a9a9a; font-weight: 400; }
.picker-field { width: 100%; min-height: 88rpx; box-sizing: border-box; background: #fff; border: 2rpx solid #eeeeee; border-radius: 14rpx; padding: 0 20rpx; font-size: 32rpx; color: #1f2329; line-height: normal; word-break: break-all; display: flex; align-items: center; }
.form-textarea { min-height: 200rpx; height: 200rpx; line-height: 1.5; padding: 18rpx 20rpx; }

.sheet-actions { display: flex; gap: 18rpx; justify-content: space-between; padding-top: 12rpx; }
.sheet-actions.fixed { padding: 20rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 2rpx solid #f2f2f2; flex-shrink: 0; }
.btn, .btn-ghost, .btn-primary { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; border-radius: 44rpx; text-align: center; font-size: 32rpx; font-weight: 600; box-sizing: border-box; white-space: nowrap; padding: 0 24rpx; margin: 0; border: 0; display: flex; align-items: center; justify-content: center; }
.btn-ghost { color: #777; background: #f5f5f5; }
/* 主按钮统一深橙（与顶栏同色），按下更深 */
.btn-primary { color: #fff; background: var(--c-primary-dark); }
.btn-primary:active { background: var(--c-primary-strong); }

/* 议题构建 */
.topic-empty { text-align: center; padding: 28rpx 0; font-size: 28rpx; color: #666; line-height: 1.6; }
.topic-line { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 18rpx 4rpx; }
.topic-line-text { flex: 1; min-width: 0; font-size: 30rpx; color: #1f2329; line-height: 1.45; word-break: break-all; }
.topic-line-del { flex-shrink: 0; font-size: 42rpx; color: #888; padding: 0 10rpx; line-height: 1; }
.topic-input-row { margin-top: 6rpx; }
.topic-input { flex: 1; min-width: 0; }
.topic-confirm-btn { flex-shrink: 0; height: 88rpx; padding: 0 28rpx; border: none; border-radius: 16rpx; background: var(--c-primary-dark); color: #fff; font-size: 30rpx; font-weight: 600; }
.topic-confirm-btn:active { background: var(--c-primary-strong); }
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
.title-input-wrap textarea.title-ta { height: auto; min-height: 104rpx; max-height: 168rpx; line-height: 1.45; padding: 22rpx 78rpx 22rpx 20rpx; resize: none; overflow-y: auto; box-sizing: border-box; display: block; }
.title-ta::-webkit-scrollbar { width: 6rpx; }
.title-ta::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.12); border-radius: 3rpx; }
.title-ta::-webkit-scrollbar-track { background: transparent; }
.title-clear { position: absolute; right: 12rpx; top: 50%; transform: translateY(-50%); font-size: 44rpx; color: #999; line-height: 1; padding: 0 8rpx; cursor: pointer; }
/* 文本框空时：叉淡化 */
.title-clear.dim { opacity: 0.25; }
/* 推荐标题：半透明覆盖在文本框内，点文字自动填入 */
.title-ghost { position: absolute; left: 20rpx; top: 22rpx; right: 78rpx; font-size: 40rpx; font-weight: 600; color: rgba(31, 32, 36, 0.32); line-height: 1.45; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
/* 语音输入按钮：文本框右下方 */
.title-voice-row { display: flex; justify-content: flex-end; margin-top: 14rpx; }
.voice-input-btn { display: inline-flex; align-items: center; gap: 6rpx; border: 2rpx solid #FFD79A; background: #FFF6E6; color: #C76A00; font-size: 28rpx; font-weight: 600; padding: 10rpx 24rpx; border-radius: 999rpx; line-height: 1.3; }
.voice-input-btn.on { background: #FFA800; border-color: #FFA800; color: #fff; animation: vi-pulse 1.2s ease-in-out infinite; }

/* 会议地点下拉 */
.loc-select { width: 100%; margin-top: 16rpx; appearance: none; -webkit-appearance: none; padding-right: 60rpx; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='22' height='22' viewBox='0 0 20 20'%3E%3Cpath fill='%23999' d='M5 7l5 5 5-5z'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 20rpx center; }
.loc-row { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; }
.loc-row .loc-select { flex: 1; min-width: 0; width: auto; margin-top: 0; }
.loc-map-btn { flex-shrink: 0; width: 96rpx; align-self: stretch; display: flex; align-items: center; justify-content: center; background: #E8F0FE; border: 2rpx solid #1A73E8; border-radius: 16rpx; box-sizing: border-box; }
.loc-map-btn svg { width: 46rpx; height: 46rpx; display: block; }
.loc-map-btn:active { background: #D6E4FC; }
.loc-row .loc-input { flex: 1; min-width: 0; width: auto; }
.loc-back { display: inline-block; margin-top: 14rpx; font-size: 28rpx; color: var(--c-primary-dark); }
.loc-other { margin-top: 16rpx; }

/* 日期/时间选择字段 */
.time-field, .date-field { justify-content: space-between; }
.tf-text { font-size: 32rpx; color: #1f2329; }
.tf-arrow { color: #999; font-size: 28rpx; }

/* 列选择器弹窗（日期/时间共用） */
.picker-pop-mask { position: fixed; inset: 0; z-index: 210; background: rgba(0,0,0,0.42); display: flex; align-items: center; justify-content: center; padding: 48rpx; box-sizing: border-box; }
/* 小日历（月历网格） */
.cal-pop { width: 86%; max-width: 620rpx; background: #fff; border-radius: 24rpx; padding: 28rpx 24rpx calc(20rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.cal-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.cal-nav { width: 76rpx; height: 76rpx; display: flex; align-items: center; justify-content: center; font-size: 52rpx; color: var(--c-primary-dark); font-weight: 700; }
.cal-nav:active { opacity: 0.5; }
.cal-week { display: grid; grid-template-columns: repeat(7, 1fr); margin-bottom: 8rpx; }
.cal-wd { text-align: center; font-size: 26rpx; color: #999; padding: 8rpx 0; }
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6rpx; }
.cal-cell { height: 78rpx; display: flex; align-items: center; justify-content: center; font-size: 32rpx; color: #1f2329; border-radius: 12rpx; }
.cal-cell.empty { visibility: hidden; }
.cal-cell.today { color: var(--c-primary-dark); font-weight: 700; }
.cal-cell.on { background: var(--c-primary-dark); color: #fff; font-weight: 700; }
.cal-cell:not(.empty):not(.on):active { background: var(--c-primary-soft); }
.picker-pop { width: 100%; max-width: 660rpx; background: #fff; border-radius: 26rpx; padding: 28rpx 26rpx 24rpx; box-sizing: border-box; }
/* 时间：大按钮点选网格（免滚动） */
.tg-cur { text-align: center; font-size: 64rpx; font-weight: 700; color: var(--c-primary-dark); letter-spacing: 2rpx; margin-bottom: 18rpx; }
.tg-label { font-size: 28rpx; color: #999; margin: 8rpx 2rpx 12rpx; }
.tg-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14rpx; }
.tg-grid-m { margin-bottom: 4rpx; }
.tg-cell { height: 88rpx; display: flex; align-items: center; justify-content: center; font-size: 36rpx; color: #1f2329; background: #f5f6f8; border-radius: 14rpx; }
.tg-cell.on { background: var(--c-primary-dark); color: #fff; font-weight: 700; }
.tg-cell:not(.on):active { background: var(--c-primary-soft); }
.pp-head { text-align: center; font-size: 34rpx; font-weight: 700; color: #1f2329; margin-bottom: 20rpx; }
.pop-close { display: flex; justify-content: flex-end; margin-bottom: 4rpx; }
.pp-cols { display: flex; gap: 16rpx; }
.pp-col { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.pp-col-label { text-align: center; font-size: 28rpx; color: #666; margin-bottom: 10rpx; }
.pp-col-scroll { height: 460rpx; overflow-y: auto; background: #f7f8fa; border-radius: 16rpx; padding: 8rpx; box-sizing: border-box; -webkit-overflow-scrolling: touch; }
.pp-item { display: flex; align-items: center; justify-content: center; height: 76rpx; font-size: 34rpx; color: #333; border-radius: 12rpx; margin: 4rpx 0; }
.pp-item.on { color: #fff; background: #FFA800; font-weight: 700; }
.pp-actions { display: flex; gap: 18rpx; margin-top: 24rpx; }


/* 议题摘要行 */
.topic-summary { background: #fff; border: 2rpx solid #eee; border-radius: 18rpx; padding: 22rpx 24rpx; margin-top: 16rpx; }
.ts-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-bottom: 14rpx; }
.ts-num { font-size: 26rpx; color: #8a9099; font-weight: 600; }
.ts-title { font-size: 32rpx; color: #1f2329; line-height: 1.45; word-break: break-all; }
.ts-badge { font-size: 25rpx; padding: 6rpx 18rpx; border-radius: 20rpx; white-space: nowrap; line-height: 1.5; }
.ts-badge.badge-notice { color: #0C447C; background: #E6F1FB; }
.ts-badge.badge-discussion { color: #085041; background: #E1F5EE; }
.ts-badge.badge-decision { color: #3C3489; background: #EEEDFE; }
.ts-actions { display: flex; justify-content: flex-end; gap: 16rpx; margin-top: 18rpx; padding-top: 16rpx; border-top: 2rpx solid #f0f0f0; }
.ts-edit-btn { font-size: 27rpx; color: #C77800; background: #fff; border: 2rpx solid #F0A020; padding: 10rpx 30rpx; border-radius: 22rpx; white-space: nowrap; line-height: 1.5; }
.ts-edit-btn:active { background: #FFF6E5; }
.ts-del-btn { font-size: 27rpx; color: #C0392B; background: #fff; border: 2rpx solid #E6B0AA; padding: 10rpx 30rpx; border-radius: 22rpx; white-space: nowrap; line-height: 1.5; }
.ts-del-btn:active { background: #FDF2F0; }

/* 议题编辑弹窗 */
.topic-dialog-mask { position: fixed; inset: 0; z-index: 210; background: rgba(0,0,0,0.42); display: flex; align-items: flex-end; }
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
.topic-add-label { display: block; font-size: 28rpx; color: #8a9099; font-weight: 600; margin-top: 20rpx; margin-bottom: 8rpx; }
.topic-add-row { display: flex; gap: 20rpx; margin-top: 8rpx; }
.topic-add-btn { flex: 1; height: 80rpx; font-size: 30rpx; border-radius: 16rpx; }

/* 流式语音输入弹窗 */
.voice-modal-mask { position: fixed; inset: 0; z-index: 300; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; }
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
