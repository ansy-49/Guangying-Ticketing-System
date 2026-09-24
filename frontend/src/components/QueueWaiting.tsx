'use client'

import { useState, useEffect, useCallback } from 'react'
import { Clock, Users } from 'lucide-react'
import api from '@/lib/api'

interface QueueWaitingProps {
  scheduleId: number
  position: number
  estimatedWaitSeconds: number
  onAdmitted: () => void
  onLeave: () => void
}

export default function QueueWaiting({
  scheduleId,
  position: initialPosition,
  estimatedWaitSeconds: initialEstimatedWait,
  onAdmitted,
  onLeave,
}: QueueWaitingProps) {
  const [position, setPosition] = useState(initialPosition)
  const [estimatedWait, setEstimatedWait] = useState(initialEstimatedWait)
  const [secondsLeft, setSecondsLeft] = useState(initialEstimatedWait)

  // 倒计时显示
  const formatWait = (seconds: number) => {
    if (seconds <= 0) return '即将入场'
    const m = Math.floor(seconds / 60)
    const s = seconds % 60
    return m > 0 ? `${m}分${s}秒` : `${s}秒`
  }

  // 轮询排队状态
  useEffect(() => {
    // 先不要立即轮询，用初始值
    const interval = setInterval(async () => {
      try {
        const res = await api.queueStatus({ scheduleId })
        const data = res.data || res
        if (data.admitted) {
          onAdmitted()
          return
        }
        setPosition(data.position)
        setEstimatedWait(data.estimatedWaitSeconds)
        setSecondsLeft(data.estimatedWaitSeconds)
      } catch {
        // 静默处理
      }
    }, 5000) // 每5秒轮询

    return () => clearInterval(interval)
  }, [scheduleId, onAdmitted])

  // 倒计时更新
  useEffect(() => {
    if (estimatedWait <= 0) return
    const timer = setInterval(() => {
      setSecondsLeft((prev) => Math.max(0, prev - 1))
    }, 1000)
    return () => clearInterval(timer)
  }, [estimatedWait])

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="bg-white rounded-lg shadow-md p-10 max-w-md w-full text-center">
        <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-primary/10 flex items-center justify-center">
          <Users className="w-10 h-10 text-primary" />
        </div>

        <h2 className="text-xl font-bold text-gray-800 mb-2">
          排队等候中
        </h2>
        <p className="text-gray-500 text-sm mb-8">
          当前场次购票人数较多，已开启排队模式，请耐心等候
        </p>

        {/* 排队位置 */}
        <div className="bg-gray-50 rounded-lg p-6 mb-6">
          <div className="text-4xl font-bold text-primary mb-1">
            {position}
          </div>
          <div className="text-sm text-gray-500">当前排队位置</div>
        </div>

        {/* 预估等待 */}
        <div className="flex items-center justify-center gap-2 text-gray-600 mb-8">
          <Clock className="w-4 h-4" />
          <span className="text-sm">预计等待 {formatWait(secondsLeft)}</span>
        </div>

        {/* 进度条 */}
        <div className="w-full bg-gray-200 rounded-full h-1.5 mb-8">
          <div
            className="bg-primary h-1.5 rounded-full transition-all duration-1000"
            style={{ width: `${Math.max(5, Math.min(100, (1 / position) * 100))}%` }}
          />
        </div>

        <p className="text-xs text-gray-400 mb-6">
          请不要关闭页面，入场后将自动进入选座界面<br />
          页面刷新排队位置不变
        </p>

        <button
          onClick={onLeave}
          className="text-sm text-gray-400 hover:text-gray-600 underline"
        >
          放弃排队，返回上一页
        </button>
      </div>
    </div>
  )
}
