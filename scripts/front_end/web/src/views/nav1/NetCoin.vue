<template>
    <el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit"
             style="margin:20px;width:100%;min-width:600px;text-align:left;">
        <el-form-item label="Server: " label-width="150px;">
            <label>&nbsp;</label>
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
        <el-form-item label="Balance: " label-width="150px;">
            <el-input v-model="form.account" class="input-large left-float" placeholder="Input the account"
                      @change="setAccount" :readonly="true"/>
            <div class="div-balance">{{form.balance}}</div>
            <el-button @click="getBalance" type="success">Get Balance</el-button>
        </el-form-item>
        <el-form-item label="Trans: " label-width="150px;">
            <label>&nbsp;&nbsp;&nbsp;&nbsp;From: &nbsp;</label>
            <el-input v-model="form.source" class="input-large" placeholder="Input source account"
                      @change="setSource" :readonly="true"/>
            <label>To: </label>
            <el-input v-model="form.target" class="input-large" placeholder="Input target account"
                      @change="setTarget"/>
            <label>Balance: </label>
            <el-input v-model="form.tarnsBalance" type="number" class="input-medium" placeholder="Input balance"
                      @change="setTarnsBalance"/>
            <el-button @click="sendTrans" type="success">Commit Transaction</el-button>
        </el-form-item>
        <el-form-item label="UTXO: " label-width="150px;">
            <label>&nbsp;</label>
            <el-button size="medium" type="primary" @click="getUTXO">Get UTXO</el-button>
        </el-form-item>
        <div id="scale_div">
            <el-button icon="el-icon-plus" @click="scalePlus"></el-button>
            <el-button icon="el-icon-minus" @click="scaleMinus"></el-button>
        </div>
        <div id="dagChart" class="dag-chart">
        </div>
    </el-form>
</template>

<script>
    let requestHost = "";
    let requestServer;
    let requestPort;
    export default {
        data() {
            return {
                form: {
                    account: this.$store.state.userInfo.account,
                    balance: "0",
                    source: this.$store.state.userInfo.account,
                    target: "",
                    tarnsBalance: ""
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
            setAccount(val) {
                this.form.account = val;
            },
            getBalance() {
                if (!this.form.account) {
                    this.$alert("Please input account info", this.messageOption.warmUp());
                    return;
                }
                if (!this.checkServerAndPort()) {
                    return;
                }
                let requestUrl = "";
                requestHost = requestServer + ":" + requestPort;
                requestUrl = requestHost + "/get_balance";
                let request = {};
                let requestData = {"account": this.form.account};
                request.requestUrl = requestUrl;
                request.requestData = JSON.stringify(requestData);
                request.requestMethod = this.requestMethod.GET;
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        this.$alert(res.data["Message"],this.messageOption.error);
                    } else {
                        this.form.balance = res.data["Data"];
                    }
                }).catch((err) => {
                    console.error(err);
                })
            },
            sendTrans() {
                if (!this.checkServerAndPort()) {
                    return;
                }
                if (!this.form.source || !this.form.target || !this.form.tarnsBalance || this.form.tarnsBalance * 1 === 0) {
                    this.$alert("Please input correct info", this.messageOption.warning);
                    return;
                }
                let requestUrl = "";
                requestHost = requestServer + ":" + requestPort;
                requestUrl = requestHost + "/put_file";
                let request = {};
                let requestData = {};
                requestData.from = this.form.source;
                requestData.to = this.form.target;
                requestData.amnt = this.form.tarnsBalance;
                requestData.tag = "TX";
                request.requestUrl = requestUrl;
                request.requestData = JSON.stringify(requestData);
                request.requestMethod = this.requestMethod.POST;
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        this.$alert(data["Message"],this.messageOption.error);
                    } else {
                        this.$alert("Sucess!",this.messageOption.success);
                    }
                }).catch((err) => {
                    console.error(err);
                })
            },
            setSource(val) {
                this.form.source = val;
            },
            setTarget(val) {
                this.form.target = val;
            },
            setTarnsBalance(val) {
                this.form.tarnsBalance = val;
            },
            getUTXO() {
                if (!this.checkServerAndPort()) {
                    return;
                }
                let requestUrl = "";
                requestHost = requestServer + ":" + requestPort;
                requestUrl = requestHost + "/get_utxo";
                let request = {};
                let requestData = {"type": "DOT"};
                request.requestUrl = requestUrl;
                request.requestData = JSON.stringify(requestData);
                request.requestMethod = this.requestMethod.GET;
                this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                    if (res.data["Code"] === 0) {
                        alert(data["Message"]);
                    } else {
                        this.drawVizGraph(res.data["Data"], {format: "svg"});
                    }
                }).catch((err) => {
                    console.error(err);
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
            },
            drawVizGraph(digraph, option) {
                let svgXml = this.$viz(digraph, option);
                $("#dagChart").html(svgXml);
            },
            checkServerAndPort() {
                if (!requestServer || !requestPort) {
                    this.$alert("Please choose server and port", "Warning", this.messageOption.warning);
                    return false;
                }
                return true;
            }
        }
    };
</script>
<style>
    .input-large {
        width: 400px;
    }

    .input-medium {
        width: 150px;
    }

    .left-float {
        float: left;
    }

    .div-balance {
        float: left;
        width: 200px;
        font-size: medium;
        margin-left: 20px;
    }

    .dag-chart {
        width: 98%;
        height: 500px;
        overflow-x: auto;
        margin-top: 100px;
    }
</style>
