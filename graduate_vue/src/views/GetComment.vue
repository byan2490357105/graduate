<template>
  <div class="container">
    <div class="page-header">
      <h1>获取评论</h1>
      <router-link to="/">
        <button class="back-btn">返回首页</button>
      </router-link>
    </div>
    
    <div class="form-group">
      <label for="bvNum">输入BV号：</label>
      <input id="bvNum" v-model="bvNum" type="text" placeholder="请输入BV号" />
    </div>
    
    <h3>获取评论页数配置</h3>
    
    <div class="page-inputs">
      <div class="form-group">
        <label for="startPage">开始页：</label>
        <select id="startPage" v-model="startPage">
          <option v-for="i in maxPage" :key="i" :value="i">第 {{ i }} 页</option>
        </select>
      </div>
      
      <div class="form-group">
        <label for="endPage">结束页：</label>
        <select id="endPage" v-model="endPage">
          <option v-for="i in maxPage" :key="i" :value="i">第 {{ i }} 页</option>
        </select>
      </div>
    </div>
    
    <button id="submitBtn" :disabled="loading" @click="submitPageParams">
      {{ loading ? '提交中...' : '提交到后端' }}
    </button>
    
    <div v-if="resultVisible" class="result-area">
      <div :style="{ color: success ? '#52c41a' : '#ff4d4f' }">
        {{ success ? '✅' : '❌' }} {{ message }}
      </div>
      <a 
        v-if="success && downloadUrl" 
        :href="downloadUrl" 
        class="download-link"
      >
        📥 点击下载评论CSV文件
      </a>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { biliApi } from '@/api'

const maxPage = 20
const bvNum = ref('')
const startPage = ref(1)
const endPage = ref(5)
const loading = ref(false)
const resultVisible = ref(false)
const success = ref(false)
const message = ref('')
const downloadUrl = ref('')

const submitPageParams = async () => {
  if (!bvNum.value.trim()) {
    alert('请输入BV号')
    return
  }
  
  if (startPage.value > endPage.value) {
    alert('错误：开始页不能大于结束页！')
    return
  }
  
  loading.value = true
  resultVisible.value = false
  downloadUrl.value = ''
  
  try {
    const params = {
      bvNum: bvNum.value.trim(),
      startPage: startPage.value,
      endPage: endPage.value + 1
    }
    
    const { data } = await biliApi.getComment(params)
    
    if (data.code === 200) {
      success.value = true
      message.value = data.msg
      downloadUrl.value = biliApi.downloadCommentCSV(bvNum.value.trim())
    } else {
      success.value = false
      message.value = data.message || data.msg
    }
    
    resultVisible.value = true
  } catch (error) {
    success.value = false
    message.value = `请求失败: ${error.message}`
    resultVisible.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page-inputs {
  display: flex;
  gap: 20px;
}

.page-inputs .form-group {
  flex: 1;
}

.result-area {
  margin-top: 20px;
  padding: 20px;
  background-color: #f9f9f9;
  border-radius: 4px;
}

.download-link {
  display: block;
  margin-top: 10px;
  color: #1677ff;
  font-size: 16px;
  text-decoration: none;
}

.download-link:hover {
  text-decoration: underline;
}
</style>
