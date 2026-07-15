<template>
  <div class="home">
    <!-- 顶栏：标题 -->
    <div class="hd">
      <div class="hd-left">
        <span class="hd-title">业委会</span>
        <span class="hd-sub">{{ activeRole.realName }} · {{ activeRole.role }}</span>
      </div>
    </div>

    <template v-if="isChair">
      <!-- 业委会综合评分（占位分数，计算规则待定后接后端） -->
      <div class="score-line">
        <span class="score-ico">🏅</span>
        <span>业委会综合评分</span>
        <span class="score-num" :style="{ backgroundImage: scoreGradient }">{{ score }}</span>
        <span class="score-unit">分</span>
      </div>
    </template>

    <!-- 当前会议卡片（进行中/准备中；主任另含已结束未公示）：置顶最优先，点继续进入流程 -->
    <template v-if="currents && currents.length > 0">
      <div v-for="cur in currents" :key="cur.id" class="meet-card">
        <div class="meet-head">
          <span class="meet-title">{{ cur.title }}</span>
          <span class="meet-status" :class="cur.stage">{{ cur.stageText }}</span>
        </div>
        <div class="meet-info">
          <span class="meet-info-item">{{ cur.timeText }}</span>
          <span class="meet-info-sep"></span>
          <span class="meet-info-item location">{{ cur.locationText }}</span>
        </div>

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
          <div class="big-btn-inner">
            <span v-if="cur.ctaIcon" class="big-btn-ico">{{ cur.ctaIcon }}</span>
            <span class="big-btn-text">{{ cur.ctaLabel }}</span>
          </div>
        </div>

        <div v-if="isChair" class="meet-del" @click="removeCurrent(cur)">删除会议</div>
      </div>
    </template>

    <!-- 履职年历（全员可见）+ 待办：分类横栏 开会（默认）/培训/接待。始终显示；
         有进行中/准备中会议时，年历（总览）收到底部、点开才展开，待办里不再重复列这场会议。
         类名 yc- 前缀（cal- 被小日历占用）。 -->
    <div class="plan-stack" :class="{ compact: planTab !== 'meeting', 'has-meeting': currents && currents.length }">
        <!-- 分类横栏：放在年份上面；开会为主（默认），培训/接待切换后日历+清单整体切到该类 -->
        <div class="plan-switch-card">
          <div class="plan-tabs">
            <div class="plan-tab" :class="{ active: planTab === 'meeting' }" @click="planTab = 'meeting'">开会</div>
            <div class="plan-tab" :class="{ active: planTab === 'reception' }" @click="planTab = 'reception'">接待</div>
            <div class="plan-tab" :class="{ active: planTab === 'learning' }" @click="planTab = 'learning'">培训</div>
          </div>
        </div>

        <!-- 有会议时：履职年历折叠头（点开才展开）；无会议时不显示、年历常驻在下方 -->
        <div v-if="currents && currents.length" class="cal-fold" @click="calFold = !calFold">
          <span class="cal-fold-title">📅 履职年历</span>
          <span class="cal-fold-act">{{ calFold ? '展开查看' : '收起' }}<span class="cal-fold-arw" :class="{ up: !calFold }">⌄</span></span>
        </div>

      <div class="plan-calendar-card" v-show="!(currents && currents.length) || !calFold">
        <div class="plan-head">
          <div class="plan-title-wrap">
            <span class="plan-title" :class="{ 'ov-title': planTab !== 'meeting' }">{{ planTab === 'meeting' ? curYear + '年' : (planTab === 'reception' ? '接待概览' : '培训概览') }}</span>
          </div>
          <div class="plan-actions">
            <span v-if="planTab === 'learning'" class="plan-tip link" @click="goLearning()">查看全部 ›</span>
          </div>
        </div>

        <div v-if="planTab === 'meeting'" class="status-legend">
          <span><i class="lg-dot done"></i>已完成</span>
          <span><i class="lg-dot current"></i>待推进</span>
          <span><i class="lg-dot overdue"></i>已逾期</span>
          <span><i class="lg-dot upcoming"></i>待安排</span>
        </div>

        <!-- 月份日历：首页主视觉。按双月期成组，保留月份，同时让一期两个月有整体感。 -->
        <div v-if="planTab === 'meeting'" class="yc-period-grid">
          <div v-for="pair in monthPairs" :key="pair.period" class="yc-period-card" :class="[pair.status, { active: pair.months.some(mc => calMonth === mc.m) }]">
            <div class="yc-pair-months">
              <div v-for="mc in pair.months" :key="mc.m" class="yc-cell pair-cell" :class="[mc.status, { sel: calMonth === mc.m }]" @click="onYcMonthTap(pair, mc)">
                <span v-if="mc.todo" class="yc-corner">{{ mc.todo }}</span>
                <span class="yc-m">{{ mc.m }}月</span>
                <span class="yc-s">{{ mc.label }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 点选期次后的反馈区（日历下方）：已开期=该期开会记录；未到期=提前准备会议；逾期/待开点击时提示并定位待办卡 -->
        <div v-if="planTab === 'meeting' && selPeriodFeedback" class="yc-period-feedback">
          <template v-if="selPeriodFeedback.kind === 'records'">
            <div class="ypf-head">{{ selPeriodFeedback.title }}</div>
            <div v-if="!selPeriodFeedback.records.length" class="plan-empty">该期没有会议记录</div>
            <div v-else v-for="r in selPeriodFeedback.records" :key="r.key" class="yc-item" @click="r.onTap()">
              <div class="yc-item-info">
                <div class="yc-item-title">{{ r.title }}</div>
                <div v-if="r.sub" class="yc-item-sub">{{ r.sub }}</div>
              </div>
              <!-- 已开的会议：右侧「查看详情」小按钮进会议详情页；未结束的仍显示状态徽标 -->
              <span v-if="r.detailTap" class="plan-badge view ypf-view" @click.stop="r.detailTap()">查看详情</span>
              <span v-else class="plan-badge" :class="r.status">{{ r.badge }}</span>
            </div>
          </template>
          <template v-else-if="selPeriodFeedback.kind === 'upcoming'">
            <div class="ypf-tip">{{ selPeriodFeedback.tip }}</div>
            <button v-if="isChair" class="advance-meeting-btn" @click="advanceSelectedMeeting">提前准备会议</button>
          </template>
        </div>

        <!-- 接待/培训概览：三数字 metric（本月·待跟进·年度 / 已开展·待开·过期未开），警示数带色 -->
        <div v-if="planTab !== 'meeting'" class="ov-metrics">
          <div v-for="mm in planOverview" :key="mm.key" class="ov-metric" :class="[mm.tone, { on: ovFilter === mm.key }]" @click="ovFilter = mm.key">
            <span class="ov-num">{{ mm.num }}</span>
            <span class="ov-label">{{ mm.label }}</span>
          </div>
        </div>
      </div>

        <!-- 待办事项：开会类同时展示本期与逾期期次；其他分类展示当前月份待办 -->
        <div class="plan-todo-card yc-list" :class="{ flash: planTodoFlash }">
          <div class="yc-list-head">
            <span class="yc-head-title">{{ planListTitle }}</span>
            <span v-if="planTab !== 'meeting'" class="yc-list-count" :class="{ active: planTodoList.length }">{{ planTodoList.length ? planTodoList.length + '项' : '无待办' }}</span>
          </div>
          <div v-if="!planTodoList.length" class="plan-empty">暂无需要处理的{{ planTabLabel }}事项</div>
          <div v-else v-for="it in planTodoList" :key="it.key" class="yc-item" :class="[planTab === 'meeting' ? it.status : 'todo-plain', it.flag]" @click="it.onTap()">
            <div class="yc-item-info">
              <div class="yc-item-title">{{ it.title }}</div>
              <div v-if="it.sub" class="yc-item-sub">{{ it.sub }}</div>
            </div>
            <span class="plan-badge" :class="it.status">{{ it.badge }}</span>
          </div>
        </div>
      </div>

    <!-- 委员且无相关会议：空闲提示 -->
    <div v-if="!isChair && (!currents || !currents.length)" class="idle">
      <span class="idle-emoji">☕</span>
      <span class="idle-hint">暂时没有需要您处理的会议</span>
      <span class="idle-sub">有新会议时，会在这里提醒您</span>
    </div>

    <!-- 待发送草稿卡：发起会议填了一半返回，内容自动存草稿，放大成首页主角，突出「继续通知」 -->
    <div v-if="isChair && hasDraft" class="draft-card">
      <div class="draft-card-top">
        <span class="draft-badge">📝 待发送 · 草稿</span>
        <span class="draft-discard" @click.stop="discardDraft">放弃草稿</span>
      </div>
      <div class="draft-title">{{ draftTitle }}</div>
      <div v-if="draftSummary" class="draft-summary">{{ draftSummary }}</div>
      <div v-if="draftMaterialCount" class="draft-mat">📎 已附 {{ draftMaterialCount }} 份材料</div>
      <button class="draft-continue" @click="continueDraft">继续通知<span class="btn-arrow">›</span></button>
    </div>

    <!-- 「更多功能」三格已删：接待/培训入口收进顶部计划卡横栏；历史记录走计划卡已开期或资料库 -->

    <!-- 发起非例会会议：低频功能收在页面底部的低调入口（例会走年历的「去通知/去补开」） -->
    <div v-if="canCreate" class="create-misc-entry" @click="openNewMeeting()">＋ 发起其他会议</div>

    <div v-if="createVisible" class="modal-mask" @click="closeCreate">
      <div class="create-panel" @click.stop>
        <div class="create-head">
          <span class="create-back" @click="closeCreate">‹</span>
          <span class="create-title">发起业委会</span>
          <span class="create-nav-ph"></span>
        </div>

        <div class="create-body" style="overflow-y:auto;">
          <!-- 顶部分段切换：手动填写 / 拍照上传（两者平级，拍照入口更醒目） -->
          <div class="create-tabs">
            <div class="create-tab" :class="{ active: createTab === 'manual' }" @click="createTab = 'manual'">手动填写</div>
            <div class="create-tab" :class="{ active: createTab === 'scan' }" @click="createTab = 'scan'">拍照 / 上传</div>
          </div>

          <!-- 拍照/上传面板：拍通知照片或传文件，AI 识别后在当前面板展示结果，并把识别内容预填到下方表单 -->
          <div v-show="createTab === 'scan'" class="scan-pane">
            <div class="doc-scan-bar inline">
              <!-- 缩略图预览条：图片显缩略图，PDF/其他显图标；可逐个删除 -->
              <div v-if="scanItems.length" class="ds-preview">
                <div v-for="it in scanItems" :key="it.id" class="ds-thumb">
                  <img v-if="it.isImage && it.thumbUrl" class="ds-thumb-img" :src="it.thumbUrl" :alt="it.name" @click="openScanItemPreview(it)" />
                  <span v-else class="ds-thumb-file" @click="openScanItemPreview(it)"><span class="ds-thumb-ico">{{ scanThumbIcon(it.ext) }}</span><span class="ds-thumb-ext">{{ it.ext || '文件' }}</span></span>
                  <span v-if="it.isImage && it.thumbUrl" class="ds-thumb-zoom" @click.stop="openScanItemPreview(it)">⤢</span>
                  <span class="ds-thumb-del" @click.stop="removeScanItem(it.id)">×</span>
                </div>
              </div>
              <div class="ds-cards ds-cards-3">
          <button class="ds-card" :disabled="scanRecognizing" @click="startCamera()">
            <span class="ds-ico or">📷</span>
            <span class="ds-t">拍照</span>
            <span class="ds-s">纸质文件</span>
          </button>
                <button class="ds-card" :disabled="scanRecognizing" @click="startDocScan('image')">
                  <span class="ds-ico bl">🖼️</span>
                  <span class="ds-t">图片</span>
                </button>
                <button class="ds-card" :disabled="scanRecognizing" @click="startDocScan('file')">
                  <span class="ds-ico bl">📄</span>
                  <span class="ds-t">文件</span>
                  <span class="ds-s">PDF·Word</span>
                </button>
              </div>
              <button v-if="scanItems.length" class="ds-recognize" :disabled="scanRecognizing" @click="recognizeScanItems">
                {{ scanRecognizing ? '识别中 ' + docProgress + '%' : '开始识别（' + scanItems.length + '）' }}
              </button>
            </div>
          </div>

          <!-- 会议内容表单：两个 tab 都显示；拍照/上传识别后就地填入这里 -->
          <div class="manual-pane">
          <!-- 会议信息：会议名称 + 时间地点合并为一个信息卡片 -->
          <div class="create-section meeting-info-card">
            <div class="form-group">
              <span class="field-caption caption-as-title">会议名称 <span v-if="createTab === 'manual'" class="req-star">*</span></span>
              <div class="title-row">
                <div class="title-input-wrap">
                  <textarea ref="titleEl" class="form-input large title-ta" :class="{ 'field-error': fieldErrors.title }" rows="1" v-model="createForm.title" :placeholder="suggestedTitle ? '' : '请输入会议名称'" @input="autoGrowTitle" @focus="clearFieldError('title')" @keydown.enter.prevent></textarea>
                  <!-- 推荐标题：半透明显示在文本框内，点文字直接填入 -->
                  <span v-if="createTab === 'manual' && suggestedTitle && !createForm.title" class="title-ghost" @click="createForm.title = suggestedTitle; clearFieldError('title')">{{ suggestedTitle }}</span>
                  <span v-show="createTab === 'manual'" class="title-clear" :class="{ dim: !createForm.title && !suggestedTitle }" @click="clearTitleOrGhost">×</span>
                </div>
                <button v-show="createTab === 'manual'" class="voice-mic-btn" :class="{ on: voiceTarget === 'title' }" @click.stop="startStreamingVoice('title')" aria-label="语音输入">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#fff" d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3zm5-3a1 1 0 0 1 2 0 7 7 0 0 1-6 6.92V21a1 1 0 1 1-2 0v-3.08A7 7 0 0 1 5 11a1 1 0 1 1 2 0 5 5 0 0 0 10 0z"/></svg>
                </button>
              </div>
            </div>
          </div>

          <!-- 时间/地点：单独成卡；日期+时间合并为一行，地点保留常用地点选择并单独露出地图入口 -->
          <div class="create-section meeting-info-card">
            <div class="field-list">
              <div class="field-line field-line-split" :class="{ 'field-error': fieldErrors.meetingDate || fieldErrors.meetingTime }">
                <div class="fl-part" @click="openDatePicker">
                  <span class="fl-label">日期 <span v-if="createTab === 'manual'" class="req-star">*</span></span>
                  <span class="fl-value" :class="{ ph: !createForm.meetingDate }">{{ createForm.meetingDate ? fmtPlanDate(createForm.meetingDate) : '选择日期' }}</span>
                  <span class="fl-arrow">›</span>
                </div>
                <div class="fl-part fl-part-time" @click="openTimePicker">
                  <span class="fl-label">时间 <span v-if="createTab === 'manual'" class="req-star">*</span></span>
                  <span class="fl-value" :class="{ ph: !createForm.meetingTime }">{{ createForm.meetingTime || '选择时间' }}</span>
                  <span class="fl-arrow">›</span>
                </div>
              </div>
              <div class="field-line field-line-location" :class="{ 'field-error': fieldErrors.location }">
                <!-- 选「其他地点」时：本行直接变输入框（不再另弹文本框）；点「地点」标签可回到常用地点选择 -->
                <template v-if="locationPreset === '__other__'">
                  <span class="fl-label fl-label-tap" @click="openLocPicker">地点 <span v-if="createTab === 'manual'" class="req-star">*</span></span>
                  <input class="fl-inline-input" v-model="createForm.location" placeholder="请输入会议地点" @focus="clearFieldError('location')" />
                </template>
                <div v-else class="fl-loc-main" @click="openLocPicker">
                  <span class="fl-label">地点 <span v-if="createTab === 'manual'" class="req-star">*</span></span>
                  <span class="fl-value" :class="{ ph: !createForm.location }">{{ createForm.location || '选择地点' }}</span>
                  <span class="fl-arrow">›</span>
                </div>
                <button class="loc-map-btn field-map-btn" @click.stop="pickLocationOnMap" aria-label="从地图选点">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#1A73E8" d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7zm0 9.5A2.5 2.5 0 1 1 12 6a2.5 2.5 0 0 1 0 5.5z"/></svg>
                </button>
              </div>
            </div>
          </div>

          <!-- 会议议程项（弹窗逐条添加） -->
          <div class="create-section">
            <div class="section-title-row topic-head">
              <span class="section-title">会议议题 <span v-if="createTab === 'manual'" class="req-star">*</span></span>
            </div>
            <div v-if="createForm.topics.length" class="topic-list">
              <div v-for="(topic, idx) in createForm.topics" :key="idx" class="topic-line">
                <span class="topic-line-text"><b>{{ idx + 1 }}.</b> {{ topic.title }}</span>
                <span class="ts-badge topic-line-badge" :class="topicTypeClass(topic)">{{ topicTypeLabel(topic) }}</span>
                <span class="topic-line-del" @click="removeCreateTopic(idx)">×</span>
              </div>
            </div>
            <div v-show="createTab === 'manual'" class="topic-kind-picker" aria-label="选择议题类型">
              <span class="topic-kind-label">本条类型</span>
              <button type="button" class="topic-kind-option notice" :class="{ on: topicInputType === 'notice' }" @click="topicInputType = 'notice'">通知</button>
              <button type="button" class="topic-kind-option discussion" :class="{ on: topicInputType === 'discussion' }" @click="topicInputType = 'discussion'">讨论</button>
              <button type="button" class="topic-kind-option decision" :class="{ on: topicInputType === 'decision' }" @click="topicInputType = 'decision'">表决</button>
            </div>
            <div v-if="createTab === 'manual' && topicInputType === 'decision'" class="topic-vote-settings">
              <div class="topic-kind-picker vote-method" aria-label="选择表决方式">
                <span class="topic-kind-label">表决方式</span>
                <button type="button" class="topic-kind-option decision" :class="{ on: topicInputDecisionType === 'simple' }" @click="pickTopicInputDecision('simple')">是 / 否</button>
                <button type="button" class="topic-kind-option decision" :class="{ on: topicInputDecisionType === 'multi_choice' }" @click="pickTopicInputDecision('multi_choice')">多选一</button>
              </div>
              <div v-if="topicInputDecisionType === 'multi_choice'" class="topic-quick-options">
                <div v-for="(opt, oi) in topicInputOptions" :key="opt.id" class="topic-quick-option-row">
                  <span class="topic-quick-option-num">{{ oi + 1 }}</span>
                  <input class="form-input topic-quick-option-input" v-model="opt.label" :placeholder="'选项 ' + (oi + 1)" />
                  <button v-if="topicInputOptions.length > 2" type="button" class="topic-quick-option-del" @click="removeTopicInputOption(oi)">×</button>
                </div>
                <button type="button" class="topic-quick-option-add" @click="addTopicInputOption">＋ 添加选项</button>
              </div>
            </div>
            <div v-show="createTab === 'manual'" class="vi-row topic-input-row">
              <input class="form-input topic-input" :class="{ 'field-error': fieldErrors.topics }" v-model="topicInput" placeholder="输入一条议题" @focus="clearFieldError('topics')" @keyup.enter="addTopicFromInput" />
              <div class="topic-actions-col">
                <button v-show="createTab === 'manual'" class="voice-mic-btn" :class="{ on: voiceTarget === 'topic' }" @click.stop="startStreamingVoice('topic')" aria-label="语音输入">
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#fff" d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3zm5-3a1 1 0 0 1 2 0 7 7 0 0 1-6 6.92V21a1 1 0 1 1-2 0v-3.08A7 7 0 0 1 5 11a1 1 0 1 1 2 0 5 5 0 0 0 10 0z"/></svg>
                </button>
                <button class="topic-confirm-btn" @click="addTopicFromInput">确定</button>
              </div>
            </div>
          </div>

          <!-- 居委会见证（说明式开关卡片，精简为一行）：初始就显示，仅标记 hasMajorIssue，不自动通知 -->
          <div class="juwei-card" @click="createForm.juweiWitness = !createForm.juweiWitness">
            <div class="juwei-title">含重大事项，需居委会到场见证</div>
            <span class="juwei-switch" :class="{ on: createForm.juweiWitness }" role="switch" :aria-checked="createForm.juweiWitness"></span>
          </div>

          <!-- 会议材料：拍照/上传识别出材料后才在底部出现（建会后自动挂到会议供委员传阅） -->
          <div v-if="pendingMaterials.length" class="create-section">
            <div class="section-title-row mat-head" @click="materialsOpen = !materialsOpen">
              <span class="section-title">会议材料（{{ pendingMaterials.length }}）</span>
              <span class="mat-toggle" :class="{ open: materialsOpen }" aria-label="展开查看材料详情">›</span>
            </div>
            <template v-if="materialsOpen">
              <div v-for="(m, idx) in pendingMaterials" :key="m.url" class="mat-card" @click="openMaterialViewer(m)">
                <span class="mat-badge" :class="'t-' + matBadge(m).cls">{{ matBadge(m).label }}</span>
                <div class="mat-info">
                  <span class="mat-fname">{{ m.fileName }}</span>
                  <span class="mat-fsize" v-if="m.sizeText">{{ m.sizeText }}</span>
                </div>
                <span class="mat-del" @click.stop="removePendingMaterial(idx)">×</span>
              </div>
            </template>
          </div>
          </div>

        </div>

        <div class="sheet-actions fixed">
          <button class="btn btn-ghost" @click="closeCreate">取消</button>
          <button class="btn btn-primary" @click="submitNewMeeting">生成通知<span class="btn-arrow">›</span></button>
        </div>
      </div>
    </div>

    <!-- 识别中：居中小弹窗（文档扫描动画 + 三步流程 + 动态文案；不做整页进度页） -->
    <div v-if="scanBusy" class="scan-pop-mask">
      <div class="scan-pop">
        <span class="sp-close" @click="cancelRecognize" aria-label="取消识别">×</span>
        <!-- 圆环进度：单一焦点，中心大百分比（替代原扫描图标+三步条+横进度条） -->
        <div class="sp-ring">
          <svg class="sp-ring-svg" viewBox="0 0 100 100">
            <defs>
              <linearGradient id="spGrad" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0%" stop-color="#FFB733" />
                <stop offset="100%" stop-color="#C76A00" />
              </linearGradient>
            </defs>
            <circle class="sp-ring-track" cx="50" cy="50" r="42" />
            <circle class="sp-ring-fill" cx="50" cy="50" r="42" :style="{ strokeDashoffset: 264 * (1 - docProgress / 100) }" />
          </svg>
          <div class="sp-ring-center"><div class="sp-ring-val"><span class="sp-ring-num">{{ docProgress }}</span><span class="sp-ring-pct">%</span></div></div>
        </div>
        <span class="sp-title">AI 智能识别中</span>
        <span class="sp-say">{{ scanSay }}</span>
        <span class="sp-foot">豆包大模型 · 已用时 {{ scanSec }}s</span>
      </div>
    </div>

    <!-- AI 识别完成：定制结果卡（识别为通知/材料、已识别/待补填字段、耗时+token） -->
    <div v-if="scanResultCard" class="scan-result-mask" @click.self="closeScanResult">
      <div class="scan-result">
        <div class="sr-hero">
          <div class="sr-hero-badge"><span class="sr-check">✓</span></div>
          <div class="sr-hero-title">识别完成</div>
        </div>

        <div class="sr-body">
          <!-- 多份不同的会议通知：列出每份，让用户选正确的一份填入（或手动填写） -->
          <div v-if="scanResultCard.mode === 'multi-notice'" class="sr-card sr-multi">
            <div class="sr-row-top"><span class="sr-pill warn">多份通知</span><span class="sr-row-note">请选正确的一份</span></div>
            <div v-for="(opt, i) in scanResultCard.noticeOptions" :key="i" class="sr-notice-opt" :class="{ sel: scanResultCard.selectedNotice === i }" @click="scanResultCard.selectedNotice = i">
              <span class="sr-radio" :class="{ on: scanResultCard.selectedNotice === i }"></span>
              <div class="sr-notice-body">
                <div class="sr-notice-title">{{ opt.title || '（未填会议名称）' }}</div>
                <div class="sr-notice-meta">{{ [formatScanDateTime(opt.meetingDate, opt.meetingTime), opt.location].filter(Boolean).join(' · ') || '时间地点未填' }}</div>
              </div>
            </div>
          </div>

          <!-- 单份通知：直接展示识别字段（单份不提示类别，多份/仅材料才提示） -->
          <template v-if="scanResultCard.mode === 'notice'">
            <div class="sr-preview">
              <div v-for="(row, i) in scanNoticeRows" :key="i" class="sr-pv-row">
                <span class="sr-pv-label">{{ row.label }}</span>
                <div class="sr-pv-val-wrap">
                  <span class="sr-pv-value" :class="{ miss: row.miss }">{{ row.miss ? '未识别 · 待手填' : row.value }}</span>
                </div>
              </div>
              <!-- 材料随通知识别出时，作为同款字段行并入列表 -->
              <div v-if="scanResultCard.materialCount" class="sr-pv-row">
                <span class="sr-pv-label">会议材料</span>
                <div class="sr-pv-val-wrap">
                  <span class="sr-pv-value">{{ scanResultCard.materialCount }} 份</span>
                </div>
              </div>
            </div>
            <div v-if="scanResultCard.conflictNote" class="sr-alert">{{ scanResultCard.conflictNote }}</div>
          </template>

          <!-- 仅材料：单独成卡，并提示会议信息手填 -->
          <template v-if="scanResultCard.mode === 'material'">
            <div class="sr-cat material"><span class="sr-cat-ico">📎</span>这是会议材料</div>
            <div class="sr-card">
              <div class="sr-card-main">
                <span class="sr-ico-chip material">📎</span>
                <div class="sr-card-text">
                  <div class="sr-card-title">共 {{ scanResultCard.materialCount }} 份材料</div>
                  <div class="sr-card-sub">将随会议一起，供委员在线传阅</div>
                </div>
              </div>
            </div>
            <div class="sr-tip">会议信息请手动填写</div>
          </template>

          <!-- 多份通知：材料压成一行说清（单份通知已并入上方字段列表） -->
          <div v-if="scanResultCard.mode === 'multi-notice' && scanResultCard.materialCount" class="sr-mat-line">
            <span class="sr-mat-ico">📎</span>另有 {{ scanResultCard.materialCount }} 份材料，一并加入传阅
          </div>

          <!-- 信息冲突：材料仍会添加，仅询问是否覆盖冲突字段 -->
          <div v-if="scanResultCard.conflicts && scanResultCard.conflicts.length" class="sr-card sr-conflict">
            <div class="sr-row-top"><span class="sr-pill warn">信息冲突</span><span class="sr-row-note">与你已填的不一致，是否覆盖？</span></div>
            <div v-for="(cf, i) in scanResultCard.conflicts" :key="i" class="sr-conf-line">
              <span class="sr-conf-label">{{ cf.label }}</span>
              <span class="sr-conf-old">{{ cf.cur }}</span>
              <span class="sr-conf-arrow">→</span>
              <span class="sr-conf-new">{{ cf.nv }}</span>
            </div>
          </div>
        </div>

        <div class="sr-meta">耗时 {{ scanResultCard.seconds }}s<template v-if="scanResultCard.tokens > 0"> · 消耗 {{ scanResultCard.tokens.toLocaleString() }} token</template></div>

        <div class="sr-actions">
          <button class="sr-btn ghost" @click="onScanGhost()">{{ scanResultCard.ghostLabel }}</button>
          <button class="sr-btn primary" @click="confirmScanResult(scanResultCard.needOverwriteAsk)">{{ scanResultCard.primaryLabel }}</button>
        </div>
      </div>
    </div>

    <!-- 模拟手机相机（测试用）：取景框对着纸质通知；拍后先进照片预览（重拍/使用照片），确认后才走识别；接真机后整块可删 -->
    <div v-if="mockCameraVisible" class="mock-cam" :class="{ preview: !!mockShotUrl }">
      <div class="mc-top">
        <span class="mc-badge">{{ mockShotUrl ? '照片预览' : '模拟相机 · 测试' }}</span>
        <span v-if="scanItems.length && !mockShotUrl" class="mc-count">已拍 {{ scanItems.length }} 张</span>
        <span class="mc-close" @click="closeMockCamera">×</span>
      </div>
      <!-- 取景模式 -->
      <template v-if="!mockShotUrl">
        <div class="mc-viewport" @touchstart.passive="onVfTouchStart" @touchend.passive="onVfTouchEnd">
          <img class="mc-paper" :src="mockViewfinderUrl" alt="取景中的纸质文件" />
          <span class="mc-corner tl"></span><span class="mc-corner tr"></span>
          <span class="mc-corner bl"></span><span class="mc-corner br"></span>
          <!-- 左右切换样张（模拟不同纸质文件） -->
          <button class="mc-nav prev" @click.stop="prevSample" aria-label="上一张">‹</button>
          <button class="mc-nav next" @click.stop="nextSample" aria-label="下一张">›</button>
          <span class="mc-sample-ind">{{ mockSampleIdx + 1 }} / {{ MOCK_SAMPLES.length }} · {{ currentSampleLabel }}</span>
          <span class="mc-tip">左右滑动切换文件 · 对准取景框</span>
        </div>
        <div class="mc-bottom">
          <button v-if="lastShotThumb" class="mc-roll" @click="closeMockCamera" aria-label="查看已拍照片">
            <img class="mc-roll-img" :src="lastShotThumb" alt="上一张" />
            <span class="mc-roll-badge">{{ scanItems.length }}</span>
          </button>
          <button class="mc-shutter" @click="mockShoot" aria-label="拍照"><span class="mc-shutter-core"></span></button>
          <button v-if="scanItems.length" class="mc-done" @click="closeMockCamera">完成（{{ scanItems.length }}）</button>
        </div>
      </template>
      <!-- 拍后预览：像系统相机一样先看照片，确认后再加入 -->
      <template v-else>
        <div class="mc-viewport">
          <img class="mc-paper shot" :src="mockShotUrl" alt="刚拍的照片" />
        </div>
        <div class="mc-confirm-tip">拍清楚了吗？点「确定」加入，可继续拍下一张</div>
        <div class="mc-bottom confirm">
          <button class="mc-btn retake" @click="mockRetake">重拍</button>
          <button class="mc-btn use" @click="mockUsePhoto">确定</button>
        </div>
      </template>
      <div class="mc-flash" :class="{ on: mockCamFlash }"></div>
    </div>

    <!-- 真·相机（getUserMedia 实时取景）：拍照按钮先申请权限，授权后进这里；桌面用网络摄像头、手机自动后置摄像头 -->
    <div v-if="realCamVisible" class="mock-cam real-cam" :class="{ preview: !!realShotUrl }">
      <div class="mc-top">
        <span class="mc-badge">{{ realShotUrl ? '照片预览' : '相机 · 实时取景' }}</span>
        <span v-if="scanItems.length && !realShotUrl" class="mc-count">已拍 {{ scanItems.length }} 张</span>
        <span class="mc-close" @click="closeRealCamera">×</span>
      </div>
      <!-- 取景模式：实时视频流 -->
      <template v-if="!realShotUrl">
        <div class="mc-viewport">
          <video ref="realVideoEl" class="mc-video" autoplay muted playsinline webkit-playsinline></video>
          <span class="mc-corner tl"></span><span class="mc-corner tr"></span>
          <span class="mc-corner bl"></span><span class="mc-corner br"></span>
          <span class="mc-tip">对准纸质文件 · 点圆钮拍摄</span>
        </div>
        <div class="mc-bottom">
          <button v-if="lastShotThumb" class="mc-roll" @click="closeRealCamera" aria-label="查看已拍照片">
            <img class="mc-roll-img" :src="lastShotThumb" alt="上一张" />
            <span class="mc-roll-badge">{{ scanItems.length }}</span>
          </button>
          <button class="mc-shutter" @click="realShoot" aria-label="拍照"><span class="mc-shutter-core"></span></button>
          <button v-if="scanItems.length" class="mc-done" @click="closeRealCamera">完成（{{ scanItems.length }}）</button>
        </div>
      </template>
      <!-- 拍后预览：确认后加入暂存，可继续连拍 -->
      <template v-else>
        <div class="mc-viewport">
          <img class="mc-paper shot" :src="realShotUrl" alt="刚拍的照片" />
        </div>
        <div class="mc-confirm-tip">拍清楚了吗？点「确定」加入，可继续拍下一张</div>
        <div class="mc-bottom confirm">
          <button class="mc-btn retake" @click="realRetake">重拍</button>
          <button class="mc-btn use" @click="realUsePhoto">确定</button>
        </div>
      </template>
      <div class="mc-flash" :class="{ on: realCamFlash }"></div>
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
        <div class="pp-head">会议时间</div>
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
          <button class="btn btn-primary" @click="timePickerOpen = false">确认</button>
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
          <div class="form-group" v-if="topicDraft.type === 'notice'">
            <span class="form-label">通知正文</span>
            <textarea class="form-input" v-model="topicDraft.content" placeholder="填写要通报给委员的内容（点开议题时展示）" style="height:auto;min-height:160rpx;line-height:1.6;resize:none;padding:16rpx 20rpx;"></textarea>
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

    <!-- 待办「查看」详情弹窗：接待/培训点「查看」当场看该条详情，底部按钮再进对应页面跟进 -->
    <div v-if="todoDetail" class="td-mask" @click.self="todoDetail = null">
      <div class="td-pop" @click.stop>
        <div class="td-pop-head">
          <span class="td-pop-title">{{ todoDetail.title }}</span>
          <span class="td-pop-close" @click="todoDetail = null">×</span>
        </div>
        <span class="td-pop-status" :class="todoDetail.statusClass">{{ todoDetail.statusText }}</span>
        <div class="td-pop-body">
          <div v-for="row in todoDetail.rows" :key="row.k" class="td-row">
            <span class="td-row-k">{{ row.k }}</span>
            <span class="td-row-v">{{ row.v }}</span>
          </div>
        </div>
        <button class="td-pop-btn" @click="todoDetail.onGo()">{{ todoDetail.goLabel }} ›</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, watch } from 'vue'
