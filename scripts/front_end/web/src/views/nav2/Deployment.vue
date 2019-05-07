<template>
    <el-form :model="form" style="margin:20px;width:100%;min-width:600px;">
        <el-form-item>
            <label>Deploy Topology:</label>
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
            <label>Image File Version:</label>
            <el-input v-model="form.fileVersion" class="input-small" placeholder="image-file version"
                      @change="setFileVersion"/>
        </el-form-item>
        <el-form-item>
            <label>Use Cache:</label>
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
    iframe {
        width: 85vw;
        height: 50vw;
    }

    .input-small {
        width: 200px;
    }
</style>
