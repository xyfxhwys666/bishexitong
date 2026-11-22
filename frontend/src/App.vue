<script setup>
import { computed, onMounted, ref } from 'vue'

const file = ref(null)
const strategy = ref('mosaic')
const privacyLevel = ref('standard')
const loading = ref(false)
const error = ref('')
const result = ref(null)
const history = ref([])
const historyLoading = ref(false)

const strategyLabels = {
  blur: '高斯模糊',
  mosaic: '像素马赛克',
  cover: '纯色遮挡',
}

const privacyLabels = {
  light: '轻度',
  standard: '标准',
  strict: '严格',
}

const resultImage = computed(() => {
  if (!result.value?.imageBase64) return ''
  return `data:image/jpeg;base64,${result.value.imageBase64}`
})

function onFileChange(event) {
  file.value = event.target.files?.[0] ?? null
  error.value = ''
  result.value = null
}

function labelForStrategy(value) {
  return strategyLabels[value] || value
}

function labelForPrivacy(value) {
  return privacyLabels[value] || value
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const response = await fetch('/api/v1/tasks')
    if (!response.ok) throw new Error('历史任务加载失败')
    history.value = await response.json()
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
  } catch (err) {
    error.value = err.message || '任务详情加载失败'
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
  if (!file.value) {
    error.value = '请先选择一张包含人脸的图片'
    return
  }
  loading.value = true
  error.value = ''
  result.value = null
  const form = new FormData()
  form.append('file', file.value)
  form.append('strategy', strategy.value)
  form.append('privacyLevel', privacyLevel.value)
  try {
    const response = await fetch('/api/v1/tasks', { method: 'POST', body: form })
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.detail || payload.message || '处理失败')
    result.value = payload
    await loadHistory()
  } catch (err) {
    error.value = err.message || '处理失败，请检查后端和 Python 服务是否启动'
  } finally {
    loading.value = false
  }
}

onMounted(loadHistory)
</script>

<template>
  <main class="page-shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">FACE PRIVACY WORKBENCH</p>
        <h1>人脸图像隐私脱敏</h1>
        <p class="subtitle">上传一张图片，先验证检测、局部处理与评价指标的完整链路。</p>
      </div>
      <span class="status-chip"><i></i> 原型环境</span>
    </header>

    <section class="workspace">
      <div class="control-panel">
        <div class="section-label">处理配置</div>
        <label class="upload-box" :class="{ selected: file }">
          <input type="file" accept="image/png,image/jpeg,image/webp" @change="onFileChange" />
          <span class="upload-icon">↑</span>
          <strong>{{ file ? file.name : '选择人脸图片' }}</strong>
          <small>{{ file ? `${(file.size / 1024 / 1024).toFixed(2)} MB` : 'PNG、JPG 或 WebP，最大 10 MB' }}</small>
        </label>

        <div class="field-grid">
          <label>
            <span>脱敏策略</span>
            <select v-model="strategy">
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
        </div>

        <button class="primary-button" :disabled="loading" @click="processImage">
          {{ loading ? '处理中…' : '开始脱敏' }}
          <span aria-hidden="true">→</span>
        </button>
        <p v-if="error" class="error-message">{{ error }}</p>
        <p class="helper">当前版本使用固定策略完成最小链路验证，Agent 工具编排将在下一阶段接入。</p>
      </div>

      <div class="result-panel">
        <div class="result-heading">
          <div>
            <div class="section-label">处理结果</div>
            <h2>{{ result ? '脱敏图像已生成' : '等待提交图片' }}</h2>
          </div>
          <span v-if="result" class="result-badge">{{ result.faceCount }} 张人脸</span>
        </div>

        <div v-if="result" class="result-content">
          <div class="image-frame"><img :src="resultImage" alt="脱敏结果" /></div>
          <div class="result-actions">
            <span class="file-name">{{ result.originalFilename }}</span>
            <button class="secondary-button" type="button" @click="downloadResult">下载脱敏图</button>
          </div>
          <div class="metric-grid">
            <div><span>策略</span><strong>{{ labelForStrategy(result.strategy) }}</strong></div>
            <div><span>隐私等级</span><strong>{{ labelForPrivacy(result.privacyLevel) }}</strong></div>
            <div><span>PSNR</span><strong>{{ result.metrics.psnr.toFixed(2) }} dB</strong></div>
            <div><span>SSIM</span><strong>{{ result.metrics.ssim.toFixed(4) }}</strong></div>
            <div><span>耗时</span><strong>{{ result.metrics.elapsedMs }} ms</strong></div>
          </div>
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
            <small>{{ formatDate(task.createdAt) }} · {{ labelForStrategy(task.strategy) }}</small>
          </span>
          <span class="history-metric">{{ task.metrics ? `${task.metrics.ssim.toFixed(4)} SSIM` : task.status }}</span>
          <span class="history-arrow" aria-hidden="true">→</span>
        </button>
      </div>
      <p v-else class="history-empty">完成第一张图片后，任务记录会保存在这里。</p>
    </section>
  </main>
</template>
