<template>
    <el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit"
             style="margin:20px;width:100%;min-width:600px;text-align:center;">
        <el-form-item>
            <label>DagAddress：</label>
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
            <label>Period：</label>
            <el-input v-model="form.period" class="input-small" placeholder="Please input period" @change="setPeriod"/>
            <label>numRank：</label>
            <el-input v-model="form.numRank" class="input-small" placeholder="Please input numRank"
                      @change="setNumRank"/>
            <el-button type="primary" @click="queryData">GetRank</el-button>
            <el-button type="success" @click="addNodeDialog = true">AddAttestation</el-button>
        </el-form-item>
        <el-form-item>
            <textarea id="dagResult" class="textarea-dag-result"></textarea>
        </el-form-item>
        <el-form-item>
            <div id="scale_div">
                <el-button icon="el-icon-plus" @click="scalePlus"></el-button>
                <el-button icon="el-icon-minus" @click="scaleMinus"></el-button>
            </div>
            <div id="relationChart" class="dag-map"></div>
        </el-form-item>
        <el-dialog title="Add Node" :visible.sync="addNodeDialog" width="60%" style="text-align: left">
            <el-form :model="form">
                <el-form-item label="Server" label-width="80px;">
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
                </el-form-item>
                <el-form-item label="Attester" label-width="80px;">
                    <el-input v-model="form.attester" class="input-small" placeholder="Input the attester"
                              @change="setAttester"/>
                </el-form-item>
                <el-form-item label="Attester" label-width="80px;">
                    <el-input v-model="form.attestee" class="input-small" placeholder="Input the attestee"
                              @change="setAttestee"/>
                </el-form-item>
                <el-form-item label="Score" label-width="80px;">
                    <el-radio-group v-model="form.score" @change="setScore">
                        <el-radio label="1"></el-radio>
                        <el-radio label="0"></el-radio>
                    </el-radio-group>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="addNode">Submit</el-button>
                    <el-button @click="addNodeDialog = false">Cancel</el-button>
                </el-form-item>
            </el-form>
        </el-dialog>
    </el-form>
</template>

<script>
    let requestData = {};
    let requestServer;
    let requestPort;
    export default {
        data() {
            return {
                form: {
                    period: "",
                    numRank: "",
                    server: "",
                    port: "",
                },
                serverList: this.ipList,
                portList: [],
                addNodeDialog: false,
                dagScale: 1
            };
        },
        methods: {
            onSubmit() {
                console.log("submit!");
            },
            setPeriod(val) {
                requestData.period = val * 1;
            },
            setNumRank(val) {
                requestData.numRank = val * 1;
            },
            setAttester(val) {
                requestData.attester = val;
            },
            setAttestee(val) {
                requestData.attestee = val;
            },
            setScore(val) {
                requestData.score = val;
            },
            chooseServer(data) {
                this.form.server = data;
                this.portList = this.serverPort[data];
                requestServer = data;
            },
            choosePort(data) {
                this.form.port = data;
                requestPort = data;
            },
            queryData() {
                $("#dagResult").val("");
                if (!requestServer || !requestPort) {
                    this.$alert("Please choose server and port", "Warning", this.messageOption.warning);
                    return;
                }
                requestData.url = requestServer + ":" + requestPort;
                this.axios.post("/api/QueryNodes", requestData).then((res) => {//success callback
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
                let digraph = "digraph {rankdir=LR;";
                for (let i in data) {
                    if (data[i].attester !== "" && data[i].attestee != "") {
                        digraph += "\"" + data[i].attester + "\"->\"" + data[i].attestee + "\";"
                    }
                }
                digraph += "}";
                let svgXml = this.$viz(digraph, {format: "svg"});
                $("#relationChart").html(svgXml);
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
            },
            addNode() {
                if (!requestServer || !requestPort) {
                    this.$alert("Please choose server and port", "Warning", this.messageOption.warning);
                    return;
                }
                requestData.url = requestServer + ":" + requestPort;
                this.axios.post("/api/AddNode", requestData).then(res => {//success callback
                    if (res.data["Code"] === 1) {
                        this.$alert("addNode success!", "Success", this.messageOption.success);
                    } else {
                        this.$alert(res.data["Message"], "Error", this.messageOption.error);
                    }
                }).catch(error => {//error callback
                    console.error(error);
                });
            },
            scalePlus() {
                this.setScale("#relationChart svg",0.1);
            },
            scaleMinus() {
                this.setScale("#relationChart svg",-0.1);
            },
            setScale(dom,value) {
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

    .input-small {
        width: 150px;
    }

    .textarea-dag-result {
        resize: none;
        height: 400px;
        width: 810px;
        line-height: 1.5;
        font-family: Helvetica Neue, Helvetica, PingFang SC, Hiragino Sans GB, Microsoft YaHei, SimSun, sans-serif;
    }

    .dag-map {
        margin-top: 50px;
        width: 95%;
        height: 500px;
    }
</style>
