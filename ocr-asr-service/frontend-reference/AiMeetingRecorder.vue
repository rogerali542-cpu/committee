<template>
  <div class="mx-auto flex h-[calc(100vh-7rem)] max-w-7xl flex-col overflow-hidden">
    <div
      v-if="processingStarted"
      data-testid="meeting-processing-view"
      class="flex min-h-0 flex-1 flex-col"
    >
      <div class="mb-4 flex items-center justify-between gap-3">
        <div>
          <h1 class="text-2xl font-semibold text-gray-950">会议处理中</h1>
          <p class="mt-1 text-sm text-gray-500">麦克风采集已停止, 正在保存录音并等待转写收尾</p>
        </div>
        <div class="flex items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2 text-xs text-gray-600">
          <span class="h-2 w-2 rounded-full bg-cyan-500"></span>
          {{ statusLabel }} · {{ elapsedLabel }}
        </div>
      </div>

      <div class="grid min-h-0 flex-1 gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <section class="flex min-h-0 flex-col rounded-lg border border-gray-200 bg-white">
          <div class="border-b border-gray-100 px-4 py-3">
            <h2 class="text-sm font-semibold text-gray-950">处理进度</h2>
            <p class="mt-0.5 text-xs text-gray-500">会后工作台准备好后会自动跳转</p>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
            <div class="space-y-3">
              <div
                v-for="(item, index) in processingLogs"
                :key="`${index}-${item}`"
                class="flex gap-3 rounded-md border border-gray-100 bg-gray-50 px-3 py-2 text-sm text-gray-700"
              >
                <span class="mt-1 h-2 w-2 shrink-0 rounded-full bg-cyan-600"></span>
                <span>{{ item }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="flex min-h-0 flex-col rounded-lg border border-gray-200 bg-white">
          <div class="border-b border-gray-100 px-4 py-3">
            <h2 class="text-sm font-semibold text-gray-950">实时转写收尾</h2>
            <p class="mt-0.5 text-xs text-gray-500">最后一段文本会继续流式补齐</p>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto px-4 py-3">
            <p v-if="liveTranscript" class="whitespace-pre-wrap text-sm leading-7 text-gray-800">{{ liveTranscript }}</p>
            <p v-else class="text-sm text-gray-500">等待服务端返回最终转写</p>
          </div>
        </section>
      </div>
    </div>

    <template v-else>
    <div class="mb-4 flex items-center justify-between gap-3">
      <h1 class="text-2xl font-semibold text-gray-950">会中控制台</h1>
      <div class="flex flex-wrap items-center justify-end gap-2">
        <span class="rounded-md border border-gray-200 bg-white px-3 py-2 text-xs text-gray-600">
          单次录音上限 {{ maxRecordingDurationLabel }}
        </span>
        <span class="flex items-center gap-2 rounded-md border border-gray-200 bg-white px-3 py-2 text-xs text-gray-600">
          <span class="h-2 w-2 rounded-full" :class="isRecording ? 'bg-rose-500' : 'bg-gray-300'"></span>
          {{ statusLabel }} · {{ elapsedLabel }}
        </span>
      </div>
    </div>

    <div class="grid min-h-0 flex-1 gap-4 lg:grid-cols-[minmax(17rem,19rem)_minmax(0,1fr)_17rem]">
      <section class="min-h-0 min-w-0 overflow-y-auto rounded-lg border border-gray-200 bg-white p-4">
        <div class="mb-4">
          <h2 class="text-sm font-semibold text-gray-950">会议设置</h2>
        </div>
        <div class="grid gap-3">
          <label class="block">
            <span class="text-sm font-medium text-gray-700">会议标题</span>
            <input
              v-model="meetingTitle"
              data-testid="meeting-title-input"
              type="text"
              class="mt-1 block h-10 w-full rounded-md border border-gray-300 bg-white px-3 text-sm text-gray-900 shadow-sm focus:border-cyan-600 focus:outline-none focus:ring-1 focus:ring-cyan-600"
              :disabled="isBusy || isRecording"
            />
          </label>

          <label class="relative block min-w-0">
            <span class="text-sm font-medium text-gray-700">上下文小组</span>
            <div class="relative min-w-0">
              <input
                v-model="squadQuery"
                data-testid="meeting-squad-search"
                type="text"
                role="combobox"
                :aria-expanded="planDropdownOpen"
                aria-autocomplete="list"
                class="mt-1 block h-11 w-full rounded-lg border border-gray-200 bg-white px-3 pr-16 text-sm text-gray-900 shadow-sm focus:outline-none focus:ring-2 focus:ring-gray-900 disabled:bg-gray-50"
                placeholder="搜索并添加小组, 可不选"
                :disabled="isBusy || isRecording"
                @focus="openSquadDropdown"
                @click="openSquadDropdown"
                @input="onSquadQueryInput"
                @blur="hideSquadDropdown"
              />
              <button
                v-if="squadQuery || selectedSquadIds.length"
                type="button"
                data-testid="meeting-squad-clear"
                class="absolute inset-y-0 right-9 my-auto flex h-5 w-5 items-center justify-center rounded-full text-sm font-medium text-gray-900 transition-colors hover:bg-gray-100"
                aria-label="清空小组"
                :disabled="isBusy || isRecording"
                @mousedown.prevent
                @click="clearSquads"
              >
                ×
              </button>
              <svg class="pointer-events-none absolute inset-y-0 right-3 my-auto h-4 w-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <div
              v-if="planDropdownOpen"
              class="absolute left-0 right-0 top-full z-20 mt-1 max-h-64 min-w-0 overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg"
            >
              <button
                v-for="item in filteredPlanOptions"
                :key="String(item.id)"
                type="button"
                :data-testid="`meeting-squad-option-${item.id}`"
                class="block w-full min-w-0 px-3 py-2 text-left text-sm text-gray-700 hover:bg-cyan-50"
                @mousedown.prevent="selectSquad(item)"
                @click.prevent="selectSquad(item)"
              >
                <span class="flex items-center gap-2 font-medium text-gray-900">
                  <span class="truncate">{{ item.squad_name || '未命名小组' }}</span>
                  <span
                    v-if="recommendedSquadIds.has(item.id)"
                    class="shrink-0 rounded bg-cyan-50 px-1.5 py-0.5 text-[11px] text-cyan-700"
                  >
                    我的
                  </span>
                </span>
                <span class="mt-0.5 block truncate text-xs text-gray-500">
                  {{ item.squad_code || '无编号' }} / {{ item.member_count || 0 }} 人
                </span>
              </button>
              <div v-if="isLoadingSquads" class="px-3 py-3 text-sm text-gray-400">
                正在加载小组
              </div>
              <div v-else-if="filteredPlanOptions.length === 0" class="px-3 py-3 text-sm text-gray-400">
                未找到匹配小组
              </div>
            </div>
          </label>

          <div class="rounded-md border border-amber-200 bg-amber-50 px-3 py-3">
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="text-sm font-medium text-amber-950">采集授权语音记录</p>
                <p class="mt-1 text-xs leading-5 text-amber-900">
                  推荐对着麦克风说: {{ consentPhrase }}
                </p>
              </div>
              <span
                class="shrink-0 rounded px-2 py-0.5 text-xs font-medium"
                :class="consentStatusClass"
              >
                {{ consentStatusText }}
              </span>
            </div>
            <button
              type="button"
              data-testid="consent-voice-button"
              class="mt-3 inline-flex h-8 items-center justify-center rounded-md border border-amber-300 bg-white px-3 text-xs font-medium text-amber-900 hover:bg-amber-100 disabled:cursor-not-allowed disabled:bg-amber-100/60"
              :disabled="isBusy || isRecording || consentRecognitionActive"
              @click="startConsentVoiceCheck"
            >
              {{ consentRecognitionActive ? '正在识别...' : '语音确认' }}
            </button>
            <p
              v-if="consentTranscript"
              data-testid="consent-voice-transcript"
              class="mt-2 text-xs leading-5 text-amber-900"
            >
              识别结果: {{ consentTranscript }}
            </p>
          </div>

          <div
            v-if="networkStatusText"
            data-testid="network-resilience-banner"
            class="rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-sm leading-6 text-sky-900"
          >
            {{ networkStatusText }}
          </div>

          <div class="min-w-0 overflow-hidden rounded-md border border-gray-100 bg-gray-50 px-4 py-3">
            <p class="text-xs font-medium text-gray-500">会议上下文</p>
            <div v-if="selectedSquads.length" class="mt-3 space-y-3">
              <div
                v-for="squad in selectedSquads"
                :key="squad.id"
                class="min-w-0 rounded-md border border-gray-200 bg-white px-3 py-2"
              >
                <div class="flex items-start justify-between gap-2">
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-gray-900">{{ squad.squad_name || '未命名小组' }}</p>
                    <p class="mt-0.5 truncate text-xs text-gray-500">{{ squad.squad_code || '无编号' }}</p>
                  </div>
                  <button
                    type="button"
                    class="rounded px-2 py-0.5 text-xs text-gray-500 hover:bg-gray-100"
                    :disabled="isBusy || isRecording"
                    @click="removeSquad(squad.id)"
                  >
                    移除
                  </button>
                </div>
                <div class="mt-2 grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <p class="text-gray-400">成员</p>
                    <p class="mt-0.5 font-medium text-gray-800">{{ squad.member_count || 0 }} 人</p>
                  </div>
                  <div>
                    <p class="text-gray-400">状态</p>
                    <p class="mt-0.5 font-medium text-gray-800">{{ squadStatusLabel(squad.status) }}</p>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="mt-3 text-sm text-gray-500">未选择小组, 本次会议仍可录音并在会后补充上下文</div>
            <div class="mt-4 min-w-0 rounded-md border border-cyan-100 bg-cyan-50 px-3 py-2">
              <div class="flex min-w-0 flex-col gap-1">
                <p class="text-xs font-medium text-cyan-800">轻量项目上下文</p>
                <span class="break-words text-xs leading-5 text-cyan-700">{{ projectContextSummaryText }}</span>
              </div>
              <div v-if="projectContextPreviewItems.length" class="mt-2 space-y-1">
                <p
                  v-for="project in projectContextPreviewItems"
                  :key="`${project.project_id}-${project.project_plan_id}`"
                  class="truncate text-xs text-cyan-900"
                >
                  {{ projectOptionLabel(project) }}
                </p>
              </div>
            </div>
            <div class="mt-3 rounded-md border border-gray-200 bg-white px-3 py-3">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <p class="text-xs font-medium text-gray-700">会议材料</p>
                  <p class="mt-0.5 text-xs text-gray-500">可上传会前材料, 创建会议后自动保存为 AI 上下文</p>
                </div>
                <label class="inline-flex h-8 cursor-pointer items-center justify-center rounded-md border border-gray-200 bg-white px-3 text-xs font-medium text-gray-700 hover:bg-gray-50">
                  选择文件
                  <input
                    data-testid="context-file-input"
                    type="file"
                    multiple
                    class="sr-only"
                    accept=".txt,.md,.markdown,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.json"
                    :disabled="isBusy || isRecording"
                    @change="onContextFilesChange"
                  />
                </label>
              </div>
              <div v-if="pendingContextFiles.length" class="mt-2 space-y-1">
                <p
                  v-for="file in pendingContextFiles"
                  :key="`${file.name}-${file.size}`"
                  class="truncate text-xs text-gray-600"
                >
                  {{ file.name }}
                </p>
              </div>
            </div>
          </div>

          <div class="flex flex-wrap gap-2">
          <button
            v-if="!isRecording"
            type="button"
            data-testid="start-recording"
            class="inline-flex h-9 items-center justify-center rounded-md bg-cyan-700 px-3 text-sm font-medium text-white shadow-sm hover:bg-cyan-800 disabled:cursor-not-allowed disabled:bg-gray-300"
            :disabled="isBusy"
            @click="startRecording"
          >
            开始录音
          </button>
          <button
            v-else
            type="button"
            data-testid="stop-recording"
            class="inline-flex h-9 items-center justify-center rounded-md bg-rose-600 px-3 text-sm font-medium text-white shadow-sm hover:bg-rose-700"
            @click="stopRecording"
          >
            停止录音
          </button>
          </div>
        </div>
      </section>

      <section
        data-testid="meeting-live-transcript-panel"
        class="flex min-h-0 flex-col rounded-lg border border-gray-200 bg-white"
      >
        <div class="flex items-center justify-between border-b border-gray-100 px-4 py-3">
          <div>
            <h2 class="text-sm font-semibold text-gray-950">实时转写流</h2>
            <p class="mt-0.5 text-xs text-gray-500">边录边看, 结束后自动进入确认台</p>
          </div>
          <span class="rounded bg-cyan-50 px-2 py-0.5 text-xs font-medium text-cyan-700">{{ statusLabel }}</span>
        </div>
        <div
          data-testid="meeting-live-transcript-body"
          class="min-h-0 max-h-[calc(100vh-15rem)] flex-1 overflow-y-auto px-5 py-4"
        >
          <p v-if="liveTranscript" class="whitespace-pre-wrap text-sm leading-7 text-gray-800">{{ liveTranscript }}</p>
          <div v-else class="flex h-full min-h-64 items-center justify-center rounded-md border border-dashed border-gray-200 bg-gray-50 text-center">
            <div>
              <p class="text-sm font-medium text-gray-700">等待实时转写</p>
              <p class="mt-1 text-xs text-gray-500">开始录音后, 识别文本会显示在这里</p>
            </div>
          </div>
        </div>
      </section>

      <aside
        data-testid="meeting-assistant-panel"
        class="min-h-0 overflow-y-auto rounded-lg border border-gray-200 bg-white p-4"
      >
        <div>
          <h2 class="text-sm font-semibold text-gray-950">AI 小助手</h2>
          <p class="mt-1 text-xs text-gray-500">会中补充信息, 会后生成纪要和行动建议</p>
        </div>
        <div class="mt-4 grid gap-3">
          <div class="rounded-md border border-gray-100 bg-gray-50 px-3 py-2">
            <p class="text-xs text-gray-500">采集授权</p>
            <p class="mt-1 text-sm font-medium" :class="recordingConsentConfirmed ? 'text-emerald-700' : consentTranscript ? 'text-sky-700' : 'text-amber-700'">
              {{ consentStatusText }}
            </p>
          </div>
          <div class="rounded-md border border-gray-100 bg-gray-50 px-3 py-2">
            <p class="text-xs text-gray-500">手动记录</p>
            <p class="mt-1 text-sm font-medium text-gray-900">{{ manualNote.trim() ? '已补充' : '暂无补充' }}</p>
          </div>
        </div>

        <label class="mt-4 block">
          <span class="flex items-center justify-between gap-3">
            <span class="text-xs font-medium text-gray-600">手动记录</span>
            <button
              type="button"
              data-testid="save-manual-note"
              class="inline-flex h-8 items-center justify-center rounded-md border border-gray-200 bg-white px-3 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:bg-gray-100"
              :disabled="isSavingManualNote || !manualNote.trim()"
              @click="saveManualNoteNow"
            >
              保存记录
            </button>
          </span>
          <textarea
            v-model="manualNote"
            data-testid="manual-note-input"
            rows="9"
            class="mt-2 block w-full resize-y rounded-md border border-gray-300 bg-white px-3 py-2 text-sm leading-6 text-gray-900 shadow-sm focus:border-cyan-600 focus:outline-none focus:ring-1 focus:ring-cyan-600 disabled:bg-gray-50"
            placeholder="可以直接补充会议重点、口头确认、背景信息或暂时听不清的内容"
            :disabled="isBusy"
          />
        </label>
        <p v-if="manualNoteSaveStatus" class="mt-2 text-xs text-gray-500">{{ manualNoteSaveStatus }}</p>
      </aside>
    </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  createAiMeetingSegment,
  createAiMeetingContext,
  createAiMeeting,
  finishAiMeetingRecording,
  startAiMeeting,
  streamAiMeetingTranscribe,
  uploadAiMeetingAudio,
  uploadAiMeetingContextFile,
  type AIMeetingVoiceStreamEvent,
  type AIMeetingVoiceStreamHandle,
} from '@/api/aiMeetings'
import { getSquads, type Squad } from '@/api/squad'
import { getMyProjects, getProjectPlan, type MyProject, type ProjectPlan, type Node as ProjectNode } from '@/api/projectPlan'
import showConfirm from '@/utils/confirm'
import message from '@/utils/message'

