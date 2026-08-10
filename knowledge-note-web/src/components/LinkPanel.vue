<template>
  <div class="link-panel">
    <div class="panel-section">
      <OutgoingLinks :links="outgoingLinks" />
    </div>
    <div class="panel-divider"></div>
    <div class="panel-section">
      <IncomingLinks :links="incomingLinks" />
    </div>
  </div>
</template>

<script setup>
import OutgoingLinks from './OutgoingLinks.vue'
import IncomingLinks from './IncomingLinks.vue'

const props = defineProps({
  outgoingLinks: { type: Array, default: () => [] },
  incomingLinks: { type: Array, default: () => [] }
})
</script>

<style scoped>
.link-panel {
  padding: 8px 0;
}
.panel-section {
  padding: 4px 0;
}
.panel-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 10px 0;
}
</style>
