import babelpolyfill from 'babel-polyfill'
import Vue from 'vue'
import App from './App'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-default/index.css'
import VueRouter from 'vue-router'
import store from './vuex/store'
import Vuex from 'vuex'
import routes from './routes'
import axios from 'axios'
import Vueaxios from 'vue-axios'


import 'font-awesome/css/font-awesome.min.css'

Vue.use(ElementUI);
Vue.use(VueRouter);
Vue.use(Vuex);
Vue.use(Vueaxios,axios);

const router = new VueRouter({
    routes
});

new Vue({
    router,
    store,
    render: h => h(App)
}).$mount('#app');



