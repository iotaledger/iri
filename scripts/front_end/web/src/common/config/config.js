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
        };
        Vue.prototype.Servers = {
            deployType: [
                {
                    name: "3_clique",
                    value: "3_clique"
                },
                {
                    name: "4_circle",
                    value: "4_circle"
                },
                {
                    name: "4_clique",
                    value: "4_clique"
                },
                {
                    name: "7_bridge",
                    value: "7_bridge"
                },
                {
                    name: "7_circle",
                    value: "7_circle"
                },
                {
                    name: "7_clique",
                    value: "7_clique"
                },
                {
                    name: "7_star",
                    value: "7_star"
                },
                {
                    name: "all_topology",
                    value: "all_topology",
                    experiment: true
                }
            ],
            serverList: {
                deploymentServer: "http://13.229.201.108:8080",
                opsServer: "http://13.229.201.108:5001"
            }
        };
    },
}