import { onMounted, onActivated, onUnmounted } from 'vue'
import api from '@/api'
import { meetingRecordingSession, discardMeetingRecording } from '@/composables/meetingRecordingSession'
import perm from '@/utils/perm'
import { showModal, showActionSheet, toast } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import PageNav from '@/components/PageNav.vue'
import { parseMeetingText } from '@/utils/meeting-parser'
import { pickFiles, humanSize } from '@/utils/upload'
import { isWecom, chooseWecomImages, isWecomCancel } from '@/utils/wecom'
import { applyHotwords } from '@/utils/helpers'
import { openMaterialViewer } from '@/composables/materialViewer'
import { aiTask } from '@/composables/aiTask'

const isChair = ref(false)
const isRecorder = ref(false)
const isExternal = ref(false)
// 合并后的主页：顶栏 + 当前会议卡 所需状态
const activeRole = ref({})
const canCreate = ref(false)
const canViewInternal = ref(false)
const unread = ref(0)
const currents = ref([])            // 进行中/准备中的会议卡片
const calFold = ref(false)          // 履职年历默认展开（0714 用户定）；折叠头仍可手动收起
const STEP_BY_STAGE = { preparing: 1, ongoing: 2, ended: 3 }
const STEP_LABELS = ['', '准备开会', '正式开会', '会后总结']
const MEETING_STAGE_TEXT = { preparing: '未开始', ongoing: '进行中', ended: '已结束' }
// —— 首页指标（占位数字，计算规则待定后再接后端，届时替换这三个值即可）——
const score = ref(92)        // 业委会综合评分
// 评分按高低走渐变(亮→深，红绿灯阶梯，background-clip:text)：≥90祖母绿 / 80-89草绿 / 70-79黄绿 / 60-69琥珀 / <60朱红
const scoreGradient = computed(() => {
  const s = score.value
  if (s >= 90) return 'linear-gradient(135deg, #17A673 0%, #0B6E43 100%)'
  if (s >= 80) return 'linear-gradient(135deg, #5BB85C 0%, #2E7D32 100%)'
  if (s >= 70) return 'linear-gradient(135deg, #9BC53D 0%, #5E8A1A 100%)'
  if (s >= 60) return 'linear-gradient(135deg, #EDB731 0%, #B87908 100%)'
  return 'linear-gradient(135deg, #E8553D 0%, #B02A1E 100%)'
})
const curMonth = new Date().getMonth() + 1
const curYear = new Date().getFullYear()
const curPeriod = Math.ceil(curMonth / 2)   // 双月一期：1-2/3-4/5-6/7-8…；7月→第4期(7-8月)
const allMeetings = ref([])                 // 全部业委会会议（loadAll 填充），按期真实统计
// 例会规则：每个双月期应召开 1 次（下面 yearPlan 每期一行即体现；接后端可调规则）
function periodLabel(p) { return ((p - 1) * 2 + 1) + '-' + (p * 2) + '月' }
// 首页履职待办：会议只要已结束，就不再作为“待开/逾期”催办项展示。
// compliance 是否有效留给详情/公示判断；首页待办避免同一场已结束会议继续催办。
function isHeldMeeting(m) { return !!m && m.stage === 'ended' }
// 会议属于本年第几期（非本年→0）
function meetingPeriod(m) {
  if (!m) return 0
  const titleText = String(m.title || '')
  const titleMatch = titleText.match(/2026年第([1-6一二三四五六])次/)
  if (titleMatch) {
    const cn = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6 }
    return cn[titleMatch[1]] || Number(titleMatch[1])
  }
  if (!m.meetingDate) return 0
  const p = String(m.meetingDate).split('-')
  if (Number(p[0]) !== curYear) return 0
  return Math.ceil(Number(p[1]) / 2)
}

// 年度会议计划（今年 6 个双月期）：竖向时间轴总览，一条主线贯全年，节点亮灭即进度。放首页「前置」。
// 状态：done=已召开(取该期首场有效会议)｜current=本期待开｜overdue=已过期未开｜upcoming=未到、待排
// node=时间轴圆点内的字符：已开✓、逾期!、其余显期号(第几期)
const planBadge = { done: '已开 ✓', current: '待开', overdue: '逾期', upcoming: '待排' }
const demoDonePeriods = {
  1: { title: '2026年第1次业委会例会', meetingDate: '2026-01-18' },
  2: { title: '2026年第2次业委会例会', meetingDate: '2026-03-15' }
}
const yearPlan = computed(() => {
  const heldByPeriod = {}
  for (const m of (allMeetings.value || [])) {
    if (!isHeldMeeting(m)) continue
    const p = meetingPeriod(m)
    if (p >= 1 && p <= 6 && !heldByPeriod[p]) heldByPeriod[p] = m
  }
  if (curYear === 2026) {
    for (const [period, meeting] of Object.entries(demoDonePeriods)) {
      if (!heldByPeriod[period]) heldByPeriod[period] = Object.assign({ stage: 'ended', compliance: 'valid' }, meeting)
    }
  }
  const rows = []
  for (let p = 1; p <= 6; p++) {
    const held = heldByPeriod[p]
    let status, sub, node
    if (held) {
      status = 'done'; node = '✓'
      const d = String(held.meetingDate || '').split('-')
      sub = d.length === 3 ? (Number(d[1]) + '月' + Number(d[2]) + '日 已召开') : '已召开'
    } else if (p === curPeriod) {
      status = 'current'; sub = '本期待召开'; node = String(p)
    } else if (p < curPeriod) {
      status = 'overdue'; sub = '已逾期未召开'; node = '!'
    } else {
      status = 'upcoming'; sub = '按计划待召开'; node = String(p)
    }
    rows.push({ period: p, monthLabel: periodLabel(p), status, sub, node, meeting: held })
  }
  return rows
})

