<template>
	<el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit" style="margin:20px;width:70%;min-width:600px;">
		<el-form-item>
			<label class="form-lable">Attester：</label>
			<el-input v-model="form.attester" class="input-small" placeholder="Please input the attester ip" @change="setAttester"/>
			<label class="form-lable">Attestee：</label>
			<el-input v-model="form.attestee" class="input-small" placeholder="Please input the attestee ip" @change="setAttestee"/>
			<label class="form-lable">Score</label>
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
	export default {
		data() {
			return {
				form: {
					attester: '',
					attestee: '',
					score: '1'
				}
			}
		},
		methods: {
			onSubmit() {
				console.log('submit!');
			},
			setAttester(val){
				requestData.Attester = val;
			},
			setAttestee(val){
				requestData.Attestee = val;
			},
			setScore(val){
				requestData.Score = val;
			},
			addNode(){
				if(!checkRequestData){
					return;
				}
				this.axios.post("/api/AddNode",requestData).then(res =>{//success callback
					if(res.data["Code"] == 1){
						alert("addNode success!")
					}
				}).catch(error => {//error callback
					console.error(error)
				})
			}
		}
	}

	function checkRequestData(){
		if(!requestData.Attestee||requestData.Attestee==""||!isValidIP(requestData.Attestee)){
			return false;
		}
		if(!requestData.Attester||requestData.Attester==""||!isValidIP(requestData.Attester)){
			return false;
		}
		if(!requestData.Score||requestData.Score==""){
			return false;
		}
		return true;
	}

</script>
<style>
	.form-lable{
		margin-left: 50px;
	}
	.input-small{
		width: 150px;
	}
</style>