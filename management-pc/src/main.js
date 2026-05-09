import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import {
  ArrowDown,
  Bell,
  Box,
  ChatDotRound,
  ChatLineSquare,
  Check,
  CircleCheckFilled,
  CircleCloseFilled,
  Cpu,
  DataLine,
  Expand,
  Files,
  Fold,
  Goods,
  Grid,
  Key,
  Lightning,
  List,
  Lock,
  MagicStick,
  Money,
  Moon,
  Picture,
  Plus,
  Position,
  PriceTag,
  Refresh,
  Search,
  Service,
  Setting,
  Shop,
  ShoppingCart,
  Sunny,
  Ticket,
  Timer,
  TrendCharts,
  Upload,
  User,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './style.css'
import App from './App.vue'
import router from './router'

const conciseCopy = import.meta.env.VITE_CONCISE_COPY !== 'false'
if (conciseCopy && typeof document !== 'undefined') {
  document.documentElement.classList.add('copy-compact')
}

const app = createApp(App)

const icons = {
  ArrowDown,
  Bell,
  Box,
  ChatDotRound,
  ChatLineSquare,
  Check,
  CircleCheckFilled,
  CircleCloseFilled,
  Cpu,
  DataLine,
  Expand,
  Files,
  Fold,
  Goods,
  Grid,
  Key,
  Lightning,
  List,
  Lock,
  MagicStick,
  Money,
  Moon,
  Picture,
  Plus,
  Position,
  PriceTag,
  Refresh,
  Search,
  Service,
  Setting,
  Shop,
  ShoppingCart,
  Sunny,
  Ticket,
  Timer,
  TrendCharts,
  Upload,
  User,
  UserFilled,
  Wallet,
}

for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')

const setupCompactCopy = () => {
  import('./utils/uiTextNormalizer').then(({ setupUiTextNormalizer }) => {
    setupUiTextNormalizer()
  })
}

if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
  window.requestIdleCallback(setupCompactCopy, { timeout: 2000 })
} else {
  window.setTimeout(setupCompactCopy, 1200)
}
