const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080'

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/ajax/:path*',
        destination: `${BACKEND_URL}/ajax/:path*`,
      },
      {
        source: '/dianying/:path*',
        destination: `${BACKEND_URL}/dianying/:path*`,
      },
      {
        source: '/api/:path*',
        destination: `${BACKEND_URL}/api/:path*`,
      },
    ]
  },
}

export default nextConfig
