## 多节点集群部署 

### 部署环境 
用户:trust  
操作系统：Ubuntu 14.04  
节点个数：7  
docker：18.06.1-ce  
python：2.7  
七台机器是互信的，选取其中一台机器作为管理节点，在管理节点除了上述环境需要安装，还要安装：  
pssh：2.3.1  
nginx：1.12.2  
jmeter：5.1  

### 部署步骤

在每台机器上下载项目压缩包并解压 

```bash
wget https://github.com/wunder3605/iri/archive/v0.1.3-streamnet.zip
unzip v0.1.3-streamnet.zip
mv iri-0.1.3-streamnet iri
```  

在管理节点下载相关配置信息  

```bash
cd scripts/examples/cluster
git clone https://github.com/wangyh2016/conf_info
```  
生成iri和cli镜像  

```bash
cd scripts/examples
sudo sh run_docker.sh true(true代表enable_batching为True,如果为false代表enable_batching为False)
```  

在管理节点启动服务并进行测试  

```bash
cd scripts/examples/cluster
./run.sh
```  
### 注意点
*.1 run.sh里面的JM_HOME根据自己安装路径进行调整
