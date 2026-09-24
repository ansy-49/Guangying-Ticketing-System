// ==================== 电影相关 ====================
export interface MovieItem {
  id: number
  nm: string
  img: string
  sc: number | string
  star: string
  showInfo: string
  wish: number
  globalReleased: boolean
  cat?: string
  enm?: string
  dur?: number
  src?: string
  pubDesc?: string
  dra?: string
  vd?: string
  photos?: string[]
  pn?: number
  comingTitle?: string
  releaseYear?: number
}

// ==================== 票房相关 ====================
export interface BoxOfficeItem {
  id: number
  title: string
  amount: number
  unit: string
}

// ==================== 城市相关 ====================
export interface CityItem {
  id: number
  nm: string
  py: string
}

export interface CityGroup {
  tag: string
  items: CityItem[]
}

// ==================== 影院相关 ====================
export interface CinemaItem {
  id: number
  nm: string
  addr: string
  distance: string
  tag: {
    allowRefund: boolean
    endorse: boolean
    snack: boolean
    vipTag: string
    hallType: string[]
  }
  promotion: {
    cardPromotionTag: string
  }
}

export interface CinemaListParams {
  offset?: number
  day: string
  cityId: number
  brandId?: number
  serviceId?: number
  hallType?: number
  areaId?: number
  districtId?: number
}

// ==================== 场次相关 ====================
export interface ScheduleItem {
  id: number
  movieId: number
  cinemaId: number
  cinemaNm?: string
  cinemaAddr?: string
  hallName: string
  showDate: string
  showTime: string
  endTime: string
  lang: string
  totalSeats: number
  availableSeats: number
  price: number
}

export interface CinemaScheduleGroup {
  cinemaId: number
  cinemaName: string
  cinemaAddr: string
  schedules: ScheduleItem[]
}

// ==================== 座位相关 ====================
export interface SeatInfo {
  row: number
  col: number
  label: string
  status: number // 0=可选 1=已售 2=他人锁定 3=我锁定 -1=不可用
  couple: boolean
}

export interface SeatLayoutData {
  hallName: string
  hallType: string
  rows: number
  cols: number
  aisles: number[]
  coupleRows: number[]
  seats: SeatInfo[][]
}

export interface LockSeatsRequest {
  scheduleId: number
  seats: { row: number; col: number }[]
}

export interface LockSeatsResponse {
  orderNo: string
  lockToken: string
  expireTime: string
  seatCount: number
  totalPrice: number
  seatsInfo: string
  hallName: string
  showTime: string
}

// ==================== 订单相关 ====================
export interface OrderItem {
  id: number
  orderNo: string
  movieName: string
  cinemaName: string
  hallName: string
  showTime: string
  seatCount: number
  seatsInfo: string
  unitPrice: number
  totalPrice: number
  status: number
  statusDesc: string
  createTime: string
  payTime?: string
  expireTime?: string
  scheduleId: number
  movieImg?: string
}

// ==================== 搜索相关 ====================
export interface SearchParams {
  kw: string | number
  cityId: number
  stype: number
}

// ==================== Tab 类型 ====================
export type Tab = 'home' | 'movies' | 'cinemas'

// ==================== 排队相关 ====================
export interface QueueEnterResult {
  admitted: boolean
  token: string
  position: number
  estimatedWaitSeconds: number
}

export interface QueueStatusResult {
  admitted: boolean
  position: number
  estimatedWaitSeconds: number
}

// ==================== 用户相关 ====================
export interface UserAccountInfo {
  account: string
  password: string
  userNick?: string
  userHeadImg?: string
  likeList?: string[]
}
