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
                <el-select v-model="form.port" placeholder="Choose a port" @change="choosePort">
                    <el-option
                            v-for="item in portList"
                            :key="item.name"
                            :label="item.name"
                            :value="item.value"
                    ></el-option>
                </el-select>
                <el-button @click="getDagMap" class="dag-button">DagMap</el-button>
                <el-button @click="getTotalOrder" class="dag-button">TotalOrder</el-button>
            </div>
        </el-form-item>
        <div id="scale_div">
            <el-button icon="el-icon-plus" @click="scalePlus"></el-button>
            <el-button icon="el-icon-minus" @click="scaleMinus"></el-button>
        </div>
        <div id="dagChart" class="dag-chart"></div>
        <div class="detail-show">
            <pre></pre>
        </div>
    </el-form>
</template>

<script>
    let requestHost = "";
    let nameMap = {};
    let requestServer;
    let requestPort;

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
        request.requestMethod = "GET";
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
                detail = JSON.parse(detail.replace("']", "]").replace("['", "[").replace(/\', \'/g, ","));
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
                serverList: this.ipList,
                portList: [],
                dagScale: 1
            };
        },
        methods: {
            onSubmit() {
                console.log("submit!");
            },
            chooseServer(data) {
                requestServer = data;
                this.portList = this.serverPort[data];
            },
            choosePort(data) {
                requestPort = data;
            },
            getDagMap() {
                if (!requestServer || !requestPort) {
                    this.$alert("Please choose server and port", "Warning", this.messageOption.warning);
                    return;
                }
                let requestUrl = "";
                requestHost = requestServer + ":" + requestPort;
                requestUrl = requestHost + "/get_dag";
                let request = {};
                let requestData = {"type": "JSON"};
                request.requestUrl = requestUrl;
                request.requestData = JSON.stringify(requestData);
                request.requestMethod = this.requestMethod.GET;
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        alert(data["Message"]);
                    } else {
                        let data = {};
                        try {
                            data = JSON.parse(res.data["Data"]);
                        } catch (e) {
                            console.error("message format error:" + res.data["Data"])
                        }
                        let digraph = this.prepareVizMap(data);
                        this.drawVizGraph(digraph, {format: "svg"});
                    }
                }).catch((err) => {
                    console.error(err);
                })
            },
            prepareVizMap(data) {
                let resultHtml = "digraph {rankdir=LR;";
                for (let i in data) {
                    let b = data[i];
                    nameMap[i.substr(0, 6)] = i;
                    for (let j in b) {
                        nameMap[b[j].substr(0, 6)] = b[j];
                        resultHtml += "\"" + i.substr(0, 6) + "\"->\"" + b[j].substr(0, 6) + "\";";
                    }
                }
                resultHtml += "}";
                return resultHtml;
            },
            getTotalOrder() {
                if (!requestServer || !requestPort) {
                    this.$alert("Please choose server and port", "Warning", this.messageOption.warning);
                    return;
                }
                let requestUrl = "";
                requestHost = requestServer + ":" + requestPort;
                requestUrl = requestHost + "/get_total_order";
                let request = {};
                request.requestUrl = requestUrl;
                request.requestData = "";
                request.requestMethod = this.requestMethod.GET;
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        alert(data["Message"]);
                    } else {
                        let data = {};
                        try {
                            data = JSON.parse(res.data["Data"]);
                        } catch (e) {
                            console.error("message format error:" + res.data["Data"])
                        }
                        let digraph = this.prepareTreeData(data);
                        this.drawVizGraph(digraph, {format: "svg"});
                    }
                }).catch((err) => {
                    console.error(err);
                });
            },
            prepareTreeData(data) {
                let resultHtml = "digraph {rankdir=LR;";
                for (let i = 0, j = data.length; i < j - 1; i++) {
                    nameMap[data[i].substr(0, 6)] = data[i];
                    nameMap[data[i + 1].substr(0, 6)] = data[i + 1];
                    resultHtml += "\"" + data[i].substr(0, 6) + "\"->\"" + data[i + 1].substr(0, 6) + "\";";
                }
                resultHtml += "}";
                return resultHtml;
            },
            drawVizGraph(digraph, option) {
                let svgXml = this.$viz(digraph, option);
                $("#dagChart").html(svgXml);
                $("#dagChart .node").off("click").on("click", function () {
                    queryNodeDetail($(this).children("title").text());
                })
            },
            scalePlus() {
                this.setScale("#dagChart svg", 0.1);
            },
            scaleMinus() {
                this.setScale("#dagChart svg", -0.1);
            },
            setScale(dom, value) {
                if ($(dom).css("-webkit-transform") === undefined) {
                    return;
                }
                this.dagScale += value;
                let scale = "scale(" + this.dagScale + ")";
                $(dom).css({"-webkit-transform": scale, "-webkit-transform-origin": "top left"});
            }
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
        overflow-x: auto;
        margin-top: 100px;
    }

    .detail-show {
        width: 98%;
        height: auto;
        background: #f3f3f3 !important;
        text-align: left;
    }

    .detail-show pre {
        font-family: Arial, serif;
        color: forestgreen;
    }

    .dag-chart .node {
        cursor: pointer;
    }
</style>
