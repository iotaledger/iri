<template>
    <el-form :model="form" style="margin:20px;width:100%;min-width:600px;">
        <el-form-item>
            <div class="label-div">
                <label>Experiment Topology:</label>
            </div>
            <el-select v-model="form.deployTopology" placeholder="Choose a deployTopology"
                       @change="chooseDeployTopology">
                <el-option
                        v-for="item in deployType"
                        :key="item.name"
                        :label="item.name"
                        :value="item.value"
                ></el-option>
            </el-select>
        </el-form-item>
        <el-form-item>
            <div class="label-div">
                <label>Image File Version:</label>
            </div>
            <el-input v-model="form.fileVersion" class="input-small" placeholder="image-file version"
                      @change="setFileVersion"/>
        </el-form-item>
        <el-form-item>
            <div class="label-div">
                <label>Data Count:</label>
            </div>
            <el-input v-model="form.dataCount" type="number" class="input-small" @change="setTotalCount" min="0"/>
        </el-form-item>
        <el-form-item>
            <div class="label-div">
                <label>Use Cache:</label>
            </div>
            <el-radio-group v-model="form.flag" @change="setFlag">
                <el-radio label="true">true</el-radio>
                <el-radio label="false">false</el-radio>
            </el-radio-group>
        </el-form-item>
        <el-form-item>
            <el-button type="success" @click="deploy">Start Stress</el-button>
        </el-form-item>
    </el-form>
</template>

<script>
    let requestData = {};
    requestData.flag = "true";
    export default {
        data() {
            return {
                form: {
                    deployTopology: "",
                    fileVersion: "",
                    dataCount: 0,
                    flag: "true"
                },
                deployType: this.Servers.deployType
            }
        },
        methods: {
            chooseDeployTopology(val) {
                requestData.topology = val;
            },
            setFileVersion(val) {
                requestData.image_tag = val;
            },
            setTotalCount(val) {
                let regex = /^\d+$/;
                if (!regex.test(val)) {
                    this.$alert("Count must be int value", this.messageOption.warning);
                    return;
                }
                requestData.stress_data = val;
            },
            setFlag(val) {
                requestData.type = val === "true"?"put_cache":"put_file";
            },
            deploy() {
                if (!requestData.topology || !requestData.image_tag || requestData.stress_data * 1 === 0) {
                    this.$alert("Please input the correct parameters", this.messageOption.warning);
                    return;
                }
                let request = {};
                request.requestUrl = this.Servers.serverList.deploymentServer + "/stress_experiment";
                request.requestData = JSON.stringify(requestData);
                request.requestMethod = this.requestMethod.POST;
                request.type =
                this.$confirm("Are you sure to post stress data?", "Tips", {
                    type: "warning",
                    confirmButtonText: "OK",
                    cancelButtonText: "Cancel"
                }).then(() => {
                    this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                        this.$alert("Success", this.messageOption.success);
                        console.log(res);
                    }).catch((err) => {
                        console.error(err);
                    })
                }).catch(() => {
                });
            }
        }
    }
</script>
<style>
    .input-small {
        width: 219px;
    }

    .label-div {
        width: 200px;
        float: left
    }
</style>
