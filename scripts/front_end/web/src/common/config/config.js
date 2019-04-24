export default {
    install(Vue, option) {
        Vue.prototype.ipList = [
            {
                name: "112",
                value: "http://192.168.199.112"
            },
            {
                name: "http://192.168.199.113",
                value: "http://192.168.199.113"
            },
            {
                name: "http://192.168.199.114",
                value: "http://192.168.199.114:"
            }
        ];
        Vue.prototype.serverPort = {
            "http://192.168.199.112": [
                {
                    name: "Cli-5000",
                    value: "5000"
                },
                {
                    name: "Cli-6000",
                    value: "6000"
                },
                {
                    name: "Server-14700",
                    value: "14700"
                }
            ],
            "http://192.168.199.113": [
                {
                    name: "Cli-5000",
                    value: "5000"
                },
                {
                    name: "Server-14700",
                    value: "14700"
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
        }
    }
}
