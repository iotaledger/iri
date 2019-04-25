import NotFound from './views/404.vue'
import Home from './views/Home.vue'
import Leviatom from './views/nav1/Leviatom.vue'
import StreamNet from "./views/nav1/StreamNet";
import NetCoin from "./views/nav1/NetCoin";
let routes = [
    {
        path: '/404',
        component: NotFound,
        name: '',
        hidden: true
    },
    {
        path: '/',
        component: Home,
        name: 'Functioin List',
        iconCls: 'el-icon-share',//图标样式class
        children: [
            { path: '/leviatom', component: Leviatom, name: 'Leviatom' },
            { path: "/streamNet", component: StreamNet, name: "StreamNet" },
            { path: "/netCoin", component: NetCoin, name: "NetCoin" },
        ]
    },
    {
        path: '*',
        hidden: true,
        redirect: { path: '/404' }
    }
];

export default routes;
