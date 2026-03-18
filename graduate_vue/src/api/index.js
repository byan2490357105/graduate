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
  },
  
  getTopVideos(data) {
    return api.post('/api/bilibili/regiondata/query-top-videos', data)
  },
  
  getVideoQuality(data) {
    return api.post('/api/bilibili/regiondata/query-video-quality', data)
  },
  
  getHighLikeRate(data) {
    return api.post('/api/bilibili/regiondata/query-high-like-rate', data)
  },
  
  getHighDanmakuRate(data) {
    return api.post('/api/bilibili/regiondata/query-high-danmaku-rate', data)
  },
  
  getPublishTimeDistribution(data) {
    return api.post('/api/bilibili/regiondata/query-publish-time-distribution', data)
  },
  
  getVideoDistribution(data) {
    return api.post('/api/bilibili/regiondata/query-video-distribution', data)
  },

  crawlUpVideo(data) {
    return api.post('/api/bilibili/upvideo/crawl', data)
  },

  getUpVideoList(params) {
    return api.get('/api/bilibili/upvideo/list', { params })
  },

  checkBvExists(bvNum) {
    return api.get('/api/bilibili/upvideo/exists', { params: { bvNum } })
  },

  importUpVideoData(data) {
    return api.post('/api/bilibili/up-video-analysis/import', data)
  },

  getImportStatus(mid) {
    return api.get('/api/bilibili/up-video-analysis/import-status', { params: { mid } })
  },

  getUpStatistics(mid) {
    return api.get('/api/bilibili/up-video-analysis/statistics', { params: { mid } })
  },

  getMonthlyVideoCount(mid, year) {
    return api.get('/api/bilibili/up-video-analysis/monthly-count', { params: { mid, year } })
  },

  getYearlyTrend(mid, year) {
    return api.get('/api/bilibili/up-video-analysis/trend', { params: { mid, year } })
  },

  terminateTask(data) {
    return api.post('/api/bilibili/up-video-analysis/terminate', data)
  }
}

export default api