const router = useRouter()
const meetingId = ref<number | null>(null)
const isBusy = ref(false)
const isRecording = ref(false)
const startedAt = ref<number | null>(null)
const elapsedSeconds = ref(0)
const voiceHandle = ref<AIMeetingVoiceStreamHandle | null>(null)
const liveTranscript = ref('')
const planOptions = ref<Squad[]>([])
const recommendedSquadOptions = ref<Squad[]>([])
const isLoadingSquads = ref(false)
const projectContextCandidates = ref<MyProject[]>([])
const isLoadingProjectContext = ref(false)
const selectedSquadIds = ref<number[]>([])
const squadQuery = ref('')
const planDropdownOpen = ref(false)
const meetingTitle = ref(defaultMeetingTitle())
const recordingConsentConfirmed = ref(false)
const consentRecognitionActive = ref(false)
const consentTranscript = ref('')
const manualNote = ref('')
const savedManualNote = ref('')
const manualNoteSaveStatus = ref('')
const isSavingManualNote = ref(false)
const isOffline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false)
const streamInterrupted = ref(false)
const pendingUploadFile = ref<File | null>(null)
const pendingUploadMeetingId = ref<number | null>(null)
const pendingContextFiles = ref<File[]>([])
const processingStarted = ref(false)
const processingLogs = ref<string[]>([])
let timer: number | null = null
let recordingSavePromise: Promise<void> | null = null
const consentPhrase = '全体参与人员认可'
const MAX_RECORDING_SECONDS = 120 * 60

