<template>
    <el-form :model="form" style="margin:20px;width:100%;min-width:600px;">
        <el-form-item>
            <div class="label-div">
                <label>Deploy Topology:</label>
            </div>
            <el-select v-model="form.deployTopology" placeholder="Choose a deployTopology"
                       @change="chooseDeployTopology">
                <el-option
                        v-for="item in deployType"
                        v-if="!item.experiment"
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
                <label>Use Cache:</label>
            </div>
            <el-radio-group v-model="form.flag" @change="setFlag">
                <el-radio label="true">true</el-radio>
                <el-radio label="false">false</el-radio>
            </el-radio-group>
        </el-form-item>
        <el-form-item>
            <el-button type="success" @click="deploy">Deploy</el-button>
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
            setFlag(val) {
                requestData.flag = val;
            },
            deploy() {
                if (!requestData.topology || !requestData.image_tag) {
                    this.$alert("Please input the correct parameters", this.messageOption.warning);
                    return;
                }
                let request = {};
                request.requestUrl = this.Servers.serverList.deploymentServer + "/cluster_deploy";
                request.requestData = JSON.stringify(requestData);
                request.requestMethod = this.requestMethod.POST;
                this.$confirm("Are you sure to deploy?", "Tips", {
                    type: "warning",
                    confirmButtonText: "OK",
                    cancelButtonText: "Cancel"
                }).then(() => {
                    this.axios.post("/api/QueryNodeDetail", request).then((res) => {
                        this.$alert("Success",this.messageOption.success);
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
