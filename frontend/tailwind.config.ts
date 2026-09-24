import type { Config } from 'tailwindcss'

const config: Config = {
  content: ['./src/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        primary: '#ef4238',
        secondary: '#2d98f3',
        gold: '#ffb400',
      },
    },
  },
  plugins: [],
}

export default config
