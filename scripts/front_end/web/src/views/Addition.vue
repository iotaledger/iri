<template>
    <el-form :model="ruleForm" :rules="rules" ref="ruleForm" label-position="left" label-width="0px"
             class="demo-ruleForm login-container">
        <h3 class="title">Perfect Information</h3>
        <el-form-item prop="account">
            <el-input type="text" v-model="ruleForm.account" auto-complete="off" placeholder="coin account"></el-input>
        </el-form-item>
        <el-form-item prop="email">
            <el-input type="text" v-model="ruleForm.email" auto-complete="off" placeholder="email"></el-input>
        </el-form-item>
        <el-form-item>
            <el-radio-group v-model="ruleForm.sex">
                <el-radio label="1">Male</el-radio>
                <el-radio label="0">Female</el-radio>
            </el-radio-group>
        </el-form-item>
        <el-form-item style="width:100%;">
            <el-button type="success" style="width: 100%;" @click.native.prevent="userRegister">Commit</el-button>
        </el-form-item>
    </el-form>
</template>

<script>
    import Cookies from "js-cookie";

    export default {
        data() {
            let checkEmail = (rule, value, callback) => {
                let reg = new RegExp(
                    "^[a-z0-9]+([._\\-]*[a-z0-9])*@([a-z0-9]+[-a-z0-9]*[a-z0-9]+.){1,63}[a-z0-9]+$"
                );
                if (!value) {
                    callback(new Error("邮箱地址不能为空"));
                } else if (!reg.test(value)) {
                    callback(new Error("请输入正确的邮箱地址"));
                } else {
                    callback();
                }
            };
            return {
                ruleForm: {
                    account: '',
                    email: '',
                    sex: '1',
                },
                rules: {
                    account: [
                        {required: true, message: "Please type in account", trigger: "blur"},
                    ],
                    email: [
                        {required: true, message: "Please type in email", trigger: "blur"},
                        {required: true, validator: checkEmail, message: "Email not correct", trigger: "blur"}
                    ]
                },
            };
        },
        methods: {
            userRegister() {
                this.$refs.ruleForm.validate((valid) => {
                    if (valid) {
                        let url = "/trias-resource/user/addition";
                        this.axios.post(url, this.ruleForm, {headers:{'Authorization': 'Bearer ' + Cookies.get("UserToken")}}).then((res) => {
                            if (res.data["code"] === 1) {
                                this.$alert("success", this.messageOption.success);
                                this.$router.push("/");
                            } else {
                                console.log(res.data["message"]);
                                this.$alert(res.data["message"], this.messageOption.warning);
                            }
                        }).catch((err) => {
                            console.error(err);
                            this.$alert("login happens a error", this.messageOption.error);
                        })
                    } else {
                        this.$alert("Please check the input", this.messageOption.error);
                        return false;
                    }
                });
            }
        }
    }

</script>

<style lang="scss" scoped>
    .login-container {
        /*box-shadow: 0 0px 8px 0 rgba(0, 0, 0, 0.06), 0 1px 0px 0 rgba(0, 0, 0, 0.02);*/
        -webkit-border-radius: 5px;
        border-radius: 5px;
        -moz-border-radius: 5px;
        background-clip: padding-box;
        margin: 180px auto;
        width: 350px;
        padding: 35px 35px 15px 35px;
        background: #fff;
        border: 1px solid #eaeaea;
        box-shadow: 0 0 25px #cac6c6;

        .title {
            margin: 0px auto 40px auto;
            text-align: center;
            color: #505458;
        }

        .remember {
            margin: 0px 0px 35px 0px;
        }
    }
</style>
