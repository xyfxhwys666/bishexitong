<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const files = ref([])
const strategy = ref('mosaic')
const privacyLevel = ref('standard')
const priority = ref('normal')
const loading = ref(false)
const error = ref('')
const result = ref(null)
const history = ref([])
const historyLoading = ref(false)
const statistics = ref({
  total: 0,
  completed: 0,
  processing: 0,
  failed: 0,
  agentTasks: 0,
  averageSsim: 0,
  averagePsnr: 0,
  strategyCounts: {},
})
const pollingIds = ref([])
let pollingTimer = null

const strategyLabels = {
  auto: 'Agent 自动选择',
  blur: '高斯模糊',
  mosaic: '像素马赛克',
  cover: '纯色遮挡',
}

const privacyLabels = {
  light: '轻度',
  standard: '标准',
  strict: '严格',
}

const priorityLabels = {
  low: '低',
  normal: '普通',
  high: '高',
}

const statusLabels = {
  QUEUED: '排队中',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '处理失败',
}

const resultImage = computed(() => {
  if (!result.value?.imageBase64) return ''
  return `data:image/jpeg;base64,${result.value.imageBase64}`
})

const agentTrace = computed(() => {
  const raw = result.value?.agentTrace
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
})

const strategyUsage = computed(() => Object.entries(statistics.value.strategyCounts || {}).map(([key, value]) => ({
  key,
  label: labelForStrategy(key),
  value,
})))

function onFileChange(event) {
  files.value = Array.from(event.target.files || []).slice(0, 20)
  error.value = ''
  result.value = null
}

function labelForStrategy(value) {
  return strategyLabels[value] || value
}

function labelForPrivacy(value) {
  return privacyLabels[value] || value
}

function labelForPriority(value) {
  return priorityLabels[value] || value
}

function labelForStatus(value) {
  return statusLabels[value] || value
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function formatObservation(value) {
  return typeof value === 'string' ? value : JSON.stringify(value)
}

async function loadStatistics() {
  try {
    const response = await fetch('/api/v1/tasks/stats')
    if (!response.ok) throw new Error('统计数据加载失败')
    statistics.value = await response.json()
  } catch (err) {
    error.value = err.message || '统计数据加载失败'
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const response = await fetch('/api/v1/tasks')
    if (!response.ok) throw new Error('历史任务加载失败')
    history.value = await response.json()
    await loadStatistics()
  } catch (err) {
    error.value = err.message || '历史任务加载失败'
  } finally {
    historyLoading.value = false
  }
}

async function openHistory(task) {
  error.value = ''
  try {
    const response = await fetch(`/api/v1/tasks/${task.id}`)
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || '任务详情加载失败')
    result.value = payload
    if (!['COMPLETED', 'FAILED'].includes(payload.status)) startPolling([payload.id])
  } catch (err) {
    error.value = err.message || '任务详情加载失败'
  }
}

function stopPolling() {
  if (pollingTimer) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
  pollingIds.value = []
}

async function pollTasks() {
  if (!pollingIds.value.length) return
  try {
    const tasks = await Promise.all(pollingIds.value.map(async (id) => {
      const response = await fetch(`/api/v1/tasks/${id}`)
      if (!response.ok) throw new Error('任务状态查询失败')
      return response.json()
    }))
    if (tasks[0]) result.value = tasks[0]
    await loadHistory()
    if (tasks.every((task) => ['COMPLETED', 'FAILED'].includes(task.status))) stopPolling()
  } catch (err) {
    error.value = err.message || '任务状态查询失败'
    stopPolling()
  }
}

function startPolling(ids) {
  stopPolling()
  pollingIds.value = ids
  pollTasks()
  pollingTimer = window.setInterval(pollTasks, 900)
}

async function retryTask() {
  if (!result.value?.id) return
  loading.value = true
  error.value = ''
  try {
    const response = await fetch(`/api/v1/tasks/${result.value.id}/retry`, { method: 'POST' })
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || '重试提交失败')
    result.value = payload
    startPolling([payload.id])
  } catch (err) {
    error.value = err.message || '重试提交失败'
  } finally {
    loading.value = false
  }
}

