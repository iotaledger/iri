import NotFound from './views/404.vue'
import Home from './views/Home.vue'
import Leviatom from './views/nav1/Leviatom.vue'
import StreamNet from "./views/nav1/StreamNet";
import NetCoin from "./views/nav1/NetCoin";
import DevOps from "./views/nav2/DevOps";
import Deployment from "./views/nav2/Deployment";
import Experiment from "./views/nav2/Experiment";
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
        iconCls: 'el-icon-share',
        children: [
            { path: '/leviatom', component: Leviatom, name: 'Leviatom' },
            { path: "/streamNet", component: StreamNet, name: "StreamNet" },
            { path: "/netCoin", component: NetCoin, name: "NetCoin" },
        ]
    },
    {
        path: '/',
        component: Home,
        name: 'Servers Platform',
        iconCls: 'el-icon-s-platform',
        children: [
            { path: "/server/devOps", component: DevOps, name: "DevOps" },
            { path: "/server/deployment", component: Deployment, name: "Deployment" },
            // { path: "/server/experiment", component: Experiment, name: "Experiment" },
        ]
    },
    {
        path: '*',
        hidden: true,
        redirect: { path: '/404' }
    }
];

export default routes;
