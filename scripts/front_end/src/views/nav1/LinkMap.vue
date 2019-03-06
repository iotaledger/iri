<template>
    <el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit"
             style="margin:20px;width:60%;min-width:600px;">
        <el-form-item>
            <label class="form-lable">DagAddress：</label>
            <el-input v-model="form.dag" class="input-small" placeholder="Dag server default localhost"
                      @change="setDag"/>
            <label class="form-lable">Period：</label>
            <el-input v-model="form.period" class="input-small" placeholder="Please input period" @change="setPeriod"/>
            <el-input v-model="form.numrank" class="input-small" placeholder="Please input numrank"
                      @change="setNumrank"/>
            <el-button type="primary" @click="queryData">QueryNodes</el-button>
        </el-form-item>
        <el-form-item>
            <div style="text-align: center;">
                <textarea id="dagResult" class="textarea-dag-result"></textarea>
            </div>
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
                    numrank: ''
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
                requestData.period = val;
            },
            setNumrank(val) {
                requestData.numRank = val;
            },
            queryData() {
                $("#dagResult").val("");
                let requestUrl = "http://" + dagUrl || window.location.host + "/api/QueryData";
                this.axios.post(requestUrl, requestData).then(res => {//success callback
                    showResultMessage(res);
                }).then(res => {//error callback
                    console.error(res)
                })
            }
        }
    }

    function showResultMessage(data) {
        if (data.constructor != Array) {//illegal data
            return;
        }
        let areaVal = "";
        //requestData.Attestee&&requestData.Attester&&requestData.Score
        data.forEach(function (item) {
            areaVal += "Attestee:'" + item["Attestee"] + "',Attester:'" + item["Attester"] + "',Score:'" + item["Score"] + "'\n"
        })
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
        height: 370px;
        width: 790px;
        line-height: 2;
    }
</style>