// ── 履职年历：分类横栏（开会默认/培训/接待）+ 12 月宫格 + 警示条 + 点月看当月该类事项 ──
const planTab = ref('meeting')   // 开会是本软件核心价值 → 默认
const calMonth = ref(curMonth)   // 选中月，默认本月
const calRecs = ref([])          // 全部接待记录（进页拉一次）
const calLearns = ref([])        // 全部学习培训（internal+training 合并）
const planTabLabel = computed(() => planTab.value === 'meeting' ? '会议' : (planTab.value === 'learning' ? '培训' : '接待'))
// "2026-07-05" → "7月5日"（其他格式原样返回）
function fmtPlanDate(s) {
  const p = String(s || '').split('-')
  return p.length === 3 ? (Number(p[1]) + '月' + Number(p[2]) + '日') : String(s || '')
}
// 日期是否属于今年第 m 月
function inMonth(dateStr, m) {
  const p = String(dateStr || '').split('-')
  return p.length >= 2 && Number(p[0]) === curYear && Number(p[1]) === m
}
// 培训是否"过期未开展"（计划日期已过还没结束）；todayStr() 用页面下方现成的函数
function isLearnOverdue(l) { return l.stage !== 'ended' && !!l.date && String(l.date) < todayStr() }
function goLearningDetail(item) { navigateTo('/pages/learning-detail/learning-detail?id=' + item.id) }
async function loadCalExtras() {
  const [recs, a, b] = await Promise.all([
    api.receptionRecords('all').catch(() => []),
    api.learningList('internal', null).catch(() => []),
    api.learningList('training', null).catch(() => [])
  ])
  calRecs.value = recs || []
  calLearns.value = [...(a || []), ...(b || [])]
}
// 12 个月宫格（随分类切换，每格一眼看该月该类状态）：
// 开会=该月所在双月期例会状态（已开绿✓/本期橙/逾期红!/待排灰）
// 培训=该月培训汇总（过期未开红!/N场待开橙/已完成绿✓/无灰—）
// 接待=该月接待汇总（N件待办橙/已办结绿✓/无灰—）
const monthCells = computed(() => {
  const cells = []
  for (let m = 1; m <= 12; m++) {
    let st, label
    if (planTab.value === 'meeting') {
      const row = yearPlan.value[Math.ceil(m / 2) - 1] || {}
      st = row.status || 'upcoming'
      if (st === 'done') label = m % 2 === 0 ? '已开 ✓' : ' '
      else if (st === 'current') label = m % 2 === 0 ? '待开' : ' '
      // 逾期/待开等两个月一期的状态统一放在期末月，便于按 1-2、3-4、5-6 阅读。
      else if (st === 'overdue') label = m % 2 === 0 ? '逾期 !' : ' '
      else label = m % 2 === 0 ? '待排' : ' '
    } else if (planTab.value === 'learning') {
      const ls = calLearns.value.filter(l => inMonth(l.date, m))
      const late = ls.filter(isLearnOverdue).length
      const todo = ls.filter(l => l.stage !== 'ended').length
      if (late) { st = 'overdue'; label = '过期未开 !' }
      else if (todo) { st = 'current'; label = todo + '场待开' }
      else if (ls.length) { st = 'done'; label = '已完成 ✓' }
      else { st = 'upcoming'; label = '—' }
    } else {
      const rs = calRecs.value.filter(r => inMonth(r.date, m))
      const todo = rs.filter(r => !r.done).length
      if (todo) { st = 'current'; label = todo + '件待办' }
      else if (rs.length) { st = 'done'; label = '已办结 ✓' }
      else { st = 'upcoming'; label = '—' }
    }
    cells.push({ m, status: st, label, todo: 0 })
  }
  return cells
})
const monthPairs = computed(() => {
  const rank = { overdue: 4, current: 3, done: 2, upcoming: 1 }
  const pairs = []
  for (let p = 1; p <= 6; p++) {
    const months = monthCells.value.slice((p - 1) * 2, p * 2)
    const status = months.reduce((best, mc) => (rank[mc.status] > rank[best] ? mc.status : best), 'upcoming')
    pairs.push({
      period: p,
      title: ((p - 1) * 2 + 1) + '-' + (p * 2) + '月',
      status,
      months
    })
  }
  return pairs
})
const selectedMeetingPlanRow = computed(() => {
  if (planTab.value !== 'meeting') return null
  return yearPlan.value[Math.ceil(calMonth.value / 2) - 1] || null
})
// 点月分流：已开期→下方看该期记录；逾期/待开→提示并定位待办卡；未到期→下方出「提前准备会议」
function onYcMonthTap(pair, mc) {
  calMonth.value = mc.m
  if (pair.status === 'overdue' || pair.status === 'current') {
    // 该期已有进行中/准备中的会议（待办卡里不重复列）→ 指向首页会议卡，而不是待办
    if (activeMeetingPeriods.value.has(pair.period)) {
      toast({ title: '该期（' + pair.title + '）会议正在进行，请从上方会议卡进入', icon: 'none' })
      const card = document.querySelector('.meeting-card, .current-card, .plan-stack')
      if (card && card.scrollIntoView) window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    toast({
      title: pair.status === 'overdue'
        ? pair.title + '例会已逾期，请在待办事项中补开'
        : '本期（' + pair.title + '）例会待开，请在待办事项中处理',
      icon: 'none'
    })
    highlightPlanTodo()
  }
}
// 待办卡定位高亮：滚过去 + 闪两下，让老人看清要点哪里
const planTodoFlash = ref(false)
let _todoFlashTimer = null
function highlightPlanTodo() {
  const el = document.querySelector('.plan-todo-card')
  if (el && el.scrollIntoView) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  clearTimeout(_todoFlashTimer)
  planTodoFlash.value = false
  requestAnimationFrame(() => {
    planTodoFlash.value = true
    _todoFlashTimer = setTimeout(() => { planTodoFlash.value = false }, 1800)
  })
}
// 日历下方反馈区内容（随选中月所在期次状态）：done=记录列表 / upcoming=提前准备；逾期与待开走待办卡不在此展示
const selPeriodFeedback = computed(() => {
  if (planTab.value !== 'meeting') return null
  const row = selectedMeetingPlanRow.value
  if (!row) return null
  const p = Math.ceil(calMonth.value / 2)
  const label = ((p - 1) * 2 + 1) + '-' + (p * 2) + '月'
  if (row.status === 'done') {
    const months = [p * 2 - 1, p * 2]
    const records = (allMeetings.value || [])
      .filter(mt => months.some(m => inMonth(mt.meetingDate, m)))
      .sort((a, b) => String(a.meetingDate || '').localeCompare(String(b.meetingDate || '')))
      .map(mt => ({
        key: 'ypf' + mt.id,
        title: mt.title || '业委会会议',
        sub: fmtPlanDate(mt.meetingDate) + (mt.location ? ' · ' + mt.location : ''),
        status: mt.stage === 'ended' ? 'done' : 'current',
        badge: mt.stage === 'ended' ? '已开 ✓' : (mt.stage === 'ongoing' ? '进行中' : '待开'),
        onTap: () => openMeetingTap(mt),
        // 已开的会议给「查看详情」明确按钮（与行点击同去详情页，给老人一个显眼的可点标识）
        detailTap: mt.stage === 'ended' ? () => openMeetingTap(mt) : null
      }))
    return { kind: 'records', title: label + '开会记录', records }
  }
  if (row.status === 'upcoming') {
    return { kind: 'upcoming', tip: label + '例会按计划还未到期' }
  }
  return null
})
// 警示条（"到期没做，赶紧补"，随分类）：例会逾期红 / 培训过期红 / 接待待跟进橙
const overduePeriodRows = computed(() => yearPlan.value.filter(r => r.status === 'overdue'))
const calAlert = computed(() => {
  if (planTab.value === 'meeting') {
    const rows = overduePeriodRows.value
    if (!rows.length) return null
    const labels = rows.map(r => r.monthLabel).join('、')
    return {
      level: '',
      text: rows.length === 1 ? labels + '例会逾期未开' : rows.length + '期例会逾期未开',
      sub: rows.length === 1 ? '例会是履职核心，请先补齐逾期期次' : labels + '需补齐',
      go: isChair.value ? '去补开 ›' : '',
      onTap: () => onPlanRow(rows[0])
    }
  }
  if (planTab.value === 'learning') {
    const n = calLearns.value.filter(isLearnOverdue).length
    if (!n) return null
    return { level: '', text: n + '场培训已过期未开展', sub: '请尽快安排培训或补充记录', go: '去查看 ›', onTap: goLearning }
  }
  const n = calRecs.value.filter(r => !r.done).length
  if (!n) return null
  return { level: 'warn', text: n + '件接待待跟进', sub: '接待事项需要闭环反馈', go: '去处理 ›', onTap: goReception }
})
const planFocusCards = computed(() => {
  if (planTab.value === 'meeting') {
    const currentRow = yearPlan.value[Math.ceil(curMonth / 2) - 1] || {}
    const overdueRow = overduePeriodRows.value[0] || null
    const nextRow = yearPlan.value.find(r => r.status === 'current' || r.status === 'upcoming') || currentRow
    const cards = [
      {
        key: 'current',
        label: '本月',
        title: currentRow.monthLabel ? currentRow.monthLabel + '例会' : curMonth + '月例会',
        sub: currentRow.status === 'done' ? '本期已完成' : (currentRow.status === 'overdue' ? '本期已逾期' : '本期还未召开'),
        status: currentRow.status || 'upcoming',
        onTap: () => currentRow.period && onPlanRow(currentRow)
      },
      {
        key: 'overdue',
        label: '逾期',
        title: overdueRow ? overdueRow.monthLabel + '例会' : '无逾期',
        sub: overdueRow ? '优先补开' : '履职节奏正常',
        status: overdueRow ? 'overdue' : 'done',
        onTap: () => overdueRow && onPlanRow(overdueRow)
      },
      {
        key: 'next',
        label: '下一期',
        title: nextRow.monthLabel ? nextRow.monthLabel + '例会' : '待排期',
        sub: nextRow.status === 'current' ? '当前待开' : (nextRow.status === 'upcoming' ? '按计划待召开' : '已完成'),
        status: nextRow.status || 'upcoming',
        onTap: () => nextRow.period && onPlanRow(nextRow)
      }
    ]
    return cards
  }
  const nowCell = monthCells.value.find(c => c.m === curMonth) || {}
  const attention = monthCells.value.find(c => c.status === 'overdue' || c.status === 'current') || null
  const nextCell = monthCells.value.find(c => c.m >= curMonth && c.status !== 'done') || nowCell
  return [
    { key: 'current', label: '本月', title: curMonth + '月' + planTabLabel.value, sub: nowCell.label || '暂无安排', status: nowCell.status || 'upcoming', onTap: () => { calMonth.value = curMonth } },
    { key: 'attention', label: attention && attention.status === 'overdue' ? '逾期' : '待办', title: attention ? attention.m + '月' + planTabLabel.value : '无待办', sub: attention ? attention.label : '暂无需要处理', status: attention ? attention.status : 'done', onTap: () => { if (attention) calMonth.value = attention.m } },
    { key: 'next', label: '下一项', title: nextCell ? nextCell.m + '月' + planTabLabel.value : '待安排', sub: nextCell ? nextCell.label : '暂无安排', status: nextCell ? nextCell.status : 'upcoming', onTap: () => { if (nextCell) calMonth.value = nextCell.m } }
  ]
})
// 选中月该类事项清单
const calList = computed(() => {
  const m = calMonth.value
  const items = []
  if (planTab.value === 'meeting') {
    for (const mt of (allMeetings.value || [])) {
      if (!inMonth(mt.meetingDate, m)) continue
      const ended = mt.stage === 'ended'
      items.push({
        key: 'm' + mt.id, icon: '📅', date: mt.meetingDate,
        title: mt.title || '业委会会议',
        sub: fmtPlanDate(mt.meetingDate) + (mt.location ? ' · ' + mt.location : ''),
        status: ended ? 'done' : 'current',
        badge: ended ? '已开 ✓' : '待开',
        onTap: () => openMeetingTap(mt)
      })
    }
    // 该期例会还没开 → 虚拟提醒行置顶（本期待开/逾期赶紧补）
    const row = yearPlan.value[Math.ceil(m / 2) - 1]
    if (row && (row.status === 'current' || row.status === 'overdue')) {
      items.unshift({
        key: 'plan' + row.period, icon: '📅', date: '',
        title: '第' + row.period + '期例会（' + row.monthLabel + '）',
        sub: row.status === 'overdue' ? '请尽快补开' : '',
        status: row.status,
        badge: row.status === 'overdue' ? '去补开' : '去通知',
        onTap: () => onPlanRow(row)
      })
    }
  } else if (planTab.value === 'learning') {
    for (const l of calLearns.value) {
      if (!inMonth(l.date, m)) continue
      const late = isLearnOverdue(l)
      items.push({
        key: 'l' + l.id, icon: '📖', date: l.date,
        title: l.title || '学习培训',
        sub: fmtPlanDate(l.date) + (l.time ? ' ' + String(l.time).slice(0, 5) : '') + (late ? ' · 已过期' : ''),
        status: l.stage === 'ended' ? 'done' : (late ? 'overdue' : (l.stage === 'ongoing' ? 'current' : 'upcoming')),
        badge: l.stage === 'ended' ? '已完成' : (late ? '去补开' : (l.stage === 'ongoing' ? '进行中' : '待开')),
        onTap: () => goLearningDetail(l)
      })
    }
  } else {
    for (const r of calRecs.value) {
      if (!inMonth(r.date, m)) continue
      items.push({
        key: 'r' + r.id, icon: '🤝', date: r.date,
        title: (r.visitorName || '来访') + ' 来访接待',
        sub: fmtPlanDate(r.date) + ' · ' + String(r.content || '').slice(0, 14),
        status: r.done ? 'done' : 'current',
        badge: r.done ? '已办结' : '待跟进',
        onTap: goReception
      })
    }
  }
  // 虚拟提醒行(无日期)保持最前，其余按日期升序
  return items.sort((a, b) => (a.date === '' ? -1 : b.date === '' ? 1 : String(a.date).localeCompare(String(b.date))))
})
const currentMonthTaskList = computed(() => {
  const active = calList.value.filter(it => it.status !== 'done')
  return active.length ? active : calList.value
})
// 方案A：接待/培训 tab 改「概览」——顶部三数字 metric（数据来自进页拉取的 calRecs / calLearns，真实统计）
const planOverview = computed(() => {
  if (planTab.value === 'reception') {
    const recs = calRecs.value || []
    return [
      { key: 'month', num: recs.filter(r => inMonth(r.date, curMonth)).length, label: '本月接待', tone: '' },
      { key: 'pending', num: recs.filter(r => !r.done).length, label: '待跟进', tone: 'warn' },
      { key: 'year', num: recs.filter(r => { const p = String(r.date || '').split('-'); return Number(p[0]) === curYear }).length, label: '年度累计', tone: '' }
    ]
  }
  if (planTab.value === 'learning') {
    const ls = calLearns.value || []
    return [
      { key: 'done', num: ls.filter(l => l.stage === 'ended').length, label: '已开展', tone: '' },
      { key: 'todo', num: ls.filter(l => l.stage !== 'ended' && !isLearnOverdue(l)).length, label: '待开', tone: '' },
      { key: 'overdue', num: ls.filter(isLearnOverdue).length, label: '过期未开', tone: 'danger' }
    ]
  }
  return []
})
// 待办「查看」详情弹窗：接待/培训点「查看」当场弹出该条详情，底部按钮再进对应页面做跟进
const todoDetail = ref(null)
function openTodoDetail(type, data) {
  if (type === 'reception') {
    const rows = [{ k: '时间', v: fmtPlanDate(data.date) + (data.time ? ' ' + String(data.time).slice(0, 5) : '') }]
    if (data.receiver) rows.push({ k: '接待人', v: data.receiver })
    if (data.categoryLabel) rows.push({ k: '分类', v: data.categoryLabel })
    if (data.content) rows.push({ k: '诉求', v: data.content })
    if (data.resolution) rows.push({ k: '处理', v: data.resolution })
    if (data.propertyReply) rows.push({ k: '物业反馈', v: data.propertyReply })
    todoDetail.value = {
      title: (data.visitorName || '来访') + (data.room ? ' · ' + data.room : '') + ' 来访接待',
      statusText: data.done ? '已办结' : '待跟进',
      statusClass: data.done ? 'done' : 'warn',
      rows,
      goLabel: '去接待页处理',
      onGo: () => { todoDetail.value = null; goReception() }
    }
  } else {
    const late = isLearnOverdue(data)
    const rows = [{ k: '时间', v: fmtPlanDate(data.date) + (data.time ? ' ' + String(data.time).slice(0, 5) : '') }]
    if (data.location) rows.push({ k: '地点', v: data.location })
    if (data.trainer) rows.push({ k: '讲师', v: data.trainer })
    if (data.attendees) rows.push({ k: '参加', v: data.attendees })
    todoDetail.value = {
      title: data.title || '学习培训',
      statusText: late ? '已过期未开' : (data.stage === 'ongoing' ? '进行中' : '待开'),
      statusClass: late ? 'danger' : (data.stage === 'ongoing' ? 'current' : 'upcoming'),
      rows,
      goLabel: '查看完整详情',
      onGo: () => { todoDetail.value = null; goLearningDetail(data) }
    }
  }
}
// 概览三数字兼作筛选器：点某个数字，下方列表切到该范围（接待 month/pending/year，培训 done/todo/overdue），默认待办
const ovFilter = ref('pending')
watch(planTab, (t) => { ovFilter.value = t === 'learning' ? 'todo' : 'pending' })
const planListTitle = computed(() => {
  if (planTab.value === 'reception') return ovFilter.value === 'month' ? '本月接待' : (ovFilter.value === 'year' ? '年度接待' : '待跟进')
  if (planTab.value === 'learning') return ovFilter.value === 'done' ? '已开展' : (ovFilter.value === 'overdue' ? '过期未开' : '待开')
  return '待办事项'
})
// 接待/培训列表：受概览筛选（ovFilter），按日期升序；每项点「查看」弹该条详情
const allPendingList = computed(() => {
  if (planTab.value === 'reception') {
    const all = calRecs.value || []
    let recs
    if (ovFilter.value === 'month') recs = all.filter(r => inMonth(r.date, curMonth))
    else if (ovFilter.value === 'year') recs = all.filter(r => { const p = String(r.date || '').split('-'); return Number(p[0]) === curYear })
    else recs = all.filter(r => !r.done)
    return recs.slice().sort((a, b) => String(a.date || '').localeCompare(String(b.date || ''))).map(r => ({
      key: 'r' + r.id,
      title: (r.visitorName || '来访') + ' 来访接待',
      sub: fmtPlanDate(r.date),
      date: r.date, status: 'view', badge: '查看', onTap: () => openTodoDetail('reception', r)
    }))
  }
  if (planTab.value === 'learning') {
    const all = calLearns.value || []
    let ls
    if (ovFilter.value === 'done') ls = all.filter(l => l.stage === 'ended')
    else if (ovFilter.value === 'overdue') ls = all.filter(isLearnOverdue)
    else ls = all.filter(l => l.stage !== 'ended' && !isLearnOverdue(l))
    return ls.slice().sort((a, b) => String(a.date || '').localeCompare(String(b.date || ''))).map(l => {
      const late = isLearnOverdue(l)
      return {
        key: 'l' + l.id,
        title: l.title || '学习培训',
        sub: fmtPlanDate(l.date) + (l.time ? ' ' + String(l.time).slice(0, 5) : '') + (late ? ' · 已过期' : ''),
        date: l.date, status: 'view', flag: late ? 'todo-overdue' : '', badge: '查看', onTap: () => openTodoDetail('learning', l)
      }
    })
  }
  return []
})
// 有进行中/准备中会议的期次（按会议日期算双月期）→ 从待办里剔除，避免与首页会议卡重复
const activeMeetingPeriods = computed(() => {
  const set = new Set()
  for (const c of (currents.value || [])) {
    if (c.stage !== 'preparing' && c.stage !== 'ongoing') continue
    const mm = String(c.meetingDate || '').match(/^\d{4}-(\d{2})-\d{2}/)
    if (mm) set.add(Math.ceil(Number(mm[1]) / 2))
  }
  return set
})
const planTodoList = computed(() => {
  if (planTab.value !== 'meeting') return allPendingList.value
  const rows = []
  const active = activeMeetingPeriods.value
  const currentRow = yearPlan.value[Math.ceil(curMonth / 2) - 1]
  if (currentRow && currentRow.status === 'current' && !active.has(currentRow.period)) rows.push(currentRow)
  for (const row of overduePeriodRows.value) {
    if (active.has(row.period)) continue
    if (!rows.some(r => r.period === row.period)) rows.push(row)
  }
  return rows.map(row => ({
    key: 'todo-meeting-' + row.period,
    icon: '📅',
    title: '第' + row.period + '期例会（' + row.monthLabel + '）',
    sub: '',
    status: row.status,
    badge: row.status === 'overdue' ? '去补开' : '去通知',
    onTap: () => onPlanRow(row)
  }))
})
const primaryMeetingAction = computed(() => {
  if (planTab.value !== 'meeting') return null
  const row = yearPlan.value[Math.ceil(curMonth / 2) - 1]
  if (!row || (row.status !== 'current' && row.status !== 'overdue')) return null
  return {
    status: row.status,
    kicker: row.status === 'overdue' ? '逾期例会' : '本期要做',
    title: '第' + row.period + '期例会（' + row.monthLabel + '）',
    cta: row.status === 'overdue' ? '去补开' : '去通知',
    onTap: () => onPlanRow(row)
  }
})
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
  meetingTime: '',
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
const topicDraft = reactive({ title: '', type: 'discussion', decisionType: 'none', options: [], content: '' })
const topicInput = ref('') // 议题输入框当前内容（打字/语音），点"确定"加入 topics 列表
const topicInputType = ref('discussion') // 快捷新增议题的类型；每加一条后保留，便于连续录入同类议题
const topicInputDecisionType = ref('simple')
const topicInputOptions = ref([])

// 必填校验：红框状态（会议名称/会议议题/会议地点）。点"生成通知"缺失→弹卡片→确认后亮红框；
// 用户点进对应输入框（focus）即清除红框。
const fieldErrors = reactive({ title: false, topics: false, location: false, meetingDate: false, meetingTime: false })
function clearFieldError(k) { if (fieldErrors[k]) fieldErrors[k] = false }
// 议题只要加进去一条（打字/语音/弹窗任一路径），红框就撤掉
watch(() => createForm.topics.length, (n) => { if (n > 0) fieldErrors.topics = false })
// 时间/地点选了值就撤红框（时间靠选择器、地点靠下拉/输入，focus 不一定触发）
watch(() => createForm.meetingDate, (v) => { if (v) fieldErrors.meetingDate = false })
watch(() => createForm.meetingTime, (v) => { if (v) fieldErrors.meetingTime = false })
watch(() => createForm.location, (v) => { if (v && v.trim()) fieldErrors.location = false })

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
  loadCalExtras() // 履职年历：接待+培训数据（月格角标与当月清单用）
  loadDraft()
  // 从「会议通知」页左箭头返回：以编辑模式打开该会议（一次性交接，用完即清）
  const _editId = getStorage('editMeetingId', null)
  if (_editId) { removeStorage('editMeetingId'); openMeetingForEdit(Number(_editId)) }
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
    allMeetings.value = all   // 供「本期会议」按本月真实统计（完整结束的会议计入已完成）
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
      // 【测试阶段】历史演示例会（第1、2次）：只进「历史记录」，不在首页当前会议卡片显示。
      // 已结束会议一律保留在首页卡片（含已公示、含"会议无效"），手动删除前不消失；仅历史例会按标题挡掉。
      // ⚠ 反复报的 bug 根因：原来这里有 && m.compliance !== 'invalid'，会把"无效"会议挡掉——
      //   演示里会议多因签到不过半判 invalid，一公示/结束回首页卡片就消失。测试期去掉该条，无效会议也保留。
      // 上线前复原：删掉 HISTORY_ONLY_TITLES 排除；按产品要求再决定是否加回 && m.compliance !== 'invalid'
      //   与 && (!m.publish || !m.publish.published)。
      const HISTORY_ONLY_TITLES = ['2026年第1次业委会例会', '2026年第2次业委会例会']
      const pend = all.filter((m) => m.stage === 'ended'
        && !HISTORY_ONLY_TITLES.includes(m.title))
      actives = [...actives, ...pend]
    }
    currents.value = actives.map((m) => decorateCurrent(m, chair))
    refreshMinutesGenStates() // 异步补查：纪要任务生成中 → 卡片按钮切「继续生成会议纪要」
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
  // 纪要已生成的已结束会议 → 三步全部完成(step=4)；否则按阶段(ended=3，会后总结进行中)
  const step = (m.stage === 'ended' && m.minutesGenerated) ? 4 : (STEP_BY_STAGE[m.stage] || 1)
  const steps = [1, 2, 3].map((no) => ({
    no: no,
    label: STEP_LABELS[no],
    state: no < step ? 'done' : (no === step ? 'active' : 'todo')
  }))
  let ctaLabel, ctaIcon, tag
  let minutesGen = false
  if (m.stage === 'preparing') {
    if (chair) {
      // 准备阶段(会议通知阶段)：卡片按钮统一「继续通知」——点回会议通知页继续发送/开始会议
      ctaLabel = '继续通知'
      ctaIcon = '📣'
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
    // ended：纪要生成中 → 继续生成会议纪要(点回纪要页)；已生成 → 查看会议；未生成 → 整理会议记录
    minutesGen = !m.minutesGenerated && aiTask.active && !!aiTask.targetPath && aiTask.targetPath.indexOf('meetingId=' + m.id) >= 0
    if (minutesGen) {
      ctaLabel = '继续生成会议纪要'
      ctaIcon = '🤖'
    } else {
      ctaLabel = m.minutesGenerated ? '查看会议' : '整理会议记录'
      ctaIcon = m.minutesGenerated ? '' : '📝'
    }
    tag = '会后总结'
  }
  return {
    id: m.id, title: m.title, meetingDate: m.meetingDate, meetingTime: (m.meetingTime || '').slice(0, 5),
    location: m.location, timeText: formatMeetingTime(m), locationText: m.location || '地点待定',
    step: step, steps: steps, stage: m.stage, stageText: MEETING_STAGE_TEXT[m.stage] || '未开始',
    ctaLabel: ctaLabel, ctaIcon: ctaIcon, tag: tag, minutesGen: minutesGen
  }
}

// 已结束未生成纪要的会议：问服务端纪要任务状态（跨刷新/换设备可靠），生成中 → 卡片按钮切「继续生成会议纪要」。
// 用户预期：生成完成前卡片都保持"回纪要页"的入口，完成后才变回"查看会议"。
async function refreshMinutesGenStates() {
  for (const c of (currents.value || [])) {
    if (c.stage !== 'ended' || c.minutesGen || c.ctaLabel !== '整理会议记录') continue
    try {
      const st = await api.committeeMinutesStatus(c.id)
      if (st && st.status === 'running') {
        c.minutesGen = true
        c.ctaLabel = '继续生成会议纪要'
        c.ctaIcon = '🤖'
      }
    } catch (e) { /* 旧后端无此接口/离线：保持默认入口 */ }
  }
}

function formatMeetingTime(m) {
  const d = m && m.meetingDate ? fmtPlanDate(m.meetingDate) : '时间待定'
  const t = m && m.meetingTime ? String(m.meetingTime).slice(0, 5) : ''
  return t ? d + ' ' + t : d
}
// 有进行中会议时，首页「去通知」按钮改为「新会议」（此时再发通知即另起一场）
const hasOngoingMeeting = computed(() => currents.value.some((c) => c.stage === 'ongoing'))

async function goCurrent(cur) {
  // 纪要生成中：点卡片回纪要页看进度/结果，而不是会议详情页（带硬导航兜底）
  if (cur.minutesGen) {
    const target = '/pages/minutes-view/minutes-view?meetingId=' + cur.id + '&from=committee'
    const browserUrl = '/minutes-view?meetingId=' + cur.id + '&from=committee'
    try { await navigateTo(target) } catch (navErr) { console.error('[继续生成纪要] 软跳 reject：', navErr) }
    setTimeout(() => { if (!document.querySelector('.mv-page')) window.location.href = browserUrl }, 500)
    return
  }
  try {
    await api.committeeDetail(cur.id)
  } catch (e) {
    toast({ title: '会议已更新，正在刷新', icon: 'none' })
    await loadAll()
    return
  }
  const chair = perm.isChair() || perm.isRecorder()
  const target = chair
    ? '/pages/committee-detail/committee-detail?id=' + cur.id
    : '/pages/my-meeting/my-meeting?id=' + cur.id
  // 硬导航兜底：navigateTo(router.push) 偶发"URL变了却不切换视图"，把用户留在主页（如"继续通知/去开会"回不到通知页）。
  // 软跳后延时校验目标页根节点是否真的挂上（详情页 .detail-page / 我的会议 .mm），没挂上就 window.location 硬跳，确保必达。
  const browserUrl = chair ? '/committee-detail?id=' + cur.id : '/my-meeting?id=' + cur.id
  const rootSel = chair ? '.detail-page' : '.mm'
  try { await navigateTo(target) } catch (navErr) { console.error('[去开会] 软跳 reject：', navErr) }
  setTimeout(() => {
    if (!document.querySelector(rootSel)) {
      console.warn('[去开会] 软跳未挂载目标页，硬导航兜底 →', browserUrl)
      window.location.href = browserUrl
    }
  }, 500)
}

