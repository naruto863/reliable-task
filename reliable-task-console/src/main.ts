import 'ant-design-vue/dist/reset.css'
import './styles/tokens.css'
import './styles/main.css'

import { Layout, Menu, Tag } from 'ant-design-vue'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { router } from './router'

createApp(App).use(createPinia()).use(router).use(Layout).use(Menu).use(Tag).mount('#app')
