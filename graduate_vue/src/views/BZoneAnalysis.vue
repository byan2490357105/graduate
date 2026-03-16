<template>
  <div class="container">
    <div class="page-header">
      <h1>按分区获取B站数据</h1>
      <router-link to="/">
        <button class="back-btn">返回首页</button>
      </router-link>
    </div>
    
    <div class="form-group">
      <label for="zone">选择B站分区：</label>
      <select id="zone" v-model="selectedZone">
        <option value="">请选择分区</option>
        <option v-for="zone in biliZones" :key="zone.id" :value="zone.id">
          {{ zone.name }}
        </option>
      </select>
    </div>
    
    <div v-if="showPageInputs" class="page-inputs-section">
      <div class="page-inputs-hint">📋 以下页号设置仅对"获取数据"按钮有效</div>
      <div class="page-inputs">
        <div class="form-group">
          <label for="startPage">起始页：</label>
          <input id="startPage" v-model.number="startPage" type="number" min="1" />
        </div>
        <div class="form-group">
          <label for="endPage">结束页：</label>
          <input id="endPage" v-model.number="endPage" type="number" min="1" max="50" @input="validateEndPage" />
          <div v-if="endPageError" class="error-hint">{{ endPageError }}</div>
        </div>
      </div>
    </div>
    
    <div class="button-group">
      <button 
        id="submitBtn" 
        :disabled="loading" 
        @click="submitZoneData"
      >
        {{ loading ? '正在获取数据中' : '获取数据' }}
      </button>
      <button 
        id="stopBtn" 
        class="stop-btn" 
        :disabled="!crawlerRunning" 
        @click="stopCrawler"
      >
        提前结束爬虫
      </button>
      <button 
        id="collectCommentBtn" 
        class="comment-btn" 
        :disabled="loading"
        @click="collectZoneComment"
      >
        {{ commentLoading ? '正在收集评论中' : '将收集分区评论' }}
      </button>
      <button 
        id="collectVideoDataBtn" 
        class="video-btn" 
        :disabled="loading"
        @click="collectZoneVideoData"
      >
        {{ videoLoading ? '正在收集视频数据中' : '保留分区视频参数（录入tag）' }}
      </button>
      <button 
        id="exportUpCsvBtn" 
        class="export-btn" 
        :disabled="!selectedZone || exportLoading"
        @click="exportUpListToCsv"
      >
        {{ exportLoading ? '正在导出中...' : '获取分区(' + getSelectedZoneName() + ')up主mid和名称并保存为csv' }}
      </button>
    </div>
    
    <div v-if="resultVisible" class="result">
      <h3>提交结果</h3>
      <p>{{ resultContent }}</p>
    </div>
    
    <div v-if="errorVisible" class="error">
      <h3>错误信息</h3>
      <p>{{ errorContent }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { biliApi } from '@/api'

const biliZones = [
  { name: "动画", id: "1005" },
  { name: "鬼畜", id: "1007" },
  { name: "舞蹈", id: "1004" },
  { name: "娱乐", id: "1002" },
  { name: "科技数码", id: "1012" },
  { name: "美食", id: "1020" },
  { name: "游戏", id: "1008" },
  { name: "音乐", id: "1003" },
  { name: "影视", id: "1001" },
  { name: "知识", id: "1010" },
  { name: "资讯", id: "1009" },
  { name: "小剧场", id: "1021" },
  { name: "动物", id: "1024" },
  { name: "家装房产", id: "1015" },
  { name: "旅游出行", id: "1022" },
  { name: "情感", id: "1027" },
  { name: "汽车", id: "1013" },
  { name: "vlog", id: "1029" },
  { name: "户外潮流", id: "1016" },
  { name: "三农", id: "1023" },
  { name: "生活兴趣", id: "1030" },
  { name: "时尚美妆", id: "1014" },
  { name: "绘画", id: "1006" },
  { name: "健身", id: "1017" },
  { name: "亲子", id: "1025" },
  { name: "生活经验", id: "1031" },
  { name: "体育运动", id: "1018" },
  { name: "人工智能", id: "1011" },
  { name: "手工", id: "1019" },
  { name: "健康", id: "1026" }
]

const selectedZone = ref('')
const startPage = ref(1)
const endPage = ref(10)
const endPageError = ref('')
const loading = ref(false)
const commentLoading = ref(false)
const videoLoading = ref(false)
const exportLoading = ref(false)
const crawlerRunning = ref(false)
const resultVisible = ref(false)
const errorVisible = ref(false)
const resultContent = ref('')
const errorContent = ref('')
const showPageInputs = ref(true)

let crawlerStatusInterval = null

let clearErrorTimer = null

const validateEndPage = () => {
  if (endPage.value > 50) {
    endPageError.value = '单次爬取，结束页号最大为50'
    endPage.value = 50
    if (clearErrorTimer) {
      clearTimeout(clearErrorTimer)
    }
    clearErrorTimer = setTimeout(() => {
      endPageError.value = ''
    }, 3000)
  } else if (endPage.value > 0 && endPage.value <= 50) {
    endPageError.value = ''
  }
}

const getSelectedZoneName = () => {
  if (!selectedZone.value) return ''
  const zone = biliZones.find(z => z.id === selectedZone.value)
  return zone ? zone.name : ''
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

const startCrawlerStatusCheck = () => {
  const checkStatus = async () => {
    try {
      const { data } = await biliApi.isCrawlerRunning()
      if (data.running) {
        crawlerRunning.value = true
        crawlerStatusInterval = setInterval(async () => {
          try {
            const { data } = await biliApi.isCrawlerRunning()
            if (!data.running) {
              clearInterval(crawlerStatusInterval)
              crawlerStatusInterval = null
              crawlerRunning.value = false
              loading.value = false
              commentLoading.value = false
              videoLoading.value = false
              showResult('爬虫执行完成！')
            }
          } catch (error) {
            console.error('查询爬虫状态失败：', error)
          }
        }, 3000)
      } else {
        setTimeout(checkStatus, 2000)
      }
    } catch (error) {
      console.error('查询爬虫状态失败：', error)
      setTimeout(checkStatus, 2000)
    }
  }
  setTimeout(checkStatus, 2000)
}

const submitZoneData = async () => {
  showPageInputs.value = true
  
  if (crawlerStatusInterval !== null) {
    showError('爬虫正在运行中，请等待完成')
    return
  }
  
  if (!selectedZone.value) {
    showError('请选择一个分区')
    return
  }
  
  if (startPage.value > endPage.value) {
    showError('起始页不能大于结束页')
    return
  }
  
  const zoneData = {
    "爬取分区ID": selectedZone.value,
    "爬取起始页": startPage.value.toString(),
    "爬取结束页": endPage.value.toString()
  }
  
  loading.value = true
  crawlerRunning.value = true
  showResult('正在提交数据...')
  
  try {
    const { data } = await biliApi.getRegionData(zoneData)
    showResult('提交成功！\n' + JSON.stringify(data, null, 2))
    startCrawlerStatusCheck()
  } catch (error) {
    showError('提交失败：' + error.message)
    loading.value = false
    crawlerRunning.value = false
  }
}

const stopCrawler = async () => {
  showResult('正在停止爬虫...')
  
  try {
    const { data } = await biliApi.stopCrawler()
    showResult('停止请求已发送！\n' + JSON.stringify(data, null, 2))
    if (crawlerStatusInterval) {
      clearInterval(crawlerStatusInterval)
      crawlerStatusInterval = null
    }
    crawlerRunning.value = false
    loading.value = false
    commentLoading.value = false
    videoLoading.value = false
  } catch (error) {
    showError('停止失败：' + error.message)
  }
}

const collectZoneComment = async () => {
  showPageInputs.value = false
  
  if (crawlerStatusInterval !== null) {
    showError('爬虫正在运行中，请等待完成')
    return
  }
  
  if (!selectedZone.value) {
    showError('请选择一个分区')
    return
  }
  
  const selectedZoneObj = biliZones.find(z => z.id === selectedZone.value)
  const requestData = {
    "分区名称": selectedZoneObj.name,
    "分区ID": selectedZone.value
  }
  
  commentLoading.value = true
  crawlerRunning.value = true
  showResult('正在提交数据...')
  
  try {
    const { data } = await biliApi.batchGetZoneComment(requestData)
    showResult('提交成功！\n' + JSON.stringify(data, null, 2))
    startCrawlerStatusCheck()
  } catch (error) {
    showError('提交失败：' + error.message)
    commentLoading.value = false
    crawlerRunning.value = false
  }
}

const collectZoneVideoData = async () => {
  showPageInputs.value = false
  
  if (crawlerStatusInterval !== null) {
    showError('爬虫正在运行中，请等待完成')
    return
  }
  
  if (!selectedZone.value) {
    showError('请选择一个分区')
    return
  }
  
  videoLoading.value = true
  crawlerRunning.value = true
  showResult('正在提交数据...')
  
  try {
    const { data } = await biliApi.batchGetVideoData(selectedZone.value)
    showResult('提交成功！\n' + JSON.stringify(data, null, 2))
    startCrawlerStatusCheck()
  } catch (error) {
    showError('提交失败：' + error.message)
    videoLoading.value = false
    crawlerRunning.value = false
  }
}

const exportUpListToCsv = async () => {
  showPageInputs.value = false
  
  if (!selectedZone.value) {
    showError('请选择一个分区')
    return
  }
  
  exportLoading.value = true
  showResult('正在导出分区UP主信息...')
  
  try {
    const zoneName = getSelectedZoneName()
    const url = `/api/bilibili/region/export-up-csv?pidV2=${selectedZone.value}`
    
    const link = document.createElement('a')
    link.href = url
    link.download = `${zoneName}_up主列表_${new Date().toISOString().split('T')[0]}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    
    showResult(`已开始导出分区(${zoneName})的UP主信息\n请查看浏览器的下载列表`)
  } catch (error) {
    showError('导出失败：' + error.message)
  } finally {
    exportLoading.value = false
  }
}

onMounted(() => {
})

onUnmounted(() => {
  if (crawlerStatusInterval) {
    clearInterval(crawlerStatusInterval)
  }
})
</script>

<style scoped>
.page-inputs-section {
  margin-bottom: 15px;
  padding: 15px;
  background-color: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
}

.page-inputs-hint {
  color: #52c41a;
  font-size: 14px;
  margin-bottom: 10px;
  font-weight: 500;
}

.page-inputs {
  display: flex;
  gap: 10px;
}

.page-inputs .form-group {
  flex: 1;
  position: relative;
}

.error-hint {
  color: #ff4d4f;
  font-size: 12px;
  margin-top: 5px;
  font-weight: 500;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.button-group {
  margin: 20px 0;
}

.button-group button {
  margin-right: 10px;
  margin-bottom: 10px;
}

.stop-btn {
  background-color: #ff4d4f;
}

.stop-btn:hover:not(:disabled) {
  background-color: #ff7875;
}

.comment-btn {
  background-color: #52c41a;
}

.comment-btn:hover:not(:disabled) {
  background-color: #73d13d;
}

.video-btn {
  background-color: #722ed1;
}

.video-btn:hover:not(:disabled) {
  background-color: #9254de;
}

.export-btn {
  background-color: #fa8c16;
}

.export-btn:hover:not(:disabled) {
  background-color: #ffa940;
}
</style>
