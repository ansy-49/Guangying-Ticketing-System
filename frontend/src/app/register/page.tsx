'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { useUserStore } from '@/store/user'
import BrandLogo from '@/components/BrandLogo'

export default function RegisterPage() {
  const router = useRouter()
  const { registerAsync } = useUserStore()
  const [nick, setNick] = useState('')
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [loading, setLoading] = useState(false)

  const handleRegister = async () => {
    if (!inviteCode.trim()) return toast.error('邀请码不能为空！')
    if (!nick.trim()) return toast.error('昵称不能为空！')
    if (!account.trim()) return toast.error('用户名不能为空！')
    if (!password) return toast.error('请输入密码！')

    setLoading(true)
    try {
      const result = await registerAsync(account, password, nick, inviteCode)
      if (result.success) {
        toast.success('注册成功，请登录')
        router.replace('/login')
      } else {
        toast.error(result.msg || '注册失败')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="w-[400px] bg-white rounded-lg shadow-lg p-8">
        <div className="flex items-center justify-center mb-8">
          <BrandLogo priority />
        </div>
        <h2 className="text-xl font-medium text-center mb-6">注 册</h2>
        <div className="space-y-4">
          <input
            type="text"
            placeholder="邀请码"
            value={inviteCode}
            onChange={(e) => setInviteCode(e.target.value)}
            className="w-full py-3 px-4 border border-gray-300 rounded outline-none text-base focus:border-primary"
          />
          <input
            type="text"
            placeholder="昵称"
            value={nick}
            onChange={(e) => setNick(e.target.value)}
            className="w-full py-3 px-4 border border-gray-300 rounded outline-none text-base focus:border-primary"
          />
          <input
            type="text"
            placeholder="用户名"
            value={account}
            onChange={(e) => setAccount(e.target.value)}
            className="w-full py-3 px-4 border border-gray-300 rounded outline-none text-base focus:border-primary"
          />
          <input
            type="password"
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleRegister()}
            className="w-full py-3 px-4 border border-gray-300 rounded outline-none text-base focus:border-primary"
          />
          <button
            onClick={handleRegister}
            disabled={loading}
            className="w-full py-3 bg-primary text-white rounded text-base disabled:opacity-60 hover:bg-red-600 transition-colors"
          >
            {loading ? '注册中...' : '注 册'}
          </button>
          <p className="text-sm text-gray-500 text-center">
            已有账号？
            <span
              className="text-secondary cursor-pointer hover:underline ml-1"
              onClick={() => router.push('/login')}
            >
              去登录
            </span>
          </p>
          <p className="text-sm text-gray-400 text-center">
            <span className="cursor-pointer hover:text-primary" onClick={() => router.push('/')}>
              返回首页
            </span>
          </p>
        </div>
      </div>
    </div>
  )
}