const selectedSquad = computed(() =>
  selectedSquads.value[0] || null
)
const selectedSquads = computed(() =>
  selectedSquadIds.value
    .map((id) => planOptions.value.find((item) => item.id === id))
    .filter((item): item is Squad => Boolean(item))
)

const filteredPlanOptions = computed(() => {
  const query = squadQuery.value.trim().toLowerCase()
  const selected = selectedSquad.value
  const recommendedIds = recommendedSquadIds.value
  const sourceOptions = !query
    ? [
        ...recommendedSquadOptions.value,
        ...planOptions.value.filter((item) => !recommendedIds.has(item.id)),
      ]
    : planOptions.value
  if (selected && query === squadLabel(selected).toLowerCase()) {
    return sourceOptions.slice(0, 8)
  }
  if (!query) return sourceOptions.slice(0, 8)
  return sourceOptions.filter((item) => {
    const text = [
      item.squad_name,
      item.squad_code,
      item.id ? String(item.id) : '',
    ].filter(Boolean).join(' ').toLowerCase()
    return text.includes(query)
  }).slice(0, 8)
})

const recommendedSquadIds = computed(() => new Set(recommendedSquadOptions.value.map((item) => item.id)))

const statusLabel = computed(() => {
  if (isRecording.value) return '录音中'
  if (isBusy.value) return '处理中'
  if (pendingUploadFile.value && isOffline.value) return '等待网络恢复'
  if (pendingUploadFile.value) return '等待上传'
  if (meetingId.value) return '录音已上传'
  return '准备开始会议'
})

