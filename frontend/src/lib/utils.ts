import type { CityItem, CityGroup } from '@/types'

/** 处理图片 URL - 支持占位图和 CDN 图 */
export function imgUrlReplace(img: string): string {
  if (!img) return 'https://picsum.photos/seed/default/180/250'
  if (img.startsWith('http')) return img
  return img.replace('w.h', '128.180')
}

/** 截断过长的定位名称 */
export function formatPosition(pos: string): string {
  return pos.length > 4 ? `${pos.substring(0, 3)}...` : pos
}

/** 格式化日期为 yyyy-MM-dd（与后端 LocalDate 格式一致） */
export function formatDate(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 按拼音首字母分组城市列表 */
export function formatCityList(list: CityItem[]): CityGroup[] {
  const sorted = [...list].sort((a, b) => a.py.localeCompare(b.py))
  const cityList: CityGroup[] = []
  let items: CityItem[] = []
  let tag = ''

  for (const city of sorted) {
    const firstLetter = city.py[0]?.toLowerCase() || ''
    if (firstLetter !== tag) {
      if (items.length > 0) {
        cityList.push({ tag, items })
      }
      tag = firstLetter
      items = [city]
    } else {
      items.push(city)
    }
  }
  if (items.length > 0) {
    cityList.push({ tag, items })
  }
  return cityList
}
