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

    <div class="button-group">
      <button id="submitBtn" :disabled="loading || !midInput.trim()" @click="startCrawl">
        {{ loading ? '爬取中...' : '开始获取' }}
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
            <p>成功保存：{{ result.successCount }} 条</p>
            <p v-if="result.duplicateCount > 0">重复数据：{{ result.duplicateCount }} 条（已自动跳过）</p>
          </div>

          <div v-if="videoList.length > 0" class="video-preview">
            <h5>该UP主最新视频预览（前 {{ videoList.length }} 条）：</h5>
            <div class="video-list">
              <div v-for="(video, index) in videoList" :key="index" class="video-item">
                <div class="video-index">{{ index + 1 }}</div>
                <div class="video-info">
                  <div class="video-title" :title="video.title">{{ video.title }}</div>
                  <div class="video-meta">
                    <span class="meta-item">📺 播放量：{{ formatNumber(video.play) }}</span>
                    <span class="meta-item">👍 点赞：{{ formatNumber(video.like) }}</span>
                    <span class="meta-item">💬 评论：{{ formatNumber(video.comment) }}</span>
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
import { ref } from 'vue'
import { biliApi } from '@/api'

const midInput = ref('')
const loading = ref(false)
const status = ref('initial')
const currentPage = ref(0)
const result = ref({})
const videoList = ref([])
const errorTitle = ref('')
const errorStatus = ref('')
const errorMessage = ref('')

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

  loading.value = true
  status.value = 'loading'
  currentPage.value = 0
  result.value = {}
  videoList.value = []

  try {
    const requestData = { mid: mid }

    const { data } = await biliApi.crawlUpVideo(requestData)

    if (data.code === 200) {
      status.value = 'success'
      result.value = data.data || {}

      // 获取该UP主的视频列表用于展示
      await fetchVideoList(mid)
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

const resetForm = () => {
  midInput.value = ''
  status.value = 'initial'
  result.value = {}
  videoList.value = []
  currentPage.value = 0
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

.video-preview {
  margin-top: 20px;
}

.video-preview h5 {
  color: #333;
  margin-bottom: 15px;
  font-size: 16px;
}

.video-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.status-error {
  color: #ff4d4f;
}

.status-error h4 {
  color: #ff4d4f;
  margin-bottom: 10px;
}
</style>