async function removeCurrent(cur) {
  const isRecordingThisMeeting = meetingRecordingSession.active
    && String(meetingRecordingSession.meetingId || '') === String(cur.id)
  const res = await showModal({
    title: '删除会议',
    content: '确定删除「' + cur.title + '」？'
      + (isRecordingThisMeeting ? '\n\n该会议正在录音，删除后录音将立即停止并丢弃。' : '')
      + '\n\n删除后无法恢复。',
    confirmText: '删除',
    confirmColor: '#E74C3C'
  })
  if (!res.confirm) return
  try {
    await api.committeeRemove(cur.id)
    await discardMeetingRecording(cur.id)
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

// 点计划某一期：已开→看这场会议；未开的（本期/逾期/未到）→主任可发起，未到期提示「提前召开」，委员提示等待
function advanceSelectedMeeting() {
  const row = selectedMeetingPlanRow.value
  if (!row) return
  openNewMeeting(row.period)
}

async function onPlanRow(row) {
  if (row.status === 'done' && row.meeting) { openMeetingTap(row.meeting); return }
  // 本期/逾期：点「去通知/去补开」直接进入发起会议(通知)流程，无需二次确认
  if (row.status === 'current' || row.status === 'overdue') {
    if (isChair.value) { openNewMeeting(row.period); return }
    const tip = row.status === 'overdue'
      ? row.monthLabel + '这期还没召开例会，已逾期。'
      : '本期（' + row.monthLabel + '）还没召开例会。'
    await showModal({ title: row.monthLabel + '例会', content: tip + '请等待主任发起。', showCancel: false, confirmText: '知道了' })
    return
  }
  // 未到期（提前召开）仍二次确认，避免误触提前起会
  const tip = row.monthLabel + '例会按计划还没到时间。'
  if (isChair.value) {
    const res = await showModal({ title: row.monthLabel + '例会', content: tip + '是否提前召开？', confirmText: '提前召开', cancelText: '暂不', showCancel: true })
    if (res && res.confirm) openNewMeeting(row.period)
  } else {
    await showModal({ title: row.monthLabel + '例会', content: tip + '请等待主任发起。', showCancel: false, confirmText: '知道了' })
  }
}

function onSearch(e) { keyword.value = e.target.value; loadAll() }
function clearSearch() { keyword.value = ''; loadAll() }

function switchStage(stage) {
  currentStage.value = stage
  keyword.value = ''
  loadAll()
}

async function openDetail(id) {
  try {
    await api.committeeDetail(id)
  } catch (e) {
    toast({ title: '会议已更新，正在刷新', icon: 'none' })
    await loadAll()
    return
  }
  // 硬导航兜底：软路由偶发"URL变了却不切换视图"，500ms 后目标页未挂载则 location 硬跳（对齐其它关键跳转做法）
  const browserUrl = '/committee-detail?id=' + id
  try { await navigateTo('/pages/committee-detail/committee-detail?id=' + id) } catch (navErr) { console.error('[进详情] 软跳 reject：', navErr) }
  setTimeout(() => { if (!document.querySelector('.detail-page')) window.location.href = browserUrl }, 500)
}

function openMeetingTap(item) {
  if (item.compliance === 'invalid') {
    openMinutes(item.id)
    return
  }
  openDetail(item.id)
}

function openMinutes(id) {
  navigateTo('/pages/minutes-view/minutes-view?meetingId=' + id)
}

async function openNewMeeting(period) {
  createVisible.value = true
  createTab.value = 'manual'      // 每次进来默认手动填写面板
  editingMeetingId.value = null   // 全新会议：非编辑模式
  docPrefilled.value = false
  materialPrefillOpen.value = false
  materialText.value = ''
  materialFiles.value = []
  materialScanResult.value = null
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
  createForm.title = ''
  createForm.meetingDate = ''
  createForm.meetingTime = ''
  createForm.location = ''
  locationPreset.value = ''
  createForm.description = ''
  createForm.topics = []
  createForm.juweiWitness = false
  // 快照默认占位值：日期/时间/地点等于这些默认时视为"未填"，不参与冲突判定，可被识别值直接填入
  createInitialDefaults.value = { title: '', meetingDate: createForm.meetingDate, meetingTime: createForm.meetingTime, location: createForm.location }
  topicInput.value = ''
  suggestedTitle.value = ''
  pendingMaterials.value = []
  scanBusy.value = ''
  lastScanTokens.value = 0
  // 从首页某期例会「去通知」进来时，直接用那一期期数当次数，标题与首页一致（第N期 → 第N次）
  if (period >= 1) {
    suggestedTitle.value = curYear + '年第' + period + '次业委会例会'
    return
  }
  // 否则（顶部新建/继续草稿等）基于历史会议推荐下一次标题（大多数会议为统一格式）
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
  // 编辑已建会议时不存草稿（它是正式会议不是草稿）；新建会议才把半成品存草稿
  if (editingMeetingId.value) editingMeetingId.value = null
  else persistDraft()
  createVisible.value = false
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
  clearScanItems()
  scanResultCard.value = null
}

// ——— 会议草稿：发起会议填了一半返回首页时自动保存，首页出「待发送·草稿」卡片可继续 ———
// 只存可序列化内容（表单字段 + 已识别落库的材料）；未识别的暂存照片(scanItems 含 File)不入草稿。
const DRAFT_KEY = 'committee_meeting_draft'
const draft = ref(null)            // 当前草稿快照（供首页卡片展示）；null=无草稿

// 快照当前发起会议表单为可序列化对象
function snapshotDraft() {
  return {
    title: createForm.title || '',
    meetingDate: createForm.meetingDate || '',
    meetingTime: createForm.meetingTime || '',
    location: createForm.location || '',
    description: createForm.description || '',
    juweiWitness: !!createForm.juweiWitness,
    topics: JSON.parse(JSON.stringify(createForm.topics || [])),
    locationPreset: locationPreset.value || '',
    pendingMaterials: JSON.parse(JSON.stringify(pendingMaterials.value || [])),
    materialFiles: JSON.parse(JSON.stringify(materialFiles.value || [])),
    savedAt: Date.now()
  }
}
// 值不值得存草稿：填了会议名 / 加了议题 / 有材料才算（日期/时间/地点是开窗默认值，不算）
function draftHasContent(d) {
  if (!d) return false
  return !!(String(d.title || '').trim()
    || (d.topics && d.topics.length)
    || (d.pendingMaterials && d.pendingMaterials.length)
    || (d.materialFiles && d.materialFiles.length))
}
// 有实质内容才落盘并提示；空表单直接返回不动已有草稿
function persistDraft() {
  const snap = snapshotDraft()
  if (!draftHasContent(snap)) return
  setStorage(DRAFT_KEY, snap)
  draft.value = snap
  toast({ title: '已保存草稿，可在首页继续', icon: 'none' })
}
function loadDraft() {
  const d = getStorage(DRAFT_KEY, null)
  draft.value = draftHasContent(d) ? d : null
}
function clearDraft() {
  removeStorage(DRAFT_KEY)
  draft.value = null
}
const hasDraft = computed(() => draftHasContent(draft.value))
// 草稿卡片摘要：会议名（缺省占位）
const draftTitle = computed(() => (draft.value && String(draft.value.title || '').trim()) || '未命名会议')
// 草稿卡片摘要：日期 时间 · 地点（有啥显啥）
const draftSummary = computed(() => {
  const d = draft.value
  if (!d) return ''
  const dt = [d.meetingDate, d.meetingTime].filter(Boolean).join(' ')
  return [dt, d.location].filter(Boolean).join(' · ')
})
const draftMaterialCount = computed(() => {
  const d = draft.value
  if (!d) return 0
  return (d.pendingMaterials ? d.pendingMaterials.length : 0) + (d.materialFiles ? d.materialFiles.length : 0)
})

// 「继续通知」：把草稿还原进表单并打开发起会议面板（不重置）
async function continueDraft() {
  const d = draft.value
  if (!d) { openNewMeeting(); return }
  createVisible.value = true
  docPrefilled.value = false
  materialPrefillOpen.value = false
  materialText.value = ''
  materialScanResult.value = null
  topicDialogOpen.value = false
  timePickerOpen.value = false
  datePickerOpen.value = false
  createForm.title = d.title || ''
  createForm.meetingDate = d.meetingDate || ''
  createForm.meetingTime = d.meetingTime || ''
  createForm.location = d.location || ''
  createForm.description = d.description || ''
  createForm.topics = JSON.parse(JSON.stringify(d.topics || []))
  createForm.juweiWitness = !!d.juweiWitness
  locationPreset.value = d.locationPreset || (d.location ? '__other__' : '社区活动室')
  syncLocationPreset(createForm.location)
  pendingMaterials.value = JSON.parse(JSON.stringify(d.pendingMaterials || []))
  materialFiles.value = JSON.parse(JSON.stringify(d.materialFiles || []))
  createInitialDefaults.value = { title: '', meetingDate: createForm.meetingDate, meetingTime: createForm.meetingTime, location: createForm.location }
  topicInput.value = ''
  scanBusy.value = ''
  lastScanTokens.value = 0
  // 标题推荐：还是给个下一次序号推荐（草稿已填标题时不显示 ghost）
  try {
    const all = await api.committeeList(null)
    suggestedTitle.value = computeSuggestedTitle(all || [])
  } catch (e) { suggestedTitle.value = '' }
}

// 「放弃草稿」：确认后清除
async function discardDraft() {
  const r = await showModal({
    title: '放弃这份草稿？',
    content: '放弃后，' + draftTitle.value + ' 已填写的内容将被清除，且无法找回。',
    confirmText: '放弃草稿',
    cancelText: '再想想',
    size: 'large'
  })
  if (r && r.confirm) { clearDraft(); toast({ title: '已放弃草稿', icon: 'none' }) }
}

// ——— 编辑已建会议：从「会议通知」页左箭头返回时，用本表单以「编辑模式」打开该会议，保存=更新不新建 ———
const editingMeetingId = ref(null)     // 非空=当前是在编辑已存在的会议（提交走 committeeUpdate）
const editInitialJuwei = ref(false)    // 载入时的居委会见证态，提交时对比决定是否翻转标记
async function openMeetingForEdit(id) {
  try {
    const d = await api.committeeDetail(id)
    if (!d) { toast({ title: '会议信息加载失败', icon: 'none' }); return }
    createVisible.value = true
    editingMeetingId.value = id
    docPrefilled.value = false
    materialPrefillOpen.value = false
    materialText.value = ''; materialFiles.value = []; materialScanResult.value = null
    topicDialogOpen.value = false; timePickerOpen.value = false; datePickerOpen.value = false
    createForm.title = d.title || ''
    createForm.meetingDate = d.meetingDate || ''
    createForm.meetingTime = (d.meetingTime || '').slice(0, 5)
    createForm.location = d.location || ''
    createForm.description = d.description || ''
    createForm.topics = (((d.record && d.record.topics) || d.topics) || []).map((t) => ({
      title: t.title || '',
      type: t.type || 'decision',
      decisionType: t.decisionType || 'none',
      options: Array.isArray(t.options) ? JSON.parse(JSON.stringify(t.options)) : [],
      content: t.content || '',
      realNameVote: !!t.realNameVote
    }))
    createForm.juweiWitness = !!(d.hasMajorIssue || d.juweiWitness)
    editInitialJuwei.value = createForm.juweiWitness
    locationPreset.value = commonLocations.indexOf(createForm.location) >= 0
      ? createForm.location : (createForm.location ? '__other__' : '社区活动室')
    createInitialDefaults.value = { title: '', meetingDate: createForm.meetingDate, meetingTime: createForm.meetingTime, location: createForm.location }
    topicInput.value = ''; suggestedTitle.value = ''
    pendingMaterials.value = []; scanBusy.value = ''; lastScanTokens.value = 0
  } catch (e) {
    toast({ title: (e && e.message) || '会议信息加载失败', icon: 'none' })
  }
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
  // 支持一次多选：全部加进材料列表，不必选一个就返回再重选。
  const files = await pickFiles()
  if (!files || !files.length) return // 用户取消
  materialFiles.value = materialFiles.value.concat(files.map((f) => ({
    name: f.name,
    sizeText: humanSize(f.size)
  })))
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
const createInitialDefaults = ref({})  // 表单打开时的默认占位值快照（日期/时间/地点）
// 字段是否为"用户真正填过"（手动改或识别填过）——等于初始默认占位则视为未填
function isFieldUserSet(key) {
  const v = (createForm[key] == null ? '' : String(createForm[key])).trim()
  const def = (createInitialDefaults.value && createInitialDefaults.value[key]) || ''
  return !!v && v !== def
}

// ——— 待挂载的会议材料（供委员传阅：如上级文件精神、报价单等）———
// 「去通知」建会成功后再逐份挂到会议（届时触发 OCR）。来源：拍照/上传 AI 判类为 material 的文件。
const pendingMaterials = ref([])      // [{ url, fileName, fileType, fileSize, sizeText }]
const materialsOpen = ref(false)      // 会议材料：默认折叠只显示份数，点标题右侧图标展开看详情
const createTab = ref('manual')       // 发起会议顶部分段：manual=手动填写 / scan=拍照上传；识别完成自动切回 manual 看预填结果
function removePendingMaterial(idx) {
  pendingMaterials.value = pendingMaterials.value.filter((_, i) => i !== idx)
}
// 材料行小图标：按文件类型区分（图片/PDF/其他）
function matExt(m) {
  let t = String((m && m.fileType) || '').toLowerCase()
  if (!t && m && m.fileName && m.fileName.indexOf('.') >= 0) t = m.fileName.split('.').pop().toLowerCase()
  return t
}
// 微信式文件卡：按类型给短标签 + 配色
function matBadge(m) {
  const t = matExt(m)
  if (t === 'pdf') return { label: 'PDF', cls: 'pdf' }
  if (['doc', 'docx'].includes(t)) return { label: 'DOC', cls: 'doc' }
  if (['xls', 'xlsx', 'csv'].includes(t)) return { label: 'XLS', cls: 'xls' }
  if (['ppt', 'pptx'].includes(t)) return { label: 'PPT', cls: 'ppt' }
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(t)) return { label: 'IMG', cls: 'img' }
  if (t === 'txt') return { label: 'TXT', cls: 'txt' }
  return { label: t ? t.slice(0, 3).toUpperCase() : '文件', cls: 'file' }
}

// ——— 拍照/上传 → 攒进暂存列表（缩略图预览）→ 一起「开始 AI 识别」→ 自动分辨通知/材料 ———
const scanBusy = ref('')          // '' | 'file'：识别中（复用识别弹窗），空=空闲
const scanRecognizing = ref(false) // 正在多文件识别（用于禁用卡片/按钮）
const lastScanTokens = ref(0)     // 上次识别消耗的 token（低调显示在卡片下方）

// 暂存待识别的照片/文件：[{ id, file, name, ext, isImage, thumbUrl }]
const scanItems = ref([])
let _scanId = 0
const IMG_EXT = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp']
function addScanItem(file, thumbUrl) {
  if (!file) return
  const ext = String((file.name || '').split('.').pop() || '').toLowerCase()
  const isImage = IMG_EXT.includes(ext) || String(file.type || '').startsWith('image/')
  scanItems.value = scanItems.value.concat([{
    id: ++_scanId, file, name: file.name || '文件', ext, isImage,
    thumbUrl: thumbUrl || (isImage ? URL.createObjectURL(file) : '')
  }])
}
function removeScanItem(id) {
  const it = scanItems.value.find((x) => x.id === id)
  if (it && it.thumbUrl && it.thumbUrl.indexOf('blob:') === 0) { try { URL.revokeObjectURL(it.thumbUrl) } catch (e) {} }
  scanItems.value = scanItems.value.filter((x) => x.id !== id)
}
function clearScanItems() {
  scanItems.value.forEach((it) => { if (it.thumbUrl && it.thumbUrl.indexOf('blob:') === 0) { try { URL.revokeObjectURL(it.thumbUrl) } catch (e) {} } })
  scanItems.value = []
}
function scanThumbIcon(ext) { return ext === 'pdf' ? '📄' : '📎' }
// 相机取景界面左下角「相册」缩略图：取最近一张有缩略图的照片（像系统相机连拍）
const lastShotThumb = computed(() => {
  const arr = scanItems.value
  for (let i = arr.length - 1; i >= 0; i--) { if (arr[i].isImage && arr[i].thumbUrl) return arr[i].thumbUrl }
  return ''
})
// 点击暂存缩略图 → 复用全屏材料查看器放大看清（图片可再放大，PDF 也能预览）
function openScanItemPreview(it) {
  if (!it) return
  const url = it.thumbUrl || (it.file ? URL.createObjectURL(it.file) : '')
  if (!url) return
  openMaterialViewer({ url, name: it.name, fileType: (it.file && it.file.type) || it.ext || '' })
}

// 上传：source='image' 选图片(可多张) / 'file' 选文档(PDF/Word 等)。拆两个入口——
// 手机微信对"图片+文档混选"会退化成单选，纯 image/* 时一次多选更容易生效；选中的都攒进暂存列表(不立即识别)。
async function startDocScan(source = 'image') {
  if (scanRecognizing.value) return
  // 企业微信里「图片」走 JS-SDK 相册多选（内置浏览器把 <input multiple> 强制单选）；
  // 用户取消就停手，配置/接口等异常则回退系统选择器，绝不打断。
  if (source === 'image' && isWecom()) {
    try {
      const files = await chooseWecomImages(9)
      files.forEach((f) => addScanItem(f))
      return
    } catch (e) {
      if (isWecomCancel(e)) return
      console.warn('[wecom] chooseImage 失败，回退系统选择器', e)
    }
  }
  const accept = source === 'file'
    ? '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,application/pdf'
    : 'image/*'
  const files = await pickFiles(accept)
  if (!files || !files.length) return // 用户取消
  files.forEach((f) => addScanItem(f))
}

// 识别中窗口点 × 取消：收起窗口、复位状态；在途请求返回后按标志丢弃，文件保留可重试
let _recognizeCancelled = false
function cancelRecognize() {
  _recognizeCancelled = true
  stopDocProgress()
  clearInterval(_scanSecTimer)
  _scanSecTimer = null
  scanBusy.value = ''
  docProgress.value = 0
  scanRecognizing.value = false
  toast({ title: '已取消识别', icon: 'none' })
}

// 「开始 AI 识别（N）」：把暂存的 N 个文件一起送后端统一识别
async function recognizeScanItems() {
  if (scanRecognizing.value || !scanItems.value.length) return
  const files = scanItems.value.map((x) => x.file)
  _recognizeCancelled = false
  scanRecognizing.value = true
  scanBusy.value = 'file'
  startDocProgress()
  scanSec.value = 0
  clearInterval(_scanSecTimer)
  _scanSecTimer = setInterval(() => { scanSec.value += 1 }, 1000)
  try {
    const res = await api.committeeParseDocuments(files)
    if (_recognizeCancelled) return // 用户已点 × 取消：丢弃结果，保留暂存文件可重试
    stopDocProgress()
    docProgress.value = 100
    await new Promise((r) => setTimeout(r, 350))
    if (res && res.tokens > 0) lastScanTokens.value = res.tokens
    clearInterval(_scanSecTimer)
    _scanSecTimer = null
    scanBusy.value = ''
    docProgress.value = 0
    await handleMultiScanResult(res)
    clearScanItems()
  } catch (e) {
    if (_recognizeCancelled) return
    stopDocProgress()
    toast({ title: '识别失败，请重试或手动填写', icon: 'none' })
  } finally {
    clearInterval(_scanSecTimer)
    _scanSecTimer = null
    scanBusy.value = ''
    docProgress.value = 0
    scanRecognizing.value = false
  }
}

// 识别弹窗的阶段与文案：按进度分 上传→OCR提取→AI理解 三步（模拟节奏，后端一次性返回）
const scanSec = ref(0)
let _scanSecTimer = null
const scanStage = computed(() => (docProgress.value < 25 ? 1 : docProgress.value < 70 ? 2 : 3))
const scanSay = computed(() => {
  if (docProgress.value < 25) return '正在安全上传文件…'
  if (docProgress.value < 70) return '正在解析文字内容…'
  return '正在理解内容、判断文件类型…'
})

// ——— 模拟手机相机（测试用）：全屏取景框对着一张"纸质会议通知"，按快门出照片；使用照片=加入暂存，可连拍多张 ———
const mockCameraVisible = ref(false)
const mockCamFlash = ref(false)
const mockViewfinderUrl = ref('')
const mockShotUrl = ref('')       // 拍后照片预览图（非空 = 预览确认模式）
let _mockShotCanvas = null
let _mockShotFile = null          // 预览中待确认的照片文件，点「使用照片」才送识别
const mockSampleIdx = ref(0)      // 当前取景样张下标（左右滑动/箭头切换，模拟拍不同纸质文件）
let _mockSampleCanvases = []      // 本次相机会话预生成的各样张 canvas
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
// 通用纸质文件样张：白纸 + 标题 + 正文若干行
function buildDocCanvas(title, lines) {
  const cv = document.createElement('canvas'); cv.width = 750; cv.height = 940
  const ctx = cv.getContext('2d')
  ctx.fillStyle = '#fdfcf8'; ctx.fillRect(0, 0, 750, 940)
  ctx.fillStyle = '#111'; ctx.textAlign = 'center'
  ctx.font = 'bold 44px serif'
  ctx.fillText(title, 375, 96)
  ctx.font = '30px serif'; ctx.textAlign = 'left'
  let y = 180; for (const l of lines) { ctx.fillText(l, 64, y); y += 44 }
  return cv
}
// 多个样张，供左右滑动切换（覆盖 通知/材料 多种情形，方便测试连拍多张）
const MOCK_SAMPLES = [
  { label: '会议通知', build: buildSampleNoticeCanvas },
  { label: '维保报价单', build: () => buildDocCanvas('电梯维保报价单', [
    '致：阳光家园业主委员会',
    '',
    '我司就贵小区电梯维保报价如下：',
    '一、维保范围：4部乘客电梯；',
    '二、维保周期：每月2次例行保养；',
    '三、报价：每部每年8000元；',
    '四、全年合计：32000元；',
    '五、合同期限：一年。',
    '',
    '            宏达电梯维保有限公司',
    '              2026年7月1日'
  ]) },
  { label: '施工方案', build: () => buildDocCanvas('消防设施改造施工方案', [
    '一、项目背景：',
    '   小区消防管网老化，需整体改造。',
    '二、施工内容：',
    '   更换消防主管道及喷淋头；',
    '   增设室内消防栓12处。',
    '三、工期：预计45天；',
    '四、预算：约人民币38万元；',
    '五、资金来源：专项维修资金。'
  ]) }
]
const currentSampleLabel = computed(() => (MOCK_SAMPLES[mockSampleIdx.value] || {}).label || '')
function switchSample(idx) {
  const n = _mockSampleCanvases.length
  if (!n) return
  mockSampleIdx.value = ((idx % n) + n) % n
  _mockShotCanvas = _mockSampleCanvases[mockSampleIdx.value]
  mockViewfinderUrl.value = _mockShotCanvas.toDataURL('image/png')
}
function nextSample() { switchSample(mockSampleIdx.value + 1) }
function prevSample() { switchSample(mockSampleIdx.value - 1) }
let _mcTouchX = 0
function onVfTouchStart(e) { _mcTouchX = (e.changedTouches && e.changedTouches[0] ? e.changedTouches[0].clientX : e.clientX) || 0 }
function onVfTouchEnd(e) {
  const x = (e.changedTouches && e.changedTouches[0] ? e.changedTouches[0].clientX : e.clientX) || 0
  const dx = x - _mcTouchX
  if (Math.abs(dx) < 40) return
  if (dx < 0) nextSample(); else prevSample()
}
function openMockCamera() {
  _mockSampleCanvases = MOCK_SAMPLES.map((s) => s.build())
  mockSampleIdx.value = 0
  _mockShotCanvas = _mockSampleCanvases[0]
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
  // demo 判类只看文件名（含「通知」→通知，否则材料）：把当前样张类别写进文件名，
  // 否则统一叫「拍照-N.png」会被判成材料。「会议通知」样张 →「会议通知-N.png」→ 正确判为通知。
  const _lbl = currentSampleLabel.value || '拍照'
  _mockShotFile = new File([blob], _lbl + '-' + (scanItems.value.length + 1) + '.png', { type: 'image/png' })
  await new Promise((r) => setTimeout(r, 260)) // 让"咔嚓"闪一下再切预览
  mockShotUrl.value = shot.toDataURL('image/png')
}
function mockRetake() { mockShotUrl.value = ''; _mockShotFile = null }
// 「使用照片」：加入暂存列表，并回到取景，可继续连拍多张（点右上角完成/× 退出）
function mockUsePhoto() {
  const file = _mockShotFile
  if (!file) return
  addScanItem(file, mockShotUrl.value) // 用拍照 dataURL 作缩略图
  mockShotUrl.value = ''
  _mockShotFile = null
  // 确定后自动切到下一张样张，连拍更顺手（仍可左右滑动手动切换）
  nextSample()
}

// ——— 真·相机（getUserMedia 实时取景 + 抓帧成图）：拍照按钮授权后进此界面，抓帧汇入识别队列 ———
// 需安全上下文（HTTPS 或 localhost）；LAN IP + http 会被浏览器拦截 getUserMedia，此时回退模拟界面。
const realCamVisible = ref(false)
const realCamFlash = ref(false)
const realShotUrl = ref('')       // 拍后预览 dataURL（非空 = 预览确认模式）
const realVideoEl = ref(null)     // <video> 模板引用
let _realStream = null
let _realShotFile = null

function stopRealStream() {
  if (_realStream) { try { _realStream.getTracks().forEach((t) => t.stop()) } catch (e) {} }
  _realStream = null
  const v = realVideoEl.value
  if (v) { try { v.srcObject = null } catch (e) {} }
}
// 把当前视频流接到 <video> 并播放。取景/预览切换时 <video> 会重新挂载，需重新绑流，否则回到取景是黑屏。
async function bindStreamToVideo() {
  if (!_realStream) return
  await nextTick()
  const v = realVideoEl.value
  if (!v) return
  try { v.srcObject = _realStream; await v.play().catch(() => {}) } catch (e) {}
}
// 统一「拍照」入口：先申请真实相机权限（浏览器弹系统授权框）——
// 授权 → 进实时取景；拒绝 / 无摄像头 / 非安全上下文(局域网 http) → 转入模拟样张界面展示。
// 注意：权限框只在“能调用相机”时才会弹；非安全上下文或没有摄像头时浏览器根本不弹，
// 会直接落到模拟——这属正常，给个 toast 说明原因，避免误以为没生效。
async function startCamera() {
  if (scanRecognizing.value) return
  const hasApi = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
  if (!hasApi || !window.isSecureContext) {
    toast({ title: '当前环境无法调用相机（需 HTTPS 或 localhost），已进入模拟拍摄', icon: 'none' })
    openMockCamera()
    return
  }
  try {
    _realStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
      audio: false
    })
    realShotUrl.value = ''
    _realShotFile = null
    realCamVisible.value = true
    await bindStreamToVideo()
  } catch (e) {
    // 权限被拒绝 / 无摄像头 / 被占用 → 转入模拟界面。多数情况浏览器不弹权限，故给提示说明。
    stopRealStream()
    const name = (e && e.name) || ''
    if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError') {
      toast({ title: '未检测到摄像头，已进入模拟拍摄', icon: 'none' })
    } else if (name === 'NotReadableError' || name === 'TrackStartError') {
      toast({ title: '摄像头被其他程序占用，已进入模拟拍摄', icon: 'none' })
    } else if (name === 'NotAllowedError' || name === 'SecurityError') {
      // 曾点过“阻止”后 Chrome 会记住、不再弹框直接拒绝——提示如何解除
      toast({ title: '相机权限被禁用，点地址栏相机图标改为“允许”后重试；已进入模拟', icon: 'none', duration: 3000 })
    } else {
      toast({ title: '相机打开失败，已进入模拟拍摄', icon: 'none' })
    }
    openMockCamera()
  }
}
function closeRealCamera() {
  stopRealStream()
  realCamVisible.value = false
  realShotUrl.value = ''
  _realShotFile = null
}
// 按快门：抓当前视频帧到 canvas → 预览确认（不直接识别，和模拟/上传一致）
async function realShoot() {
  const v = realVideoEl.value
  if (!v || !v.videoWidth) return
  realCamFlash.value = true
  setTimeout(() => { realCamFlash.value = false }, 180)
  const cv = document.createElement('canvas')
  cv.width = v.videoWidth
  cv.height = v.videoHeight
  cv.getContext('2d').drawImage(v, 0, 0, cv.width, cv.height)
  const blob = await new Promise((r) => cv.toBlob(r, 'image/jpeg', 0.92))
  _realShotFile = new File([blob], '相机-' + (scanItems.value.length + 1) + '.jpg', { type: 'image/jpeg' })
  await new Promise((r) => setTimeout(r, 220)) // 让"咔嚓"闪一下再切预览
  realShotUrl.value = cv.toDataURL('image/jpeg', 0.92)
}
function realRetake() { realShotUrl.value = ''; _realShotFile = null; bindStreamToVideo() }
// 「确定」：加入暂存并回到取景，可继续连拍多张（回取景需重绑视频流）
function realUsePhoto() {
  const file = _realShotFile
  if (!file) return
  addScanItem(file, realShotUrl.value)
  realShotUrl.value = ''
  _realShotFile = null
  bindStreamToVideo()
}
// 离开页面/组件卸载时务必释放摄像头
onUnmounted(() => { stopRealStream() })