const consentStatusText = computed(() => {
  if (recordingConsentConfirmed.value) return '已记录'
  if (consentTranscript.value) return '已记录但未命中推荐短句'
  return '未记录'
})

const consentStatusClass = computed(() => {
  if (recordingConsentConfirmed.value) return 'bg-emerald-100 text-emerald-700'
  if (consentTranscript.value) return 'bg-sky-100 text-sky-700'
  return 'bg-amber-100 text-amber-800'
})

const elapsedLabel = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60).toString().padStart(2, '0')
  const seconds = (elapsedSeconds.value % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
})

const maxRecordingDurationLabel = computed(() => {
  const minutes = Math.floor(MAX_RECORDING_SECONDS / 60).toString().padStart(2, '0')
  const seconds = (MAX_RECORDING_SECONDS % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
})

const networkStatusText = computed(() => {
  if (pendingUploadFile.value && isOffline.value) return '录音已保存在本地, 等待网络恢复后上传'
  if (pendingUploadFile.value) return '网络已恢复, 正在上传本地录音'
  if (streamInterrupted.value) return '网络连接中断, 本地录音继续, 停止后会保留录音文件'
  if (isOffline.value) return '当前网络不可用, 本地录音继续, 停止后会等待网络恢复再上传'
  return ''
})

const projectContextSummaryText = computed(() => {
  if (isLoadingProjectContext.value) return '正在匹配项目计划上下文'
  if (!selectedSquadIds.value.length) return '选择小组后自动匹配项目计划摘要'
  if (!projectContextCandidates.value.length) return '暂未匹配到项目计划'
  const planCount = projectContextCandidates.value.filter((item) => item.project_plan_id).length
  const milestoneCount = projectContextCandidates.value.reduce((sum, item) => sum + (item.in_progress_milestone_count || 0), 0)
  return `已匹配 ${planCount} 个项目计划 / ${milestoneCount} 个进行中里程碑`
})

const projectContextPreviewItems = computed(() => projectContextCandidates.value.slice(0, 3))

function startTimer() {
  startedAt.value = Date.now()
  elapsedSeconds.value = 0
  timer = window.setInterval(() => {
    if (startedAt.value) {
      elapsedSeconds.value = Math.floor((Date.now() - startedAt.value) / 1000)
      if (isRecording.value && elapsedSeconds.value >= MAX_RECORDING_SECONDS) {
        message.warning('已达到单次录音上限, 正在自动停止录音')
        void stopRecording()
      }
    }
  }, 1000)
}

function stopTimer() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

function defaultMeetingTitle() {
  const now = new Date()
  const date = `${now.getFullYear()}/${now.getMonth() + 1}/${now.getDate()}`
  const time = [
    now.getHours().toString().padStart(2, '0'),
    now.getMinutes().toString().padStart(2, '0'),
    now.getSeconds().toString().padStart(2, '0'),
  ].join(':')
  return `会议录音 ${date} ${time}`
}

function squadLabel(squad: Squad): string {
  return `${squad.squad_name || '未命名小组'} / ${squad.squad_code || '无编号'}`
}

function onSquadQueryInput() {
  planDropdownOpen.value = true
}

function openSquadDropdown(event: Event) {
  planDropdownOpen.value = true
  if (selectedSquad.value && event.target instanceof HTMLInputElement) {
    event.target.select()
  }
}

function selectSquad(squad: Squad) {
  if (!selectedSquadIds.value.includes(squad.id)) {
    selectedSquadIds.value = [...selectedSquadIds.value, squad.id]
  }
  squadQuery.value = ''
  planDropdownOpen.value = false
}

function removeSquad(squadId: number) {
  selectedSquadIds.value = selectedSquadIds.value.filter((id) => id !== squadId)
}

function clearSquads() {
  selectedSquadIds.value = []
  squadQuery.value = ''
  planDropdownOpen.value = true
}

function hideSquadDropdown() {
  window.setTimeout(() => {
    planDropdownOpen.value = false
  }, 160)
}

function squadStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    ACTIVE: '启用',
    INACTIVE: '停用',
  }
  return status ? labels[status] || status : '-'
}

