<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const files = ref([])
const auth = ref(null)
const loginForm = ref({ username: 'student', password: 'student123' })
const loginLoading = ref(false)
const loginError = ref('')
const strategy = ref('mosaic')
const privacyLevel = ref('standard')
const priority = ref('normal')
const loading = ref(false)
const error = ref('')
const result = ref(null)
const history = ref([])
const historyLoading = ref(false)
const comparisonResults = ref([])
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
const pollingMode = ref('single')
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

const roleLabels = {
  ADMIN: '管理员',
  USER: '普通用户',
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

const strategyTotal = computed(() => strategyUsage.value.reduce((total, item) => total + item.value, 0))

const completedComparisons = computed(() => comparisonResults.value.filter((task) => task.status === 'COMPLETED' && task.metrics))

const comparisonSummary = computed(() => {
  const tasks = completedComparisons.value
  if (!tasks.length) return null
  const highestFidelity = tasks.reduce((best, task) => task.metrics.ssim > best.metrics.ssim ? task : best)
  const strongestVisualChange = tasks.reduce((best, task) => task.metrics.ssim < best.metrics.ssim ? task : best)
  const fastest = tasks.reduce((best, task) => task.metrics.elapsedMs < best.metrics.elapsedMs ? task : best)
  const highestSuppression = tasks.reduce((best, task) => {
    const currentRate = task.privacyEvaluation?.detectionSuppressionRate ?? -1
    const bestRate = best.privacyEvaluation?.detectionSuppressionRate ?? -1
    return currentRate > bestRate ? task : best
  })
  return { highestFidelity, strongestVisualChange, fastest, highestSuppression }
})

function onFileChange(event) {
  files.value = Array.from(event.target.files || []).slice(0, 20)
  error.value = ''
  result.value = null
  comparisonResults.value = []
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

function strategyPercent(value) {
  return strategyTotal.value ? Math.round((value / strategyTotal.value) * 100) : 0
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (character) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[character]))
}

function roleLabel(value) {
  return roleLabels[value] || value
}

function clearAuth() {
  auth.value = null
  sessionStorage.removeItem('facePrivacySession')
  stopPolling()
  history.value = []
  result.value = null
  comparisonResults.value = []
}

async function apiFetch(input, options = {}) {
  const headers = new Headers(options.headers || {})
  if (auth.value?.token) {
    headers.set('Authorization', `Bearer ${auth.value.token}`)
  }
  const response = await fetch(input, { ...options, headers })
  if (response.status === 401 && auth.value) {
    clearAuth()
    loginError.value = '登录已过期，请重新登录'
  }
  return response
}

async function login() {
  loginLoading.value = true
  loginError.value = ''
  try {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(loginForm.value),
    })
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || '登录失败')
    auth.value = { token: payload.token, ...payload.user }
    sessionStorage.setItem('facePrivacySession', JSON.stringify(auth.value))
    await loadHistory()
  } catch (err) {
    loginError.value = err.message || '登录失败，请检查账号和密码'
  } finally {
    loginLoading.value = false
  }
}

async function initializeAuth() {
  const saved = sessionStorage.getItem('facePrivacySession')
  if (!saved) return
  try {
    const session = JSON.parse(saved)
    const response = await fetch('/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${session.token}` },
    })
    if (!response.ok) throw new Error('session expired')
    const user = await response.json()
    auth.value = { token: session.token, ...user }
    await loadHistory()
  } catch {
    sessionStorage.removeItem('facePrivacySession')
  }
}

