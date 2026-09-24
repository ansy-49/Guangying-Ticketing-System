import axios from 'axios'
import type { CinemaListParams, SearchParams, LockSeatsRequest } from '@/types'

const instance = axios.create({
  baseURL: '/ajax',
})

// 需要鉴权的 axios 实例（指向 /api）
const authInstance = axios.create({
  baseURL: '/api',
})

// 请求拦截器 —— 为所有请求附带 JWT token
const attachToken = (config: any) => {
  if (typeof window !== 'undefined') {
    let token: string | null = null
    try {
      const stored = localStorage.getItem('guangying-user')
      if (stored) {
        const parsed = JSON.parse(stored)
        token = parsed?.state?.token || null
      }
    } catch {}
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
}

instance.interceptors.request.use(attachToken)
authInstance.interceptors.request.use(attachToken)

const api = {
  /** 正在热映列表 */
  getMovieOnInfoList: () =>
    instance.get('/movieOnInfoList').then((res) => res.data),

  /** 最受期待 */
  getMostExpected: () =>
    instance.get('/mostExpected').then((res) => res.data),

  /** 即将上映列表 */
  getComingList: () =>
    instance.get('/comingList').then((res) => res.data),

  /** 电影详情 */
  getDetailMovie: (params: { movieId: string }) =>
    instance.get('/detailmovie', { params }).then((res) => res.data),

  /** 加载更多列表 */
  getMoreList: (params: { movieIds: string }) =>
    instance.get('/moreComingList', { params }).then((res) => res.data),

  /** 电影筛选 */
  filterMovies: (params: {
    movieStatus?: number | null
    cat?: string
    src?: string
    year?: number | null
    sortBy?: string
    page?: number
    pageSize?: number
  }) => instance.get('/filterMovies', { params }).then((res) => res.data),

  /** 城市列表 */
  getCities: () =>
    axios
      .get('/dianying/cities.json')
      .then((res) => res.data),

  /** 影院列表 */
  getCinemaList: (params: CinemaListParams) =>
    instance.get('/cinemaList', { params }).then((res) => res.data),

  /** 搜索 */
  search: (params: SearchParams) =>
    instance.get('/search', { params }).then((res) => res.data),

  /** 影院筛选项 */
  filterCinemas: (params: { ci: string | number }) =>
    instance.get('/filterCinemas', { params }).then((res) => res.data),

  // ==================== 场次相关 ====================

  /** 获取电影某日场次 */
  getSchedules: (params: { movieId: number; showDate?: string }) =>
    instance.get('/schedules', { params }).then((res) => res.data),

  /** 获取电影某日场次（按影院分组） */
  getSchedulesByCinema: (params: { movieId: number; showDate?: string }) =>
    instance.get('/schedulesByCinema', { params }).then((res) => res.data),

  /** 获取电影有场次的日期列表 */
  getAvailableDates: (params: { movieId: number }) =>
    instance.get('/availableDates', { params }).then((res) => res.data),

  // ==================== 影院详情页 ====================

  /** 获取影院详情 */
  getCinemaDetail: (params: { cinemaId: number }) =>
    instance.get('/cinemaDetail', { params }).then((res) => res.data),

  /** 获取影院正在排片的电影 */
  getCinemaMovies: (params: { cinemaId: number }) =>
    instance.get('/cinemaMovies', { params }).then((res) => res.data),

  /** 获取影院某电影某日的场次 */
  getCinemaSchedules: (params: { cinemaId: number; movieId: number; showDate?: string }) =>
    instance.get('/cinemaSchedules', { params }).then((res) => res.data),

  /** 获取影院某电影有排片的日期列表 */
  getCinemaAvailableDates: (params: { cinemaId: number; movieId: number }) =>
    instance.get('/cinemaAvailableDates', { params }).then((res) => res.data),

  // ==================== 座位相关 ====================

  /** 获取座位布局 */
  getSeatLayout: (params: { scheduleId: number }) =>
    authInstance.get('/seat/layout', { params }).then((res) => res.data),

  /** 锁定座位 */
  lockSeats: (data: LockSeatsRequest) =>
    authInstance.post('/seat/lock', data).then((res) => res.data),

  /** 释放座位 */
  unlockSeats: (params: { scheduleId: number }) =>
    authInstance.post('/seat/unlock', null, { params }).then((res) => res.data),

  // ==================== 订单相关 ====================

  /** 取消订单 */
  cancelOrder: (orderNo: string) =>
    authInstance.post(`/order/cancel/${orderNo}`).then((res) => res.data),

  /** 查询用户订单列表 */
  getUserOrders: (params: { page?: number; size?: number }) =>
    authInstance.get('/order/list', { params }).then((res) => res.data),

  // ==================== 支付相关 ====================

  /** 模拟支付 */
  payOrder: (params: { orderNo: string }) =>
    authInstance.post('/payment/pay', null, { params }).then((res) => res.data),

  /** 查询订单详情 */
  getOrderDetail: (params: { orderNo: string }) =>
    authInstance.get('/payment/orderDetail', { params }).then((res) => res.data),

  // ==================== 认证相关 ====================

  /** 用户登录 */
  login: (data: { account: string; password: string }) =>
    axios.post('/api/auth/login', data).then((res) => res.data),

  /** 用户注册 */
  register: (data: { account: string; password: string; userNick?: string; inviteCode: string }) =>
    axios.post('/api/auth/register', data).then((res) => res.data),

  /** 获取当前用户信息（含积分） */
  getUserInfo: () => {
    let token: string | null = null
    try {
      const stored = localStorage.getItem('guangying-user')
      if (stored) {
        const parsed = JSON.parse(stored)
        token = parsed?.state?.token || null
      }
    } catch {}
    return axios.get('/api/auth/me', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }).then((res) => res.data)
  },

  // ==================== 排队相关（热门场次 Waiting Room） ====================

  /** 尝试入场（热门场次排队/直接放行） */
  queueEnter: (params: { scheduleId: number }) =>
    authInstance.post('/queue/enter', null, { params }).then((res) => res.data),

  /** 查询排队状态 */
  queueStatus: (params: { scheduleId: number }) =>
    authInstance.get('/queue/status', { params }).then((res) => res.data),
}

export default api
