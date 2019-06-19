module.exports = {
    proxy: {
        "/api": {    //将www.exaple.com印射为/apis
            target: "http://192.168.199.126:8000",  // 接口域名
            secure: false,  // 如果是https接口，需要配置这个参数
            changeOrigin: true,  //是否跨域
            pathRewrite: {
                "^/api": ""   //需要rewrite的,
            }
        },
        "/trias-resource": {
            target: "http://localhost:9081",  // 接口域名
            secure: false,  // 如果是https接口，需要配置这个参数
            changeOrigin: true,  //是否跨域
            pathRewrite: {
                "^/trias-resource": "/trias-resource/"   //需要rewrite的,
            }
        }
    }
};
