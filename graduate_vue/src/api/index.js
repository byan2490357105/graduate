import axios from 'axios'

const api = axios.create({
  timeout: 300000
})

export const biliApi = {
  getComment(data) {
    return api.post('/getComment', data)
  },
  
  downloadCommentCSV(bvNum) {
    return `/api/bilibili/comment/downloadCommentCSV?bvNum=${bvNum}`
  },
  
  getVideo(data) {
    return api.post('/api/bilibili/video/getvideo', data)
  },
  
  clearVideo() {
    return api.post('/api/bilibili/video/clear')
  },
  
  getRegionData(data) {
    return api.post('/getBZoneRegion', data)
  },
  
  stopCrawler() {
    return api.post('/stopCrawler', {})
  },
  
  isCrawlerRunning() {
    return api.get('/isCrawlerRunning')
  },
  
  batchGetZoneComment(data) {
    return api.post('/batchGetZoneComment', data)
  },
  
  batchGetVideoData(pidV2) {
    return api.post(`/api/bilibili/video/batch-get-video-data?pidV2=${pidV2}`)
  },
  
  getRegionStatistics(data) {
    return api.post('/api/bilibili/regiondata/query-all-statistics', data)
  },
  
  getDataCount(data) {
    return api.post('/api/bilibili/regiondata/query-data-count', data)
  },
  
  getTagsStatistics(limit = 100) {
    return api.get(`/tag/getTagsStatistics?limit=${limit}`)
  },
  
  getCommentWordStatistics(limit = 150) {
    return api.get(`/comment/getCommentWordStatistics?limit=${limit}`)
  }
}

export default api
