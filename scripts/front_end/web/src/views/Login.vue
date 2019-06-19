<template>
    <el-form :model="ruleForm2" :rules="rules2" ref="ruleForm2" label-position="left" label-width="0px"
             class="demo-ruleForm login-container">
        <h3 class="title">Trias</h3>
        <el-form-item prop="username">
            <el-input type="text" v-model="ruleForm2.username" auto-complete="off" placeholder="username"></el-input>
        </el-form-item>
        <el-form-item prop="password">
            <el-input type="password" v-model="ruleForm2.password" auto-complete="off" placeholder="password"></el-input>
        </el-form-item>
        <el-form-item style="width:100%;">
            <el-button type="primary" style="width: 48%; float: left" @click.native.prevent="userLogin" :loading="logining">Login
            </el-button>
            <el-button type="success" style="width: 48%; float: left" @click.native.prevent="userRegister">Sign Up</el-button>
        </el-form-item>
    </el-form>
</template>

<script>
    import Cookies from "js-cookie";
    export default {
        data() {
            return {
                logining: false,
                ruleForm2: {
                    username: '',
                    password: ''
                },
                rules2: {
                    username: [
                        {required: true, message: "Please type in username", trigger: "blur"},
                    ],
                    password: [
                        {required: true, message: "Please type in password", trigger: "blur"},
                    ]
                },
            };
        },
        methods: {
            userLogin() {
                this.$refs.ruleForm2.validate((valid) => {
                    if (valid) {
                        this.logining = true;
                        let request = {username: this.ruleForm2.username, password: this.ruleForm2.password};
                        let url = "/trias-resource/user/oauthLogin";
                        console.log(this.messageOption.error);
                        this.axios.post(url, request).then((res) => {
                            if (res.data["code"] === 1) {
                                Cookies.set("UserToken", res.data["data"]);
                                this.$router.push("/");
                            } else {
                                this.$alert(res.data["message"], this.messageOption.error);
                            }
                        }).catch((err) => {
                            console.error(err);
                            this.$alert("login happens a error", this.messageOption.error);
                            this.logining = false;
                        });
                        this.logining = false;
                    } else {
                        this.$alert("Please check the input", this.messageOption.error);
                        return false;
                    }
                });
            },
            userRegister() {
                this.$router.push("/register");
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
