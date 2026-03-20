import type { NextConfig } from 'next'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080'

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/',
        has: [{ type: 'query', key: 'url' }],
        destination: `${BACKEND_URL}/`,
      },
      {
        source: '/api/:path*',
        destination: `${BACKEND_URL}/api/:path*`,
      },
    ]
  },
}

export default nextConfig