// AI 识别完成结果卡（自定义精美弹层，替代通用 showModal）
const scanResultCard = ref(null)
function closeScanResult() { scanResultCard.value = null }
function confirmScanResult(overwrite) {
  const c = scanResultCard.value
  if (!c) return
  let filled = false
  if (c.mode === 'multi-notice') {
    // 用户从多份通知中选定一份 → 覆盖填入该份
    const src = (c.selectedNotice != null && c.selectedNotice >= 0) ? c.noticeOptions[c.selectedNotice] : null
    if (src) { applyNoticeFields(src, true); filled = true }
  } else if (c.mode === 'notice') {
    // 空字段总是填入；有冲突的字段仅当用户选择「覆盖」时才改写
    applyNoticeFields(c.res, !!overwrite); filled = true
  }
  if (c.materials && c.materials.length) {
    c.materials.forEach(addScannedMaterialFromInfo)
    toast({ title: '已加入 ' + c.materials.length + ' 份会议材料', icon: 'none' })
  } else if (filled) {
    toast({ title: '已自动填写，请核对', icon: 'none' })
  }
  scanResultCard.value = null
}
// 多份通知的「手动填写」：不填通知字段，但仍把材料加上
function manualFillScanResult() {
  const c = scanResultCard.value
  if (!c) return
  if (c.materials && c.materials.length) {
    c.materials.forEach(addScannedMaterialFromInfo)
    toast({ title: '已加入 ' + c.materials.length + ' 份会议材料，会议信息请手动填写', icon: 'none' })
  }
  createTab.value = 'manual'   // 识别完成 → 切回手动填写
  scanResultCard.value = null
}
// 结果卡次按钮分流：多份通知→手动填写（加材料）；冲突→保留原信息（填空+加材料）；否则→取消
function onScanGhost() {
  const c = scanResultCard.value
  if (!c) return
  if (c.mode === 'multi-notice') { manualFillScanResult(); return }
  if (c.needOverwriteAsk) { confirmScanResult(false); return }
  closeScanResult()
}
// 通知去重：过滤全空、按关键字段签名去重
function noticeSig(o) {
  return [o.title || '', o.meetingDate || '', o.meetingTime || '', o.location || '', (o.topics || []).join('｜')].join('|')
}
function dedupeNotices(arr) {
  const seen = new Set(), out = []
  for (const o of (arr || [])) {
    const sig = noticeSig(o)
    if (sig === '||||') continue
    if (seen.has(sig)) continue
    seen.add(sig); out.push(o)
  }
  return out
}

// 会议通知字段：与当前表单比对，返回冲突项（两边都有值且不同）
function noticeConflicts(res) {
  const out = []
  const cmp = (label, cur, nv) => {
    const a = (cur == null ? '' : String(cur)).trim()
    const b = (nv == null ? '' : String(nv)).trim()
    if (a && b && a !== b) out.push({ label, cur: a, nv: b })
  }
  // 仅"用户真正填过"的字段才参与冲突比对（等于默认占位值的视为未填，不冲突）
  cmp('会议名称', isFieldUserSet('title') ? createForm.title : '', res.title)
  cmp('日期', isFieldUserSet('meetingDate') ? createForm.meetingDate : '', res.meetingDate)
  cmp('时间', isFieldUserSet('meetingTime') ? createForm.meetingTime : '', res.meetingTime)
  cmp('地点', isFieldUserSet('location') ? createForm.location : '', res.location)
  const curT = (createForm.topics || []).map((t) => t.title).join('｜')
  const newT = (Array.isArray(res.topics) ? res.topics : []).map((t) => String(t)).join('｜')
  if (curT && newT && curT !== newT) out.push({ label: '议题', cur: curT, nv: newT })
  return out
}

// 应用通知字段：未填(空或仍是默认占位)的总是填；用户已填且冲突的字段仅 overwrite 时才覆盖
function applyNoticeFields(res, overwrite) {
  let changed = false
  if (res.title && (!isFieldUserSet('title') || overwrite)) { createForm.title = res.title; changed = true }
  if (res.meetingDate && (!isFieldUserSet('meetingDate') || overwrite)) { createForm.meetingDate = res.meetingDate; changed = true }
  if (res.meetingTime && (!isFieldUserSet('meetingTime') || overwrite)) { createForm.meetingTime = res.meetingTime; changed = true }
  if (res.location && (!isFieldUserSet('location') || overwrite)) { createForm.location = res.location; syncLocationPreset(res.location); changed = true }
  if (Array.isArray(res.topics) && res.topics.length && (!(createForm.topics && createForm.topics.length) || overwrite)) {
    createForm.topics = res.topics.map(normalizeRecognizedTopic)
    changed = true
  }
  if (changed) docPrefilled.value = true
  return changed
}

function normalizeRecognizedTopic(raw) {
  let title = String(raw || '').trim()
  let type = 'decision'
  const typed = title.match(/^\s*(notice|discussion|decision)\s*[|｜:]\s*(.+)$/i)
  if (typed) {
    type = typed[1].toLowerCase()
    title = typed[2].trim()
  } else if (/通知|通报|知悉|传达/.test(title)) {
    type = 'notice'
  } else if (/讨论|研讨|征求意见|意见汇总/.test(title) && !/表决|审议通过|投票/.test(title)) {
    type = 'discussion'
  }
  return {
    title,
    type,
    decisionType: type === 'decision' ? 'simple' : 'none',
    options: []
  }
}

// 多文件识别结果 → 组装结果卡：识别为通知/材料、已识别/待补填字段、耗时+token
function handleMultiScanResult(res) {
  if (!res) { toast({ title: '识别未完成，请重试或手动填写', icon: 'none' }); return }
  const filesArr = Array.isArray(res.files) ? res.files : []
  const hasPrefill = res.available && !!(res.title || res.meetingDate || res.meetingTime || res.location || (Array.isArray(res.topics) && res.topics.length))
  const materials = hasPrefill
    ? filesArr.filter((f) => f.category === 'material' && f.fileUrl)
    : filesArr.filter((f) => f.fileUrl)
  const seconds = scanSec.value || 0
  const tokens = res.tokens || 0

  // 全部是材料（没抽到通知信息）：默认添加
  if (!hasPrefill) {
    if (!materials.length) { toast({ title: '请手动填写会议信息', icon: 'none' }); return }
    scanResultCard.value = {
      mode: 'material', materialCount: materials.length,
      missingRequired: [], conflictNote: '', conflicts: [],
      needOverwriteAsk: false, primaryLabel: '加入材料', ghostLabel: '取消',
      seconds, tokens, res, materials
    }
    return
  }

  // 识别到多份"不同"的会议通知 → 让用户选一份填入（或手动填写）；材料仍会添加
  const noticeOpts = dedupeNotices(res.notices)
  if (noticeOpts.length > 1) {
    scanResultCard.value = {
      mode: 'multi-notice',
      materialCount: materials.length,
      noticeOptions: noticeOpts,
      selectedNotice: 0,
      missingRequired: [], conflictNote: '', conflicts: [],
      needOverwriteAsk: false,
      primaryLabel: '填入所选', ghostLabel: '手动填写',
      seconds, tokens, res, materials
    }
    return
  }

  // 识别为会议通知：冲突 = 新识别值与「已填写」的不同 → 才问覆盖；否则空字段直填、材料照加
  const conflicts = noticeConflicts(res)
  const hasConf = conflicts.length > 0
  const REQUIRED = [
    { label: '标题', has: !!(res.title && res.title.trim()) },
    { label: '议题', has: !!(Array.isArray(res.topics) && res.topics.length) }
  ]
  scanResultCard.value = {
    mode: 'notice',
    materialCount: materials.length,
    missingRequired: REQUIRED.filter((f) => !f.has).map((f) => f.label),
    conflictNote: (res.conflictNote || '').trim(),
    conflicts,
    needOverwriteAsk: hasConf,
    primaryLabel: hasConf ? '覆盖并填入' : '确认填入',
    ghostLabel: hasConf ? '保留原信息' : '取消',
    seconds, tokens, res, materials
  }
}
// 单份通知识别结果 → 预览行（名称/时间/地点/议题），让用户在弹窗里一眼核对
const scanNoticeRows = computed(() => {
  const c = scanResultCard.value
  if (!c || c.mode !== 'notice') return []
  const r = c.res || {}
  const topics = Array.isArray(r.topics) ? r.topics.map((t) => String(t).trim()).filter(Boolean) : []
  const dt = formatScanDateTime(r.meetingDate, r.meetingTime)
  return [
    { label: '名称', value: (r.title || '').trim(), miss: !(r.title && r.title.trim()) },
    { label: '时间', value: dt, miss: !dt },
    { label: '地点', value: (r.location || '').trim(), miss: !(r.location && r.location.trim()) },
    { label: '议题', value: topics.length ? (topics.length + ' 项') : '', miss: !topics.length, topics }
  ]
})
// 识别到的日期时间 → 友好显示：2026-07-15 14:00 → 7月15日 14:00
function formatScanDateTime(d, t) {
  const date = (d || '').trim(), time = (t || '').trim()
  const m = date.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/)
  const ds = m ? (Number(m[2]) + '月' + Number(m[3]) + '日') : date
  return [ds, time].filter(Boolean).join(' ')
}
// 把某个已落库的文件加入待挂载材料（去重：同 url 不重复加）
function addScannedMaterialFromInfo(f) {
  if (!f || !f.fileUrl) return
  if (pendingMaterials.value.some((m) => m.url === f.fileUrl)) return
  pendingMaterials.value = pendingMaterials.value.concat([{
    url: f.fileUrl, fileName: f.fileName || '识别文件', fileType: f.fileType || '',
    fileSize: f.fileSize || 0, sizeText: humanSize(f.fileSize || 0)
  }])
}
const docProgress = ref(0)
let docProgTimer = null
// 模拟进度：后端一次性返回拿不到真实百分比，定时器渐进逼近 90%（越近越慢），完成时跳 100%。
// 节奏放慢到约 12-13s 到 90%，与后端识别耗时(约 10-15s)对齐，避免前段冲太快、后段干等。
function startDocProgress() {
  docProgress.value = 0
  clearInterval(docProgTimer)
  docProgTimer = setInterval(() => {
    const p = docProgress.value
    if (p >= 90) return
    const step = p < 60 ? 5 : p < 80 ? 2 : 1
    docProgress.value = Math.min(90, p + step)
  }, 400)
}
function stopDocProgress() {
  clearInterval(docProgTimer)
  docProgTimer = null
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
  clearFieldError('location')
}

// 地点行点击：原生 action sheet 选常用地点 / 其他手填 / 从地图选点（学微信/系统的底部选择，比内嵌下拉更简洁）
async function openLocPicker() {
  clearFieldError('location')
  const items = [...commonLocations, '其他地点（手动填写）', '从地图选点']
  const res = await showActionSheet({ itemList: items })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  const i = res.tapIndex
  if (i < commonLocations.length) {
    locationPreset.value = commonLocations[i]
    createForm.location = commonLocations[i]
  } else if (i === commonLocations.length) {
    locationPreset.value = '__other__'
    createForm.location = ''
  } else {
    pickLocationOnMap()
  }
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
  topicDraft.content = ''
  topicDialogOpen.value = true
}

