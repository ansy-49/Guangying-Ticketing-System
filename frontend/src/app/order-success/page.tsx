'use client'

import { Suspense, useState, useEffect } from 'react'
import { useSearchParams, useRouter } from 'next/navigation'
import { CheckCircle2, Ticket, Home, Coins } from 'lucide-react'
import Loading from '@/components/Loading'
import api from '@/lib/api'
import type { OrderItem } from '@/types'

function OrderSuccessContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const orderNo = searchParams.get('orderNo')

  const [order, setOrder] = useState<OrderItem | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!orderNo) return
    api.getOrderDetail({ orderNo })
      .then((res) => setOrder(res.data || res))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [orderNo])

  if (loading) return <Loading />

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center pt-20">
      {/* 成功图标 */}
      <div className="w-20 h-20 rounded-full bg-green-100 flex items-center justify-center mb-6">
        <CheckCircle2 className="w-12 h-12 text-green-500" />
      </div>

      <h1 className="text-2xl font-bold text-gray-800 mb-2">支付成功！</h1>
      <p className="text-gray-500 mb-8">请凭订单信息到影院取票观影</p>

      {/* 订单卡片 */}
      {order && (
        <div className="bg-white rounded-lg shadow-sm w-[480px] overflow-hidden">
          {/* 影片信息 */}
          <div className="bg-gradient-to-r from-primary to-red-400 text-white p-6">
            <div className="text-xl font-bold mb-1">{order.movieName}</div>
            <div className="text-sm opacity-90">{order.cinemaName} · {order.hallName}</div>
          </div>

          {/* 详情 */}
          <div className="p-6 space-y-4 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-400">场次时间</span>
              <span className="text-gray-700">{order.showTime}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-400">座位信息</span>
              <span className="text-gray-700">{order.seatsInfo || `${order.seatCount}张`}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-400">订单编号</span>
              <span className="text-gray-700 font-mono text-xs">{order.orderNo}</span>
            </div>
            <div className="border-t border-gray-100 pt-4 flex justify-between items-baseline">
              <span className="text-gray-400">支付积分</span>
              <div className="flex items-center gap-1">
                <Coins className="w-4 h-4 text-amber-500" />
                <span className="text-2xl font-bold text-primary">{Math.ceil(Number(order.totalPrice))}</span>
                <span className="text-sm text-gray-400">积分</span>
              </div>
            </div>
          </div>

          {/* 取票码（模拟） */}
          <div className="border-t border-dashed border-gray-200 mx-6" />
          <div className="p-6 text-center">
            <div className="text-xs text-gray-400 mb-2">取票验证码</div>
            <div className="text-3xl font-bold tracking-[0.3em] text-gray-800">
              {String(Math.floor(Math.random() * 900000) + 100000)}
            </div>
            <div className="text-xs text-gray-400 mt-1">请到影院自助取票机输入此验证码取票</div>
          </div>
        </div>
      )}

      {/* 操作按钮 */}
      <div className="flex gap-4 mt-8">
        <button
          onClick={() => router.push('/orders')}
          className="flex items-center gap-2 px-6 py-3 border border-gray-300 rounded-full text-gray-700 hover:bg-gray-100 transition-colors"
        >
          <Ticket className="w-4 h-4" />
          查看我的订单
        </button>
        <button
          onClick={() => router.push('/')}
          className="flex items-center gap-2 px-6 py-3 bg-primary text-white rounded-full hover:bg-red-600 transition-colors"
        >
          <Home className="w-4 h-4" />
          返回首页
        </button>
      </div>
    </div>
  )
}

export default function OrderSuccessPage() {
  return (
    <Suspense fallback={<Loading />}>
      <OrderSuccessContent />
    </Suspense>
  )
}
