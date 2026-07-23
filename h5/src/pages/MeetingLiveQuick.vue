<template>
  <div v-if="detail" class="live-page" :class="{ 'lp-signin': currentStep === 1, 'has-fixed-end': isChair && currentStep === 2 && meetingPhase === 'recording' && !meetingEnded, 'has-fixed-end-voting': currentStep === 2 && meetingPhase === 'voting' && !meetingEnded }" style="overflow-y:auto;">

    <!-- 页面对所有身份统一：主任/副主任可操作录音等，其余身份只读+表决/意见 -->
    <PageNav :title="isOnlineMeeting ? '线上会议' : (currentStep === 1 ? '会议签到' : (meetingPhase === 'voting' ? '议题处理' : '会议进行'))" style="margin:-3.2vw -3.2vw 0;">
      <template #left>
        <div class="mlq-back" @click="onNavBack">‹</div>
      </template>
      <template #right>
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>

    <OnlineMeetingFlow v-if="isOnlineMeeting" :detail="detail" :meeting-id="meetingId"
                       :is-chair="isChair" @reload="loadDetail" />

    <template v-else>
    <!-- 方案C：录音开始后收成顶部状态条；暂停后在这里露出继续/上传 -->
    <div v-if="currentStep === 2 && (recActive || isPaused)" class="top-rec-status" :class="{ paused: isPaused }">
      <span class="top-rec-dot"></span>
      <span class="top-rec-main">{{ isPaused ? '录音已暂停' : '录音中' }}</span>
      <span class="top-rec-time">{{ timeText }}</span>
    </div>

    <!-- AI 工作中：一个遮罩连续覆盖 转写(asr) → 生成纪要(gen)；全部完成后显"已生成会议纪要"、点击进纪要页 -->
    <!-- 遮罩只在「生成会议纪要」阶段弹；上传/转写在后台静默进行，靠录音卡下方行内提示 -->
    <AiWorkingOverlay :active="generatingMinutes" :phase="overlayPhase" @confirm="onAiWorkDone" @close="onAiWorkClose" :audioDurSec="asrAudioDurSec" :audioFileSizeByte="asrFileSizeBytes" />

    <!-- 结束会议后的会后整理决策页：把“生成纪要/直接结束”从弹窗提升为明确页面 -->
    <div v-if="endReviewVisible" class="end-review-page">
      <!-- 与主页面同款顶栏：‹ 返回(回录音页) + 首页 -->
      <PageNav title="会后整理">
        <template #left>
          <div class="mlq-back" @click="closeEndReview">‹</div>
        </template>
        <template #right>
          <button class="nav-home" @click="goHome">首页</button>
        </template>
      </PageNav>
      <!-- 任务清单式排版（0722）：一句引导 + 三个带状态灯的核对项（绿✓=已好，黄●=待办），
           全绿后点底部主按钮。替代原「三步引导框 + 平铺区块」的无主次布局 -->
      <div class="end-review-body">
        <div class="end-review-card">
          <div class="er-lead">现场会议已结束。核对以下事项，然后生成会议纪要。</div>

          <div class="er-item">
            <div class="er-item-head" @click="toggleErItem(1)">
              <span class="er-item-no">1</span>
              <span class="er-item-title">签到确认</span>
              <!-- 过半(法定人数)绿灯，未过半黄灯提醒——未过半的表决决议无效 -->
              <span class="er-item-state" :class="signinQuorum.ready ? 'ok' : 'warn'">
                {{ (signinQuorum.ready ? '✓ ' : '● ') + signinStats.signedCount + '/' + signinStats.expectedCount + ' 已签到' + (signinQuorum.ready ? '' : '（未过半）') }}
              </span>
              <span class="er-item-toggle">{{ erCollapsed[1] ? '▸' : '▾' }}</span>
            </div>
            <template v-if="!erCollapsed[1]">
            <div v-if="signinStats.absentCount > 0" class="er-item-sub">（{{ signinStats.absentCount }} 人请假缺席，不计入应到）</div>
            <!-- 列席人员（0723 向真实材料看齐）：居委/街道/物业等非委员到会者，进会议记录（实到 N+M）与纪要（到会指导） -->
            <div class="er-observers" @click="isChair && editObservers()">
              <span class="er-observers-label">列席人员</span>
              <span class="er-observers-value">{{ observersText || '未登记（居委、街道、物业等到会人员）' }}</span>
              <button v-if="isChair" class="er-observers-edit">{{ observersText ? '修改' : '登记' }}</button>
            </div>
            <div class="er-item-actions">
              <button class="er-act" @click="rosterPopOpen = true">查看名单</button>
              <button class="er-act" :disabled="exportingAttendanceSheet" @click="exportAttendanceSheet">
                {{ exportingAttendanceSheet ? '正在生成…' : '打印签到表' }}
              </button>
            </div>
            </template>
          </div>

          <div class="er-item">
            <div class="er-item-head" @click="toggleErItem(2)">
              <span class="er-item-no">2</span>
              <span class="er-item-title">议题处理</span>
              <span class="er-item-toggle">{{ erCollapsed[2] ? '▸' : '▾' }}</span>
            </div>
            <template v-if="!erCollapsed[2]">
            <!-- 各议题结果一行一条（纯状态展示，不可点——0722 用户定：入口统一走「继续处理议题」，
                 避免误导；未投拦截的「去代录」仍程序直开弹层） -->
            <div v-if="meetingTopics.length" class="er-topic-list">
              <div class="er-topic-row" v-for="(t, ti) in meetingTopics" :key="'er-' + t.id">
                <span class="er-topic-title">（{{ ti + 1 }}）{{ t.title }}</span>
                <span class="er-topic-result" :class="erTopicResult(t).cls">{{ erTopicResult(t).text }}</span>
              </div>
            </div>
            <div v-if="pendingTopicCount" class="er-item-actions">
              <button class="er-act warm" @click="continuePendingTopics">继续处理议题</button>
            </div>
            </template>
          </div>

          <div class="er-item">
            <div class="er-item-head" @click="toggleErItem(3)">
              <span class="er-item-no">3</span>
              <span class="er-item-title">会议录音</span>
              <span class="er-item-state" :class="erRecordState.cls">{{ erRecordState.text }}</span>
              <span class="er-item-toggle">{{ erCollapsed[3] ? '▸' : '▾' }}</span>
            </div>
            <template v-if="!erCollapsed[3]">
            <!-- 就绪时这行纯重复（徽章已说就绪、下面逐段列出），只在未就绪时显示状态说明，减一行密度 -->
            <div v-if="!(generated && recordingsChrono.length)" class="er-item-sub">{{ endReviewRecordText }}<template v-if="hasSavedRecordings || canUpload"> · {{ endReviewAsrText }}</template></div>
            <!-- 每段录音一行：段号+时长，右侧 转写（看这一段）/ 删除（仅主持人） -->
            <div v-if="recordingsChrono.length" class="er-rec-list">
              <div class="er-rec-row" v-for="(rItem, ri) in recordingsChrono" :key="'er-rec-' + rItem.id">
                <span class="er-rec-name">第 {{ ri + 1 }} 段 · {{ fmtDur(rItem.durationSec) }}</span>
                <button class="er-rec-act" @click="openRecordingTranscript(rItem, ri)">查看</button>
                <button v-if="isChair" class="er-rec-act del" @click="deleteRecording(rItem, ri)">删除</button>
              </div>
            </div>
            <!-- 整会合并转写稿入口（录音删光后本地转写是残留，不再给入口） -->
            <div v-if="hasTranscript && recordingsChrono.length" class="er-item-actions">
              <button class="er-act" @click="openTranscript('full')">查看合并转写</button>
            </div>
            </template>
          </div>

          <div class="er-item">
            <div class="er-item-head" @click="toggleErItem(4)">
              <span class="er-item-no">4</span>
              <span class="er-item-title">会议材料</span>
              <span class="er-item-state muted">{{ materials.length ? ('共 ' + materials.length + ' 份') : '暂无材料' }}</span>
              <span class="er-item-toggle">{{ erCollapsed[4] ? '▸' : '▾' }}</span>
            </div>
            <template v-if="!erCollapsed[4]">
            <!-- 现有材料：点名字预览 -->
            <div v-if="materials.length" class="er-mat-list">
              <div class="er-mat-row" v-for="(m, mi) in materials" :key="'er-mat-' + mi" @click="previewMaterial(mi)">
                <span class="er-mat-name">{{ m.name }}</span>
                <span class="er-mat-size">{{ m.sizeText || '查看' }}</span>
              </div>
            </div>
            <div class="er-item-actions">
              <button class="er-act" @click="uploadMaterial">上传材料</button>
              <!-- 拍照上传（0723 泛化）：可拍委员合影，也可把纸质材料（手写记录/签到表/方案等）拍下来存档，
                   都直接进会议材料，公示材料自动带上 -->
              <button v-if="isChair" class="er-act" :disabled="photoUploading" @click="takePhoto">
                {{ photoUploading ? '照片保存中…' : '拍照上传' }}
              </button>
            </div>
            </template>
          </div>

          <!-- 结论行紧贴主按钮：解释按钮此刻为什么可点/不可点 -->
          <div class="er-hint">{{ endReviewHint }}</div>
          <button class="end-review-primary" :disabled="endReviewPrimaryDisabled" @click="handleEndReviewPrimary">
            {{ endReviewPrimaryText }}
          </button>
          <button class="end-review-secondary" :disabled="ending" @click="endWithoutMinutes">完成会后整理</button>
        </div>
      </div>
    </div>

    <!-- 首屏（步骤条已删）：议题 + 签到/录音。撑满一屏高度，把参会名单顶到首屏之下（需要时往下拉才看到） -->
    <!-- 阶段条：签到 → 议题表决 → 会议材料；录音作为会议记录辅助工具常驻 -->
    <div class="lp-flow" :class="{ 'lp-flow--tight': currentStep === 2 && (recActive || isPaused) }">
      <div class="lp-flow-step" :class="flowStep > 1 ? 'done' : (flowStep === 1 ? 'on' : '')">
        <span class="lp-flow-dot"><template v-if="flowStep > 1">✓</template><template v-else>1</template></span>
        <span class="lp-flow-label">会议签到</span>
      </div>
      <span class="lp-flow-line" :class="{ done: flowStep > 1 }"></span>
      <div class="lp-flow-step" :class="flowStep > 3 || (flowStep === 3 && fieldMeetingEnded) ? 'done' : (flowStep === 2 || flowStep === 3 ? 'on' : '')">
        <span class="lp-flow-dot"><template v-if="flowStep > 3 || (flowStep === 3 && fieldMeetingEnded)">✓</template><template v-else>2</template></span>
        <span class="lp-flow-label">会议进行</span>
      </div>
      <span class="lp-flow-line dashed" :class="{ done: flowStep > 3, available: flowStep === 2 || (flowStep === 3 && !fieldMeetingEnded) }"></span>
      <div class="lp-flow-step" :class="flowStep > 3 ? 'done' : (flowStep === 3 ? 'on' : (flowStep === 2 ? 'available' : ''))">
        <span class="lp-flow-dot">3</span>
        <span class="lp-flow-label">议题处理</span>
      </div>
      <span class="lp-flow-line" :class="{ done: flowStep > 3 }"></span>
      <div class="lp-flow-step" :class="flowStep >= 4 ? 'on' : ''">
        <span class="lp-flow-dot">4</span>
        <span class="lp-flow-label">会后整理</span>
      </div>
    </div>

    <!-- ========== 步骤1：签到页（顶部精简会议卡·点开看议题 → 参会名单·默认收起 → 底部大签到钮·拇指区） ========== -->
    <template v-if="currentStep === 1">
      <div class="signin-page">
        <div class="si-scroll-content">
        <!-- 顶部精简会议卡：名称/时间/地点，点击展开议题 -->
        <div class="si-meet-card" @click="siMeetOpen = !siMeetOpen">
          <div class="si-meet-main">
            <div class="si-meet-title">{{ detail.title || '本次会议' }}</div>
            <div class="si-meet-meta">
              <span class="si-meet-row">{{ formatSigninDateTime(detail.meetingDate, detail.meetingTime) }}</span>
              <span v-if="detail.location" class="si-meet-row">{{ detail.location }}</span>
            </div>
          </div>
          <span class="si-meet-caret">{{ siMeetOpen ? '收起 ▲' : '查看议题 ▾' }}</span>
        </div>
        <div v-if="siMeetOpen" class="si-meet-topics">
          <template v-if="detail.record && detail.record.topics && detail.record.topics.length">
            <div class="si-topic-item" v-for="(item, index) in detail.record.topics" :key="item.id">
              <span class="si-topic-idx">{{ index + 1 }}</span>
              <span class="si-topic-title">{{ item.title }}</span>
            </div>
          </template>
          <span v-else class="si-topic-empty">暂无议题</span>
        </div>

        <!-- 参会名单：默认收起，点击展开 -->
        <div v-if="signinStats.total" class="si-roster" :class="{ open: siRosterOpen }">
          <div class="si-roster-bar" @click="siRosterOpen = !siRosterOpen">
            <span class="si-roster-title">参会名单</span>
            <span class="si-roster-count">
              <span class="si-roster-summary">已有{{ signinStats.signedCount || 0 }}人签到（{{ signinStats.remoteCount || 0 }}人线上参会）</span>
            </span>
            <span class="si-roster-caret">{{ siRosterOpen ? '收起 ▲' : '展开 ▾' }}</span>
          </div>
          <div v-if="siRosterOpen" class="si-roster-body">
            <div class="signin-roster-row" v-for="a in signinStats.list" :key="a.userRoleId">
              <span class="srr-name">{{ a.name }}</span>
              <span class="srr-state"
                    :class="a.signedIn ? (a.attendanceMode === 'remote' ? 'remote' : 'on') : (a.declined ? 'off' : 'wait')">
                {{ a.signedIn ? (a.attendanceMode === 'remote' ? '线上' : '已签到') : (a.declined ? '请假/缺席' : '未确认') }}
              </span>
            </div>
          </div>
        </div>
        </div>

        <!-- 底部大签到按钮（si-bottom 用 margin-top:auto 吸底；名单展开占满剩余空间内部滚动，按钮不被挤走） -->
        <div class="si-bottom">
          <button class="lp-primary-btn signin-big-btn" @click="confirmSignIn">{{ signedIn ? '进入会议' : '现场签到' }}</button>
          <button v-if="!signedIn && !isChair" class="signin-remote-btn" @click="confirmRemoteAttend">线上参加</button>
        </div>
      </div>
    </template>

    <!-- ========== 步骤2：录音 ========== -->
    <template v-else>
      <div v-if="meetingPhase === 'recording'" class="meeting-console">
        <div class="meeting-console-head">
          <div>
            <div class="meeting-console-title">会议进行中</div>
            <div class="meeting-console-sub">{{ detail.title || '本次业委会会议' }}</div>
            <div class="meeting-console-meta">{{ detail.meetingDate }} {{ detail.meetingTime }}<template v-if="detail.location"> · {{ detail.location }}</template></div>
          </div>
          <div v-if="isChair" class="meeting-console-roster" :class="{ ready: signinQuorum.ready }" @click="rosterPopOpen = true">
            已签到 {{ signinStats.signedCount || 0 }}/{{ signinStats.expectedCount || 0 }} ›
          </div>
        </div>
        <div class="meeting-console-topics">
          <div class="mct-head">
            <span class="mct-title">会议议题</span>
            <span class="mct-count">共{{ meetingTopics.length }}项</span>
          </div>
          <div class="mct-list">
            <div class="mct-item" v-for="(t, i) in meetingTopics" :key="'mct-' + t.id">
              <span class="mct-no">{{ i + 1 }}</span>
              <span class="mct-name">{{ t.title }}</span>
              <span class="mct-type" :class="topicActionType(t)">{{ topicActionName(t) }}</span>
            </div>
            <div v-if="!meetingTopics.length" class="mct-empty">暂无会议议题</div>
          </div>
          <button class="meeting-stage-next" :disabled="phaseChanging" @click="enterVotingPhase">
            {{ phaseChanging ? '正在处理…' : '处理议题 ›' }}
          </button>
        </div>
      </div>

      <!-- 委员集中拿出手机处理议题：表决/确认通知/记录意见（0722 用户定：更名「议题处理」，与流程链、按钮用词统一） -->
      <div v-if="meetingPhase === 'voting'" class="core-card">
        <div class="core-head">
          <span class="core-title">会议议题</span>
        </div>

        <div v-if="meetingTopics.length" class="core-list">
          <div class="core-topic" v-for="(item, index) in meetingTopics" :key="'topic-' + item.id">
            <span class="core-topic-no">{{ index + 1 }}</span>
            <div class="core-topic-main">
              <span class="core-topic-title">{{ item.title }}</span>
              <span class="core-topic-type" :class="topicActionType(item)">{{ topicActionName(item) }}</span>
            </div>
            <button class="core-topic-btn" :class="[topicActionType(item), { done: topicRowDone(item) }]" @click="openTopicSheet(item)">
              {{ topicActionButton(item) }}
            </button>
          </div>
        </div>

        <div v-else class="core-empty">暂无会议议题</div>

        <!-- 主持人操作区（0722 用户定：仅主持人=主任）：只保留「临时添加议题」并居中。
             方案A：不再单设「结束表决」——表决全程开放、实时可见，由「结束现场会议/完成本次会议」
             统一定稿（会议结束后 stage 非 ongoing，自动锁票并揭晓结果） -->
        <div v-if="isHost" class="host-topic-actions">
          <button class="hta-btn add" @click="openAddTopic">+ 临时添加议题</button>
        </div>
      </div>

      <!-- 录音中断预警：放在录音卡上方（不占卡内空间）；切出瞬间 JS 冻结无法当场提示，只能前置 -->
      <div v-if="recActive" class="rec-bg-warn">⚠ 录音中请不要切出微信或锁屏，否则录音会中断</div>

      <!-- 会中辅助区：拆「会议录音」+「会议材料」两个子标题区 -->
      <div class="supp-card" v-if="meetingPhase === 'recording'">
        <!-- ① 会议录音 -->
        <div class="supp-head recording-compact-head">
          <span class="supp-title">会议录音</span>
          <button v-if="!isPaused && !isSelfRemote" class="supp-btn rec recording-head-action" @click="onCircleTap" :disabled="uploading || generatingMinutes">
            {{ recActive ? '暂停录音' : (idleAfterUpload ? '继续录音' : '开始录音') }}
          </button>
        </div>
        <!-- 线上参会：不参与现场录音，仅可查看已录段落与会议进展 -->
        <div v-if="isSelfRemote" class="rec-remote-note">您以线上方式参会，无需现场录音。现场录音由到场委员完成。</div>
        <!-- 已录段落行：左侧「已录N段」文字，右侧展开/收起按钮 -->
        <div v-if="recordings.length" class="rec-seg-toggle-row">
          <span class="rec-seg-label">已录 {{ recordings.length }} 段</span>
          <button class="recording-summary-toggle" @click="recListOpen = !recListOpen">{{ recListOpen ? '收起 ▲' : '展开 ▾' }}</button>
        </div>
        <!-- 已录内容作为录音区状态摘要，放在主操作上方，避免与下方会议材料混在一起 -->
        <div v-if="recordings.length && recListOpen" class="rec-list rec-list-before-action">
          <div class="rec-list-body">
            <div class="qk-rec-list-item rec-summary-row" v-for="(item, idx) in recordingsChrono" :key="item.id">
              <span class="qrl-name rec-summary-name">第 {{ idx + 1 }} 段</span>
              <span class="rec-summary-duration">{{ fmtDur(item.durationSec) }}</span>
              <!-- 0721 用户定：行内播放按钮去掉——录音基本没人听，转写内容才是重点；试听收进详情 -->
              <span class="rec-summary-more" @click="openRecordingDetail(item, idx)">详情 ›</span>
              <span v-if="isChair" class="rec-summary-del" @click="deleteRecording(item, idx)">删除</span>
            </div>
          </div>
        </div>
        <!-- 录音上传/后台转写状态：上传后自动转写 -->
        <div v-if="uploading" class="rec-status"><span class="qk-up-spin"></span>正在上传并处理录音…<span v-if="uploadPct > 0"> 预计 {{ uploadPct }}%</span></div>
        <div v-else-if="asrStatus === 'empty' || asrStatus === 'failed'" class="rec-status err">⚠ {{ asrErrorText }}</div>
        <div v-else-if="uploadErrorText" class="rec-status err">⚠ {{ uploadErrorText }}</div>
        <div v-else-if="polling || extracting" class="rec-status"><span class="qk-up-spin"></span>录音识别中，可继续录音和开会</div>
        <!-- 识别失败的重试入口：仅失败时出现（平时不放识别按钮，免得误导；正常识别全自动） -->
        <div v-if="recognizeRetryVisible" class="supp-actions single paused">
          <button class="supp-btn upload-rec" @click="uploadAndRecognize">重新识别录音</button>
        </div>
        <!-- 暂停态：继续/上传放卡片下部（初始「开始录音」在头部右侧，见上方 supp-head）
             已停止未上传态（中断/上传失败）：只出「上传录音」，继续录音无从恢复不显示 -->
        <div v-if="(isPaused || stoppedUnuploaded) && !isSelfRemote" class="supp-actions single paused">
          <button v-if="isPaused" class="supp-btn rec" @click="resumeRecording" :disabled="uploading || generatingMinutes">继续录音</button>
          <button class="supp-btn upload-rec" @click="uploadRecordingStep" :disabled="uploadRecordingDisabled || uploading || polling || extracting || generatingMinutes">上传录音</button>
        </div>
        <input ref="audioFileInput" type="file" accept="audio/*" multiple style="display:none" @change="onAudioFileChange" />
      </div>

      <!-- 会议材料：独立卡片（与会议录音分开）；份数紧跟标题，文件名蓝字下划线示可点 -->
      <div class="supp-card" v-if="meetingPhase === 'recording'">
        <div class="supp-head" :class="{ 'supp-head-click': materials.length }" @click="materials.length && (matListOpen = !matListOpen)">
          <span class="supp-title">会议材料<span v-if="materials.length">（共{{ materials.length }}份）</span></span>
          <span v-if="materials.length" class="rec-list-toggle">{{ matListOpen ? '收起 ▲' : '展开 ▾' }}</span>
        </div>
        <div v-if="materials.length" class="supp-files">
          <div v-if="matListOpen" class="rec-list-body">
            <div class="supp-file" v-for="(m, idx) in materials" :key="idx" @click="previewMaterial(idx)">
              <span class="supp-file-name">{{ m.name }}</span>
              <span class="supp-file-size">{{ m.sizeText || '查看' }}</span>
            </div>
          </div>
        </div>
      </div>

      <template v-if="isChair && meetingPhase === 'recording' && !meetingEnded">
        <div class="mlq-endbar-space"></div>
        <div class="mlq-endbar">
          <button class="fixed-end-field-btn" @click="handleMeetingBottomAction">{{ fieldMeetingEnded ? '会后整理 →' : '结束现场会议' }}</button>
        </div>
      </template>

      <!-- 底部操作栏：议题处理阶段钉底（返回录音更常用、结束会议随时可点），两按钮同等重量并排。
           “结束现场会议”只停止现场录音并进入会后整理；“完成本次会议”在下一页执行。 -->
      <div v-if="meetingEnded || meetingPhase === 'voting'" class="end-meeting-row" :class="{ pinned: meetingPhase === 'voting' && !meetingEnded }">
        <button v-if="meetingPhase === 'voting'" class="back-recording-btn" @click="returnToRecordingPage">
          <span class="emb-arrow pre">←</span>返回录音
        </button>
        <!-- 右键：现场未结束→结束现场会议(确认)；已结束现场→会后整理；整场已完成→查看会议详情 -->
        <button v-if="isChair || meetingEnded" class="end-meeting-btn" @click="handleMeetingBottomAction">
          {{ meetingEnded ? '查看会议详情' : (fieldMeetingEnded ? '会后整理' : '结束现场会议') }}<span class="emb-arrow">→</span>
        </button>
      </div>

    </template>

    <!-- 非主任：录音试听列表（主任的录音列表已并入录音卡） -->
    <div class="lp-card qk-rec-list" v-if="currentStep === 2 && !isChair && meetingPhase !== 'recording' && recordings.length">
      <div class="qk-rec-list-head">会议录音 {{ recordings.length }} 段</div>
      <div class="qk-rec-list-item" v-for="(item, idx) in recordingsChrono" :key="item.id">
        <span class="qrl-idx">{{ idx + 1 }}</span>
        <div class="qrl-info">
          <span class="qrl-name">第 {{ idx + 1 }} 段 · {{ fmtDur(item.durationSec) }}</span>
          <span class="qrl-meta">{{ fmtTimeRange(item) }}</span>
        </div>
        <span class="qrl-play" :class="{ on: playingId === item.id }" @click="togglePlay(item)">{{ playingId === item.id ? '⏸' : '▶' }}</span>
      </div>
    </div>

    <!-- 签到名单弹窗：点「已签到 N/M」胶囊弹出，查看各人签到状态 -->
    <div v-if="rosterPopOpen" class="roster-pop-mask" @click.self="rosterPopOpen = false">
      <div class="roster-pop" @click="attMenuFor = null">
        <div class="roster-pop-head">
          <span class="roster-pop-title">签到情况 {{ signinStats.signedCount || 0 }}/{{ signinStats.expectedCount || 0 }}<template v-if="signinStats.absentCount"> · {{ signinStats.absentCount }}人请假</template></span>
          <span class="roster-pop-close" @click="rosterPopOpen = false">×</span>
        </div>
        <div class="roster-pop-body">
          <!-- 状态纠错（0722 用户定）：主持人点状态右侧 ▾，从该行悬浮弹出一列状态选项（不占行、不弹底部大面板） -->
          <div class="roster-pop-row" v-for="a in signinStats.list" :key="a.userRoleId">
            <span class="rp-name">{{ a.name }}</span>
            <span class="rp-state" :class="a.signedIn ? (a.attendanceMode === 'remote' ? 'remote' : 'on') : (a.declined ? 'off' : 'wait')">
              {{ a.signedIn ? (a.attendanceMode === 'remote' ? '线上' : '已签到') : (a.declined ? '请假/缺席' : '未签到') }}
            </span>
            <span v-if="canEditAttendance" class="rp-edit" :class="{ on: attMenuFor === a.userRoleId }"
                  @click.stop="toggleAttMenu(a, $event)">▾</span>
            <div v-if="attMenuFor === a.userRoleId" class="rp-menu" :class="{ up: attMenuUp }">
              <div v-for="o in ATTENDANCE_STATUS_OPTIONS" :key="o.value" class="rp-menu-item"
                   :class="{ cur: attendanceValueOf(a) === o.value }"
                   @click.stop="applyAttendance(a, o)">{{ o.label }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 议题弹层：表决 + 意见（点议题行打开）；下一个议题直接切换 -->
    <!-- allow-proxy：代委员投票仅在现场会议结束后的会后整理阶段开放（补录未投委员），现场进行中不出现 -->
    <TopicSheet :meeting-id="meetingId" :topic="sheetTopic" :interactive="detail.stage === 'ongoing'"
                :allow-proxy="fieldMeetingEnded"
                :signed-in="signedIn" :is-chair="isChair" :has-prev="sheetHasPrev" :has-next="sheetHasNext"
                @close="sheetTopicId = null" @changed="loadDetail" @prev="gotoPrevTopic" @next="gotoNextTopic" />
    </template>

    <!-- 转录文本查看 -->
    <div v-if="transcriptVisible" class="qk-modal-mask" @click="closeTranscript">
      <div class="qk-transcript-sheet" @click.stop="noop">
        <div class="qk-sheet-head">
          <span class="qk-modal-title">{{ transcriptTitle }}</span>
          <span class="qk-sheet-close" @click="closeTranscript">×</span>
        </div>
        <div class="qk-transcript-tabs">
          <span class="qk-transcript-tab" :class="transcriptMode === 'short' ? 'on' : ''" @click="switchTranscriptMode('short')">摘要</span>
          <span class="qk-transcript-tab" :class="transcriptMode === 'full' ? 'on' : ''" @click="switchTranscriptMode('full')">全部内容</span>
        </div>
        <div class="qk-transcript-scroll" style="overflow-y:auto;">
          <div v-if="transcriptViewLoading" class="lp-empty">正在加载这段录音的转写…</div>
          <!-- 单段无内容/取不到：两个 tab 统一给可读说明，不再显示"暂无"或报错；结果丢失时给重新识别入口 -->
          <div v-else-if="transcriptView && transcriptView.emptyText" class="lp-empty">
            {{ transcriptView.emptyText }}
            <button v-if="transcriptView.retryId" class="qk-retry-asr" :disabled="retranscribing"
                    @click="retranscribeSegment">重新识别</button>
          </div>
          <div v-else-if="transcriptMode === 'short'">
            <span class="qk-transcript-body">{{ transcriptPreviewText || '暂无摘要文本' }}</span>
            <div class="qk-note">{{ transcriptView ? '这是该条录音单独识别出的原文；整会合并稿见录音卡片的查看转写。' : '摘要用于快速判断转写是否完成；正式匹配仍以全部内容为依据。' }}</div>
          </div>
          <div v-else>
            <div v-if="transcriptSegs.length === 0" class="lp-empty">暂无转写原文</div>
            <div class="qk-tr-list" v-else>
              <div class="qk-tr-seg" v-for="seg in transcriptSegs" :key="seg.id">
                <div class="qk-tr-meta"><span class="qk-tr-spk">{{ seg.speaker }}</span><span class="qk-tr-time">{{ seg.time }}</span></div>
                <span class="qk-tr-text">{{ seg.text }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="recordingDetail" class="qk-modal-mask" @click="closeRecordingDetail">
      <div class="recording-detail-card" @click.stop="noop">
        <div class="qk-sheet-head">
          <span class="qk-modal-title">第 {{ recordingDetail.index + 1 }} 段录音</span>
          <span class="qk-sheet-close" @click="closeRecordingDetail">×</span>
        </div>
        <div class="recording-detail-grid">
          <div><span>录音时长</span><b>{{ fmtDur(recordingDetail.item.durationSec) }}</b></div>
          <div><span>录制时间</span><b>{{ fmtTimeRange(recordingDetail.item) }}</b></div>
          <div><span>识别状态</span><b>{{ recordingStatusText(recordingDetail.item) }}</b></div>
        </div>
        <div class="recording-detail-actions">
          <!-- 0721 用户定：转写内容是重点（主样式在前），播放降为普通样式——录音基本没人回听；
               删除不进详情（录音行上已有红字删除，重复入口去掉） -->
          <button v-if="recordingDetail.item.asrStatus === 'done'" class="supp-btn rec" @click="openTranscriptFromDetail">查看这段转写</button>
          <button class="supp-btn detail-secondary" @click="togglePlay(recordingDetail.item)">{{ playingId === recordingDetail.item.id ? '暂停播放' : '播放录音' }}</button>
        </div>
      </div>
    </div>

    <!-- 实时添加议题弹窗 -->
    <div v-if="addTopicVisible" class="qk-modal-mask" @click="closeAddTopic">
      <div class="qk-modal" @click.stop="noop">
        <span class="qk-modal-title">实时添加议题</span>
        <div class="qk-input-row">
          <input class="qk-modal-input" placeholder="输入议题" v-model="newTopicForm.title" />
        </div>
        <div class="qk-modal-label">议题类型</div>
        <!-- 0717 用户定：通知并入讨论，只剩两类；填了通知正文提交时存 notice、没填存 discussion（见 submitAddTopic） -->
        <div class="qk-modal-types">
          <span class="qk-type discussion" :class="newTopicForm.type !== 'decision' ? 'on' : ''" @click="pickTopicType('discussion')">通知和讨论</span>
          <span class="qk-type decision" :class="newTopicForm.type === 'decision' ? 'on' : ''" @click="pickTopicType('decision')">表决事项</span>
        </div>
        <template v-if="newTopicForm.type !== 'decision'">
          <div class="qk-modal-label">通知正文（选填）</div>
          <textarea class="qk-modal-input qk-modal-textarea" placeholder="请输入内容" v-model="newTopicForm.content" rows="3"></textarea>
        </template>
        <template v-if="newTopicForm.type === 'decision'">
          <div class="qk-modal-label">表决方式</div>
          <div class="qk-modal-types">
            <span class="qk-type" :class="newTopicForm.decisionType === 'simple' ? 'on' : ''" @click="pickDecisionType('simple')">是 / 否</span>
            <span class="qk-type" :class="newTopicForm.decisionType === 'multi_choice' ? 'on' : ''" @click="pickDecisionType('multi_choice')">多选一</span>
          </div>
        </template>
        <template v-if="newTopicForm.type === 'decision' && newTopicForm.decisionType === 'multi_choice'">
          <div class="qk-modal-label">选项（至少两个）</div>
          <div v-for="(opt, oi) in newTopicForm.options" :key="opt.id" class="qk-opt-row">
            <span class="qk-opt-num">{{ oi + 1 }}.</span>
            <input class="qk-modal-input qk-opt-input" placeholder="" v-model="opt.label" />
            <span v-if="newTopicForm.options.length > 1" class="qk-opt-del" @click="removeTopicOption(oi)">×</span>
          </div>
          <span class="qk-link" @click="addTopicOption" style="display:block;margin-bottom:14rpx;">+ 添加选项</span>
        </template>
        <div class="qk-modal-btns">
          <button class="lp-ghost-btn qk-cancel-btn" @click="closeAddTopic">取消</button>
          <button class="lp-primary-btn" @click="submitAddTopic">添加</button>
        </div>
      </div>
    </div>

    <!-- 议题语音输入弹层（Web Speech 流式识别，确认后写入议题内容框；与创建页同款交互） -->
    <div v-if="voiceOn" class="voice-modal-mask">
      <div class="voice-modal">
        <div class="vm-body">
          <div class="vm-wave"><span></span><span></span><span></span><span></span><span></span></div>
          <div class="vm-text">
            <span v-if="voiceFinal || voiceInterim">{{ voiceFinal }}<span class="vm-interim">{{ voiceInterim }}</span></span>
            <span v-else class="vm-placeholder">请说话输入议题</span>
          </div>
        </div>
        <div class="vm-actions">
          <button class="btn btn-ghost" @click="cancelTopicVoice">取消</button>
          <button class="btn btn-ghost" @click="retryTopicVoice">重新输入</button>
          <button class="btn btn-primary" @click="confirmTopicVoice">确认</button>
        </div>
      </div>
    </div>

    <!-- 签到 → 录音 跳转动画：签到成功后短暂全屏，勾选动画 + 提示，随后进入录音步 -->
    <div v-if="signinFx" class="signin-fx">
      <div class="signin-fx-card">
        <div class="signin-fx-check">✓</div>
        <span class="signin-fx-title">签到成功</span>
        <span class="signin-fx-sub">正在进入录音…</span>
      </div>
    </div>

  </div>
  <div v-else class="live-page live-error-page">
    <PageNav title="会议进行" style="margin:-3.2vw -3.2vw 0;">
      <template #left>
        <div class="mlq-back" @click="goHome">‹</div>
      </template>
      <template #right>
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>
    <div class="live-error-card">
      <div class="live-error-title">会议无法打开</div>
      <div class="live-error-text">{{ loadError || '会议信息加载失败，请返回首页重新进入。' }}</div>
      <button class="live-error-primary" @click="goHome">回到首页</button>
      <button class="live-error-secondary" @click="loadDetail">重新加载</button>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'MeetingLiveQuick' })
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onUnmounted, onDeactivated, onErrorCaptured } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast, showModal, showActionSheet } from '@/utils/ui'
import { navigateTo, redirectTo, navigateBack } from '@/utils/navigate'
import { aiTask, startAiTask, finishAiTask, failAiTask, clearAiTask } from '@/composables/aiTask'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import { useRecorder } from '@/composables/useRecorder'
import recStore from '@/utils/recStore'
import { applyHotwords } from '@/utils/helpers'
import { openMaterialViewer } from '@/composables/materialViewer'
import { uploadAttachment, humanSize } from '@/utils/upload'
import { meetingRecordingSession, registerMeetingRecordingDiscard } from '@/composables/meetingRecordingSession'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'
import TopicSheet from '@/components/TopicSheet.vue'
import OnlineMeetingFlow from '@/components/OnlineMeetingFlow.vue'

