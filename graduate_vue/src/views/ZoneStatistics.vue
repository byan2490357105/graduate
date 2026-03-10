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
      </div>
    </div>
    
    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    
    <div class="chart-container">
      <div ref="chartRef" style="width: 100%; height: 100%;"></div>
    </div>
    
    <div class="chart-container">
      <div ref="countChartRef" style="width: 100%; height: 100%;"></div>
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
const selectedZones = ref(biliZones.map(z => z.id))
const dataType = ref('avgPlayCount')
const loading = ref(false)
const errorMessage = ref('')

let chart = null
let countChart = null

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
    const [statisticsRes, countRes] = await Promise.all([
      biliApi.getRegionStatistics({ pidV2List: selectedZoneIds }),
      biliApi.getDataCount({ pidV2List: selectedZoneIds })
    ])
    
    if (statisticsRes.data.code === 200) {
      const statisticsList = statisticsRes.data.data
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
    
    if (countRes.data.code === 200) {
      const countList = countRes.data.data
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
  } catch (error) {
    errorMessage.value = '查询失败：' + error.message
  } finally {
    loading.value = false
  }
}

const handleResize = () => {
  chart?.resize()
  countChart?.resize()
}

onMounted(() => {
  initChart()
  initCountChart()
  refreshData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  countChart?.dispose()
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
</style>
