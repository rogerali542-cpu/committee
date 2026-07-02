<template>
  <router-view />
  <TabBar />
  <UiHost />
  <MaterialViewer />
</template>

<script setup>
import TabBar from '@/components/TabBar.vue'
import UiHost from '@/components/UiHost.vue'
import MaterialViewer from '@/components/MaterialViewer.vue'
</script>