const route = useRoute()
const isOnlineMeeting = computed(() => detail.value && detail.value.meetingMethod === 'online')
const rec = useRecorder()
const unregisterRecordingDiscard = registerMeetingRecordingDiscard(async (targetMeetingId) => {
  if (String(meetingId.value || route.query.meetingId || '') !== String(targetMeetingId)) return
  rec.reset()
})

const POLL_INTERVAL = 1500
const MAX_POLL_COUNT = 120        // 兜底：时长未知时至少轮询这么多次(≈3 分钟)
const QUICK_STATE_PREFIX = 'committee_quick_meeting_state_'

// 轮询上限按待转写音频时长动态给足余量。
// 豆包大模型录音识别是异步的，处理耗时随录音时长增长；固定 3 分钟会在豆包仍在
// 正常转写长录音时被误判为「转写超时」。这里用「基础 5 分钟 + 时长×2」估算预算，
// 封顶 30 分钟，换算成轮询次数。时长未知时回退到 MAX_POLL_COUNT。
function maxPollCount() {
  const durSec = asrAudioDurSec.value || 0
  if (durSec <= 0) return MAX_POLL_COUNT
  const budgetSec = Math.min(1800, Math.max(300, 300 + durSec * 2))
  return Math.max(MAX_POLL_COUNT, Math.ceil(budgetSec * 1000 / POLL_INTERVAL))
}

// ═══════════════════════════════════════════════
// 顶层纯函数（自 meeting-live-quick.js 1:1 迁移）
// ═══════════════════════════════════════════════
function voteHintText(hint) {
  if (!hint) return '需人工确认'
  if (hint.result === 'passed') return '建议通过'
  if (hint.result === 'rejected') return '建议不通过'
  return '需人工确认'
}

function resultLabel(result) {
  if (result === 'passed') return '通过'
  if (result === 'rejected') return '未通过'
  if (result === 'abstain') return '弃权'
  return '需人工确认'
}

function topicTypeLabel(type) {
  // 0722 用户定：类型写清楚——通知/讨论分开显示
  if (type === 'notice') return '通知'
  if (type === 'discussion') return '讨论'
  if (type === 'major') return '重大表决'
  return '表决事项'
}

function reviewLevelLabel(level) {
  if (level === 'auto') return '建议匹配'
  if (level === 'review') return '疑似匹配'
  return '待人工处理'
}

function asPercent(value) {
  if (typeof value !== 'number') return null
  return Math.round(value > 1 ? value : value * 100)
}

function voteResultLabel(result) {
  if (result === 'passed') return '通过'
  if (result === 'rejected') return '否决'
  return '不明确'
}

// rule_kb 抽取字段 -> 展示用列表（每项一行：字段名 + 文本 + 佐证片段）
function mapExtractedFields(hit) {
  const fields = (hit && hit.extractedFields) || []
  return fields.map(function (f) {
    const values = f.values || []
    let text
    if (f.type === 'keyword_mapping') {
      // 表决结果归一化为中文；资金来源/责任方直接用标签
      text = f.field === '表决结果' ? voteResultLabel(f.normalized) : (f.normalized || '')
    } else {
      // regex：把抽到的具体值去重拼接
      const seen = {}
      const distinct = []
      values.forEach(function (v) {
        if (v && v.value && !seen[v.value]) { seen[v.value] = 1; distinct.push(v.value) }
      })
      text = distinct.join('、')
    }
    return {
      field: f.field,
      text: text,
      evidences: values.map(function (v) { return { value: v.value, text: v.evidence || '' } })
    }
  }).filter(function (f) { return f.text })
}

function formatMs(ms) {
  const total = Math.floor((ms || 0) / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
}

function decorateEvidence(topic) {
  const matches = topic.segmentMatches || []
  const first = matches[0]
  const last = matches.length > 1 ? matches[matches.length - 1] : null
  const range = first
    ? (first.time || '') + (last && last.time && last.time !== first.time ? ' - ' + last.time : '')
    : ''
  const expanded = !!topic.evidenceExpanded
  return Object.assign({}, topic, {
    evidenceRange: range,
    evidencePreview: matches.slice(0, 2),
    visibleSegments: expanded ? matches : matches.slice(0, 2),
    evidenceHiddenCount: Math.max(0, matches.length - 2),
    evidenceExpanded: expanded
  })
}

function mapTopic(hit, fallback, voteTotalArg) {
  const fallbackTitle = typeof fallback === 'string' ? fallback : (fallback && fallback.title)
  // voteTotal: 实到人数，用于"全票/一致通过"按人数补齐预填票数
  const type = (hit && hit.type) || (fallback && fallback.type) || 'decision'
  const voteRequired = hit && typeof hit.voteRequired === 'boolean'
    ? hit.voteRequired
    : (fallback && typeof fallback.voteRequired === 'boolean' ? fallback.voteRequired : !(type === 'notice' || type === 'discussion'))
  const confidence = hit && hit.voteHint && typeof hit.voteHint.confidence === 'number'
    ? Math.round(hit.voteHint.confidence * 100)
    : null
  // 根据 voteHint 自动预填票数（人工可改、需确认）
  const vh = hit && hit.voteHint
  const total = Number(voteTotalArg) || 0
  let voteFor = 0, voteAgainst = 0, voteAbstain = 0, aiVote = '', aiVoteLabel = ''
  if (voteRequired && vh) {
    if (vh.source === 'explicit') {
      voteFor = Number(vh.forVotes) || 0; voteAgainst = Number(vh.agVotes) || 0; voteAbstain = Number(vh.abVotes) || 0
      aiVote = 'explicit'; aiVoteLabel = '按录音播报票数'
    } else if (vh.source === 'unanimous' && vh.unanimous && total > 0) {
      voteFor = total; voteAgainst = 0; voteAbstain = 0
      aiVote = 'unanimous'; aiVoteLabel = '录音为全票/一致通过'
    } else if (vh.source === 'counted') {
      voteFor = Number(vh.forVotes) || 0; voteAgainst = Number(vh.agVotes) || 0; voteAbstain = Number(vh.abVotes) || 0
      aiVote = 'counted'; aiVoteLabel = '按发言逐个计数(粗略)'
    }
  }
  let result = (vh && vh.result) || 'unclear'
  if (voteRequired && aiVote) {
    const need = total > 0 ? Math.floor(total / 2) + 1 : 1
    const counted = voteFor + voteAgainst + voteAbstain
    if (voteFor >= need) result = 'passed'
    else if (total > 0 && counted >= total) result = 'rejected'
    else result = 'unclear'
  }
  const matchConfidence = hit && typeof hit.confidence === 'number' ? asPercent(hit.confidence) : null
  const matchedSegments = (hit && hit.matchedSegments) || []
  const segmentMatches = ((hit && hit.segmentMatches) || []).map(function (m) {
    const reasons = m.reasons || []
    return {
      segmentIndex: m.segmentIndex,
      speaker: m.speaker || '',
      time: typeof m.startMs === 'number' ? formatMs(m.startMs) : '',
      text: m.text || '',
      score: typeof m.score === 'number' ? asPercent(m.score) : null,
      level: m.level || 'review',
      levelLabel: reviewLevelLabel(m.level),
      reasons: reasons,
      reasonText: reasons.join('；'),
      scoreDetail: m.scoreDetail || null
    }
  })
  return decorateEvidence({
    id: (hit && (hit.topicId || hit.tempId)) || fallbackTitle,
    title: (hit && hit.title) || fallbackTitle || '未命名议题',
    type: type,
    typeLabel: topicTypeLabel(type),
    voteRequired: voteRequired,
    suggest: voteRequired ? voteHintText(hit && hit.voteHint) : '记录要点',
    result: result,
    resultLabel: resultLabel(result),
    voteFor: voteFor,
    voteAgainst: voteAgainst,
    voteAbstain: voteAbstain,
    voteCounted: voteFor + voteAgainst + voteAbstain,
    aiVote: aiVote,
    aiVoteLabel: aiVoteLabel,
    // 表决类议题且 AI 未能确定（无票数来源或结果不明）时，提示主任核对
    needsReview: voteRequired && (result === 'unclear' || !aiVote),
    confidence: confidence,
    matchConfidence: matchConfidence,
    reviewLevel: (hit && hit.reviewLevel) || (matchedSegments.length ? 'review' : 'empty'),
    reviewLevelLabel: reviewLevelLabel(hit && hit.reviewLevel),
    needManualReview: hit && typeof hit.needManualReview === 'boolean' ? hit.needManualReview : true,
    summaryDraft: (hit && hit.summaryDraft) || '',
    extractedFields: mapExtractedFields(hit),
    segmentMatches: segmentMatches,
    matchedSegments: matchedSegments,
    matchedCount: segmentMatches.length || matchedSegments.length,
    evidenceExpanded: false,
    confirmed: false
  })
}

// ═══════════════════════════════════════════════
// data（自 Page.data 1:1 迁移到 ref）
// ═══════════════════════════════════════════════
const type = ref('committee')
const meetingId = ref(null)
const detail = ref(null)
const loadError = ref('')

// These immediate recorder refs may run their watcher during setup, so register
// the session bridge only after meetingId/detail have been initialized.
watch([rec.recording, rec.paused, rec.timeText], ([recording, paused, time]) => {
  meetingRecordingSession.active = !!recording
  meetingRecordingSession.paused = !!paused
  meetingRecordingSession.timeText = time || '00:00'
  if (recording) meetingRecordingSession.meetingId = String(meetingId.value || route.query.meetingId || '')
})
watch(() => detail.value && detail.value.title, (title) => {
  if (title) meetingRecordingSession.meetingTitle = title
})

onErrorCaptured((err) => {
  const msg = err && err.message ? err.message : '页面渲染失败'
  console.error('[会议进行页] 渲染失败：', err)
  detail.value = null
  loadError.value = '会议页面加载异常：' + msg
  return false
})
// 议题弹层：存 id、从最新 detail 里取对象（轮询/刷新后计票等自动更新）
const sheetTopicId = ref(null)
const sheetTopic = computed(() => {
  const list = (detail.value && detail.value.record && detail.value.record.topics) || []
  return list.find(t => t.id === sheetTopicId.value) || null
})
const meetingTopics = computed(() => (detail.value && detail.value.record && detail.value.record.topics) || [])
const noticeTopics = computed(() => meetingTopics.value.filter(t => t.type === 'notice'))
const decisionTopics = computed(() => meetingTopics.value.filter(t => t.voteRequired || t.type === 'decision'))
const discussionTopics = computed(() => meetingTopics.value.filter(t => t.type !== 'notice' && !(t.voteRequired || t.type === 'decision')))
function openTopicSheet(item) {
  // 未签到不进表决/意见弹层：在点击时就提醒签到，而不是进去后再提示
  if (!signedIn.value) {
    toast({ title: '请先返回签到，签到后即可表决/发言', icon: 'none' })
    return
  }
  sheetTopicId.value = item.id
}
// 弹层"上一个/下一个议题"：当前议题在列表中的位置 + 切换
function _sheetTopicIndex() {
  const list = meetingTopics.value
  return { list, i: list.findIndex(t => t.id === sheetTopicId.value) }
}
const sheetHasPrev = computed(() => _sheetTopicIndex().i > 0)
const sheetHasNext = computed(() => { const { list, i } = _sheetTopicIndex(); return i >= 0 && i < list.length - 1 })
function gotoPrevTopic() { const { list, i } = _sheetTopicIndex(); if (i > 0) sheetTopicId.value = list[i - 1].id }
function gotoNextTopic() { const { list, i } = _sheetTopicIndex(); if (i >= 0 && i < list.length - 1) sheetTopicId.value = list[i + 1].id }
// 议题状态标签：显示"待/已"状态而非属性。状态由后端 TopicVO 字段驱动，
// 录音经 ASR 识别+确认后 status/opinionCount 会更新，loadDetail 刷新后标签自动翻成"已"。
function topicBadgeDone(item) {
  if (item.voteRequired) return item.status === 'passed' || item.status === 'failed' // 表决达成(含ASR识别票数确认后)
  if (item.type === 'notice') return !!item.notified || (item.opinionCount || 0) > 0 // 通报：已宣读/全体已看，或录音里被提到
  return (item.opinionCount || 0) > 0 // 讨论：录音里被提到/有意见 = 已处理
}
const allTopicsCompleted = computed(() => meetingTopics.value.length > 0 && meetingTopics.value.every(topicBadgeDone))
const pendingTopicCount = computed(() => meetingTopics.value.filter(item => !topicBadgeDone(item)).length)
const meetingEnded = computed(() => !!detail.value && detail.value.stage === 'ended')
// 主任可随时结束现场会议；议题允许在停止现场录音后继续处理。
const canEndFromRecordingPage = computed(() => isChair.value && meetingPhase.value === 'recording')
function topicBadgeText(item) {
  const done = topicBadgeDone(item)
  if (item.voteRequired) return done ? '已表决' : '待表决'
  return done ? '已讨论' : '待讨论'
}
// 胶囊按钮文案：待办用动词(去表决/去通报/去讨论)增强"可点"召唤；已办沿用状态词(已表决…)
function topicPillLabel(item) {
  if (topicBadgeDone(item)) return topicBadgeText(item)
  if (item.voteRequired) return '去表决'
  return '去讨论'
}
// 胶囊按钮按议题类型着色，与议题弹层标签同一套：表决橙 / 通报紫 / 讨论蓝
function topicPillType(item) {
  if (item.voteRequired) return 'vote'
  return 'discuss'  // 0721 通知并入讨论：不再单列 notice，「宣读/通报」统一走「讨论」
}
function topicActionType(item) {
  return topicPillType(item)
}
function topicActionName(item) {
  if (item.type === 'notice') return '通知' // 通报类：标签文字叫「通知」，颜色仍走 discuss（与讨论一致）
  const t = topicActionType(item)
  return t === 'vote' ? '表决' : '讨论'
}
// 行按钮的"完成感"：表决类只看「表决是否已结束」（0722 用户定：不再看本人是否投过——
// 投过票但表决仍开着时若变「看结果」，想改票的人找不到入口；表决没结束就一直「去表决」，
// 点进去既能改票也能看实时票数，表决结束后才变「看结果」，那时确实只能看）
function topicRowDone(item) {
  // 方案A：表决全程开放→一直「去表决」；会议结束后（或旧数据已 voteClosed）才「看结果」
  if (item.voteRequired) return !!item.voteClosed || meetingEnded.value
  return topicBadgeDone(item)
}
function topicActionButton(item) {
  const t = topicActionType(item)
  if (t === 'vote') return topicRowDone(item) ? '看结果' : '去表决'
  // 通报类（有正文，走"宣读/我已读"确认）：主持人「去通知」、其余人「查看通知」；全体已通报后「已通报」
  if (item.type === 'notice') {
    if (topicBadgeDone(item)) return '已通报'
    return isHost.value ? '去通知' : '查看通知'
  }
  return topicBadgeDone(item) ? '已完成' : '去讨论'
}

// 方案A（0722 用户定）：取消单独的「结束表决」——表决全程开放、实时可见，
// 会议结束（stage 非 ongoing）时统一锁票并揭晓结果，不再需要独立的结束表决动作。

// 步骤条 UI 已删（steps 数组随之移除）；currentStep 仍驱动 签到卡(1)/录音卡(2) 的切换
const currentStep = ref(1)
const meetingPhase = ref('recording') // recording=现场会议并录音；voting=会后集中表决与确认意见
const phaseChanging = ref(false)
const pageActive = ref(false)
const signedIn = ref(false)        // 后端持久态：会议开始时已清空，ongoing 阶段即"本人会上是否已签到"（按用户区分、服务器持久）
const selfAttendance = ref(null)
// 本人是否「线上参会」：线上参会不参与现场录音（不出录音操作、也不自动开录）
const isSelfRemote = computed(() => !!(selfAttendance.value && selfAttendance.value.attendanceMode === 'remote'))

// 录音计时显示直接复用 useRecorder（recording/paused/hasRecording 在脚本里用 rec.* 读取）
const timeText = rec.timeText
// useRecorder 会话模型 → 原四态展示。注意：暂停时 recording 仍为 true，需先判 paused。
// 录音中(未停) = recording.value；活动录制(出红点) = recording && !paused。
const recActive = computed(() => rec.recording.value && !rec.paused.value)
const recDotOn = computed(() => recActive.value)
// 是否已有“上传保存”的录音（决定圆圈按钮是“继续”还是“开始/重录”）
const hasSavedRecordings = computed(() => (recordings.value || []).length > 0)
// 暂停态：显示"继续录音/重新录音"并排按钮（继续=恢复同一会话，自然合并成一个文件）
const isPaused = computed(() => rec.recording.value && rec.paused.value)
// 悬浮窗显示的录音人姓名（本人＝当前录音的主任/副主任）
const recorderName = computed(() => {
  const role = getStorage('activeRole', null) || {}
  return (selfAttendance.value && selfAttendance.value.name) || role.realName || '本人'
})
watch(recorderName, (name) => {
  meetingRecordingSession.recorderName = name || '本人'
}, { immediate: true })
// ── 悬浮录音标签：可拖动（手机触摸 + 桌面鼠标）──
// 默认走 CSS 的 top/right 定位；拖过一次后切成 left/top 像素定位并记住位置（限制在屏内）。
const recFloatEl = ref(null)
const recDragging = ref(false)
const recDragPos = ref(null) // { left, top } px；null=没拖过，用 CSS 默认位
const recFloatStyle = computed(() => recDragPos.value
  ? { left: recDragPos.value.left + 'px', top: recDragPos.value.top + 'px', right: 'auto', bottom: 'auto' }
  : null)
let _recDrag = null // 拖动会话：{ dx, dy, w, h }
function _recPoint(e) {
  const t = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || e
  return { x: t.clientX, y: t.clientY }
}
function onRecDragStart(e) {
  const el = recFloatEl.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const p = _recPoint(e)
  _recDrag = { dx: p.x - r.left, dy: p.y - r.top, w: r.width, h: r.height }
  recDragging.value = true
  window.addEventListener('touchmove', onRecDragMove, { passive: false })
  window.addEventListener('touchend', onRecDragEnd)
  window.addEventListener('touchcancel', onRecDragEnd)
  window.addEventListener('mousemove', onRecDragMove)
  window.addEventListener('mouseup', onRecDragEnd)
}
function onRecDragMove(e) {
  if (!_recDrag) return
  if (e.cancelable) e.preventDefault() // 拖动时别让页面跟着滚
  const p = _recPoint(e)
  const margin = 6
  const maxLeft = Math.max(margin, window.innerWidth - _recDrag.w - margin)
  const maxTop = Math.max(margin, window.innerHeight - _recDrag.h - margin)
  recDragPos.value = {
    left: Math.min(Math.max(margin, p.x - _recDrag.dx), maxLeft),
    top: Math.min(Math.max(margin, p.y - _recDrag.dy), maxTop),
  }
}
function onRecDragEnd() {
  recDragging.value = false
  _recDrag = null
  if (recDragPos.value) setStorage('recFloatPos', recDragPos.value) // 记住拖到的位置（跨会话）
  window.removeEventListener('touchmove', onRecDragMove, { passive: false })
  window.removeEventListener('touchend', onRecDragEnd)
  window.removeEventListener('touchcancel', onRecDragEnd)
  window.removeEventListener('mousemove', onRecDragMove)
  window.removeEventListener('mouseup', onRecDragEnd)
}
onUnmounted(onRecDragEnd) // 卸载兜底：清掉可能残留的全局监听

// 位置记忆：恢复上次拖到的位置（跨会话）。恢复的是绝对像素，卡片显示时再按当前视口夹紧，
// 防止换设备/横竖屏后跑到屏幕外。
const _savedRecFloatPos = getStorage('recFloatPos', null)
if (_savedRecFloatPos && typeof _savedRecFloatPos.left === 'number' && typeof _savedRecFloatPos.top === 'number') {
  recDragPos.value = { left: _savedRecFloatPos.left, top: _savedRecFloatPos.top }
}
function clampRecFloatIntoView() {
  const el = recFloatEl.value
  if (!el || !recDragPos.value) return
  const r = el.getBoundingClientRect()
  const margin = 6
  const maxLeft = Math.max(margin, window.innerWidth - r.width - margin)
  const maxTop = Math.max(margin, window.innerHeight - r.height - margin)
  recDragPos.value = {
    left: Math.min(Math.max(margin, recDragPos.value.left), maxLeft),
    top: Math.min(Math.max(margin, recDragPos.value.top), maxTop),
  }
}
// 卡片出现（开始/继续录音）时，按当前视口把记忆位置夹回屏内
watch(() => recActive.value || isPaused.value, async (vis) => {
  if (vis && recDragPos.value) { await nextTick(); clampRecFloatIntoView() }
})
// 当前是否有"可上传的新内容"（正在录 / 暂停中 / 内存里有还没上传的录音）。
// 上传成功后已 rec.reset()，此值变 false → "结束录音并上传"置灰，避免重复上传同一段。
const canUpload = computed(() => recActive.value || isPaused.value || rec.hasRecording.value)
// 手里这段录音时长为 0（还没录到内容）→ 禁用「上传并识别录音」，避免上传空录音（火山必判静音失败）
const freshRecEmpty = computed(() => (rec.seconds.value || 0) < 1)
// 录音进行中不允许上传，必须先暂停；暂停后按钮才从灰色变为可用。
const uploadRecordingDisabled = computed(() => recActive.value || freshRecEmpty.value)
// 已上传过录音、且当前没有新录音在手 → 上传后的"空闲"态，引导继续录下一段
const idleAfterUpload = computed(() => !rec.recording.value && !rec.hasRecording.value && hasSavedRecordings.value)
// 已停止但尚未上传的录音（上传失败/录音被中断后落到此态）：必须露出「上传录音」入口——
// 否则界面只剩「开始录音」，点了弹"有一段录音还没上传"却无处可传（用户实测踩过）
const stoppedUnuploaded = computed(() => !rec.recording.value && rec.hasRecording.value)

// ── 「谁在录音」心跳：录音进行中每 10s 报一次到服务端，供他人开录前提示 ──
let _recBeatTimer = null
watch(recActive, (on) => {
  if (on) {
    const send = () => { try { api.committeeRecordingBeat(meetingId.value).catch(() => {}) } catch (e) {} }
    send()
    if (!_recBeatTimer) _recBeatTimer = setInterval(send, 10000)
  } else {
    if (_recBeatTimer) { clearInterval(_recBeatTimer); _recBeatTimer = null }
    try { api.committeeRecordingBeatStop(meetingId.value).catch(() => {}) } catch (e) {}
  }
})

// 开录前查在册表：别人正在录 → 提示重复录音的后果，由用户拍板（老后端无此接口时不拦）
async function confirmOthersRecording() {
  try {
    const list = await api.committeeRecordingLive(meetingId.value)
    const others = (list || []).filter(x => String(x.roleId) !== String(myRoleId.value || ''))
    if (!others.length) return true
    const names = others.map(o => o.name).filter(Boolean).join('、') || '有人'
    const r = await showModal({
      title: names + ' 正在录音',
      content: '一场会议一人录音即可。两人同时录到同一段发言，转写合并时会出现重复内容。确定还要再录一路吗？',
      confirmText: '仍要录音',
      cancelText: '先不录'
    })
    return !!r.confirm
  } catch (e) { return true }
}
// 本轮识别已覆盖的录音 id（识别成功/恢复历史转写时回填）——用它判断是否还有新录音没识别，
// 不依赖 recordings.asrStatus（桩模式不落该字段）
const recognizedIds = ref([])
// 还有录音没经大模型识别 → 按钮显示「上传录音」；识别完变「继续生成会议纪要」
const needRecognize = computed(() => !generated.value
  || (recordings.value || []).some(r => r.asrStatus !== 'done' && !recognizedIds.value.includes(r.id)))
// 有任一段"已转写出内容"（done 或本轮已识别）→ 生成按钮即可用，不再要求全部段都识别完
const hasAnyTranscribed = computed(() => (recordings.value || [])
  .some(r => r.asrStatus === 'done' || recognizedIds.value.includes(r.id)))
// 识别失败后的重试入口（0721 用户定：按钮仅失败时出现，平时不放识别按钮以免误导）。
// 覆盖两种失败痕迹：本机流程刚失败(asrStatus='failed') / 服务端落库的失败段(行上"识别异常")
// 0722 起识别对全员开放，重试按钮不再限主任（委员传的段失败了自己就能重试）
const recognizeRetryVisible = computed(() =>
  !uploading.value && !polling.value && !extracting.value && !generatingMinutes.value
  && !canUpload.value
  && (asrStatus.value === 'failed' || (recordings.value || []).some(r => r.asrStatus === 'failed')))

// ── 主任端静默补识别：委员上传的段/识别中途丢任务的段，主任进入或刷新详情时自动识别 ──
// 不设按钮（0721 用户定：识别入口会误导用户）。每段只自动尝试一次，失败的留给
// 结束会议/生成纪要流程里的「先识别录音」提示兜底，避免失败段被无限重试烧识别费。
const _autoRecognizeTried = new Set()
function maybeAutoRecognizePending() {
  if (!isChair.value || !signedIn.value) return
  if (!detail.value || detail.value.stage !== 'ongoing') return
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  // 主任手上有在录/未传的段时先不跑：识别在途会临时挡住主任自己的上传（finishRecord 排队），
  // 而主任上传成功后本来就会把所有待识别段一起识别，不差这一会儿
  if (canUpload.value) return
  const pending = (recordings.value || [])
    .filter(r => r.asrStatus !== 'done' && r.asrStatus !== 'empty' && !_autoRecognizeTried.has(r.id))
  if (!pending.length) return
  pending.forEach(r => _autoRecognizeTried.add(r.id))
  uploadAndRecognize()
}
// 圆圈按钮（圆圈即录音键）文案：四字状态、圈内两行显示（开始/录音 各占一行）
const recCircleLabel = computed(() => {
  if (recActive.value) return '暂停录音'
  if (isPaused.value) return '继续录音'
  if (idleAfterUpload.value) return '继续录音'
  if (rec.hasRecording.value) return '重新录音'
  return '开始录音'
})
// 圆圈点击：暂停态走恢复（toggleRecord 不处理暂停），其余状态交给 toggleRecord 原逻辑
function onCircleTap() {
  if (isPaused.value) { resumeRecording(); return }
  toggleRecord()
}

const uploading = ref(false)
const uploadPct = ref(0)   // 上传+服务端保存/转码的预计综合进度；完成响应前最多显示 94%
let _uploadProgressTimer = null
function startUploadEstimatedProgress() {
  if (_uploadProgressTimer) clearInterval(_uploadProgressTimer)
  const startedAt = Date.now()
  uploadPct.value = 2
  _uploadProgressTimer = setInterval(() => {
    const seconds = (Date.now() - startedAt) / 1000
    // 前 10 秒到约 42%，10~30 秒到约 78%，之后缓慢逼近 94%，避免文件传完就误显 100%。
    const target = seconds <= 10
      ? 2 + seconds * 4
      : (seconds <= 30 ? 42 + (seconds - 10) * 1.8 : 78 + (seconds - 30) * 0.45)
    uploadPct.value = Math.min(94, Math.max(uploadPct.value, Math.floor(target)))
  }, 500)
}
function stopUploadEstimatedProgress(completed) {
  if (_uploadProgressTimer) clearInterval(_uploadProgressTimer)
  _uploadProgressTimer = null
  if (completed) uploadPct.value = 100
}
const polling = ref(false)
const extracting = ref(false)
const overlayPhase = ref('asr') // AI 工作遮罩阶段：转写=asr / 生成纪要=gen（一键流程内切换）
const asrAudioDurSec = ref(0)    // 本次待转写录音总时长(秒)
const asrFileSizeBytes = ref(0)  // 本次待转写文件大小(bytes)，用于更准确估算处理耗时
const taskId = ref('')
const asrStatus = ref('')
const processText = ref('正在上传录音...')
const finishText = ref('录音已转写，规则抽取结果已生成')
const generated = ref(false)
// 识别失败/空转写的行内提示文案（后台转写不弹遮罩，失败靠这条明确告知 + 引导重试）
const asrErrorText = computed(() => {
  if (asrStatus.value === 'empty') return '没识别到说话声，可能录到了静音或太轻。请点「开始录音」重新录一段，靠近麦克风、说清楚些再试。'
  if (asrStatus.value === 'failed') return (processText.value || '录音识别失败') + '　可点下方「重新识别录音」重试'
  return ''
})
// 上传失败的常驻行内提示：toast 一闪即逝，用户回头看不到失败原因；重试上传/重新开录时清掉
const uploadErrorText = ref('')
const extraction = ref(null)

const presetTopics = ref([])
const aiTopics = ref([])
const transcript = ref([])
const transcriptPreview = ref('')
const transcriptFullText = ref('')
const transcriptCharCount = ref(0)
// 是否已有转写内容（转写完成后录音行才显示「查看内容」入口）
const hasTranscript = computed(() => !!(transcriptFullText.value || '').trim() || (transcript.value || []).length > 0)
const transcriptVisible = ref(false)
const transcriptMode = ref('short')
// 转写查看范围：null=整会合并稿；非空={title, segments, preview}=某条录音单独的转写
const transcriptView = ref(null)
const transcriptViewLoading = ref(false)
const transcriptTitle = computed(() => transcriptView.value ? transcriptView.value.title : '语音转录文本')
const transcriptSegs = computed(() => transcriptView.value ? transcriptView.value.segments : transcript.value)
const transcriptPreviewText = computed(() => transcriptView.value ? transcriptView.value.preview : transcriptPreview.value)
const ending = ref(false)
const endReviewVisible = ref(false)
const fieldMeetingEnded = ref(false)
// 会后整理已完成（点过「生成会议纪要/完成会后整理」）：测试期会议仍是 ongoing，
// 首页卡片靠这个本地标记把入口从「会后整理」切成「会议详情」
const reviewCompleted = ref(false)
const exportingAttendanceSheet = ref(false)
// 重做后的「最后一步」：AI 纪要审核
const minutesGenerated = ref(false)   // 是否已生成 AI 纪要草稿
const generatingMinutes = ref(false)  // 生成中（按钮 loading 态）
const minutesGenAt = ref(0)           // 生成纪要发起时刻(ms)；>0且较近=生成在途。整页刷新/硬跳会丢内存 aiTask，用它+服务端从durable信号重建入口
const minutesResuming = ref(false)    // 内存任务已丢、据 durable 标记+服务端轮询恢复出的「生成中」入口
// 后台生成跨页恢复用（本页无 keep-alive，切走再回来是全新实例、generatingMinutes 会丢）：
let _leftWhileGenerating = false // 生成中切走过本页 → 完成时该走全局悬浮条兜底，而非误判「仍在前台看遮罩」
let _bgRehydrated = false        // 本页遮罩是「切回时按后台任务恢复」出来的（非本实例发起）→ 由 watch(aiTask) 收尾

// 切回本会议页时按全局后台任务恢复界面：仍在生成→重亮遮罩；已生成→显示「查看纪要」态。
// 不恢复的话，切走再回来遮罩没了、悬浮条又在发起页隐藏，用户会以为任务中止了。
function resumeBgAiTask() {
  const mine = aiTask.targetPath && aiTask.targetPath.indexOf('meetingId=' + meetingId.value) >= 0
  if (!mine) return
  if (aiTask.active) {
    overlayPhase.value = 'gen'
    generatingMinutes.value = true
    _bgRehydrated = true
  } else if (aiTask.done) {
    minutesGenerated.value = true
  }
}

// 后台生成结束（完成/失败/清除）→ 收起「切回恢复」出来的遮罩，并把纪要状态反映到本页。
watch(() => aiTask.active, (act) => {
  if (act || !_bgRehydrated) return
  _bgRehydrated = false
  generatingMinutes.value = false
  if (aiTask.done) { minutesGenerated.value = true; persistQuickState() }
  else loadDetail() // 被清除/失败 → 重新拉真实状态
})
// 本页全屏遮罩(AiWorkingOverlay :active=generatingMinutes)是否正盖着这个任务 → 同步给全局。
// 遮罩在=悬浮胶囊隐藏(免重复)；遮罩不在(切走/resume 没恢复)=胶囊自动出来当兜底入口。
watch(generatingMinutes, (v) => { aiTask.overlayShown = v }, { immediate: true })
// 全局后台纪要任务是否属于本会议（与首页悬浮条同源）——内存任务还在时用它兜底显示「生成中」入口
const bgMinutesGenerating = computed(() => aiTask.active && !!aiTask.targetPath && aiTask.targetPath.indexOf('meetingId=' + meetingId.value) >= 0)
// 会中已不生成纪要：原「AI生成纪要」主按钮及 suppMinutes* 计算属性已移除，纪要一律会后在会议详情页生成
const endReviewHint = computed(() => {
  if (minutesGenerated.value) return '会议纪要已经生成，可直接查看并继续编辑。'
  if (canUpload.value) return '本次未上传的录音不会用于自动生成会议纪要。'
  if (uploading.value) return '录音正在上传，完成后会自动识别。'
  if (polling.value || extracting.value) return '录音正在后台识别，你可以停留在本页等待完成。'
  if (generated.value && hasSavedRecordings.value) return '已整理好会议记录，可以生成会议纪要。'
  if (hasSavedRecordings.value) return '已有会议记录，但还没有完成识别整理。'
  return '没有可用于自动生成纪要的录音记录。'
})
// 会后整理清单第2项：每条议题的简要结果（表决=通过/未通过，讨论/通报=已办与否）
function erTopicResult(t) {
  if (t.voteRequired) {
    // 现场已结束（0722 用户定）：不再显示"待表决"，直接给结论——
    // 签到未过半会议不成立→无效；否则 通过/未通过（不带票数，票数明细在议题弹层里看）
    if (!signinQuorum.value.ready) return { cls: 'fail', text: '表决无效' }
    // 多选一：显示定下的领先选项
    if ((t.decisionType || 'simple') === 'multi_choice') {
      let best = null
      for (const o of (t.options || [])) if (!best || (o.votes || 0) > (best.votes || 0)) best = o
      return t.passed
        ? { cls: 'pass', text: '已定：' + ((best && best.label) || '') }
        : { cls: 'fail', text: '未通过' }
    }
    return t.passed
      ? { cls: 'pass', text: '通过' }
      : { cls: 'fail', text: '未通过' }
  }
  if (t.type === 'notice') return topicBadgeDone(t) ? { cls: 'done', text: '已通报' } : { cls: 'todo', text: '待通报' }
  return topicBadgeDone(t) ? { cls: 'done', text: '已讨论' } : { cls: 'todo', text: '待讨论' }
}

// 清单第3项「会议记录」的状态灯：绿=可生成纪要，蓝=识别在途，黄=有录音没识别完，灰=没录音
const erRecordState = computed(() => {
  if (minutesGenerated.value) return { cls: 'ok', text: '✓ 纪要已生成' }
  if (uploading.value || polling.value || extracting.value) return { cls: 'busy', text: '识别中…' }
  // generated 是本地快照标志：录音被删光后它会残留，必须同时有录音在列表里才算就绪
  if (generated.value && hasSavedRecordings.value) return { cls: 'ok', text: '✓ 已就绪' }
  if (hasSavedRecordings.value || canUpload.value) return { cls: 'warn', text: '● 未完成识别' }
  return { cls: 'muted', text: '暂无录音' }
})
const endReviewRecordText = computed(() => {
  if (canUpload.value) return '有未上传录音'
  if (hasSavedRecordings.value) return '已录 ' + ((recordings.value || []).length) + ' 段'
  return '暂无录音'
})
const endReviewAsrText = computed(() => {
  if (uploading.value) return '上传中'
  if (polling.value || extracting.value) return '识别中'
  if (generated.value && hasSavedRecordings.value) return '识别完成'
  return '未完成识别'
})
// 会后整理四个核对项的收起态（0722 用户定：每项右上角 ▾/▸ 可收起，降低整页密度）
// 第4项会议材料默认收起（列表长、非核对重点），其余默认展开
const erCollapsed = ref({ 4: true })
function toggleErItem(n) { erCollapsed.value = { ...erCollapsed.value, [n]: !erCollapsed.value[n] } }
const leavingToMinutes = ref(false) // 正在结束会议并跳纪要页的过渡态：盖住按钮文案，避免闪现「已生成」
const endReviewPrimaryText = computed(() => {
  if (leavingToMinutes.value) return '正在生成会议纪要…'
  if (minutesGenerated.value) return '查看纪要'
  if (uploading.value) return '上传中…'
  if (polling.value || extracting.value) return '识别中…'
  if (generated.value && hasSavedRecordings.value) return '生成会议纪要'
  return '暂不能生成纪要'
})
const endReviewPrimaryDisabled = computed(() => {
  if (minutesGenerated.value) return false
  if (ending.value || generatingMinutes.value) return true
  if (uploading.value || polling.value || extracting.value) return true
  return !(generated.value && hasSavedRecordings.value)
})
// —— 重做：阶段条 + 折叠态 + 签到跳转动画 ——
// 阶段：1=签到 2=录音 3=生成会议纪要（已生成即到第3步）
const flowStep = computed(() => endReviewVisible.value || minutesGenerated.value
  ? 4
  : (currentStep.value === 1 ? 1 : (meetingPhase.value === 'voting' ? 3 : 2)))

function syncRecordingPageVisibility() {
  meetingRecordingSession.pageVisible = pageActive.value
    && currentStep.value === 2
    && meetingPhase.value === 'recording'
    && !endReviewVisible.value
}
watch([currentStep, meetingPhase, endReviewVisible], syncRecordingPageVisibility)
watch(() => meetingRecordingSession.openRequest, () => {
  if (!meetingRecordingSession.active) return
  currentStep.value = 2
  meetingPhase.value = 'recording'
  endReviewVisible.value = false
  syncRecordingPageVisibility()
  persistQuickState()
})
const recListOpen = ref(true)    // 录音卡内「已录N段」列表默认展开
const rosterPopOpen = ref(false) // 点「已签到 N/M」胶囊弹出的签到名单
const matListOpen = ref(false)   // 会中优先展示录音和主流程，材料按需展开
const siMeetOpen = ref(false)    // 签到页：会议卡是否展开(看议题)
const siRosterOpen = ref(false)  // 签到页：参会名单是否展开
const signinFx = ref(false)      // 签到→录音 跳转动画遮罩
function playSigninFx() {
  signinFx.value = true
  setTimeout(() => { currentStep.value = 2; meetingPhase.value = 'recording'; persistQuickState() }, 220)
  setTimeout(() => { signinFx.value = false }, 520)                     // 一闪而过，别停留
}
const extraOpen = ref(false)          // 「AI 额外发现」是否展开

// 角色 / 资料 / 实时议题 / 录音列表
const isChair = ref(false)
// 主持人：目前仅「主任」（0722 用户定：结束表决/临时加议题只主持人可点；后续放开副主任等再改）
const isHost = ref(false)
const myRoleId = ref(null)
const recordings = ref([])
// 后端按 createdAt DESC 返回（新录音在前）。展示时倒过来：最早录的=第1段，新段依次往后排（符合常规认知）。
// index 仅用于显示编号(详情标题/删除确认)、不参与数组导航，故倒序安全。
const recordingsChrono = computed(() => (recordings.value || []).slice().reverse())
const recordingDetail = ref(null)

function openRecordingDetail(item, index) { recordingDetail.value = { item, index } }
function closeRecordingDetail() { recordingDetail.value = null }
function recordingStatusText(item) {
  if (item && item.asrStatus === 'done') return '已识别'
  if (item && (item.asrStatus === 'failed' || item.asrStatus === 'empty')) return '识别异常'
  if (polling.value || extracting.value) return '识别中'
  return '待识别'
}
// 从录音详情查看「这一条录音」单独的转写（不是整会合并稿）
async function openTranscriptFromDetail() {
  const detail = recordingDetail.value
  if (!detail || !detail.item) return
  const item = detail.item
  const index = detail.index
  closeRecordingDetail()
  await openRecordingTranscript(item, index)
}

// 查看某一段录音的转写（录音详情/会后整理段列表共用）
async function openRecordingTranscript(item, index) {
  const title = '第 ' + (index + 1) + ' 段录音转写'
  transcriptView.value = { title: title, segments: [], preview: '' }
  transcriptMode.value = 'short'
  transcriptVisible.value = true
  if (typeof api.committeeQuickRecordingTranscript !== 'function') {
    // 兜底：旧后端没有单段接口时，退回整会合并稿
    transcriptView.value = null
    return
  }
  transcriptViewLoading.value = true
  try {
    const raw = await api.committeeQuickRecordingTranscript(meetingId.value, item.id)
    // 后端返回 null = 这段的转写结果不在了（旧结果没落库、服务重启被清掉）≠ 真静音。
    // 给「重新识别」按钮当场找回，不再误报"没有识别到内容"。
    if (!raw) {
      transcriptView.value = {
        title: title, segments: [], preview: '',
        emptyText: '这段录音的转写结果不在了，重新识别即可找回。',
        retryId: item.id, retryIndex: index
      }
      return
    }
    const segs = mapTranscript(raw)
    const st = buildTranscriptState(segs)
    // 没识别到内容（静音/太轻/结果为空）→ 明确告知，而不是报错或空白
    const empty = !segs.some(s => s.text && s.text.trim())
    transcriptView.value = {
      title: title,
      segments: empty ? [] : segs,
      preview: empty ? '' : st.transcriptPreview,
      emptyText: empty ? '这段录音没有识别到内容（可能是静音、杂音或声音太轻）。' : ''
    }
  } catch (e) {
    // 取不到也别甩「服务器错误」——在弹层里给可读的说明，并给重新识别入口
    transcriptView.value = {
      title: title, segments: [], preview: '',
      emptyText: '暂时取不到这段的转写内容，可以重新识别。',
      retryId: item.id, retryIndex: index
    }
  } finally {
    transcriptViewLoading.value = false
  }
}

// 单段转写丢失时的当场找回：重新提交这一条的识别并轮询到完成。
// 不复用 transcribeOneAwait——它会动 taskId/asrStatus 等主流程全局态，这里只做局部轮询。
const retranscribing = ref(false)
async function retranscribeSegment() {
  const v = transcriptView.value
  if (!v || !v.retryId || retranscribing.value) return
  const rid = v.retryId
  const idx = v.retryIndex
  retranscribing.value = true
  transcriptView.value = { title: v.title, segments: [], preview: '', emptyText: '正在重新识别，约需十几秒…' }
  try {
    let task = await api.committeeTranscribeRecording(meetingId.value, rid)
    const t0 = Date.now()
    while (task && task.status !== 'done' && task.status !== 'failed') {
      if (Date.now() - t0 > 5 * 60 * 1000) throw new Error('识别超时，请稍后再试')
      await new Promise(r => setTimeout(r, 3000))
      task = await api.committeeQuickRecordingStatus(meetingId.value, task.taskId)
    }
    if (!task || task.status === 'failed') throw new Error(asrFailMessage(task && task.message))
    retranscribing.value = false
    const stillOpen = transcriptVisible.value // 用户等不及关了弹层就别再动界面
    // 先刷新再重开弹层：loadDetail 里的 restoreQuickState 会顺手把转写弹层关掉，顺序反了弹层会闪没
    await loadDetail()
    if (!stillOpen) return
    await openRecordingTranscript({ id: rid }, idx)
  } catch (e) {
    retranscribing.value = false
    if (!transcriptVisible.value) return
    transcriptView.value = {
      title: v.title, segments: [], preview: '',
      emptyText: '重新识别失败：' + ((e && e.message) || '请稍后再试'),
      retryId: rid, retryIndex: idx
    }
  }
}
// 转写页录音回放：当前正在播放的录音 id（null=未播放）
const playingId = ref(null)
const materials = ref([])
const signedInList = ref([])
const voteTotal = ref(0)
const voteNeed = ref(0)
// 主任签到统计：全体参会名单（含未签到）+ 悬浮面板开关
const attendanceList = ref([])
const signinPanelOpen = ref(false)
const signinStats = computed(() => {
  const raw = attendanceList.value || []
  const total = raw.length
  const signedCount = raw.filter(a => a.signedIn).length
  const onsiteCount = raw.filter(a => a.signedIn && a.attendanceMode !== 'remote').length
  const remoteCount = raw.filter(a => a.signedIn && a.attendanceMode === 'remote').length
  const absentCount = raw.filter(a => a.declined).length
  const unconfirmedCount = Math.max(0, total - signedCount - absentCount)
  // 正在录音时，把录音人(本人)排到名单最前，一眼看到谁在录
  let list = raw
  if (recActive.value || isPaused.value) {
    list = [...raw]
    const i = list.findIndex(a => a.isSelf)
    if (i > 0) { const [self] = list.splice(i, 1); list.unshift(self) }
  }
  const pct = total ? Math.round((signedCount / total) * 100) : 0
  // 应到人数=全体-请假缺席（0722 用户定）：签到进度按应到显示，请假的人单独括注不占分母。
  // 注意：法定人数(signinQuorum)仍按全体委员算——请假不减少"过半"门槛，否则大面积请假会误判会议有效
  const expectedCount = Math.max(0, total - absentCount)
  return { total, expectedCount, signedCount, onsiteCount, remoteCount, absentCount, unconfirmedCount, pct, list }
})

const signinQuorum = computed(() => {
  const total = signinStats.value.total
  const signedCount = signinStats.value.signedCount
  if (!total) return { total: 0, ready: false, required: 0, remaining: 0, text: '' }
  const required = Math.floor(total / 2) + 1
  const remaining = Math.max(0, required - signedCount)
  const ready = remaining === 0
  return {
    total,
    required,
    remaining,
    ready,
    text: ready
      ? `已达到召开人数要求`
      : `未达到召开人数要求，还差${remaining}名委员`
  }
})

function formatSigninDateTime(date, time) {
  const dateText = String(date || '').trim()
  const timeText = String(time || '').trim().replace(/:\d{2}$/, '')
  const match = dateText.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/)
  if (!match) return [dateText, timeText].filter(Boolean).join(' ')
  return `${match[1]}年${Number(match[2])}月${Number(match[3])}日${timeText ? ` ${timeText}` : ''}`
}
const addTopicVisible = ref(false)
const newTopicForm = reactive({ title: '', type: 'discussion', decisionType: 'none', options: [], content: '' })

