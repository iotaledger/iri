<template>
	<el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit" style="margin:20px;width:60%;min-width:600px;">
		<el-form-item>
			<label class="form-lable">DagAddress：</label>
			<el-select v-model="form.dag" placeholder="Choose a dag server">
				<el-option label="192.168.199.106" value="192.168.199.106"></el-option>
				<el-option label="192.168.199.107" value="192.168.199.107"></el-option>
				<el-option label="192.168.199.108" value="192.168.199.108"></el-option>
				<el-option label="192.168.199.109" value="192.168.199.109"></el-option>
			</el-select>
			<label class="form-lable">Period：</label>
			<el-select v-model="form.period" placeholder="Choose a period">
				<el-option label="period1" value="period1"></el-option>
				<el-option label="period2" value="period2"></el-option>
			</el-select>
			<el-button type="primary">Submit</el-button>
		</el-form-item>
		<el-form-item>
			<div style="text-align: center;">show result list</div>
		</el-form-item>
	</el-form>
</template>

<script>
	export default {
		data() {
			return {
				form: {
					dag: '',
					period: ''
				}
			}
		},
		methods: {
			onSubmit() {
				console.log('submit!');
			}
		}
	}
</script>
<style>
	.form-lable{
		margin-left: 50px;
	}
</style>