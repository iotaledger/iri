export default {
    install(Vue, option) {
        Vue.prototype.ipList = [
            {
                name: "126",
                value: "http://192.168.199.126"
            }
        ];
        Vue.prototype.serverPort = {
            "http://192.168.199.126": [
                {
                    name: "Cli-5000",
                    value: "5000"
                },
                {
                    name: "Cli-6000",
                    value: "6000"
                }
            ]
        };
        Vue.prototype.messageOption = {
            warning: {
                type: "warning",
                confirmButtonText: "OK"
            },
            success: {
                type: "success",
                confirmButtonText: "OK"
            },
            error: {
                type: "error",
                confirmButtonText: "OK"
            }
        };
        Vue.prototype.requestMethod = {
            GET: "GET",
            POST: "POST"
        }
    }
}