// 选片高亮（this._transcribingRecordingId）改为响应式以驱动样式
const _transcribingRecordingId = ref('')

// 转写页多选：已勾选待转写的录音 id；已转写(done)的会被后端自动并入，无需勾选
const pickedIds = ref([])
// 已知录音 id（非响应式）：用于在 loadDetail 时只对“新出现”的录音默认勾选，保留用户手动取消
let _knownRecordingIds = new Set()
const hasDoneRecordings = computed(() => (recordings.value || []).some(r => r.asrStatus === 'done'))

// 隐藏文件输入（chooseAudioFile 用）
const audioFileInput = ref(null)

// onLoad/this 级上下文
let _pollCount = 0
let _asrDoneHandled = false
let _recognizeAfterUpload = false // 「上传录音」标记：上传成功后只识别、不生成
let _pollTimer = null
let _booted = false
let _attendanceTimer = null // 主任签到进度的轻量轮询
let _playAudio = null       // 转写页录音回放用的 HTMLAudioElement

// ═══════════════════════════════════════════════
// 生命周期
// ═══════════════════════════════════════════════
function initFromRoute() {
  type.value = route.query.type === 'owner' ? 'owner' : 'committee'
  meetingId.value = route.query.meetingId || route.query.id
  detail.value = null
  loadError.value = ''
  if (type.value === 'owner') {
    toast({ title: '业主大会模块已停用', icon: 'none' })
    setTimeout(function () { navigateBack() }, 500)
    return false
  }
  if (!meetingId.value) {
    loadError.value = '缺少会议编号，请返回首页重新进入。'
    return false
  }
  loadDetail()
  return true
}

onMounted(() => {
  pageActive.value = true
  syncRecordingPageVisibility()
  // onLoad(options)
  initFromRoute()
  _pollCount = 0
  _booted = true
  if (typeof window !== 'undefined') window.addEventListener('beforeunload', _beforeUnloadGuard)
  // useRecorder 内部已管理录音生命周期与计时，无需 initRecorder
  // 主任端每 12s 轻量刷新签到进度（委员陆续签到时进度自动增加）
  _attendanceTimer = setInterval(refreshAttendance, 12000)
})

watch(() => route.query.meetingId, (val, oldVal) => {
  if (!_booted || String(val || '') === String(oldVal || '')) return
  // KeepAlive 缓存期间用户去了别的页面：query 里 meetingId 消失≠本页要重初始化。
  // 此时乱跑 initFromRoute 会把 meetingId 清成 undefined → 切身份时"丢弃录音"回调
  // 因 meetingId 失配而不执行，后台 MediaRecorder 和 beforeunload 守卫一直活着，
  // 反过来拦死首页卡片/开始会议的硬跳转（点了没反应的根因）。
  if (route.path !== '/meeting-live-quick' || !val) return
  initFromRoute()
})

// onShow → onMounted 首跑 + onActivated（保持热切回页刷新；但避免与 onMounted 重复首跑）
onActivated(() => {
  pageActive.value = true
  syncRecordingPageVisibility()
  // KeepAlive 复用实例：上次「生成纪要/完成会后整理」跳走时留下的过渡态必须复位，
  // 否则回到会后整理后两个按钮一个卡在"正在生成…"、一个被 ending 锁死点不动（0722 实测）
  ending.value = false
  leavingToMinutes.value = false
  // 守卫只在本页处于前台时生效（见 onDeactivated 的移除说明）
  if (typeof window !== 'undefined') window.addEventListener('beforeunload', _beforeUnloadGuard)
  if (!_booted) return
})

// onUnload → onUnmounted
onUnmounted(() => {
  pageActive.value = false
  if (generatingMinutes.value) _leftWhileGenerating = true // 生成中切走 → 完成走全局悬浮条兜底
  aiTask.overlayShown = false // 本页遮罩随本页销毁 → 交还给全局悬浮胶囊兜底（保证切走后有入口）
  persistQuickState()
  clearPoll()
  clearMinutesPoll()
  stopUploadEstimatedProgress(false)
  if (_attendanceTimer) { clearInterval(_attendanceTimer); _attendanceTimer = null }
  if (_recBeatTimer) { clearInterval(_recBeatTimer); _recBeatTimer = null }
  if (_playAudio) { try { _playAudio.pause() } catch (e) {} _playAudio = null }
  if (typeof window !== 'undefined') {
    window.removeEventListener('beforeunload', _beforeUnloadGuard)
  }
  rec.reset() // 释放麦克风
  unregisterRecordingDiscard()
  meetingRecordingSession.active = false
  meetingRecordingSession.pageVisible = false
})

// onHide → onDeactivated
onDeactivated(() => {
  pageActive.value = false
  persistQuickState()
  syncRecordingPageVisibility()
  // 离开本页即移除 beforeunload 守卫（onActivated 回来时再挂）。
  // 本页被 KeepAlive 缓存、onUnmounted 不触发——守卫若一直留在 window 上，
  // 录音/暂停期间会拦截全应用的硬跳转（首页「进入会议」、通知页「开始会议」都点不动）。
  // 离开本页后录音切片仍持续落盘(IndexedDB)，真被硬跳杀掉也能恢复，无需靠守卫硬拦。
  if (typeof window !== 'undefined') window.removeEventListener('beforeunload', _beforeUnloadGuard)
})

// ═══════════════════════════════════════════════
// 方法（自 Page 方法 1:1 迁移）
// ═══════════════════════════════════════════════
function quickStateKey() {
  // 按「会议 + 角色」隔离：同设备切换委员/主任测试时，各角色的页面状态互不串味
  // （此前共用一个键，主任进来会恢复出委员留下的步骤/阶段状态）
  const role = getStorage('activeRole', null) || {}
  return QUICK_STATE_PREFIX + meetingId.value + '_r' + (role.id || 0)
}

function persistQuickState(extra) {
  if (!meetingId.value || !detail.value || detail.value.stage !== 'ongoing') return
  const state = Object.assign({
    meetingId: meetingId.value,
    savedAt: Date.now(),
    currentStep: currentStep.value,
    meetingPhase: meetingPhase.value,
    fieldMeetingEnded: fieldMeetingEnded.value,
    reviewCompleted: reviewCompleted.value,
    endReviewVisible: endReviewVisible.value, // 会后整理页可见态：刷新/重进后直接回到整理页，不落回录音页
    generated: generated.value,
    minutesGenerated: minutesGenerated.value, // 纪要已生成标志：持久化，避免回首页再进来退回「生成会议纪要」单键
    minutesGenAt: minutesGenAt.value,         // 生成发起时刻：整页刷新丢了内存任务后，据此判断"生成在途"并轮询服务端恢复入口
    taskId: taskId.value,
    asrStatus: asrStatus.value,
    processText: processText.value,
    finishText: finishText.value,
    extraction: extraction.value,
    presetTopics: presetTopics.value,
    aiTopics: aiTopics.value,
    transcript: transcript.value,
    transcriptPreview: transcriptPreview.value,
    transcriptFullText: transcriptFullText.value,
    transcriptCharCount: transcriptCharCount.value
  }, extra || {})
  setStorage(quickStateKey(), state)
}

function restoreQuickState(signedInArg) {
  let saved = getStorage(quickStateKey(), null)
  if (!saved || String(saved.meetingId) !== String(meetingId.value)) return false

  const transcriptVal = saved.transcript || []
  const transcriptState = buildTranscriptState(transcriptVal)
  // 原 step3(录音转写)/step4(整理纪要) 已并入录音页一键「生成会议纪要」；页面只剩 签到(1)/录音(2) 两步。
  // 签到过就停录音页，否则回签到步——绝不再恢复到"三步全完成、只剩会议议题"的空页。
  const step = signedInArg ? 2 : 1

  currentStep.value = step
  meetingPhase.value = saved.meetingPhase === 'voting' ? 'voting' : 'recording'
  fieldMeetingEnded.value = !!saved.fieldMeetingEnded
  reviewCompleted.value = !!saved.reviewCompleted
  endReviewVisible.value = !!saved.endReviewVisible && !!saved.fieldMeetingEnded // 现场已结束才可能恢复整理页
  generated.value = !!saved.generated
  minutesGenerated.value = !!saved.minutesGenerated // 恢复「纪要已生成」→ 显示 查看纪要/重新生成 两键，而非「生成会议纪要」
  minutesGenAt.value = saved.minutesGenAt || 0      // 恢复「生成发起时刻」→ reconcileMinutesState 据此兜底恢复"生成中"入口
  if (saved.generated) recognizedIds.value = (recordings.value || []).map(r => r.id) // 恢复的转写已覆盖当前录音
  taskId.value = saved.taskId || ''
  asrStatus.value = saved.asrStatus || ''
  processText.value = saved.processText || processText.value
  finishText.value = saved.finishText || finishText.value
  extraction.value = saved.extraction || null
  presetTopics.value = saved.presetTopics && saved.presetTopics.length ? saved.presetTopics.map(decorateEvidence) : presetTopics.value
  aiTopics.value = (saved.aiTopics || []).map(decorateEvidence)
  transcript.value = transcriptVal
  transcriptVisible.value = false
  uploading.value = false
  polling.value = false
  extracting.value = false
  transcriptFullText.value = transcriptState.transcriptFullText || saved.transcriptFullText || ''
  transcriptPreview.value = transcriptState.transcriptPreview || saved.transcriptPreview || ''
  transcriptCharCount.value = transcriptState.transcriptCharCount || saved.transcriptCharCount || 0

  if (signedInArg && saved.taskId && !saved.generated && saved.asrStatus === 'done') {
    _asrDoneHandled = false
    handleAsrDone({ taskId: saved.taskId, status: 'done' })
  } else if (signedInArg && saved.taskId && !saved.generated && saved.asrStatus !== 'failed') {
    currentStep.value = 2 // 原 step3「录音转写」页已删；停在录音页，转写进度由 AI 工作遮罩展示
    polling.value = true
    processText.value = saved.processText || statusText(saved.asrStatus || 'pending')
    startPoll(saved.taskId)
  }
  return true
}

function clearQuickState() {
  if (!meetingId.value) return
  removeStorage(quickStateKey())
}

async function loadDetail() {
  try {
    loadError.value = ''
    const d = await api.committeeDetail(meetingId.value)
    const raw = (d.record && d.record.topics) || []
    const presets = raw.map(t => mapTopic(null, t))
    const self = getSelfAttendance(d)
    const isSigned = !!(self && self.signedIn)
    const role = getStorage('activeRole', null)
    const roleId = role && role.id ? role.id : null
    const recObj = d.record || {}
    const recs = recObj.recordings || []
    const signed = (recObj.attendances || []).filter(function (a) { return a.signedIn })
    const total = signed.length

    detail.value = d
    presetTopics.value = presets
    selfAttendance.value = self
    signedIn.value = isSigned
    isChair.value = d.userView === 'chair'
    isHost.value = !!(role && role.role === '主任')
    myRoleId.value = roleId
    recordings.value = recs
    reconcilePickedIds(recs)
    materials.value = d.materials || []
    signedInList.value = signed
    attendanceList.value = recObj.attendances || []
    voteTotal.value = total
    voteNeed.value = Math.floor(total / 2) + 1
    // 已签到但尚未进入本次前端会话时仍停在入口页，由用户点击进入；
    // 录音不自动开始（0721 方案A），麦克风权限在主任点「开始录音」时才申请。

    if (d.stage === 'ongoing') {
      // 上传/识别在途时不重放本地快照、不从服务端恢复历史成果——loadDetail 会在流程中被调用
      // （上传成功刷新列表、加议题后刷新等），此时重放会把 polling/generated 盖掉：
      // 识别中的状态条消失、上传按钮解禁，第二段就能在识别中并发上传（测试矩阵 S18 抓到的真 bug）
      const flowBusy = uploading.value || polling.value || extracting.value
      const restored = flowBusy ? true : restoreQuickState(isSigned)
      if (isSigned && !flowBusy && (!restored || (!generated.value && !taskId.value))) tryRestoreGeneratedFromServer()
      resumeBgAiTask() // 切回本页时恢复后台生成的遮罩/完成态（内存 aiTask 还在时）
      reconcileMinutesState() // 内存任务丢失(硬跳/刷新)兜底：从服务端+本地durable标记重建"生成中/查看"入口
      maybeAutoRecognizePending() // 委员传的/中断丢任务的"待识别"段：主任端静默补识别（内部有忙时守卫）
    } else {
      clearQuickState()
    }
  } catch (e) {
    const msg = (e && e.message) || '会议信息加载失败'
    loadError.value = msg === '会议不存在'
      ? '这场会议已不存在或已被重新创建，请返回首页重新进入。'
      : msg
    toast({ title: msg === '会议不存在' ? '会议不存在，请返回首页' : '加载失败', icon: 'none' })
  }
}

// 轻量刷新签到名单/进度 + 录音列表，不动步骤机——供参会人看实时签到（定时 + 手动）。
// 录音列表跟着刷：别人（委员/另一位主任）新传的段 12s 内出现在本机列表；
// 主任端顺带触发静默补识别（maybeAutoRecognizePending 内部有忙时/已试过守卫）。
async function refreshAttendance() {
  if (!meetingId.value) return
  if (!detail.value || detail.value.stage !== 'ongoing') return
  try {
    const d = await api.committeeDetail(meetingId.value)
    const atts = (d.record && d.record.attendances) || []
    attendanceList.value = atts
    const signed = atts.filter(function (a) { return a.signedIn })
    signedInList.value = signed
    voteTotal.value = signed.length
    voteNeed.value = Math.floor(signed.length / 2) + 1
    // 上传/识别在途时不动录音列表，避免和本机流程的状态刷新互相踩
    if (!uploading.value && !polling.value && !extracting.value) {
      const recs = (d.record && d.record.recordings) || []
      recordings.value = recs
      reconcilePickedIds(recs)
      maybeAutoRecognizePending()
    }
  } catch (e) { /* 静默：签到刷新失败不打扰主任 */ }
}

async function tryRestoreGeneratedFromServer() {
  if (!meetingId.value || !signedIn.value || generated.value) return
  if (typeof api.committeeQuickTranscript !== 'function' || typeof api.committeeQuickExtract !== 'function') return
  // 恢复是异步的：请求往返期间可能有新的上传/识别流程启动，直接覆盖状态会踩灭在途流程
  const flowBusy = () => uploading.value || polling.value || extracting.value || generatingMinutes.value
  if (flowBusy()) return
  try {
    const transcriptRaw = await api.committeeQuickTranscript(meetingId.value)
    if (flowBusy() || generated.value) return
    const tr = mapTranscript(transcriptRaw)
    if (!tr.length) return
    const ext = await api.committeeQuickExtract(meetingId.value)
    if (flowBusy() || generated.value) return
    const mapped = mapExtraction(ext)
    currentStep.value = 2 // 原 step4「整理纪要」已删；已有转写时停在录音页，用一键「生成会议纪要」继续
    extraction.value = ext
    presetTopics.value = mapped.presetTopics
    aiTopics.value = mapped.aiTopics
    transcript.value = tr
    transcriptVisible.value = false
    uploading.value = false
    polling.value = false
    extracting.value = false
    generated.value = true
    recognizedIds.value = (recordings.value || []).map(r => r.id) // 历史转写已覆盖当前录音
    finishText.value = '已恢复上次转写与议题匹配结果'
    const tState = buildTranscriptState(tr)
    transcriptFullText.value = tState.transcriptFullText
    transcriptPreview.value = tState.transcriptPreview
    transcriptCharCount.value = tState.transcriptCharCount
    persistQuickState()
  } catch (e) {}
}

// —— 纪要状态兜底恢复：内存里的后台 aiTask 一旦因整页刷新/硬跳丢失，切回本页就没了「生成中/查看」入口。
//    这里改从两处 durable 信号重建，不依赖内存单例：①服务端是否已有纪要 ②本地持久的"生成发起时刻"。
let _minutesPollTimer = null
function clearMinutesPoll() { if (_minutesPollTimer) { clearInterval(_minutesPollTimer); _minutesPollTimer = null } }
async function reconcileMinutesState() {
  if (!meetingId.value || minutesGenerated.value) return
  if (typeof api.committeeMinutes !== 'function') return
  // ① 服务端已有纪要 → 直接给「查看纪要」入口（最可靠，硬跳/换实例都不丢）
  let hasServer = false
  try { const t = await api.committeeMinutes(meetingId.value); hasServer = !!(t && String(t).trim()) } catch (e) {}
  if (hasServer) { minutesGenerated.value = true; minutesGenAt.value = 0; minutesResuming.value = false; persistQuickState(); return }
  // 内存任务仍在(遮罩/悬浮条已接管)则不插手
  if (generatingMinutes.value || bgMinutesGenerating.value) return
  // ② 问服务端任务状态（权威，跨刷新/换设备/隔天，不依赖前端内存单例）
  if (typeof api.committeeMinutesStatus === 'function') {
    let st = null
    try { st = await api.committeeMinutesStatus(meetingId.value) } catch (e) {}
    const s = st && st.status
    if (s === 'running' || s === 'success') { startMinutesResumePoll(); return } // 生成中/刚完成待落库 → 轮询到正文出现即转"查看"
    if (s === 'failed') { minutesGenAt.value = 0; persistQuickState(); return }    // 失败 → 让用户可重新生成
    if (s === 'none') { minutesGenAt.value = 0; persistQuickState(); return }      // 从没生成过 → 生成入口
  }
  // ③ 旧后端无 minutes-status 接口 → 退回本地"生成发起时刻"兜底
  const RECENT = 5 * 60 * 1000
  if (minutesGenAt.value && (Date.now() - minutesGenAt.value) < RECENT) startMinutesResumePoll()
  else if (minutesGenAt.value) { minutesGenAt.value = 0; persistQuickState() }
}
// 轮询服务端直到纪要出现(生成完成)或超时(放弃 → 用户可重新生成)。仅在内存任务已丢时用。
function startMinutesResumePoll() {
  minutesResuming.value = true
  clearMinutesPoll()
  const deadline = (minutesGenAt.value || Date.now()) + 5 * 60 * 1000
  const tick = async () => {
    if (minutesGenerated.value) { minutesResuming.value = false; clearMinutesPoll(); return }
    try {
      const t = await api.committeeMinutes(meetingId.value)
      if (t && String(t).trim()) { minutesGenerated.value = true; minutesGenAt.value = 0; minutesResuming.value = false; clearMinutesPoll(); persistQuickState(); return }
    } catch (e) {}
    if (Date.now() > deadline) { minutesResuming.value = false; minutesGenAt.value = 0; clearMinutesPoll(); persistQuickState() }
  }
  _minutesPollTimer = setInterval(tick, 4000)
  tick()
}

function getSelfAttendance(d) {
  const attendances = d && d.record ? (d.record.attendances || []) : []
  return attendances.find(function (a) { return a.isSelf }) || null
}

