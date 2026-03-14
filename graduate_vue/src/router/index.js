import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/comment',
    name: 'GetComment',
    component: () => import('@/views/GetComment.vue')
  },
  {
    path: '/video',
    name: 'GetVideo',
    component: () => import('@/views/GetVideo.vue')
  },
  {
    path: '/analysis',
    name: 'BZoneAnalysis',
    component: () => import('@/views/BZoneAnalysis.vue')
  },
  {
    path: '/statistics',
    name: 'ZoneStatistics',
    component: () => import('@/views/ZoneStatistics.vue')
  },
  {
    path: '/wordcloud',
    name: 'WordCloud',
    component: () => import('@/views/WordCloud.vue')
  },
  {
    path: '/video-statistics',
    name: 'VideoStatistics',
    component: () => import('@/views/VideoStatistics.vue')
  },
  {
    path: '/up-video-crawler',
    name: 'UpVideoCrawler',
    component: () => import('@/views/UpVideoCrawler.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
