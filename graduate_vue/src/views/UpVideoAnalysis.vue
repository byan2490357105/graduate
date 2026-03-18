<template>
  <div class="container">
    <div class="page-header">
      <h1>UP主视频数据分析</h1>
      <router-link to="/">
        <button class="back-btn">返回首页</button>
      </router-link>
    </div>

    <div class="form-group">
      <label for="mid">UP主MID：</label>
      <input id="mid" v-model.number="mid" type="number" placeholder="请输入UP主的MID" />
    </div>

    <div class="button-group">
      <button :disabled="loading || isTaskRunning" @click="importVideoData">
        {{ loading ? '正在启动任务...' : (isTaskRunning ? '任务进行中...' : '录入UP主(MID)详细视频数据') }}
      </button>
      <button v-if="isTaskRunning" :disabled="loading" @click="terminateTask" class="terminate-btn">
        提前结束获取详细视频数据直接查看up主视频数据
      </button>
    </div>

    <div v-if="resultVisible" class="result">
      <h3>录入结果</h3>
      <p>{{ resultContent }}</p>
    </div>

    <div v-if="errorVisible" class="error">
      <h3>错误信息</h3>
      <p>{{ errorContent }}</p>
    </div>

    <div v-if="taskStatusVisible" class="task-status">
      <h3>任务状态</h3>
      <div class="status-info">
        <p><strong>状态：</strong>{{ taskStatus.running ? '进行中' : '已完成' }}</p>
        <p><strong>总BV号数：</strong>{{ taskStatus.totalBvNum }}</p>
        <p><strong>成功录入：</strong>{{ taskStatus.successCount }}</p>
        <p><strong>失败：</strong>{{ taskStatus.failCount }}</p>
        <p><strong>消息：</strong>{{ taskStatus.message }}</p>
        <p v-if="taskStatus.earlyTerminated" class="early-terminated">
          <strong>⚡ 提前终止：</strong>连续发现重复数据，后续数据已存在，已提前结束爬虫
        </p>
      </div>
      <div v-if="taskStatus.running" class="progress-bar">
        <div class="progress" :style="{ width: taskProgress + '%' }"></div>
      </div>
    </div>

    <div v-if="statisticsVisible" class="statistics-section">
      <h3>UP主视频统计数据</h3>
      <div class="statistics-cards">
        <div class="stat-card">
          <div class="stat-label">视频总数</div>
          <div class="stat-value">{{ statistics.videoCount }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均播放量</div>
          <div class="stat-value">{{ formatNumber(statistics.avgPlayCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均弹幕数</div>
          <div class="stat-value">{{ formatNumber(statistics.avgDanmakuCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均点赞数</div>
          <div class="stat-value">{{ formatNumber(statistics.avgLikeCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均投币数</div>
          <div class="stat-value">{{ formatNumber(statistics.avgCoinCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均收藏数</div>
          <div class="stat-value">{{ formatNumber(statistics.avgFavoriteCount) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">平均分享数</div>
          <div class="stat-value">{{ formatNumber(statistics.avgShareCount) }}</div>
        </div>
      </div>
    </div>

    <div v-if="monthlyCountVisible" class="monthly-section">
      <h3>月度视频发布数量 ({{ selectedYear }}年)</h3>
      <div class="year-selector">
        <label for="year">选择年份：</label>
        <select id="year" v-model.number="selectedYear" @change="loadMonthlyCount">
          <option v-for="year in availableYears" :key="year" :value="year">{{ year }}年</option>
        </select>
      </div>
      <div class="chart-container">
        <div class="bar-chart">
          <div v-for="(count, month) in monthlyCount" :key="month" class="bar-item">
            <div class="bar" :style="{ height: (count / maxMonthlyCount * 100) + '%' }">
              <span class="bar-value">{{ count }}</span>
            </div>
            <span class="bar-label">{{ month }}月</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="trendVisible" class="trend-section">
      <h3>年度数据趋势 ({{ selectedTrendYear }}年)</h3>
      <div class="year-selector">
        <label for="trendYear">选择年份：</label>
        <select id="trendYear" v-model.number="selectedTrendYear" @change="loadTrend">
          <option v-for="year in availableYears" :key="year" :value="year">{{ year }}年</option>
        </select>
      </div>
      <div class="trend-table">
        <table>
          <thead>
            <tr>
              <th>月份</th>
              <th>视频数</th>
              <th>播放量</th>
              <th>点赞数</th>
              <th>投币数</th>
              <th>收藏数</th>
              <th>弹幕数</th>
              <th>分享数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in trendData" :key="item.month">
              <td>{{ item.month }}月</td>
              <td>{{ item.videoCount }}</td>
              <td>{{ formatNumber(item.totalPlayCount) }}</td>
              <td>{{ formatNumber(item.totalLikeCount) }}</td>
              <td>{{ formatNumber(item.totalCoinCount) }}</td>
              <td>{{ formatNumber(item.totalFavoriteCount) }}</td>
              <td>{{ formatNumber(item.totalDanmakuCount) }}</td>
              <td>{{ formatNumber(item.totalShareCount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="chartVisible" class="chart-section">
      <h3>月度数据趋势图 ({{ selectedChartYear }}年)</h3>
      <div class="year-selector">
        <label for="chartYear">选择年份：</label>
        <select id="chartYear" v-model.number="selectedChartYear" @change="loadChartData">
          <option v-for="year in availableYears" :key="year" :value="year">{{ year }}年</option>
        </select>
      </div>
      <div class="metric-selector">
        <label>选择指标：</label>
        <button 
          v-for="metric in metrics" 
          :key="metric.value"
          :class="['metric-btn', { active: selectedMetric === metric.value }]"
          @click="selectedMetric = metric.value; loadChartData()"
        >
          {{ metric.label }}
        </button>
      </div>
      <div class="chart-container">
        <div class="line-chart">
          <div class="chart-header">
            <h4>{{ getMetricLabel(selectedMetric) }} 月度趋势</h4>
          </div>
          <div class="chart-content">
            <div class="chart-grid">
              <div class="grid-line" v-for="i in 5" :key="i" :style="{ top: (i * 20) + '%' }"></div>
              <div class="grid-label" v-for="i in 5" :key="'label-' + i" :style="{ top: (i * 20) + '%' }">{{ maxChartValue * (5 - i) / 5 }}</div>
            </div>
            <div class="chart-lines">
              <svg width="100%" height="100%" viewBox="0 0 1200 400">
                <polyline 
                  :points="chartDataPoints" 
                  fill="none" 
                  stroke="#1890ff" 
                  stroke-width="2"
                />
                <circle 
                  v-for="(point, index) in chartDataPointsArray" 
                  :key="index"
                  :cx="point.x" 
                  :cy="point.y" 
                  r="4" 
                  fill="#1890ff"
                />
                <text 
                  v-for="(point, index) in chartDataPointsArray" 
                  :key="'text-' + index"
                  :x="point.x" 
                  :y="point.y - 10" 
                  text-anchor="middle" 
                  fill="#666"
                  font-size="12"
                >
                  {{ point.value }}
                </text>
              </svg>
            </div>
            <div class="chart-labels">
              <div 
                v-for="(month, index) in months" 
                :key="month"
                class="month-label"
                :style="{ left: (index * 100 / (months.length - 1)) + '%' }"
              >
                {{ month }}月
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { biliApi } from '@/api'

const mid = ref('')
const loading = ref(false)
const resultVisible = ref(false)
const errorVisible = ref(false)
const resultContent = ref('')
const errorContent = ref('')

const isTaskRunning = ref(false)
const taskStatusVisible = ref(false)
const taskStatus = ref({
  running: false,
  totalBvNum: 0,
  successCount: 0,
  failCount: 0,
  message: '',
  earlyTerminated: false
})

const statisticsVisible = ref(false)
const statistics = ref({
  videoCount: 0,
  avgPlayCount: 0,
  avgDanmakuCount: 0,
  avgLikeCount: 0,
  avgCoinCount: 0,
  avgFavoriteCount: 0,
  avgShareCount: 0
})

const monthlyCountVisible = ref(false)
const monthlyCount = ref({})
const selectedYear = ref(new Date().getFullYear())

const trendVisible = ref(false)
const trendData = ref([])
const selectedTrendYear = ref(new Date().getFullYear())

// 图表相关
const chartVisible = ref(false)
const selectedChartYear = ref(new Date().getFullYear())
const selectedMetric = ref('likeCount')
const metrics = [
  { label: '平均点赞数', value: 'likeCount' },
  { label: '平均投币数', value: 'coinCount' },
  { label: '平均收藏数', value: 'favoriteCount' },
  { label: '平均播放量', value: 'playCount' },
  { label: '平均弹幕数', value: 'danmakuCount' },
  { label: '平均分享数', value: 'shareCount' }
]
const chartData = ref({})
const months = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

const currentYear = new Date().getFullYear()
const availableYears = computed(() => {
  const years = []
  for (let i = currentYear; i >= currentYear - 5; i--) {
    years.push(i)
  }
  return years
})

const maxMonthlyCount = computed(() => {
  const counts = Object.values(monthlyCount.value)
  return Math.max(...counts, 1)
})

const taskProgress = computed(() => {
  if (taskStatus.value.totalBvNum === 0) return 0
  const processed = taskStatus.value.successCount + taskStatus.value.failCount
  return Math.round((processed / taskStatus.value.totalBvNum) * 100)
})

const maxChartValue = computed(() => {
  const values = months.map(month => chartData.value[month] || 0)
  const max = Math.max(...values, 1)
  return Math.ceil(max * 1.1) // 留10%的余量
})

const chartDataPointsArray = computed(() => {
  const maxValue = maxChartValue.value
  return months.map((month, index) => {
    const value = chartData.value[month] || 0
    const x = (index / (months.length - 1)) * 1100 + 50
    const y = 350 - (value / maxValue) * 300
    return { x, y, value: Math.round(value) }
  })
})

const chartDataPoints = computed(() => {
  return chartDataPointsArray.value.map(point => `${point.x},${point.y}`).join(' ')
})

let statusCheckInterval = null

const formatNumber = (num) => {
  if (num === null || num === undefined) return '0'
  return Math.round(num).toLocaleString()
}

const getMetricLabel = (metric) => {
  const found = metrics.find(m => m.value === metric)
  return found ? found.label : metric
}

const showResult = (content) => {
  resultContent.value = content
  resultVisible.value = true
  errorVisible.value = false
}

const showError = (content) => {
  errorContent.value = content
  errorVisible.value = true
  resultVisible.value = false
}

const loadChartData = async () => {
  if (!mid.value) return

  try {
    const { data } = await biliApi.getYearlyTrend(mid.value, selectedChartYear.value)
    if (data.code === 200) {
      const trendData = data.data
      const newChartData = {}
      
      // 初始化所有月份为0
      months.forEach(month => {
        newChartData[month] = 0
      })
      
      // 填充数据
      trendData.forEach(item => {
        const month = item.month
        switch (selectedMetric.value) {
          case 'likeCount':
            newChartData[month] = item.videoCount > 0 ? item.totalLikeCount / item.videoCount : 0
            break
          case 'coinCount':
            newChartData[month] = item.videoCount > 0 ? item.totalCoinCount / item.videoCount : 0
            break
          case 'favoriteCount':
            newChartData[month] = item.videoCount > 0 ? item.totalFavoriteCount / item.videoCount : 0
            break
          case 'playCount':
            newChartData[month] = item.videoCount > 0 ? item.totalPlayCount / item.videoCount : 0
            break
          case 'danmakuCount':
            newChartData[month] = item.videoCount > 0 ? item.totalDanmakuCount / item.videoCount : 0
            break
          case 'shareCount':
            newChartData[month] = item.videoCount > 0 ? item.totalShareCount / item.videoCount : 0
            break
        }
      })
      
      chartData.value = newChartData
      chartVisible.value = true
    }
  } catch (error) {
    console.error('获取图表数据失败：', error)
  }
}

const startStatusCheck = () => {
  // 清除之前的定时器
  if (statusCheckInterval) {
    clearInterval(statusCheckInterval)
  }
  
  // 立即检查一次
  checkTaskStatus()
  
  // 每3秒检查一次状态
  statusCheckInterval = setInterval(async () => {
    await checkTaskStatus()
  }, 3000)
}

const checkTaskStatus = async () => {
  try {
    const { data } = await biliApi.getImportStatus(mid.value)
    if (data.code === 200) {
      taskStatus.value = data.data
      taskStatusVisible.value = true
      isTaskRunning.value = data.data.running
      
      // 如果任务完成，停止轮询并加载统计数据
      if (!data.data.running) {
        if (statusCheckInterval) {
          clearInterval(statusCheckInterval)
          statusCheckInterval = null
        }
        
        showResult(`录入完成！\n总BV号数: ${data.data.totalBvNum}\n成功录入: ${data.data.successCount}\n失败: ${data.data.failCount}`)
        
        await loadStatistics()
        await loadMonthlyCount()
        await loadTrend()
        await loadChartData()
      }
    }
  } catch (error) {
    console.error('获取任务状态失败：', error)
  }
}

const terminateTask = async () => {
  if (!mid.value) {
    showError('请输入UP主的MID')
    return
  }

  try {
    const { data } = await biliApi.terminateTask({ mid: mid.value })
    if (data.code === 200) {
      showResult('任务已成功提前结束，正在加载统计数据...')
      // 立即检查任务状态，确保轮询停止
      await checkTaskStatus()
    } else {
      showError(data.msg || '提前结束任务失败')
    }
  } catch (error) {
    let errorMessage = '提前结束任务失败：'
    if (error.response?.data?.msg) {
      errorMessage += error.response.data.msg
    } else if (error.message) {
      errorMessage += error.message
    } else {
      errorMessage += '未知错误'
    }
    showError(errorMessage)
  }
}

const importVideoData = async () => {
  if (!mid.value) {
    showError('请输入UP主的MID')
    return
  }

  loading.value = true
  resultVisible.value = false
  errorVisible.value = false
  statisticsVisible.value = false
  monthlyCountVisible.value = false
  trendVisible.value = false

  try {
    const { data } = await biliApi.importUpVideoData({ mid: mid.value })
    if (data.code === 200) {
      showResult('任务已启动，正在处理中...')
      isTaskRunning.value = true
      taskStatusVisible.value = true
      taskStatus.value = {
        running: true,
        totalBvNum: data.data.totalBvNum,
        successCount: 0,
        failCount: 0,
        message: '正在处理中...',
        earlyTerminated: false
      }
      // 开始轮询任务状态
      startStatusCheck()
    } else {
      showError(data.msg || '启动任务失败')
    }
  } catch (error) {
    let errorMessage = '启动任务失败：'
    if (error.response?.data?.msg) {
      errorMessage += error.response.data.msg
    } else if (error.message) {
      errorMessage += error.message
    } else {
      errorMessage += '未知错误'
    }
    showError(errorMessage)
  } finally {
    loading.value = false
  }
}

const loadStatistics = async () => {
  try {
    const { data } = await biliApi.getUpStatistics(mid.value)
    if (data.code === 200) {
      statistics.value = data.data
      statisticsVisible.value = true
    }
  } catch (error) {
    console.error('获取统计数据失败：', error)
  }
}

const loadMonthlyCount = async () => {
  try {
    const { data } = await biliApi.getMonthlyVideoCount(mid.value, selectedYear.value)
    if (data.code === 200) {
      monthlyCount.value = data.data
      monthlyCountVisible.value = true
    }
  } catch (error) {
    console.error('获取月度数据失败：', error)
  }
}

const loadTrend = async () => {
  try {
    const { data } = await biliApi.getYearlyTrend(mid.value, selectedTrendYear.value)
    if (data.code === 200) {
      trendData.value = data.data
      trendVisible.value = true
    }
  } catch (error) {
    console.error('获取趋势数据失败：', error)
  }
}

// 组件卸载时清除定时器
onUnmounted(() => {
  if (statusCheckInterval) {
    clearInterval(statusCheckInterval)
  }
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.back-btn {
  background-color: #1890ff;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.back-btn:hover {
  background-color: #40a9ff;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: inline-block;
  width: 100px;
  font-weight: 500;
}

.form-group input,
.form-group select {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  width: 300px;
}

.button-group {
  margin: 20px 0;
}

.button-group button {
  background-color: #1890ff;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.button-group button:hover:not(:disabled) {
  background-color: #40a9ff;
}

.button-group button:disabled {
  background-color: #d9d9d9;
  cursor: not-allowed;
}

.terminate-btn {
  background-color: #fa8c16 !important;
  margin-left: 10px;
}

.terminate-btn:hover:not(:disabled) {
  background-color: #faad14 !important;
}

.result,
.error {
  margin-top: 20px;
  padding: 15px;
  border-radius: 4px;
}

.result {
  background-color: #f6ffed;
  border: 1px solid #b7eb8f;
}

.error {
  background-color: #fff2f0;
  border: 1px solid #ffccc7;
}

.result h3,
.error h3 {
  margin-top: 0;
  color: #52c41a;
}

.error h3 {
  color: #ff4d4f;
}

.result p,
.error p {
  white-space: pre-wrap;
  margin: 0;
}

.task-status {
  margin-top: 20px;
  padding: 15px;
  background-color: #e6f7ff;
  border: 1px solid #91d5ff;
  border-radius: 4px;
}

.task-status h3 {
  margin-top: 0;
  color: #1890ff;
}

.status-info p {
  margin: 5px 0;
}

.early-terminated {
  color: #fa8c16;
  font-weight: 500;
  margin-top: 10px;
  padding: 8px;
  background-color: #fff7e6;
  border-left: 3px solid #fa8c16;
  border-radius: 2px;
}

.progress-bar {
  width: 100%;
  height: 20px;
  background-color: #f0f0f0;
  border-radius: 10px;
  overflow: hidden;
  margin-top: 10px;
}

.progress {
  height: 100%;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  transition: width 0.3s ease;
}

.statistics-section {
  margin-top: 30px;
}

.statistics-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 15px;
  margin-top: 15px;
}

.stat-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
}

.stat-card:nth-child(2) {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-card:nth-child(3) {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-card:nth-child(4) {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-card:nth-child(5) {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
}

.stat-card:nth-child(6) {
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
  color: #333;
}

.stat-card:nth-child(7) {
  background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%);
  color: #333;
}

.stat-label {
  font-size: 14px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
}

.monthly-section,
.trend-section {
  margin-top: 30px;
}

.year-selector {
  margin-bottom: 15px;
}

.year-selector label {
  margin-right: 10px;
  font-weight: 500;
}

.year-selector select {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
}

.chart-container {
  height: 300px;
  padding: 20px;
  background-color: #fafafa;
  border-radius: 8px;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 100%;
  gap: 10px;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  height: 100%;
}

.bar {
  width: 100%;
  background: linear-gradient(180deg, #1890ff 0%, #096dd9 100%);
  border-radius: 4px 4px 0 0;
  min-height: 20px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  transition: height 0.3s ease;
}

.bar-value {
  color: white;
  font-size: 12px;
  padding-top: 5px;
}

.bar-label {
  margin-top: 10px;
  font-size: 12px;
  color: #666;
}

.trend-table {
  overflow-x: auto;
}

.trend-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.trend-table th,
.trend-table td {
  padding: 12px;
  text-align: center;
  border: 1px solid #e8e8e8;
}

.trend-table th {
  background-color: #fafafa;
  font-weight: 600;
}

.trend-table tr:hover {
  background-color: #f5f5f5;
}

.chart-section {
  margin-top: 30px;
}

.metric-selector {
  margin: 15px 0;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.metric-selector label {
  font-weight: 500;
  margin-right: 10px;
  white-space: nowrap;
}

.metric-btn {
  background-color: #f0f0f0;
  border: 1px solid #d9d9d9;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s ease;
}

.metric-btn:hover {
  background-color: #e6f7ff;
  border-color: #91d5ff;
}

.metric-btn.active {
  background-color: #1890ff;
  color: white;
  border-color: #1890ff;
}

.line-chart {
  background-color: #fafafa;
  border-radius: 8px;
  padding: 20px;
  position: relative;
}

.chart-header {
  margin-bottom: 20px;
  text-align: center;
}

.chart-header h4 {
  margin: 0;
  color: #333;
}

.chart-content {
  position: relative;
  height: 400px;
}

.chart-grid {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background-color: #e8e8e8;
}

.grid-label {
  position: absolute;
  left: -60px;
  transform: translateY(-50%);
  font-size: 12px;
  color: #666;
}

.chart-lines {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.chart-labels {
  position: absolute;
  bottom: -30px;
  left: 0;
  right: 0;
  height: 30px;
}

.month-label {
  position: absolute;
  transform: translateX(-50%);
  font-size: 12px;
  color: #666;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .metric-selector {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .metric-selector label {
    margin-bottom: 10px;
  }
  
  .chart-container {
    overflow-x: auto;
  }
  
  .line-chart {
    min-width: 600px;
  }
}
</style>
