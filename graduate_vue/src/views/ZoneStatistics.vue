<template>
  <div class="container">
    <div class="page-header">
      <h1>B站分区数据统计</h1>
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
        <label for="dataType">选择数据类型：</label>
        <select id="dataType" v-model="dataType" @change="refreshData">
          <option value="avgPlayCount">平均播放量</option>
          <option value="avgLikeCount">平均点赞数</option>
          <option value="avgDanmukuCount">平均弹幕数</option>
        </select>
      </div>
      
      <div class="button-group">
        <button :disabled="loading" @click="refreshData">
          {{ loading ? '刷新中...' : '刷新数据' }}
        </button>
        <button :disabled="loading" @click="toggleMainChart">
          {{ showMainChart ? '隐藏B站分区数据统计' : '显示B站分区数据统计' }}
        </button>
        <button :disabled="loading" @click="toggleCountChart">
          {{ showCountChart ? '隐藏数据库内数据条数' : '显示数据库内数据条数' }}
        </button>
        <button :disabled="loading" @click="toggleHeatmap">
          {{ showHeatmap ? '隐藏发布时间热力图' : '查看发布时间热力图' }}
        </button>
      </div>
    </div>
    
    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    
    <div v-if="showMainChart" class="chart-container">
      <div ref="chartRef" style="width: 100%; height: 100%;"></div>
    </div>
    
    <div v-if="showCountChart" class="chart-container">
      <div ref="countChartRef" style="width: 100%; height: 100%;"></div>
    </div>
    
    <div v-if="showHeatmap" class="chart-container">
      <div ref="heatmapRef" style="width: 100%; height: 100%;"></div>
    </div>
    
    <div v-if="showHeatmap && peakHourInfo.length > 0" class="peak-hour-info">
      <h3>发布时间高峰期分析</h3>
      <ul>
        <li v-for="info in peakHourInfo" :key="info.pidV2">
          {{ info.zoneName }}分区集中在{{ info.peakHour }}发布
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
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

const chartRef = ref(null)
const countChartRef = ref(null)
const heatmapRef = ref(null)
const selectedZones = ref(biliZones.map(z => z.id))
const dataType = ref('avgPlayCount')
const loading = ref(false)
const errorMessage = ref('')
const showMainChart = ref(true) // 默认显示B站分区数据统计
const showCountChart = ref(true) // 默认显示数据库内数据条数
const showHeatmap = ref(false)
const peakHourInfo = ref([])

let chart = null
let countChart = null
let heatmap = null

const initChart = () => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    chart.setOption({
      title: {
        text: 'B站分区数据统计',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLabel: {
          interval: 0,
          rotate: 30
        }
      },
      yAxis: {
        type: 'value',
        name: ''
      },
      series: [{
        name: '',
        type: 'bar',
        data: [],
        itemStyle: {
          color: '#00a1d6'
        }
      }]
    })
  }
}

const initCountChart = () => {
  if (countChartRef.value) {
    countChart = echarts.init(countChartRef.value)
    countChart.setOption({
      title: {
        text: '数据库内数据条数',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLabel: {
          interval: 0,
          rotate: 30
        }
      },
      yAxis: {
        type: 'value',
        name: '数据条数'
      },
      series: [{
        name: '数据条数',
        type: 'bar',
        data: [],
        itemStyle: {
          color: '#52c41a'
        }
      }]
    })
  }
}

