<template>
  <div class="container">
    <div class="page-header">
      <h1>B站视频数据统计</h1>
      <router-link to="/">
        <button class="back-btn">返回首页</button>
      </router-link>
    </div>
    
    <div class="control-panel">
      <div class="checkbox-group">
        <label><strong>选择分区：</strong></label>
        <div class="checkbox-container">
          <label v-for="zone in biliZones" :key="zone.id" class="checkbox-label">
            <input 
              type="checkbox" 
              :value="zone.id" 
              v-model="selectedZones"
              @change="refreshData"
            />
            {{ zone.name }}
          </label>
        </div>
      </div>
      
      <div class="select-group">
        <label for="sortField">排序字段：</label>
        <select id="sortField" v-model="sortField" @change="refreshData">
          <option value="playCount">播放量</option>
          <option value="likeCount">点赞数</option>
          <option value="danmukuCount">弹幕数</option>
        </select>
      </div>
      
      <div class="select-group">
        <label for="limit">显示数量：</label>
        <select id="limit" v-model="limit" @change="refreshData">
          <option :value="5">5</option>
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
      </div>
      
      <div class="button-group">
        <button :disabled="loading" @click="refreshData">
          {{ loading ? '刷新中...' : '刷新数据' }}
        </button>
        <button :disabled="loading" @click="toggleDistributionChart">
          {{ showDistributionChart ? '隐藏视频数量分布' : '查看视频数量分布' }}
        </button>
      </div>
      
      <div v-if="showDistributionChart" class="distribution-controls">
        <div class="select-group">
          <label for="distributionZone">选择分区：</label>
          <select id="distributionZone" v-model="distributionZone" @change="loadDistributionData">
            <option :value="zone.id" v-for="zone in biliZones" :key="zone.id">{{ zone.name }}</option>
          </select>
        </div>
        
        <div class="select-group">
          <label for="distributionMetric">选择指标：</label>
          <select id="distributionMetric" v-model="distributionMetric" @change="loadDistributionData">
            <option value="playCount">播放量</option>
            <option value="likeCount">点赞数</option>
            <option value="danmukuCount">弹幕数</option>
          </select>
        </div>
        
        <div class="select-group">
          <label for="distributionInterval">选择梯度：</label>
          <div class="interval-input-group">
            <input 
              type="number" 
              id="distributionInterval" 
              v-model.number="distributionInterval" 
              @change="loadDistributionData"
              min="1"
              style="width: 100px; margin-right: 10px;"
            />
            <select @change="setIntervalFromSelect" style="width: 100px;">
              <option value="100">100</option>
              <option value="1000">1000</option>
              <option value="5000">5000</option>
              <option value="10000">10000</option>
            </select>
          </div>
        </div>
      </div>
    </div>
    
    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    
    <div v-if="showDistributionChart" class="chart-container">
      <div ref="distributionChartRef" style="width: 100%; height: 400px;"></div>
    </div>
    
    <div class="quality-panel">
      <h2>视频质量评价</h2>
      <div class="quality-buttons">
        <button @click="showVideoQuality" :class="{ active: activeTab === 'quality' }">视频质量评分</button>
        <button @click="showHighLikeRate" :class="{ active: activeTab === 'likeRate' }">点赞率高的视频</button>
        <button @click="showHighDanmakuRate" :class="{ active: activeTab === 'danmakuRate' }">弹幕活跃度高的视频</button>
      </div>
    </div>
    
    <div v-if="activeTab !== 'default'" class="quality-content">
      <div v-if="qualityData.length > 0" class="quality-list">
        <div v-for="(item, index) in qualityData" :key="item.bvNum" class="quality-item">
          <div class="quality-rank">{{ index + 1 }}</div>
          <div class="quality-info">
            <div class="quality-title" @click="openVideoPage(item.bvNum)">{{ item.name }}</div>
            <div class="quality-up">UP主：{{ item.upName }}</div>
            <div class="quality-stats">
              <span v-if="activeTab === 'quality'">质量评分：{{ item.score }}</span>
              <span v-if="activeTab === 'likeRate'">点赞率：{{ item.likeRate }}%</span>
              <span v-if="activeTab === 'danmakuRate'">弹幕活跃度：{{ item.danmakuRate }}%</span>
            </div>
            <div class="quality-tags">
              <span v-if="activeTab === 'quality'" class="quality-tag">B站特色</span>
              <span v-if="activeTab === 'likeRate'" class="quality-tag">B站特色{{ formatNumber(item.playCount) }}播放{{ formatNumber(item.likeCount) }}赞</span>
              <span v-if="activeTab === 'danmakuRate'" class="quality-tag">观众弹幕较多</span>
            </div>
            <div class="quality-details">
              <span>播放：{{ formatNumber(item.playCount) }}</span>
              <span>点赞：{{ formatNumber(item.likeCount) }}</span>
              <span>弹幕：{{ formatNumber(item.danmukuCount) }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="no-data">暂无数据</div>
    </div>
    
    <div class="video-list">
      <div v-for="zone in zoneVideoData" :key="zone.pidV2" class="zone-section">
        <h3>{{ zone.zoneName }}</h3>
        <div v-if="zone.videos.length > 0" class="video-table">
          <table>
            <thead>
              <tr>
                <th>排名</th>
                <th>视频名称</th>
                <th>UP主</th>
                <th>播放量</th>
                <th>点赞数</th>
                <th>弹幕数</th>
                <th>时长</th>
                <th>发布时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(video, index) in zone.videos" :key="video.bvNum">
                <td>{{ index + 1 }}</td>
                <td><span class="video-link" @click="openVideoPage(video.bvNum)">{{ video.name }}</span></td>
                <td>{{ video.upName }}</td>
                <td>{{ formatNumber(video.playCount) }}</td>
                <td>{{ formatNumber(video.likeCount) }}</td>
                <td>{{ formatNumber(video.danmukuCount) }}</td>
                <td>{{ formatDuration(video.duration) }}</td>
                <td>{{ formatTime(video.publishTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="no-data">该分区数据库内暂无数据</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { biliApi } from '@/api'

const biliZones = [
  { name: "生活", id: "160" },
  { name: "游戏", id: "4" },
  { name: "娱乐", id: "5" },
  { name: "知识", id: "36" },
  { name: "影视", id: "181" },
  { name: "音乐", id: "3" },
  { name: "动画", id: "1" },
  { name: "时尚", id: "155" },
  { name: "美食", id: "211" },
  { name: "汽车", id: "223" },
  { name: "运动", id: "234" },
  { name: "科技", id: "188" },
  { name: "动物圈", id: "217" },
  { name: "舞蹈", id: "129" },
  { name: "国创", id: "167" },
  { name: "鬼畜", id: "119" }
]

const oldBiliZones = {
  "160": 1009,
  "4": 1008,
  "5": 1002,
  "36": 1010,
  "181": 1001,
  "3": 1003,
  "1": 1005,
  "155": 1015,
  "211": 1020,
  "223": 1013,
  "234": 1018,
  "188": 1019,
  "217": 1024,
  "129": 1004,
  "167": 1022,
  "119": 1007
}

const selectedZones = ref(biliZones.map(z => z.id))
const sortField = ref('playCount')
const limit = ref(10)
const loading = ref(false)
const errorMessage = ref('')
const zoneVideoData = ref([])
const activeTab = ref('default')
const qualityData = ref([])

// 分布图表相关
const distributionChartRef = ref(null)
const showDistributionChart = ref(false)
const distributionZone = ref(biliZones[0].id)
const distributionMetric = ref('playCount')
const distributionInterval = ref('1000')
let distributionChart = null

const formatNumber = (num) => {
  if (!num) return 0
  if (num >= 100000000) {
    return (num / 100000000).toFixed(1) + '亿'
  } else if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toString()
}

const formatDuration = (seconds) => {
  if (!seconds) return '-'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  
  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  } else {
    return `${minutes}:${secs.toString().padStart(2, '0')}`
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  return time.substring(0, 16)
}

const openVideoPage = (bvNum) => {
  const videoUrl = 'https://www.bilibili.com/video/' + bvNum
  window.open(videoUrl, '_blank')
}

// 初始化分布图表
const initDistributionChart = () => {
  if (distributionChartRef.value) {
    try {
      distributionChart = echarts.init(distributionChartRef.value)
      distributionChart.setOption({
        title: {
          text: '视频数量分布',
          left: 'center'
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross',
            label: {
              backgroundColor: '#6a7985'
            }
          }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: [],
          axisLabel: {
            interval: 0,
            rotate: 45
          }
        },
        yAxis: {
          type: 'value',
          name: '视频数量'
        },
        dataZoom: [
          {
            type: 'slider',
            show: true,
            start: 0,
            end: 100
          },
          {
            type: 'inside',
            start: 0,
            end: 100
          }
        ],
        series: [{
          name: '视频数量',
          type: 'line',
          stack: 'Total',
          areaStyle: {
            opacity: 0.3
          },
          data: []
        }]
      })
    } catch (error) {
      console.error('初始化分布图表失败:', error)
      distributionChart = null
    }
  }
}

// 加载分布数据
const loadDistributionData = async () => {
  const pidV2 = oldBiliZones[distributionZone.value]
  if (!pidV2) return
  
  loading.value = true
  errorMessage.value = ''
  
  try {
    const res = await biliApi.getVideoDistribution({
      pidV2,
      metric: distributionMetric.value,
      interval: parseInt(distributionInterval.value)
    })
    
    if (res.data.code === 200) {
      const distribution = res.data.data
      if (distribution) {
        const labels = Object.keys(distribution)
        const values = Object.values(distribution)
        
        if (distributionChart) {
          let metricName = '播放量'
          if (distributionMetric.value === 'likeCount') {
            metricName = '点赞数'
          } else if (distributionMetric.value === 'danmukuCount') {
            metricName = '弹幕数'
          }
          
          distributionChart.setOption({
            title: {
              text: `${getZoneNameByPidV2(pidV2)}分区${metricName}分布`
            },
            xAxis: {
              data: labels
            },
            series: [{
              data: values
            }]
          })
        }
      }
    }
  } catch (error) {
    errorMessage.value = '查询失败：' + error.message
  } finally {
    loading.value = false
  }
}

// 切换分布图表显示/隐藏
const toggleDistributionChart = async () => {
  showDistributionChart.value = !showDistributionChart.value
  if (showDistributionChart.value) {
    // 等待 DOM 更新，确保图表容器已渲染
    await new Promise(resolve => setTimeout(resolve, 100))
    // 初始化图表
    initDistributionChart()
    // 加载数据
    if (distributionChart) {
      await loadDistributionData()
    } else {
      errorMessage.value = '图表初始化失败，请重试'
      showDistributionChart.value = false
    }
  }
}

// 处理窗口大小变化
const handleResize = () => {
  distributionChart?.resize()
}

// 从下拉框设置梯度值
const setIntervalFromSelect = (event) => {
  distributionInterval.value = parseInt(event.target.value)
  loadDistributionData()
}

const refreshData = async () => {
  const selectedZoneIds = selectedZones.value
    .map(id => oldBiliZones[id])
    .filter(id => id !== undefined)
  
  if (selectedZoneIds.length === 0) {
    errorMessage.value = '请至少选择一个分区'
    return
  }
  
  loading.value = true
  errorMessage.value = ''
  zoneVideoData.value = []
  
  try {
    const promises = selectedZoneIds.map(pidV2 => {
      return biliApi.getTopVideos({
        pidV2,
        sortField: sortField.value,
        limit: limit.value
      })
    })
    
    const results = await Promise.all(promises)
    
    results.forEach((res, index) => {
      if (res.data.code === 200) {
        const pidV2 = selectedZoneIds[index]
        const zoneName = getZoneNameByPidV2(pidV2)
        
        zoneVideoData.value.push({
          pidV2,
          zoneName,
          videos: res.data.data || []
        })
      }
    })
  } catch (error) {
    errorMessage.value = '查询失败：' + error.message
  } finally {
    loading.value = false
  }
}

const getZoneNameByPidV2 = (pidV2) => {
  for (const [newId, oldId] of Object.entries(oldBiliZones)) {
    if (oldId === pidV2) {
      const zone = biliZones.find(z => z.id === newId)
      if (zone) {
        return zone.name
      }
    }
  }
  return '未知分区'
}

const showVideoQuality = async () => {
  activeTab.value = 'quality'
  await loadQualityData('quality')
}

const showHighLikeRate = async () => {
  activeTab.value = 'likeRate'
  await loadQualityData('likeRate')
}

const showHighDanmakuRate = async () => {
  activeTab.value = 'danmakuRate'
  await loadQualityData('danmakuRate')
}

const loadQualityData = async (type) => {
  const selectedZoneIds = selectedZones.value
    .map(id => oldBiliZones[id])
    .filter(id => id !== undefined)
  
  if (selectedZoneIds.length === 0) {
    qualityData.value = []
    return
  }
  
  loading.value = true
  
  try {
    let apiMethod
    if (type === 'quality') {
      apiMethod = biliApi.getVideoQuality
    } else if (type === 'likeRate') {
      apiMethod = biliApi.getHighLikeRate
    } else {
      apiMethod = biliApi.getHighDanmakuRate
    }
    
    const promises = selectedZoneIds.map(pidV2 => {
      return apiMethod({
        pidV2,
        limit: limit.value
      })
    })
    
    const results = await Promise.all(promises)
    
    qualityData.value = []
    results.forEach(res => {
      if (res.data.code === 200 && res.data.data) {
        qualityData.value.push(...res.data.data)
      }
    })
    
    qualityData.value.sort((a, b) => {
      const scoreA = type === 'quality' ? a.score : (type === 'likeRate' ? a.likeRate : a.danmakuRate)
      const scoreB = type === 'quality' ? b.score : (type === 'likeRate' ? b.likeRate : b.danmakuRate)
      return scoreB - scoreA
    })
    
    qualityData.value = qualityData.value.slice(0, limit.value)
  } catch (error) {
    errorMessage.value = '查询失败：' + error.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  distributionChart?.dispose()
})
</script>

<style scoped>
.control-panel {
  margin-bottom: 30px;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 4px;
  border-left: 4px solid #00a1d6;
}

.checkbox-group {
  margin-bottom: 20px;
}

.checkbox-container {
  margin-top: 10px;
}

.checkbox-label {
  display: inline-block;
  margin-right: 15px;
  margin-bottom: 10px;
  font-weight: normal;
  color: #555;
}

.checkbox-label input {
  margin-right: 5px;
  width: auto;
}

.select-group {
  margin-bottom: 20px;
}

.select-group select {
  padding: 8px 12px;
  font-size: 16px;
  width: auto;
  min-width: 200px;
}

.button-group {
  margin-bottom: 20px;
}

.button-group button {
  padding: 10px 20px;
  font-size: 16px;
  background-color: #00a1d6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.button-group button:hover {
  background-color: #0085b3;
}

.button-group button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}

.error {
  color: #ff4d4f;
  padding: 10px;
  margin-bottom: 20px;
  background-color: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
}

.quality-panel {
  margin-bottom: 30px;
  padding: 20px;
  background-color: #f0f5ff;
  border-radius: 4px;
  border-left: 4px solid #1890ff;
}

.quality-panel h2 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #1890ff;
}

.quality-buttons {
  display: flex;
  gap: 10px;
}

.quality-buttons button {
  padding: 8px 16px;
  font-size: 14px;
  background-color: white;
  color: #1890ff;
  border: 1px solid #1890ff;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.quality-buttons button:hover {
  background-color: #e6f7ff;
}

.quality-buttons button.active {
  background-color: #1890ff;
  color: white;
}

.quality-content {
  margin-bottom: 30px;
}

.quality-list {
  display: grid;
  gap: 15px;
}

.quality-item {
  display: flex;
  padding: 15px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s;
}

.quality-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.quality-rank {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-weight: bold;
  font-size: 18px;
  border-radius: 50%;
  margin-right: 15px;
  flex-shrink: 0;
}

.quality-info {
  flex: 1;
}

.quality-title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 8px;
  color: #333;
  cursor: pointer;
  transition: color 0.3s;
}

