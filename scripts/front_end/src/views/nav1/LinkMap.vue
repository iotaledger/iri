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
    </el-form>
</template>

<script>
    let requestData = {};
    let dagUrl;
    export default {
        data() {
            return {
                form: {
                    dag: '',
                    period: '',
                    numRank: ''
                }
            }
        },
        methods: {
            onSubmit() {
                console.log('submit!');
            },
            setDag(val) {
                dagUrl = val
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
                this.axios.post(requestUrl, requestData).then(res => {//success callback
                    let data = res.data["Data"];
                    showResultMessage(data);
                }).catch(error => {
                    console.error(error)
                })
            }
        }
    }

    function showResultMessage(data) {
        if (!data || data.constructor != Array) {//illegal data
            return;
        }
        let areaVal = "";
        //requestData.Attestee&&requestData.Attester&&requestData.Score
        data.forEach(function (item) {
            areaVal += "Attestee:'" + item["attestee"] + "',Attester:'" + item["attester"] + "',Score:'" + item["score"].toFixed(2) + "'\n"
        });
        $("#dagResult").val(areaVal)
    }
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
</style>