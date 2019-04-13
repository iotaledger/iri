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
    </el-form>
</template>

<script>
    let requestHost = "";
    export default {
        data() {
            return {
                form: {
                    server: ""
                },
                serverList: [
                    {"name": "http://192.168.199.105:5000", "value": "http://192.168.199.105:5000"}
                ]
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
                let action = "get_dag";
                requestHost += "/" + action;
                let request = {};
                let requestData = {"type": "JSON"};
                request.requestUrl = requestHost;
                request.requestData = JSON.stringify(requestData);
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        alert(data["Message"]);
                    } else {
                        let data = JSON.parse(res.data["Data"]);
                        this.drawDagMap(data);
                    }
                }).catch((err) => {
                    console.error(err);
                })
            },
            getTotalOrder() {
                if (requestHost === "") {
                    return;
                }
                let action = "get_total_order";
                requestHost += "/" + action;
                let request = {};
                request.requestUrl = requestHost;
                request.requestData = "";
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    console.log(res);
                }).catch((err) => {
                    console.error(err);
                })
            },
            drawDagMap(data) {
                let myChart = this.$echarts.init(document.getElementById("dagChart"));
                myChart.showLoading();
                // prepare nodes
                let nodes = {};
                let relations = [];
                for (let i in data) {
                    let unit = {};
                    nodes[i] = 1;
                    unit.target = i;
                    let b = data[i];
                    for (let j in b) {
                        nodes[b[j]] = 1;
                        unit.source = b[j];
                        relations.push(unit);
                    }
                }
                //prepare charts datas
                let series = [];
                let seriesData;
                seriesData = {
                    type: "graph",
                    layout: "force",
                    edgeSymbol: ['arrow'],
                    data: Object.keys(nodes).map(function (item) {
                        return {
                            x: null,
                            y: null,
                            id: item,
                            name: item,
                            symbolSize: 25,
                            itemStyle: {
                                normal: {
                                    color: "rgb(63, 167, 220)"
                                }
                            }
                        };
                    }),
                    edges: relations.map(function (edge) {
                        return {
                            source: edge.source,
                            target: edge.target
                        };
                    }),
                    label: {
                        emphasis: {
                            position: "right",
                            show: true
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
                    },
                    force: {
                        repulsion: 200,
                        edgeLength: [200, 200]
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
                    if (requestHost === "") {
                        return;
                    }
                    let action = "get_block_content";
                    requestHost += "/" + action;
                    let request = {};
                    let requestData = {"hashes": name};
                    request.requestUrl = requestHost;
                    request.requestData = JSON.stringify(requestData);
                    // $.ajax(
                    //
                    // );
                    this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                        console.log(res);
                    }).catch((err) => {
                        console.error(err);
                    })
                });
                myChart.hideLoading();
            },
        }
    }
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
</style>
