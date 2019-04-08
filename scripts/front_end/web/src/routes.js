import NotFound from './views/404.vue'
import Home from './views/Home.vue'
import Approve from './views/nav1/Approve.vue'
import Form from './views/nav1/LinkMap.vue'
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
        iconCls: 'el-icon-message',//图标样式class
        children: [
            { path: '/approve', component: Approve, name: 'Approve' },
            { path: '/linkMap', component: Form, name: 'Link-Map' },
        ]
    },
    {
        path: '*',
        hidden: true,
        redirect: { path: '/404' }
    }
];

export default routes;