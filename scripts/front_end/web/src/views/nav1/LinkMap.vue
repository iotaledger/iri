<template>
    <el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit"
             style="margin:20px;width:100%;min-width:600px;">
        <el-form-item>
            <label class="form-lable">DagAddress：</label>
            <el-input v-model="form.dag" class="input-small" placeholder="Dag server default localhost"
                      @change="setDag"/>
            <label class="form-lable">Period：</label>
            <el-input v-model="form.period" class="input-small" placeholder="Please input period" @change="setPeriod"/>
            <label class="form-lable">numRank：</label>
            <el-input v-model="form.numRank" class="input-small" placeholder="Please input numRank"
                      @change="setNumRank"/>
            <el-button type="primary" @click="queryData">QueryNodes</el-button>
        </el-form-item>
        <el-form-item>
            <textarea id="dagResult" class="textarea-dag-result"></textarea>
        </el-form-item>
        <div id="relationChart" class="dag-map"></div>
    </el-form>
</template>

<script>
    let requestData = {};
    let dagUrl;
    export default {
        data() {
            return {
                form: {
                    dag: "",
                    period: "",
                    numRank: ""
                }
            };
        },
        methods: {
            onSubmit() {
                console.log("submit!");
            },
            setDag(val) {
                dagUrl = val;
            },
            setPeriod(val) {
                requestData.period = val * 1;
            },
            setNumRank(val) {
                requestData.numRank = val * 1;
            },
            queryData() {
                $("#dagResult").val("");
                let requestUrl = "http://" + (dagUrl || window.location.host) + "/api/QueryData";
                this.axios.post(requestUrl, requestData).then((res) => {//success callback
                    if (res.data["Code"] === 0) {
                        alert(data["Message"]);
                    } else {
                        let data = res.data["Data"];
                        let scoreList = data["DataScore"];
                        let relationMap = data["DataCtx"];
                        this.showResultMessage(scoreList);
                        this.drawRelationMap(relationMap);
                    }

                }).catch((error) => {
                    console.error(error);
                });
            },
            drawRelationMap(data) {
                let myChart = this.$echarts.init(document.getElementById("relationChart"));
                myChart.showLoading();
                let datas = {};
                data.forEach(function (item) {
                    if (item["attester"] !== "") {
                        datas[item["attester"]] = "1";
                    }
                    if (item["attestee"] !== "") {
                        datas[item["attestee"]] = "1";
                    }
                });
                let series = [];
                let seriesData;
                seriesData = {
                    type: "graph",
                    layout: "force",
                    // progressiveThreshold: 700,
                    data: Object.keys(datas).map(function (item) {
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
                    edges: data.map(function (edge) {
                        return {
                            source: edge.attestee,
                            target: edge.attester
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
                        edgeLength: [150, 100]
                    }
                };
                series[0] = seriesData;
                let mapOption = {};
                mapOption.title = {};
                mapOption.title.text = "Trias-Dag Map";
                mapOption.animationDurationUpdate = 1500;
                mapOption.animationEasingUpdate = "quinticInOut";
                mapOption.series = series;
                myChart.setOption(mapOption, true);
                myChart.hideLoading();
            },
            showResultMessage(data) {
                if (!data || data.constructor !== Array) {//illegal data
                    return;
                }
                let areaVal = "";
                data.forEach(function (item) {
                    areaVal += "Attestee:'" + item["attestee"] + "',Score:'" + item["score"].toFixed(2) + "'\n";
                });
                $("#dagResult").val(areaVal);
            }
        }
    };

</script>
<style>
    .form-lable {
        margin-left: 50px;
    }

    .input-small {
        width: 150px;
    }

    .textarea-dag-result {
        resize: none;
        height: 400px;
        width: 810px;
        line-height: 1.5;
        margin-left: 50px;
        font-family: Helvetica Neue, Helvetica, PingFang SC, Hiragino Sans GB, Microsoft YaHei, SimSun, sans-serif;
    }

    .dag-map {
        width: 70%;
        height: 500px;
        background: #f3f3f3 !important;
    }
</style>
