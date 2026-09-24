'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { ChevronDown, Search, User, Smartphone, Coins } from 'lucide-react'
import { useUserStore } from '@/store/user'
import { useHomeStore } from '@/store/home'
import BrandLogo from '@/components/BrandLogo'
import type { Tab } from '@/types'

interface HeaderProps {
  activeTab: Tab
  setActiveTab: (tab: Tab) => void
}

export default function Header({ activeTab, setActiveTab }: HeaderProps) {
  const router = useRouter()
  const { isLogged, userNick, points, logout } = useUserStore()
  const { position } = useHomeStore()
  const [searchText, setSearchText] = useState('')
  const [showUserMenu, setShowUserMenu] = useState(false)

  const handleSearch = () => {
    if (searchText.trim()) {
      router.push(`/search?kw=${encodeURIComponent(searchText)}`)
    }
  }

  return (
    <header className="border-b border-gray-200 bg-white sticky top-0 z-50 min-w-[1200px]">
      <div className="max-w-[1200px] mx-auto h-[80px] flex items-center justify-between">
        {/* Left: Logo & Nav */}
        <div className="flex items-center space-x-6 h-full">
          {/* Logo */}
          <div
            className="flex items-center cursor-pointer"
            onClick={() => setActiveTab('home')}
          >
            <BrandLogo priority />
          </div>

          {/* City Selector */}
          <div
            className="flex items-center cursor-pointer group"
            onClick={() => router.push('/position')}
          >
            <span className="text-gray-700 text-[15px]">{position}</span>
            <ChevronDown
              size={14}
              className="ml-1 text-gray-500 group-hover:rotate-180 transition-transform"
            />
          </div>

          {/* Nav */}
          <nav className="flex h-full space-x-8 ml-8">
            {([
              { key: 'home', label: '首页' },
              { key: 'movies', label: '电影' },
              { key: 'cinemas', label: '影院' },
            ] as { key: Tab; label: string }[]).map((item) => (
              <button
                key={item.key}
                onClick={() => setActiveTab(item.key)}
                className={`h-full px-4 text-lg font-medium transition-colors ${
                  activeTab === item.key
                    ? 'bg-primary text-white'
                    : 'text-gray-800 hover:text-primary'
                }`}
              >
                {item.label}
              </button>
            ))}
            <button className="h-full px-4 text-lg font-medium text-gray-800 hover:text-primary">
              演出
            </button>
          </nav>
        </div>

        {/* Right: App, Search, User */}
        <div className="flex items-center space-x-6">
          <div className="flex items-center text-gray-500 cursor-pointer hover:text-primary">
            <Smartphone size={18} className="mr-1" />
            <span className="text-sm">APP下载</span>
            <ChevronDown size={14} className="ml-1" />
          </div>

          {/* Search */}
          <div className="relative">
            <input
              type="text"
              placeholder="找影视剧、影人、影院"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className="bg-gray-100 rounded-full py-2 px-4 pr-10 text-sm w-52 focus:outline-none focus:ring-1 focus:ring-primary"
            />
            <div
              onClick={handleSearch}
              className="absolute right-1 top-1 w-8 h-8 bg-primary rounded-full flex items-center justify-center cursor-pointer"
            >
              <Search size={16} className="text-white" />
            </div>
          </div>

          {/* User */}
          <div className="relative">
            <div
              className="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center cursor-pointer"
              onClick={() => {
                if (isLogged) {
                  setShowUserMenu(!showUserMenu)
                } else {
                  router.push('/login')
                }
              }}
            >
              {isLogged ? (
                <span className="text-primary text-sm font-medium">
                  {userNick?.[0] || 'U'}
                </span>
              ) : (
                <User size={20} className="text-gray-500" />
              )}
            </div>

            {/* User dropdown */}
            {showUserMenu && isLogged && (
              <>
                <div
                  className="fixed inset-0 z-40"
                  onClick={() => setShowUserMenu(false)}
                />
                <div className="absolute right-0 top-12 z-50 bg-white border border-gray-200 rounded shadow-lg py-2 w-44">
                  <div className="px-4 py-2 text-sm text-gray-700 border-b border-gray-100">
                    {userNick}
                  </div>
                  <div className="px-4 py-2 text-sm text-gray-600 border-b border-gray-100 flex items-center gap-1">
                    <Coins size={14} className="text-amber-500" />
                    <span className="text-primary font-medium">{points}</span> 积分
                  </div>
                  <button
                    className="w-full text-left px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
                    onClick={() => { router.push('/orders'); setShowUserMenu(false) }}
                  >
                    我的订单
                  </button>
                  <button
                    className="w-full text-left px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
                    onClick={() => {
                      logout()
                      setShowUserMenu(false)
                    }}
                  >
                    退出登录
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  )
}
