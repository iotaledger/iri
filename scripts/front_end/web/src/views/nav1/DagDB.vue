<template>
    <el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit"
             style="margin:20px;width:100%;min-width:600px;">
        <el-form-item style="text-align: center">
            <div class="operation-div">
                <el-select v-model="form.server" placeholder="Choose a server" @change="chooseServer">
                    <el-option
                            v-for="item in serverList"
                            :key="item.name"
                            :label="item.name"
                            :value="item.value"
                    ></el-option>
                </el-select>
                <el-button @click="getDagMap" class="dag-button">DagMap</el-button>
                <el-button @click="getTotalOrder" class="dag-button">TotalOrder</el-button>
            </div>
        </el-form-item>
        <div id="dagChart" class="dag-chart"></div>
        <div class="detail-show">
            <pre></pre>
        </div>
    </el-form>
</template>

<script>
    let iplist = require("./ipConfig");
    let requestHost = "";
    let nameMap = {};
    let servers = [];
    for (let i in iplist.ips) {
        servers.push({
            name: iplist.ips[i],
            value: iplist.ips[i]
        })
    }

    function queryNodeDetail(name) {
        if (requestHost === "") {
            return;
        }
        let requestUrl = requestHost + "/get_block_content";
        let request = {};
        let requestData = {};
        requestData.hashes = [];
        requestData.hashes.push(nameMap[name]);
        request.requestUrl = requestUrl;
        request.requestData = JSON.stringify(requestData);
        let settings = {
            "async": true,
            "crossDomain": true,
            "url": "/api/QueryNodeDetail",
            "method": "POST",
            "headers": {
                "Content-Type": "application/json"
            },
            "processData": false,
            "data": JSON.stringify(request)
        };
        $.ajax(settings).done(function (response) {
            response = JSON.parse(response);
            if (response["Code"] === 0) {
                alert(response["Message"]);
            } else {
                let detail = response["Data"];
                detail = JSON.parse(detail.replace("']", "]").replace("['", "["));
                $(".detail-show pre").html(JSON.stringify(detail, null, 2));
            }
        });
    }

    export default {
        data() {
            return {
                form: {
                    server: ""
                },
                serverList: servers
            };
        },
        methods: {
            onSubmit() {
                console.log("submit!");
            },
            chooseServer(data) {
                requestHost = data;
            },
            getDagMap() {
                if (requestHost === "") {
                    return;
                }
                let requestUrl = "";
                requestUrl = requestHost + "/get_dag";
                let request = {};
                let requestData = {"type": "JSON"};
                request.requestUrl = requestUrl;
                request.requestData = JSON.stringify(requestData);
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        alert(data["Message"]);
                    } else {
                        let data = JSON.parse(res.data["Data"]);
                        let nodes = this.prepareMapData(data);
                        let relations = [];
                        for (let i in data) {
                            let b = data[i];
                            for (let j in b) {
                                let unit = {};
                                unit.target = i.substr(0, 6);
                                unit.source = b[j].substr(0, 6);
                                relations.push(unit);
                            }
                        }
                        this.drawDagMap(nodes, relations);
                    }
                }).catch((err) => {
                    console.error(err);
                })
            },
            drawDagMap(nodes, relations) {
                nameMap = {};
                let myChart = this.$echarts.init(document.getElementById("dagChart"));
                myChart.showLoading();
                // prepare nodes
                let datas = [];
                nodes.forEach(function (item) {
                    let shortName = item.id.substr(0, 6);
                    let unit = {
                        x: item.x,
                        y: item.y,
                        id: shortName,
                        name: shortName,
                        symbolSize: 20,
                        itemStyle: {
                            normal: {
                                color: "rgb(63, 167, 220)"
                            }
                        }
                    };
                    datas.push(unit);
                    nameMap[shortName] = item.id;
                });
                //prepare charts datas
                let series = [];
                let seriesData;
                seriesData = {
                    type: "graph",
                    layout: "none",
                    edgeSymbol: ["arrow"],
                    data: datas,
                    edges: relations.map(function (edge) {
                        return {
                            source: edge.source,
                            target: edge.target
                        };
                    }),
                    label: {
                        normal: {
                            show: true,
                            fontSize: 9
                        }
                    },
                    roam: true,
                    focusNodeAdjacency: true,
                    lineStyle: {
                        normal: {
                            width: 0.5,
                            curveness: 0,
                            opacity: 0.7
                        }
                    }
                };
                series[0] = seriesData;
                let mapOption = {};
                mapOption.title = {};
                mapOption.title.text = "Dag Map";
                mapOption.animationDurationUpdate = 1500;
                mapOption.animationEasingUpdate = "quinticInOut";
                mapOption.series = series;
                myChart.setOption(mapOption, true);
                myChart.on("click", function (param) {
                    let name = param.name;
                    queryNodeDetail(name);
                });
                myChart.hideLoading();
            },
            prepareMapData(data) {
                //get root node
                let root = "";
                let keys = [];
                let values = {};
                for (let i in data) {
                    keys.push(i);
                    let b = data[i];
                    for (let j in b) {
                        values[b[j]] = 1;
                    }
                }
                for (let i in keys) {
                    if (values[keys[i]] == null) {
                        root = keys[i];
                        break;
                    }
                }
                let resultData = [];
                let existNode = {};
                let startx = 0;
                let starty = 500;
                let unit = {0: 100, 1: -100};
                if (root !== "") {
                    let createMap = function (nodeName, data, x, y) {
                        if (!data[nodeName] || existNode[nodeName]) {
                            return;
                        } else {
                            let item = {};
                            item.id = nodeName;
                            item.x = x;
                            item.y = y;
                            resultData.push(item);
                            existNode[nodeName] = 1;
                            for (var i = 0, j = data[nodeName].length; i < j; i++) {
                                if (j > 1) {
                                    createMap(data[nodeName][i], data, x + 200, y + unit[i]);
                                } else {
                                    createMap(data[nodeName][i], data, x + 200, y);
                                }
                            }
                        }
                    };
                    createMap(root, data, startx, starty);
                }
                return resultData;
            },
            getTotalOrder() {
                if (requestHost === "") {
                    return;
                }
                let requestUrl = "";
                requestUrl = requestHost + "/get_total_order";
                let request = {};
                request.requestUrl = requestUrl;
                request.requestData = "";
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        alert(data["Message"]);
                    } else {
                        let data = JSON.parse(res.data["Data"]);
                        let nodes = this.prepareTreeData(data);
                        let relations = [];
                        for (let i = 0, j = data.length; i < j - 1; i++) {
                            let unit = {};
                            unit.source = data[i].substr(0, 6);
                            unit.target = data[i + 1].substr(0, 6);
                            relations.push(unit)
                        }
                        this.drawDagMap(nodes, relations);
                    }
                }).catch((err) => {
                    console.error(err);
                });
            },
            prepareTreeData(data) {
                let result = [];
                for (let i in data) {
                    let item = {};
                    item.id = data[i];
                    item.x = i * 100;
                    item.y = 300;
                    result.push(item);
                }
                return result;
            },
        }
    };
</script>
<style>
    .operation-div {
        float: left;
        width: 100%;
        text-align: center;
    }

    .dag-button {
        width: 200px;
    }

    .dag-chart {
        width: 98%;
        height: 500px;
        background: #f3f3f3 !important;
    }

    .detail-show {
        width: 98%;
        height: auto;
        text-align: left;
    }

    .detail-show pre {
        font-family: Arial, serif;
        color: forestgreen;
    }
</style>