function openEditTopic(idx) {
  const t = createForm.topics[idx]
  topicEditIdx.value = idx
  topicDraft.title = t.title || ''
  topicDraft.type = t.type || 'discussion'
  topicDraft.decisionType = t.decisionType || 'none'
  topicDraft.options = (t.options || []).map(function (o) { return { id: o.id, label: o.label } })
  topicDraft.content = t.content || ''
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
    options: (topicDraft.options || []).map(function (o) { return { id: o.id, label: o.label } }),
    content: topicDraft.type === 'notice' ? (topicDraft.content || '').trim() : ''
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
  // 自动收编：输入框里还有一条没点「确定」的议题（真机上用户常打完直接点「生成通知」）→ 先帮加进列表，
  // 不让它凭空丢掉；若这条本身不合规（如多选一选项不足）则中止提交，addTopicFromInput 已 toast 原因。
  if (createTab.value === 'manual' && topicInput.value.trim()) {
    if (!addTopicFromInput()) return
  }
  // 议题：逐条添加在 createForm.topics（过滤空标题）
  var topics = (form.topics || []).filter(function (t) { return t.title && t.title.trim() })
  // 必填校验：会议名称 / 会议地点 / 会议议题。缺失 → 弹卡片列出，确认后亮红框
  fieldErrors.title = false; fieldErrors.location = false; fieldErrors.topics = false; fieldErrors.meetingDate = false; fieldErrors.meetingTime = false
  const missing = []
  if (!form.title || !form.title.trim()) missing.push('会议名称')
  if (!form.meetingDate || !form.meetingDate.trim()) missing.push('会议日期')
  if (!form.meetingTime || !form.meetingTime.trim()) missing.push('会议时间')
  if (!form.location || !form.location.trim()) missing.push('会议地点')
  if (!topics.length) missing.push('会议议题')
  if (missing.length) {
    await showModal({
      title: '还有内容没填写',
      content: '请先补全以下内容：\n' + missing.map((m) => '· ' + m).join('\n'),
      confirmText: '知道了',
      showCancel: false,
      size: 'large'
    })
    if (missing.includes('会议名称')) fieldErrors.title = true
    if (missing.includes('会议日期')) fieldErrors.meetingDate = true
    if (missing.includes('会议时间')) fieldErrors.meetingTime = true
    if (missing.includes('会议地点')) fieldErrors.location = true
    if (missing.includes('会议议题')) fieldErrors.topics = true
    return
  }
  // 编辑模式：更新本会议（不新建），完成后回到该会议的「会议通知」页
  if (editingMeetingId.value) {
    const id = editingMeetingId.value
    try {
      await api.committeeUpdate(id, {
        title: form.title, meetingDate: form.meetingDate, meetingTime: form.meetingTime,
        location: form.location, description: form.description, topics: topics
      })
      // 居委会见证态若有变，翻转标记（toggle 语义：与载入态不同才切）
      if (form.juweiWitness !== editInitialJuwei.value) {
        try { await api.committeeToggleFlag(id, 'hasMajorIssue') } catch (e) {}
      }
      editingMeetingId.value = null
      createVisible.value = false
      toast({ title: '已保存修改', icon: 'success' })
      const target = '/pages/committee-detail/committee-detail?id=' + id
      const browserUrl = '/committee-detail?id=' + id
      try { await navigateTo(target) } catch (navErr) { console.error('[编辑会议] 软跳 reject：', navErr) }
      setTimeout(() => { if (!document.querySelector('.detail-page')) window.location.href = browserUrl }, 500)
    } catch (e) {
      toast({ title: (e && e.message) || '保存失败', icon: 'none' })
    }
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
    clearDraft()   // 会议已发出，草稿完成使命，清掉首页草稿卡
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
// 返回是否成功加入（供提交时"自动收编未点确定的议题"判断，失败即已 toast 原因）
function addTopicFromInput() {
  const t = topicInput.value.trim()
  if (!t) { toast({ title: '请输入议题内容', icon: 'none' }); return false }
  const type = topicInputType.value
  const decisionType = type === 'decision' ? topicInputDecisionType.value : 'none'
  const options = decisionType === 'multi_choice'
    ? topicInputOptions.value.map(function (o) { return { id: o.id, label: o.label.trim() } }).filter(function (o) { return o.label })
    : []
  if (decisionType === 'multi_choice' && options.length < 2) {
    toast({ title: '多选一表决至少需要两个选项', icon: 'none' })
    return false
  }
  createForm.topics = createForm.topics.concat([{
    title: t,
    type,
    decisionType,
    options,
    content: ''
  }])
  topicInput.value = ''
  if (decisionType === 'multi_choice') topicInputOptions.value = newTopicInputOptions()
  return true
}

function newTopicInputOptions() {
  return [{ id: 1, label: '' }, { id: 2, label: '' }]
}

function pickTopicInputDecision(type) {
  topicInputDecisionType.value = type
  if (type === 'multi_choice' && topicInputOptions.value.length < 2) topicInputOptions.value = newTopicInputOptions()
}

function addTopicInputOption() {
  const list = topicInputOptions.value
  const id = list.length ? Math.max.apply(null, list.map(function (o) { return o.id })) + 1 : 1
  topicInputOptions.value = list.concat([{ id, label: '' }])
}

function removeTopicInputOption(index) {
  if (topicInputOptions.value.length <= 2) return
  topicInputOptions.value = topicInputOptions.value.filter(function (_, i) { return i !== index })
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
  /* 底部留空：清开固定 TabBar(100rpx) 再留一点缝隙；更多功能靠 margin-top:auto 贴底 */
  padding-bottom: calc(132rpx + env(safe-area-inset-bottom));
  display: flex; flex-direction: column; box-sizing: border-box;
}
/* 顶栏 */
.hd { display: flex; align-items: flex-end; justify-content: space-between; padding: env(safe-area-inset-top) 32rpx 10rpx; background: var(--c-primary-dark); }
.hd-left { display: flex; flex-direction: column; padding-top: 4rpx; }
.hd-title { font-size: 42rpx; font-weight: 700; color: #fff; line-height: 1.25; }
.hd-sub { font-size: 28rpx; color: #fff; margin-top: 4rpx; line-height: 1.3; }
.hd-bell { position: relative; padding: 8rpx; align-self: center; }
.hd-bell-ico { font-size: 52rpx; }
.hd-badge { position: absolute; top: -2rpx; right: -6rpx; min-width: 34rpx; height: 34rpx; padding: 0 8rpx; background: var(--c-danger); color: #fff; font-size: 28rpx; border-radius: 17rpx; line-height: 34rpx; text-align: center; }
/* 当前会议主卡片 */
.meet-card { margin: 14rpx 24rpx 14rpx; background: var(--c-bg-card); border-radius: 22rpx; padding: 18rpx 26rpx 18rpx; box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.05); }
.meet-tag { font-size: 30rpx; color: var(--c-primary-dark); font-weight: 600; }
.meet-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }
.meet-title { flex: 1; min-width: 0; display: block; font-size: 33rpx; font-weight: 700; color: var(--c-text-strong); margin-top: 0; line-height: 1.35; word-break: break-word; }
.meet-status { flex-shrink: 0; margin-top: 4rpx; padding: 6rpx 14rpx; border-radius: 999rpx; font-size: 24rpx; font-weight: 700; line-height: 1.25; }
.meet-status.preparing { color: #9A5A00; background: #FFF4E5; border: 2rpx solid #F2C786; }
.meet-status.ongoing { color: #0F766E; background: #E7F6F3; border: 2rpx solid #B9E4DC; }
.meet-status.ended { color: #5F6B7A; background: #EEF1F4; border: 2rpx solid #D9DEE5; }
.meet-info { display: flex; align-items: center; gap: 14rpx; margin-top: 8rpx; padding: 10rpx 16rpx; border-radius: 16rpx; background: #F7F9FA; border: 2rpx solid #EEF1F3; }
.meet-info-item { min-width: 0; font-size: 27rpx; color: var(--c-text-mid); line-height: 1.35; }
.meet-info-item.location { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meet-info-sep { flex-shrink: 0; width: 2rpx; height: 28rpx; background: #DDE2E6; }
.meet-meta { display: block; font-size: 30rpx; color: var(--c-text-mid); margin-top: 16rpx; }
/* 三步进度 */
.steps { display: flex; align-items: flex-start; justify-content: space-between; margin: 14rpx 8rpx 10rpx; }
.step { display: flex; flex-direction: column; align-items: center; width: 120rpx; }
.step-dot { width: 42rpx; height: 42rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 700; background: #E3E5E9; color: var(--c-text-weak); }
.step-label { font-size: 22rpx; color: var(--c-text-weak); margin-top: 6rpx; }
.step.done .step-dot { background: var(--c-primary-soft); color: var(--c-primary-dark); }
.step.done .step-label { color: var(--c-text-mid); }
.step.active .step-dot { background: var(--c-primary-dark); color: #fff; }
.step.active .step-label { color: var(--c-text-strong); font-weight: 700; }
.step-line { flex: 1; height: 6rpx; border-radius: 3rpx; margin-top: 21rpx; }
.step-line.done { background: var(--c-primary); }
.step-line.todo { background: #E3E5E9; }
/* 大按钮（方案④ 浅橙卡包实心钮：外层浅橙框 + 内层深橙实心白字）*/
.big-btn { padding: 10rpx; border-radius: 26rpx; background: var(--c-primary-soft); margin-top: 4rpx; box-sizing: border-box; box-shadow: 0 12rpx 84rpx 12rpx rgba(232, 140, 20, 0.26); }
.big-btn-inner { display: flex; align-items: center; justify-content: center; height: 88rpx; border-radius: 16rpx; background: var(--c-primary); }
.big-btn:active .big-btn-inner { background: var(--c-primary-strong); }
.big-btn-ico { font-size: 36rpx; margin-right: 12rpx; }
.big-btn-text { font-size: 33rpx; font-weight: 700; color: #fff; }
/* 去开会主按钮：缩窄并居中（比卡片按钮收得更多，两者看起来差不多宽） */
.go-meeting { margin: auto auto 16rpx; width: 84%; }
/* 有草稿时本按钮退为次要：整体缩小、收窄、弱化光晕，把视觉重心让给草稿卡的「继续通知」 */
.big-btn.minor { width: 60%; padding: 12rpx; box-shadow: 0 6rpx 30rpx 4rpx rgba(232, 140, 20, 0.16); }
.big-btn.minor .big-btn-inner { height: 84rpx; }
.big-btn.minor .big-btn-ico { font-size: 34rpx; margin-right: 10rpx; }
.big-btn.minor .big-btn-text { font-size: 34rpx; }
/* 卡片内"去开会"：略收窄并居中；光晕收敛（大弥散光晕留给底部灰底上的独立按钮，白卡里会外溢显脏） */
.meet-card .big-btn { width: 71%; margin-left: auto; margin-right: auto; box-shadow: 0 6rpx 22rpx rgba(232, 140, 20, 0.18); }
/* 删除会议（测试用，弱化） */
.meet-del { text-align: center; color: var(--c-danger); font-size: 27rpx; margin-top: 10rpx; padding: 4rpx; }
.meet-del:active { opacity: 0.6; }

/* 待发送草稿卡：放大成首页主角，橙调、看得见的"没写完的会议"，底部整行大按钮=继续通知 */
.draft-card { width: 94%; margin: 8rpx auto 20rpx; box-sizing: border-box; background: var(--c-bg-card, #fff); border: 2rpx solid rgba(232,140,20,0.4); border-left: 14rpx solid var(--c-primary); border-radius: 28rpx; padding: 30rpx 32rpx 34rpx; box-shadow: 0 10rpx 34rpx rgba(232,140,20,0.18); }
.draft-card-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.draft-badge { font-size: 30rpx; font-weight: 700; color: var(--c-primary-strong, #c96a12); background: var(--c-primary-soft, #fdf0e0); padding: 8rpx 20rpx; border-radius: 999rpx; }
.draft-discard { font-size: 28rpx; color: #9aa0a6; padding: 8rpx 10rpx; }
.draft-discard:active { color: var(--c-danger); }
.draft-title { font-size: 46rpx; font-weight: 700; color: #1f2329; line-height: 1.3; word-break: break-all; }
.draft-summary { font-size: 30rpx; color: #6b7075; margin-top: 10rpx; }
.draft-mat { font-size: 28rpx; color: #6b7075; margin-top: 10rpx; }
.draft-continue { width: 80%; margin: 26rpx auto 0; height: 104rpx; border: none; border-radius: 24rpx; background: var(--c-primary); color: #fff; font-size: 42rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; box-shadow: 0 8rpx 26rpx rgba(232,140,20,0.28); }
/* 页面底部「发起其他会议」：低频入口，虚线灰低调样式，与主流程按钮拉开视觉层级 */
.create-misc-entry { width: 70%; margin: 30rpx auto 8rpx; padding: 22rpx 0; text-align: center; color: #8A8F98; font-size: 30rpx; border: 2rpx dashed #D4D8DE; border-radius: 16rpx; background: #FBFBFC; cursor: pointer; }
.create-misc-entry:active { background: #F2F3F5; color: #666; }
.draft-continue:active { background: var(--c-primary-strong); transform: scale(0.99); }
.draft-continue .btn-arrow { margin-left: 6rpx; font-size: 44rpx; }
/* 空闲态 */
.idle { margin: 64rpx 24rpx 0; display: flex; flex-direction: column; align-items: center; }
.idle-emoji { font-size: 104rpx; margin-bottom: 24rpx; }
.idle-hint { font-size: 38rpx; color: var(--c-text-mid); margin-bottom: 8rpx; }
.idle-sub { font-size: 30rpx; color: var(--c-text-weak); margin-top: 6rpx; }
/* 「更多功能」三格已删（0709）：接待/培训入口移入计划卡横栏 */

/* 综合评分小字（占位分数）：贴近顶栏、紧凑 */
.score-line { display: flex; align-items: center; gap: 6rpx; margin: 6rpx 28rpx 2rpx; font-size: 26rpx; color: var(--c-text-mid); }
.score-ico { font-size: 30rpx; }
.score-num { font-size: 38rpx; font-weight: 800; margin-left: 4rpx; -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; color: transparent; }
.score-unit { font-size: 24rpx; color: var(--c-text-weak); }

/* 今年会议计划：首页前置总览，竖向时间轴——一条主线贯全年，节点亮灭即进度 */
.plan-card { margin: 0 24rpx 22rpx; background: var(--c-bg-card); border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); overflow: hidden; }
.plan-stack { display: flex; flex-direction: column; gap: 14rpx; margin: 0 24rpx 20rpx; }
.plan-stack.compact { gap: 12rpx; }
/* 首页有会议卡时：待办事项 + 履职年历整体缩小一档，与已缩小的会议卡协调 */
.plan-stack.has-meeting { gap: 18rpx; }
.plan-stack.has-meeting .plan-switch-card { padding: 6rpx; }
.plan-stack.has-meeting .plan-tab { font-size: 28rpx; padding: 6rpx 0; }
.plan-stack.has-meeting .yc-list.plan-todo-card { padding: 16rpx 24rpx 18rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-list-head { font-size: 30rpx; padding-bottom: 4rpx; }
.plan-stack.has-meeting .yc-list-count { font-size: 24rpx; padding: 4rpx 13rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-item { gap: 12rpx; padding: 8rpx 4rpx; }
/* 接待/培训列表行：不吃开会档的极限压缩，保留舒适行高 */
.plan-stack.has-meeting .plan-todo-card .yc-item.todo-plain { padding: 20rpx 4rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-item-title { font-size: 31rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-item-sub { font-size: 24rpx; margin-top: 4rpx; }
.plan-stack.has-meeting .plan-todo-card .plan-badge { min-width: 128rpx; font-size: 30rpx; padding: 15rpx 22rpx; }
.plan-stack.has-meeting .plan-todo-card .yc-item.todo-plain .plan-badge { min-width: 104rpx; font-size: 25rpx; padding: 12rpx 16rpx; }
.plan-stack.has-meeting .cal-fold { padding: 14rpx 24rpx; }
.plan-stack.has-meeting .cal-fold-title { font-size: 29rpx; }
.plan-stack.has-meeting .cal-fold-act { font-size: 26rpx; }
.plan-stack.has-meeting .plan-title { font-size: 30rpx; }
.plan-stack.has-meeting .status-legend { font-size: 19rpx; }
.plan-stack.has-meeting .yc-period-grid { gap: 9rpx; padding: 8rpx 14rpx 12rpx; }
.plan-stack.has-meeting .yc-cell { padding: 8rpx 0 7rpx; }
.plan-stack.has-meeting .yc-cell.pair-cell .yc-m { font-size: 27rpx; }
.plan-stack.has-meeting .yc-cell.pair-cell .yc-s { font-size: 20rpx; }
.plan-switch-card, .plan-calendar-card, .plan-todo-card { background: var(--c-bg-card); border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); box-sizing: border-box; overflow: hidden; }
.plan-switch-card { padding: 10rpx; order: 0; }
/* 首页重排：待办事项（主操作）紧跟分段栏，年历（总览）下沉 */
.plan-todo-card { margin: 0; order: 1; }
.plan-calendar-card { padding-top: 0; order: 3; }
/* 有会议时：履职年历折叠头（点开才展开），排在待办和年历之间 */
.cal-fold { order: 2; display: flex; align-items: center; justify-content: space-between; padding: 24rpx 26rpx; background: var(--c-bg-card); border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); cursor: pointer; }
.cal-fold:active { background: #FAFBFC; }
.cal-fold-title { font-size: 32rpx; font-weight: 700; color: var(--c-text-strong); }
.cal-fold-act { display: flex; align-items: center; gap: 8rpx; font-size: 28rpx; font-weight: 600; color: var(--c-primary-dark); }
.cal-fold-arw { display: inline-block; font-size: 30rpx; line-height: 1; transition: transform .2s; }
.cal-fold-arw.up { transform: rotate(180deg); }
/* 履职年历：分类横栏 + 12月宫格 + 警示条 + 当月清单。
   前缀 yc-（year calendar）：cal- 已被下方日期选择弹窗的小日历占用，同名会被其 7 列网格覆盖 */
.plan-tabs { display: flex; gap: 8rpx; margin: 0; background: #F2F6F7; border-radius: 16rpx; padding: 5rpx; }
.plan-tab { flex: 1; text-align: center; padding: 8rpx 0; font-size: 30rpx; line-height: 1.3; font-weight: 700; color: #52646B; border-radius: 12rpx; cursor: pointer; }
.plan-tab.active { background: #D97706; color: #fff; font-weight: 800; box-shadow: 0 6rpx 16rpx rgba(217,119,6,0.2); }
.plan-tab:active { opacity: 0.75; }
/* 方案A：接待/培训概览三数字（本月/待跟进/年度 · 已开展/待开/过期未开） */
.ov-metrics { display: flex; gap: 18rpx; padding: 26rpx 22rpx 28rpx; }
.ov-metric { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; padding: 24rpx 8rpx; border-radius: 18rpx; background: #F6F7F9; cursor: pointer; }
.ov-metric:active { opacity: 0.8; }
.ov-metric.on { box-shadow: inset 0 0 0 4rpx #D97706; }
.ov-num { font-size: 50rpx; font-weight: 800; color: var(--c-text-strong); line-height: 1; }
.ov-label { font-size: 24rpx; color: var(--c-text-weak); font-weight: 600; }
.plan-title.ov-title { font-size: 36rpx; font-weight: 500; }
.ov-metric.warn { background: #FFF7ED; }
.ov-metric.warn .ov-num { color: #D97706; }
.ov-metric.warn .ov-label { color: #B45309; }
.ov-metric.danger { background: #FFF4F2; }
.ov-metric.danger .ov-num { color: #D83A2E; }
.ov-metric.danger .ov-label { color: #B02A1E; }
.yc-alert-main { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; margin: 8rpx 20rpx 0; background: #FFF4F2; border: 2rpx solid #F0B8B1; border-left: 8rpx solid #D83A2E; border-radius: 16rpx; padding: 15rpx 16rpx 15rpx 16rpx; cursor: pointer; box-shadow: 0 6rpx 18rpx rgba(216,58,46,0.1); }
.yc-alert-main:active { opacity: 0.85; }
.yc-alert-main.warn { background: #FFF7EA; border-color: #F2CFA0; border-left-color: var(--c-primary); box-shadow: 0 8rpx 22rpx rgba(232,140,20,0.13); }
.yc-alert-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.yc-alert-kicker { font-size: 22rpx; font-weight: 800; color: #B02A1E; line-height: 1.2; }
.yc-alert-main.warn .yc-alert-kicker { color: var(--c-primary-dark); }
.yc-alert-title { font-size: 33rpx; font-weight: 800; color: #8F1F17; line-height: 1.2; word-break: break-all; }
.yc-alert-main.warn .yc-alert-title { color: var(--c-primary-strong); }
.yc-alert-sub { font-size: 23rpx; color: #81534F; line-height: 1.25; word-break: break-all; }
.yc-alert-main.warn .yc-alert-sub { color: #8A6428; }
.yc-alert-cta { flex-shrink: 0; align-self: stretch; display: flex; align-items: center; padding: 0 14rpx; border-radius: 12rpx; background: #D83A2E; color: #fff; font-size: 25rpx; font-weight: 800; }
.yc-alert-main.warn .yc-alert-cta { background: var(--c-primary); }
.yc-alert-main.secondary { margin-top: 8rpx; padding: 13rpx 16rpx; border-left-width: 6rpx; box-shadow: none; }
.yc-alert-main.outside { margin: -8rpx 24rpx 20rpx; }
.yc-alert-main.secondary .yc-alert-copy { gap: 2rpx; }
.yc-alert-main.secondary .yc-alert-kicker { font-size: 20rpx; }
.yc-alert-main.secondary .yc-alert-title { font-size: 27rpx; }
.yc-alert-main.secondary .yc-alert-sub { display: none; }
.yc-alert-main.secondary .yc-alert-cta { font-size: 23rpx; padding: 0 12rpx; }
.yc-alert-ok { margin: 8rpx 20rpx 0; display: flex; flex-direction: column; gap: 4rpx; background: var(--c-success-soft); border: 2rpx solid rgba(39,174,96,0.2); border-radius: 16rpx; padding: 14rpx 18rpx; }
.yc-alert-ok .yc-alert-kicker, .yc-alert-ok .yc-alert-title { color: var(--c-success); }
.yc-alert-ok .yc-alert-title { font-size: 30rpx; font-weight: 800; }
.yc-focus-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10rpx; padding: 12rpx 20rpx 4rpx; }
.yc-focus { min-width: 0; min-height: 102rpx; display: flex; flex-direction: column; justify-content: center; gap: 4rpx; padding: 12rpx 12rpx; border-radius: 14rpx; background: #F6F7F9; box-sizing: border-box; cursor: pointer; }
.yc-focus:active { opacity: 0.75; }
.yc-focus-label { font-size: 22rpx; font-weight: 700; color: var(--c-text-weak); line-height: 1.15; }
.yc-focus-title { font-size: 26rpx; font-weight: 800; color: var(--c-text-strong); line-height: 1.2; word-break: break-all; }
.yc-focus-sub { font-size: 21rpx; color: var(--c-text-weak); line-height: 1.2; word-break: break-all; }
.yc-focus.done { background: var(--c-success-soft); }
.yc-focus.done .yc-focus-title, .yc-focus.done .yc-focus-sub { color: var(--c-success); }
.yc-focus.current { background: var(--c-primary-soft); }
.yc-focus.current .yc-focus-title, .yc-focus.current .yc-focus-sub { color: var(--c-primary-dark); }
.yc-focus.overdue { background: #FDECEA; }
.yc-focus.overdue .yc-focus-title, .yc-focus.overdue .yc-focus-sub { color: #B02A1E; }
.yc-year-toggle { display: flex; align-items: center; justify-content: center; gap: 8rpx; margin: 8rpx 20rpx 0; height: 50rpx; border-radius: 12rpx; background: #F6F7F9; color: #176B87; font-size: 24rpx; font-weight: 700; cursor: pointer; }
.yc-year-toggle:active { opacity: 0.75; }
.yc-year-panel { padding-top: 4rpx; }
.yc-period-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10rpx; padding: 10rpx 17rpx 14rpx; }
.yc-action-card { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin: 12rpx 20rpx 2rpx; padding: 18rpx 18rpx 18rpx 20rpx; border-radius: 18rpx; background: #FFF7ED; border: 2rpx solid #FED7AA; box-shadow: 0 8rpx 20rpx rgba(217,119,6,0.1); cursor: pointer; }
.yc-action-card:active { opacity: 0.82; }
.yc-action-card.overdue { background: #FFF4F2; border-color: #F3C6C0; box-shadow: 0 8rpx 20rpx rgba(216,58,46,0.11); }
.yc-action-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5rpx; }
.yc-action-kicker { font-size: 23rpx; color: #B45309; font-weight: 800; line-height: 1.15; }
.yc-action-card.overdue .yc-action-kicker { color: #B02A1E; }
.yc-action-title { font-size: 33rpx; color: var(--c-text-strong); font-weight: 900; line-height: 1.18; word-break: break-all; }
.yc-action-btn { flex-shrink: 0; min-width: 128rpx; height: 62rpx; padding: 0 20rpx; border-radius: 999rpx; display: flex; align-items: center; justify-content: center; background: #D97706; color: #fff; font-size: 28rpx; font-weight: 900; box-shadow: 0 8rpx 16rpx rgba(217,119,6,0.22); box-sizing: border-box; }
.yc-action-card.overdue .yc-action-btn { background: #D83A2E; box-shadow: 0 8rpx 16rpx rgba(216,58,46,0.22); }
.yc-period-card { position: relative; min-width: 0; padding: 7rpx; border-radius: 17rpx; border: 2rpx solid #EEF1F3; background: #F8FAFB; box-sizing: border-box; }
.yc-period-card.done { background: #F6FCF8; border-color: #D7EFDE; }
.yc-period-card.current { background: #F3FAFF; border-color: #B9E2FF; }
.yc-period-card.overdue { background: #FFF4F2; border-color: #F3C6C0; }
.yc-period-card.active { box-shadow: 0 8rpx 18rpx rgba(20,42,58,0.08); }
.yc-pair-months { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7rpx; }
.yc-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10rpx; padding: 12rpx 20rpx 6rpx; }
.yc-grid.compact { grid-template-columns: repeat(3, 1fr); gap: 12rpx; padding: 12rpx 20rpx 14rpx; }
.yc-cell { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2rpx; padding: 10rpx 0 9rpx; border-radius: 14rpx; background: #F8FAFB; border: 2rpx solid #EEF1F3; box-sizing: border-box; cursor: pointer; }
.yc-grid.compact .yc-cell { min-height: 94rpx; gap: 4rpx; padding: 15rpx 0 13rpx; border-radius: 16rpx; }
.yc-cell.pair-cell { min-height: 86rpx; gap: 2rpx; padding: 12rpx 0 10rpx; border-radius: 14rpx; background: rgba(255,255,255,0.66); }
.yc-cell.pair-cell.sel { background: rgba(255,255,255,0.66); }
.yc-cell.sel::after { content: ''; position: absolute; inset: -4rpx; border: 3rpx solid #8B5CF6; border-radius: 18rpx; pointer-events: none; }
.yc-cell.sel::before { content: ''; position: absolute; top: 8rpx; right: 8rpx; width: 10rpx; height: 10rpx; border-radius: 50%; background: #8B5CF6; box-shadow: 0 0 0 4rpx rgba(139,92,246,0.13); pointer-events: none; }
.yc-cell:active { opacity: 0.75; }
.yc-m { font-size: 28rpx; font-weight: 700; color: var(--c-text-strong); line-height: 1.1; }
.yc-s { font-size: 22rpx; color: var(--c-text-weak); line-height: 1.2; }
.yc-grid.compact .yc-m { font-size: 32rpx; }
.yc-grid.compact .yc-s { font-size: 22rpx; }
.yc-cell.pair-cell .yc-m { font-size: 31rpx; }
.yc-cell.pair-cell .yc-s { font-size: 22rpx; }
.yc-cell.done { background: #F0FAF4; border-color: #D7EFDE; }
.yc-cell.done .yc-m, .yc-cell.done .yc-s { color: var(--c-success); }
.yc-cell.current { background: #EAF6FF; border-color: #B9E2FF; }
.yc-cell.current .yc-m, .yc-cell.current .yc-s { color: #0284C7; }
.yc-cell.overdue { background: #FDECEA; border-color: #F3C6C0; }
.yc-cell.overdue .yc-m, .yc-cell.overdue .yc-s { color: #B02A1E; }
.yc-cell.sel { box-shadow: 0 8rpx 18rpx rgba(20,42,58,0.08); }
.yc-corner { position: absolute; top: -10rpx; right: -8rpx; min-width: 34rpx; height: 34rpx; padding: 0 8rpx; box-sizing: border-box; border-radius: 17rpx; background: var(--c-danger); color: #fff; font-size: 22rpx; font-weight: 700; line-height: 34rpx; text-align: center; }
.period-calendar { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8rpx; padding: 10rpx 20rpx 2rpx; }
.period-cell { min-height: 88rpx; display: flex; flex-direction: column; justify-content: center; gap: 2rpx; padding: 10rpx 12rpx; border-radius: 14rpx; background: #F6F7F9; border: 3rpx solid transparent; box-sizing: border-box; cursor: pointer; }
.period-cell.active { border-color: #176B87; }
.period-no { font-size: 20rpx; color: var(--c-text-weak); font-weight: 700; }
.period-month { font-size: 28rpx; color: var(--c-text-strong); font-weight: 800; line-height: 1.15; }
.period-status { font-size: 21rpx; color: var(--c-text-weak); line-height: 1.15; }
.period-cell.done { background: var(--c-success-soft); }
.period-cell.done .period-month, .period-cell.done .period-status { color: var(--c-success); }
.period-cell.current { background: var(--c-primary-soft); }
.period-cell.current .period-month, .period-cell.current .period-status { color: var(--c-primary-dark); }
.period-cell.overdue { background: #FDECEA; }
.period-cell.overdue .period-month, .period-cell.overdue .period-status { color: #B02A1E; }
.yc-list { margin: 0 20rpx 18rpx; padding: 14rpx 16rpx 12rpx; background: #F8FAFB; border-radius: 18rpx; border: 2rpx solid #EEF1F3; }
.yc-list.plan-todo-card { margin: 0; padding: 12rpx 26rpx 18rpx; background: #FFFCF6; border-color: #FBE7CC; border-radius: 22rpx; box-shadow: 0 12rpx 30rpx rgba(199,106,0,0.12); }
.yc-list-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; font-size: 28rpx; font-weight: 800; color: var(--c-text-strong); padding: 0 2rpx 8rpx; }
.plan-todo-card .yc-list-head { font-size: 33rpx; font-weight: 700; padding-bottom: 0; }
.yc-list-count { flex-shrink: 0; padding: 5rpx 15rpx; border-radius: 999rpx; background: #EAF6FF; color: #0284C7; font-size: 27rpx; font-weight: 800; }
/* 待办标题吸睛：图标 + 有待办时徽章实心暖色并缓慢脉动光环 */
.yc-head-title { display: inline-flex; align-items: center; min-width: 0; }
.plan-todo-card .yc-list-count.active { background: var(--c-primary); color: #fff; animation: todoPulse 2.2s ease-in-out infinite; }
@keyframes todoPulse {
  0% { box-shadow: 0 0 0 0 rgba(199,106,0,0.42); }
  70% { box-shadow: 0 0 0 18rpx rgba(199,106,0,0); }
  100% { box-shadow: 0 0 0 0 rgba(199,106,0,0); }
}
@media (prefers-reduced-motion: reduce) { .plan-todo-card .yc-list-count.active { animation: none; } }
.yc-item { display: flex; align-items: center; gap: 14rpx; padding: 13rpx 4rpx; border-top: 2rpx solid #EEF1F3; cursor: pointer; }
.plan-todo-card .yc-item { gap: 16rpx; padding: 6rpx 4rpx; }
.plan-todo-card .yc-item.current,
.plan-todo-card .yc-item.overdue { border-top: none; border-radius: 18rpx; padding: 14rpx 20rpx 16rpx; margin-top: 8rpx; border: 2rpx solid transparent; border-left-width: 10rpx; }
/* 卡片与卡片之间拉开（首卡贴标题保持紧凑）；:first-of-type 会被前面的标题 div 干扰，用相邻兄弟选择器 */
.plan-todo-card .yc-item + .yc-item.current,
.plan-todo-card .yc-item + .yc-item.overdue { margin-top: 20rpx; }
.plan-todo-card .yc-item.current { background: #FFF7ED; border-color: #FED7AA; border-left-color: #D97706; }
.plan-todo-card .yc-item.overdue { background: #FFF4F2; border-color: #F3C6C0; border-left-color: #D83A2E; }
/* 接待/培训待办：条数多，用轻列表（细分隔线，不套会议那种强调橙块），标题弱化、副标题单行省略、按钮收小，避免堆叠拥挤 */
.plan-todo-card .yc-item.todo-plain { padding: 30rpx 4rpx; }
.plan-todo-card .yc-item.todo-plain:first-of-type { border-top: none; }
.plan-todo-card .yc-item.todo-plain .yc-item-title { font-weight: 500; }
.plan-todo-card .yc-item.todo-plain .yc-item-sub { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.plan-todo-card .yc-item.todo-plain .plan-badge { min-width: 124rpx; font-size: 29rpx; padding: 15rpx 20rpx; }
/* 「查看」入口：不是实心操作按钮，而是浅橙描边+箭头，表示「点进去看详情」（跟进选项在详情页里） */
.plan-todo-card .yc-item.todo-plain .plan-badge.view { background: #fff; color: #C2410C; border: 2rpx solid #FED7AA; font-weight: 700; box-shadow: none; }
.plan-todo-card .yc-item.todo-plain .plan-badge.view::after { content: '›'; margin-left: 6rpx; }
/* 培训过期项：列表里加红左条，一眼看出「过期未开」 */
.plan-todo-card .yc-item.todo-plain.todo-overdue { border-left: 6rpx solid #D83A2E; padding-left: 16rpx; }
/* 待办「查看」详情弹窗 */
.td-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; z-index: 200; padding: 40rpx; box-sizing: border-box; }
.td-pop { width: 100%; max-width: 620rpx; background: var(--c-bg-card, #fff); border-radius: 28rpx; padding: 34rpx 32rpx 32rpx; box-sizing: border-box; box-shadow: 0 20rpx 60rpx rgba(0,0,0,0.25); }
.td-pop-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.td-pop-title { font-size: 40rpx; font-weight: 700; color: var(--c-text-strong); line-height: 1.3; }
.td-pop-close { font-size: 48rpx; color: var(--c-text-weak); line-height: 1; padding: 0 4rpx; }
.td-pop-status { display: inline-block; margin-top: 14rpx; padding: 8rpx 20rpx; border-radius: 999rpx; font-size: 27rpx; font-weight: 700; }
.td-pop-status.warn { background: #FFF7ED; color: #B45309; }
.td-pop-status.danger { background: #FFF4F2; color: #B02A1E; }
.td-pop-status.done { background: var(--c-success-soft); color: var(--c-success); }
.td-pop-status.current { background: #EAF6FF; color: #0284C7; }
.td-pop-status.upcoming { background: #EEF0F3; color: #52646B; }
.td-pop-body { margin-top: 24rpx; display: flex; flex-direction: column; gap: 18rpx; }
.td-row { display: flex; gap: 18rpx; font-size: 30rpx; line-height: 1.5; }
.td-row-k { flex-shrink: 0; width: 128rpx; color: var(--c-text-weak); }
.td-row-v { flex: 1; color: var(--c-text-strong); word-break: break-all; }
.td-pop-btn { width: 100%; margin-top: 32rpx; height: 96rpx; border: none; border-radius: 22rpx; background: var(--c-primary); color: #fff; font-size: 36rpx; font-weight: 700; }
.td-pop-btn:active { background: var(--c-primary-strong); }
.yc-item:active { opacity: 0.6; }
.yc-item-ico { flex-shrink: 0; font-size: 32rpx; }
.plan-todo-card .yc-item-ico { font-size: 38rpx; }
.yc-item-info { flex: 1; min-width: 0; }
.yc-item-title { font-size: 28rpx; font-weight: 600; color: var(--c-text-strong); line-height: 1.25; }
.yc-item-sub { font-size: 22rpx; color: var(--c-text-weak); margin-top: 3rpx; line-height: 1.25; }
.plan-todo-card .yc-item-title { font-size: 38rpx; font-weight: 600; }
.plan-todo-card .yc-item-sub { font-size: 28rpx; margin-top: 6rpx; }
.yc-links { display: flex; justify-content: center; gap: 48rpx; padding: 18rpx 0 4rpx; font-size: 26rpx; font-weight: 600; color: var(--c-primary-dark); }
.yc-links span:active { opacity: 0.6; }
.plan-empty { text-align: center; color: var(--c-text-weak); font-size: 26rpx; padding: 24rpx 0 24rpx; }
/* 日历下方反馈区：点选期次后就地展示（已开期记录 / 未到期提前准备）。记录行放宽有呼吸感 */
.yc-period-feedback { margin-top: 20rpx; border-top: 2rpx dashed #EFE7DA; padding-top: 16rpx; }
.ypf-head { font-size: 30rpx; font-weight: 700; color: var(--c-text-strong); padding: 4rpx 6rpx 12rpx; }
.ypf-tip { text-align: center; font-size: 27rpx; color: var(--c-text-weak); padding: 18rpx 0 6rpx; }
.yc-period-feedback .yc-item:first-of-type { border-top: 0; }
.yc-period-feedback .yc-item { padding: 22rpx 6rpx; gap: 18rpx; }
.yc-period-feedback .yc-item-title { font-size: 31rpx; line-height: 1.45; }
.yc-period-feedback .yc-item-sub { font-size: 26rpx; margin-top: 8rpx; line-height: 1.4; }
.yc-period-feedback .plan-badge { font-size: 26rpx; padding: 10rpx 20rpx; }
/* 「查看公示」小按钮：白底橙描边，带 › 引导 */
.yc-period-feedback .plan-badge.ypf-view { background: #fff; color: #C2410C; border: 2rpx solid #FED7AA; font-weight: 700; box-shadow: none; }
.yc-period-feedback .plan-badge.ypf-view::after { content: '›'; margin-left: 6rpx; }
/* 待办卡定位高亮：滚动到位后闪两下橙色提示 */
.plan-todo-card.flash { animation: todoFlash 0.9s ease 2; }
@keyframes todoFlash { 50% { background: #FFF1DC; box-shadow: 0 0 0 4rpx rgba(217,119,6,0.35); } }
.advance-meeting-btn { width: 100%; height: 88rpx; margin-top: 16rpx; border: 0; border-radius: 44rpx; background: #0F766E; color: #fff; font-size: 32rpx; font-weight: 800; line-height: 88rpx; box-shadow: 0 12rpx 24rpx rgba(15,118,110,0.24); }
.advance-meeting-btn::after { border: 0; }
.advance-meeting-btn:active { background: #0B5F59; }
.plan-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; padding: 16rpx 20rpx 2rpx; }
/* 概览（接待/培训）标题与卡片顶部再留出一点距离；仅 compact 态生效，不动开会年历 */
.plan-stack.compact .plan-head { padding-top: 26rpx; }
/* 概览卡与上方待办卡、下方各再拉开一点间距（仅 compact 态）*/
.plan-stack.compact .plan-calendar-card { margin-top: 10rpx; margin-bottom: 17rpx; }
.plan-title-wrap { display: flex; align-items: center; gap: 12rpx; min-width: 0; }
.plan-title { font-size: 34rpx; font-weight: 800; color: var(--c-text-strong); line-height: 1.12; }
.plan-year { padding: 4rpx 12rpx; border-radius: 999rpx; background: #EAF6FF; color: #0284C7; font-size: 22rpx; font-weight: 800; line-height: 1.2; }
.plan-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10rpx; flex-shrink: 0; }
.plan-tip { font-size: 24rpx; color: var(--c-text-weak); }
.plan-tip.link { color: #0284C7; font-weight: 800; font-size: 25rpx; padding: 4rpx 6rpx; }
.status-legend { display: flex; flex-wrap: wrap; align-items: center; gap: 6rpx 16rpx; padding: 0 20rpx 2rpx; color: #6B7B82; font-size: 20rpx; font-weight: 600; }
.status-legend span { display: inline-flex; align-items: center; gap: 6rpx; line-height: 1.2; }
.lg-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #C6CDD1; }
.lg-dot.done { background: var(--c-success); }
.lg-dot.current { background: #0EA5E9; }
.lg-dot.overdue { background: #D83A2E; }
.lg-dot.upcoming { background: #AAB4BA; }
/* 时间轴：左侧 52rpx 轨道列（贯穿细线+节点圆点），右侧内容行。紧凑以免顶下方主按钮 */
.plan-timeline { padding: 2rpx 26rpx 10rpx; }
.tl-i { display: grid; grid-template-columns: 52rpx 1fr; gap: 18rpx; cursor: pointer; }
.tl-i:active { opacity: 0.55; }
.tl-rail { position: relative; display: flex; justify-content: center; }
.tl-rail::before { content: ''; position: absolute; top: 0; bottom: 0; width: 6rpx; background: var(--c-border); border-radius: 3rpx; }
.tl-i:first-child .tl-rail::before { top: 30rpx; }
.tl-i:last-child .tl-rail::before { bottom: calc(100% - 30rpx); }
.tl-node { position: relative; z-index: 1; width: 44rpx; height: 44rpx; margin-top: 8rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 700; color: #fff; background: var(--c-border); font-variant-numeric: tabular-nums; }
.tl-node.done { background: var(--c-success); }
.tl-node.current { background: var(--c-primary); box-shadow: 0 0 0 6rpx var(--c-primary-soft); }
.tl-node.overdue { background: #B02A1E; }
.tl-node.upcoming { background: var(--c-bg-card); border: 5rpx solid var(--c-border); color: var(--c-text-weak); }
.tl-body { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-width: 0; padding: 10rpx 0 16rpx; border-bottom: 2rpx solid #F1F2F4; }
.tl-i:last-child .tl-body { border-bottom: none; padding-bottom: 4rpx; }
.tl-info { min-width: 0; }
.tl-month { font-size: 30rpx; font-weight: 600; color: var(--c-text-strong); line-height: 1.2; }
.tl-sub { font-size: 24rpx; color: var(--c-text-weak); margin-top: 4rpx; line-height: 1.3; }
.tl-i.current .tl-month { color: var(--c-primary-dark); font-weight: 700; }
.tl-i.done .tl-sub { color: var(--c-success); }
.tl-i.overdue .tl-sub { color: #B02A1E; }
.plan-badge { flex-shrink: 0; font-size: 23rpx; font-weight: 700; padding: 7rpx 16rpx; border-radius: 999rpx; line-height: 1.2; }
.plan-todo-card .plan-badge { min-width: 158rpx; text-align: center; font-size: 36rpx; font-weight: 900; padding: 20rpx 30rpx; box-sizing: border-box; }
/* 已开=绿｜待开=橙｜逾期=红｜待排=灰 */
.plan-badge.done     { color: var(--c-success); background: var(--c-success-soft); }
.plan-badge.current  { color: #fff; background: #D97706; box-shadow: 0 10rpx 22rpx rgba(217,119,6,0.34); }
.plan-badge.overdue  { color: #fff; background: #D83A2E; box-shadow: 0 10rpx 22rpx rgba(216,58,46,0.30); }
.plan-badge.upcoming { color: var(--c-text-weak); background: #EEF0F3; }
/* 方案B：待办卡里「去通知/去补开」从小胶囊升级为整行大按钮 + 缓慢呼吸光晕（核心履职动作要一眼看到） */
.plan-todo-card .yc-item.current, .plan-todo-card .yc-item.overdue { flex-direction: column; align-items: stretch; gap: 12rpx; }
.plan-todo-card .yc-item.current .yc-item-title, .plan-todo-card .yc-item.overdue .yc-item-title { font-size: 32rpx; }
.plan-todo-card .yc-item.current .plan-badge, .plan-todo-card .yc-item.overdue .plan-badge {
  width: 70%; margin: 0 auto; box-sizing: border-box;
  text-align: center; font-size: 33rpx; font-weight: 700; padding: 22rpx 0; border-radius: 18rpx; letter-spacing: 2rpx;
}
.plan-todo-card .yc-item.current .plan-badge.current { animation: ctaBreathOrange 2.2s ease-in-out infinite; }
.plan-todo-card .yc-item.overdue .plan-badge.overdue { animation: ctaBreathRed 2.2s ease-in-out infinite; }
@keyframes ctaBreathOrange {
  0%, 100% { box-shadow: 0 10rpx 22rpx rgba(217,119,6,0.35); }
  50% { box-shadow: 0 12rpx 34rpx rgba(217,119,6,0.62), 0 0 0 16rpx rgba(217,119,6,0.22); }
}
@keyframes ctaBreathRed {
  0%, 100% { box-shadow: 0 10rpx 22rpx rgba(216,58,46,0.32); }
  50% { box-shadow: 0 12rpx 34rpx rgba(216,58,46,0.58), 0 0 0 16rpx rgba(216,58,46,0.22); }
}
@media (prefers-reduced-motion: reduce) {
  .plan-todo-card .yc-item.current .plan-badge.current, .plan-todo-card .yc-item.overdue .plan-badge.overdue { animation: none; }
}
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
.create-body { flex: 1; min-height: 0; overflow-y: auto; padding: 14rpx 24rpx 18rpx; box-sizing: border-box; display: flex; flex-direction: column; }
.create-section { background: #fafbfc; border-radius: 16rpx; padding: 16rpx 18rpx; margin-bottom: 31rpx; border: 2rpx solid #f0f0f0; }
.create-section.meeting-info-card { margin-top: 0; margin-bottom: 31rpx; padding: 16rpx 18rpx; }
.meeting-info-card .section-title { margin-bottom: 12rpx; }
.meeting-info-card .form-group { margin-bottom: 14rpx; }
.meeting-info-card .field-caption { display: block; font-size: 26rpx; color: #61656c; font-weight: 600; margin-bottom: 8rpx; line-height: 1.4; }
/* 会议名称 caption 顶替原「会议信息」标题：放大到区块标题字号并居于卡片首行 */
.meeting-info-card .field-caption.caption-as-title { font-size: 32rpx; color: #1f2329; font-weight: 700; margin-bottom: 12rpx; }
/* 时间/地点信息收紧（仅本卡片，不影响议题输入框）：日期时间、地点框都变矮变小 */
.meeting-info-card .form-row { gap: 14rpx; }
.meeting-info-card .picker-field { min-height: 72rpx; font-size: 30rpx; border-radius: 12rpx; padding: 0 18rpx; }
.meeting-info-card .tf-text { font-size: 30rpx; }
.meeting-info-card .loc-row { margin-top: 12rpx; }
.meeting-info-card .loc-input.form-input { height: 72rpx; min-height: 72rpx; font-size: 30rpx; border-radius: 12rpx; }
.loc-input-manual { margin-top: 12rpx; }
.meeting-info-card .loc-map-btn { width: 84rpx; border-radius: 12rpx; }

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
/* 顶部分段切换：手动填写 / 拍照上传（复用首页 plan-tabs 视觉：灰底圆角胶囊 + active 橙底白字） */
.create-tabs { display: flex; gap: 8rpx; background: #F2F6F7; border-radius: 16rpx; padding: 4rpx; margin-bottom: 22rpx; }
.create-tab { flex: 1; display: flex; align-items: center; justify-content: center; padding: 14rpx 0; font-size: 36rpx; line-height: 1.2; font-weight: 700; color: #52646B; border-radius: 12rpx; cursor: pointer; }
.create-tab.active { background: #D97706; color: #fff; box-shadow: 0 6rpx 16rpx rgba(217,119,6,0.2); }
.create-tab:active { opacity: 0.8; }
.ct-ico { font-size: 32rpx; line-height: 1; }
/* 拍照/上传面板 */
.scan-pane { margin-bottom: 12rpx; }
/* 时间/地点设置项列表（iOS 日历式）：标签左、值右、点整行展开选择器 */
.field-list { border: 2rpx solid #ececec; border-radius: 16rpx; overflow: hidden; background: #fff; margin-top: 6rpx; }
.field-line { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 18rpx; border-bottom: 2rpx solid #f2f2f2; }
.field-line:last-child { border-bottom: none; }
.field-line:active { background: #FAFAFA; }
.fl-label { flex-shrink: 0; width: 100rpx; font-size: 26rpx; color: #61656c; font-weight: 700; }
.fl-value { flex: 1; text-align: right; font-size: 28rpx; color: #1f2329; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fl-value.ph { color: #b7bbc0; }
.fl-arrow { flex-shrink: 0; font-size: 28rpx; color: #c4c8cd; line-height: 1; }
.field-line.field-error { background: #FFF4F4; }
.field-line-split { padding: 0; gap: 0; }
.fl-part { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12rpx; padding: 16rpx 18rpx; box-sizing: border-box; }
.fl-part-time { border-left: 2rpx solid #f2f2f2; }
.fl-part .fl-label { width: auto; }
.fl-part .fl-value { text-align: right; }
.field-line-location { padding: 0 12rpx 0 18rpx; gap: 12rpx; }
.fl-loc-main { flex: 1; min-width: 0; display: flex; align-items: center; gap: 16rpx; padding: 16rpx 0; }
/* 「其他地点」内联输入：直接替换本行选项，无边框，视觉贴合原选项栏 */
.fl-label-tap { color: var(--c-primary-dark); }
.fl-inline-input { flex: 1; min-width: 0; padding: 16rpx 0; border: 0; background: transparent; text-align: right; font-size: 28rpx; font-weight: 700; color: #1f2329; outline: none; }
.fl-inline-input::placeholder { color: #b7bbc0; }
.meeting-info-card .field-list { border: 0; border-radius: 0; overflow: visible; background: transparent; display: flex; flex-direction: column; gap: 16rpx; }
.meeting-info-card .field-line { border: 2rpx solid #ececec; border-radius: 16rpx; background: #fff; }
.meeting-info-card .field-line:last-child { border-bottom: 2rpx solid #ececec; }
.meeting-info-card .fl-value.ph { text-align: left; font-size: 26rpx; font-weight: 500; }
.meeting-info-card .fl-inline-input { text-align: left; }
.meeting-info-card .fl-inline-input::placeholder { font-size: 26rpx; font-weight: 500; }
.field-line-location .field-map-btn { width: 72rpx; height: 60rpx; min-height: 60rpx; align-self: center; border-radius: 12rpx; padding: 0; }
.field-map-btn svg { width: 38rpx; height: 38rpx; }
/* 拍照/上传 双卡片（政务风功能入口）：图标圆 + 标题 + 两行说明整合在卡片内 */
.doc-scan-bar { flex-shrink: 0; padding: 16rpx 26rpx 10rpx; background: var(--c-bg-page); border-top: 1rpx solid #ececec; }
/* 展开在顶部入口内时：去掉底部固定栏的边框/灰底，接在入口下方成一体 */
.doc-scan-bar.inline { flex-shrink: initial; background: #FFFDF9; border: 2rpx dashed #F0B978; border-radius: 16rpx; padding: 18rpx 20rpx; }
.ds-cards { display: flex; gap: 18rpx; }
.ds-cards-3 { gap: 12rpx; }
.ds-cards-3 .ds-card { padding: 12rpx 4rpx 10rpx; }
.ds-card { flex: 1; min-width: 0; background: #fff; border: 2rpx solid #eee; border-radius: 20rpx; padding: 20rpx 10rpx 16rpx; display: flex; flex-direction: column; align-items: center; gap: 6rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.05); }
.ds-card:active { background: #FFF8EE; border-color: #FFD79A; }
.ds-card:disabled { opacity: 0.75; }
.ds-ico { width: 58rpx; height: 58rpx; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 30rpx; margin-bottom: 2rpx; }
.ds-ico.or { background: #FFF3E0; }
.ds-ico.bl { background: #EAF2FF; }
.ds-t { font-size: 28rpx; font-weight: 700; color: #1f2329; line-height: 1.3; }
.ds-s { font-size: 22rpx; color: #999; text-align: center; line-height: 1.4; }
.ds-hint { font-size: 24rpx; color: #9a9a9a; text-align: center; margin: 12rpx 2rpx 0; line-height: 1.45; }
.ds-spin { width: 68rpx; height: 68rpx; border-radius: 50%; border: 6rpx solid rgba(168,88,0,0.2); border-top-color: var(--c-primary-dark); box-sizing: border-box; animation: aiSpin 0.7s linear infinite; margin-bottom: 4rpx; }
/* 待识别缩略图预览条：横向排列，可删 */
.ds-preview { display: flex; flex-wrap: wrap; gap: 14rpx; padding: 4rpx 2rpx 16rpx; }
.ds-thumb { position: relative; width: 108rpx; height: 108rpx; border-radius: 14rpx; overflow: hidden; border: 2rpx solid #E7E2D8; background: #fff; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.05); }
.ds-thumb-img { width: 100%; height: 100%; object-fit: cover; display: block; cursor: pointer; }
.ds-thumb-file { cursor: pointer; }
/* 放大角标：提示缩略图可点击展开看清（左下角，避开右上删除×） */
.ds-thumb-zoom { position: absolute; bottom: 0; left: 0; width: 36rpx; height: 36rpx; border-radius: 0 12rpx 0 12rpx; background: rgba(0,0,0,0.5); color: #fff; font-size: 24rpx; line-height: 36rpx; text-align: center; }
.ds-thumb-file { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4rpx; background: #FAF7F1; }
.ds-thumb-ico { font-size: 44rpx; line-height: 1; }
.ds-thumb-ext { font-size: 20rpx; color: #A98; text-transform: uppercase; }
.ds-thumb-del { position: absolute; top: -2rpx; right: -2rpx; width: 38rpx; height: 38rpx; border-radius: 0 14rpx 0 14rpx; background: rgba(0,0,0,0.55); color: #fff; font-size: 30rpx; line-height: 38rpx; text-align: center; }
/* 开始 AI 识别：实心橙大按钮，攒了文件才出现 */
/* 开始 AI 识别：深青绿 + 居中变窄(70%) + 边缘淡淡发光(轻脉冲)，醒目且与橙色协调 */
.ds-recognize { display: block; width: 62%; height: 81rpx; margin: 40rpx auto 4rpx; border: none; border-radius: 16rpx; background: #0E8A7B; color: #fff; font-size: 29rpx; font-weight: 700; box-shadow: 0 6rpx 16rpx rgba(12,90,80,0.30), 0 0 14rpx rgba(30,180,155,0.5); animation: dsGlow 1.9s ease-in-out infinite; }
.ds-recognize:active { background: #0B6F63; }
.ds-recognize:disabled { opacity: 0.72; animation: none; }
@keyframes dsGlow {
  0%, 100% { box-shadow: 0 6rpx 16rpx rgba(12,90,80,0.30), 0 0 12rpx rgba(30,180,155,0.38); }
  50% { box-shadow: 0 6rpx 16rpx rgba(12,90,80,0.30), 0 0 26rpx rgba(40,200,170,0.82); }
}
/* token 消耗：低调小字，识别完成后显示 */

/* 模拟系统相机权限弹窗（仿系统样式：居中白盒 + 细线分隔双按钮） */

/* 识别中：居中小弹窗（文档扫描动画 + 三步流程 + 动态文案） */
.scan-pop-mask { position: fixed; inset: 0; z-index: 3050; background: rgba(10, 8, 4, 0.42); backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; }
.scan-pop { position: relative; width: 640rpx; max-width: 92%; background: linear-gradient(180deg, #FFFDF9 0%, #fff 30%); border: 1rpx solid rgba(255, 168, 0, 0.25); border-radius: 30rpx; padding: 48rpx 44rpx 38rpx; display: flex; flex-direction: column; align-items: center; box-shadow: 0 20rpx 60rpx rgba(120, 70, 0, 0.28), 0 0 0 6rpx rgba(255, 168, 0, 0.06); box-sizing: border-box; }
.sp-close { position: absolute; top: 10rpx; right: 16rpx; width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; font-size: 48rpx; line-height: 1; color: #B0A48E; z-index: 2; }
.sp-close:active { color: #7A6E58; }
/* 圆环进度：单一焦点，中心大百分比 */
.sp-ring { position: relative; width: 210rpx; height: 210rpx; margin-bottom: 32rpx; }
.sp-ring-svg { width: 100%; height: 100%; transform-origin: 50% 50%; animation: spSpin 2.6s linear infinite; }
.sp-ring-track { fill: none; stroke: #F1EADB; stroke-width: 8.5; }
.sp-ring-fill { fill: none; stroke: url(#spGrad); stroke-width: 8.5; stroke-linecap: round; stroke-dasharray: 264; transition: stroke-dashoffset .45s ease; }
@keyframes spSpin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .sp-ring-svg { animation: none; transform: rotate(-90deg); } }
.sp-ring-center { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; }
.sp-ring-val { display: flex; align-items: baseline; }
.sp-ring-num { font-size: 76rpx; font-weight: 800; color: #C76A00; font-variant-numeric: tabular-nums; line-height: 1; }
.sp-ring-pct { font-size: 32rpx; font-weight: 800; color: #C76A00; margin-left: 3rpx; }
@keyframes spRingGlow { 0%, 100% { filter: drop-shadow(0 0 0 rgba(255,168,0,0)); } 50% { filter: drop-shadow(0 0 5rpx rgba(255,168,0,0.55)); } }
.sp-title { font-size: 42rpx; font-weight: 700; color: #1f2329; letter-spacing: 1rpx; }
.sp-say { font-size: 32rpx; color: #B07400; margin: 14rpx 0 4rpx; min-height: 44rpx; }
.sp-foot { font-size: 23rpx; color: #C0B29A; margin-top: 26rpx; letter-spacing: 0.5rpx; }

/* AI 识别完成：精美结果卡 */
.scan-result-mask { position: fixed; inset: 0; z-index: 3060; background: rgba(10, 8, 4, 0.42); backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; padding: 40rpx; box-sizing: border-box; }
.scan-result { position: relative; width: 640rpx; max-width: 92%; background: linear-gradient(180deg, #FFFDF9 0%, #fff 24%); border: 1rpx solid rgba(255, 168, 0, 0.22); border-radius: 30rpx; padding: 40rpx 40rpx 34rpx; box-shadow: 0 20rpx 60rpx rgba(120, 70, 0, 0.28), 0 0 0 6rpx rgba(255, 168, 0, 0.05); box-sizing: border-box; display: flex; flex-direction: column; align-items: center; }
.sr-hero { display: flex; flex-direction: row; align-items: center; justify-content: center; gap: 18rpx; width: 100%; margin-bottom: 26rpx; }
.sr-hero-badge { flex-shrink: 0; width: 70rpx; height: 70rpx; border-radius: 50%; background: #ECF6F0; display: flex; align-items: center; justify-content: center; box-shadow: 0 0 0 6rpx rgba(46, 139, 87, 0.10); }
.sr-hero-badge .sr-check { font-size: 42rpx; }
.sr-hero-title { font-size: 44rpx; font-weight: 800; color: #1f2329; letter-spacing: 1rpx; }
.sr-check { color: #1E7E4E; font-size: 40rpx; font-weight: 800; line-height: 1; }
.sr-body { width: 100%; display: flex; flex-direction: column; gap: 16rpx; }
.sr-card { background: #fff; border: 1rpx solid #F1E7D6; border-radius: 18rpx; padding: 20rpx 22rpx; box-shadow: 0 4rpx 14rpx rgba(120, 70, 0, 0.06); }
/* 结果条：图标 chip + 标题 + 说明 */
.sr-card-main { display: flex; align-items: center; gap: 18rpx; }
.sr-ico-chip { flex-shrink: 0; width: 72rpx; height: 72rpx; border-radius: 18rpx; display: flex; align-items: center; justify-content: center; font-size: 38rpx; line-height: 1; }
.sr-ico-chip.notice { background: #FFF1E2; }
.sr-ico-chip.material { background: #E9F2FC; }
.sr-card-text { flex: 1; min-width: 0; }
.sr-card-title { font-size: 31rpx; font-weight: 800; color: #1f2329; line-height: 1.3; }
.sr-card-sub { font-size: 25rpx; color: #7A8590; margin-top: 6rpx; line-height: 1.4; }

/* 识别结果：类别小标签 + 字段一览（一眼核对） */
.sr-cat { align-self: flex-start; display: inline-flex; align-items: center; gap: 8rpx; font-size: 28rpx; font-weight: 700; padding: 8rpx 22rpx; border-radius: 999rpx; }
.sr-cat.notice { background: #FFF3E0; color: #A85800; }
.sr-cat.material { background: #E9F2FC; color: #2E86C1; }
.sr-cat-ico { font-size: 30rpx; line-height: 1; }
.sr-preview { width: 100%; background: #fff; border: 1rpx solid #EEE6D8; border-radius: 16rpx; overflow: hidden; }
.sr-pv-row { display: flex; align-items: flex-start; gap: 16rpx; padding: 22rpx 24rpx; }
.sr-pv-row + .sr-pv-row { border-top: 1rpx solid #F3EEE4; }
.sr-pv-label { flex-shrink: 0; width: 90rpx; color: #8A8F98; font-size: 31rpx; line-height: 1.5; }
.sr-pv-val-wrap { flex: 1; min-width: 0; }
.sr-pv-value { color: #1f2329; font-size: 34rpx; font-weight: 600; line-height: 1.5; word-break: break-all; }
.sr-pv-value.miss { color: #B0863A; }
.sr-mat-line { display: flex; align-items: center; gap: 12rpx; font-size: 30rpx; color: #4A5560; padding: 2rpx; line-height: 1.5; }
.sr-mat-ico { flex-shrink: 0; color: #2E86C1; font-size: 33rpx; line-height: 1; }
.sr-row-top { display: flex; align-items: center; gap: 14rpx; }
.sr-pill { font-size: 24rpx; font-weight: 700; color: #fff; padding: 6rpx 18rpx; border-radius: 999rpx; flex-shrink: 0; }
.sr-pill.notice { background: var(--c-primary-dark); }
.sr-pill.material { background: #2E86C1; }
.sr-pill.warn { background: #8A5800; }
.sr-row-note { font-size: 30rpx; color: #4A5560; font-weight: 600; }
/* 多份通知选择卡：逐份单选 */
.sr-multi { background: #FBF7F0; border-color: #F0E6D6; }
.sr-notice-opt { display: flex; align-items: flex-start; gap: 14rpx; margin-top: 14rpx; padding: 16rpx 16rpx; background: #fff; border: 2rpx solid #EDE4D4; border-radius: 14rpx; cursor: pointer; }
.sr-notice-opt.sel { border-color: var(--c-primary-dark); background: #FFF7EC; }
.sr-radio { flex-shrink: 0; width: 34rpx; height: 34rpx; margin-top: 4rpx; border-radius: 50%; border: 3rpx solid #C9CDD4; box-sizing: border-box; }
.sr-radio.on { border-color: var(--c-primary-dark); background: radial-gradient(circle at center, var(--c-primary-dark) 0 44%, #fff 46% 100%); }
.sr-notice-body { flex: 1; min-width: 0; }
.sr-notice-title { font-size: 32rpx; font-weight: 700; color: #1f2329; line-height: 1.35; }
.sr-notice-meta { font-size: 28rpx; color: #4A5560; margin-top: 6rpx; }
.sr-notice-topics { font-size: 25rpx; color: #6A7480; margin-top: 4rpx; line-height: 1.4; }
/* 信息冲突卡：偏红底 + 逐条「原值 → 新值」 */
.sr-conflict { background: #FFF7EA; border-color: #F0DEB6; }
.sr-conf-line { display: flex; align-items: center; flex-wrap: wrap; gap: 8rpx; margin-top: 12rpx; font-size: 29rpx; line-height: 1.4; }
.sr-conf-label { color: #7A8590; font-weight: 700; margin-right: 4rpx; }
.sr-conf-old { color: #9aa0a6; text-decoration: line-through; }
.sr-conf-arrow { color: #B0863A; }
.sr-conf-new { color: #A85800; font-weight: 700; }
/* 红字提示：仅在缺必填或多份通知时间地点冲突时出现 */
.sr-alert { margin-top: 12rpx; font-size: 29rpx; line-height: 1.5; color: #8A5800; font-weight: 600; }
.sr-tip { font-size: 30rpx; color: #6A7480; text-align: center; padding: 4rpx; }
.sr-tip.warn { color: #C0392B; font-weight: 600; }
.sr-meta { font-size: 28rpx; color: #C0A587; margin: 22rpx 0 24rpx; }
.sr-actions { display: flex; align-items: center; gap: 18rpx; width: 100%; }
.sr-btn { height: 92rpx; border: none; border-radius: 46rpx; font-size: 34rpx; font-weight: 700; white-space: nowrap; display: flex; align-items: center; justify-content: center; }
.sr-btn.ghost { flex: 0 0 auto; min-width: 172rpx; padding: 0 28rpx; background: #f2f2f2; color: #777; }
.sr-btn.ghost:active { background: #e9e9e9; }
.sr-btn.primary { flex: 1; background: var(--c-primary-dark); color: #fff; box-shadow: 0 6rpx 16rpx rgba(168, 88, 0, 0.24); }
.sr-btn.primary:active { background: var(--c-primary-strong); }

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
/* 左右切换样张：半透明圆形箭头 + 顶部样张指示 */
.mc-nav { position: absolute; top: 50%; transform: translateY(-50%); width: 72rpx; height: 72rpx; border-radius: 50%; background: rgba(0,0,0,0.4); color: #fff; font-size: 48rpx; line-height: 1; border: none; display: flex; align-items: center; justify-content: center; }
.mc-nav.prev { left: 18rpx; }
.mc-nav.next { right: 18rpx; }
.mc-nav:active { background: rgba(0,0,0,0.6); }
.mc-sample-ind { position: absolute; top: 20rpx; left: 50%; transform: translateX(-50%); font-size: 24rpx; color: #fff; background: rgba(0,0,0,0.42); padding: 6rpx 22rpx; border-radius: 999rpx; white-space: nowrap; }
.mc-bottom { flex-shrink: 0; position: relative; display: flex; justify-content: center; padding: 30rpx 0 calc(46rpx + env(safe-area-inset-bottom)); }
.mc-count { font-size: 24rpx; color: #fff; background: rgba(255,255,255,0.18); padding: 6rpx 18rpx; border-radius: 999rpx; }
.mc-done { position: absolute; right: 40rpx; top: 50%; transform: translateY(-50%); height: 78rpx; padding: 0 32rpx; border: none; border-radius: 40rpx; background: var(--c-primary-dark); color: #fff; font-size: 30rpx; font-weight: 700; }
.mc-done:active { background: var(--c-primary-strong); }
/* 左下角相册缩略图（像系统相机连拍）：显示最近一张 + 张数角标，点击=完成并回到列表查看/删除 */
.mc-roll { position: absolute; left: 40rpx; top: 50%; transform: translateY(-50%); width: 92rpx; height: 92rpx; padding: 0; border: 4rpx solid rgba(255,255,255,0.9); border-radius: 16rpx; background: rgba(0,0,0,0.3); overflow: visible; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.35); }
.mc-roll:active { transform: translateY(-50%) scale(0.92); }
.mc-roll-img { width: 100%; height: 100%; object-fit: cover; border-radius: 12rpx; display: block; }
.mc-roll-badge { position: absolute; top: -14rpx; right: -14rpx; min-width: 36rpx; height: 36rpx; padding: 0 8rpx; box-sizing: border-box; border-radius: 999rpx; background: var(--c-primary-strong); color: #fff; font-size: 24rpx; font-weight: 700; line-height: 36rpx; text-align: center; border: 3rpx solid #fff; }
.mc-shutter { width: 140rpx; height: 140rpx; border-radius: 50%; background: transparent; border: 8rpx solid #fff; display: flex; align-items: center; justify-content: center; padding: 0; }
.mc-shutter-core { width: 104rpx; height: 104rpx; border-radius: 50%; background: #fff; transition: transform .12s ease; }
.mc-shutter:active .mc-shutter-core { transform: scale(0.85); }
.mc-bottom.confirm { gap: 28rpx; padding-left: 44rpx; padding-right: 44rpx; }
/* 重拍/确定 = 35:65，突出确定 */
.mc-bottom.confirm .mc-btn.retake { flex: 0 0 35%; }
.mc-bottom.confirm .mc-btn.use { flex: 1; }
.mc-btn { flex: 1; height: 96rpx; border-radius: 48rpx; font-size: 34rpx; font-weight: 700; border: none; }
.mc-btn.retake { background: rgba(255,255,255,0.16); color: #fff; }
.mc-btn.use { background: var(--c-primary-dark); color: #fff; }
.mc-flash { position: absolute; inset: 0; background: #fff; opacity: 0; pointer-events: none; transition: opacity .1s ease; }
.mc-flash.on { opacity: 0.9; }

/* 真·相机：实时视频铺满取景区；打不开时的状态/错误提示 */
.real-cam .mc-viewport { padding: 8rpx 12rpx; }
.mc-video { width: 100%; height: 100%; object-fit: cover; background: #000; border-radius: 6rpx; }
/* 会议材料行：小图标 + 可点文件名（点开全屏预览）+ 大小 + 删除 */
/* 会议材料折叠头：标题 + 右侧小箭头，点击展开详情 */
.mat-head { cursor: pointer; }
.mat-toggle { flex-shrink: 0; font-size: 40rpx; line-height: 1; color: var(--c-primary-dark); font-weight: 700; transition: transform .2s ease; }
.mat-toggle.open { transform: rotate(90deg); }
/* 微信式文件卡：类型图标 + 文件名 + 下方大小 */
.mat-card { display: flex; align-items: center; gap: 18rpx; background: #F7F8FA; border: 1rpx solid #ECEEF1; border-radius: 14rpx; padding: 16rpx 18rpx; margin-top: 14rpx; cursor: pointer; }
.mat-card:active { background: #EEF0F3; }
.mat-badge { flex-shrink: 0; width: 72rpx; height: 72rpx; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 22rpx; font-weight: 700; letter-spacing: 1rpx; }
.mat-badge.t-pdf  { background: #E5533C; }
.mat-badge.t-doc  { background: #2B7CD3; }
.mat-badge.t-xls  { background: #1E9E5A; }
.mat-badge.t-ppt  { background: #E07B2E; }
.mat-badge.t-img  { background: #17A2A2; }
.mat-badge.t-txt  { background: #7A8598; }
.mat-badge.t-file { background: #9AA0A6; }
.mat-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.mat-fname { font-size: 28rpx; color: #1f2329; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mat-fsize { font-size: 24rpx; color: #9aa0a6; }
.mat-del { flex-shrink: 0; font-size: 40rpx; color: #c4c8cd; padding: 0 6rpx; line-height: 1; }
.mat-del:active { color: #999; }
/* 转圈圈：按钮内白色细环旋转（识别中显示，模拟进度数字在按钮文字里） */
.ai-fill-spin { width: 34rpx; height: 34rpx; border-radius: 50%; border: 5rpx solid rgba(255,255,255,0.45); border-top-color: #fff; box-sizing: border-box; animation: aiSpin 0.7s linear infinite; }
@keyframes aiSpin { to { transform: rotate(360deg); } }
/* 居委会见证（创建页，移自通知页）：白卡 + 标题/说明 + 适老化大复选框 */
/* 居委会见证：普通选项行（非卡片），标题比 section-title 小一号、无灰字注释 */
/* 居委会见证（说明式开关卡片，精简为一行：标题 + 开关） */
.juwei-card { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; background: #fff; border: 2rpx solid #f0f0f0; border-radius: 16rpx; padding: 16rpx 18rpx; margin-top: 4rpx; margin-bottom: 25rpx; box-shadow: 0 2rpx 10rpx rgba(0,0,0,0.04); cursor: pointer; }
.juwei-title { flex: 1; min-width: 0; font-size: 28rpx; color: #1f2329; font-weight: 600; line-height: 1.4; }
/* 开关 */
.juwei-switch { flex-shrink: 0; width: 84rpx; height: 48rpx; border-radius: 999rpx; background: #D3D6DB; position: relative; transition: background .2s ease; }
.juwei-switch::after { content: ""; position: absolute; top: 5rpx; left: 5rpx; width: 38rpx; height: 38rpx; border-radius: 50%; background: #fff; box-shadow: 0 2rpx 6rpx rgba(0,0,0,0.2); transition: left .2s ease; }
.juwei-switch.on { background: var(--c-primary-dark); }
.juwei-switch.on::after { left: 41rpx; }
.assist-row { display: flex; gap: 18rpx; margin-bottom: 18rpx; }
.assist-btn { flex: 1; height: 80rpx; line-height: 80rpx; border-radius: 40rpx; background: #fff; color: #C77800; border: 2rpx solid #FFE0A3; font-size: 28rpx; font-weight: 600; padding: 0 20rpx; margin: 0; box-sizing: border-box; display: flex; align-items: center; justify-content: center; }
.assist-btn.primary { background: #FFA800; color: #fff; border-color: #FFA800; }
.file-list { display: flex; flex-direction: column; gap: 12rpx; margin-bottom: 18rpx; }
.file-item { background: #fff; border: 2rpx solid #f0f0f0; border-radius: 14rpx; padding: 16rpx 18rpx; display: flex; align-items: flex-start; justify-content: space-between; gap: 14rpx; }
.file-name { flex: 1; min-width: 0; font-size: 28rpx; color: #333; line-height: 1.45; word-break: break-all; }
.file-size { flex-shrink: 0; font-size: 28rpx; color: #666; line-height: 1.45; }
.material-textarea { min-height: 200rpx; height: 200rpx; margin-bottom: 18rpx; }
.scan-line { display: block; font-size: 28rpx; color: #666; line-height: 1.6; word-break: break-all; }
.scan-line.ok { color: #27AE60; }
.clear-link { display: block; margin-top: 14rpx; font-size: 28rpx; color: #666; line-height: 1.5; }
.section-title-row { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 10rpx; }
.section-title { display: block; font-size: 32rpx; color: #1f2329; font-weight: 700; margin-bottom: 8rpx; line-height: 1.4; word-break: break-all; }
.section-title-row .section-title { margin-bottom: 0; }
.required-note { font-size: 28rpx; color: #E67E22; line-height: 1.35; white-space: nowrap; }

.form-row { display: flex; gap: 18rpx; align-items: flex-start; }
.form-group { margin-bottom: 12rpx; min-width: 0; }
.form-group:last-child { margin-bottom: 0; }
.form-group.half { flex: 1; min-width: 0; }
.form-label { display: block; font-size: 28rpx; color: #777; margin-bottom: 12rpx; line-height: 1.45; word-break: break-all; }
.form-input, .form-textarea { width: 100%; box-sizing: border-box; background: #fff; border-radius: 14rpx; font-size: 32rpx; color: #1f2329; border: 2rpx solid #eeeeee; }
.form-input { height: 88rpx; min-height: 88rpx; line-height: normal; padding: 0 20rpx; }
.form-input.large { height: 72rpx; min-height: 72rpx; line-height: normal; font-size: 30rpx; font-weight: 600; }
.form-input::placeholder, .form-textarea::placeholder { color: #666; font-size: 30rpx; line-height: 1.5; }
/* 会议标题输入框：占位用更淡的灰 + 常规字重（"请输入会议名称"作浅提示） */
.form-input.large::placeholder { color: #9a9a9a; font-weight: 400; }
.picker-field { width: 100%; min-height: 88rpx; box-sizing: border-box; background: #fff; border: 2rpx solid #eeeeee; border-radius: 14rpx; padding: 0 20rpx; font-size: 32rpx; color: #1f2329; line-height: normal; word-break: break-all; display: flex; align-items: center; }
.form-textarea { min-height: 200rpx; height: 200rpx; line-height: 1.5; padding: 18rpx 20rpx; }

.sheet-actions { display: flex; gap: 18rpx; justify-content: space-between; padding-top: 12rpx; }
.sheet-actions.fixed { padding: 20rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 2rpx solid #f2f2f2; flex-shrink: 0; align-items: center; }
/* 方案D（回调版）：主次靠宽度区分为主，两按钮等高；生成通知加宽带箭头，取消收窄 */
.sheet-actions.fixed .btn-ghost { flex: 0 0 38%; height: 96rpx; }
.sheet-actions.fixed .btn-primary { flex: 1; height: 96rpx; border-radius: 48rpx; font-size: 33rpx; gap: 6rpx; }
.sheet-actions.fixed .btn-primary .btn-arrow { font-size: 40rpx; font-weight: 700; line-height: 1; margin-top: -4rpx; }
.btn, .btn-ghost, .btn-primary { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; border-radius: 44rpx; text-align: center; font-size: 32rpx; font-weight: 600; box-sizing: border-box; white-space: nowrap; padding: 0 24rpx; margin: 0; border: 0; display: flex; align-items: center; justify-content: center; }
.btn-ghost { color: #777; background: #f5f5f5; }
/* 主按钮统一深橙（与顶栏同色），按下更深 */
.btn-primary { color: #fff; background: var(--c-primary-dark); }
.btn-primary:active { background: var(--c-primary-strong); }

/* 议题构建 */
.topic-empty { text-align: center; padding: 28rpx 0; font-size: 28rpx; color: #666; line-height: 1.6; }
/* 议题列表：议题多时固定高度、内部下拉滚动，避免把下方「重大事项」挤出屏幕 */
.topic-list { max-height: 200rpx; overflow-y: auto; margin: 2rpx 0 6rpx; }
.topic-line { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 16rpx 4rpx; }
.topic-line-text { flex: 1; min-width: 0; font-size: 30rpx; color: #1f2329; line-height: 1.45; word-break: break-all; }
.topic-line-badge { flex-shrink: 0; margin: 0; }
.topic-line-del { flex-shrink: 0; font-size: 42rpx; color: #888; padding: 0 10rpx; line-height: 1; }
.topic-kind-picker { display: flex; align-items: center; gap: 10rpx; margin: 12rpx 0 14rpx; }
.topic-kind-label { flex-shrink: 0; margin-right: 4rpx; font-size: 26rpx; color: #6b7280; }
.topic-kind-option { min-width: 108rpx; height: 56rpx; box-sizing: border-box; border: 2rpx solid #e1e4e8; border-radius: 28rpx; background: #f7f8fa; color: #4b5563; font-size: 26rpx; line-height: 1; }
.topic-kind-option.on { font-weight: 650; }
.topic-kind-option.notice.on { border-color: #78B9DC; background: #E6F4FB; color: #1677B8; }
.topic-kind-option.discussion.on { border-color: #82BE97; background: #EAF6EE; color: #2E8B57; }
.topic-kind-option.decision.on { border-color: #E6A370; background: #FFF0E5; color: #D56A16; }
.topic-vote-settings { margin: -2rpx 0 14rpx; padding: 14rpx 16rpx; border-radius: 14rpx; background: #FFF8F2; border: 2rpx solid #FFE0C7; }
.topic-kind-picker.vote-method { margin: 0; }
.topic-quick-options { display: flex; flex-direction: column; gap: 10rpx; margin-top: 14rpx; }
.topic-quick-option-row { display: flex; align-items: center; gap: 10rpx; }
.topic-quick-option-num { width: 38rpx; height: 38rpx; flex-shrink: 0; border-radius: 50%; background: #FFF0E5; color: #D56A16; font-size: 24rpx; line-height: 38rpx; text-align: center; }
.topic-quick-option-input { flex: 1; min-width: 0; height: 60rpx; font-size: 26rpx; }
.topic-quick-option-del { width: 48rpx; height: 48rpx; flex-shrink: 0; border: 0; background: transparent; color: #7b8190; font-size: 36rpx; line-height: 1; }
.topic-quick-option-add { align-self: flex-start; padding: 6rpx 4rpx; border: 0; background: transparent; color: #D56A16; font-size: 26rpx; }
.vi-row.topic-input-row { margin-top: 0; align-items: center; }
/* 会议议题：标题与输入框贴近一些 */
.section-title-row.topic-head { margin-bottom: 0; }
.topic-input { flex: 1; min-width: 0; height: 68rpx; min-height: 68rpx; font-size: 28rpx; }
/* 麦克风与确定竖排：确定落在麦克风下方 */
.topic-actions-col { flex-shrink: 0; display: flex; flex-direction: column; align-items: stretch; gap: 10rpx; }
.topic-actions-col .voice-mic-btn { align-self: center; }
.topic-confirm-btn { flex-shrink: 0; height: 68rpx; padding: 0 20rpx; border: none; border-radius: 12rpx; background: #1A5F9E; color: #fff; font-size: 26rpx; font-weight: 600; }
.topic-confirm-btn:active { background: #124B85; }
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
.title-input-wrap textarea.title-ta { height: auto; min-height: 64rpx; max-height: 124rpx; line-height: 1.4; padding: 11rpx 78rpx 11rpx 20rpx; resize: none; overflow-y: auto; box-sizing: border-box; display: block; }
.title-ta::-webkit-scrollbar { width: 6rpx; }
.title-ta::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.12); border-radius: 3rpx; }
.title-ta::-webkit-scrollbar-track { background: transparent; }
.title-clear { position: absolute; right: 12rpx; top: 50%; transform: translateY(-50%); font-size: 44rpx; color: #999; line-height: 1; padding: 0 8rpx; cursor: pointer; }
/* 文本框空时：叉淡化 */
.title-clear.dim { opacity: 0.25; }
/* 必填标记：灰色小字 + 红星（会议名称在文本框右上角绝对定位，会议议题在标题行右侧） */
.req-star { color: #E4572E; font-weight: 700; }
/* 必填未填的红框提醒：点进对应输入框（focus）即消失 */
.field-error { border-color: #E4572E !important; box-shadow: 0 0 0 2rpx rgba(228,87,46,0.16); }
/* 推荐标题：半透明覆盖在文本框内，点文字自动填入 */
.title-ghost { position: absolute; left: 20rpx; top: 13rpx; right: 78rpx; font-size: 32rpx; font-weight: 600; color: rgba(31, 32, 36, 0.32); line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; }
/* 会议名称：输入框 + 右侧圆形麦克风按钮 同排 */
.title-row { display: flex; align-items: center; gap: 16rpx; }
.title-row .title-input-wrap { flex: 1; min-width: 0; }
/* 语音输入：框右实心橙圆钮（纯图标，方案④），标题与议题共用 */
.voice-mic-btn { flex-shrink: 0; width: 72rpx; height: 72rpx; border-radius: 50%; background: #1A5F9E; border: none; padding: 0; display: flex; align-items: center; justify-content: center; box-shadow: 0 4rpx 12rpx rgba(26,95,158,0.28); }
.voice-mic-btn svg { width: 34rpx; height: 34rpx; display: block; }
.voice-mic-btn:active { background: #124B85; }
.voice-mic-btn.on { background: #2E7BC4; animation: vi-pulse 1.2s ease-in-out infinite; }
/* 议题行的麦克风钮略小一号 */
.topic-input-row .voice-mic-btn { width: 68rpx; height: 68rpx; }

/* 会议地点下拉 */
.loc-select { width: 100%; margin-top: 16rpx; appearance: none; -webkit-appearance: none; padding-right: 60rpx; background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='22' height='22' viewBox='0 0 20 20'%3E%3Cpath fill='%23999' d='M5 7l5 5 5-5z'/%3E%3C/svg%3E"); background-repeat: no-repeat; background-position: right 20rpx center; }
.loc-row { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; }
.loc-row .loc-select { flex: 1; min-width: 0; width: auto; margin-top: 0; }
.loc-map-btn { flex-shrink: 0; width: 96rpx; align-self: stretch; display: flex; align-items: center; justify-content: center; background: #E8F0FE; border: 2rpx solid #1A73E8; border-radius: 16rpx; box-sizing: border-box; }
.loc-map-btn svg { width: 46rpx; height: 46rpx; display: block; }
.loc-map-btn:active { background: #D6E4FC; }
.loc-row .loc-input { flex: 1; min-width: 0; width: auto; }
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
.picker-pop { width: 100%; max-width: 660rpx; background: #fff; border-radius: 26rpx; padding: 12rpx 26rpx 48rpx; box-sizing: border-box; }
/* 时间：大按钮点选网格（免滚动） */
.tg-cur { text-align: center; font-size: 64rpx; font-weight: 700; color: #1f2329; letter-spacing: 2rpx; margin-bottom: 6rpx; }
.tg-label { font-size: 28rpx; color: #999; margin: 2rpx 2rpx 8rpx; }
.tg-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14rpx; }
.tg-grid-m { margin-bottom: 4rpx; }
.tg-cell { height: 88rpx; display: flex; align-items: center; justify-content: center; font-size: 36rpx; color: #1f2329; background: #f5f6f8; border-radius: 14rpx; }
.tg-cell.on { background: var(--c-primary-soft); color: var(--c-primary-dark); font-weight: 700; box-shadow: inset 0 0 0 3rpx var(--c-primary); }
.tg-cell:not(.on):active { background: var(--c-primary-soft); }
.pp-head { text-align: center; font-size: 56rpx; font-weight: 700; color: #1f2329; margin: 0 0 12rpx; }
.pop-close { display: flex; justify-content: flex-end; margin-bottom: 4rpx; }
.pp-cols { display: flex; gap: 16rpx; }
.pp-col { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.pp-col-label { text-align: center; font-size: 28rpx; color: #666; margin-bottom: 10rpx; }
.pp-col-scroll { height: 460rpx; overflow-y: auto; background: #f7f8fa; border-radius: 16rpx; padding: 8rpx; box-sizing: border-box; -webkit-overflow-scrolling: touch; }
.pp-item { display: flex; align-items: center; justify-content: center; height: 76rpx; font-size: 34rpx; color: #333; border-radius: 12rpx; margin: 4rpx 0; }
.pp-item.on { color: #fff; background: #FFA800; font-weight: 700; }
.pp-actions { display: flex; gap: 18rpx; margin-top: 24rpx; justify-content: center; }
.pp-actions .btn { flex: 0 0 60%; width: 60%; }


/* 议题摘要行 */
.topic-summary { background: #fff; border: 2rpx solid #eee; border-radius: 18rpx; padding: 22rpx 24rpx; margin-top: 16rpx; }
.ts-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; margin-bottom: 14rpx; }
.ts-num { font-size: 26rpx; color: #8a9099; font-weight: 600; }
.ts-title { font-size: 32rpx; color: #1f2329; line-height: 1.45; word-break: break-all; }
.ts-badge { font-size: 25rpx; padding: 6rpx 18rpx; border-radius: 20rpx; white-space: nowrap; line-height: 1.5; }
.ts-badge.badge-notice { color: #1677B8; background: #E6F4FB; }
.ts-badge.badge-discussion { color: #2E8B57; background: #EAF6EE; }
.ts-badge.badge-decision { color: #D56A16; background: #FFF0E5; }
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
