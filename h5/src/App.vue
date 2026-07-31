<template>
  <!-- App 壳（0731 用户定：底栏被滚走）——壳锁视口高，页面滚动只发生在 .app-scroll 内层；
       底栏是滚动区的 flex:none 兄弟节点，物理上不可能被滚走。
       页内主按钮条仍 position:fixed：body 不再滚动后，fixed 元素不受任何滚动影响，同样稳。 -->
  <div class="app-shell">
    <div id="app-scroll" class="app-scroll">
      <router-view v-slot="{ Component }">
        <KeepAlive include="MeetingLiveQuick">
          <component :is="Component" />
        </KeepAlive>
      </router-view>
    </div>
    <TabBar />
  </div>
  <UiHost />
  <MaterialViewer />
  <AiTaskHost />
  <MeetingRecordingHost />
</template>

<script setup>
import TabBar from '@/components/TabBar.vue'
import UiHost from '@/components/UiHost.vue'
import MaterialViewer from '@/components/MaterialViewer.vue'
import AiTaskHost from '@/components/AiTaskHost.vue'
import MeetingRecordingHost from '@/components/MeetingRecordingHost.vue'
</script>
