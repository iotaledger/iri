import babelpolyfill from "babel-polyfill";
import Vue from "vue";
import App from "./App";
import ElementUI from "element-ui";
import "element-ui/lib/theme-chalk/index.css";
import VueRouter from "vue-router";
import store from "./vuex/store";
import Vuex from "vuex";
import routes from "./routes";
import axios from "axios";
import Vueaxios from "vue-axios";
import Echarts from "echarts";
import localConfig from "./common/config/config"
import Viz from "viz.js";
import Cookies from "js-cookie";
import "font-awesome/css/font-awesome.min.css";

Vue.use(ElementUI);
Vue.use(VueRouter);
Vue.use(Vuex);
Vue.use(localConfig);
Vue.use(Vueaxios, axios);
Vue.prototype.$echarts = Echarts;
Vue.prototype.$viz = Viz;

const router = new VueRouter({
    routes
});

router.beforeEach((to, from, next) => {
    if (to.path === '/login') {
        Cookies.remove("UserToken");
    }
    let token = Cookies.get("UserToken");
    // let token = "77b5601f-6d2b-4a27-be7c-82634b0d73d9";
    if (!token && to.path !== '/login') {
        next({path: '/login'})
    } else if (token) {
        let action = "/user/getUserInfo";
        let settings = {
            "async": false,
            "crossDomain": true,
            "timeout": 60000,
            "url": localConfig.oauthResource + action,
            "method": "GET",
            "headers": {
                "Authorization": "bearer " + token,
                "Accept": "*/*",
                "Cache-Control": "no-cache"
            }
        };
        $.ajax(settings).done(function (response) {
            if (response.code !== 1) {
                Cookies.remove("UserToken");
                console.log("Invalid user or token timeout,please login");
                next({path: "/login"})
            } else {
                let userInfo = response["data"];
                store.commit("setUserInfo",userInfo);
                if(store.state.pathMap[to.path] !== 1){
                    next({path: "/login"});
                }
                next();
            }
        });

    } else {
        next()
    }
});

new Vue({
    router,
    store,
    render: h => h(App)
}).$mount("#app");
