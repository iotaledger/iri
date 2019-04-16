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
    let nameMap = {};
    export default {
        data() {
            return {
                form: {
                    server: ""
                },
                serverList: [
                    {"name": "http://192.168.199.112:5000", "value": "http://192.168.199.112:5000"}
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
                        this.drawDagMap(data);
                    }
                }).catch((err) => {
                    console.error(err);
                })
            },
            drawDagMap(data) {
                let myChart = this.$echarts.init(document.getElementById("dagChart"));
                myChart.showLoading();
                // prepare nodes
                let nodes = this.prepareMapData(data);
                let datas = [];
                nodes.forEach(function (item) {
                    let shortName = item.id.substr(0,6);
                    let unit = {
                        x: item.x,
                        y: item.y,
                        id: shortName,
                        name: shortName,
                        symbolSize: 15,
                        itemStyle: {
                            normal: {
                                color: "rgb(63, 167, 220)"
                            }
                        }
                    };
                    datas.push(unit);
                    nameMap[nameMap] = item.id;
                });
                console.log(JSON.stringify(datas));
                let relations = [];
                for (let i in data) {
                    let b = data[i];
                    for (let j in b) {
                        let unit = {};
                        unit.target = i.substr(0,6);
                        unit.source = b[j].substr(0,6);
                        relations.push(unit);
                    }
                }
                console.log(relations);
                //prepare charts datas
                let series = [];
                let seriesData;
                seriesData = {
                    type: "graph",
                    layout: "none",
                    edgeSymbol: ['arrow'],
                    data: datas,
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
                        repulsion: 150,
                        edgeLength: [50, 250]
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
                let unit = {0: 80, 1: -80};
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
                                    createMap(data[nodeName][i], data, x + 100, y + unit[i]);
                                } else {
                                    createMap(data[nodeName][i], data, x + 100, y);
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
                        this.drawOrderMap(data);
                    }
                }).catch((err) => {
                    console.error(err);
                })
            },
            drawOrderMap(data) {
                let myChart = this.$echarts.init(document.getElementById("dagChart"));
                myChart.showLoading();
                let treeData = this.prepareTreeData(data);
                let series = [];
                let seriesData;
                seriesData = {
                    type: 'tree',
                    data: [treeData],
                    top: '1%',
                    left: '2%',
                    bottom: '1%',
                    right: '2%',
                    symbolSize: 10,
                    label: {
                        normal: {
                            position: 'left',
                            verticalAlign: 'middle',
                            align: 'right',
                            fontSize: 9
                        }
                    },
                    leaves: {
                        label: {
                            normal: {
                                position: 'right',
                                verticalAlign: 'middle',
                                align: 'left'
                            }
                        }
                    },
                    expandAndCollapse: false,
                    animationDuration: 550,
                    animationDurationUpdate: 750
                };
                series[0] = seriesData;
                let mapOption = {};
                mapOption.title = {};
                mapOption.title.text = "Dag Map";
                mapOption.series = series;
                mapOption.tooltip = {trigger: 'item', triggerOn: 'mousemove'};
                myChart.setOption(mapOption, true);
                myChart.on("click", function (param) {
                    if (requestHost === "") {
                        return;
                    }
                    let action = "get_block_content";
                    requestHost += "/" + action;
                    let request = {};
                    let requestData = {"hashes": param.name};
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
            prepareTreeData(data) {
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
                // create tree
                let treeData = {};
                let existNode = {};
                if (root !== "") {
                    let createTree = function (nodeName, data, treeData) {
                        treeData.name = nodeName.substr(0, 6);
                        if (!data[nodeName] || existNode[nodeName]) {
                            return treeData;
                        } else {
                            existNode[nodeName] = 1;
                            if (!treeData.children) {
                                treeData.children = [];
                            }
                            for (var i in data[nodeName]) {
                                let item = {};
                                treeData.children.push(createTree(data[nodeName][i], data, item));
                            }
                        }
                        return treeData;
                    };
                    createTree(root, data, treeData);
                }
                return treeData;
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