async function confirmSignIn() {
  // 已签到（后端 signedIn=true）→ 直接进入录音步
  if (signedIn.value) {
    await enterLiveMeeting()
    return
  }
  const res = await showModal({
    title: '',
    content: '请确认本人已到达会议地点\n参加会议',
    confirmText: '确认',
    cancelText: '取消',
    size: 'attendance'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfAttend(meetingId.value, 'onsite', false)
    signedIn.value = true
    loadDetail()
    playSigninFx()   // 播放「签到成功 → 进入录音」跳转动画，动画中途切到录音步
    // 0721 用户定（方案A）：不再自动开始录音——主任进入后手动点「开始录音」，
    // 与录音卡的显式按钮一致；忘了点由「处理议题/结束会议」的状态3提示兜底
  } catch (e) {
    toast({ title: e.message || '确认失败', icon: 'none' })
  }
}

async function enterLiveMeeting() {
  currentStep.value = 2
  if (meetingPhase.value !== 'voting') meetingPhase.value = 'recording'
  persistQuickState()
  // 0721 用户定（方案A）：进入会议不再自动开始录音，由主任手动点「开始录音」
}

async function enterVotingPhase() {
  if (phaseChanging.value) return
  // 状态3：没有进行中的录音、也没有已上传的录音 → 先确认（很可能是还没开始录音）。
  // 状态1/2/4（有进行中 或 有已上传）都直接进入：进入后录音状态照旧保留，可随时返回。
  // 仅主任弹：canUpload 是"本设备"的录音状态，委员设备看不到主任正在录——对委员弹"还没开始录音"是误导
  if (isChair.value && !canUpload.value && !hasSavedRecordings.value) {
    const res = await showModal({
      title: '还没有开始录音',
      content: '本次现场还没有录音。确定现在就去处理议题吗？也可以先返回开始录音。',
      confirmText: '确认进入',
      cancelText: '返回录音'
    })
    if (!res.confirm) return
  }
  phaseChanging.value = true
  try {
    // 只切换议题处理页面，录音继续进行；最终结束会议时再统一停止并保存。
    meetingPhase.value = 'voting'
    persistQuickState()
    toast({ title: '已进入议题处理', icon: 'success' })
  } finally {
    phaseChanging.value = false
  }
}

// 录音进行中（含暂停、尚未上传）时拦截刷新/关页，避免内存里的录音被丢弃
function _beforeUnloadGuard(e) {
  if (rec.recording.value) { e.preventDefault(); e.returnValue = '' }
}

async function toggleRecord() {
  if (type.value !== 'committee') {
    toast({ title: '快速录音暂先支持业委会会议', icon: 'none' })
    return
  }
  if (isSelfRemote.value) {
    toast({ title: '线上参会无需现场录音', icon: 'none' })
    return
  }
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (!rec.supported.value) {
    toast({ title: '当前浏览器不支持录音（需 HTTPS 且允许麦克风）', icon: 'none' })
    return
  }
  if (uploading.value) return // 转写在后台跑不拦录音；仅上传中(占用同段)时不响应

  // 录音中且未暂停 → 暂停（个别 iOS 不支持暂停，pause 静默失败时提示用户）
  if (rec.recording.value && !rec.paused.value) {
    rec.pause()
    if (!rec.paused.value) {
      toast({ title: '本设备不支持暂停，可直接点「上传录音」', icon: 'none' })
    }
    return
  }
  // 别人正在录音 → 先提示（两路同录会导致转写重复内容），用户确认后才继续
  if (!(await confirmOthersRecording())) return
  // 暂停态的「继续录音/重新录音」由模板里并排按钮独立处理，本函数只在非暂停态被调用
  // ⚠ 本地有未上传的录音（如中断后选了「稍后处理」）→ 必须先确认，开新录会把它丢掉。
  //   这条检查必须放在 hasSavedRecordings 捷径之前——否则已传过段的会议里会不加确认直接丢（真机踩过）
  if (rec.hasRecording.value) {
    const res = await showModal({
      title: '有一段录音还没上传',
      content: '手里这段录音还没有上传。直接开始新录音会丢掉它。建议先点「上传录音」保存这段，再录新的。',
      confirmText: '丢弃并重新录',
      cancelText: '先不录'
    })
    if (res.confirm) await startRecord()
    return
  }
  // 已上传过录音 → 本次是“新增录音”，旧录音已存服务器、不会丢，直接开录、无需覆盖确认
  await startRecord()
}

async function startRecord(opts) {
  uploadErrorText.value = '' // 用户选择重录/续录：旧的上传失败提示不再适用
  // 后台正在转写上一段 → 只启动新录音，绝不 clearPoll / 重置转写状态，避免打断后台转写
  const bgTranscribing = polling.value || extracting.value
  // 已有已上传的段或已有转写成果 → 这是「续录下一段」，不是「推倒重来」：
  // 历史段的转写/议题成果必须保留（否则第一段的「查看内容」会凭空消失——真机实测踩过）
  const continuing = hasSavedRecordings.value || generated.value
  if (!bgTranscribing && !continuing) {
    clearPoll()
    clearQuickState()
    generated.value = false
    extraction.value = null
    aiTopics.value = []
    transcript.value = []
    transcriptPreview.value = ''
    transcriptFullText.value = ''
    transcriptCharCount.value = 0
    transcriptVisible.value = false
    uploading.value = false
    taskId.value = ''
  }
  if (!bgTranscribing) {
    _recognizeAfterUpload = false // 重新开录，清掉可能残留的"上传后自动识别"标记
    asrStatus.value = '' // 新一段开录，清上一段的错误横幅
  }
  processText.value = '正在录音...'
  try {
    // 中断续录(resume)：不 reset——旧段落地会话要留给后台上传成功后再清；start 自会重置录音内部状态
    if (!(opts && opts.resume)) rec.reset()
    await rec.start({ persistKey: 'committee-' + meetingId.value, keepPrevPersist: !!(opts && opts.resume) }) // 切片落盘：页面被杀后可恢复
  } catch (e) {
    console.error('[startRecord] 录音启动失败:', e && e.name, e && e.message, e)
    showModal({ title: '无法开始录音', content: micErrorText(e), showCancel: false, confirmText: '知道了' })
  }
}

// 录音启动失败 → 按错误类型给可操作的中文提示（替代原来笼统一句）；微信/企业微信环境额外提示用浏览器打开。
// TimeoutError 来自 useRecorder 的 getUserMedia 超时兜底——正是"点了没反应"的那种挂起。
function micErrorText(e) {
  const name = (e && e.name) || ''
  const ua = (typeof navigator !== 'undefined' && navigator.userAgent) || ''
  const isWx = /(MicroMessenger|wxwork|WeChat)/i.test(ua)
  if (typeof window !== 'undefined' && !window.isSecureContext) return '需在 HTTPS 或 localhost 环境下才能录音。'
  if (name === 'NotAllowedError' || name === 'SecurityError') return '麦克风权限被拒绝。请在浏览器或系统设置里允许麦克风，然后重试。'
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') return '未检测到麦克风设备。'
  if (name === 'NotReadableError' || name === 'TrackStartError') return '麦克风被其它应用占用，请关闭占用后重试。'
  if (name === 'TimeoutError') return isWx
    ? '调起麦克风无响应。微信内录音常受限——请点右上角「···」选择“在浏览器打开”，再进入录音。'
    : '调起麦克风无响应。请检查麦克风权限，或换用系统浏览器（Safari/Chrome）打开本页再录。'
  return (isWx ? '微信内可能不支持网页录音，建议用浏览器打开。' : '') + '无法开始录音：' + (name || (e && e.message) || '未知错误')
}

function fmt(s) {
  const m = Math.floor(s / 60)
  return String(m).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0')
}

// 落盘切片恢复：上次录音时页面被杀（微信杀后台/手滑刷新），切片还躺在本机 IndexedDB 里 →
// 详情加载后查一次孤儿会话，提示主任恢复上传（走正常上传转写链路），成功即清盘。
async function checkOrphanRecordings() {
  // 方案B后委员也可上传，恢复弹窗对所有已签到角色开放（此前仅主任，委员的中断录音会烂在本机）
  if (!detail.value || detail.value.stage !== 'ongoing') return
  if (rec.recording.value) return
  const sessions = await recStore.listSessions('committee-' + meetingId.value)
  if (!sessions.length) return
  const totalSec = sessions.reduce((s, x) => s + x.chunkCount, 0)
  if (totalSec < 5) { sessions.forEach(s => recStore.clearSession(s.key)); return } // 几秒的碎片没有恢复价值
  const durText = totalSec >= 60 ? '约 ' + Math.floor(totalSec / 60) + ' 分钟' : '约 ' + totalSec + ' 秒'
  const r = await showModal({
    title: '发现未上传的录音',
    content: '上次录音中断时，已自动保存' + durText + '的内容在本手机上。选择“稍后处理”不会删除录音，您可以稍后再次恢复并上传。',
    confirmText: '恢复并上传',
    cancelText: '稍后处理'
  })
  if (!r.confirm) return // 保留切片：下次进来再问，7 天后自动清理
  for (const s of sessions) {
    const asm = await recStore.assembleSession(s.key)
    if (!asm) { recStore.clearSession(s.key); continue }
    const file = new File([asm.blob], 'recovered.' + extFromBlob(asm.blob), { type: asm.mimeType })
    try {
      await uploadRecordingFile(file, asm.durationSec) // 上传成功自动触发后台转写
      recStore.clearSession(s.key)
    } catch (e) { /* 上传失败保留切片，用户可稍后重进再试 */ }
  }
}
// 详情首次加载完成后跑一次恢复检查（isChair/stage 此时才可判）
const _stopOrphanWatch = watch(() => (detail.value && detail.value.stage) || '', (st) => {
  if (!st) return
  _stopOrphanWatch()
  checkOrphanRecordings()
})

// 录音异常中断（来电抢占麦克风/切后台被挂起）：立即收段保住已录内容（切片本就实时落盘），
// 等用户回到页面弹显眼确认框——「恢复录音」一键完成：上传已保住的段 → 自动开新段接着录。
// 不做无确认的自动续录（用户明确要求由人拍板），杜绝"界面还在计时、实际早没在录"的假录音。
function waitPageVisible() {
  if (typeof document === 'undefined' || document.visibilityState === 'visible') return Promise.resolve()
  return new Promise((resolve) => {
    const on = () => {
      if (document.visibilityState !== 'visible') return
      document.removeEventListener('visibilitychange', on)
      resolve()
    }
    document.addEventListener('visibilitychange', on)
  })
}
watch(() => rec.interrupted.value, async (v) => {
  if (!v) return
  let saved = null
  try { saved = await rec.stop() } catch (e) { /* 收段失败下面按未保住提示 */ }
  const recordedText = rec.timeText.value
  const kept = !!(saved && saved.blob && saved.blob.size > 0)
  await waitPageVisible() // 人还在接电话/在别的App时不弹，回到页面第一眼看到
  const r = await showModal({
    title: '录音已中断',
    content: kept
      ? '切出微信或来电会中断录音。已录的 ' + recordedText + ' 已保存好，不会丢。\n\n点「继续录音」：立即接着录，刚才这段自动上传，最后合并成一份完整记录。\n点「稍后处理」：先不录，这段仍保留，可稍后点「上传录音」保存。'
      : '切出微信或来电会中断录音，这段没有录到内容。要重新开始录音吗？',
    confirmText: kept ? '继续录音' : '重新开始录音',
    cancelText: '稍后处理'
  })
  if (!r.confirm) return
  if (!kept) { await startRecord(); return }
  // 「继续录音」：立即开新段（不让会议内容在等待上传时漏录），中断段转后台静默上传。
  // 上传失败不丢：落地切片保留（会话键未清），下次进本页由孤儿恢复弹窗兜底。
  const oldFile = new File([saved.blob], 'recording.' + saved.ext, { type: saved.mimeType })
  const oldDur = saved.durationSec
  const oldSessionKey = rec.getPersistSessionKey()
  await startRecord({ resume: true })
  uploadInterruptedSegment(oldFile, oldDur, oldSessionKey)
})

// 后台静默上传中断段：不动 rec/转写等页面状态（正在录新段），成功后清落盘并刷新录音列表
async function uploadInterruptedSegment(file, durationSec, sessionKey) {
  try {
    await api.committeeUploadRecording(meetingId.value, file, durationSec, () => {})
    if (sessionKey) recStore.clearSession(sessionKey)
    try {
      const d = await api.committeeDetail(meetingId.value)
      const recs = (d.record && d.record.recordings) || []
      if (recs.length) { recordings.value = recs; reconcilePickedIds(recs) }
    } catch (e) { /* 列表刷新失败无妨，下次 loadDetail 补 */ }
    toast({ title: '中断前的录音已上传', icon: 'success' })
  } catch (e) {
    // 内存里的旧段已被新录音顶掉，但落地切片还在——提示用户，重进本页可恢复上传
    toast({ title: '中断前那段上传失败，重新进入本页可恢复', icon: 'none' })
  }
}

async function finishRecord() {
  if (type.value !== 'committee') {
    toast({ title: '快速录音暂先支持业委会会议', icon: 'none' })
    return
  }
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (!rec.recording.value && !rec.hasRecording.value) {
    // 没有新录音可传：若已上传过则明确告知，避免重复上传同一段
    if (hasSavedRecordings.value) {
      showModal({
        title: '这段录音已上传',
        content: '当前没有新的录音内容。如需接着录，请点"继续录音"，转写时会和已上传的自动合并为一份。',
        showCancel: false,
        confirmText: '知道了'
      })
    } else {
      toast({ title: '请先开始录音', icon: 'none' })
    }
    return
  }
  if (uploading.value) return
  // 上一段还在后台识别 → 本段先留在本地(不并发第二个转写)，等识别完再传
  if (polling.value || extracting.value) {
    toast({ title: '上一段还在识别，识别完就能上传这段', icon: 'none' })
    return
  }

  // 仍在录音会话中 → 先停拿产出上传（对齐原 recorderStarted 分支：stop 后上传）
  if (rec.recording.value) {
    const r = await rec.stop()
    if (!r) {
      toast({ title: '录音为空，请重试', icon: 'none' })
      return
    }
    if (r.durationSec) asrAudioDurSec.value = r.durationSec
    if (r.blob) asrFileSizeBytes.value = r.blob.size
    await uploadRecording(r.blob, r.ext, r.durationSec)
    return
  }
  // 会话已停止但已有上次产出（对齐原 tempFilePath 分支）→ 直接复用已生成的 blob 上传
  const blob = rec.getBlob && rec.getBlob()
  if (blob && blob.size > 0) {
    await uploadRecording(blob, extFromBlob(blob), asrAudioDurSec.value)
    return
  }
  toast({ title: '录音为空，请重试', icon: 'none' })
}

// 继续录音：恢复同一录音会话（chunks 持续累积），整段始终是一个文件，不另存为新文件
function resumeRecording() {
  if (uploading.value) return
  if (rec.recording.value && rec.paused.value) rec.resume()
}

// 重新录音：放弃当前这段，从头开始（需确认）
async function restartRecording() {
  if (uploading.value) return
  const res = await showModal({
    title: '',
    content: '将清除当前录音片段，确认重录？',
    confirmText: '重新录音',
    cancelText: '取消',
    contentBold: true,
    emphasizeConfirm: true
  })
  if (res.confirm) await startRecord()
}

// 从已生成 blob 的 mimeType 推扩展名（与 useRecorder.extFromMime 对齐）
function extFromBlob(blob) {
  const t = (blob && blob.type) || ''
  if (t.includes('mp4')) return 'm4a'
  if (t.includes('ogg')) return 'ogg'
  if (t.includes('mpeg')) return 'mp3'
  return 'webm'
}

// 不现场录音，直接选一个已有的音频文件上传
function chooseAudioFile() {
  if (type.value !== 'committee') {
    toast({ title: '快速录音暂先支持业委会会议', icon: 'none' })
    return
  }
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (uploading.value || polling.value || extracting.value) return
  if (audioFileInput.value) {
    audioFileInput.value.value = '' // 允许重复选同一文件
    audioFileInput.value.click()
  }
}

async function onAudioFileChange(e) {
  const files = Array.from((e.target && e.target.files) || [])
  if (!files.length) {
    toast({ title: '未选择文件', icon: 'none' })
    return
  }
  // 支持一次多选：逐个上传保存，全部传完后自动统一识别
  let ok = 0
  for (const file of files) {
    asrFileSizeBytes.value = file.size
    const dur = await getAudioDuration(file) // 读取已选音频的时长（秒），用于转写页展示
    if (dur > 0) asrAudioDurSec.value = dur
    try {
      await uploadRecordingFile(file, dur)
      ok++
    } catch (err) { /* 单个失败 uploadRecordingFile 内已提示，继续传下一个 */ }
  }
  if (files.length > 1) toast({ title: '已上传 ' + ok + '/' + files.length + ' 个录音文件', icon: 'success' })
  // 全部文件上传完 → 自动统一识别（后台静默，不弹遮罩）；识别完成后「生成会议纪要」按钮自动变亮可点
  if (ok > 0) uploadAndRecognize()
}

// 读取音频文件时长（秒）：临时 audio 元素读 metadata，失败返回 0
function getAudioDuration(file) {
  return new Promise((resolve) => {
    try {
      const url = URL.createObjectURL(file)
      const a = document.createElement('audio')
      a.preload = 'metadata'
      a.onloadedmetadata = () => { const d = a.duration; URL.revokeObjectURL(url); resolve(isFinite(d) ? Math.round(d) : 0) }
      a.onerror = () => { URL.revokeObjectURL(url); resolve(0) }
      a.src = url
    } catch (err) { resolve(0) }
  })
}

// 录音/选片上传：把 Blob 包成带正确文件名的 File，后端据扩展名识别格式并转 16k 单声道 mp3
async function uploadRecording(blob, ext, durationSec) {
  const file = new File([blob], 'recording.' + ext, { type: blob.type })
  await uploadRecordingFile(file, durationSec)
}

async function uploadRecordingFile(file, durationSec) {
  clearPoll()
  _asrDoneHandled = false
  // 上传期间留在录音页(step 2)，显示"正在上传录音…"，不再自动跳转写页
  uploading.value = true
  startUploadEstimatedProgress()
  uploadErrorText.value = ''
  asrStatus.value = ''
  polling.value = false
  extracting.value = false
  generated.value = false
  transcript.value = []
  transcriptPreview.value = ''
  transcriptFullText.value = ''
  transcriptCharCount.value = 0
  transcriptVisible.value = false
  processText.value = '正在上传录音…'

  persistQuickState()

  try {
    // 上传后返回录音记录信息（带上时长），随后自动触发后台转写
    // 网络上传完成后服务器仍需保存和转码，因此不直接采用 axios 很快到 100% 的发送进度。
    await api.committeeUploadRecording(meetingId.value, file, durationSec, () => {
      // 网络发送进度通常一秒内就会到 100%，显示层仍使用上面的综合预计进度。
    })
    stopUploadEstimatedProgress(true)
    await new Promise(resolve => setTimeout(resolve, 300))
    toast({ title: '录音已上传', icon: 'success' })
    uploading.value = false
    rec.reset() // 清空录音器内存：消除返回录音页时的残留时长，避免把同一段重复上传
    currentStep.value = 2 // 停在录音页：显示"上传录音并生成会议纪要"按钮
    await loadDetail() // 刷新录音列表（await 确保新录音进入列表后再自动识别）
    // 上传成功 → 一律自动后台转写（0722 用户定：委员上传的段也自动识别）。
    // 会中只录、纪要会后在详情页生成；转写静默进行，靠录音卡下方行内提示
    _recognizeAfterUpload = false
    uploadAndRecognize()
  } catch (e) {
    stopUploadEstimatedProgress(false)
    _recognizeAfterUpload = false
    uploading.value = false
    currentStep.value = 2 // 退回录音步，可复用已录音频重试
    processText.value = '上传失败，请重试'
    // 常驻行内提示（toast 一闪就没）：录音还在手里，引导点「上传录音」重试
    uploadErrorText.value = '上传失败：' + ((e && e.message) || '网络异常') + '　录音还在，请点「上传录音」重试'
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}

// ── 转写页多选 ──
// loadDetail 时同步勾选状态：新出现的“未转写”录音默认勾选；保留用户已取消的；移除已转写/已删除的
function reconcilePickedIds(recs) {
  const list = recs || []
  const existing = new Set(list.map(r => r.id))
  const doneIds = new Set(list.filter(r => r.asrStatus === 'done').map(r => r.id))
  const next = (pickedIds.value || []).filter(id => existing.has(id) && !doneIds.has(id))
  list.forEach(r => {
    if (!_knownRecordingIds.has(r.id) && r.asrStatus !== 'done' && !next.includes(r.id)) next.push(r.id)
  })
  list.forEach(r => _knownRecordingIds.add(r.id))
  pickedIds.value = next
}
function isPicked(id) { return (pickedIds.value || []).includes(id) }
function togglePick(id) {
  pickedIds.value = isPicked(id)
    ? pickedIds.value.filter(x => x !== id)
    : pickedIds.value.concat([id])
}
// 点击整行切换勾选；已转写的行默认不勾选(自动并入)，但仍可勾选以重新转写
function onPickRowTap(item) {
  if (!item) return
  if (polling.value || extracting.value) return
  togglePick(item.id)
}

// 「上传录音」：只做 转写→提炼（遮罩 recognize），完成后由遮罩「下一步」进入表决核对（voteCheckFlow），
// 不再一键连做生成纪要——表决结果先经主任确认，再决定是否继续生成。
// 0722 用户定：委员上传的段也自动识别 → 本函数对全部会内角色开放（后端接口已同步放开）
async function uploadAndRecognize() {
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  if (!(recordings.value || []).length) { toast({ title: '还没有录音，请先录一段', icon: 'none' }); return }
  overlayPhase.value = 'recognize'
  // empty(静音段)不重转：重识别只会再花一次钱得到同样的空结果
  pickedIds.value = (recordings.value || []).filter(r => r.asrStatus !== 'done' && r.asrStatus !== 'empty').map(r => r.id)
  await transcribeSelected()
  if (generated.value) {
    recognizedIds.value = (recordings.value || []).map(r => r.id)
    // 后台识别完成（无遮罩）：给一条成功提示，引导去点「生成会议纪要」
    toast({ title: '录音识别完成，可生成会议纪要', icon: 'success' })
  }
}

// 「上传录音」：上传在手录音 → 识别（转写→提炼），不生成。识别完关遮罩，露出「继续上传录音 / 生成会议纪要」两键。
// 0721 用户定（方案B）：委员/记录员也可上传自己录的段（后端本就许可）；识别仍由主任触发。
async function uploadRecordingStep() {
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  // 录音进行中也直接处理：走到这里都是用户明确要上传（结束会议选"上传"/生成纪要确认/点上传按钮），
  // finishRecord 会自动停止录音再上传，无需让用户回录音区手动暂停。
  if (recActive.value) toast({ title: '已自动停止录音，正在上传', icon: 'none' })
  if (rec.recording.value || rec.hasRecording.value) {
    _recognizeAfterUpload = true
    await finishRecord() // 内部：录音中→自动 stop 拿产出→上传；上传成功后触发 uploadAndRecognize
    return
  }
  if (!hasSavedRecordings.value) { toast({ title: '请先开始录音', icon: 'none' }); return }
  if (needRecognize.value) await uploadAndRecognize() // 已上传但未识别 → 直接识别（全员可发起）
}

// 识别完成后的主按钮——「生成会议纪要」：表决核对（AI票数确认/未表决提示）→ 生成。
// （想再补录：直接点上方录音圆圈，此时它显示「继续录音」，录完会重新出现「上传并识别录音」）
async function generateNow() {
  // ② 还有未上传的录音（正在录 / 暂停 / 内存里未上传）→ 先问：停止并上传转写，还是继续录音
  if (canUpload.value) {
    const r = await showModal({
      title: '正在录音中',
      content: '当前还有未上传的录音。要先停止并把这段一起上传、转写吗？（转写完成后，再点「生成会议纪要」）',
      confirmText: '停止并上传转写',
      cancelText: '继续录音'
    })
    // 方案B：确认后只停录+上传+转写；识别完按钮自动变亮，由用户手动再点生成
    if (r.confirm) uploadRecordingStep()
    return
  }
  // ④ 有"未检测到说话声"的空段 → 提示是哪几段，确认后忽略空段、用其余已转写内容生成
  const empties = (recordings.value || []).filter(r => r.asrStatus === 'empty')
  if (empties.length) {
    const nums = empties.map(e => '第 ' + (((recordings.value || []).indexOf(e)) + 1) + ' 段').join('、')
    const r = await showModal({
      title: '部分录音没有声音',
      content: nums + ' 未检测到说话声（可能录到了静音或声音太轻）。确认后将忽略这些段，用其余已转写的内容生成会议纪要。',
      confirmText: '确认生成',
      cancelText: '取消'
    })
    if (!r.confirm) return
  }
  voteCheckFlow()
}

// 真实议题里"需要表决的"（用 detail.record.topics 的 voteRequired，不依赖 ASR 抽取的 presetTopics）
function realVoteTopics() {
  const raw = (detail.value && detail.value.record && detail.value.record.topics) || []
  return raw.filter(t => t.voteRequired)
}
// 真实议题里"还没完成表决的"：voteRequired 且没投票数(voted=0)、也没达成结论(status 非 passed/failed)
function unvotedRealTopics() {
  return realVoteTopics().filter(t => (t.voted || 0) === 0 && t.status !== 'passed' && t.status !== 'failed')
}

// 识别完成后的表决核对（由「上传录音并生成会议纪要」触发）：检查有没有未完成表决的议题，
// 有 → 弹提示；没有 → 直接生成纪要（进纪要页）。表决状态一律以真实议题为准，不靠 ASR 抽取。
// 1) 无表决议题 → 直接生成
// 2) AI 识别到票数 → 确认填写后生成（生成前守卫再提示其它仍未表决的议题）
// 3) 有表决议题但没识别到票数、也没 app 投票 → 弹「还有议题没有表决」提示
// 4) 有表决议题且都已表决 → 直接生成
async function voteCheckFlow() {
  try { await loadDetail() } catch (e) { /* 刷新失败不阻断核对 */ }

  // 1) 没有需要表决的议题 → 直接生成
  if (!realVoteTopics().length) { continueGenerateMinutes(true); return }

  const recognized = (presetTopics.value || []).filter(t => t.voteRequired && t.aiVote)

  // 2) AI 识别到票数 → 确认填写后生成
  if (recognized.length) {
    const lines = recognized.map(t =>
      '「' + t.title + '」同意 ' + t.voteFor + ' · 反对 ' + t.voteAgainst + ' · 弃权 ' + t.voteAbstain
      + '（' + t.aiVoteLabel + '，建议：' + t.resultLabel + '）')
    const r = await showModal({
      title: 'AI 识别到表决结果',
      content: lines.join('\n') + '\n\n要按识别结果自动填写吗？填写后以此计入表决。',
      confirmText: '确认填写',
      cancelText: '暂不填写',
      size: 'large'
    })
    if (r.confirm) {
      recognized.forEach(t => { t.confirmed = true })
      try {
        await api.committeeQuickConfirm(meetingId.value, buildConfirmPayload())
        toast({ title: '已填写表决结果', icon: 'success' })
        await loadDetail()
      } catch (e) {
        toast({ title: (e && e.message) || '保存失败，请重试', icon: 'none' })
        return
      }
      continueGenerateMinutes(false) // 守卫再检查其它没被识别、也没 app 投票的表决议题
      return
    }
    toast({ title: '未填写表决结果，可点议题手动表决后再生成', icon: 'none' })
    return
  }

  // 3) 有表决议题、没识别到票数、也没 app 投票 → 弹提示（仍要生成 / 先去表决）
  const missing = unvotedRealTopics()
  if (missing.length) {
    const r = await showModal({
      title: '还有议题没有表决',
      content: '「' + missing[0].title + '」' + (missing.length > 1 ? '等 ' + missing.length + ' 个表决议题' : '') + '还没有表决结果。',
      confirmText: '仍要生成',
      cancelText: '先去表决'
    })
    if (r.confirm) continueGenerateMinutes(true)
    return
  }

  // 4) 有表决议题且都已有表决结果 → 直接生成
  continueGenerateMinutes(true)
}

// 「继续生成会议纪要」：确认票数落库 + 大模型生成（遮罩 gen）。skipGuard=true 表示表决核对刚做过，不再重复提醒
async function continueGenerateMinutes(skipGuard) {
  if (!isChair.value) { toast({ title: '仅主任/副主任可生成纪要', icon: 'none' }); return }
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  if (!generated.value) { toast({ title: '请先上传录音完成识别', icon: 'none' }); return }
  if (!skipGuard) {
    const missing = unvotedRealTopics()
    if (missing.length) {
      const r = await showModal({
        title: '还有议题没有表决',
        content: '「' + missing[0].title + '」' + (missing.length > 1 ? '等 ' + missing.length + ' 个表决议题' : '') + '还没有表决结果。建议先完成表决再生成纪要。',
        confirmText: '仍要生成',
        cancelText: '先去表决'
      })
      if (!r.confirm) return
    }
  }
  overlayPhase.value = 'gen'
  generatingMinutes.value = true
  const mid = meetingId.value
  minutesGenAt.value = Date.now() // durable「生成中」时刻：整页刷新/硬跳丢了内存 aiTask 后，切回本页据此从服务端恢复入口
  // 全局后台任务：切到别的页面时顶部悬浮「会议纪要生成中…」，完成后可点直达纪要页
  const minutesPath = '/pages/minutes-view/minutes-view?meetingId=' + mid + '&from=meeting-live-quick'
  startAiTask({ label: '会议纪要生成中…', originPath: window.location.pathname, targetPath: minutesPath })
  persistQuickState() // 立刻落盘生成标记（原先要等生成成功才 persist，中途刷新就丢了 → 重进无入口）
  try {
    await api.committeeQuickConfirm(mid, buildConfirmPayload())
    await api.committeeQuickPolish(mid) // 大模型生成纪要并落库（长请求，切到别的页面也不中断，后端继续跑）
    minutesGenerated.value = true
    persistQuickState()
    // 已切走/关掉遮罩（后台完成）→ 全局完成条（可点直达纪要页）；仍在前台看遮罩 → 遮罩完成态接管
    if (backgroundDone()) finishAiTask({ doneLabel: '会议纪要已生成' })
    else clearAiTask()
  } catch (e) {
    if (backgroundDone()) failAiTask({ failLabel: '会议纪要生成失败' })
    else { clearAiTask(); toast({ title: (e && e.message) || '生成纪要失败，请重试', icon: 'none' }) }
  } finally {
    generatingMinutes.value = false // 仍在前台看 → 遮罩转完成态「已生成会议纪要」
    minutesGenAt.value = 0           // 生成已结束(成功/失败)→ 清 durable 标记，避免重进误显示"生成中"
    persistQuickState()
  }
}

// 生成完成时用户是否已不在等这块遮罩：生成中切走过本页（_leftWhileGenerating）或点×关了遮罩（generatingMinutes=false）
// → 该用全局悬浮提示而非遮罩完成态。
// ⚠ 不能用 document.querySelector('.live-page') 判断：本页无 keep-alive，切走再回来是全新实例，
//   旧实例的 promise 完成时会误命中新实例的 .live-page，把「已生成」悬浮条错误清掉（=看着像中止）。
function backgroundDone() {
  return _leftWhileGenerating || !generatingMinutes.value
}

// 遮罩完成按钮：recognize 阶段 → 关遮罩，露出「继续上传录音 / 生成会议纪要」两键（不再自动生成）；
// gen 阶段（「查看会议纪要」）→ 进纪要页。软路由偶发不切换——加硬导航兜底。
function onAiWorkDone() {
  if (overlayPhase.value === 'recognize') return // 识别完成：遮罩自行收起，页面回到两键状态
  const q = 'meetingId=' + meetingId.value + '&from=meeting-live-quick'
  navigateTo('/pages/minutes-view/minutes-view?' + q)
  setTimeout(() => {
    if (document.querySelector('.live-page')) {
      window.location.href = '/minutes-view?' + q
    }
  }, 500)
}

// 关闭「AI 工作中」遮罩：停止前端等待态，回到当前页（后台若已转完，下次进入会自动恢复）。
// 必须把 generatingMinutes 也置回 false——否则「生成会议纪要/继续上传/查看纪要」三组按钮
// 都带 !generatingMinutes 条件会被一直隐藏，录音页只剩暂停态的「重新录音」。中止生成后应
// 回到「生成会议纪要 + 继续上传录音」两键，方便重新生成。
function onAiWorkClose() {
  clearPoll()
  polling.value = false
  extracting.value = false
  generatingMinutes.value = false
  _transcribingRecordingId.value = ''
}

// 录音时长（秒）→ "MM:SS"；无时长显示占位
// 录音起止时间：createdAt=后端上传时刻(≈录音结束)，start=end−时长。显示 "MM-DD HH:MM:SS – HH:MM:SS"（近似，±上传耗时几秒）。
function fmtTimeRange(item) {
  const raw = item && item.createdAt
  const end = raw ? new Date(String(raw).replace(' ', 'T')) : null
  if (!end || isNaN(end.getTime())) return fmtTime(raw)
  const dur = Number(item.durationSec) || 0
  const start = new Date(end.getTime() - dur * 1000)
  const p = (n) => String(n).padStart(2, '0')
  const hms = (d) => p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
  return p(end.getMonth() + 1) + '-' + p(end.getDate()) + ' ' + hms(start) + ' – ' + hms(end)
}

function fmtDur(sec) {
  const s = Number(sec)
  if (!s || s <= 0) return '未知'
  const m = Math.floor(s / 60)
  return m + ':' + String(s % 60).padStart(2, '0')
}
// 上传时间：后端 LocalDateTime（如 2026-06-29T10:01:23）→ "MM-DD HH:mm"
function fmtTime(iso) {
  if (!iso) return ''
  const m = String(iso).match(/(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  return m ? (m[2] + '-' + m[3] + ' ' + m[4] + ':' + m[5]) : String(iso)
}

// 转写页录音回放：点播放试听这段录音，再点暂停；切到另一段会停掉上一段
function togglePlay(item) {
  const url = item && item.recordingUrl
  if (!url) { toast({ title: '该录音暂无音频', icon: 'none' }); return }
  // 再点同一段 = 暂停
  if (playingId.value === item.id && _playAudio) { try { _playAudio.pause() } catch (e) {} return }
  // 切到另一段：先静默停掉旧的（摘掉旧 handler 避免误触发）
  if (_playAudio) { _playAudio.onplay = _playAudio.onpause = _playAudio.onended = _playAudio.onerror = null; try { _playAudio.pause() } catch (e) {} }
  _playAudio = new Audio(url)
  _playAudio.onplay = () => { playingId.value = item.id }
  _playAudio.onpause = () => { if (playingId.value === item.id) playingId.value = null }
  _playAudio.onended = () => { if (playingId.value === item.id) playingId.value = null }
  _playAudio.onerror = () => { playingId.value = null; toast({ title: '播放失败', icon: 'none' }) }
  const p = _playAudio.play()
  if (p && p.catch) p.catch(() => { playingId.value = null; toast({ title: '播放失败', icon: 'none' }) })
}

// 删除一条录音（转写页删废录/多余段）：仅主任/副主任
async function deleteRecording(item, idx) {
  if (!isChair.value) { toast({ title: '仅主任/副主任可删除', icon: 'none' }); return }
  if (polling.value || extracting.value) return
  const res = await showModal({
    title: '删除这段录音',
    content: '确定删除「第 ' + (idx + 1) + ' 段 · 时长 ' + fmtDur(item.durationSec) + '」？删除后不可恢复，转写合并结果也会去掉这段。',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!res.confirm) return
  // 删的是正在播放的那段，先停掉回放
  if (playingId.value === item.id && _playAudio) { try { _playAudio.pause() } catch (e) {} }
  try {
    await api.committeeDeleteRecording(meetingId.value, item.id)
    toast({ title: '已删除', icon: 'success' })
    pickedIds.value = (pickedIds.value || []).filter(x => x !== item.id)
    _knownRecordingIds.delete(item.id)
    loadDetail() // 刷新录音列表
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  }
}

// 转写所选录音：逐条顺序转写，全部完成后统一抽取；已转写的由后端自动并入合并
async function transcribeSelected() {
  // 0722 用户定：委员上传的段也自动识别 → 转写对全部会内角色开放
  if (polling.value || extracting.value) return
  // 勾选的都转写（含被重新勾选的“已转写”项，按 recordingId 覆盖其缓存，不会重复）
  const ids = (recordings.value || [])
    .filter(r => isPicked(r.id))
    .map(r => r.id)
  // 没有勾选：若已有转写过的录音(缓存中)，直接重新拉取合并结果即可
  if (!ids.length) {
    if (!hasDoneRecordings.value) { toast({ title: '请先勾选要转写的录音', icon: 'none' }); return }
    _asrDoneHandled = false
    polling.value = false
    extracting.value = true
    await finalizeTranscription()
    return
  }
  clearPoll()
  _asrDoneHandled = false
  polling.value = true
  extracting.value = false
  generated.value = false
  transcript.value = []
  transcriptPreview.value = ''
  transcriptFullText.value = ''
  transcriptCharCount.value = 0
  transcriptVisible.value = false
  asrStatus.value = ''
  processText.value = '正在提交转写...'
  persistQuickState()
  try {
    for (let i = 0; i < ids.length; i++) {
      _transcribingRecordingId.value = ids[i]
      processText.value = ids.length > 1
        ? ('正在转写第 ' + (i + 1) + '/' + ids.length + ' 条录音…')
        : '正在转写录音…'
      await transcribeOneAwait(ids[i])
    }
    _transcribingRecordingId.value = ''
    await finalizeTranscription()
  } catch (e) {
    _transcribingRecordingId.value = ''
    polling.value = false
    extracting.value = false
    handleAsrFailed((e && e.message) || '转写失败')
  }
}

// 提交单条录音转写并等待其完成（done 时 resolve；失败/超时 reject）
function transcribeOneAwait(recordingId) {
  return new Promise((resolve, reject) => {
    api.committeeTranscribeRecording(meetingId.value, recordingId)
      .then((task) => {
        taskId.value = task.taskId
        asrStatus.value = task.status || 'pending'
        persistQuickState()
        if (task.status === 'done') return resolve(task)
        if (task.status === 'failed') return reject(new Error(asrFailMessage(task.message)))
        pollUntilDone(task.taskId, resolve, reject)
      })
      .catch(reject)
  })
}

// 轮询单条录音的转写状态直至 done（Promise 版，供顺序转写串联）
function pollUntilDone(tid, resolve, reject) {
  clearPoll()
  _pollCount = 0
  let errStreak = 0 // 手机网络抖一下很常见：单次查询失败不放弃，连错 3 次才判失败
  _pollTimer = setInterval(async () => {
    _pollCount += 1
    if (_pollCount > maxPollCount()) {
      clearPoll()
      reject(new Error('转写超时，请稍后重试或换一条录音'))
      return
    }
    try {
      const task = await api.committeeQuickRecordingStatus(meetingId.value, tid)
      errStreak = 0
      asrStatus.value = task.status || ''
      persistQuickState()
      if (task.status === 'done') { clearPoll(); resolve(task) }
      else if (task.status === 'failed') { clearPoll(); reject(new Error(asrFailMessage(task.message))) }
    } catch (e) {
      errStreak += 1
      if (errStreak >= 3) { clearPoll(); reject(e) }
    }
  }, POLL_INTERVAL)
}

function startPoll(tid) {
  clearPoll()
  _pollCount = 0
  let errStreak = 0 // 与 pollUntilDone 同策略：网络抖动不放弃，连错 3 次才判失败
  _pollTimer = setInterval(async () => {
    _pollCount += 1
    if (_pollCount > maxPollCount()) {
      clearPoll()
      polling.value = false
      asrStatus.value = 'failed'
      processText.value = '转写超时'
      showModal({
        title: '转写超时',
        content: '这条录音长时间没有完成转写，可能是录音里没有有效语音或网络较慢。请换一条录音，或返回上一步重新录音。',
        confirmText: '知道了',
        showCancel: false
      })
      return
    }
    try {
      const task = await api.committeeQuickRecordingStatus(meetingId.value, tid)
      asrStatus.value = task.status || ''
      processText.value = statusText(task.status, task.message)
      persistQuickState()
      errStreak = 0
      if (task.status === 'done') {
        handleAsrDone(task)
      }
      if (task.status === 'failed') handleAsrFailed(task.message)
    } catch (e) {
      errStreak += 1
      if (errStreak < 3) return
      clearPoll()
      polling.value = false
      processText.value = '查询转写状态失败'
      toast({ title: e.message || '查询失败', icon: 'none' })
    }
  }, POLL_INTERVAL)
}

function clearPoll() {
  if (_pollTimer) {
    clearInterval(_pollTimer)
    _pollTimer = null
  }
}

function statusText(status, message) {
  if (status === 'done') return '转写完成，正在抽取议题与表决提示...'
  if (status === 'failed') return asrFailMessage(message)
  if (status === 'processing') return '录音转写中...'
  if (status === 'pending') return '转写任务已提交，等待处理...'
  if (status === 'uploading') return '正在上传录音...'
  return '正在处理录音...'
}

function asrFailMessage(message) {
  const m = String(message || '')
  if (m.indexOf('45000006') >= 0) return '音频文件读取失败，请返回上一步重新上传录音'
  if (/timeout|timed out|超时/i.test(m)) return '录音较大，转写提交超时了。建议分段录制、缩短单段时长后重试'
  if (/network|连接|refused|unreachable/i.test(m)) return '网络不稳定，转写没提交成功，请稍后重试'
  return m || '录音转写失败，请重试'
}

async function handleAsrDone(task) {
  if (_asrDoneHandled) return
  _asrDoneHandled = true
  asrStatus.value = (task && task.status) || 'done'
  await finalizeTranscription()
}

// 所选录音全部转写完成后：拉取(已按多条录音合并的)转写原文 → 判空 → 抽取议题 → 渲染。
// 单条转写(恢复态 handleAsrDone)与多条顺序转写(transcribeSelected)共用此收尾逻辑。
async function finalizeTranscription() {
  clearPoll()
  uploading.value = false
  polling.value = false
  extracting.value = true
  processText.value = '转写完成，正在抽取议题与表决提示...'
  persistQuickState()

  // 刷新录音列表：转写状态(asrStatus)已在服务端落库，但列表里还是上传时的旧快照——
  // 不刷新的话行上会一直显示「待识别」（临时内存态一清就露馅，真机实测踩过）
  try {
    const d = await api.committeeDetail(meetingId.value)
    const recs = (d.record && d.record.recordings) || []
    if (recs.length) { recordings.value = recs; reconcilePickedIds(recs) }
  } catch (e) { /* 刷新失败不影响主流程，下次 loadDetail 会补上 */ }

  // 先取转写原文(已含多条录音合并)，判断是否「空转写」（录音里没有可识别的说话声）。
  // 豆包对静音/无效音频也会返回 done，但识别结果为空——此时必须提示用户，而不是继续抽取出空议题。
  let tr = null        // null = 取原文失败(网络等)，不据此判空，避免误报
  try {
    if (typeof api.committeeQuickTranscript === 'function') {
      const raw = await api.committeeQuickTranscript(meetingId.value)
      tr = mapTranscript(raw)
    }
  } catch (e) { tr = null }

  if (tr) {
    const hasText = tr.some(function (seg) { return seg.text && seg.text.trim() })
    if (!hasText) {
      handleAsrEmpty()
      return
    }
  }

  // 有文字（或取原文失败的兜底）→ 抽取议题与表决提示
  try {
    const ext = await api.committeeQuickExtract(meetingId.value)
    const mapped = mapExtraction(ext)
    extraction.value = ext
    presetTopics.value = mapped.presetTopics
    aiTopics.value = mapped.aiTopics
    extracting.value = false
    generated.value = true
    finishText.value = '转写完成，已合并为会议记录并生成待确认的议题'
    persistQuickState()
  } catch (e) {
    extracting.value = false
    generated.value = false
    asrStatus.value = 'failed'
    processText.value = '录音识别成功，但整理议题失败'
    toast({ title: e.message || '整理议题失败，请重试', icon: 'none' })
    return
  }

  // 渲染转写原文（前面已取到则直接用）
  if (tr) {
    transcript.value = tr
    const tState = buildTranscriptState(tr)
    transcriptFullText.value = tState.transcriptFullText
    transcriptPreview.value = tState.transcriptPreview
    transcriptCharCount.value = tState.transcriptCharCount
    persistQuickState()
  }

  // 两人同时录音的兜底：各段转写两两比对，内容高度相似 → 提醒删除其一（异步、不阻塞主流程）
  checkDuplicateTranscripts().catch(() => {})
}

// ── 重复录音检测：两段转写内容高度相似（两人同时录了同一段会议）时提醒 ──
const _dupWarnedPairs = new Set() // 已提醒过的段对，别反复弹
function _trNormalize(t) { return String(t || '').replace(/[\s\p{P}\p{S}]/gu, '') }
function _trBigrams(s) { const g = new Set(); for (let i = 0; i < s.length - 1; i++) g.add(s.slice(i, i + 2)); return g }
// 重叠系数（交集/较小集）：短段完整包含于长段（一人录得短）也能命中
function _trSimilarity(a, b) {
  const A = _trBigrams(a); const B = _trBigrams(b)
  if (!A.size || !B.size) return 0
  let inter = 0
  for (const x of A) if (B.has(x)) inter++
  return inter / Math.min(A.size, B.size)
}
async function checkDuplicateTranscripts() {
  if (typeof api.committeeQuickRecordingTranscript !== 'function') return
  const done = (recordingsChrono.value || []).filter(r => r.asrStatus === 'done')
  if (done.length < 2) return
  // 取各段单独转写的纯文本（段数少，逐个取；失败的跳过）
  const texts = []
  for (let i = 0; i < done.length; i++) {
    try {
      const raw = await api.committeeQuickRecordingTranscript(meetingId.value, done[i].id)
      const body = ((raw && raw.segments) || []).map(s => s.text || '').join('')
      texts.push({ idx: i + 1, id: done[i].id, norm: _trNormalize(body) })
    } catch (e) { /* 单段取不到就不参与比对 */ }
  }
  for (let a = 0; a < texts.length; a++) {
    for (let b = a + 1; b < texts.length; b++) {
      const ta = texts[a]; const tb = texts[b]
      if (ta.norm.length < 40 || tb.norm.length < 40) continue // 太短没有比对价值
      const key = ta.id + '-' + tb.id
      if (_dupWarnedPairs.has(key)) continue
      if (_trSimilarity(ta.norm, tb.norm) >= 0.65) {
        _dupWarnedPairs.add(key)
        await showModal({
          title: '两段录音内容高度相似',
          content: '第 ' + ta.idx + ' 段与第 ' + tb.idx + ' 段的转写内容高度相似，可能是两人同时录了同一段会议。建议在录音列表删除其中一段，避免会议记录和纪要出现重复内容。',
          confirmText: '知道了',
          showCancel: false
        })
      }
    }
  }
}

// 空转写：录音里没有识别到可转写的语音——停止轮询、明确弹窗、允许换录音重试
function handleAsrEmpty() {
  clearPoll()
  _asrDoneHandled = false   // 允许换一条录音/重新录音后再次转写
  uploading.value = false
  polling.value = false
  extracting.value = false
  generated.value = false
  asrStatus.value = 'empty'
  taskId.value = ''
  transcript.value = []
  transcriptPreview.value = ''
  transcriptFullText.value = ''
  transcriptCharCount.value = 0
  processText.value = '未检测到有效语音'
  persistQuickState()
  showModal({
    title: '没有识别到语音',
    content: '这条录音里没有听到可转写的说话声，可能是录到了静音、杂音，或录音太短。请换一条录音，或返回上一步重新录音。',
    confirmText: '知道了',
    showCancel: false
  })
}

// 转写结果（说话人+时间+文本）转成可渲染列表
function mapTranscript(asr) {
  const segs = (asr && asr.segments) || []
  return segs.map((s, i) => ({
    id: i,
    speaker: s.speaker || '',
    time: fmt(Math.floor((s.startMs || 0) / 1000)),
    text: s.text || ''
  }))
}

function buildTranscriptState(tr) {
  const lines = (tr || []).map(function (seg) {
    var prefix = [seg.time, seg.speaker].filter(Boolean).join(' ')
    return (prefix ? prefix + '：' : '') + (seg.text || '')
  }).filter(Boolean)
  const fullText = lines.join('\n')
  // 摘要/字数只统计转写正文，不带时间戳与说话人前缀
  const body = (tr || []).map(function (seg) {
    return (seg.text || '').trim()
  }).filter(Boolean).join(' ').replace(/\s+/g, ' ').trim()
  return {
    transcriptFullText: fullText,
    transcriptPreview: body.length > 92 ? body.slice(0, 92) + '...' : body,
    transcriptCharCount: body.length
  }
}

function openTranscript(mode) {
  transcriptView.value = null   // 整会合并稿
  transcriptViewLoading.value = false
  transcriptVisible.value = true
  transcriptMode.value = mode || 'short'
}

function closeTranscript() {
  transcriptVisible.value = false
  transcriptView.value = null
  transcriptViewLoading.value = false
}

function switchTranscriptMode(mode) {
  transcriptMode.value = mode || 'short'
}

function handleAsrFailed(message) {
  clearPoll()
  const text = asrFailMessage(message)
  uploading.value = false
  polling.value = false
  extracting.value = false
  generated.value = false
  asrStatus.value = 'failed'
  processText.value = text
  persistQuickState()
  toast({ title: text, icon: 'none' })
}

function retryToStep2() {
  clearPoll()
  currentStep.value = 2
  uploading.value = false
  polling.value = false
  extracting.value = false
  generated.value = false
  taskId.value = ''
  asrStatus.value = ''
  processText.value = '请重新上传录音'
  persistQuickState()
}

function mapExtraction(ext) {
  const hitList = (ext && ext.presetTopicHits) || []
  const candidateList = (ext && ext.candidateTopics) || []
  const fallbackPreset = presetTopics.value || []
  const total = voteTotal.value || 0
  const presets = hitList.length
    ? hitList.map(hit => mapTopic(hit, null, total))
    : fallbackPreset.map(t => Object.assign({}, t, { confirmed: false }))
  const ai = candidateList.map(hit => mapTopic(hit, null, total))
  return { presetTopics: presets, aiTopics: ai }
}

// 采纳疑似新议题：主任选类型 → 调用现场添加议题接口建真实议题 → 并入正式议题列表
async function adoptCandidate(idx) {
  if (!isChair.value) { toast({ title: '仅主任/副主任可立项', icon: 'none' }); return }
  const cand = (aiTopics.value || [])[idx]
  if (!cand) return
  const choices = [
    // 0717 用户定：通知并入讨论。采纳候选没有正文输入，统一落 discussion；要挂正文可事后编辑
    { label: '通知和讨论', type: 'discussion', decisionType: 'none', voteRequired: false },
    { label: '表决事项', type: 'decision', decisionType: 'simple', voteRequired: true }
  ]
  const sheet = await showActionSheet({ itemList: choices.map(function (c) { return c.label }) })
  const c = choices[sheet.tapIndex]
  if (!c) return
  try {
    const created = await api.committeeAddTopic(meetingId.value, cand.title, c.type, c.decisionType, null, false)
    const realId = created && created.id ? created.id : cand.id
    const adopted = decorateEvidence(Object.assign({}, cand, {
      id: realId,
      type: c.type,
      typeLabel: topicTypeLabel(c.type),
      voteRequired: c.voteRequired,
      suggest: c.voteRequired ? voteHintText(null) : '记录要点',
      result: 'unclear',
      resultLabel: resultLabel('unclear'),
      voteFor: 0, voteAgainst: 0, voteAbstain: 0, voteCounted: 0,
      confirmed: false
    }))
    const presets = (presetTopics.value || []).concat([adopted])
    const ai = (aiTopics.value || []).slice()
    ai.splice(idx, 1)
    presetTopics.value = presets
    aiTopics.value = ai
    persistQuickState()
    toast({ title: '已立项为' + c.label, icon: 'success' })
  } catch (err) {
    toast({ title: err.message || '立项失败', icon: 'none' })
  }
}

function ignoreCandidate(idx) {
  const ai = (aiTopics.value || []).slice()
  if (idx < 0 || idx >= ai.length) return
  ai.splice(idx, 1)
  aiTopics.value = ai
  persistQuickState()
}

function pickTopicResult(group, idx, result) {
  const key = group === 'ai' ? 'aiTopics' : 'presetTopics'
  const target = group === 'ai' ? aiTopics : presetTopics
  const list = target.value.slice()
  const res = result || 'unclear'
  list[idx] = Object.assign({}, list[idx], {
    result: res,
    resultLabel: resultLabel(res),
    needsReview: res === 'unclear',
    confirmed: false
  })
  target.value = list
  if (minutesGenerated.value) minutesGenerated.value = false // 结果已改，草稿作废
  persistQuickState()
}

function recalcVoteTopic(topic) {
  const voteFor = Number(topic.voteFor) || 0
  const voteAgainst = Number(topic.voteAgainst) || 0
  const voteAbstain = Number(topic.voteAbstain) || 0
  const counted = voteFor + voteAgainst + voteAbstain
  const need = voteNeed.value || 1
  const total = voteTotal.value || 0
  let result = 'unclear'
  if (voteFor >= need) result = 'passed'
  else if (total > 0 && counted >= total) result = 'rejected'
  return Object.assign({}, topic, {
    voteFor,
    voteAgainst,
    voteAbstain,
    voteCounted: counted,
    result,
    resultLabel: resultLabel(result),
    needsReview: result === 'unclear',
    confirmed: false
  })
}

function changeVoteCount(group, idx, field, delta) {
  const target = group === 'ai' ? aiTopics : presetTopics
  const d = Number(delta) || 0
  const list = target.value.slice()
  const topic = Object.assign({}, list[idx])
  const current = Number(topic[field]) || 0
  const next = Math.max(0, current + d)
  const total = voteTotal.value || 0
  const otherTotal = (Number(topic.voteFor) || 0) + (Number(topic.voteAgainst) || 0) + (Number(topic.voteAbstain) || 0) - current
  if (total > 0 && otherTotal + next > total) {
    toast({ title: '票数不能超过实到人数', icon: 'none' })
    return
  }
  topic[field] = next
  list[idx] = recalcVoteTopic(topic)
  target.value = list
  if (minutesGenerated.value) minutesGenerated.value = false // 票数已改，草稿作废
  persistQuickState()
}

function buildConfirmPayload() {
  return {
    topics: (presetTopics.value || []).map(function (t) {
      return {
        topicId: t.id,
        result: t.voteRequired ? t.result : 'recorded',
        confirmed: t.confirmed,
        summaryDraft: t.summaryDraft || '',
        segmentIndexes: (t.segmentMatches || []).map(function (seg) { return seg.segmentIndex }),
        forVotes: t.voteRequired ? (Number(t.voteFor) || 0) : null,
        agVotes: t.voteRequired ? (Number(t.voteAgainst) || 0) : null,
        abVotes: t.voteRequired ? (Number(t.voteAbstain) || 0) : null,
        totalVotes: t.voteRequired ? (Number(t.voteCounted) || 0) : null
      }
    }),
    ignoredSegmentIndexes: []
  }
}

// 仅查看已保存的纪要草稿，不触发重新生成
function viewMinutes() {
  const q = 'meetingId=' + meetingId.value + '&from=meeting-live-quick'
  navigateTo('/pages/minutes-view/minutes-view?' + q)
  // 软路由偶发不切换（URL 变了却停在录音页）→ 500ms 后仍在本页则硬导航兜底
  setTimeout(() => {
    if (document.querySelector('.live-page')) window.location.href = '/minutes-view?' + q
  }, 500)
}

// 重新生成纪要：会覆盖当前草稿，先确认再走生成流程（表决核对 → 生成）
async function regenerateMinutes() {
  const res = await showModal({
    title: '',
    content: '将重新生成会议纪要，覆盖当前草稿。确认重新生成？',
    confirmText: '重新生成',
    cancelText: '取消',
    contentBold: true,
    emphasizeConfirm: true
  })
  if (!res.confirm) return
  generateNow()
}

// ── 重做后的「最后一步」：表决核对 / 生成纪要 / 结束会议 ──

// 展开/收起某个议题的票数核对
function toggleReview(idx) {
  const list = (presetTopics.value || []).slice()
  if (!list[idx]) return
  list[idx] = Object.assign({}, list[idx], { _reviewOpen: !list[idx]._reviewOpen })
  presetTopics.value = list
}

// 展开/收起「AI 额外发现」
function toggleExtra() {
  extraOpen.value = !extraOpen.value
}

// 用 AI 生成会议纪要草稿（主任主动点击；等待大模型属预期内，按钮显示「生成中」）
async function generateMinutes() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可生成纪要', icon: 'none' }); return }
  if (!generated.value) { toast({ title: '请先完成录音转写', icon: 'none' }); return }
  generatingMinutes.value = true
  const payload = buildConfirmPayload()
  // 1) 先把(含已核对的)票数/结果快速落库（不依赖大模型）；失败则停下
  try {
    await api.committeeQuickConfirm(meetingId.value, payload)
  } catch (e) {
    generatingMinutes.value = false
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
    return
  }
  // 2) 跳转纪要页：由纪要页（gen=1）统一调用大模型生成并显示"生成中"，
  //    这里不再 fire 一次，避免重复生成 / 两份结果竞争写库。
  generatingMinutes.value = false
  minutesGenerated.value = true
  persistQuickState()
  navigateTo('/pages/minutes/minutes?meetingId=' + meetingId.value + '&from=meeting-live-quick&gen=1')
}

// 共用：落库当前结果 + 结束会议，然后跳转到指定页面（结束本身不再等大模型）
// ⚠ 测试期开关（0722 用户定）：生成纪要/完成会后整理不真正结束会议——不调 advance('end')，
// 会议保持「进行中」留在首页，随时可再进各页面查看投票/记录，测试数据由用户手动删除清理。
// 上线前置回 false 恢复「结束并归档」（公示流程需要已结束状态）。已记入 docs/上线前TODO.md
const TEST_KEEP_MEETING_OPEN = true

// 返回是否成功走到跳转（false=失败留在本页，调用方需要回滚自己的过渡态）
async function _doEndAndGo(navUrl) {
  ending.value = true
  try {
    // 把最新(可能刚核对过的)结果再存一次，确保归档与展示一致
    try { await api.committeeQuickConfirm(meetingId.value, buildConfirmPayload()) } catch (ce) { /* ignore */ }
    if (TEST_KEEP_MEETING_OPEN) {
      reviewCompleted.value = true // 整理已完成：首页卡片入口切「会议详情」
      persistQuickState() // 保留本地状态：回到本页仍停在会后整理，可继续查看
    } else {
      try {
        await api.committeeAdvance(meetingId.value, 'end')
      } catch (ae) {
        const msg = (ae && ae.message) || ''
        const alreadyEnded = msg.indexOf('仅进行中') >= 0 || msg.indexOf('已结束') >= 0 || msg.indexOf('ended') >= 0
        if (!alreadyEnded) {
          ending.value = false
          showModal({ title: '结束会议失败', content: msg || '请稍后重试', showCancel: false })
          return false
        }
      }
      clearQuickState()
    }
    goAfterEnd(navUrl)
    return true
  } catch (err) {
    ending.value = false
    showModal({ title: '结束会议失败', content: (err && err.message) || '请稍后重试', showCancel: false })
    return false
  }
}

function goAfterEnd(navUrl) {
  redirectTo(navUrl)
  setTimeout(() => {
    if (!document.querySelector('.live-page')) return
    if (String(navUrl).indexOf('/pages/committee-detail/committee-detail') === 0) {
      window.location.href = '/committee-detail?id=' + meetingId.value + '&from=meeting-live-quick'
      return
    }
    window.location.href = navUrl.replace(/^\/pages\/([^/]+)\/[^?]+/, '/$1')
  }, 500)
}

function viewMeetingDetail() {
  goAfterEnd('/pages/committee-detail/committee-detail?id=' + meetingId.value + '&from=meeting-live-history')
}

function handleMeetingBottomAction() {
  if (meetingEnded.value) {
    viewMeetingDetail()
    return
  }
  // 现场会议已结束（进入过会后整理）→ 右键直接回会后整理，不再二次确认结束
  if (fieldMeetingEnded.value) {
    openEndReview()
    return
  }
  confirmEndMeeting()
}

async function confirmRemoteAttend() {
  const res = await showModal({
    title: '',
    content: '确认线上参加此次会议吗？',
    confirmText: '确认',
    cancelText: '取消',
    size: 'attendance'
  })
  if (!res.confirm) return
  try {
    // 线上参会确认只登记参会方式；代签授权在后续实际签字/表决环节另行确认。
    await api.committeeSelfAttend(meetingId.value, 'remote', false)
    signedIn.value = true
    await loadDetail()
    playSigninFx()
  } catch (e) {
    toast({ title: e.message || '确认失败', icon: 'none' })
  }
}

function openEndReview() {
  fieldMeetingEnded.value = true
  endReviewVisible.value = true
  persistQuickState()
}

// 关闭会后整理页也要持久化：否则刷新后 restoreQuickState 会按旧存档把整理页又翻回来
function closeEndReview() {
  endReviewVisible.value = false
  persistQuickState()
}

// 结束现场会议：只停止/保存现场录音并进入会后整理，不在这里把整场会议改为已结束。
async function confirmEndMeeting() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可结束现场会议', icon: 'none' }); return }
  // 两维状态：有无「进行中/未上传」的录音 × 有无「已上传」的录音，组合出四档确认文案。
  // 正在上传(uploading)单列一档（0723 用户定）：录音已在后台传输，结束不放弃、让它传完保存，
  // 避免误报"还没上传会放弃"——那段其实正在传，reset 也拦不住已发出的请求，反而认知不一致。
  const isUploading = uploading.value
  const hasOngoing = canUpload.value && !isUploading  // 排除"正在上传"那段，只算真正在手里、没传的
  const hasUploaded = hasSavedRecordings.value  // 服务器上已有录音段
  const pendingTail = pendingTopicCount.value ? '还有 ' + pendingTopicCount.value + ' 项议题未处理。' : ''

  let content, confirmText, cancelText
  if (isUploading) {
    // 正在上传：不放弃，系统后台继续传完并保存
    content = '录音正在后台上传，不会丢失，确认结束会议吗？' + pendingTail
    confirmText = '结束会议'; cancelText = '继续开会'
  } else if (hasOngoing && !hasUploaded) {
    // 状态1：有进行中、无已上传。确认=放弃这段并结束。
    content = '这段录音还没上传，结束会放弃它。确认结束？'
    confirmText = '确认结束'; cancelText = '继续录音'
  } else if (hasOngoing && hasUploaded) {
    // 状态2：有进行中、有已上传。前面已保存，只放弃当前这段。
    content = '当前这段录音没上传，结束会放弃它（前面的已保存）。确认结束？'
    confirmText = '确认结束'; cancelText = '继续录音'
  } else if (!hasOngoing && !hasUploaded) {
    // 状态3：无进行中、无已上传。基本是没录音就想结束。
    content = '本次还没有任何录音，确认结束？'
    confirmText = '确认结束'; cancelText = '返回'
  } else {
    // 状态4：无进行中、有已上传（最常见）。普通确认即可。
    content = '结束后进入会后整理。' + pendingTail
    confirmText = '结束会议'; cancelText = '继续开会'
  }

  // 四状态只看得到本机：别的设备（副主任/委员）还在录音时补一句提醒——
  // 结束现场会议不影响他们继续录和上传，只是让主任心里有数（老后端无接口时跳过）
  try {
    const live = await api.committeeRecordingLive(meetingId.value)
    const others = (live || []).filter(x => String(x.roleId) !== String(myRoleId.value || ''))
    if (others.length) {
      const names = others.map(o => o.name).filter(Boolean).join('、') || '有人'
      content = names + ' 还在录音（不影响，仍可上传）。\n' + content
    }
  } catch (e) { /* 查不到不拦 */ }

  const confirmed = await showModal({ title: '', content, confirmText, cancelText })
  if (!confirmed.confirm) return

  // 决策1：确认即放弃当前「进行中/未上传」的录音——结束后不再提供上传/返回入口。
  // reset 会停录并清掉落盘切片，watch 把 recording=false 同步给全局悬浮条并隐藏。
  if (hasOngoing) {
    rec.reset()
    toast({ title: '已停止并放弃当前录音', icon: 'none' })
  }

  // 决策2（安全网）：已上传但还没识别完成（识别失败/被中断/从未识别）→ 直接结束会导致会后生成纪要必失败。
  // 保留二次提示（软拦截），把「先识别录音」作为推荐项；识别在途(uploading/polling/extracting)不拦：
  // 会后整理页会显示「上传中/识别中」并在完成后放行生成。
  if (hasSavedRecordings.value && !generated.value
      && !uploading.value && !polling.value && !extracting.value) {
    const choice = await showActionSheet({
      title: '录音还没识别完，直接结束无法自动生成纪要',
      variant: 'opinion-change',
      itemList: [
        { icon: 'AI', label: '先识别录音', tone: 'ai' },
        { icon: '略', label: '不识别，继续结束', tone: 'danger' }
      ]
    })
    if (choice.tapIndex === 0) {
      await uploadAndRecognize()
      // 识别失败/没识别出内容：留在本页，由录音卡下方的行内提示引导重试
      if (!generated.value) return
      openEndReview()
      return
    }
    if (choice.tapIndex !== 1) return
  }
  openEndReview()
}

function continuePendingTopics() {
  // 与点击清单第一条议题同路径（0722 用户定）：留在会后整理页，直接弹出第一个议题，
  // 用弹层的「下一个议题」逐条走完，不再跳回议题处理页
  const first = meetingTopics.value[0]
  if (first) openTopicSheet(first)
}

async function handleEndReviewPrimary() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可操作', icon: 'none' }); return }
  if (minutesGenerated.value) { viewMinutes(); return }
  if (uploading.value || polling.value || extracting.value) return
  if (!generated.value) { toast({ title: '录音识别完成后可生成纪要', icon: 'none' }); return }
  await endAndGenerateMinutes()
}

// 底层逻辑（0722 用户定）：已签到参会者都必须有投票结果——没投的由主持人代录（无意见=弃权），
// 请假/未参会不计入（表决分母=已签到人数，后端已同步）。完成会议前硬拦截还有人未投的表决议题。
function findUnvotedVoteTopic() {
  return meetingTopics.value.find(t => t.voteRequired && (t.voted || 0) < (t.total || 0))
}
// 状态纠错（0722 用户定）：主持人在名单弹窗用状态右侧小下拉改参会状态（忘了签/手机没电等）。
// 四种：已签到/线上参会/请假缺席/未参会；仅会议进行中（含会后整理）可改，后端记操作人留痕。
// 签到状态纠错仅在现场会议结束后的会后整理阶段开放（与代投同理）：现场进行中主持人不改他人签到，
// 委员本人签到；现场结束后整理时才由主持人补正忘签/手机没电等（0723 用户定）
const canEditAttendance = computed(() => isHost.value && !!detail.value && detail.value.stage === 'ongoing' && fieldMeetingEnded.value)
const ATTENDANCE_STATUS_OPTIONS = [
  { label: '已签到', value: 'onsite' },
  { label: '线上参会', value: 'remote' },
  { label: '请假缺席', value: 'declined' },
  { label: '未参会', value: 'none' },
]
const attMenuFor = ref(null) // 当前弹出状态下拉的 userRoleId（null=都收起）
const attMenuUp = ref(false) // 名单窗底部的行下方放不下菜单 → 向上弹，避免被窗口边裁掉
watch(rosterPopOpen, () => { attMenuFor.value = null }) // 名单弹窗开合时收起
function toggleAttMenu(a, ev) {
  if (attMenuFor.value === a.userRoleId) { attMenuFor.value = null; return }
  attMenuUp.value = false
  try {
    const el = ev && ev.currentTarget
    const r = el ? el.getBoundingClientRect() : null
    const pop = el ? el.closest('.roster-pop') : null
    const bottomEdge = pop ? Math.min(pop.getBoundingClientRect().bottom, window.innerHeight) : window.innerHeight
    if (r && bottomEdge - r.bottom < 160) attMenuUp.value = true // 四个选项约 140px
  } catch (e) { /* 量不到就默认向下 */ }
  attMenuFor.value = a.userRoleId
}
// 该行当前对应的状态值（下拉里高亮当前项用）
function attendanceValueOf(a) {
  if (!a) return 'none'
  if (a.signedIn) return a.attendanceMode === 'remote' ? 'remote' : 'onsite'
  return a.declined ? 'declined' : 'none'
}
async function applyAttendance(a, opt) {
  if (!canEditAttendance.value || !a || !opt) return
  attMenuFor.value = null
  try {
    await api.committeeSetAttendanceStatus(meetingId.value, a.userRoleId, opt.value)
    toast({ title: '已改为「' + opt.label + '」', icon: 'success' })
    await loadDetail()
  } catch (e) {
    toast({ title: (e && e.message) || '修改失败，请重试', icon: 'none' })
  }
}

async function guardUnvotedBeforeEnd() {
  const t = findUnvotedVoteTopic()
  if (!t) return true
  const missing = (t.total || 0) - (t.voted || 0)
  const res = await showModal({
    title: '还有人未投票',
    content: '「' + t.title + '」还有 ' + missing + ' 人未投票。已签到的委员都需要有投票结果，可由您代录（无意见选弃权）。',
    confirmText: '去代录',
    cancelText: '返回'
  })
  if (res.confirm) openTopicSheet(t) // 弹层内「代委员投票」为最显眼按钮，不再自动展开面板
  return false
}

// 完成会议前的待办拦截：会议结束即锁票（方案A），还有待处理议题时先确认，免得再也补不了
async function confirmDespitePendingTopics() {
  if (!pendingTopicCount.value) return true
  const res = await showModal({
    title: '',
    content: '还有 ' + pendingTopicCount.value + ' 项议题未处理，完成会议后不能再表决或补充。确认完成？',
    confirmText: '确认完成',
    cancelText: '返回处理'
  })
  return !!res.confirm
}

async function endAndGenerateMinutes() {
  if (!generated.value) { toast({ title: '请先完成录音识别', icon: 'none' }); return }
  if (!(await guardUnvotedBeforeEnd())) return
  if (!(await confirmDespitePendingTopics())) return
  // 不提前置 minutesGenerated（纪要其实还没生成，是跳到纪要页才开始生成的）：
  // 提前置会让按钮在跳转前闪现「已生成，查看纪要」误导用户；失败留在本页时更是错误状态。
  // 跳转期间由 leavingToMinutes 把按钮文案盖成「正在生成…」。
  leavingToMinutes.value = true
  const ok = await _doEndAndGo('/pages/minutes/minutes?meetingId=' + meetingId.value + '&from=meeting-live-quick&gen=1')
  if (!ok) leavingToMinutes.value = false
}

async function endWithoutMinutes() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可操作', icon: 'none' }); return }
  if (!(await guardUnvotedBeforeEnd())) return
  if (!(await confirmDespitePendingTopics())) return
  await _doEndAndGo('/pages/committee-detail/committee-detail?id=' + meetingId.value + '&from=meeting-live-quick')
}

// ── 会议资料：任意已签到参会人可上传/查看 ──
function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

function materialKey(name, sizeText) {
  return ((name || '').trim().toLowerCase()) + '::' + ((sizeText || '').trim().toLowerCase())
}

function isImageFile(file) {
  const type = (file && file.type) || ''
  const name = ((file && file.name) || '').toLowerCase()
  return type.indexOf('image/') === 0 || /\.(png|jpe?g|webp|bmp|gif|heic|heif)$/.test(name)
}

function duplicateMaterialText(file) {
  return isImageFile(file) ? '这张照片已经上传过了' : '这份文件已经上传过了'
}

// 资料上传：H5 用隐藏 input[type=file] 选文件，登记元数据（与小程序契约一致，仅存 name/sizeText）
const materialFileInput = ref(null)
function uploadMaterial() {
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (!materialFileInput.value) {
    materialFileInput.value = document.createElement('input')
    materialFileInput.value.type = 'file'
    materialFileInput.value.multiple = true
    materialFileInput.value.style.display = 'none'
    materialFileInput.value.addEventListener('change', onMaterialFileChange)
    document.body.appendChild(materialFileInput.value)
  }
  materialFileInput.value.value = ''
  materialFileInput.value.click()
}

async function onMaterialFileChange(e) {
  const picked = Array.from(e.target.files || []).slice(0, 3)
  if (!picked.length) return
  const existing = new Set((materials.value || []).map(function (m) {
    return materialKey(m.name || m.fileName, m.sizeText)
  }))
  const seen = new Set()
  const files = []
  const duplicateTips = []
  picked.forEach(function (f) {
    const item = { name: f.name || ('资料_' + Date.now()), sizeText: formatSize(f.size), raw: f }
    const key = materialKey(item.name, item.sizeText)
    if (existing.has(key) || seen.has(key)) {
      duplicateTips.push(duplicateMaterialText(f))
      return
    }
    seen.add(key)
    files.push(item)
  })
  if (!files.length) {
    toast({ title: duplicateTips[0] || '该材料已经上传过了', icon: 'none' })
    return
  }
  try {
    await Promise.all(files.map(function (f) {
      return api.committeeAddMaterial(meetingId.value, f.name, f.sizeText)
    }))
    const skipped = duplicateTips.length
    toast({ title: skipped ? ('已上传 ' + files.length + ' 份，跳过重复 ' + skipped + ' 份') : ('已上传 ' + files.length + ' 份'), icon: 'success' })
    loadDetail()
  } catch (err) {
    toast({ title: err.message || '上传失败', icon: 'none' })
  }
}

// ——— 列席人员（0723 向真实材料看齐）：居委/街道/物业等非委员到会者。
// 登记后进会议记录（实到写「委员数+列席数」）与纪要（结尾「××等同志到会指导」） ———
const observersText = computed(() => (detail.value && detail.value.observers) || '')
async function editObservers() {
  const res = await showModal({
    title: '登记列席人员',
    content: observersText.value,
    editable: true,
    placeholderText: '如：新村居委朱书记、街道治家服务站张同志（顿号分隔）',
    confirmText: '保存',
    cancelText: '取消'
  })
  if (!res.confirm) return
  try {
    await api.committeeSetObservers(meetingId.value, (res.content || '').trim())
    toast({ title: '列席人员已登记', icon: 'success' })
    loadDetail()
  } catch (e) {
    toast({ title: (e && e.message) || '保存失败，请重试', icon: 'none' })
  }
}

// ——— 拍照上传（0723 泛化）：既能拍委员合影，也能把纸质材料（手写记录/签到表/纸质方案等）
// 拍下来存档；拍完都直接进会议材料，公示材料自动带上。先选类型（决定命名前缀）再调相机。———
const photoUploading = ref(false)
let _groupPhotoInput = null
let _photoNamePrefix = '委员合影'  // 当前拍摄类型的命名前缀，onChange 时按此命名
const PHOTO_KINDS = [
  { label: '委员合影', prefix: '委员合影' },
  { label: '现场材料照片（手写记录/签到表等）', prefix: '现场材料' }
]
async function takePhoto() {
  if (photoUploading.value) return
  // 先选拍什么：老人一次点选，不用打字命名
  const res = await showActionSheet({ title: '拍照上传', itemList: PHOTO_KINDS.map(k => k.label) })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  _photoNamePrefix = PHOTO_KINDS[res.tapIndex].prefix
  if (!_groupPhotoInput) {
    _groupPhotoInput = document.createElement('input')
    _groupPhotoInput.type = 'file'
    _groupPhotoInput.accept = 'image/*'
    _groupPhotoInput.capture = 'environment'
    _groupPhotoInput.style.display = 'none'
    _groupPhotoInput.addEventListener('change', onGroupPhotoChange)
    document.body.appendChild(_groupPhotoInput)
  }
  _groupPhotoInput.value = ''
  _groupPhotoInput.click()
}
async function onGroupPhotoChange(e) {
  const f = (e.target.files || [])[0]
  if (!f) return
  photoUploading.value = true
  try {
    const prefix = _photoNamePrefix
    const already = (materials.value || []).filter(m => ((m.name || '')).indexOf(prefix) >= 0).length
    const fname = prefix + (already ? '（' + (already + 1) + '）' : '') + '.jpg'
    const file = new File([f], fname, { type: f.type || 'image/jpeg' })
    const r = await uploadAttachment(file)
    await api.committeeAddMaterial(meetingId.value, fname, humanSize(r.fileSize), r.fileType, r.url)
    toast({ title: '照片已存入会议材料', icon: 'success' })
    loadDetail()
  } catch (err) {
    toast({ title: (err && err.message) || '照片上传失败，请重试', icon: 'none' })
  } finally { photoUploading.value = false }
}

function previewMaterial(idx) {
  const list = materials.value || []
  const m = list[idx]
  if (!m) return
  if (m.url) { openMaterialViewer(m); return }
  showModal({ title: m.name, content: m.content || '（暂无预览内容）', showCancel: false, confirmText: '关闭' })
}

// ── 实时添加议题（主任/副主任）──
function openAddTopic() {
  if (!isHost.value) {
    toast({ title: '仅主持人可添加议题', icon: 'none' })
    return
  }
  addTopicVisible.value = true
  newTopicForm.title = ''
  newTopicForm.type = 'discussion'
  newTopicForm.decisionType = 'none'
  newTopicForm.options = []
  newTopicForm.content = ''
}
function closeAddTopic() { addTopicVisible.value = false; cancelTopicVoice() }

// ——— 议题内容语音输入（Web Speech 流式，与创建页同款；热词纠错走 helpers.applyHotwords） ———
const voiceOn = ref(false)
const voiceFinal = ref('')
const voiceInterim = ref('')
let _voiceRec = null
function _stopTopicVoice() {
  if (_voiceRec) { try { _voiceRec.stop() } catch (e) {} _voiceRec = null }
}
function startTopicVoice() {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRec) { toast({ title: '当前浏览器不支持语音识别', icon: 'none' }); return }
  _stopTopicVoice()
  voiceFinal.value = ''
  voiceInterim.value = ''
  voiceOn.value = true
  const r = new SpeechRec()
  r.lang = 'zh-CN'; r.continuous = true; r.interimResults = true
  r.onresult = function (e) {
    let fin = '', inter = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const t = e.results[i][0].transcript
      if (e.results[i].isFinal) fin += t
      else inter += t
    }
    if (fin) voiceFinal.value += applyHotwords(fin)
    voiceInterim.value = inter
  }
  r.onerror = function () { _stopTopicVoice() }
  _voiceRec = r
  r.start()
}
function confirmTopicVoice() {
  const text = (voiceFinal.value + voiceInterim.value).trim()
  cancelTopicVoice()
  if (text) newTopicForm.title = text
}
function retryTopicVoice() { startTopicVoice() }
function cancelTopicVoice() {
  _stopTopicVoice()
  voiceOn.value = false
  voiceFinal.value = ''
  voiceInterim.value = ''
}
// 议题类型：通知和讨论/表决（与准备会议时一致；0717 通知并入讨论）
function pickTopicType(t) {
  newTopicForm.type = t
  newTopicForm.decisionType = t === 'decision' ? 'simple' : 'none'
  newTopicForm.options = []
}
function pickDecisionType(t) {
  newTopicForm.decisionType = t
  newTopicForm.options = t === 'multi_choice' ? [{ id: 1, label: '' }, { id: 2, label: '' }] : []
}
function addTopicOption() {
  const options = newTopicForm.options || []
  const newId = options.length ? Math.max.apply(null, options.map(function (o) { return o.id })) + 1 : 1
  newTopicForm.options = options.concat([{ id: newId, label: '' }])
}
function removeTopicOption(i) {
  newTopicForm.options = newTopicForm.options.filter(function (_, idx) { return idx !== i })
}
async function submitAddTopic() {
  const f = newTopicForm
  if (!f.title.trim()) { toast({ title: '请输入议题内容', icon: 'none' }); return }
  let optionsJson = null
  if (f.type === 'decision' && f.decisionType === 'multi_choice') {
    const valid = (f.options || []).filter(function (o) { return o.label.trim() })
    if (valid.length < 2) { toast({ title: '多选一议题至少需要两个选项', icon: 'none' }); return }
    optionsJson = JSON.stringify(valid.map(function (o, i) { return { id: i + 1, label: o.label.trim() } }))
  }
  const dt = f.type === 'decision' ? f.decisionType : 'none'
  // 合并类型的落库映射（0717，与发起会议弹窗同款）：非表决类有正文存 notice、无正文存 discussion
  const mergedContent = f.type !== 'decision' ? (f.content || '').trim() : ''
  const sendType = f.type === 'decision' ? 'decision' : (mergedContent ? 'notice' : 'discussion')
  try {
    // 现场新增只允许通知和讨论/表决，重大事项后端会拦截；带正文走通报机制
    await api.committeeAddTopic(meetingId.value, f.title.trim(), sendType, dt, optionsJson, false, sendType === 'notice' ? mergedContent : null)
    toast({ title: '议题已添加', icon: 'success' })
    addTopicVisible.value = false
    loadDetail()
  } catch (e) { toast({ title: e.message || '添加失败', icon: 'none' }) }
}