async function logout() {
  if (auth.value?.token) {
    await fetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${auth.value.token}` },
    })
  }
  clearAuth()
}

async function loadStatistics() {
  try {
    const response = await apiFetch('/api/v1/tasks/stats')
    if (!response.ok) throw new Error('统计数据加载失败')
    statistics.value = await response.json()
  } catch (err) {
    error.value = err.message || '统计数据加载失败'
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const response = await apiFetch('/api/v1/tasks')
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
  comparisonResults.value = []
  try {
    const response = await apiFetch(`/api/v1/tasks/${task.id}`)
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
      const response = await apiFetch(`/api/v1/tasks/${id}`)
      if (!response.ok) throw new Error('任务状态查询失败')
      return response.json()
    }))
    if (pollingMode.value === 'comparison') {
      comparisonResults.value = tasks
    }
    if (tasks[0]) result.value = tasks[0]
    await loadHistory()
    if (tasks.every((task) => ['COMPLETED', 'FAILED'].includes(task.status))) stopPolling()
  } catch (err) {
    error.value = err.message || '任务状态查询失败'
    stopPolling()
  }
}

function startPolling(ids, mode = 'single') {
  stopPolling()
  pollingIds.value = ids
  pollingMode.value = mode
  pollTasks()
  pollingTimer = window.setInterval(pollTasks, 900)
}

async function retryTask() {
  if (!result.value?.id) return
  loading.value = true
  error.value = ''
  try {
    const response = await apiFetch(`/api/v1/tasks/${result.value.id}/retry`, { method: 'POST' })
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

async function compareStrategies() {
  if (files.value.length !== 1) {
    error.value = '策略对比实验需要只选择一张图片'
    return
  }
  stopPolling()
  loading.value = true
  error.value = ''
  result.value = null
  comparisonResults.value = []
  const strategies = ['blur', 'mosaic', 'cover']
  try {
    const submissions = await Promise.all(strategies.map(async (selectedStrategy) => {
      const form = new FormData()
      form.append('file', files.value[0])
      form.append('strategy', selectedStrategy)
      form.append('privacyLevel', privacyLevel.value)
      form.append('priority', 'high')
      const response = await apiFetch('/api/v1/tasks', { method: 'POST', body: form })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload.message || `${labelForStrategy(selectedStrategy)}提交失败`)
      return payload
    }))
    comparisonResults.value = submissions
    result.value = submissions[0]
    await loadHistory()
    startPolling(submissions.map((task) => task.id), 'comparison')
  } catch (err) {
    error.value = err.message || '对比实验提交失败'
  } finally {
    loading.value = false
  }
}

function downloadComparisonCsv() {
  if (!comparisonResults.value.length) return
  const rows = [
    ['任务编号', '策略', '隐私等级', '人脸数', 'PSNR(dB)', 'SSIM', '耗时(ms)', '检测抑制率', '状态'],
    ...comparisonResults.value.map((task) => [
      task.id,
      labelForStrategy(task.appliedStrategy || task.strategy),
      labelForPrivacy(task.privacyLevel),
      task.faceCount ?? '',
      task.metrics?.psnr?.toFixed(4) ?? '',
      task.metrics?.ssim?.toFixed(6) ?? '',
      task.metrics?.elapsedMs ?? '',
      task.privacyEvaluation ? `${(task.privacyEvaluation.detectionSuppressionRate * 100).toFixed(2)}%` : '',
      labelForStatus(task.status),
    ]),
  ]
  const csv = `\ufeff${rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\n')}`
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'face-privacy-strategy-comparison.csv'
  link.click()
  URL.revokeObjectURL(url)
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

function openReport() {
  if (result.value?.status !== 'COMPLETED' || !resultImage.value) return
  const task = result.value
  const metrics = task.metrics || {}
  const evaluation = task.privacyEvaluation || {}
  const traceRows = agentTrace.value.map((step) => `
    <tr><td>${escapeHtml(step.step)}</td><td>${escapeHtml(step.action)}</td><td>${escapeHtml(step.thought)}</td><td>${escapeHtml(formatObservation(step.observation))}</td></tr>
  `).join('')
  const reportWindow = window.open('', '_blank')
  if (!reportWindow) {
    error.value = '浏览器阻止了报告窗口，请允许当前页面打开新窗口'
    return
  }
  reportWindow.document.write(`<!doctype html>
    <html lang="zh-CN"><head><meta charset="UTF-8"><title>人脸图像隐私脱敏处理报告</title>
    <style>
      *{box-sizing:border-box}body{margin:0;color:#1c2730;background:#f3f5f7;font:14px/1.6 Arial,"Microsoft YaHei",sans-serif}
      .page{max-width:900px;margin:0 auto;padding:38px;background:#fff;min-height:100vh}.head{display:flex;justify-content:space-between;gap:20px;border-bottom:2px solid #206e74;padding-bottom:22px}
      h1{margin:0 0 5px;font-size:26px}.muted{color:#6e7a84;font-size:12px}.tag{color:#206e74;background:#edf8f5;border:1px solid #ccebe3;border-radius:20px;padding:5px 10px;height:max-content;white-space:nowrap}
      h2{font-size:17px;margin:26px 0 10px;color:#206e74}.preview{display:flex;justify-content:center;background:#20272d;border-radius:6px;padding:18px;margin-top:16px}.preview img{max-width:100%;max-height:410px;object-fit:contain}
      table{width:100%;border-collapse:collapse}th,td{text-align:left;border:1px solid #e0e6e9;padding:9px;vertical-align:top}th{width:18%;background:#f6f8f9;color:#56636d;font-weight:600}.trace th{width:auto}.trace td{font-size:12px}.note{padding:12px 14px;background:#f7fbfa;border-left:3px solid #2d7c83;color:#586771;font-size:12px}
      .footer{margin-top:32px;padding-top:14px;border-top:1px solid #e5eaed;color:#89949e;font-size:11px}@media print{body{background:#fff}.page{padding:0;max-width:none}.preview{break-inside:avoid}.trace{font-size:11px}}
    </style></head><body><main class="page">
      <header class="head"><div><div class="muted">FACE PRIVACY WORKBENCH · 处理报告</div><h1>人脸图像隐私脱敏处理报告</h1><div class="muted">生成时间：${escapeHtml(new Date().toLocaleString('zh-CN', { hour12: false }))}</div></div><span class="tag">${escapeHtml(labelForStatus(task.status))}</span></header>
      <h2>一、处理结果</h2><div class="preview"><img src="${resultImage.value}" alt="脱敏结果"></div>
      <table><tbody><tr><th>任务编号</th><td>${escapeHtml(task.id)}</td><th>原文件名</th><td>${escapeHtml(task.originalFilename)}</td></tr>
      <tr><th>实际策略</th><td>${escapeHtml(labelForStrategy(task.appliedStrategy || task.strategy))}</td><th>隐私等级</th><td>${escapeHtml(labelForPrivacy(task.privacyLevel))}</td></tr>
      <tr><th>检测人脸数</th><td>${escapeHtml(task.faceCount)}</td><th>处理耗时</th><td>${escapeHtml(metrics.elapsedMs)} ms</td></tr>
      <tr><th>PSNR</th><td>${escapeHtml(Number(metrics.psnr).toFixed(4))} dB</td><th>SSIM</th><td>${escapeHtml(Number(metrics.ssim).toFixed(6))}</td></tr>
      <tr><th>处理后检测数</th><td>${escapeHtml(evaluation.detectedFacesAfter ?? '—')}</td><th>检测抑制率</th><td>${evaluation.detectionSuppressionRate == null ? '—' : `${(evaluation.detectionSuppressionRate * 100).toFixed(2)}%`}</td></tr></tbody></table>
      <h2>二、指标口径</h2><p class="note">PSNR 与 SSIM 用于衡量脱敏后的图像保真度；检测抑制率是基于固定人脸检测器的辅助对比指标，不等同于人脸识别准确率或隐私保护的充分证明。原始人脸图片仅在处理期间驻留内存，系统未将原图写入数据库。</p>
      ${traceRows ? `<h2>三、Agent 工具轨迹</h2><table class="trace"><thead><tr><th>步骤</th><th>工具</th><th>决策说明</th><th>观测结果</th></tr></thead><tbody>${traceRows}</tbody></table>` : ''}
      <div class="footer">本报告由人脸图像隐私脱敏与效果评估系统生成，适用于课程设计与实验记录。</div>
    </main></body></html>`)
  reportWindow.document.close()
  reportWindow.onload = () => window.setTimeout(() => reportWindow.print(), 280)
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
  comparisonResults.value = []
  const form = new FormData()
  const isBatch = files.value.length > 1
  const endpoint = isBatch ? '/api/v1/tasks/batch' : '/api/v1/tasks'
  const field = isBatch ? 'files' : 'file'
  files.value.forEach((selectedFile) => form.append(field, selectedFile))
  form.append('strategy', strategy.value)
  form.append('privacyLevel', privacyLevel.value)
  form.append('priority', priority.value)
  try {
    const response = await apiFetch(endpoint, { method: 'POST', body: form })
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

onMounted(initializeAuth)
onUnmounted(stopPolling)
</script>

<template>
  <div class="app-root">
    <section v-if="!auth" class="login-screen">
      <div class="login-panel">
        <div class="brand-line">
          <span class="brand-mark">FP</span>
          <p class="eyebrow">FACE PRIVACY WORKBENCH</p>
        </div>
        <h1>进入隐私脱敏工作台</h1>
        <p class="login-subtitle">登录后管理人脸图片处理任务与实验记录。</p>
        <form class="login-form" @submit.prevent="login">
          <label>
            <span>用户名</span>
            <input v-model="loginForm.username" autocomplete="username" placeholder="输入用户名" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="输入密码" />
          </label>
          <p v-if="loginError" class="login-error">{{ loginError }}</p>
          <button class="primary-button login-button" type="submit" :disabled="loginLoading">
            {{ loginLoading ? '登录中…' : '进入系统' }}
            <span aria-hidden="true">→</span>
          </button>
        </form>
        <div class="demo-account">
          <span>演示账号</span>
          <code>student / student123</code>
          <code>admin / admin123</code>
        </div>
      </div>
    </section>

  <main v-else class="page-shell">
    <header class="topbar">
      <div>
        <div class="brand-line">
          <span class="brand-mark">FP</span>
          <p class="eyebrow">FACE PRIVACY WORKBENCH</p>
        </div>
        <h1>人脸图像隐私脱敏</h1>
        <p class="subtitle">从人脸检测到策略决策，再到效果评估的智能处理工作台。</p>
      </div>
      <div class="topbar-actions">
        <span class="status-chip"><i></i> 答辩演示模式</span>
        <span class="user-chip">{{ auth.username }} · {{ roleLabel(auth.role) }}</span>
        <button class="logout-button" type="button" @click="logout">退出登录</button>
      </div>
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
        <span class="overview-label">平均 PSNR</span>
        <strong>{{ statistics.averagePsnr ? statistics.averagePsnr.toFixed(2) : '—' }}</strong>
        <small>像素误差指标 dB</small>
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
        <button v-if="files.length === 1" class="compare-button" type="button" :disabled="loading" @click="compareStrategies">
          <span>对比三种脱敏策略</span>
          <span aria-hidden="true">↗</span>
        </button>
        <p v-if="error" class="error-message">{{ error }}</p>
        <p class="helper">原图仅在处理期间驻留内存，不写入数据库；系统保存脱敏结果、指标和工具轨迹。</p>
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
            <div class="result-action-buttons">
              <button class="secondary-button" type="button" @click="openReport">打印/保存报告</button>
              <button class="secondary-button" type="button" @click="downloadResult">下载脱敏图</button>
            </div>
          </div>
          <div class="metric-grid">
            <div><span>策略</span><strong>{{ labelForStrategy(result.appliedStrategy || result.strategy) }}</strong></div>
            <div><span>隐私等级</span><strong>{{ labelForPrivacy(result.privacyLevel) }}</strong></div>
            <div><span>PSNR</span><strong>{{ result.metrics.psnr.toFixed(2) }} dB</strong></div>
            <div><span>SSIM</span><strong>{{ result.metrics.ssim.toFixed(4) }}</strong></div>
            <div><span>耗时</span><strong>{{ result.metrics.elapsedMs }} ms</strong></div>
            <div><span>检测抑制率</span><strong>{{ result.privacyEvaluation ? `${(result.privacyEvaluation.detectionSuppressionRate * 100).toFixed(1)}%` : '—' }}</strong></div>
          </div>
          <p v-if="result.privacyEvaluation" class="privacy-note">检测器辅助指标：处理前 {{ result.faceCount }} 张，处理后 {{ result.privacyEvaluation.detectedFacesAfter }} 张。该指标用于对比实验，不等同于人脸识别准确率。</p>
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

    <section v-if="comparisonResults.length" class="comparison-section">
      <div class="comparison-heading">
        <div>
          <div class="section-label">对比实验</div>
          <h2>同一图片的策略效果对比</h2>
          <p>固定输入图片和隐私等级，比较三种局部脱敏策略的图像质量指标与处理结果。</p>
        </div>
        <button class="secondary-button" type="button" @click="downloadComparisonCsv">导出实验数据</button>
      </div>
      <div class="comparison-grid">
        <article v-for="task in comparisonResults" :key="task.id" class="comparison-card">
          <div class="comparison-card-heading">
            <strong>{{ labelForStrategy(task.appliedStrategy || task.strategy) }}</strong>
            <span :class="['mini-status', task.status.toLowerCase()]">{{ labelForStatus(task.status) }}</span>
          </div>
          <div v-if="task.imageBase64" class="comparison-image">
            <img :src="`data:image/jpeg;base64,${task.imageBase64}`" :alt="`${labelForStrategy(task.strategy)}结果`" />
          </div>
          <div v-else class="comparison-loading">等待处理结果</div>
          <div v-if="task.metrics" class="comparison-metrics">
            <span><small>PSNR</small><strong>{{ task.metrics.psnr.toFixed(2) }}</strong></span>
            <span><small>SSIM</small><strong>{{ task.metrics.ssim.toFixed(4) }}</strong></span>
            <span><small>耗时</small><strong>{{ task.metrics.elapsedMs }} ms</strong></span>
            <span><small>检测抑制</small><strong>{{ task.privacyEvaluation ? `${(task.privacyEvaluation.detectionSuppressionRate * 100).toFixed(1)}%` : '—' }}</strong></span>
          </div>
        </article>
      </div>
      <div v-if="comparisonSummary" class="comparison-insight">
        <div>
          <div class="section-label">实验观察</div>
          <h3>同一输入下的可解释结论</h3>
        </div>
        <div class="insight-grid">
          <div><span>保真度最高</span><strong>{{ labelForStrategy(comparisonSummary.highestFidelity.appliedStrategy) }}</strong><small>SSIM {{ comparisonSummary.highestFidelity.metrics.ssim.toFixed(4) }}</small></div>
          <div><span>视觉改动最大</span><strong>{{ labelForStrategy(comparisonSummary.strongestVisualChange.appliedStrategy) }}</strong><small>SSIM {{ comparisonSummary.strongestVisualChange.metrics.ssim.toFixed(4) }}</small></div>
          <div><span>处理最快</span><strong>{{ labelForStrategy(comparisonSummary.fastest.appliedStrategy) }}</strong><small>{{ comparisonSummary.fastest.metrics.elapsedMs }} ms</small></div>
          <div><span>检测抑制最高</span><strong>{{ labelForStrategy(comparisonSummary.highestSuppression.appliedStrategy) }}</strong><small>{{ comparisonSummary.highestSuppression.privacyEvaluation ? `${(comparisonSummary.highestSuppression.privacyEvaluation.detectionSuppressionRate * 100).toFixed(1)}%` : '—' }}（辅助指标）</small></div>
        </div>
      </div>
      <p class="comparison-note">实验结论只描述本次固定输入和固定检测器下的观测结果，不将 PSNR、SSIM 或检测抑制率直接解释为人脸识别安全证明。</p>
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
        <div class="distribution-list">
          <div v-for="item in strategyUsage" :key="`${item.key}-bar`" class="distribution-row">
            <div class="distribution-meta"><span>{{ item.label }}</span><strong>{{ strategyPercent(item.value) }}%</strong></div>
            <div class="distribution-track"><i :class="`strategy-fill ${item.key}`" :style="{ width: `${Math.max(strategyPercent(item.value), 4)}%` }"></i></div>
          </div>
        </div>
      </div>
      <p v-if="!history.length" class="history-empty">完成第一张图片后，任务记录会保存在这里。</p>
    </section>

    <section class="method-section">
      <div class="section-label">答辩口径</div>
      <div class="method-grid">
        <div><strong>受限 ReAct 编排</strong><span>工具链和策略范围由系统控制，保证本地复现与失败兜底。</span></div>
        <div><strong>图像保真度评价</strong><span>PSNR、SSIM 反映处理前后视觉变化，不代表隐私安全强度。</span></div>
        <div><strong>辅助隐私指标</strong><span>检测抑制率用于固定检测器下的策略对比，不能替代人脸识别攻击实验。</span></div>
        <div><strong>数据留存边界</strong><span>原图不入库，系统仅保存脱敏结果、任务指标和 Agent 轨迹。</span></div>
      </div>
    </section>
  </main>
  </div>
</template>