function getSpeechRecognitionCtor() {
  const win = window as typeof window & {
    SpeechRecognition?: new () => any
    webkitSpeechRecognition?: new () => any
  }
  return win.SpeechRecognition || win.webkitSpeechRecognition || null
}

function extractSpeechText(event: any): string {
  const results = Array.from(event?.results || []) as any[]
  return results
    .map((result) => Array.from(result || []).map((item: any) => item?.transcript || '').join(''))
    .join('')
    .trim()
}

function startConsentVoiceCheck() {
  const Recognition = getSpeechRecognitionCtor()
  if (!Recognition) {
    message.warning('当前浏览器不支持语音确认')
    return
  }
  recordingConsentConfirmed.value = false
  consentRecognitionActive.value = true
  consentTranscript.value = ''
  const recognition = new Recognition()
  recognition.lang = 'zh-CN'
  recognition.interimResults = false
  recognition.maxAlternatives = 1
  recognition.onresult = (event: any) => {
    const text = extractSpeechText(event)
    consentTranscript.value = text
    recordingConsentConfirmed.value = text.includes(consentPhrase)
    consentRecognitionActive.value = false
    if (recordingConsentConfirmed.value) {
      message.success('采集授权语音已记录')
    } else {
      message.warning(`未命中推荐短句"${consentPhrase}", 仍可开始录音`)
    }
  }
  recognition.onerror = () => {
    consentRecognitionActive.value = false
    message.warning('未识别到有效授权语音')
  }
  recognition.start()
}

