<template>
  <router-view v-slot="{ Component }">
    <KeepAlive include="MeetingLiveQuick">
      <component :is="Component" />
    </KeepAlive>
  </router-view>
  <TabBar />
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
