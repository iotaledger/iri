<template>
	<el-form ref="form" :model="form" label-width="80px" @submit.prevent="onSubmit" style="margin:20px;width:70%;min-width:600px;">
		<el-form-item>
			<label class="form-lable">Attestee2：</label>
			<el-select v-model="form.region" placeholder="请选择IP">
				<el-option label="192.168.199.106" value="192.168.199.106"></el-option>
				<el-option label="192.168.199.107" value="192.168.199.107"></el-option>
				<el-option label="192.168.199.108" value="192.168.199.108"></el-option>
				<el-option label="192.168.199.109" value="192.168.199.109"></el-option>
			</el-select>
			<label class="form-lable">Attestee2：</label>
			<el-select v-model="form.region" placeholder="请选择IP">
				<el-option label="192.168.199.106" value="192.168.199.106"></el-option>
				<el-option label="192.168.199.107" value="192.168.199.107"></el-option>
				<el-option label="192.168.199.108" value="192.168.199.108"></el-option>
				<el-option label="192.168.199.109" value="192.168.199.109"></el-option>
			</el-select>
			<label class="form-lable">Score</label>
			<el-radio-group v-model="form.resource">
				<el-radio label="1"></el-radio>
				<el-radio label="0"></el-radio>
			</el-radio-group>
		</el-form-item>
		<el-form-item>
			<div style="text-align: center;">
				<el-button type="primary">Submit</el-button>
			</div>
		</el-form-item>
	</el-form>
</template>

<script>
	export default {
		data() {
			return {
				form: {
					name: '',
					region: '',
					date1: '',
					date2: '',
					delivery: false,
					type: [],
					resource: '',
					desc: ''
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