async function startRecording() {
  const ok = await showConfirm({
    title: '开始会议录音',
    message: '本次会议将录音并用于生成会议纪要和项目行动建议.',
    confirmText: '开始录音',
  })
  if (!ok) return

  isBusy.value = true
  try {
    const meeting = await createAiMeeting({
      title: meetingTitle.value.trim() || defaultMeetingTitle(),
      squad_ids: selectedSquadIds.value,
    })
    meetingId.value = meeting.id
    await persistConsentRecord(meeting.id)
    await persistProjectPlanSummaryContext(meeting.id)
    await uploadPendingContextFiles(meeting.id)
    if (manualNote.value.trim()) {
      await persistManualNote(meeting.id)
    }
    await startAiMeeting(meeting.id)
    liveTranscript.value = ''
    processingStarted.value = false
    processingLogs.value = []
    voiceHandle.value = await streamAiMeetingTranscribe(meeting.id, handleVoiceEvent)
    isRecording.value = true
    startTimer()
  } catch (error) {
    message.error(error)
    voiceHandle.value?.destroy()
    voiceHandle.value = null
  } finally {
    isBusy.value = false
  }
}

async function stopRecording() {
  if (!voiceHandle.value) return
  const handle = voiceHandle.value
  const activeMeetingId = meetingId.value
  stopTimer()
  isRecording.value = false
  isBusy.value = true
  processingStarted.value = true
  processingLogs.value = ['麦克风采集已停止', '正在保存浏览器本地录音']
  try {
    recordingSavePromise = saveRecordingAfterStop(handle, activeMeetingId)
    await recordingSavePromise
    processingLogs.value = [...processingLogs.value, '录音文件已保存并上传', '等待实时转写完成']
    if (pendingUploadFile.value || streamInterrupted.value) {
      processingLogs.value = [...processingLogs.value, '网络异常, 已保留本地录音, 等待网络恢复后上传']
      isBusy.value = false
    }
  } catch (error) {
    message.error(error)
    handle.destroy()
    voiceHandle.value = null
    isBusy.value = false
  }
}

