/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  corePlugins: {
    // Element Plus 已提供完整组件基础样式；关闭 Tailwind Preflight，避免 reset 在
    // 应用样式入口中后加载时覆盖 el-button 等组件的默认背景/边框。
    preflight: false,
  },
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      colors: {
        brand: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
        },
        violetBrand: {
          50: '#f5f3ff',
          100: '#ede9fe',
          200: '#ddd6fe',
          300: '#c4b5fd',
          400: '#a78bfa',
          500: '#8b5cf6',
          600: '#7c3aed',
          700: '#6d28d9',
          800: '#5b21b6',
          900: '#4c1d95',
        },
      },
      boxShadow: {
        soft: '0 18px 45px rgba(15, 23, 42, 0.08)',
        glow: '0 18px 45px rgba(99, 102, 241, 0.18)',
        float: '0 24px 80px rgba(15, 23, 42, 0.14)',
      },
      borderRadius: {
        '2xl': '1.25rem',
        '3xl': '1.75rem',
      },
      backgroundImage: {
        'brand-gradient': 'linear-gradient(135deg, #38bdf8 0%, #6366f1 52%, #8b5cf6 100%)',
        'page-glow': 'radial-gradient(circle at 12% 12%, rgba(59, 130, 246, 0.16), transparent 34%), radial-gradient(circle at 88% 8%, rgba(139, 92, 246, 0.14), transparent 30%), linear-gradient(135deg, #f8fbff 0%, #f6f7ff 48%, #ffffff 100%)',
      },
    },
  },
  plugins: [],
}
