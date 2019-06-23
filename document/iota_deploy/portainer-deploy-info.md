## Portainer 部署  

### 部署环境  
操作系统：ubuntu 14.04.5  
节点个数：7(1个manager,6个worker)  
docker：18.06.1-ce  

### 部署步骤  

在每个节点开启远程管理端口：2375

```bash
sudo vim /etc/default/docker
```  

在文件的最后面添加下面内容  

```bash
DOCKER_OPTS="-H 0.0.0.0:2375 -H unix:///var/run/docker.sock"
```  

重启docker服务  

```bash
sudo service docker restart
sudo lsof -i:2375
```  

在manager节点执行
```bash
sudo docker swarm init --advertise-addr 172.31.34.26
```  
![](https://github.com/wangyh2016/storm/blob/master/1.png?raw=true)

在worker节点执行  

```bash
docker swarm join --token SWMTKN-14nt13grfdvgnot5niofnr4nlc37087xb8ugscl5b9mhgyia9im-55o7kn185fg6iwdksknkm7ohc 172.31.34.26:2377
```  
在manager节点拉取portainer镜像并启动容器

```bash
docker pull portainer/portainer
docker run -ti -d --name my-portainer -p 9000:9000 --restart=always portainer/portainer
```  

通过nginx代理到9000端口，在浏览器访问http:ip 需要密码设置  

登陆进去之后,点击Home页面下“add endportainer”添加该节点docker信息:  

![](https://s1.51cto.com/images/20180906/1536202813127556.png?x-oss-process=image/watermark,size_16,text_QDUxQ1RP5Y2a5a6i,color_FFFFFF,t_100,g_se,x_10,y_10,shadow_90,type_ZmFuZ3poZW5naGVpdGk=)

输入以下相关信息，点击“add endpoartainer”添加信息即可：  

![](https://s1.51cto.com/images/20180906/1536202895723000.png?x-oss-process=image/watermark,size_16,text_QDUxQ1RP5Y2a5a6i,color_FFFFFF,t_100,g_se,x_10,y_10,shadow_90,type_ZmFuZ3poZW5naGVpdGk=)

看到添加docker-node1节点成功:  

![](https://s1.51cto.com/images/20180906/1536202930633600.png?x-oss-process=image/watermark,size_16,text_QDUxQ1RP5Y2a5a6i,color_FFFFFF,t_100,g_se,x_10,y_10,shadow_90,type_ZmFuZ3poZW5naGVpdGk=)

到此，可以通过portainer进行容器管理了
