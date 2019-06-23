import Vue from 'vue';
import Vuex from 'vuex';
import * as actions from './actions';
import * as getters from './getters';

Vue.use(Vuex);

// 应用初始状态
const state = {
    count: 10,
    pathMap: {},
    rootMap: {},
    userInfo: {}
};
// 定义所需的 mutations
const mutations = {
    INCREMENT(state) {
        state.count++
    },
    DECREMENT(state) {
        state.count--
    },
    setUserInfo(state, userInfo) {
        let resourceList = userInfo.resourceList;
        state.rootMap = {};
        state.pathMap = {};
        state.pathMap["/addition"] = 1;
        state.pathMap["/"] = 1;
        for (let i in resourceList) {
            state.rootMap[resourceList[i]["rootName"]] = 1;
            state.pathMap[resourceList[i]["path"]] = 1;
        }
        state.userInfo = userInfo.userInfo;
    }
};

// 创建 store 实例
export default new Vuex.Store({
    actions,
    getters,
    state,
    mutations
});
