//test
export const increment = ({commit}) => {
    commit('INCREMENT')
};
export const decrement = ({commit}) => {
    commit('DECREMENT')
};

export const DEL_OAUTH_TOKEN = ({commit}) => {
    commit("DEL_OAUTH_TOKEN")
};

export const SET_OAUTH_TOKEN = ({commit}, {token}) => {
    commit("SET_OAUTH_TOKEN", {token})
};
