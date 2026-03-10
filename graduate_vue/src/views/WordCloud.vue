<template>
  <div class="container">
    <div class="page-header">
      <h1>词云展示</h1>
      <router-link to="/">
        <button class="back-btn">返回首页</button>
      </router-link>
    </div>
    
    <div class="btn-container">
      <button 
        id="tagBtn" 
        :class="{ active: currentType === 'tag' }"
        :disabled="loading"
        @click="generateWordCloud('tag')"
      >
        查看标签词云
      </button>
      <button 
        id="commentBtn" 
        :class="{ active: currentType === 'comment' }"
        :disabled="loading"
        @click="generateWordCloud('comment')"
      >
        查看评论词云
      </button>
    </div>
    
    <p class="tip">提示：评论数据量较大时，生成词云可能需要较长时间，请耐心等待</p>
    
    <div v-if="currentType" class="current-type">
      {{ currentTypeTitle }}
    </div>
    
    <div ref="wordcloudRef" class="wordcloud"></div>
    
    <div v-if="loading" class="loading">正在生成词云，请稍候...</div>
    
    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import 'echarts-wordcloud'
import { biliApi } from '@/api'

const wordcloudRef = ref(null)
const loading = ref(false)
const currentType = ref(null)
const errorMessage = ref('')

let chart = null

const currentTypeTitle = computed(() => {
  return currentType.value === 'tag' 
    ? '当前显示：视频标签词云' 
    : '当前显示：评论热词词云'
})

const generateWordCloud = async (type) => {
  loading.value = true
  errorMessage.value = ''
  currentType.value = null
  
  chart?.clear()
  
  try {
    let data
    if (type === 'tag') {
      const res = await biliApi.getTagsStatistics(100)
      data = res.data
    } else {
      const res = await biliApi.getCommentWordStatistics(150)
      data = res.data
    }
    
    const words = []
    for (const key in data) {
      words.push({
        name: key,
        value: data[key]
      })
    }
    
    if (words.length === 0) {
      errorMessage.value = '暂无数据'
      return
    }
    
    const colors = type === 'tag' 
      ? ['#4CAF50', '#8BC34A', '#CDDC39', '#FFEB3B', '#FFC107', '#FF9800', '#FF5722']
      : ['#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1', '#13c2c2', '#eb2f96']
    
    const option = {
      title: {
        text: type === 'tag' ? '视频标签词云' : '评论热词词云',
        left: 'center',
        top: 10,
        textStyle: {
          fontSize: 18,
          color: '#333'
        }
      },
      tooltip: {
        trigger: 'item',
        formatter: (params) => {
          return params.name + ': ' + params.value + (type === 'comment' ? '次' : '')
        }
      },
      series: [
        {
          type: 'wordCloud',
          shape: 'circle',
          left: 'center',
          top: 'center',
          width: '80%',
          height: '80%',
          right: null,
          bottom: null,
          sizeRange: [14, 68],
          rotationRange: [-45, 45],
          rotationStep: 45,
          gridSize: 8,
          drawOutOfBound: false,
          textStyle: {
            fontFamily: 'sans-serif',
            fontWeight: 'bold',
            color: () => {
              return colors[Math.floor(Math.random() * colors.length)]
            }
          },
          emphasis: {
            focus: 'self',
            textStyle: {
              shadowBlur: 10,
              shadowColor: '#333'
            }
          },
          data: words
        }
      ]
    }
    
    chart.setOption(option)
    currentType.value = type
  } catch (error) {
    console.error('获取数据失败:', error)
    errorMessage.value = '获取数据失败，请重试'
  } finally {
    loading.value = false
  }
}

const handleResize = () => {
  chart?.resize()
}

onMounted(() => {
  if (wordcloudRef.value) {
    chart = echarts.init(wordcloudRef.value)
    window.addEventListener('resize', handleResize)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped>
.btn-container {
  text-align: center;
  margin-bottom: 30px;
}

.btn-container button {
  padding: 12px 24px;
  font-size: 16px;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
  margin: 0 10px;
}

#tagBtn {
  background-color: #4CAF50;
}

#tagBtn:hover:not(:disabled) {
  background-color: #45a049;
}

#tagBtn.active {
  background-color: #388E3C;
  box-shadow: 0 0 5px rgba(0,0,0,0.3);
}

#commentBtn {
  background-color: #1890ff;
}

#commentBtn:hover:not(:disabled) {
  background-color: #40a9ff;
}

#commentBtn.active {
  background-color: #096dd9;
  box-shadow: 0 0 5px rgba(0,0,0,0.3);
}

.btn-container button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}

.tip {
  text-align: center;
  color: #999;
  font-size: 14px;
  margin-top: 10px;
}

.current-type {
  text-align: center;
  color: #666;
  font-size: 14px;
  margin-bottom: 10px;
}

.wordcloud {
  width: 100%;
  height: 600px;
  margin-top: 20px;
}

.loading {
  text-align: center;
  margin: 20px 0;
  color: #666;
}

.error {
  text-align: center;
  margin: 20px 0;
  color: #ff4d4f;
}
</style>