async function saveRecordingAfterStop(handle: AIMeetingVoiceStreamHandle, activeMeetingId: number | null) {
  const recordingFile = await handle.stop()
  if (!activeMeetingId) return
  processingLogs.value = [...processingLogs.value, '正在写入会议记录和录音结束状态']
  if (manualNote.value.trim()) {
    await persistManualNote(activeMeetingId)
  }
  await finishAiMeetingRecording(activeMeetingId)
  if (recordingFile) {
    await uploadRecordingFile(activeMeetingId, recordingFile)
  }
}

async function uploadRecordingFile(activeMeetingId: number, recordingFile: File) {
  try {
    await uploadAiMeetingAudio(activeMeetingId, recordingFile)
    pendingUploadFile.value = null
    pendingUploadMeetingId.value = null
    streamInterrupted.value = false
  } catch (error) {
    pendingUploadFile.value = recordingFile
    pendingUploadMeetingId.value = activeMeetingId
    message.warning(isOffline.value ? '网络中断, 已保留本地录音' : '录音上传失败, 已保留本地录音')
  }
}

async function retryPendingUpload() {
  if (!pendingUploadFile.value || !pendingUploadMeetingId.value || isOffline.value) return
  const file = pendingUploadFile.value
  const activeMeetingId = pendingUploadMeetingId.value
  try {
    await uploadRecordingFile(activeMeetingId, file)
    if (!pendingUploadFile.value) {
      message.success('本地录音已上传')
      await router.push({ name: 'AiMeetingConfirm', params: { meetingId: activeMeetingId } })
    }
  } finally {
    isBusy.value = false
  }
}

async function persistManualNote(activeMeetingId: number) {
  const note = manualNote.value.trim()
  const saved = savedManualNote.value.trim()
  if (!note || note === saved) return

  const text = saved && note.startsWith(saved) ? note.slice(saved.length).trim() : note
  if (!text) return
  await createAiMeetingSegment(activeMeetingId, { text })
  await createAiMeetingContext(activeMeetingId, {
    asset_type: 'manual_note',
    title: '手动背景',
    content_text: text,
    metadata_json: { source: 'recorder_manual_note' },
  })
  savedManualNote.value = note
}

async function saveManualNoteNow() {
  if (!manualNote.value.trim()) return
  isSavingManualNote.value = true
  try {
    if (meetingId.value) {
      await persistManualNote(meetingId.value)
      manualNoteSaveStatus.value = '手动记录已保存'
      message.success('手动记录已保存')
      return
    }
    manualNoteSaveStatus.value = '手动记录已暂存, 开始录音后会写入会议记录'
    message.success('手动记录已暂存')
  } catch (error) {
    message.error(error)
  } finally {
    isSavingManualNote.value = false
  }
}

function onContextFilesChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return
  pendingContextFiles.value = [...pendingContextFiles.value, ...files]
  input.value = ''
}

async function uploadPendingContextFiles(activeMeetingId: number) {
  if (!pendingContextFiles.value.length) return
  const files = [...pendingContextFiles.value]
  for (const file of files) {
    await uploadAiMeetingContextFile(activeMeetingId, file)
  }
  pendingContextFiles.value = []
}

async function persistConsentRecord(activeMeetingId: number) {
  const transcript = consentTranscript.value.trim()
  const confirmed = recordingConsentConfirmed.value
  const contentText = transcript
    ? confirmed
      ? `识别结果: ${transcript}.`
      : `识别结果: ${transcript}. 未识别到推荐授权短句.`
    : '本次会议未记录全体参与人员认可采集语音.'
  await createAiMeetingContext(activeMeetingId, {
    asset_type: 'consent_record',
    title: '采集授权记录',
    content_text: contentText,
    metadata_json: {
      source: 'recorder_consent_voice',
      confirmed,
      transcript,
    },
  })
}

function projectOptionLabel(project: MyProject): string {
  const projectName = project.project_name || '未命名项目'
  const code = project.project_code ? ` (${project.project_code})` : ''
  const squadName = project.squad_name ? ` / ${project.squad_name}` : ''
  return `${projectName}${code}${squadName}`
}

function activeNodes(plan: ProjectPlan): ProjectNode[] {
  const nodes: ProjectNode[] = []
  for (const milestone of plan.milestones || []) {
    for (const node of milestone.nodes || []) {
      if (!node.is_shelved && ['TODO', 'IN_PROGRESS'].includes(String(node.status))) {
        nodes.push(node)
      }
    }
  }
  return nodes.slice(0, 3)
}