const initHeatmap = () => {
  if (heatmapRef.value) {
    try {
      heatmap = echarts.init(heatmapRef.value)
      heatmap.setOption({
        title: {
          text: '各分区发布时间热力图',
          left: 'center'
        },
        tooltip: {
          position: 'top'
        },
        grid: {
          height: '50%',
          top: '10%'
        },
        xAxis: {
          type: 'category',
          data: ['00:00', '01:00', '02:00', '03:00', '04:00', '05:00', '06:00', '07:00', '08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00', '18:00', '19:00', '20:00', '21:00', '22:00', '23:00'],
          splitArea: {
            show: true
          }
        },
        yAxis: {
          type: 'category',
          data: [],
          splitArea: {
            show: true
          }
        },
        visualMap: {
          min: 0,
          max: 100,
          calculable: true,
          orient: 'horizontal',
          left: 'center',
          bottom: '5%',
          inRange: {
            color: ['#e0f2ff', '#bae6fd', '#7dd3fc', '#38bdf8', '#0ea5e9', '#0284c7', '#0369a1', '#075985', '#0c4a6e']
          }
        },
        series: [{
          name: '发布数量',
          type: 'heatmap',
          data: [],
          label: {
            show: true
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }]
      })
    } catch (error) {
      console.error('初始化热力图失败:', error)
      heatmap = null
    }
  } else {
    console.error('热力图容器未找到')
    heatmap = null
  }
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
  
  try {
    const promises = [
      biliApi.getRegionStatistics({ pidV2List: selectedZoneIds }),
      biliApi.getDataCount({ pidV2List: selectedZoneIds })
    ]
    
    if (showHeatmap.value) {
      promises.push(biliApi.getPublishTimeDistribution({ pidV2List: selectedZoneIds }))
    }
    
    const results = await Promise.all(promises)
    
    // 处理统计数据
    if (results[0].data.code === 200) {
      const statisticsList = results[0].data.data
      if (statisticsList && statisticsList.length > 0) {
        const zoneNames = []
        const values = []
        
        statisticsList.forEach(statistics => {
          const pidV2 = statistics.pidV2
          for (const [newId, oldId] of Object.entries(oldBiliZones)) {
            if (oldId === pidV2) {
              const zone = biliZones.find(z => z.id === newId)
              if (zone) {
                zoneNames.push(zone.name)
                values.push(statistics[dataType.value])
              }
              break
            }
          }
        })
        
        let yAxisName = ''
        let seriesName = ''
        switch (dataType.value) {
          case 'avgPlayCount':
            yAxisName = '平均播放量'
            seriesName = '平均播放量'
            break
          case 'avgLikeCount':
            yAxisName = '平均点赞数'
            seriesName = '平均点赞数'
            break
          case 'avgDanmukuCount':
            yAxisName = '平均弹幕数'
            seriesName = '平均弹幕数'
            break
        }
        
        chart.setOption({
          xAxis: { data: zoneNames },
          yAxis: { name: yAxisName },
          series: [{ name: seriesName, data: values }]
        })
      }
    }
    
    // 处理数据条数
    if (results[1].data.code === 200) {
      const countList = results[1].data.data
      if (countList && countList.length > 0) {
        const zoneNames = []
        const counts = []
        
        countList.forEach(countData => {
          const pidV2 = countData.pidV2
          for (const [newId, oldId] of Object.entries(oldBiliZones)) {
            if (oldId === pidV2) {
              const zone = biliZones.find(z => z.id === newId)
              if (zone) {
                zoneNames.push(zone.name)
                counts.push(countData.count)
              }
              break
            }
          }
        })
        
        countChart.setOption({
          xAxis: { data: zoneNames },
          series: [{ data: counts }]
        })
      }
    }
    
    // 处理发布时间分布
    if (showHeatmap.value && results.length > 2 && results[2].data.code === 200) {
      const distributionList = results[2].data.data
      if (distributionList && distributionList.length > 0) {
        const zoneNames = []
        const heatmapData = []
        const peakHourInfoList = []
        
        distributionList.forEach(distributionItem => {
          const pidV2 = distributionItem.pidV2
          const distribution = distributionItem.distribution
          const peakHour = distributionItem.peakHour || '无数据'
          
          // 找到对应的分区名称
          let zoneName = '未知分区'
          for (const [newId, oldId] of Object.entries(oldBiliZones)) {
            if (oldId === pidV2) {
              const zone = biliZones.find(z => z.id === newId)
              if (zone) {
                zoneName = zone.name
              }
              break
            }
          }
          
          zoneNames.push(zoneName)
          
          // 生成热力图数据
          const hours = ['00:00', '01:00', '02:00', '03:00', '04:00', '05:00', '06:00', '07:00', '08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00', '18:00', '19:00', '20:00', '21:00', '22:00', '23:00']
          hours.forEach((hour, index) => {
            const value = distribution[hour] || 0
            heatmapData.push([index, zoneNames.length - 1, value])
          })
          
          // 添加高峰期信息
          peakHourInfoList.push({
            pidV2,
            zoneName,
            peakHour
          })
        })
        
        // 更新高峰期信息
        peakHourInfo.value = peakHourInfoList
        
        // 确保热力图已初始化
        if (heatmap) {
          heatmap.setOption({
            yAxis: { data: zoneNames },
            series: [{
              data: heatmapData
            }]
          })
        } else {
          // 如果热力图未初始化，先初始化再设置数据
          initHeatmap()
          if (heatmap) {
            heatmap.setOption({
              yAxis: { data: zoneNames },
              series: [{
                data: heatmapData
              }]
            })
          }
        }
      }
    }
  } catch (error) {
    errorMessage.value = '查询失败：' + error.message
  } finally {
    loading.value = false
  }
}

const handleResize = () => {
  chart?.resize()
  countChart?.resize()
  heatmap?.resize()
}

const toggleMainChart = () => {
  showMainChart.value = !showMainChart.value
}

const toggleCountChart = () => {
  showCountChart.value = !showCountChart.value
}

const toggleHeatmap = async () => {
  showHeatmap.value = !showHeatmap.value
  if (showHeatmap.value) {
    // 等待 DOM 更新，确保热力图容器已渲染
    await new Promise(resolve => setTimeout(resolve, 100))
    // 初始化热力图
    initHeatmap()
    // 刷新数据以显示热力图
    if (heatmap) {
      await refreshData()
    } else {
      errorMessage.value = '热力图初始化失败，请重试'
      showHeatmap.value = false
    }
  }
}

onMounted(() => {
  initChart()
  initCountChart()
  initHeatmap() // 初始化热力图（默认隐藏）
  refreshData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  countChart?.dispose()
  heatmap?.dispose()
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

.chart-container {
  width: 100%;
  height: 600px;
  margin-top: 20px;
}

.peak-hour-info {
  margin: 20px 0;
  padding: 15px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background-color: #f9f9f9;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.peak-hour-info h3 {
  margin-top: 0;
  color: #333;
  font-size: 16px;
  font-weight: bold;
}

.peak-hour-info ul {
  list-style: none;
  padding: 0;
  margin: 10px 0 0 0;
}

.peak-hour-info li {
  padding: 5px 0;
  border-bottom: 1px solid #eee;
}

.peak-hour-info li:last-child {
  border-bottom: none;
}
</style>
