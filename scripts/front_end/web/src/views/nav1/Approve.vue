<template>
    <el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit"
             style="margin:20px;width:100%;min-width:600px;text-align: center;">
        <el-form-item>
            <label>server：</label>
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
            <label>Attester：</label>
            <el-input v-model="form.attester" class="input-small" placeholder="Input the attester"
                      @change="setAttester"/>
            <label>Attestee：</label>
            <el-input v-model="form.attestee" class="input-small" placeholder="Input the attestee"
                      @change="setAttestee"/>
            <label>Score</label>
            <el-radio-group v-model="form.score" @change="setScore">
                <el-radio label="1"></el-radio>
                <el-radio label="0"></el-radio>
            </el-radio-group>
        </el-form-item>
        <el-form-item>
            <div style="text-align: center;">
                <el-button type="primary" @click="addNode">AddNode</el-button>
            </div>
        </el-form-item>
    </el-form>
</template>

<script>
    let requestData = {};
    requestData.Score = "1";
    let requestServer;
    let requestPort;
    export default {
        data() {
            return {
                form: {
                    attester: "",
                    attestee: "",
                    score: "1",
                    server: ""
                },
                serverList: this.ipList,
                portList: this.portList
            }
        },
        methods: {
            onSubmit() {
                console.log("submit!");
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
                requestServer = data;
            },
            choosePort(data) {
                requestPort = data;
            },
            addNode() {
                if (!requestServer || !requestPort) {
                    this.$alert("Please choose server and port", "Warning",{type:"warning"});
                }
                requestData.url = requestServer + ":" + requestPort;
                this.axios.post("/api/AddNode", requestData).then(res => {//success callback
                    if (res.data["Code"] === 1) {
                        this.$alert("addNode success!","Success",{type:"success"});
                    } else {
                        this.$alert(res.data["Message"],"Error",{type:"error"})
                    }
                }).catch(error => {//error callback
                    console.error(error);
                });
            }
        }
    };

</script>
<style>
    .input-small {
        width: 150px;
    }
</style>
