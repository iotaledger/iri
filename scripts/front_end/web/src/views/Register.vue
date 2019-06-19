<template>
    <el-form :model="ruleForm" :rules="rules" ref="ruleForm" label-position="left" label-width="0px"
             class="demo-ruleForm login-container">
        <span style="cursor: pointer;color: #1d90e6" @click="loginSystem">Login</span>
        <h3 class="title">Sign Up</h3>
        <el-form-item prop="username">
            <el-input type="text" v-model="ruleForm.username" auto-complete="off" placeholder="username"></el-input>
        </el-form-item>
        <el-form-item prop="password">
            <el-input type="password" v-model="ruleForm.password" auto-complete="off"
                      placeholder="password"></el-input>
        </el-form-item>
        <!--<el-form-item prop="account">-->
        <!--<el-input type="text" v-model="ruleForm.account" auto-complete="off" placeholder="account"></el-input>-->
        <!--</el-form-item>-->
        <!--<el-form-item prop="email">-->
        <!--<el-input type="text" v-model="ruleForm.email" auto-complete="off" placeholder="email"></el-input>-->
        <!--</el-form-item>-->
        <!--<el-form-item>-->
        <!--<el-radio-group v-model="ruleForm.sex">-->
        <!--<el-radio label="1">Male</el-radio>-->
        <!--<el-radio label="0">Female</el-radio>-->
        <!--</el-radio-group>-->
        <!--</el-form-item>-->
        <el-form-item style="width:100%;">
            <el-button type="success" style="width: 100%;" @click.native.prevent="userRegister">Sign Up</el-button>
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
                    username: '',
                    password: '',
                    // account: '',
                    // email: '',
                    // sex: '1',
                },
                rules: {
                    username: [
                        {required: true, message: "Please type in username", trigger: "blur"},
                    ],
                    password: [
                        {required: true, message: "Please type in password", trigger: "blur"},
                    ],
                    // account: [
                    //     {required: true, message: "Please type in account", trigger: "blur"},
                    // ],
                    // email: [
                    //     {required: true, message: "Please type in email", trigger: "blur"},
                    //     {required: true, validator: checkEmail, message: "Email not correct", trigger: "blur"}
                    // ]
                },
            };
        },
        methods: {
            userRegister() {
                this.$refs.ruleForm.validate((valid) => {
                    if (valid) {
                        // let request = {username: this.ruleForm2.username, password: this.ruleForm2.password};
                        let url = "/trias-resource/user/register";
                        this.axios.post(url, this.ruleForm).then((res) => {
                            if (res.data["code"] === 1) {
                                this.$alert("success", this.messageOption.success);
                                this.$router.push("/login");
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
            },
            loginSystem() {
                this.$router.push("/login")
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