function buildProjectPlanSummary(plans: ProjectPlan[]): string {
  return plans.map((plan) => {
    const projectName = plan.project_name || `项目 #${plan.project_id}`
    const leaderText = plan.leader_name ? `负责人: ${plan.leader_name}` : '负责人: 未记录'
    const background = plan.background ? `背景: ${plan.background}` : ''
    const milestoneLines = (plan.milestones || [])
      .filter((milestone) => !milestone.is_deleted && !milestone.is_shelved)
      .slice(0, 3)
      .map((milestone) => `里程碑: ${milestone.title} (${milestone.status})`)
    const nodeLines = activeNodes(plan).map((node) => {
      const owner = node.owner_name ? `, 负责人: ${node.owner_name}` : ''
      const endDate = node.end_date ? `, 截止: ${node.end_date}` : ''
      return `节点: ${node.title} (${node.status}${owner}${endDate})`
    })
    return [
      `项目: ${projectName}`,
      leaderText,
      background,
      ...milestoneLines,
      ...nodeLines,
    ].filter(Boolean).join('\n')
  }).join('\n\n')
}

async function persistProjectPlanSummaryContext(activeMeetingId: number) {
  const candidates = projectContextCandidates.value
    .filter((item) => item.project_plan_id)
    .slice(0, 3)
  if (!candidates.length) return

  const plans = await Promise.all(candidates.map((item) => getProjectPlan(Number(item.project_plan_id))))
  const contentText = buildProjectPlanSummary(plans)
  if (!contentText.trim()) return
  await createAiMeetingContext(activeMeetingId, {
    asset_type: 'project_plan_summary',
    title: '轻量项目计划上下文',
    content_text: contentText,
    metadata_json: {
      source: 'recorder_project_plan_summary',
      squad_ids: selectedSquadIds.value,
      project_plan_ids: candidates.map((item) => Number(item.project_plan_id)),
    },
  })
}

async function handleVoiceEvent(event: AIMeetingVoiceStreamEvent) {
  if (event.type === 'transcript') {
    liveTranscript.value = event.text || liveTranscript.value
    return
  }
  if (event.type === 'transcript_final') {
    liveTranscript.value = event.text || liveTranscript.value
    return
  }
  if (event.type === 'done') {
    try {
      await recordingSavePromise
    } catch {
      return
    }
    message.success('会议转写已完成')
    voiceHandle.value?.destroy()
    voiceHandle.value = null
    isBusy.value = false
    processingLogs.value = [...processingLogs.value, '转写完成, 正在进入会后工作台']
    await router.push({ name: 'AiMeetingConfirm', params: { meetingId: event.meeting_id } })
    return
  }
  if (event.type === 'error') {
    if (isOffline.value && isRecording.value) {
      streamInterrupted.value = true
      message.warning('网络中断, 本地录音继续')
      return
    }
    message.error(event.error)
    voiceHandle.value?.destroy()
    voiceHandle.value = null
    isRecording.value = false
    isBusy.value = false
    stopTimer()
    return
  }
}

function handleOffline() {
  isOffline.value = true
  if (isRecording.value) {
    streamInterrupted.value = true
  }
}

function handleOnline() {
  isOffline.value = false
  void retryPendingUpload()
}

watch(selectedSquadIds, async (ids) => {
  if (!ids.length) {
    projectContextCandidates.value = []
    return
  }
  isLoadingProjectContext.value = true
  try {
    const result = await getMyProjects({ squad_ids: ids, page_size: 20 })
    projectContextCandidates.value = result.items || []
  } catch (error) {
    projectContextCandidates.value = []
    message.error(error)
  } finally {
    isLoadingProjectContext.value = false
  }
}, { deep: true })

onMounted(async () => {
  window.addEventListener('offline', handleOffline)
  window.addEventListener('online', handleOnline)
  isLoadingSquads.value = true
  try {
    const [recommendedResult, result] = await Promise.all([
      getSquads({ page_size: 20, status: 'ACTIVE', my_squads: true }),
      getSquads({ page_size: 200, status: 'ACTIVE' }),
    ])
    recommendedSquadOptions.value = recommendedResult.items || []
    planOptions.value = result.items || []
  } catch (error) {
    message.error(error)
  } finally {
    isLoadingSquads.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('online', handleOnline)
  stopTimer()
  voiceHandle.value?.destroy()
})
</script>
