import 'ant-design-vue/dist/reset.css'
import './styles/tokens.css'
import './styles/main.css'

import { Layout, Menu, Tag } from 'ant-design-vue'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { router } from './router'

// 控制台是独立 Vite 应用，仅按需注册当前 shell 使用到的 Ant Design Vue 组件。
// 业务页面里的其余 UI 仍以本地 HTML/CSS 为主，降低预览控制台的依赖面。
createApp(App).use(createPinia()).use(router).use(Layout).use(Menu).use(Tag).mount('#app')