.quality-title:hover {
  color: #1890ff;
}

.quality-up {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.quality-stats {
  margin-bottom: 8px;
}

.quality-stats span {
  display: inline-block;
  padding: 4px 8px;
  background-color: #f0f5ff;
  color: #1890ff;
  border-radius: 4px;
  font-size: 14px;
  font-weight: bold;
}

.quality-tags {
  margin-bottom: 8px;
}

.quality-tag {
  display: inline-block;
  padding: 4px 12px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: bold;
}

.quality-details {
  font-size: 14px;
  color: #666;
}

.quality-details span {
  margin-right: 15px;
}

.video-list {
  margin-top: 30px;
}

.zone-section {
  margin-bottom: 30px;
  padding: 20px;
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.zone-section h3 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #00a1d6;
  border-bottom: 2px solid #00a1d6;
  padding-bottom: 10px;
}

.video-table {
  overflow-x: auto;
}

.video-table table {
  width: 100%;
  border-collapse: collapse;
}

.video-table th,
.video-table td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid #e8e8e8;
}

.video-table th {
  background-color: #fafafa;
  font-weight: bold;
  color: #333;
}

.video-table tr:hover {
  background-color: #f5f5f5;
}

.video-link {
  color: #1890ff;
  cursor: pointer;
  text-decoration: none;
  transition: color 0.3s;
}

.video-link:hover {
  color: #096dd9;
  text-decoration: underline;
}

.no-data {
  padding: 20px;
  text-align: center;
  color: #999;
  background-color: #fafafa;
  border-radius: 4px;
}

.distribution-controls {
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f0f9ff;
  border-radius: 8px;
  border-left: 4px solid #40a9ff;
}

.chart-container {
  width: 100%;
  height: 400px;
  margin: 20px 0;
  border: 1px solid #ddd;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
</style>