function downloadResult() {
  if (!result.value?.imageBase64) return
  const bytes = atob(result.value.imageBase64)
  const buffer = new Uint8Array(bytes.length)
  for (let index = 0; index < bytes.length; index += 1) buffer[index] = bytes.charCodeAt(index)
  const url = URL.createObjectURL(new Blob([buffer], { type: 'image/jpeg' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `face-privacy-task-${result.value.id}.jpg`
  link.click()
  URL.revokeObjectURL(url)
}

async function processImage() {
  if (!files.value.length) {
    error.value = '请先选择一张包含人脸的图片'
    return
  }
  stopPolling()
  loading.value = true
  error.value = ''
  result.value = null
  const form = new FormData()
  const isBatch = files.value.length > 1
  const endpoint = isBatch ? '/api/v1/tasks/batch' : '/api/v1/tasks'
  const field = isBatch ? 'files' : 'file'
  files.value.forEach((selectedFile) => form.append(field, selectedFile))
  form.append('strategy', strategy.value)
  form.append('privacyLevel', privacyLevel.value)
  form.append('priority', priority.value)
  try {
    const response = await fetch(endpoint, { method: 'POST', body: form })
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.detail || payload.message || '处理失败')
    const tasks = isBatch ? payload.tasks : [payload]
    result.value = tasks[0]
    await loadHistory()
    startPolling(tasks.map((task) => task.id))
  } catch (err) {
    error.value = err.message || '处理失败，请检查后端和 Python 服务是否启动'
  } finally {
    loading.value = false
  }
}

onMounted(loadHistory)
onUnmounted(stopPolling)
</script>

<template>
  <main class="page-shell">
    <header class="topbar">
      <div>
        <div class="brand-line">
          <span class="brand-mark">FP</span>
          <p class="eyebrow">FACE PRIVACY WORKBENCH</p>
        </div>
        <h1>人脸图像隐私脱敏</h1>
        <p class="subtitle">从人脸检测到策略决策，再到效果评估的智能处理工作台。</p>
      </div>
      <span class="status-chip"><i></i> 答辩演示模式</span>
    </header>

    <section class="overview-grid" aria-label="系统概览">
      <div class="overview-card accent">
        <span class="overview-label">累计任务</span>
        <strong>{{ statistics.total }}</strong>
        <small>已接收的处理任务</small>
      </div>
      <div class="overview-card">
        <span class="overview-label">Agent 调度</span>
        <strong>{{ statistics.agentTasks }}</strong>
        <small>自动决策任务</small>
      </div>
      <div class="overview-card">
        <span class="overview-label">平均 SSIM</span>
        <strong>{{ statistics.averageSsim ? statistics.averageSsim.toFixed(4) : '—' }}</strong>
        <small>图像结构保真度</small>
      </div>
      <div class="overview-card">
        <span class="overview-label">任务状态</span>
        <strong>{{ statistics.processing }}</strong>
        <small>{{ statistics.failed ? `${statistics.failed} 个待复核失败任务` : '当前无失败任务' }}</small>
      </div>
    </section>

    <section class="workspace">
      <div class="control-panel">
        <div class="panel-heading">
          <div>
            <div class="section-label">处理配置</div>
            <h2>提交隐私任务</h2>
          </div>
          <span class="panel-index">01</span>
        </div>
        <label class="upload-box" :class="{ selected: files.length }">
          <input type="file" accept="image/png,image/jpeg,image/webp" multiple @change="onFileChange" />
          <span class="upload-icon">↑</span>
          <strong>{{ files.length ? `${files.length} 张图片已选择` : '选择人脸图片' }}</strong>
          <small>{{ files.length ? files.map((selectedFile) => selectedFile.name).join('、') : 'PNG、JPG 或 WebP，最多 20 张' }}</small>
        </label>

        <div class="field-grid">
          <label>
            <span>脱敏策略</span>
            <select v-model="strategy">
              <option value="auto">Agent 自动选择</option>
              <option value="blur">高斯模糊</option>
              <option value="mosaic">像素马赛克</option>
              <option value="cover">纯色遮挡</option>
            </select>
          </label>
          <label>
            <span>隐私等级</span>
            <select v-model="privacyLevel">
              <option value="light">轻度</option>
              <option value="standard">标准</option>
              <option value="strict">严格</option>
            </select>
          </label>
          <label>
            <span>任务优先级</span>
            <select v-model="priority">
              <option value="low">低</option>
              <option value="normal">普通</option>
              <option value="high">高</option>
            </select>
          </label>
        </div>

        <button class="primary-button" :disabled="loading" @click="processImage">
          {{ loading ? '任务提交中…' : files.length > 1 ? '提交批量任务' : '提交脱敏任务' }}
          <span aria-hidden="true">→</span>
        </button>
        <p v-if="error" class="error-message">{{ error }}</p>
        <p class="helper">任务会进入后台队列，Agent 模式会记录检测、决策和评估轨迹；失败任务支持重试。</p>
      </div>

      <div class="result-panel">
        <div class="result-heading">
          <div>
          <div class="section-label">处理结果</div>
            <h2>{{ result ? labelForStatus(result.status) : '等待提交图片' }}</h2>
          </div>
          <div v-if="result" class="result-meta">
            <span class="result-badge">{{ result.status === 'COMPLETED' ? `${result.faceCount} 张人脸` : labelForPriority(result.priority) + '优先级' }}</span>
            <span v-if="result.strategy === 'auto'" class="agent-badge">Agent</span>
          </div>
        </div>

        <div v-if="result?.status === 'COMPLETED'" class="result-content">
          <div class="image-frame"><img :src="resultImage" alt="脱敏结果" /></div>
          <div class="result-actions">
            <span class="file-name">{{ result.originalFilename }}</span>
            <button class="secondary-button" type="button" @click="downloadResult">下载脱敏图</button>
          </div>
          <div class="metric-grid">
            <div><span>策略</span><strong>{{ labelForStrategy(result.appliedStrategy || result.strategy) }}</strong></div>
            <div><span>隐私等级</span><strong>{{ labelForPrivacy(result.privacyLevel) }}</strong></div>
            <div><span>PSNR</span><strong>{{ result.metrics.psnr.toFixed(2) }} dB</strong></div>
            <div><span>SSIM</span><strong>{{ result.metrics.ssim.toFixed(4) }}</strong></div>
            <div><span>耗时</span><strong>{{ result.metrics.elapsedMs }} ms</strong></div>
          </div>
          <div v-if="agentTrace.length" class="agent-trace">
            <div class="agent-trace-heading">
              <span class="section-label">Agent 工具轨迹</span>
              <span>{{ agentTrace.length }} 步</span>
            </div>
            <div v-for="step in agentTrace" :key="step.step" class="agent-step">
              <span class="agent-step-number">{{ step.step }}</span>
              <div>
                <strong>{{ step.action }}</strong>
                <p>{{ step.thought }}</p>
                <small>{{ formatObservation(step.observation) }}</small>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="result" class="progress-state">
          <div class="progress-mark" :class="result.status.toLowerCase()">{{ result.status === 'FAILED' ? '!' : '…' }}</div>
          <strong>{{ labelForStatus(result.status) }}</strong>
          <p>第 {{ result.attempt || 0 }} 次尝试 · {{ labelForPriority(result.priority) }}优先级</p>
          <p v-if="result.errorMessage" class="progress-error">{{ result.errorMessage }}</p>
          <button v-if="result.status === 'FAILED'" class="secondary-button" type="button" :disabled="loading" @click="retryTask">重新排队</button>
        </div>
        <div v-else class="empty-state">
          <div class="empty-mark">◎</div>
          <p>处理完成后，结果图和评价指标会显示在这里</p>
        </div>
      </div>
    </section>

    <section class="history-section">
      <div class="history-heading">
        <div>
          <div class="section-label">任务记录</div>
          <h2>最近处理</h2>
        </div>
        <span class="history-count">{{ historyLoading ? '加载中…' : `${history.length} 条记录` }}</span>
      </div>
      <div v-if="history.length" class="history-list">
        <button
          v-for="task in history"
          :key="task.id"
          class="history-row"
          type="button"
          @click="openHistory(task)"
        >
          <span class="history-status" :class="task.status.toLowerCase()"></span>
          <span class="history-main">
            <strong>{{ task.originalFilename }}</strong>
            <small>{{ formatDate(task.createdAt) }} · {{ labelForStrategy(task.appliedStrategy || task.strategy) }}</small>
          </span>
          <span class="history-metric">{{ task.metrics ? `${task.metrics.ssim.toFixed(4)} SSIM` : task.status }}</span>
          <span class="history-arrow" aria-hidden="true">→</span>
        </button>
      </div>
      <div v-if="strategyUsage.length" class="strategy-summary">
        <span class="section-label">已完成策略分布</span>
        <div class="strategy-pills">
          <span v-for="item in strategyUsage" :key="item.key" class="strategy-pill">
            <i :class="`strategy-dot ${item.key}`"></i>{{ item.label }} <strong>{{ item.value }}</strong>
          </span>
        </div>
      </div>
      <p v-if="!history.length" class="history-empty">完成第一张图片后，任务记录会保存在这里。</p>
    </section>
  </main>
</template>
