<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  rows: { type: Array, required: true },
  admin: Boolean,
  canReturn: Boolean,
  processing: Number,
})
defineEmits(['process'])

const statuses = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已借出', type: 'primary' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  RETURNED: { label: '已归还', type: 'success' },
}
const status = ref('')
const visibleRows = computed(() => props.rows.filter(row => !status.value || row.status === status.value))
</script>

<template>
  <div class="filters record-filter">
    <el-select v-model="status" clearable placeholder="全部状态" aria-label="借阅状态">
      <el-option v-for="(entry, value) in statuses" :key="value" :label="entry.label" :value="value" />
    </el-select>
  </div>
  <el-table :data="visibleRows" row-key="id" empty-text="暂无借阅记录">
    <el-table-column prop="id" label="编号" width="80" />
    <el-table-column prop="title" label="书名" min-width="180" />
    <el-table-column v-if="admin" prop="username" label="借阅人" min-width="125" />
    <el-table-column label="状态" width="105">
      <template #default="{ row }">
        <el-tag :type="statuses[row.status]?.type">{{ statuses[row.status]?.label || row.status }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="requestedAt" label="申请时间" width="174" />
    <el-table-column prop="updatedAt" label="更新时间" width="174" />
    <el-table-column v-if="admin || canReturn" label="操作" :width="admin ? 174 : 112" :fixed="canReturn ? 'right' : false">
      <template #default="{ row }">
        <template v-if="admin && row.status === 'PENDING'">
          <el-button size="small" type="primary" :disabled="processing != null"
            @click="$emit('process', row, 'APPROVED')">批准</el-button>
          <el-button size="small" type="danger" plain :disabled="processing != null"
            @click="$emit('process', row, 'REJECTED')">拒绝</el-button>
        </template>
        <el-button v-else-if="row.status === 'APPROVED'" size="small" :disabled="processing != null"
          :loading="processing === row.id"
          @click="$emit('process', row, 'RETURNED')">{{ admin ? '登记归还' : '归还图书' }}</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>
