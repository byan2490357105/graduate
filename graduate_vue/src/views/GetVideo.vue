<template>
  <div class="container">
    <div class="page-header">
      <h1>B站视频下载工具</h1>
      <router-link to="/">
        <button class="back-btn">返回首页</button>
      </router-link>
    </div>
    
    <div class="form-group">
      <label>输入BV号获取视频（多个BV号用换行或逗号分隔）</label>
      <textarea 
        v-model="bvInput" 
        placeholder="例如：BV1X1rKB3Eyz,BV1xx411c7mC&#10;或每行一个BV号" 
        rows="6"
      ></textarea>
    </div>
    
    <div class="button-group">
      <button id="submitBtn" :disabled="loading" @click="submitVideo">
        {{ loading ? '下载文件中...' : '提交到后端' }}
      </button>
      <button v-if="showDownloadBtn" id="downloadBtn" :disabled="loading || bvNums.length === 0" @click="batchDownload">
        批量下载到本地
      </button>
      <button id="resetBtn" class="reset-btn" @click="resetInput">重置输入</button>
      <button id="clearBtn" class="clear-btn" @click="clearDatabase">清空视频数据库</button>
    </div>
    
    <div class="status-area">
      <div class="status-title">处理状态</div>
      <div class="status-content">
        <div v-if="status === 'initial'" class="status-initial">
          📥 请输入BV号并点击"提交到后端"按钮开始下载
        </div>
        
        <div v-else-if="status === 'loading'" class="status-loading">
          <div class="spinner"></div>
          <div>
            <p>📥 正在处理 {{ bvNums.length }} 个视频...</p>
            <p>请稍候，下载过程可能需要几分钟时间</p>
          </div>
        </div>
        
        <div v-else-if="status === 'success'" class="status-success">
          <h4>🎉 处理完成！</h4>
          <div class="result-summary">
            <p>状态码：{{ result.code }}</p>
            <p>提示：{{ result.msg }}</p>
            <p>成功数：{{ result.successCount || 0 }} | 失败数：{{ result.failCount || 0 }}</p>
          </div>
          <div v-if="result.detail && result.detail.length > 0" class="result-detail">
            <h5>详细结果：</h5>
            <ul>
              <li 
                v-for="(item, index) in result.detail" 
                :key="index"
                :class="item.code === 200 ? 'result-success' : 'result-error'"
              >
                {{ item.code === 200 ? '✅' : '❌' }} BV号：{{ item.bvNum }} | 状态：{{ item.msg }}
              </li>
            </ul>
          </div>
        </div>
        
        <div v-else-if="status === 'error'" class="status-error">
          <h4>❌ {{ errorTitle }}</h4>
          <p>状态：{{ errorStatus }}</p>
          <p>错误：{{ errorMessage }}</p>
          <p>请检查网络连接或稍后重试</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { biliApi } from '@/api'

const bvInput = ref('')
const loading = ref(false)
const status = ref('initial')
const result = ref({})
const showDownloadBtn = ref(false)
const errorTitle = ref('')
const errorStatus = ref('')
const errorMessage = ref('')

const bvNums = computed(() => {
  return bvInput.value
    .split(/[\n,，\s]+/)
    .filter(bv => bv.trim() !== '')
})

const submitVideo = async () => {
  if (!bvInput.value.trim()) {
    status.value = 'error'
    errorTitle.value = '输入错误'
    errorStatus.value = '验证失败'
    errorMessage.value = 'BV号不能为空！'
    return
  }
  
  loading.value = true
  status.value = 'loading'
  showDownloadBtn.value = false // 开始下载时隐藏下载按钮
  
  try {
    const requestData = {
      bvNums: bvNums.value,
      savePath: ''
    }
    
    const { data } = await biliApi.getVideo(requestData)
    
    if (data.code === 200) {
      status.value = 'success'
      result.value = data
      showDownloadBtn.value = true // 下载完成后显示下载按钮
    } else {
      status.value = 'error'
      errorTitle.value = '处理失败'
      errorStatus.value = data.code
      errorMessage.value = data.msg
      showDownloadBtn.value = false
    }
  } catch (error) {
    status.value = 'error'
    errorTitle.value = '请求失败'
    errorStatus.value = '网络错误'
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

const resetInput = () => {
  bvInput.value = ''
  status.value = 'initial'
  result.value = {}
  showDownloadBtn.value = false
}

const clearDatabase = async () => {
  if (!confirm('确定要清空视频数据库和所有下载文件吗？此操作不可恢复！')) {
    return
  }
  
  loading.value = true
  status.value = 'loading'
  
  try {
    const { data } = await biliApi.clearVideo()
    
    if (data.code === 200) {
      status.value = 'success'
      result.value = {
        code: 200,
        msg: data.msg + ' - 视频数据库和所有下载文件已清空'
      }
    } else {
      status.value = 'error'
      errorTitle.value = '清空失败'
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
  }
}

// 监听输入框变化，隐藏下载按钮
watch(() => bvInput.value, () => {
  showDownloadBtn.value = false
})

const batchDownload = async () => {
  if (bvNums.value.length === 0) {
    alert('请输入BV号')
    return
  }
  
  try {
    // 构建下载URL
    const bvNumStr = bvNums.value.join(',')
    const url = `/api/bilibili/video/batch-download?bvNums=${bvNumStr}`
    
    // 创建下载链接
    const link = document.createElement('a')
    link.href = url
    link.download = 'videos.zip'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    
    // 显示成功提示
    status.value = 'success'
    result.value = {
      code: 200,
      msg: `开始下载 ${bvNums.value.length} 个视频，请稍候...`
    }
  } catch (error) {
    status.value = 'error'
    errorTitle.value = '下载失败'
    errorStatus.value = '网络错误'
    errorMessage.value = error.message
  }
}
</script>

<style scoped>
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

.clear-btn {
  background-color: #ff4d4f;
}

.clear-btn:hover {
  background-color: #ff7875;
}

#downloadBtn {
  background-color: #13c2c2;
}

#downloadBtn:hover {
  background-color: #36cfc9;
}

#downloadBtn:disabled {
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
  background-color: #f6ffed;
  border-color: #b7eb8f;
  color: #52c41a;
  padding: 20px;
  border-radius: 4px;
}

.status-error {
  background-color: #fff1f0;
  border-color: #ffccc7;
  color: #ff4d4f;
  padding: 20px;
  border-radius: 4px;
}

.result-summary {
  margin: 20px 0;
  padding: 15px;
  background-color: #f0f2f5;
  border-radius: 4px;
}

.result-summary p {
  margin: 5px 0;
}

.result-detail {
  margin-top: 15px;
}

.result-detail ul {
  list-style: none;
}

.result-detail li {
  padding: 8px 12px;
  margin: 5px 0;
  border-radius: 4px;
  font-size: 14px;
}

.result-success {
  background-color: #f6ffed;
  border-left: 4px solid #52c41a;
}

.result-error {
  background-color: #fff1f0;
  border-left: 4px solid #ff4d4f;
}
</style>
