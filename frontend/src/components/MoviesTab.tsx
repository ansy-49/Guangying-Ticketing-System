'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import MovieCard from '@/components/MovieCard'
import Loading from '@/components/Loading'
import api from '@/lib/api'
import type { MovieItem } from '@/types'

type SubTab = 'playing' | 'coming'
type SortType = 'hot' | 'time' | 'rating'

const GENRES = ['全部', '爱情', '喜剧', '动画', '剧情', '恐怖', '惊悚', '科幻', '动作', '悬疑', '犯罪', '冒险', '战争', '奇幻']
const REGIONS = ['全部', '大陆', '美国', '韩国', '日本', '中国香港', '中国台湾', '泰国', '印度', '法国', '英国', '俄罗斯']
const YEARS = ['全部', '2026', '2025', '2024', '2023', '2022', '2021', '2020', '2019', '2018']

export default function MoviesTab() {
  const router = useRouter()
  const [subTab, setSubTab] = useState<SubTab>('playing')
  const [sortType, setSortType] = useState<SortType>('hot')
  const [activeGenre, setActiveGenre] = useState('全部')
  const [activeRegion, setActiveRegion] = useState('全部')
  const [activeYear, setActiveYear] = useState('全部')
  const [movies, setMovies] = useState<MovieItem[]>([])
  const [loading, setLoading] = useState(true)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)

  const fetchMovies = useCallback((p: number = 1) => {
    setLoading(true)
    const params: any = {
      movieStatus: subTab === 'playing' ? 1 : 0,
      sortBy: sortType,
      page: p,
      pageSize: 30,
    }
    if (activeGenre !== '全部') params.cat = activeGenre
    if (activeRegion !== '全部') params.src = activeRegion
    if (activeYear !== '全部') params.year = parseInt(activeYear)

    api.filterMovies(params)
      .then((res) => {
        const list = res.movies || []
        if (p === 1) {
          setMovies(list)
        } else {
          setMovies((prev) => [...prev, ...list])
        }
        setTotal(res.total || 0)
        setHasMore(res.hasMore || false)
        setPage(p)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [subTab, sortType, activeGenre, activeRegion, activeYear])

  useEffect(() => {
    fetchMovies(1)
  }, [fetchMovies])

  const handleLoadMore = () => {
    if (hasMore && !loading) {
      fetchMovies(page + 1)
    }
  }

  return (
    <div>
      {/* Sub Navigation */}
      <div className="bg-[#47464a] h-[60px] flex items-center justify-center mb-8 -mx-[calc((100vw-1200px)/2)] min-w-[1200px]">
        <div className="flex space-x-12">
          <button
            onClick={() => { setSubTab('playing'); setPage(1) }}
            className={`h-[60px] px-2 font-medium ${
              subTab === 'playing'
                ? 'text-primary border-b-2 border-primary'
                : 'text-gray-300 hover:text-white transition-colors'
            }`}
          >
            正在热映
          </button>
          <button
            onClick={() => { setSubTab('coming'); setPage(1) }}
            className={`h-[60px] px-2 font-medium ${
              subTab === 'coming'
                ? 'text-primary border-b-2 border-primary'
                : 'text-gray-300 hover:text-white transition-colors'
            }`}
          >
            即将上映
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="mb-8 space-y-4 border border-gray-200 p-4 rounded-sm text-sm text-gray-600">
        <FilterRow
          label="类型："
          options={GENRES}
          active={activeGenre}
          onSelect={setActiveGenre}
        />
        <FilterRow
          label="区域："
          options={REGIONS}
          active={activeRegion}
          onSelect={setActiveRegion}
        />
        <FilterRow
          label="年代："
          options={YEARS}
          active={activeYear}
          onSelect={setActiveYear}
        />
      </div>

      {/* Sort Bar + Total */}
      <div className="flex items-center justify-between mb-6 mt-10">
        <div className="flex space-x-6 text-sm text-gray-500">
          <SortRadio label="按热门排序" active={sortType === 'hot'} onClick={() => setSortType('hot')} />
          <SortRadio label="按时间排序" active={sortType === 'time'} onClick={() => setSortType('time')} />
          <SortRadio label="按评价排序" active={sortType === 'rating'} onClick={() => setSortType('rating')} />
        </div>
        <span className="text-sm text-gray-400">共 {total} 部</span>
      </div>

      {/* Grid */}
      {loading && movies.length === 0 ? (
        <Loading />
      ) : movies.length === 0 ? (
        <div className="text-center py-20 text-gray-400">暂无相关电影</div>
      ) : (
        <>
          <div className="grid grid-cols-6 gap-x-8 gap-y-10">
            {movies.map((movie) => (
              <div
                key={movie.id}
                className="flex flex-col items-center cursor-pointer"
                onClick={() => router.push(`/movie-detail?movieId=${movie.id}`)}
              >
                <MovieCard movie={movie} showButton={false} />
                <div className="text-center w-full mt-[-20px]">
                  <h3 className="truncate font-medium text-gray-800 text-[16px] mb-1">
                    {movie.nm}
                  </h3>
                  {movie.globalReleased && movie.sc ? (
                    <div className="text-gold text-sm italic">
                      {Number(movie.sc).toFixed(1)}
                    </div>
                  ) : movie.globalReleased ? (
                    <div className="text-gray-400 text-sm">暂无评分</div>
                  ) : (
                    <div className="text-gold text-sm">{movie.wish}人想看</div>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* Load More */}
          {hasMore && (
            <div className="text-center py-8">
              <button
                onClick={handleLoadMore}
                disabled={loading}
                className="px-8 py-2 border border-gray-300 text-gray-500 rounded-full hover:bg-gray-50 transition-colors text-sm"
              >
                {loading ? '加载中...' : '加载更多'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function FilterRow({
  label,
  options,
  active,
  onSelect,
}: {
  label: string
  options: string[]
  active: string
  onSelect: (v: string) => void
}) {
  return (
    <div className="flex items-start">
      <span className="w-16 text-gray-400">{label}</span>
      <div className="flex flex-wrap gap-4 flex-1">
        {options.map((opt) => (
          <span
            key={opt}
            onClick={() => onSelect(opt)}
            className={`cursor-pointer ${
              opt === active
                ? 'bg-primary text-white px-2 rounded-xl'
                : 'hover:text-primary'
            }`}
          >
            {opt}
          </span>
        ))}
      </div>
    </div>
  )
}

function SortRadio({
  label,
  active,
  onClick,
}: {
  label: string
  active: boolean
  onClick: () => void
}) {
  return (
    <div className="flex items-center space-x-1 cursor-pointer" onClick={onClick}>
      <div className="w-4 h-4 rounded-full border border-gray-300 flex items-center justify-center">
        {active && <div className="w-2 h-2 bg-primary rounded-full" />}
      </div>
      <span className={active ? 'text-black' : ''}>{label}</span>
    </div>
  )
}