// ── 居委会签字（结束步）──
async function toggleJuwei() {
  try { await api.committeeToggleJuwei(meetingId.value); loadDetail() }
  catch (e) { toast({ title: e.message || '操作失败', icon: 'none' }) }
}

// 会后打印用签到表：PDF 内含全体参会名单及手写签名栏。
async function exportAttendanceSheet() {
  if (exportingAttendanceSheet.value) return
  exportingAttendanceSheet.value = true
  try {
    const blob = await api.committeeExportAttendanceSheet(meetingId.value)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${(detail.value && detail.value.title) || '会议'}-会议签到表.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    toast({ title: '签到表已生成，请打印后签字', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '签到表生成失败', icon: 'none' })
  } finally {
    exportingAttendanceSheet.value = false
  }
}

// ── 导出签到名单（H5：写 CSV → Blob 下载；失败回退复制到剪贴板）──
async function exportAttendance() {
  try {
    const res = await api.committeeExportAttendance(meetingId.value)
    const content = res.content || ''
    const fileName = res.fileName || 'attendance.csv'
    try {
      const blob = new Blob(['﻿' + content], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      setTimeout(function () { URL.revokeObjectURL(url) }, 1000)
    } catch (dl) {
      try {
        await navigator.clipboard.writeText(content)
        toast({ title: '名单已复制', icon: 'none' })
      } catch (cp) {
        toast({ title: '导出失败', icon: 'none' })
      }
    }
  } catch (e) { toast({ title: e.message || '导出失败', icon: 'none' }) }
}

function noop() {}

function exitLive() {
  navigateBack()
}

// 顶栏右上「首页」：回业委会主页
function goHome() {
  redirectTo('/main')
  // 软路由偶发不切换（URL 变了却停在录音页）→ 500ms 后仍在本页则硬导航兜底
  setTimeout(() => {
    if (document.querySelector('.live-page')) window.location.replace('/main')
  }, 500)
}

// 顶栏左上返回：录音步(step2)回签到页；签到页不再回到「会议进行中」中间页，直接回首页。
async function onNavBack() {
  // 议题处理页的返回键只退回录音页，不离开会议，也不改变正在进行的录音。
  if (currentStep.value === 2 && meetingPhase.value === 'voting') {
    returnToRecordingPage()
    return
  }
  if (currentStep.value === 2) {
    // 只返回签到页面查看会议信息，不撤销已经完成的签到。
    // 因此底部按钮会显示“进入会议”，再次进入也不会重复签到。
    signinFx.value = false
    currentStep.value = 1
    persistQuickState()
    refreshAttendance()   // 只刷新名单/进度，不动步骤机
  } else {
    const url = '/main'
    redirectTo(url)
    setTimeout(() => { if (document.querySelector('.live-page')) window.location.replace(url) }, 500)
  }
}

async function returnToRecordingPage() {
  // 「返回录音」始终回到录音页（含现场会议已结束后：可回看录音段）；「会后整理」由右键负责
  sheetTopicId.value = null
  meetingPhase.value = 'recording'
  persistQuickState()
  // 离开最后一个议题时重新拉取一次，确保“全部完成”状态和结束会议按钮立即更新。
  try { await loadDetail() } catch (e) { /* 保留当前页面状态，下次轮询继续刷新 */ }
}
</script>

<style scoped>
.live-page { min-height:100vh; background:#f4f5f7; padding:24rpx; padding-bottom:48rpx; box-sizing:border-box; display:flex; flex-direction:column; }
.live-error-page { min-height:100vh; }
.live-error-card { margin-top:160rpx; background:#fff; border-radius:28rpx; padding:44rpx 36rpx; box-shadow:0 12rpx 36rpx rgba(31,35,41,0.08); display:flex; flex-direction:column; align-items:stretch; gap:24rpx; }
.live-error-title { font-size:38rpx; font-weight:800; color:#1F2329; text-align:center; }
.live-error-text { font-size:30rpx; line-height:1.6; color:#6B7280; text-align:center; }
.live-error-primary,
.live-error-secondary { height:88rpx; border-radius:18rpx; font-size:32rpx; font-weight:700; }
.live-error-primary { margin-top:8rpx; background:#0F766E; color:#fff; }
.live-error-secondary { background:#F3F4F6; color:#374151; }
/* 仅本页：顶栏矮 24rpx(12px)，124→100rpx（PageNav 是共享组件，其他页不动） */
.live-page :deep(.page-nav) { height:calc(100rpx + env(safe-area-inset-top)); }
.live-page :deep(.page-nav .nav-back) { height:100rpx; }
/* 顶栏统一为纯深橙（与其他页一致，覆盖 PageNav 默认黄橙渐变） */
:deep(.page-nav) { background: var(--c-primary-dark); }

/* 步骤指示器 */
/* 首屏容器：至少撑满一屏（100vh 减 顶栏+页面上下留白），参会名单被顶到首屏之下，往下拉才看到 */
.lp-fold { display:flex; flex-direction:column; min-height:calc(100vh - 96rpx); }

.core-card { background:#fff; border-radius:24rpx; padding:20rpx 28rpx 28rpx; margin-top:24rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.meeting-stage-card { margin-top:18rpx; padding:30rpx 28rpx 28rpx; border-radius:18rpx; background:#FFFDF9; border:2rpx solid #EEE6D9; box-shadow:0 3rpx 12rpx rgba(48,42,32,.04); }
.meeting-stage-title { font-size:35rpx; line-height:1.4; font-weight:700; color:#34363A; }
.meeting-stage-text { margin-top:10rpx; font-size:28rpx; line-height:1.55; color:#5f6570; }
.meeting-stage-next { display:block; width:auto; min-width:340rpx; margin:24rpx auto 0; padding:22rpx 44rpx; border:0; border-radius:999rpx; background:var(--c-primary-dark,#A85800); color:#fff; font-size:30rpx; font-weight:700; box-shadow:0 6rpx 16rpx rgba(168,88,0,.20); }
.meeting-stage-next[disabled] { opacity:.6; box-shadow:none; }
.meeting-console { margin-top:18rpx; padding:28rpx; border-radius:22rpx; background:#fff; border:2rpx solid #E8EBEF; box-shadow:0 8rpx 24rpx rgba(31,35,41,.06); }
.meeting-console-head { display:flex; align-items:flex-start; justify-content:space-between; gap:24rpx; }
.meeting-console-title { font-size:34rpx; line-height:1.35; font-weight:800; color:#20242A; }
.meeting-console-sub { margin-top:7rpx; max-width:470rpx; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:26rpx; color:#7A818B; }
.meeting-console-meta { margin-top:5rpx; font-size:23rpx; color:#9AA0A6; line-height:1.3; }
/* 签到胶囊随法定人数变色（都放浅、对比度不强）：未过半浅黄，过半浅绿。人数够不够只由它一处表达，不再另起带背景的一行。 */
.meeting-console-roster { flex-shrink:0; padding:10rpx 18rpx; border-radius:999rpx; background:#FBF1DC; color:#A9843F; font-size:25rpx; font-weight:700; }
.meeting-console-roster.ready { background:#E9F4EC; color:#5A9A73; }
/* 议题默认展开：编号+标题+类型标签，供会中参考；「处理议题」入口整宽放列表下方 */
.meeting-console-topics { margin-top:22rpx; padding-top:22rpx; border-top:2rpx solid #EEF0F2; }
.mct-head { display:flex; align-items:baseline; gap:12rpx; flex-wrap:wrap; margin-bottom:6rpx; }
.mct-title { font-size:30rpx; font-weight:750; color:#252A30; }
.mct-count { font-size:23rpx; color:#8A8F98; margin-left:auto; }  /* 共N项靠右 */
.mct-list { display:flex; flex-direction:column; }
.mct-item { display:flex; align-items:center; gap:14rpx; padding:16rpx 0; border-top:2rpx solid #F2F4F6; }
.mct-item:first-child { border-top:0; }
.mct-no { flex-shrink:0; width:40rpx; height:40rpx; border-radius:50%; background:#F3F5F7; color:#7A818B; font-size:24rpx; font-weight:700; display:flex; align-items:center; justify-content:center; }
.mct-name { flex:1; min-width:0; font-size:28rpx; color:#2A2F35; line-height:1.4; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.mct-type { flex-shrink:0; font-size:22rpx; font-weight:700; padding:3rpx 12rpx; border-radius:8rpx; }
.mct-type.notice { background:#E6F4FB; color:#1677B8; }
.mct-type.vote { background:#FFF0E5; color:#D56A16; }
.mct-type.discuss { background:#EAF6EE; color:#2E8B57; }
.mct-empty { padding:20rpx 0; text-align:center; color:#8A8F98; font-size:26rpx; }
/* 议题在录音页只是「参考+入口」，真正处理在下一页——降为次要描边样式，
   把主操作让给红色「开始录音」，避免两颗实心大按钮抢焦点。 */
.meeting-console .meeting-stage-next { display:block; width:70%; min-width:0; margin:18rpx auto 0; padding:20rpx 0; border:2rpx solid #D8C3A0; border-radius:14rpx; background:#FFFBF3; color:#8F4A06; font-size:29rpx; font-weight:700; box-shadow:none; }
.meeting-console .meeting-stage-next:active { background:#F6E8D3; }
/* 上边距拉开与顶栏的距离；底部留够绝对定位的步骤文字空间，避免探进「会议进行中」卡片——保持呼吸感 */
.live-page:not(.lp-signin) .lp-flow { margin-top:44rpx; padding:14rpx 4rpx 44rpx; }
/* 有顶部录音条时，流程链上移贴近录音条（录音条自带 18rpx 下边距，这里不再叠 44rpx） */
.live-page:not(.lp-signin) .lp-flow.lp-flow--tight { margin-top:-6rpx; }
.live-page:not(.lp-signin) .lp-flow-dot { width:42rpx; height:42rpx; font-size:22rpx; }
.live-page:not(.lp-signin) .lp-flow-label { margin-top:7rpx; font-size:22rpx; }
.live-page:not(.lp-signin) .lp-flow-line { margin-top:20rpx; }
.core-head { display:flex; align-items:center; justify-content:space-between; gap:18rpx; margin-bottom:22rpx; }
.core-title { font-size:38rpx; font-weight:700; color:#1F2024; line-height:1.35; }
.core-sub { flex-shrink:0; font-size:26rpx; color:#0F766E; background:#E7F6F3; border:2rpx solid #B9E4DC; border-radius:999rpx; padding:8rpx 16rpx; font-weight:700; }
.core-list { border:2rpx solid #EEF1F3; border-radius:20rpx; padding:4rpx 22rpx; margin-top:18rpx; background:#FAFBFC; }
.core-topic { display:flex; align-items:center; gap:16rpx; padding:18rpx 0; border-top:2rpx solid rgba(31,36,42,0.06); }
.core-topic:first-child { border-top:0; }
.core-topic-no { flex-shrink:0; width:44rpx; height:44rpx; border-radius:50%; background:#FDF3D6; color:#B26A19; border:2rpx solid #EBD08A; display:flex; align-items:center; justify-content:center; font-size:26rpx; font-weight:700; }
.core-topic-main { flex:1; min-width:0; display:flex; flex-direction:column; gap:6rpx; }
.core-topic-title { flex:1; min-width:0; color:#1F2024; font-size:31rpx; line-height:1.45; word-break:break-all; overflow:hidden; text-overflow:ellipsis; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
.core-topic-meta { font-size:25rpx; color:#8A8F98; }
.core-topic-type { align-self:flex-start; font-size:25rpx; font-weight:700; border-radius:8rpx; padding:4rpx 13rpx; line-height:1.4; }
.core-topic-type.notice { background:#E6F4FB; color:#1677B8; }
.core-topic-type.vote { background:#FFF0E5; color:#D56A16; }
.core-topic-type.discuss { background:#EAF6EE; color:#2E8B57; }
.core-topic-btn { flex-shrink:0; min-width:128rpx; min-height:80rpx; display:inline-flex; align-items:center; justify-content:center; border-radius:999rpx; padding:8rpx 24rpx; font-size:28rpx; font-weight:800; border:0; color:#fff; font-family:inherit; }
.core-topic-btn.notice { background:#1677B8; }
.core-topic-btn.vote { background:#D56A16; }
.core-topic-btn.discuss { background:#2E8B57; }
.core-topic-btn.done { background:#E8F6EC; color:#2E7D32; border:2rpx solid #BFE0B2; }
.core-empty { text-align:center; color:#8A8F98; font-size:30rpx; padding:36rpx 0; }
.record-helper-head { display:flex; align-items:center; justify-content:space-between; gap:16rpx; margin-bottom:20rpx; }
.record-helper-title { font-size:32rpx; font-weight:800; color:#1F2024; }
.rec-import-mini { flex-shrink:0; border:0; background:transparent; color:#8A8F98; font-size:24rpx; font-weight:600; padding:8rpx 10rpx; border-radius:10rpx; font-family:inherit; }
.rec-import-mini:active { background:#ECEFF3; color:#5F6670; }
.top-rec-status { display:flex; align-items:center; gap:14rpx; height:88rpx; margin:0 -3.2vw 18rpx; padding:0 32rpx; background:#FFF5F5; border-bottom:2rpx solid #FED7D7; color:#B42318; box-sizing:border-box; }
.top-rec-status.paused { background:#F7F8FA; border-bottom-color:#E2E6EA; color:#1F2937; }
.top-rec-dot { flex-shrink:0; width:18rpx; height:18rpx; border-radius:50%; background:#E23B3B; box-shadow:0 0 0 8rpx rgba(226,59,59,0.12); animation:recFlPulse 1.3s ease-out infinite; }
.top-rec-status.paused .top-rec-dot { background:#94A3B8; box-shadow:none; animation:none; }
.top-rec-main { flex-shrink:0; display:inline-flex; align-items:center; font-size:30rpx; font-weight:800; line-height:1; }
.top-rec-time { flex:1; min-width:0; display:inline-flex; align-items:center; font-size:30rpx; font-weight:800; line-height:1; color:inherit; font-variant-numeric:tabular-nums; }
.top-rec-btn { flex-shrink:0; border:2rpx solid #FECACA; background:#fff; color:#B42318; font-size:26rpx; font-weight:800; border-radius:999rpx; padding:10rpx 22rpx; font-family:inherit; }
.top-rec-status.paused .top-rec-btn { border-color:#CBD5E1; color:#334155; }
.top-rec-status.paused .top-rec-btn.primary { border-color:#126A72; background:#126A72; color:#fff; }
.top-rec-btn[disabled] { opacity:0.45; }
.live-quorum-tip { align-self:flex-end; margin:14rpx 2rpx -4rpx auto; padding:10rpx 16rpx; border:2rpx solid #F0C98E; border-radius:14rpx; background:#FFF8EA; color:#A35D08; display:flex; align-items:center; gap:12rpx; font-size:23rpx; font-weight:700; box-shadow:0 4rpx 12rpx rgba(163,93,8,.08); }
.live-quorum-tip.ready { border-color:#B9DEC7; background:#EEF9F2; color:#24784A; }
.live-quorum-count { padding-right:12rpx; border-right:2rpx solid currentColor; opacity:.9; }
.supp-card { background:#FFF; border:2rpx solid #E8EBEF; border-radius:18rpx; padding:18rpx 20rpx; margin-top:16rpx; box-shadow:0 5rpx 16rpx rgba(31,35,41,.04); } /* 会中辅助区压缩为紧凑工具卡 */
.supp-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16rpx; margin-bottom:12rpx; }
.recording-compact-head { align-items:center; margin-bottom:8rpx; }
/* 已录段落 toggle：独立成行、左起头、字号加大一号 */
.rec-seg-toggle-row { display:flex; align-items:center; justify-content:space-between; margin-top:6rpx; margin-bottom:2rpx; }
.rec-seg-label { font-size:26rpx; color:#6B7480; font-weight:650; }
/* 录音主控放卡片下部、居中 */
.rec-bottom-action { display:flex; justify-content:center; margin-top:16rpx; }
.rec-bottom-action .supp-btn.rec { width:56%; min-width:260rpx; height:72rpx; font-size:27rpx; }
.recording-summary-toggle { border:0; background:transparent; color:#6B7480; font-size:26rpx; font-weight:650; padding:6rpx 0; white-space:nowrap; }

/* 签到名单弹窗 */
.roster-pop-mask { position:fixed; inset:0; z-index:200; background:rgba(0,0,0,0.4); display:flex; align-items:center; justify-content:center; padding:48rpx; } /* 高于会后整理页(180)，两处都能弹 */
.roster-pop { width:100%; max-width:560rpx; max-height:76vh; overflow:auto; background:#fff; border-radius:22rpx; padding:26rpx 26rpx 30rpx; box-shadow:0 20rpx 56rpx rgba(0,0,0,0.25); }
.roster-pop-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:12rpx; }
.roster-pop-title { font-size:32rpx; font-weight:800; color:#1F2329; }
.roster-pop-close { font-size:46rpx; color:#8A8F98; line-height:1; padding:0 6rpx; }
.roster-pop-row { position:relative; display:flex; align-items:center; gap:16rpx; padding:18rpx 4rpx; border-top:2rpx solid #F2F4F6; }
.roster-pop-row:first-child { border-top:0; }
.rp-name { flex:1; min-width:0; font-size:30rpx; color:#1F2329; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
/* 主持人改状态的小下拉箭头（状态右侧），弱化配色不抢眼 */
.rp-edit { flex-shrink:0; width:44rpx; height:44rpx; display:flex; align-items:center; justify-content:center; border-radius:10rpx; color:#A0A5AD; font-size:24rpx; background:#F4F5F7; }
.rp-edit:active, .rp-edit.on { background:#E8EAED; color:#5F6673; }
/* 状态下拉：从 ▾ 处悬浮弹出一列选项（0722 用户定，不占行、不弹底部大面板） */
.rp-menu { position:absolute; right:0; top:calc(100% - 8rpx); z-index:30; display:flex; flex-direction:column;
  min-width:184rpx; padding:8rpx; background:#fff; border:2rpx solid #E8EAED; border-radius:14rpx;
  box-shadow:0 10rpx 28rpx rgba(31,35,41,0.16); }
.rp-menu-item { padding:14rpx 22rpx; font-size:25rpx; font-weight:600; color:#42464D; border-radius:10rpx; line-height:1.3; }
.rp-menu-item:active { background:#F1F2F4; }
.rp-menu-item.cur { color:#0F766E; background:#EFF6F5; }
.rp-menu.up { top:auto; bottom:calc(100% - 8rpx); }
.rp-state { flex-shrink:0; font-size:27rpx; font-weight:700; }
.rp-state.on { color:#2E8B57; }
.rp-state.remote { color:#2980B9; }
.rp-state.wait { color:#B0752F; }
.rp-state.off { color:#B0392E; }
/* ⚠ 须用 .supp-btn.recording-head-action 提权：基础 .supp-btn(84rpx) 在文件更后面，
   单类同权重会被其覆盖（0722 用户实测按钮一直没变小的根因） */
.supp-btn.recording-head-action { flex-shrink:0; width:auto; min-width:142rpx; height:54rpx; padding:0 26rpx; font-size:26rpx; font-weight:600; }
.supp-head.supp-head-2 { margin-top:22rpx; padding-top:20rpx; border-top:2rpx solid #EAEDF0; } /* 「会议材料」子标题：与上方「会议录音」区拉开分隔 */
.supp-title { font-size:30rpx; font-weight:700; color:#2F3740; }
.supp-sub { flex:1; text-align:right; font-size:25rpx; color:#7B8490; line-height:1.45; }
.supp-actions { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:14rpx; margin-bottom:12rpx; }
.supp-actions.single { grid-template-columns:1fr; }
.supp-actions.single .supp-btn { width:56%; min-width:250rpx; justify-self:center; } /* 会中操作统一胶囊宽度 */
.supp-actions.paused { grid-template-columns:repeat(2, minmax(0, 1fr)); }
.supp-actions.single.paused { grid-template-columns:1fr; gap:24rpx; }
.supp-actions.single.paused .supp-btn { width:30%; min-width:128rpx; height:46rpx; font-size:22rpx; font-weight:500; justify-self:center; }  /* 暂停态按钮（0722 二次再缩：46rpx/22号） */
.rec-list-before-action { margin:4rpx 0 12rpx; padding:8rpx 14rpx; border:2rpx solid #E4E8ED; border-radius:14rpx; background:#FFF; }
.supp-btn { height:84rpx; border-radius:999rpx; border:2rpx solid #D9E2EA; background:#F8FAFB; color:#334155; font-size:28rpx; font-weight:600; font-family:inherit; }
.supp-btn.rec { border-color:#C0685A; background:#C0685A; color:#FFF; box-shadow:none; }  /* 稍减重：调浅一档 + 去投影 */
.supp-actions.single:not(.paused) .supp-btn.rec { width:310rpx; min-width:310rpx; height:88rpx; font-size:28rpx; }
.supp-btn.rec:active { background:#A85446; border-color:#A85446; }
.supp-btn.upload-rec { border-color:#D6A75F; background:#FFF9EF; color:#91611C; }
.supp-btn.ai { border-color:#BFD7D9; background:#EAF6F6; color:#126A72; }
.supp-actions.single .supp-material-btn { width:310rpx; min-width:310rpx; height:88rpx; border-radius:999rpx; border-color:#BFC9D3; background:#FFFFFF; color:#52606D; font-size:28rpx; font-weight:600; box-shadow:none; }
.supp-actions.single .supp-material-btn:active { background:#F2F5F7; border-color:#AEBAC6; }
.supp-actions.paused .supp-material-btn { grid-column:1 / -1; } /* 仅暂停时「上传材料」铺满整行；非暂停(开始录音前)与「开始录音」左右并排 */
.supp-btn:active { background:#EEF3F7; }
.supp-btn[disabled] { opacity:0.55; box-shadow:none; }
.supp-files { border-top:2rpx solid #F0F2F4; margin-top:12rpx; padding-top:10rpx; }
.supp-file { display:flex; align-items:center; justify-content:space-between; gap:16rpx; padding:16rpx 0; border-bottom:2rpx solid #F5F6F8; }
.supp-file:last-child { border-bottom:0; }
.supp-file-name { flex:1; min-width:0; font-size:28rpx; color:#2563EB; text-decoration:underline; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.supp-file-size { flex-shrink:0; font-size:24rpx; color:#8A8F98; }
.supp-files-foot { font-size:24rpx; color:#8A8F98; text-align:right; padding-top:14rpx; margin-top:4rpx; border-top:2rpx solid #F5F6F8; }
.supp-head-click { cursor:pointer; }
/* 结束会议：会议进行页底部的固定出口（纪要可选，不生成也能结束）。白底描边 full-width，清晰但不抢 AI纪要 主按钮的焦点 */
/* 返回录音 + 结束现场会议：同等重量并排（返回录音更常用，与结束会议等宽等重）。
   议题处理阶段(.pinned)钉底常驻，随时可点；两按钮同款中等重量、以颜色区分语义（0722） */
.end-meeting-row { margin-top:38rpx; padding:24rpx 0 calc(18rpx + env(safe-area-inset-bottom)); display:flex; flex-direction:row; align-items:stretch; justify-content:center; gap:16rpx; border-top:2rpx solid #ECE8E1; }
/* 钉底栏：抬离屏幕底边一点、去掉白色背景条，用页面底色（仍不透明，挡住滚动内容不露馅） */
.end-meeting-row.pinned { position:fixed; z-index:80; left:0; right:0; bottom:0; margin-top:0; padding:14rpx 32rpx calc(60rpx + env(safe-area-inset-bottom)); background:#f4f5f7; }
/* 两按钮等宽、缩小一档：配色对齐卡内「临时添加议题」(中性白描边) 与「结束表决」(浅暖描边) */
.back-recording-btn { flex:1 1 0; display:flex; align-items:center; justify-content:center; min-height:70rpx; box-sizing:border-box; border:2rpx solid #E6E9ED; border-radius:14rpx; background:#fff; color:#7A7F87; font-size:26rpx; font-weight:700; font-family:inherit; }
.back-recording-btn:active { background:#F3F4F6; }
.end-meeting-btn { flex:1 1 0; display:flex; align-items:center; justify-content:center; width:auto; margin:0; min-height:70rpx; box-sizing:border-box; border:2rpx solid #F0DBB4; background:#FFFDF8; color:#C58A3E; font-size:26rpx; font-weight:700; border-radius:14rpx; font-family:inherit; }
.end-meeting-btn:active { background:#FCF5E7; }
/* 两按钮的左右箭头同款样式（← / →）：仅方向与前后位置不同 */
.emb-arrow { margin-left:10rpx; font-weight:600; opacity:0.8; font-size:28rpx; }
.emb-arrow.pre { margin-left:0; margin-right:10rpx; }
.live-page.has-fixed-end { padding-bottom:150rpx; }
/* 议题处理阶段底部操作栏钉底：给页面留出等高底边，滚到底时最后内容不被盖住 */
.live-page.has-fixed-end-voting { padding-bottom:235rpx; }
/* 结束现场会议：改成钉底操作栏，消灭原来悬空按钮上方那块大空白。
   描边+浅底，分量比红色「开始录音」轻，不抢主操作。 */
.mlq-endbar { position:fixed; z-index:80; left:0; right:0; bottom:0; padding:14rpx 32rpx calc(14rpx + env(safe-area-inset-bottom)); background:rgba(255,255,255,.97); border-top:2rpx solid #ECEEF1; backdrop-filter:blur(8px); }
.mlq-endbar-space { height:170rpx; }  /* 占位，保证滚动到底时最后内容不被钉底栏盖住 */
.fixed-end-field-btn { display:block; width:70%; margin:0 auto; height:92rpx; border:2rpx solid #B47A34; border-radius:16rpx; background:#FFFBF3; color:#8F4A06; font-size:30rpx; font-weight:750; font-family:inherit; }
.fixed-end-field-btn:active { background:#F6E8D3; }
.end-review-page { position:fixed; inset:0; z-index:180; background:#F6F7F9; display:flex; flex-direction:column; }
.end-review-body { flex:1; min-height:0; overflow:auto; padding:34rpx 30rpx calc(42rpx + env(safe-area-inset-bottom)); box-sizing:border-box; display:flex; align-items:flex-start; }
.end-review-card { width:100%; min-height:860rpx; background:#fff; border:2rpx solid #E8EEF0; border-radius:30rpx; padding:25rpx 38rpx 44rpx; box-sizing:border-box; box-shadow:0 18rpx 46rpx rgba(25,40,55,0.10); }
.end-review-kicker { display:inline-flex; padding:8rpx 18rpx; border-radius:999rpx; background:#EAF6F6; color:#0F766E; font-size:25rpx; font-weight:800; }
/* ── 会后整理任务清单（0722 重排）：引导语 + 三个核对项（状态灯）+ 结论 + 主按钮 ── */
.er-lead { font-size:30rpx; font-weight:650; color:#2A2F36; line-height:1.6; }
.er-item { margin-top:32rpx; padding-top:30rpx; border-top:2rpx solid #EEF0F3; }
.er-item-head { display:flex; align-items:center; gap:14rpx; }
.er-item-no { flex-shrink:0; width:40rpx; height:40rpx; border-radius:50%; background:#FDF3D6; color:#B26A19; border:2rpx solid #EBD08A; display:flex; align-items:center; justify-content:center; font-size:24rpx; font-weight:700; }
.er-item-title { font-size:30rpx; font-weight:700; color:#23272E; }
/* 状态徽章跟在标题旁（0722 用户定），右上角是展开/收起按钮 */
.er-item-state { flex-shrink:0; font-size:23rpx; font-weight:600; padding:6rpx 16rpx; border-radius:999rpx; }
.er-item-toggle { margin-left:auto; flex-shrink:0; width:48rpx; height:48rpx; display:flex; align-items:center; justify-content:center; border-radius:12rpx; color:#A0A5AD; font-size:24rpx; background:#F4F5F7; }
.er-item-toggle:active { background:#E8EAED; color:#5F6673; }
.er-item-state.ok { background:#EAF6E5; color:#2E7D32; }
.er-item-state.warn { background:#FFF6E8; color:#B26A00; }
.er-item-state.busy { background:#EAF3FC; color:#1F6FB2; }
.er-item-state.muted { background:#F2F3F5; color:#8A9099; }
.er-item-sub { margin-top:12rpx; padding-left:16rpx; font-size:25rpx; color:#8A9099; line-height:1.5; }
/* 列席人员行：签到项内轻量一行，标签+值+登记按钮 */
.er-observers { display:flex; align-items:center; gap:14rpx; margin-top:14rpx; padding:14rpx 16rpx; background:#F7F8FA; border-radius:12rpx; }
.er-observers-label { flex-shrink:0; font-size:26rpx; color:#55585E; font-weight:600; }
.er-observers-value { flex:1; min-width:0; font-size:25rpx; color:#8A9099; line-height:1.5; overflow:hidden; text-overflow:ellipsis; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
.er-observers-edit { flex-shrink:0; border:2rpx solid #8FB3DC; background:#fff; color:#2464B4; font-size:25rpx; font-weight:600; border-radius:999rpx; padding:8rpx 24rpx; }
.er-item-actions { margin-top:26rpx; padding-left:16rpx; display:flex; gap:14rpx; }
.er-act { border:2rpx solid #D8DBE0; background:#fff; color:#55585E; font-size:24rpx; font-weight:500; border-radius:999rpx; padding:10rpx 26rpx; font-family:inherit; line-height:1.3; }
.er-act:active { background:#F1F2F4; }
.er-act.warm { border-color:#EAC79A; background:#FFFCF7; color:#B26A19; font-weight:600; }
.er-act:disabled { opacity:.55; }
/* 议题结果简表：标题省略 + 右侧结论小签 */
.er-topic-list { margin-top:20rpx; padding-left:16rpx; display:flex; flex-direction:column; gap:22rpx; }
.er-topic-row { display:flex; align-items:center; gap:14rpx; }
.er-topic-title { flex:1; min-width:0; font-size:26rpx; color:#4A5058; line-height:1.5; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.er-topic-result { flex-shrink:0; font-size:22rpx; font-weight:600; font-variant-numeric:tabular-nums; }
.er-topic-result.pass { color:#2E7D32; }
.er-topic-result.fail { color:#C0392B; }
.er-topic-result.done { color:#2E7D32; } /* 已讨论/已通报也用绿色（0722 用户定） */
.er-topic-result.todo { color:#B26A00; }
/* 会议录音：每段一行（段号+时长 + 转写/删除小按钮） */
.er-rec-list { margin-top:18rpx; padding-left:16rpx; display:flex; flex-direction:column; gap:18rpx; }
.er-rec-row { display:flex; align-items:center; gap:14rpx; }
.er-rec-name { flex:1; min-width:0; font-size:26rpx; color:#4A5058; font-variant-numeric:tabular-nums; }
.er-rec-act { flex-shrink:0; border:2rpx solid #D8DBE0; background:#fff; color:#55585E; font-size:22rpx; font-weight:500; border-radius:999rpx; padding:6rpx 20rpx; font-family:inherit; line-height:1.3; }
.er-rec-act:active { background:#F1F2F4; }
.er-rec-act.del { border-color:#EBC2BC; color:#C9483D; }
.er-rec-act.del:active { background:#FDEDEC; }
/* 会议材料：现有材料一行一条，点名字预览 */
.er-mat-list { margin-top:18rpx; padding-left:16rpx; display:flex; flex-direction:column; gap:18rpx; }
.er-mat-row { display:flex; align-items:center; gap:14rpx; cursor:pointer; }
.er-mat-name { flex:1; min-width:0; font-size:26rpx; color:#1A73E8; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.er-mat-size { flex-shrink:0; font-size:22rpx; color:#A0A5AD; }
/* 会议纪要块与清单之间空出一段，形成"核对完 → 生成"的段落感 */
.er-hint { margin-top:64rpx; text-align:center; font-size:24rpx; color:#98A2B3; line-height:1.5; }
.end-review-primary { display:block; width:70%; margin:14rpx auto 0; height:80rpx; border:0; border-radius:20rpx; background:#0F766E; color:#fff; font-size:30rpx; font-weight:700; line-height:80rpx; box-shadow:0 10rpx 22rpx rgba(15,118,110,0.22); }
.end-review-primary[disabled] { background:#C7D1D5; box-shadow:none; color:#fff; }
/* 直接完成会议：与生成会议纪要同等明显（0722 用户定）——同宽同高同字重，浅底描边区分语义 */
.end-review-secondary { display:block; width:70%; margin:18rpx auto 0; height:80rpx; box-sizing:border-box; border:2rpx solid #B9C0C9; border-radius:20rpx; background:#F7F8FA; color:#3C434B; font-size:30rpx; font-weight:700; }
.end-review-secondary:active { background:#EBEDF0; }
/* 主持人操作区：仅「临时添加议题」，居中的安静次要按钮（方案A 已移除结束表决） */
.host-topic-actions { border-top:2rpx solid #F2F2F4; margin-top:10rpx; padding:18rpx 0 0; display:flex; justify-content:center; gap:16rpx; }
.hta-btn { flex:1 1 0; max-width:340rpx; font-size:27rpx; font-weight:600; background:#fff; border:2rpx solid #D8DBE0; color:#55585E; border-radius:999rpx; padding:15rpx 20rpx; line-height:1.3; font-family:inherit; }
.hta-btn:active { background:#F1F2F4; }
.hta-btn:disabled { opacity:.55; }

/* 主任：签到统计卡（点"查看名单"标签展开） */
.lp-signin { position:relative; background:#fff; border-radius:24rpx; padding:32rpx; margin-bottom:28rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.lp-signin.open { box-shadow:0 10rpx 34rpx rgba(255,168,0,0.22); }
/* 弱化版（底部不显眼处）：融入页面背景、无卡片感、小字淡色细进度条 */
.lp-signin.faint { background:transparent; box-shadow:none; padding:6rpx 6rpx 2rpx; margin:8rpx 0 54rpx; }
.lp-signin.faint.open { box-shadow:none; }
.lp-signin.faint .ls-head { margin-bottom:10rpx; }
.lp-signin.faint .ls-label { font-size:28rpx; color:#9AA0A6; font-weight:600; }
.lp-signin.faint .ls-count { font-size:26rpx; color:#9AA0A6; }
.lp-signin.faint .ls-count b { font-size:30rpx; }
.lp-signin.faint .ls-tag { font-size:26rpx; padding:6rpx 16rpx; }
.lp-signin.faint .ls-bar { height:10rpx; }
.ls-head { display:flex; align-items:center; gap:16rpx; margin-bottom:22rpx; }
.ls-label { font-size:36rpx; font-weight:700; color:#1F2024; }
.ls-count { font-size:34rpx; color:#666; }
.ls-count b { font-size:42rpx; font-weight:800; }
.ls-count.low b { color:#E74C3C; }
.ls-count.mid b { color:#E6920A; }
.ls-count.full b { color:#27AE60; }
/* 查看名单小标签（手机端点击展开/收起） */
.ls-tag { margin-left:auto; flex-shrink:0; border:2rpx solid #FFD79A; background:#FFF6E6; color:#D88900; font-size:30rpx; font-weight:600; padding:8rpx 20rpx; border-radius:999rpx; line-height:1.3; }
.ls-tag.on { background:#FFA800; border-color:#FFA800; color:#fff; }
.ls-bar { height:18rpx; border-radius:9rpx; background:#F0F0F2; overflow:hidden; }
.ls-fill { height:100%; border-radius:9rpx; transition:width .35s ease, background .35s ease; }
/* 进度条颜色随签到比例变化：少→红，过半→黄，全签到→绿 */
.ls-fill.low { background:linear-gradient(90deg,#FF8A8A,#E74C3C); }
.ls-fill.mid { background:linear-gradient(90deg,#FFD24D,#F5A623); }
.ls-fill.full { background:linear-gradient(90deg,#5BD08A,#27AE60); }

/* 明细名单：仅由"查看名单"标签点击控制 (v-show) */
.ls-pop { position:absolute; left:0; right:0; top:calc(100% + 10rpx); z-index:30; background:#fff; border-radius:20rpx; box-shadow:0 16rpx 48rpx rgba(0,0,0,0.18); padding:20rpx 24rpx; }
.ls-pop::before { content:''; position:absolute; left:60rpx; top:-12rpx; width:24rpx; height:24rpx; background:#fff; transform:rotate(45deg); box-shadow:-3rpx -3rpx 8rpx rgba(0,0,0,0.04); }
/* 底部弱化版：明细向上弹出，避免遮挡底部按钮 */
.ls-pop.up { top:auto; bottom:calc(100% + 10rpx); }
.ls-pop.up::before { top:auto; bottom:-12rpx; box-shadow:3rpx 3rpx 8rpx rgba(0,0,0,0.04); }
.ls-pop-head { display:flex; justify-content:space-between; align-items:center; font-size:30rpx; font-weight:700; color:#1F2024; padding-bottom:14rpx; margin-bottom:10rpx; border-bottom:2rpx solid #F2F2F4; }
.ls-pop-sub { font-size:28rpx; color:#D88900; font-weight:800; }
.ls-pop-list { max-height:48vh; overflow-y:auto; }
.ls-pop-item { display:flex; align-items:center; gap:14rpx; padding:14rpx 0; border-bottom:2rpx solid #F6F6F8; }
.ls-pop-item:last-child { border-bottom:0; }
.ls-dot { width:18rpx; height:18rpx; border-radius:50%; flex-shrink:0; background:#D5D5DA; }
.ls-dot.on { background:#27AE60; }
.ls-dot.off { background:#E74C3C; }
.ls-dot.wait { background:#D5D5DA; }
.ls-name { font-size:32rpx; color:#1F2024; flex-shrink:0; }
.ls-me { font-size:26rpx; color:#999; }
.ls-role { font-size:28rpx; color:#999; flex:1; min-width:0; }
.ls-state { font-size:28rpx; font-weight:600; flex-shrink:0; padding:4rpx 16rpx; border-radius:12rpx; }
.ls-state.on { color:#27AE60; background:#E8F7EE; }
.ls-state.off { color:#E74C3C; background:#FDECEA; }
.ls-state.wait { color:#999; background:#F2F2F4; }

/* 参会人员名单卡：列表滚动区 */
/* 人员名单卡（仅列表）收窄留白；沉底的统计卡（标题+进度条） */
.lp-roster { padding:16rpx 32rpx; }
/* 签到统计卡整体缩小约 30%（卡片内边距 + 标题 + 数字 + 进度条一并缩） */
.lp-roster-stats { padding:27rpx 22rpx; }
.lp-roster-stats .ls-head { margin-bottom:11rpx; }
.lp-roster-stats .lp-card-title { font-size:28rpx; }
.lp-roster-stats .ls-count { font-size:24rpx; }
.lp-roster-stats .ls-count b { font-size:30rpx; }
.lp-roster-stats .ls-bar { height:13rpx; border-radius:7rpx; }
/* 名单完整展示（不做内部滚动）；首屏自然只露出前几行，往下滚页面看其余 */
/* 默认只露约 3-4 人，其余在本区下拉查看（不撑高卡片） */
.lr-list { margin-top:8rpx; max-height:300rpx; overflow-y:auto; }
/* 行内「正在录音」标签（并入右侧状态栏，未录音时隐藏；录音蓝 / 暂停黑，避免与缺席红混淆） */
.lr-rec-tag { display:inline-flex; align-items:center; gap:8rpx; flex-shrink:0; font-size:26rpx; font-weight:700; color:#2563EB; background:#E8F0FE; padding:4rpx 14rpx; border-radius:12rpx; margin-right:20rpx; }
.lr-rec-tag.paused { color:#1F2024; background:#EDEEF0; }
.lr-rec-dot { width:14rpx; height:14rpx; border-radius:50%; background:#2563EB; animation:qkpulse 1.2s ease-in-out infinite; }
.lr-rec-tag.paused .lr-rec-dot { animation:none; opacity:.85; background:#1F2024; }

/* 步骤卡片 */
.lp-card { background:#fff; border-radius:24rpx; padding:38rpx 32rpx; margin-bottom:44rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); } /* 卡间距 +8px(28→44) */
.lp-card-step { display:block; font-size:28rpx; color:#D88900; font-weight:700; margin-bottom:12rpx; }
.lp-card-title { display:block; font-size:40rpx; font-weight:700; color:#1f2329; line-height:1.4; }

.lp-primary-btn { display:block; width:100%; box-sizing:border-box; background:var(--c-primary-dark); color:#fff; border:0; border-radius:44rpx; font-size:32rpx; font-weight:600; padding:26rpx 0; margin-top:12rpx; }
.lp-primary-btn:active { background:var(--c-primary-strong); }
/* 签到按钮：窄一点居中（与其他页主按钮一致），别全宽显大 */
.lp-primary-btn.narrow { width:420rpx; margin-left:auto; margin-right:auto; font-size:42rpx; padding:20rpx 0; }
/* 录音卡两个按钮：宽度贴合文字、只比字宽一圈、居中 */
.lp-primary-btn[disabled] { background:#d8c3ab; color:#fff; }
.lp-ghost-btn { display:block; width:100%; box-sizing:border-box; background:#fff; color:#FFA800; border:2rpx solid #FFA800; border-radius:44rpx; font-size:30rpx; padding:22rpx 0; margin-top:16rpx; margin-bottom:8rpx; }
/* 没有新录音可传时，"结束录音并上传"弱化为不可用样子（仍可点，点了弹提示说明已上传） */
.lp-ghost-btn.muted { color:#BBB; border-color:#E2E2E2; background:#FAFAFA; }
/* 结束录音并上传：浅橙填充，明显一点（覆盖 muted 灰化，始终醒目） */
.lp-ghost-btn.finish-upload, .lp-ghost-btn.finish-upload.muted { background:#FFF1E0; color:var(--c-primary-dark); border:2rpx solid var(--c-primary-dark); font-weight:700; width:fit-content; margin-left:auto; margin-right:auto; margin-top:8rpx; padding:10rpx 34rpx; font-size:26rpx; } /* 上距收紧让录音卡更紧凑 */
/* 上传后空闲提示：已录段数会合并为一份 */
.qk-seg-hint { font-size:26rpx; color:#9A6A00; line-height:1.5; margin:6rpx 0 2rpx; background:#FFF8EC; border-radius:12rpx; padding:14rpx 18rpx; text-align:center; }
/* 上传录音中：转圈 + 实时进度百分比 */
.qk-seg-hint.uploading { display:flex; align-items:center; justify-content:center; gap:12rpx; font-weight:600; }
.qk-up-spin { width:30rpx; height:30rpx; border:5rpx solid #F0D9B8; border-top-color:#C76A00; border-radius:50%; animation:qk-up-spin 0.7s linear infinite; }
.qk-up-pct { color:#C76A00; font-variant-numeric:tabular-nums; }
@keyframes qk-up-spin { to { transform:rotate(360deg); } }
/* 识别失败/空转写：明确红色提示条 */
.qk-seg-hint.asr-error { background:#FDECEA; color:#C0392B; font-weight:600; text-align:left; }

/* 第4步底部：次要操作弱化为小链接 */
.qk-sub-actions { display:flex; align-items:center; justify-content:center; gap:18rpx; margin-top:18rpx; }
.qk-sub-link { font-size:28rpx; color:#666; padding:8rpx 4rpx; }
.qk-sub-sep { color:#666; }
/* 结束步：不显眼的次要入口（手写纪要 / 先结束后补） */
.qk-minor-links { display:flex; flex-direction:column; align-items:center; gap:8rpx; margin-top:20rpx; }
.qk-minor-link { font-size:28rpx; color:#777; padding:12rpx 16rpx; }

/* 录音控件 */
/* 录音卡（精简版）：圆圈即录音按钮——橙芯白环=待录，红芯呼吸=录音中；无说明/状态小字。整体缩两号+紧凑 */
.lp-rec { padding:14rpx 26rpx 4rpx; } /* 卡片再缩一号：内边距进一步收紧(圆圈/字号不变) */
.lp-rec .lp-card-title { font-size:34rpx; } /* 标题缩一号(40→34)，比之前回大一点 */
.lp-rec { padding-bottom:22rpx; } /* 录音卡底部空白再收紧(34→22) */
.qk-recorder { display:flex; flex-direction:column; align-items:center; gap:4rpx; padding:2rpx 0 0; }
.qk-rec-circle { width:120rpx; height:120rpx; border-radius:50%; background:var(--c-primary); color:#fff; font-size:24rpx; font-weight:700; display:flex; align-items:center; justify-content:center; border:5rpx solid #FFF3E0; box-shadow:0 6rpx 16rpx rgba(199,106,0,0.24); box-sizing:border-box; } /* 方案A：大幅缩小 204→120、字36→24 */
/* 圈内文案固定两字一行（"开始/录音"两行） */
.qrc-txt { display:block; width:2em; line-height:1.35; text-align:center; word-break:break-all; }
.qk-rec-circle:active { transform:scale(0.95); }
.qk-rec-circle:disabled { background:#E5E8EC; color:#999; border-color:#F2F2F4; box-shadow:none; }
.qk-rec-circle.on { background:#E74C3C; border-color:#FDECEA; box-shadow:0 8rpx 22rpx rgba(231,76,60,0.30); animation:qkpulse 1.2s ease-in-out infinite; }
@keyframes qkpulse { 0%,100% { opacity:1; transform:scale(1); } 50% { opacity:.55; transform:scale(.92); } }
.qk-rec-time { font-size:36rpx; font-weight:700; color:#1f2329; letter-spacing:2rpx; margin-top:4rpx; font-variant-numeric:tabular-nums; font-feature-settings:'tnum' 1; } /* 计时用等宽数字(tabular)，秒数跳动不晃 */

.qk-note { font-size:28rpx; color:#666; line-height:1.6; margin-top:20rpx; background:#FAFBFC; border-radius:14rpx; padding:18rpx 20rpx; }
.qk-note.warn { color:#C77700; background:#FFF8EC; }

/* 入会签到：状态待签到（左）+ 身份（右）一行两端对齐 */
/* 签到卡（精简版）：无标题行，收窄上下留白 */
.lp-sign { padding:30rpx 32rpx 26rpx; }

/* 转写原文 */
.qk-tr-list { background:#FAFBFC; border-radius:14rpx; padding:12rpx 20rpx; }
.qk-tr-seg { padding:16rpx 0; border-bottom:2rpx solid #EEF1F4; }
.qk-tr-seg:last-child { border-bottom:none; }
.qk-tr-meta { display:flex; align-items:center; gap:14rpx; margin-bottom:8rpx; }
.qk-tr-spk { font-size: 28rpx; color:#fff; background:#7C8AA0; border-radius:8rpx; padding:2rpx 14rpx; }
.qk-tr-time { font-size: 28rpx; color:#666; }
.qk-tr-text { font-size:30rpx; color:#333; line-height:1.7; }

.qk-transcript-card { border:2rpx solid #EEF1F4; background:#FAFBFC; border-radius:18rpx; padding:22rpx; margin:24rpx 0 8rpx; }
.qk-transcript-card.compact { margin:0 0 24rpx; }
.qk-transcript-head { display:flex; align-items:flex-start; justify-content:space-between; gap:18rpx; }
.qk-transcript-title { display:block; font-size:30rpx; font-weight:700; color:#1F2024; line-height:1.4; }
.qk-transcript-meta { display:block; font-size: 28rpx; color:#666; margin-top:4rpx; line-height:1.35; }
.qk-transcript-action { flex-shrink:0; color:#C77800; background:#FFF6E5; border:2rpx solid #FFE2A8; border-radius:16rpx; font-size:28rpx; padding:8rpx 20rpx; line-height:1.35; }
.qk-transcript-preview { display:block; margin-top:14rpx; color:#5F6673; font-size:28rpx; line-height:1.6; word-break:break-all; }
.qk-transcript-sheet { width:88%; max-height:82vh; background:#fff; border-radius:24rpx; padding:28rpx; }
.qk-sheet-head { display:flex; align-items:center; justify-content:space-between; gap:18rpx; margin-bottom:18rpx; }
.qk-sheet-close { font-size:44rpx; color:#666; padding:0 8rpx; }
.qk-transcript-tabs { display:flex; gap:14rpx; margin-bottom:18rpx; }
.qk-transcript-tab { flex:1; text-align:center; padding:16rpx 0; border-radius:16rpx; font-size:28rpx; color:#777; background:#F3F5F7; }
.qk-transcript-tab.on { color:#C77800; background:#FFF6E5; font-weight:700; }
.qk-transcript-scroll { max-height:58vh; }
.qk-transcript-body { display:block; color:#333; font-size:30rpx; line-height:1.7; word-break:break-all; white-space:pre-wrap; }

/* “或”分割线 */
/* 暂停态：继续录音 + 重新录音 并排橙色窄按钮 */
.qk-rec-actions { display:flex; gap:16rpx; margin-top:12rpx; }
.qk-rec-actions .lp-primary-btn { flex:1; width:auto; margin-top:0; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2rpx; padding:16rpx 6rpx; line-height:1.25; }
/* 重新录音：窄款（约缩40%）居中 */
.qk-rec-actions .lp-primary-btn.narrow40 { flex:0 0 60%; max-width:60%; margin:0 auto; padding:16rpx 0; }
.qk-rec-actions .lp-primary-btn.narrow40 .qra-main { font-size:30rpx; }
/* 生成会议纪要：与"结束录音并上传"同尺寸的小胶囊，并上移 16rpx(8px) */
.qk-rec-actions .lp-primary-btn.gen-minutes { flex:0 0 auto; width:fit-content; margin:-16rpx auto 0; padding:12rpx 36rpx; }
.gen-minutes .qra-main { font-size:26rpx; }
/* 常驻「生成会议纪要」：橙色小胶囊，居中，与录音卡内其它按钮呼应 */
.gen-standalone { width:60%; max-width:100%; margin:30rpx auto 0; padding:16rpx 0; display:flex; align-items:center; justify-content:center; }
.gen-standalone .qra-main { font-size:30rpx; font-weight:700; white-space:nowrap; }
/* 上传本地录音文件：低调次级入口(虚线框)，与现场录音圈区分；支持一次多选 */
.qk-import-row { display:flex; justify-content:center; margin-top:16rpx; }
/* 样式A 橙色描边胶囊：白底+橙边+橙字，与录音卡其它按钮同一橙色系 */
.qk-import-btn { display:inline-flex; align-items:center; gap:10rpx; background:#fff; border:3rpx solid var(--c-primary-dark, #E8890C); color:#C76A00; font-size:30rpx; font-weight:600; border-radius:999rpx; padding:20rpx 48rpx; }
.qk-import-btn:active { background:#FFF6E8; }
.qk-import-ico { width:34rpx; height:34rpx; color:var(--c-primary-dark, #E8890C); flex-shrink:0; }

/* ============ 重做：阶段条 / 录音卡 / 单一主按钮 / 合并名单 / 签到动画 ============ */
/* 阶段条 */
/* 顶栏右上「首页」按钮（插槽内容走本页作用域） */
/* 顶栏左上返回箭头（自定义 #left 插槽，走本页作用域） */
.mlq-back { width:96rpx; height:64rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:52rpx; font-weight:700; line-height:1; }
.mlq-back:active { opacity:0.7; }
.nav-home { display:inline-flex; align-items:center; height:64rpx; margin-right:20rpx; padding:0 24rpx; border:2rpx solid rgba(255,255,255,0.6); border-radius:34rpx; background:rgba(255,255,255,0.12); color:#fff; font-size:30rpx; font-weight:600; line-height:1; }
.nav-home:active { background:rgba(255,255,255,0.28); }
/* 流程条：与顶栏拉开距离；左右留白让首尾圆点不贴边；底部留白容纳绝对定位的文字 */
.lp-flow { display:flex; align-items:center; padding:14rpx 48rpx 42rpx; margin-top:36rpx; }
/* 步骤只由圆点决定宽度(文字绝对定位不撑宽)，三个步骤等宽 → 圆点等距对称 */
.lp-flow-step { position:relative; flex-shrink:0; }
.lp-flow-dot { width:58rpx; height:58rpx; border-radius:50%; background:#E4E6EA; color:#9AA0A6; font-size:30rpx; font-weight:700; display:flex; align-items:center; justify-content:center; transition:all .2s; }
/* 文字绝对定位在圆点正下方居中，不影响圆点水平位置 */
.lp-flow-label { position:absolute; top:calc(100% + 7rpx); left:50%; transform:translateX(-50%); font-size:23rpx; color:#9AA0A6; white-space:nowrap; }
.lp-flow-step.on .lp-flow-dot { background:var(--c-primary-dark, #A85800); color:#fff; box-shadow:0 0 0 5rpx rgba(168,88,0,0.18); }
.lp-flow-step.on .lp-flow-label { color:var(--c-primary-dark, #A85800); font-weight:600; }
.lp-flow-step.done .lp-flow-dot { background:#2E8B57; color:#fff; }
.lp-flow-step.done .lp-flow-label { color:#2E8B57; }
/* 未到步骤统一浅灰（原橙描边「可达」态会和当前步抢焦点，去掉）——只剩 完成绿 / 当前棕 / 未到灰 三态 */
.lp-flow-step.available .lp-flow-dot { background:#E4E6EA; color:#9AA0A6; border:0; }
.lp-flow-step.available .lp-flow-label { color:#9AA0A6; font-weight:400; }
/* 连线与圆点同在 align-items:center 下自然居中(步骤已只有圆点高，无需再补 margin) */
.lp-flow-line { flex:1; height:4rpx; background:#E4E6EA; margin:0 10rpx; border-radius:2rpx; }
.lp-flow-line.done { background:#2E8B57; }
.lp-flow-line.available { background:#E4E6EA; opacity:1; }  /* 连线统一：走过=绿、没到=灰，去掉橙虚线 */
/* 会议进行 → 议题处理 之间用棕色虚线连接（0722 用户定），不随 done/available 变绿变灰 */
.lp-flow-line.dashed { background:transparent; height:0; border-top:3rpx dashed #A97544; border-radius:0; }
.lp-flow-line.dashed.done, .lp-flow-line.dashed.available { background:transparent; }
.lp-signin .lp-flow { padding:13rpx 43rpx 38rpx; margin-top:42rpx; }
.lp-signin .lp-flow-dot { width:52rpx; height:52rpx; font-size:27rpx; }
.lp-signin .lp-flow-label { top:calc(100% + 6rpx); font-size:22rpx; }
.lp-signin .lp-flow-line { height:3rpx; margin:0 9rpx; }

/* 录音主卡 */
.rec-hero { position:relative; background:#F6F7F9; border-radius:24rpx; padding:20rpx 22rpx 14rpx; margin-bottom:18rpx; box-shadow:0 3rpx 14rpx rgba(0,0,0,0.045); }
.rec-hero-tools { display:flex; justify-content:center; gap:20rpx; margin-top:16rpx; flex-wrap:wrap; }
.rec-tool-btn { display:inline-flex; align-items:center; gap:8rpx; background:#8C3A2A; border:0; color:#fff; font-size:26rpx; font-weight:600; border-radius:999rpx; padding:14rpx 30rpx; box-shadow:0 4rpx 12rpx rgba(140,58,42,0.22); }
.rec-tool-btn:active { background:#753023; }
/* 上传录音：主色橙，比"重新录音"更主动（上传后自动后台转写） */
.rec-tool-btn.go { background:#C76A00; box-shadow:0 4rpx 12rpx rgba(199,106,0,0.24); }
.rec-tool-btn.go:active { background:#A85800; }
.rec-tool-ico { width:30rpx; height:30rpx; color:var(--c-primary-dark, #E8890C); flex-shrink:0; }
/* 已录N段（折叠） */
.rec-list { margin-top:12rpx; border-top:2rpx solid #ECEDEF; padding-top:10rpx; }
.rec-list-head { display:flex; align-items:center; justify-content:space-between; font-size:24rpx; color:#5A6069; padding:4rpx 2rpx; }
.rec-list-toggle { font-size:22rpx; color:var(--c-primary-dark, #E8890C); }
.rec-list-body { margin-top:6rpx; }

/* 下一步：单一主按钮区 */
.rec-action { margin-bottom:24rpx; display:flex; flex-direction:column; align-items:center; gap:14rpx; }
.rec-status { display:flex; align-items:center; justify-content:center; gap:12rpx; width:100%; font-size:28rpx; color:#E8890C; font-weight:600; padding:6rpx 0; }
/* 录音中的切出预警：常驻、醒目但不刺眼（切出瞬间无法当场提示，只能事先讲清） */
.rec-bg-warn { width:fit-content; max-width:100%; text-align:center; font-size:23rpx; line-height:1.4; color:#A65A08; background:#FFF8EC; border:1px solid #F2D9AF; border-radius:10rpx; padding:7rpx 14rpx; box-sizing:border-box; margin:14rpx auto 0; }  /* 移到录音卡上方，居中一条 */
.rec-status.err { color:#C0392B; }
/* 线上参会提示：不参与现场录音 */
.rec-remote-note { width:100%; box-sizing:border-box; text-align:center; font-size:26rpx; line-height:1.5; color:#6B7280; background:#F6F7F9; border:1px solid #E5E7EB; border-radius:12rpx; padding:16rpx 18rpx; margin-top:12rpx; }
.rec-main { width:52% !important; max-width:360rpx; margin:0 auto !important; font-size:26rpx !important; font-weight:700; padding:16rpx 0 !important; box-shadow:0 6rpx 16rpx rgba(232,137,12,0.22); } /* 缩小约40% */
.rec-sub { background:none; border:0; color:#8A8F98; font-size:28rpx; padding:6rpx 20rpx; }
.rec-sub:active { color:#5A6069; }

/* 录音悬浮标签：窄条两行（状态 / 录音人），默认在录音卡右侧空白处；可拖动到任意位置（触摸/鼠标） */
.rec-floating { position:fixed; top:calc(env(safe-area-inset-top) + 330rpx); right:16rpx; z-index:400;
  display:flex; align-items:center; gap:10rpx; max-width:190rpx;
  background:rgba(22,24,28,0.86); color:#fff; padding:12rpx 16rpx; border-radius:16rpx;
  box-shadow:0 6rpx 18rpx rgba(0,0,0,0.26); line-height:1.3; -webkit-backdrop-filter:blur(6rpx); backdrop-filter:blur(6rpx);
  cursor:grab; touch-action:none; user-select:none; -webkit-user-select:none; }
/* 拖动中：抓手光标 + 轻微放大提亮，明确"抓住了" */
.rec-floating.dragging { cursor:grabbing; box-shadow:0 10rpx 26rpx rgba(0,0,0,0.34); transform:scale(1.04); }
.rec-fl-dot { width:14rpx; height:14rpx; border-radius:50%; background:#ff3b30; flex-shrink:0; box-shadow:0 0 0 0 rgba(255,59,48,0.55); animation:recFlPulse 1.3s ease-out infinite; }
.rec-floating.paused { background:rgba(60,50,30,0.9); }
.rec-floating.paused .rec-fl-dot { background:#F5A623; animation:none; }
.rec-fl-txt { display:flex; flex-direction:column; min-width:0; }
.rec-fl-l1 { font-size:22rpx; opacity:0.9; }
.rec-fl-name { font-size:24rpx; font-weight:700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
@keyframes recFlPulse { 0%{ box-shadow:0 0 0 0 rgba(255,59,48,0.55);} 70%{ box-shadow:0 0 0 14rpx rgba(255,59,48,0);} 100%{ box-shadow:0 0 0 0 rgba(255,59,48,0);} }
@media (prefers-reduced-motion: reduce) { .rec-fl-dot { animation:none; } }

/* 签到 → 录音 跳转动画 */
/* 签到页：大签到按钮 + 下方签到情况名单 */
/* ===== 步骤1 签到页：会议卡 + 名单(默认收起) + 底部大钮(拇指区) ===== */
/* 签到步固定为一屏高、不整页滚：名单 flex 占据剩余空间内部滚动、签到按钮吸底，两者都不超视口（覆盖 live-page 的 inline overflow-y:auto） */
.live-page.lp-signin { height:100dvh; min-height:100dvh; overflow:hidden !important; }
.signin-page { flex:1 1 auto; min-height:0; display:flex; flex-direction:column; padding:8rpx 0 0; overflow:hidden; }
.si-scroll-content { flex:1 1 auto; min-height:0; overflow-y:auto; -webkit-overflow-scrolling:touch; display:flex; flex-direction:column; gap:24rpx; padding-bottom:24rpx; }
.si-scroll-content > * { flex-shrink:0; }
/* 顶部精简会议卡 */
.si-meet-card { position:relative; display:block; background:#fff; border-radius:26rpx; padding:40rpx 34rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.si-meet-main { flex:1; min-width:0; }
.si-meet-title { font-size:36rpx; font-weight:700; color:#1F2024; line-height:1.42; letter-spacing:-0.5rpx; }
.si-meet-meta { display:flex; flex-direction:column; gap:18rpx; margin-top:24rpx; padding-right:130rpx; }
.si-meet-row { font-size:31rpx; color:#61656C; line-height:1.55; }
.si-meet-caret { position:absolute; right:34rpx; bottom:43rpx; min-width:116rpx; text-align:right; font-size:28rpx; color:#A85800; font-weight:700; }
.si-meet-topics { background:#fff; border-radius:26rpx; padding:28rpx 34rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); margin-top:-4rpx; min-height:170rpx; box-sizing:border-box; }
.si-topic-item { display:flex; align-items:flex-start; gap:18rpx; padding:24rpx 0; border-bottom:2rpx solid #F4F4F6; }
.si-topic-item:last-child { border-bottom:0; }
.si-topic-idx { flex-shrink:0; width:50rpx; height:50rpx; border-radius:50%; background:#FFF1E0; color:#E8890C; font-size:30rpx; font-weight:700; display:flex; align-items:center; justify-content:center; }
.si-topic-title { flex:1; min-width:0; font-size:30rpx; color:#2B2E33; line-height:1.6; white-space:normal; overflow:visible; word-break:break-word; }
.si-topic-empty { display:block; text-align:center; color:#9AA0A6; font-size:30rpx; padding:34rpx 0; }
/* 参会名单：收起态一条，展开显示逐人 */
.si-roster { background:#fff; border-radius:24rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); padding:0 28rpx; }
.si-roster-bar { display:flex; align-items:center; gap:14rpx; padding:26rpx 0; flex-shrink:0; }
.si-roster-title { font-size:30rpx; font-weight:700; color:#1f2329; }
.si-roster-count { flex:1; font-size:26rpx; color:#6b7078; }
.si-roster-summary { margin-left:12rpx; color:#8A5A2B; font-size:24rpx; font-weight:550; }
.si-roster-caret { flex-shrink:0; color:#7A4C22; font-size:25rpx; font-weight:600; }
.si-quorum { display:flex; align-items:center; gap:12rpx; margin:-4rpx 0 22rpx; padding:16rpx 18rpx; border-radius:16rpx; background:#FFF5E8; color:#B76700; font-size:26rpx; font-weight:600; }
.si-quorum-icon { width:34rpx; height:34rpx; border-radius:50%; display:flex; align-items:center; justify-content:center; background:#D98012; color:#fff; font-size:23rpx; font-weight:800; flex-shrink:0; }
/* 名单展开后跟随整页滚动，避免内部滚动区域的末尾被固定签到按钮遮挡。 */
.si-roster.open { display:flex; flex-direction:column; }
.si-roster-body { max-height:none; overflow:visible; padding-bottom:10rpx; border-top:2rpx solid #F2F2F4; }
/* 底部拇指区：跟随名单下方，避免首屏中段出现大片空白 */
.si-bottom { flex:0 0 auto; z-index:180; display:flex; flex-direction:column; align-items:center; gap:35rpx; margin:0; padding:24rpx 0 env(safe-area-inset-bottom); background:rgba(255,255,255,.38); }
/* 方案B「通栏沉稳大按钮」：深橙实色通栏，无渐变/脉动/投影 */
.signin-big-btn { width:65% !important; height:100rpx; box-sizing:border-box; max-width:none; margin:0 auto !important; background:#315F7D !important; color:#fff !important; font-size:40rpx !important; font-weight:700; letter-spacing:4rpx; padding:0 !important; border-radius:24rpx; box-shadow:0 10rpx 24rpx rgba(49,95,125,.28); }
.signin-big-btn:active { filter:brightness(0.92); }
.signin-remote-btn { width:65%; height:100rpx; box-sizing:border-box; margin:0 auto; padding:0; border:2rpx solid #88A9BF; border-radius:24rpx; background:#EFF5F9; color:#315F7D; font-size:36rpx; font-weight:700; letter-spacing:2rpx; box-shadow:0 5rpx 14rpx rgba(49,95,125,.10); }
.signin-remote-btn:active { background:#E1EDF4; }
.signin-page-tip { font-size:28rpx; color:#8A8F98; }
/* 参会名单 */
.signin-roster { width:88%; max-width:640rpx; margin-top:14rpx; background:#fff; border-radius:20rpx; padding:20rpx 26rpx 8rpx; box-shadow:0 6rpx 20rpx rgba(0,0,0,0.05); box-sizing:border-box; }
.signin-roster-head { display:flex; align-items:center; justify-content:space-between; font-size:28rpx; font-weight:700; color:#1f2329; padding-bottom:12rpx; border-bottom:2rpx solid #F2F2F4; }
.signin-roster-count { font-size:26rpx; color:#8A8F98; font-weight:400; }
.signin-roster-count b { font-size:30rpx; color:#27AE60; font-weight:800; }
.signin-roster-row { display:flex; align-items:center; justify-content:space-between; gap:18rpx; padding:16rpx 0; border-bottom:2rpx solid #F6F6F8; }
.signin-roster-row:last-child { border-bottom:0; }
.srr-name { font-size:28rpx; color:#1f2329; font-weight:600; flex-shrink:0; }
.srr-state { font-size:24rpx; font-weight:600; padding:4rpx 16rpx; border-radius:12rpx; flex-shrink:0; }
.srr-state.on { color:#2E8B57; background:#E8F7EE; }
.srr-state.remote { color:#2676D9; background:#EAF2FF; }
.srr-state.off { color:#C0392B; background:#FDECEA; }
.srr-state.wait { color:#6b7078; background:#EDEEF0; }
/* 签到成功 → 录音 的一闪而过动画（时长收短，别停留） */
.signin-fx { position:fixed; inset:0; z-index:1200; background:rgba(255,255,255,0.94); display:flex; align-items:center; justify-content:center; animation:sfxFade .14s ease; }
.signin-fx-card { display:flex; flex-direction:column; align-items:center; gap:18rpx; animation:sfxRise .24s cubic-bezier(.2,.8,.3,1); }
.signin-fx-check { width:150rpx; height:150rpx; border-radius:50%; background:#3E9B34; color:#fff; font-size:92rpx; font-weight:700; display:flex; align-items:center; justify-content:center; animation:sfxPop .3s cubic-bezier(.2,1.3,.4,1); box-shadow:0 12rpx 36rpx rgba(62,155,52,0.35); }
.signin-fx-title { font-size:40rpx; font-weight:700; color:#1a1a1a; }
.signin-fx-sub { font-size:28rpx; color:#8A8F98; }
@keyframes sfxFade { from { opacity:0; } to { opacity:1; } }
@keyframes sfxRise { from { opacity:0; transform:translateY(20rpx); } to { opacity:1; transform:translateY(0); } }
@keyframes sfxPop { 0% { transform:scale(0.3); opacity:0; } 60% { transform:scale(1.12); } 100% { transform:scale(1); opacity:1; } }
/* 识别完成后的两键：继续上传录音(浅) / 生成会议纪要(深)——缩小、拉开间距 */
/* 上下堆叠、居中、宽度 60%：主(生成纪要)实心在上，次(继续上传)描边在下 */
.qk-two-btns { display:flex; flex-direction:column; align-items:center; gap:14rpx; margin-top:14rpx; padding:0; }
.qk-two-btns .lp-primary-btn.qk-two-btn { width:60%; flex:none; margin-top:0; padding:20rpx 0; display:flex; align-items:center; justify-content:center; }
.qk-two-btn .qra-main { font-size:28rpx; font-weight:700; white-space:nowrap; }
.qk-two-btns .lp-primary-btn.qk-two-btn:not(.ghost) .qra-main { font-size:30rpx; }
.qk-two-btn.ghost { background:#fff; color:var(--c-primary-dark); border:2rpx solid var(--c-primary-dark); }
.qk-two-btn.ghost:active { background:#FFF6E8; }
.qra-main { font-size:34rpx; font-weight:700; }
.qra-sub { font-size:24rpx; font-weight:400; opacity:0.92; }
/* 选择已有录音文件上传：窄一点、居中 */
.qk-rec-narrow { width:auto; max-width:fit-content; margin-left:auto; margin-right:auto; padding-left:44rpx; padding-right:44rpx; }
.qk-divider { display:flex; align-items:center; gap:18rpx; margin:24rpx 0 16rpx; }
.qk-divider::before, .qk-divider::after { content:''; flex:1; height:2rpx; background:#E5E8EC; }
.qk-divider-text { font-size:28rpx; color:#666; }
.lp-empty { color:#666; font-size:28rpx; text-align:center; padding:28rpx 0; }
/* 转写弹层里"结果丢失→当场找回"的重新识别按钮 */
.qk-retry-asr { display:block; margin:24rpx auto 0; height:68rpx; padding:0 44rpx; border:none; border-radius:34rpx;
  background:#0F766E; color:#fff; font-size:26rpx; font-weight:600; }
.qk-retry-asr:disabled { opacity:0.5; }

/* 生成中 */
.qk-gen { display:flex; flex-direction:column; align-items:center; gap:20rpx; padding:36rpx 0; }
.qk-gen-icon { width:100rpx; height:100rpx; border-radius:50%; background:#27AE60; color:#fff; display:flex; align-items:center; justify-content:center; font-size:52rpx; }
.qk-gen-text { font-size:30rpx; font-weight:600; color:#333; }
.qk-spinner { width:80rpx; height:80rpx; border-radius:50%; border:8rpx solid #FFE2A8; border-top-color:#FFA800; animation:qkspin .8s linear infinite; }
@keyframes qkspin { to { transform:rotate(360deg); } }

/* 议题确认 */
.qk-grp-title { font-size:28rpx; font-weight:700; color:#555; margin:24rpx 0 14rpx; }
.qk-topic { display:flex; align-items:center; gap:18rpx; border:2rpx solid #eef0f3; border-radius:18rpx; padding:22rpx; margin-bottom:14rpx; }
.qk-topic-main { flex:1; }
.qk-topic-title-row { display:flex; align-items:flex-start; justify-content:space-between; gap:14rpx; }
.qk-topic-title { font-size:30rpx; color:#333; font-weight:600; line-height:1.45; flex:1; }
.qk-rename-link { flex-shrink:0; font-size:28rpx; color:#C77800; font-weight:600; padding:4rpx 8rpx; }
.qk-tag-new { font-size: 28rpx; color:#fff; background:#FFA800; border-radius:8rpx; padding:2rpx 12rpx; margin-right:10rpx; }
.qk-tag-type { font-size: 28rpx; color:#4D6B8A; background:#EDF5FB; border-radius:8rpx; padding:2rpx 12rpx; margin-right:10rpx; vertical-align:middle; }
.qk-match-line { display:block; font-size: 28rpx; color:#6B7A90; margin-top:10rpx; line-height:1.45; }
.qk-match-score { display:block; font-size: 28rpx; color:#9A6A1B; margin-top:8rpx; line-height:1.45; }
.qk-sug { display:block; font-size:28rpx; color:#666; margin-top:10rpx; }
.qk-report-actions { display:flex; flex-wrap:wrap; gap:14rpx; justify-content:flex-start; margin-top:16rpx; }
.qk-report-btn { display:inline-block; color:#C77800; background:#FFF3DC; border-radius:12rpx; padding:14rpx 22rpx; font-size:28rpx; font-weight:600; }
.qk-report-btn.disabled { opacity:0.55; }
.qk-report-btn.primary { background:var(--c-primary-dark); color:#fff; }
.qk-report-edit { margin-top:16rpx; border:2rpx solid #FFE2A8; border-radius:14rpx; background:#fff; padding:18rpx 20rpx; }
.qk-report-textarea { width:100%; min-height:220rpx; box-sizing:border-box; font-size:28rpx; color:#374151; line-height:1.65; }
.qk-ph { color:#9aa4b2; }
.qk-report-edit-actions { display:flex; gap:14rpx; justify-content:flex-end; margin-top:14rpx; }
.qk-report { margin-top:16rpx; border:2rpx solid #E6EAF0; border-radius:14rpx; background:#FAFBFC; padding:18rpx 20rpx; }
.qk-report-title { display:block; font-size:28rpx; color:#1F2937; font-weight:700; margin-bottom:12rpx; }
.qk-report-body { display:block; font-size:28rpx; color:#374151; line-height:1.65; white-space:pre-line; word-break:break-word; }
.qk-fields { display:flex; flex-direction:column; gap:8rpx; margin-top:14rpx; background:#F4F9F4; border-radius:14rpx; padding:16rpx 18rpx; }
.qk-field { display:flex; align-items:flex-start; gap:14rpx; font-size:28rpx; line-height:1.5; }
.qk-field-name { flex:0 0 auto; min-width:110rpx; color:#3F7A4B; font-weight:600; }
.qk-field-val { flex:1; color:#1F2937; word-break:break-all; }
.qk-fields-hint { font-size: 28rpx; color:#8A9A8C; margin-top:6rpx; }
.qk-record-hint { display:block; font-size:28rpx; color:#6B7280; line-height:1.45; }
.qk-record-row { display:flex; align-items:center; justify-content:space-between; gap:18rpx; margin-top:14rpx; }
.qk-mini-action.disabled { opacity:0.5; }
.qk-vote-ai { display:block; font-size: 28rpx; color:#B8860B; margin-top:8rpx; line-height:1.45; }

/* 议题列表行 */
.qk-row { display:flex; align-items:center; gap:18rpx; background:#fff; border:2rpx solid #EEF0F2; border-radius:18rpx; padding:24rpx; margin-top:16rpx; }
.qk-row.done { border-color:#C7E3CD; background:#F6FBF7; }
.qk-row.candidate { border-style:dashed; border-color:#C9B07A; background:#FFFDF6; }
.qk-row-no { flex:0 0 auto; width:44rpx; height:44rpx; line-height:44rpx; text-align:center; font-size:28rpx; color:#6B7280; background:#F0F1F3; border-radius:50%; }
.qk-row-no.new { color:#fff; background:#FFA800; }
.qk-row-main { flex:1; min-width:0; }
.qk-row-title { display:block; font-size:32rpx; color:#1F2937; line-height:1.4; }
.qk-row-meta { display:flex; flex-wrap:wrap; align-items:center; gap:12rpx; margin-top:8rpx; }
.qk-row-sub { font-size: 28rpx; color:#666; }
.qk-row-status { flex:0 0 auto; font-size:28rpx; color:#B0863B; }
.qk-row-status.on { color:#3F7A4B; }
.qk-row-arrow { flex:0 0 auto; font-size:40rpx; color:#C2C7CC; }

/* 议题详情全屏面板 */
.qk-detail-sheet { position:absolute; left:0; right:0; bottom:0; top:10%; height:90vh; max-height:90vh; background:#F5F6F8; border-radius:24rpx 24rpx 0 0; display:flex; flex-direction:column; overflow:hidden; box-sizing:border-box; }
.qk-detail-scroll { flex:1; height:calc(90vh - 96rpx); min-height:0; padding:24rpx 28rpx 180rpx; box-sizing:border-box; }
.qk-detail-body { background:#fff; border-radius:18rpx; padding:24rpx; }
.qk-candidate { border:2rpx dashed #C9B07A; background:#FFFDF6; border-radius:18rpx; padding:22rpx; margin-top:18rpx; }
.qk-candidate-actions { display:flex; gap:18rpx; margin-top:18rpx; }
.qk-cand-btn { flex:1; text-align:center; font-size:28rpx; padding:18rpx 0; border-radius:14rpx; background:#F0F1F3; color:#4B5563; }
.qk-cand-btn.primary { background:var(--c-primary-dark); color:#fff; }
.qk-evidence { display:flex; flex-direction:column; gap:12rpx; margin-top:16rpx; }
.qk-evidence-head { display:flex; justify-content:space-between; align-items:center; gap:14rpx; color:#6B7A90; font-size: 28rpx; line-height:1.4; }
.qk-evidence-toggle { color:#C77800; font-weight:600; flex-shrink:0; }
.qk-evidence-item { background:#fff; border:2rpx solid #EEF0F3; border-radius:14rpx; padding:16rpx 18rpx; }
.qk-evidence-meta { display:flex; justify-content:space-between; gap:14rpx; color:#8A93A3; font-size: 28rpx; margin-bottom:8rpx; line-height:1.35; }
.qk-evidence-text { display:block; color:#333; font-size:28rpx; line-height:1.55; word-break:break-all; }
.qk-evidence-reason { display:block; color:#6B7A90; font-size: 28rpx; margin-top:8rpx; line-height:1.4; }
.qk-evidence-more { display:block; color:#666; font-size: 28rpx; text-align:center; padding:4rpx 0; }
.qk-mini-action { display:inline-block; color:#6B7A90; font-size: 28rpx; margin-top:10rpx; padding:6rpx 0; }
.qk-mini-action.primary { color:#C77800; font-weight:600; }
.qk-result-row { display:flex; gap:12rpx; flex-wrap:wrap; margin-top:16rpx; }
.qk-result { font-size:28rpx; color:#777; background:#F3F5F7; border:2rpx solid #E4E8EC; border-radius:16rpx; padding:8rpx 18rpx; }
.qk-result.on { color:#C77800; background:#FFF6E5; border-color:#FFD98A; font-weight:700; }
.qk-vote-box { margin-top:16rpx; background:#FFFDF8; border:2rpx solid #F1E2C4; border-radius:14rpx; padding:18rpx 20rpx; }
.qk-vote-rule { display:block; color:#805A18; font-size: 28rpx; line-height:1.45; margin-bottom:14rpx; }
.qk-vote-counts { display:grid; grid-template-columns:repeat(3, 1fr); gap:12rpx; }
.qk-vote-counter { background:#fff; border:2rpx solid #EFE5D2; border-radius:14rpx; padding:14rpx; text-align:center; }
.qk-vote-counter > span { display:block; font-size: 28rpx; color:#777; margin-bottom:10rpx; }
.qk-vote-counter > div { display:flex; align-items:center; justify-content:space-between; gap:8rpx; }
.qk-vote-counter > div span { min-width:52rpx; height:52rpx; line-height:52rpx; border-radius:50%; font-size:30rpx; color:#333; background:#F6F6F8; }
.qk-vote-counter > div span:nth-child(2) { background:transparent; font-weight:700; color:#1F2024; }
.qk-confirm { display:block; text-align:center; margin-top:20rpx; flex-shrink:0; font-size:30rpx; font-weight:700; padding:20rpx 24rpx; border-radius:36rpx; border:2rpx solid #FFA800; color:#FFA800; background:#fff; }
.qk-confirm.on { background:#EAFAF1; border-color:#27AE60; color:#27AE60; }

/* 底部导航 */
.lp-nav { display:flex; gap:24rpx; margin-top:8rpx; justify-content:center; }
.lp-nav-btn { border-radius:44rpx; font-size:30rpx; font-weight:600; padding:22rpx 44rpx; border:0; }
.lp-nav-btn.ghost { background:#fff; color:#555; border:2rpx solid #dfe3e8; }

/* 录音负责人 */
.qk-recorder-role { background:#FAFBFC; border-radius:18rpx; padding:22rpx 26rpx; margin:12rpx 0 18rpx; }
.qk-rr-status { display:block; font-size:28rpx; color:#6B6E76; margin-bottom:14rpx; }
.qk-rr-status.ok { color:#1D9E75; font-weight:600; }

/* 录音卡里的「已录制」文件列表 */
/* 已有录音：独立卡片沉底（.lp-card 已提供底/留白） */
.qk-rec-list { padding-top:24rpx; padding-bottom:20rpx; }
.qk-rec-list-head { font-size:28rpx; color:#888; margin-bottom:12rpx; }
.qk-rec-list-item { display:flex; align-items:center; gap:16rpx; padding:16rpx 0; border-bottom:2rpx solid #F6F6F8; }
.qk-rec-list-item:last-child { border-bottom:0; }
.qrl-idx { width:44rpx; height:44rpx; flex-shrink:0; border-radius:50%; background:#FFF1E0; color:var(--c-primary-dark); font-size:28rpx; font-weight:700; text-align:center; line-height:44rpx; }
.qrl-info { flex:1; min-width:0; display:flex; flex-direction:column; gap:4rpx; }
.rec-summary-row { padding:14rpx 0; }
.rec-summary-name { flex:1; min-width:0; }
.rec-summary-duration { color:#667085; font-size:26rpx; font-variant-numeric:tabular-nums; }
.rec-summary-more { flex-shrink:0; color:#8A8F98; font-size:23rpx; cursor:pointer; padding:6rpx 4rpx; }
/* 删除：红色小字，与「详情」间隔开、并加内边距扩大热区避免误点 */
.rec-summary-del { flex-shrink:0; color:#D0392E; font-size:23rpx; margin-left:26rpx; padding:6rpx 6rpx; }
.rec-summary-del:active { opacity:0.6; }
.recording-detail-card { width:88%; max-height:82vh; overflow-y:auto; box-sizing:border-box; background:#fff; border-radius:24rpx; padding:30rpx; }
.recording-detail-card .qk-modal-title { margin-bottom:0; }
.recording-detail-grid { display:flex; flex-direction:column; gap:0; border:2rpx solid #ECEFF3; border-radius:16rpx; overflow:hidden; }
.recording-detail-grid > div { display:flex; align-items:flex-start; justify-content:space-between; gap:24rpx; padding:22rpx; border-bottom:2rpx solid #ECEFF3; }
.recording-detail-grid > div:last-child { border-bottom:0; }
.recording-detail-grid span { flex-shrink:0; color:#8A8F98; font-size:25rpx; }
.recording-detail-grid b { color:#1F2024; font-size:26rpx; text-align:right; line-height:1.45; }
.recording-detail-actions { display:flex; flex-direction:column; align-items:center; gap:18rpx; margin-top:28rpx; }
.recording-detail-actions .supp-btn { width:82%; }
.detail-secondary { background:#F1F4F7 !important; color:#344054 !important; }
.qrl-name { font-size:32rpx; color:#1F2024; font-weight:600; }
.qrl-meta { font-size:26rpx; color:#999; }
/* 查看内容：左对齐到正文左缘，主色链接样式，点击打开整场转写弹窗 */
.qrl-view { align-self:flex-start; margin-top:6rpx; font-size:26rpx; color:var(--c-primary-dark, #E8890C); font-weight:600; }
.qrl-play { flex-shrink:0; width:56rpx; height:56rpx; border-radius:50%; background:#FFF1E0; color:var(--c-primary-dark); font-size:30rpx; text-align:center; line-height:56rpx; }
.qrl-play.on { background:var(--c-primary-dark); color:#fff; }
.qrl-del { flex-shrink:0; font-size:26rpx; color:#C0392B; padding:6rpx 10rpx; }
.qk-link { font-size:28rpx; color:#FFA800; font-weight:600; }

/* 会议资料 / 佐证列表 */
.qk-mat-item { display:flex; align-items:center; gap:14rpx; padding:20rpx 22rpx; background:#FAFBFC; border-radius:14rpx; margin-bottom:12rpx; }
.qk-mat-name { flex:1; font-size:28rpx; color:#1F2024; min-width:0; word-break:break-all; }
.qk-mat-meta { font-size: 28rpx; color:#666; flex-shrink:0; }
.qk-mat-arrow { color:#666; font-size:32rpx; }
.qk-mat-del { color:#666; font-size:38rpx; padding:0 8rpx; }

/* 居委会签字 */
.qk-juwei { display:flex; align-items:center; justify-content:space-between; gap:18rpx; background:#EBF5FB; border-radius:14rpx; padding:20rpx 26rpx; margin:18rpx 0; }
.qk-juwei-name { font-size:28rpx; color:#355C7D; }
.qk-juwei-chip { font-size:28rpx; color:#C77800; background:#fff; border:2rpx solid #F4D08A; border-radius:24rpx; padding:8rpx 24rpx; }
.qk-juwei-chip.on { color:#1D9E75; background:#E1F5EE; border-color:#E1F5EE; }

/* 等待主任拍板 */
.qk-wait-tip { text-align:center; font-size:28rpx; color:#666; background:#FAFBFC; border-radius:18rpx; padding:26rpx; margin-top:12rpx; line-height:1.6; }

/* 实时添加议题弹窗 */
.qk-modal-mask { position:fixed; inset:0; background:rgba(0,0,0,0.45); display:flex; align-items:center; justify-content:center; z-index:200; } /* 高于会后整理页(180)：查看转写等弹窗在整理页也能弹 */
.qk-modal { width:88%; max-height:84vh; overflow-y:auto; box-sizing:border-box; background:#fff; border-radius:24rpx; padding:54rpx 44rpx 48rpx; }
.qk-modal-title { display:block; font-size:34rpx; font-weight:700; color:#1F2024; margin-bottom:46rpx; }
.qk-modal-input { box-sizing:border-box; width:100%; height:88rpx; line-height:88rpx; background:#F6F6F8; border-radius:14rpx; padding:0 20rpx; font-size:30rpx; margin-bottom:18rpx; border:0; }
.qk-modal-textarea { height:auto; min-height:150rpx; line-height:1.6; padding:16rpx 20rpx; resize:none; font-family:inherit; }
.qk-modal-types { display:flex; gap:18rpx; margin-bottom:46rpx; }
.qk-type { font-size:28rpx; padding:12rpx 26rpx; border-radius:24rpx; background:#F6F6F8; color:#6B6E76; border:2rpx solid #ECECEF; }
.qk-type.notice.on { background:#E6F4FB; color:#1677B8; border-color:#78B9DC; }
.qk-type.discussion.on { background:#EAF6EE; color:#2E8B57; border-color:#82BE97; }
.qk-type.decision.on, .qk-type.on:not(.notice):not(.discussion) { background:#FFF0E5; color:#D56A16; border-color:#E6A370; }
.qk-modal-label { display:block; font-size:26rpx; color:#777; font-weight:600; margin-bottom:26rpx; }
.qk-opt-row { display:flex; align-items:center; gap:14rpx; margin-bottom:12rpx; }
.qk-opt-num { font-size:28rpx; color:#666; width:40rpx; text-align:right; flex-shrink:0; }
.qk-opt-input { flex:1; min-width:0; height:76rpx; line-height:76rpx; margin-bottom:0; }
.qk-opt-del { font-size:38rpx; color:#999; padding:0 8rpx; flex-shrink:0; }
.qk-modal-area { width:100%; box-sizing:border-box; background:#F6F6F8; border-radius:14rpx; padding:18rpx 20rpx; font-size:28rpx; height:160rpx; margin-bottom:18rpx; border:0; }
.qk-modal-btns { display:flex; gap:30rpx; margin-top:22rpx; }
/* 添加/取消 较原生尺寸缩小约30%（高约96→68rpx） */
.qk-modal-btns .lp-ghost-btn, .qk-modal-btns .lp-primary-btn { flex:1; margin:0; padding:15rpx 0; font-size:26rpx; border-radius:34rpx; }
/* 取消：改中性灰底（原橙字描边不清晰、显廉价），与右侧「添加」主按钮对比更明确 */
.qk-modal-btns .lp-ghost-btn.qk-cancel-btn { background:#F0F1F3; color:#5A6069; border:2rpx solid #E2E4E8; font-weight:600; }
.qk-modal-btns .lp-ghost-btn.qk-cancel-btn:active { background:#E5E7EA; }
/* 议题内容 + 语音输入按钮 同行 */
.qk-input-row { display:flex; align-items:center; gap:14rpx; margin-bottom:46rpx; }
.qk-input-row .qk-modal-input { flex:1; min-width:0; margin-bottom:0; }
.voice-input-btn { flex-shrink:0; display:inline-flex; align-items:center; gap:6rpx; border:2rpx solid #FFD79A; background:#FFF6E6; color:#C76A00; font-size:26rpx; font-weight:600; padding:12rpx 18rpx; border-radius:999rpx; line-height:1.3; }
.voice-input-btn.on { background:#FFA800; border-color:#FFA800; color:#fff; }
/* 语音输入弹层（与创建页同款） */
.voice-modal-mask { position:fixed; inset:0; z-index:300; background:rgba(0,0,0,0.5); display:flex; align-items:flex-end; }
.voice-modal { width:100%; background:#fff; border-radius:32rpx 32rpx 0 0; padding:32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); display:flex; flex-direction:column; gap:24rpx; }
.vm-body { background:#f7f8fa; border-radius:20rpx; padding:24rpx 20rpx; min-height:160rpx; display:flex; flex-direction:column; align-items:center; gap:18rpx; }
.vm-wave { display:flex; align-items:flex-end; gap:8rpx; height:48rpx; }
.vm-wave span { width:8rpx; border-radius:4rpx; background:#0051FF; animation:vm-bar 1.1s ease-in-out infinite; }
.vm-wave span:nth-child(1) { height:20rpx; animation-delay:0s; }
.vm-wave span:nth-child(2) { height:36rpx; animation-delay:0.15s; }
.vm-wave span:nth-child(3) { height:48rpx; animation-delay:0.3s; }
.vm-wave span:nth-child(4) { height:36rpx; animation-delay:0.45s; }
.vm-wave span:nth-child(5) { height:20rpx; animation-delay:0.6s; }
@keyframes vm-bar { 0%,100% { transform:scaleY(0.4); opacity:0.6; } 50% { transform:scaleY(1); opacity:1; } }
.vm-text { font-size:32rpx; color:#1f2329; line-height:1.6; text-align:center; width:100%; word-break:break-all; }
.vm-interim { color:#888; }
.vm-placeholder { color:#aaa; }
.vm-actions { display:flex; gap:20rpx; }
.vm-actions .btn { flex:1; height:88rpx; font-size:34rpx; border-radius:18rpx; }

/* 第3步：选片列表 */
/* 本次会议录音分组：把多段框成一个整体 */
.qk-seg-group { border:2rpx solid #FFE2B0; border-radius:18rpx; padding:18rpx; background:#FFFCF6; margin-bottom:8rpx; }
.qk-seg-group-head { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:14rpx; }
.qk-seg-group-title { font-size:30rpx; font-weight:700; color:#1F2024; }
.qk-seg-group-sub { font-size:26rpx; color:#D88900; font-weight:600; }
.qk-pick-list { display:flex; flex-direction:column; gap:14rpx; margin-bottom:8rpx; }
.qk-pick-item { display:flex; align-items:center; gap:16rpx; background:#FAFBFC; border:2rpx solid #EEF1F4; border-radius:16rpx; padding:22rpx; }
.qk-pick-item.transcribing { border-color:#FFD98A; background:#FFFDF6; }
.qk-pick-item.picked { border-color:#FFD98A; background:#FFFDF6; }
.qk-pick-check { width:46rpx; height:46rpx; border-radius:12rpx; border:3rpx solid #C9CFD6; background:#fff; display:flex; align-items:center; justify-content:center; font-size:32rpx; font-weight:700; color:#fff; flex-shrink:0; }
.qk-pick-check.on { background:#FFA800; border-color:#FFA800; }
.qk-pick-hint { display:block; font-size:26rpx; color:#8A9099; line-height:1.55; margin:16rpx 0 14rpx; }
.qk-back-rec { display:block; text-align:center; font-size:28rpx; color:#D88900; padding:18rpx 0 4rpx; }
.qk-pick-info { flex:1; min-width:0; }
.qk-pick-name { display:block; font-size:30rpx; color:#1f2329; word-break:break-all; }
.qk-pick-meta { display:block; font-size: 28rpx; color:#666; margin-top:6rpx; }
.qk-pick-done { font-size:28rpx; color:#27AE60; font-weight:600; flex-shrink:0; }
.qk-pick-play { flex-shrink:0; width:60rpx; height:60rpx; box-sizing:border-box; display:flex; align-items:center; justify-content:center; font-size:28rpx; color:#0051FF; border:2rpx solid #BCD0FF; background:#EEF3FF; border-radius:50%; }
.qk-pick-play.on { color:#fff; background:#0051FF; border-color:#0051FF; }
.qk-pick-del { flex-shrink:0; font-size:26rpx; color:#E74C3C; border:2rpx solid #F3C2BD; background:#FDECEA; border-radius:999rpx; padding:8rpx 20rpx; line-height:1.3; }
.qk-pick-ing { font-size:28rpx; color:#E67E22; flex-shrink:0; }
.qk-pick-item .lp-ghost-btn { width:auto; flex-shrink:0; margin:0; padding:14rpx 32rpx; }

/* ── 重做后的「最后一步」：AI 纪要审核 ── */
.qk-rev { border:2rpx solid #EEF1F4; border-radius:16rpx; padding:20rpx 22rpx; margin-bottom:16rpx; background:#fff; }
.qk-rev.warn { border-color:#FFB020; background:#FFF8EC; }
.qk-rev-head { display:flex; align-items:flex-start; gap:14rpx; }
.qk-rev-no { width:44rpx; height:44rpx; border-radius:50%; background:#FFF0D6; color:#C77700; font-size:28rpx; font-weight:700; text-align:center; line-height:44rpx; flex-shrink:0; }
.qk-rev-main { flex:1; min-width:0; }
.qk-rev-title { font-size:32rpx; color:#1f2329; font-weight:600; display:block; }
.qk-rev-meta { display:flex; flex-wrap:wrap; align-items:center; gap:12rpx; margin-top:10rpx; }
.qk-rev-result { font-size:28rpx; font-weight:700; padding:2rpx 14rpx; border-radius:8rpx; }
.qk-rev-result.passed { color:#1B8A3A; background:#E6F6EA; }
.qk-rev-result.rejected { color:#C0341D; background:#FBE9E6; }
.qk-rev-result.unclear { color:#C77700; background:#FFF0D6; }
.qk-rev-result.recorded { color:#5a6573; background:#eef0f2; }
.qk-rev-counts { font-size:28rpx; color:#8a94a0; }
.qk-rev-btn { color:#C77700; font-size:28rpx; font-weight:600; padding:6rpx 22rpx; border:2rpx solid #F0C277; border-radius:32rpx; flex-shrink:0; }
.qk-rev-tip { display:block; margin-top:12rpx; color:#C77700; font-size:28rpx; }
.qk-rev-box { margin-top:18rpx; padding-top:18rpx; border-top:2rpx dashed #ead9b6; }
.qk-vote-rule { display:block; font-size: 28rpx; color:#666; margin:8rpx 0 14rpx; }
.qk-result-pick { display:flex; gap:14rpx; margin-top:16rpx; }
.qk-result-pick span { flex:1; text-align:center; padding:18rpx 0; border-radius:12rpx; border:2rpx solid #dfe3e8; color:#5a6573; font-size:28rpx; }
.qk-result-pick span.on { background:#FFA800; color:#fff; border-color:#FFA800; font-weight:700; }

/* AI 额外发现（折叠） */
.qk-extra { margin:4rpx 0 12rpx; border:2rpx solid #EEF1F4; border-radius:16rpx; overflow:hidden; }
.qk-extra-head { display:flex; justify-content:space-between; align-items:center; padding:22rpx; background:#FAFBFC; color:#5a6573; font-size:28rpx; }
.qk-extra-toggle { color:#C77700; flex-shrink:0; }
.qk-extra-list { padding:4rpx 22rpx 16rpx; }
.qk-extra-item { display:flex; justify-content:space-between; align-items:center; gap:14rpx; padding:18rpx 0; border-top:2rpx solid #F0F2F4; }
.qk-extra-title { flex:1; min-width:0; color:#1f2329; font-size:28rpx; }
.qk-extra-acts { display:flex; gap:12rpx; flex-shrink:0; }
.qk-extra-acts .qk-cand-btn { flex:0 0 auto; padding:10rpx 28rpx; }

/* AI 纪要草稿展示 */
.qk-minutes-draft { max-height:560rpx; background:#fff; border:2rpx solid #EEF1F4; border-radius:16rpx; padding:24rpx; margin:16rpx 0; }
.qk-minutes-text { font-size:28rpx; color:#1f2329; line-height:1.75; white-space:pre-wrap; word-break:break-word; }
</style>
