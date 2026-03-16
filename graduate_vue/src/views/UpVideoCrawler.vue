<template>
  <div class="container">
    <div class="page-header">
      <h1>UP主视频信息获取</h1>
      <router-link to="/">
        <button class="back-btn">返回首页</button>
      </router-link>
    </div>

    <div class="form-group">
      <label>输入UP主MID</label>
      <input
        v-model="midInput"
        type="text"
        placeholder="例如：15385187"
        :disabled="loading"
      />
      <p class="hint">提示：可以在B站UP主个人空间URL中找到MID，如 space.bilibili.com/15385187</p>
    </div>

    <div class="form-group">
      <label class="checkbox-label">
        <input
          v-model="usePageRange"
          type="checkbox"
          :disabled="loading"
        />
        <span>按页号精确获取up主视频信息</span>
      </label>
    </div>

    <div v-if="usePageRange" class="page-range-group">
      <div class="page-range-inputs">
        <div class="page-input">
          <label>起始页</label>
          <input
            v-model="startPage"
            type="number"
            min="1"
            placeholder="1"
            :disabled="loading"
          />
        </div>
        <div class="page-separator">——</div>
        <div class="page-input">
          <label>结束页</label>
          <input
            v-model="endPage"
            type="number"
            min="1"
            placeholder="10"
            :disabled="loading"
          />
        </div>
      </div>
      <p class="hint">提示：输入要爬取的页码范围，例如：1-5表示爬取第1页到第5页</p>
    </div>

    <div class="button-group">
      <button id="submitBtn" :disabled="loading || !midInput.trim()" @click="startCrawl">
        {{ loading ? '爬取中...' : (usePageRange ? '获取该up主的指定页视频信息' : '获取该up主的全部视频信息') }}
      </button>
      <button id="resetBtn" class="reset-btn" @click="resetForm" :disabled="loading">重置</button>
    </div>

    <div class="status-area">
      <div class="status-title">处理状态</div>
      <div class="status-content">
        <div v-if="status === 'initial'" class="status-initial">
          📝 请输入UP主的MID并点击"开始获取"按钮
        </div>

        <div v-else-if="status === 'loading'" class="status-loading">
          <div class="spinner"></div>
          <div>
            <p>🔄 正在爬取UP主视频信息...</p>
            <p v-if="currentPage > 0">当前页数：第 {{ currentPage }} 页</p>
            <p>请稍候，爬取过程可能需要几分钟时间</p>
          </div>
        </div>

        <div v-else-if="status === 'success'" class="status-success">
          <h4>🎉 爬取完成！</h4>
          <div class="result-summary">
            <p>UP主ID：{{ result.mid }}</p>
            <p>总页数：{{ result.totalPages }}</p>
            <p>已处理页数：{{ result.lastProcessedPage }} 页</p>
            <p>成功保存：{{ result.successCount }} 条</p>
            <p v-if="result.duplicateCount > 0">重复数据：{{ result.duplicateCount }} 条（已自动跳过）</p>
            <p v-if="result.stopReason" class="stop-reason">{{ result.stopReason }}</p>
          </div>

          <div v-if="result.mid && midInput.trim() === result.mid.toString()" class="download-section">
            <div class="download-controls">
              <div class="select-all">
                <label class="checkbox-label">
                  <input
                    type="checkbox"
                    v-model="selectAll"
                    @change="handleSelectAll"
                  />
                  <span>全选</span>
                </label>
                <span class="selected-count">已选择 {{ selectedVideos.size }} 个视频</span>
              </div>
              <button 
                class="download-btn" 
                @click="downloadSelectedVideos" 
                :disabled="downloading || selectedVideos.size === 0"
              >
                {{ downloading ? '下载中...' : `下载选中的 ${selectedVideos.size} 个视频` }}
              </button>
            </div>

            <div class="video-list-section">
              <h5>该UP主视频列表：</h5>
              <div v-if="loadingVideoList" class="loading-videos">
                <div class="spinner"></div>
                <span>加载视频列表中...</span>
              </div>
              <div v-else-if="pagedVideos.length > 0">
                <div class="video-list">
                  <div v-for="(video, index) in pagedVideos" :key="video.bvNum" class="video-item">
                    <div class="video-checkbox">
                      <input
                        type="checkbox"
                        :checked="isVideoSelected(video.bvNum)"
                        @change="toggleVideoSelection(video.bvNum)"
                      />
                    </div>
                    <div class="video-index">{{ (currentVideoPage - 1) * pageSize + index + 1 }}</div>
                    <div class="video-info">
                      <div class="video-title" :title="video.title" @click="openVideoPage(video.bvNum)">{{ video.title }}</div>
                      <div class="video-meta">
                        <span class="meta-item">⏱️ 时长：{{ video.length }}</span>
                        <span class="meta-item">📅 发布时间：{{ formatDate(video.created) }}</span>
                      </div>
                      <div class="video-bv">BV号：{{ video.bvNum }}</div>
                    </div>
                  </div>
                </div>
                <div class="pagination">
                  <button 
                    @click="currentVideoPage = 1" 
                    :disabled="currentVideoPage === 1"
                  >
                    首页
                  </button>
                  <button 
                    @click="currentVideoPage--" 
                    :disabled="currentVideoPage === 1"
                  >
                    上一页
                  </button>
                  <span class="page-info">
                    第 {{ currentVideoPage }} / {{ totalPages }} 页
                  </span>
                  <button 
                    @click="currentVideoPage++" 
                    :disabled="currentVideoPage === totalPages"
                  >
                    下一页
                  </button>
                  <button 
                    @click="currentVideoPage = totalPages" 
                    :disabled="currentVideoPage === totalPages"
                  >
                    末页
                  </button>
                </div>
              </div>
              <div v-else class="no-videos">
                暂无视频数据
              </div>
            </div>
          </div>

          <div v-if="videoList.length > 0" class="video-preview">
            <h5>该UP主最新视频预览（前 {{ videoList.length }} 条）：</h5>
            <div class="video-list">
              <div v-for="(video, index) in videoList" :key="index" class="video-item">
                <div class="video-index">{{ index + 1 }}</div>
                <div class="video-info">
                  <div class="video-title" :title="video.title" @click="openVideoPage(video.bvNum)">{{ video.title }}</div>
                  <div class="video-meta">
                    <span class="meta-item">⏱️ 时长：{{ video.length }}</span>
                    <span class="meta-item">📅 发布时间：{{ formatDate(video.created) }}</span>
                  </div>
                  <div class="video-bv">BV号：{{ video.bvNum }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="status === 'error'" class="status-error">
          <h4>❌ {{ errorTitle }}</h4>
          <p>状态：{{ errorStatus }}</p>
          <p>错误：{{ errorMessage }}</p>
          <p>请检查MID是否正确或稍后重试</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue'
import { biliApi } from '@/api'

const midInput = ref('')
const loading = ref(false)
const downloading = ref(false)
const status = ref('initial')
const currentPage = ref(0)
const result = ref({})
const videoList = ref([])
const errorTitle = ref('')
const errorStatus = ref('')
const errorMessage = ref('')
const lastCrawledMid = ref(null)
const usePageRange = ref(false)
const startPage = ref(1)
const endPage = ref(10)

// 视频列表和分页相关
const allVideos = ref([])
const loadingVideoList = ref(false)
const currentVideoPage = ref(1)
const pageSize = ref(20)
const selectedVideos = ref(new Set())
const selectAll = ref(false)

// 计算分页视频
const pagedVideos = computed(() => {
  const start = (currentVideoPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return allVideos.value.slice(start, end)
})

// 计算总页数
const totalPages = computed(() => {
  return Math.ceil(allVideos.value.length / pageSize.value)
})

// 监听midInput变化
watch(midInput, (newMid) => {
  if (newMid && lastCrawledMid.value && newMid !== lastCrawledMid.value.toString()) {
    // 输入的mid与上次爬取的mid不同，重置状态
    result.value = {}
    allVideos.value = []
    selectedVideos.value = new Set()
    selectAll.value = false
    currentVideoPage.value = 1
  }
})

// 监听爬取结果，获取视频列表
watch(() => result.value.mid, async (newMid) => {
  if (newMid) {
    await fetchAllVideos(newMid)
  }
})

const formatNumber = (num) => {
  if (num === undefined || num === null) return '0'
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toString()
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

const startCrawl = async () => {
  if (!midInput.value.trim()) {
    status.value = 'error'
    errorTitle.value = '输入错误'
    errorStatus.value = '验证失败'
    errorMessage.value = 'MID不能为空！'
    return
  }

  const mid = parseInt(midInput.value)
  if (isNaN(mid) || mid <= 0) {
    status.value = 'error'
    errorTitle.value = '输入错误'
    errorStatus.value = '验证失败'
    errorMessage.value = 'MID必须是大于0的数字！'
    return
  }

  // 验证页码范围
  if (usePageRange.value) {
    const start = parseInt(startPage.value)
    const end = parseInt(endPage.value)
    
    if (isNaN(start) || start < 1) {
      status.value = 'error'
      errorTitle.value = '输入错误'
      errorStatus.value = '验证失败'
      errorMessage.value = '起始页必须是大于0的数字！'
      return
    }
    
    if (isNaN(end) || end < 1) {
      status.value = 'error'
      errorTitle.value = '输入错误'
      errorStatus.value = '验证失败'
      errorMessage.value = '结束页必须是大于0的数字！'
      return
    }
    
    if (start > end) {
      status.value = 'error'
      errorTitle.value = '输入错误'
      errorStatus.value = '验证失败'
      errorMessage.value = '起始页不能大于结束页！'
      return
    }
  }

  loading.value = true
  status.value = 'loading'
  currentPage.value = 0
  result.value = {}
  videoList.value = []
  allVideos.value = []
  selectedVideos.value = new Set()
  selectAll.value = false
  currentVideoPage.value = 1

  try {
    const requestData = { 
      mid: mid,
      usePageRange: usePageRange.value,
      startPage: usePageRange.value ? parseInt(startPage.value) : null,
      endPage: usePageRange.value ? parseInt(endPage.value) : null
    }

    const { data } = await biliApi.crawlUpVideo(requestData)

    if (data.code === 200) {
      status.value = 'success'
      result.value = data.data || {}
      lastCrawledMid.value = mid

      // 使用返回结果中的视频列表，如果没有则调用fetchVideoList
      if (result.value.videoList && result.value.videoList.length > 0) {
        videoList.value = result.value.videoList
      } else {
        // 获取该UP主的视频列表用于展示
        await fetchVideoList(mid)
      }

      // 获取所有视频用于分页和选择
      await fetchAllVideos(mid)
    } else {
      status.value = 'error'
      errorTitle.value = '爬取失败'
      errorStatus.value = data.code
      errorMessage.value = data.msg
    }
  } catch (error) {
    status.value = 'error'
    errorTitle.value = '请求失败'
    errorStatus.value = '网络错误'
    errorMessage.value = error.message
  } finally {
    loading.value = false
    currentPage.value = 0
  }
}

const fetchVideoList = async (mid) => {
  try {
    const { data } = await biliApi.getUpVideoList({ mid: mid, limit: 10 })
    if (data.code === 200) {
      videoList.value = data.data || []
    }
  } catch (error) {
    console.error('获取视频列表失败:', error)
  }
}

const fetchAllVideos = async (mid) => {
  loadingVideoList.value = true
  try {
    const { data } = await biliApi.getUpVideoList({ mid: mid, limit: 1000 }) // 假设接口支持较大的limit
    if (data.code === 200) {
      allVideos.value = data.data || []
    }
  } catch (error) {
    console.error('获取所有视频失败:', error)
  } finally {
    loadingVideoList.value = false
  }
}

const resetForm = () => {
  midInput.value = ''
  status.value = 'initial'
  result.value = {}
  videoList.value = []
  allVideos.value = []
  selectedVideos.value = new Set()
  selectAll.value = false
  currentVideoPage.value = 1
  currentPage.value = 0
}

const openVideoPage = (bvNum) => {
  const videoUrl = 'https://www.bilibili.com/video/' + bvNum
  window.open(videoUrl, '_blank')
}

const isVideoSelected = (bvNum) => {
  return selectedVideos.value.has(bvNum)
}

const toggleVideoSelection = (bvNum) => {
  const newSet = new Set(selectedVideos.value)
  if (newSet.has(bvNum)) {
    newSet.delete(bvNum)
  } else {
    newSet.add(bvNum)
  }
  selectedVideos.value = newSet
  updateSelectAll()
}

const handleSelectAll = () => {
  const newSet = new Set(selectedVideos.value)
  if (selectAll.value) {
    // 全选所有视频
    allVideos.value.forEach(video => {
      newSet.add(video.bvNum)
    })
  } else {
    // 取消全选
    newSet.clear()
  }
  selectedVideos.value = newSet
}

const updateSelectAll = () => {
  selectAll.value = selectedVideos.value.size === allVideos.value.length && allVideos.value.length > 0
}

const downloadSelectedVideos = async () => {
  if (selectedVideos.value.size === 0) {
    return
  }

  // 防止重复点击
  if (downloading.value) {
    return
  }

  // 添加确认对话框
  const confirmed = confirm(`确定要下载选中的 ${selectedVideos.value.size} 个视频吗？\n\n注意：\n1. 这将下载选中的视频文件\n2. 下载过程可能需要较长时间\n3. 下载过程中请不要关闭页面`)
  if (!confirmed) {
    return
  }

  downloading.value = true
  try {
    const bvNums = Array.from(selectedVideos.value)
    const mid = result.value.mid
    
    // 每40个视频一批进行下载
    const batchSize = 40
    for (let i = 0; i < bvNums.length; i += batchSize) {
      const batchBvNums = bvNums.slice(i, i + batchSize)
      const bvNumsParam = batchBvNums.join(',')
      const url = `/api/bilibili/upvideo/download-selected?bvNums=${bvNumsParam}&mid=${mid}`
      
      // 创建隐藏的a标签来触发下载
      const link = document.createElement('a')
      link.href = url
      link.download = `${mid}_videos_${i + 1}-${Math.min(i + batchSize, bvNums.length)}_${new Date().toISOString().split('T')[0]}.zip`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      
      // 给浏览器一点时间处理下载
      if (i + batchSize < bvNums.length) {
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
    }
    
    // 显示成功提示
    alert(`已开始下载选中的 ${selectedVideos.value.size} 个视频\n\n请查看浏览器的下载列表`)
    
  } catch (error) {
    console.error('下载视频失败:', error)
    alert('下载失败：' + error.message)
  } finally {
    downloading.value = false
  }
}
</script>

<style scoped>
.form-group input {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-group input:focus {
  border-color: #1890ff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.form-group input:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
}

.hint {
  color: #888;
  font-size: 12px;
  margin-top: 8px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.checkbox-label input {
  margin-right: 8px;
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.checkbox-label span {
  font-size: 14px;
  color: #333;
}

.page-range-group {
  margin-top: 20px;
  padding: 15px;
  background-color: #f9f9f9;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
}

.page-range-inputs {
  display: flex;
  align-items: center;
  gap: 15px;
}

.page-input {
  flex: 1;
}

.page-input label {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 5px;
}

.page-input input {
  width: 100%;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  box-sizing: border-box;
}

.page-input input:focus {
  border-color: #1890ff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.page-separator {
  font-size: 20px;
  color: #999;
  font-weight: bold;
  padding-top: 20px;
}

.button-group {
  margin: 20px 0;
}

.button-group button {
  margin-right: 10px;
}

.reset-btn {
  background-color: #faad14;
}

.reset-btn:hover {
  background-color: #ffc53d;
}

.reset-btn:disabled {
  background-color: #d9d9d9;
  cursor: not-allowed;
}

.status-area {
  margin-top: 30px;
  padding: 20px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background-color: #fafafa;
}

.status-title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 15px;
  color: #555;
}

.status-content {
  min-height: 100px;
}

.status-initial {
  color: #999;
  text-align: center;
  padding: 20px;
}

.status-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px;
}

.loading-videos {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px;
  color: #666;
}

.spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #1890ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-right: 15px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.status-success {
  color: #52c41a;
}

.status-success h4 {
  color: #52c41a;
  margin-bottom: 15px;
}

.result-summary {
  background-color: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
  padding: 15px;
  margin-bottom: 20px;
}

.result-summary p {
  margin: 5px 0;
  color: #333;
}

.stop-reason {
  color: #faad14;
  font-weight: 500;
  padding: 8px;
  background-color: #fffbe6;
  border-left: 3px solid #faad14;
  border-radius: 2px;
}

.download-section {
  margin-top: 20px;
  text-align: left;
}

.download-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 4px;
}

.select-all {
  display: flex;
  align-items: center;
  gap: 15px;
}

.select-all .checkbox-label {
  font-size: 14px;
  font-weight: 500;
}

.selected-count {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.download-btn {
  background-color: #52c41a;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.download-btn:hover {
  background-color: #389e0d;
}

.download-btn:disabled {
  background-color: #d9d9d9;
  cursor: not-allowed;
}

.video-list-section {
  margin-top: 20px;
}

.video-list-section h5 {
  color: #333;
  margin-bottom: 15px;
  font-size: 16px;
}

.video-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.video-item {
  display: flex;
  background-color: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 15px;
  transition: all 0.3s;
}

.video-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-color: #1890ff;
}

.video-checkbox {
  margin-right: 15px;
  padding-top: 5px;
  flex-shrink: 0;
}

.video-checkbox input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.video-index {
  width: 30px;
  height: 30px;
  background-color: #1890ff;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  margin-right: 15px;
  flex-shrink: 0;
}

.video-info {
  flex: 1;
  min-width: 0;
}

.video-title {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.3s;
}

.video-title:hover {
  color: #1890ff;
}

.video-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
}

.meta-item {
  font-size: 13px;
  color: #666;
}

.video-bv {
  font-size: 12px;
  color: #999;
  font-family: monospace;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e8e8e8;
}

.pagination button {
  padding: 6px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background-color: #fff;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.pagination button:hover:not(:disabled) {
  border-color: #1890ff;
  color: #1890ff;
}

.pagination button:disabled {
  background-color: #f5f5f5;
  color: #999;
  cursor: not-allowed;
  border-color: #d9d9d9;
}

.page-info {
  font-size: 14px;
  color: #666;
  margin: 0 10px;
}

.no-videos {
  text-align: center;
  padding: 30px;
  color: #999;
  background-color: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}

.video-preview {
  margin-top: 20px;
}

.video-preview h5 {
  color: #333;
  margin-bottom: 15px;
  font-size: 16px;
}

.status-error {
  color: #ff4d4f;
}

.status-error h4 {
  color: #ff4d4f;
  margin-bottom: 10px;
}
</style>
