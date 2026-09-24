'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import MovieCard from '@/components/MovieCard'
import Loading from '@/components/Loading'
import api from '@/lib/api'
import type { MovieItem, BoxOfficeItem } from '@/types'

// 硬编码的 TOP 100 数据
const TOP100 = [
  { id: 1, title: '我不是药神', score: 9.6 },
  { id: 2, title: '肖申克的救赎', score: 9.5 },
  { id: 3, title: '海上钢琴师', score: 9.3 },
  { id: 4, title: '绿皮书', score: 9.5 },
  { id: 5, title: '霸王别姬', score: 9.4 },
  { id: 6, title: '美丽人生', score: 9.3 },
  { id: 7, title: '这个杀手不太冷', score: 9.6 },
  { id: 8, title: '星际穿越', score: 9.3 },
  { id: 9, title: '泰坦尼克号', score: 9.6 },
  { id: 10, title: '盗梦空间', score: 9.0 },
]

export default function HomeTab() {
  const [hotMovies, setHotMovies] = useState<MovieItem[]>([])
  const [comingMovies, setComingMovies] = useState<MovieItem[]>([])
  const [loading, setLoading] = useState(true)

  // 硬编码的票房数据 (可以后续接真实接口)
  const boxOffice: BoxOfficeItem[] = [
    { id: 1, title: '流浪地球3', amount: 4271.7, unit: '万' },
    { id: 2, title: '烟火人间', amount: 2023.2, unit: '万' },
    { id: 3, title: '平凡英雄', amount: 1568.3, unit: '万' },
    { id: 4, title: '逐光者', amount: 1526.7, unit: '万' },
    { id: 5, title: '长安幻夜', amount: 883.6, unit: '万' },
  ]

  useEffect(() => {
    Promise.all([
      api.getMovieOnInfoList(),
      api.getComingList(),
    ])
      .then(([hotRes, comingRes]) => {
        setHotMovies(hotRes.movieList?.slice(0, 8) || [])
        setComingMovies(comingRes.coming?.slice(0, 8) || [])
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const router = useRouter()

  if (loading) return <Loading />

  return (
    <div className="flex gap-10">
      {/* LEFT COLUMN: Movies */}
      <div className="flex-1">
        {/* 正在热映 */}
        <div className="mb-12">
          <div className="flex justify-between items-end mb-6">
            <h2 className="text-2xl text-primary font-normal">
              正在热映{' '}
              <span className="text-2xl text-primary ml-1">
                （{hotMovies.length}部）
              </span>
            </h2>
            <a className="flex items-center text-primary hover:underline text-sm cursor-pointer">
              全部 <ChevronRight size={14} />
            </a>
          </div>

          <div className="grid grid-cols-4 gap-6">
            {hotMovies.map((movie) => (
              <div key={movie.id} className="flex flex-col items-center">
                <MovieCard movie={movie} showButton={false} />
                <button
                  onClick={() => router.push(`/movie-detail?movieId=${movie.id}`)}
                  className="w-full max-w-[160px] py-1 text-primary bg-white border border-gray-200 shadow-sm rounded-full hover:bg-primary hover:text-white transition-colors text-sm -mt-2"
                >
                  购票
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* 即将上映 */}
        <div>
          <div className="flex justify-between items-end mb-6 border-b border-gray-100 pb-2">
            <h2 className="text-2xl text-secondary font-normal">
              即将上映{' '}
              <span className="text-2xl text-secondary ml-1">
                （{comingMovies.length}部）
              </span>
            </h2>
            <a className="flex items-center text-secondary hover:underline text-sm cursor-pointer">
              全部 <ChevronRight size={14} />
            </a>
          </div>

          <div className="grid grid-cols-4 gap-6">
            {comingMovies.map((movie) => (
              <div key={movie.id} className="flex flex-col items-center">
                <MovieCard movie={movie} showButton={true} />
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* RIGHT COLUMN: Sidebar */}
      <div className="w-[360px] shrink-0">
        {/* 今日票房 */}
        <div className="mb-10">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-xl text-primary">今日票房</h3>
          </div>

          <div className="bg-[#fdfdfd] border border-gray-100 p-4 shadow-sm">
            {/* Top 1 */}
            <div className="flex gap-3 mb-4 bg-gray-50 p-2 border border-gray-100 relative overflow-hidden">
              <div className="absolute top-0 left-0 w-6 h-6 bg-primary text-white flex items-center justify-center text-xs font-bold z-10">
                1
              </div>
              <img
                src="https://placehold.co/60x80?text=1"
                alt=""
                className="w-16 h-20 object-cover"
              />
              <div className="flex flex-col justify-center flex-1">
                <h4 className="font-bold text-gray-800">
                  {boxOffice[0].title}
                </h4>
                <p className="text-primary text-sm mt-2 font-bold">
                  {boxOffice[0].amount}
                  {boxOffice[0].unit}
                </p>
              </div>
            </div>

            {/* List 2-5 */}
            <ul className="space-y-3">
              {boxOffice.slice(1).map((item, idx) => (
                <li
                  key={item.id}
                  className="flex justify-between items-center text-sm"
                >
                  <div className="flex items-center flex-1 overflow-hidden">
                    <span className="text-gray-400 italic mr-3 w-3">
                      {idx + 2}
                    </span>
                    <span className="truncate text-gray-700">{item.title}</span>
                  </div>
                  <span className="text-primary text-xs">
                    {item.amount}
                    {item.unit}
                  </span>
                </li>
              ))}
            </ul>
          </div>

          {/* Total Box Office */}
          <div className="mt-4 bg-primary text-white p-4 flex justify-between items-center shadow-md">
            <div>
              <div className="flex items-end">
                <span className="text-3xl font-bold">10273.5</span>
                <span className="text-sm mb-1 ml-1">万</span>
              </div>
              <div className="text-[10px] opacity-80 mt-1">
                北京时间 21:54:06{' '}
                <span className="ml-2">光影票务实时票房数据</span>
              </div>
            </div>
            <div className="text-xs flex items-center cursor-pointer hover:opacity-80">
              查看更多 <ChevronRight size={12} />
            </div>
          </div>
        </div>

        {/* 最受期待 */}
        <div className="mb-10">
          <div className="flex justify-between items-end mb-4">
            <h3 className="text-xl text-gold">最受期待</h3>
            <a className="flex items-center text-gold hover:underline text-xs cursor-pointer">
              查看完整榜单 <ChevronRight size={12} />
            </a>
          </div>

          {/* Top 1 Large */}
          {comingMovies[0] && (
            <div className="mb-4 bg-white border border-gray-100 p-0 relative group cursor-pointer overflow-hidden">
              <div className="w-full h-40 overflow-hidden relative">
                <img
                  src={comingMovies[0].img}
                  className="w-full object-cover -mt-10"
                  alt="Top1"
                />
                <div className="absolute top-0 left-2 bg-gold text-white w-6 h-6 flex items-center justify-center font-bold shadow-md text-sm">
                  1
                </div>
                <div className="absolute bottom-0 w-full bg-gradient-to-t from-black/70 to-transparent p-2 text-white">
                  <div className="font-bold">{comingMovies[0].nm}</div>
                  <div className="text-xs text-gray-200">
                    上映时间：{comingMovies[0].comingTitle || comingMovies[0].pubDesc}
                  </div>
                  <div className="text-xs text-gold">
                    {comingMovies[0].wish}人想看
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Row of 2 & 3 */}
          {comingMovies.length >= 3 && (
            <div className="flex gap-3 mb-4">
              {[comingMovies[1], comingMovies[2]].map((m, idx) => (
                <div key={m.id} className="flex-1 relative cursor-pointer">
                  <div className="w-full h-28 overflow-hidden relative bg-gray-100">
                    <img
                      src={m.img}
                      className="w-full h-full object-cover"
                      alt=""
                    />
                    <div className="absolute top-0 left-0 bg-gold text-white w-5 h-5 flex items-center justify-center text-xs font-bold shadow">
                      {idx + 2}
                    </div>
                  </div>
                  <div className="mt-1">
                    <h4 className="font-bold text-sm truncate">{m.nm}</h4>
                    <p className="text-xs text-gold">{m.wish}人想看</p>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* List 4-8 */}
          <ul className="space-y-4">
            {comingMovies.slice(3).map((m, idx) => (
              <li
                key={m.id}
                className="flex justify-between items-center text-sm"
              >
                <div className="flex items-center">
                  <span className="text-gray-400 italic mr-3 w-3">
                    {idx + 4}
                  </span>
                  <span className="text-gray-600">{m.nm}</span>
                </div>
                <span className="text-gold text-xs">{m.wish}人想看</span>
              </li>
            ))}
          </ul>
        </div>

        {/* TOP 100 */}
        <div className="mb-10">
          <div className="flex justify-between items-end mb-4">
            <h3 className="text-xl text-gold">TOP 100</h3>
            <a className="flex items-center text-gold hover:underline text-xs cursor-pointer">
              查看完整榜单 <ChevronRight size={12} />
            </a>
          </div>

          {/* Top 1 */}
          <div className="flex gap-3 mb-4 bg-gray-50 p-2 border border-gray-100 relative cursor-pointer">
            <div className="absolute top-0 left-0 w-6 h-6 bg-gold text-white flex items-center justify-center text-xs font-bold z-10">
              1
            </div>
            <img
              src="https://placehold.co/60x80?text=TOP1"
              alt=""
              className="w-16 h-20 object-cover"
            />
            <div className="flex flex-col justify-center flex-1">
              <h4 className="font-bold text-gray-800">{TOP100[0].title}</h4>
              <p className="text-gold text-xl font-bold italic mt-2">
                {TOP100[0].score}分
              </p>
            </div>
          </div>

          <ul className="space-y-4">
            {TOP100.slice(1).map((m) => (
              <li
                key={m.id}
                className="flex justify-between items-center text-sm"
              >
                <div className="flex items-center">
                  <span className="text-gray-400 italic mr-3 w-3">{m.id}</span>
                  <span className="text-gray-600">{m.title}</span>
                </div>
                <span className="text-gold text-xs font-bold italic">
                  {m.score}分
                </span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}
