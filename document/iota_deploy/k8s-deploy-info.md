# k8s环境搭建说明  

## 安装环境  
操作系统:Ubuntu 16.04  
三台机器  
192.168.50.128 master  
192.168.50.129 node  
192.168.50.130 node  
## 安装步骤   
步骤1.安装docker添加docker官方源    

```
curl -fsSL https://download.docker.com/linux/ubuntu/gpg |apt-key add -
```  

步骤2.增加docker官方源仓库  

```
  add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
```  

步骤3.apt-get update    

步骤4.查看aplache docker官方的稳定版本    

```
apt-cache madison docker-ce
```  

步骤5.安装docker 17.03版本  

```
apt-get install docker-ce=17.03.2~ce-0~ubuntu-xenial
```

步骤6.查看docker 版本及服务  

```
docker version 查看docker版本  
systemctl status docker  查看docker是否在运行  
```  

步骤7.运行hello-world  

```
docker run hello-world --显示正确结果
```  

步骤8.添加公钥文件  

```
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
```  

添加公钥的需要连接google官方网站，许翻墙，这里通过下载kube_apt_key.gpg源文件来安装 

步骤9.把源文件上传到服务器上，然后执行  

```
cat kube_apt_key.gpg | sudo apt-key add -
```   

步骤10 添加k8s源，安装kubelet,kubeadm,kubectl  

```echo "deb [arch=amd64] https://mirrors.ustc.edu.cn/kubernetes/apt kubernetes-xenial main" | sudo tee -a /etc/apt/sources.list  
apt-get update  
apt-get install  kubelet kubeadm kubectl 
```  

步骤11.核心组件镜像下载  

```  
docker pull mirrorgooglecontainers/kube-apiserver:v1.13.2    
docker pull mirrorgooglecontainers/kube-controller-manager:v1.13.2  
docker pull mirrorgooglecontainers/kube-scheduler:v1.13.2  
docker pull mirrorgooglecontainers/kube-proxy:v1.13.2  
docker pull mirrorgooglecontainers/pause:3.1  
docker pull mirrorgooglecontainers/etcd:3.2.24  
docker pull coredns/coredns:1.2.6  
```  

步骤12.修改标签为gcr.io  

```
docker tag docker.io/mirrorgooglecontainers/kube-apiserver:v1.13.2 k8s.gcr.io/kube-apiserver:v1.13.2  
docker tag docker.io/mirrorgooglecontainers/kube-controller-manager:v1.13.2 k8s.gcr.io/kube-controller-manager:v1.13.2  
docker tag docker.io/mirrorgooglecontainers/kube-scheduler:v1.13.2 k8s.gcr.io/kube-scheduler:v1.13.2  
docker tag docker.io/mirrorgooglecontainers/kube-proxy:v1.13.2 k8s.gcr.io/kube-proxy:v1.13.2  
docker tag docker.io/mirrorgooglecontainers/pause:3.1  k8s.gcr.io/pause:3.1  
docker tag docker.io/mirrorgooglecontainers/etcd:3.2.24  k8s.gcr.io/etcd:3.2.24  
docker tag docker.io/coredns/coredns:1.2.6  k8s.gcr.io/coredns:1.2.6  
```    

步骤13.删除多余镜像  

```
docker rmi mirrorgooglecontainers/kube-apiserver:v1.13.2  
docker rmi mirrorgooglecontainers/kube-controller-manager:v1.13.2  
docker rmi mirrorgooglecontainers/kube-scheduler:v1.13.2  
docker rmi mirrorgooglecontainers/kube-proxy:v1.13.2  
docker rmi mirrorgooglecontainers/pause:3.1  
docker rmi mirrorgooglecontainers/etcd:3.2.24  
docker rmi coredns/coredns:1.2.6  
```  
步骤14.初始化master节点
 
引导和初始化master
```
kubeadm init --pod-network-cidr=10.244.0.0/16 --apiserver-advertise-address=192.168.50.128
```  
执行命令如果报swap错误，解决办法为：
```
swapoff -a && sed -i '/swap/d' /etc/fstab
```  
，然后再重新执行kubeadm init,初始化成功最后几行会生成下面的信息，通过该信息添加node节点  
```  
kubeadm join 192.168.50.128:6443 --token rnrw5t.nedeq3364h7m0t6e --discovery-token-ca-cert-hash \ sha256:0e1090ceed078b2e9e97c5db325c9fe2233ab40cff6452b349eca3814f8f78e9
```  
步骤15.添加环境变量  

```
export KUBECONFIG=/etc/kubernetes/admin.conf
``` 

步骤16.查看运行结果  
```
kubectl get pods -n kube-system -o wide
```  

步骤17.安装pod网络附加组件  

```
kubectl create -f https://raw.githubusercontent.com/coreos/flannel/v0.9.1/Documentation/kube-flannel.yml
```  
查看运行结果
```
kubectl get pods -n kube-system -o wide
```  
如果发现coredns-86c58d9df4-bqvrn ：CrashLoopBackOff
查看报错日志
```
kubectl --namespace kube-system logs coredns-86c58d9df4-bqvrn
```  

步骤18.如果报错信息如下及解决办法  

```linux/amd64, go1.11.2, 756749c[INFO] plugin/reload:Running configuration MD5=f65c4821c8a9b7b5eb30fa4fbc167769[FATAL] plugin/loop: Forwarding loop detected in "." zone. Exiting. See https://coredns.io/plugins/loop#troubleshooting. Probe "
```  
编辑coredns  
```
kubectl -n kube-system edit configmap coredns
```  
注释掉loop那一行，然后保存
然后再执行：
```
kubectl get pods -n kube-system -o wide
```  

步骤19.查看当前集群节点  

```
kubectl get nodes
```  
步骤20.在node节点操作  

```
swapoff -a
```  
然后执行:  
```
kubeadm join 192.168.50.128:6443 --token rnrw5t.nedeq3364h7m0t6e --discovery-token-ca-cert-hash \ sha256:0e1090ceed078b2e9e97c5db325c9fe2233ab40cff6452b349eca3814f8f78e9
```  

步骤21.查看node节点加入情况

```
kubectl get nodes
```  
集群创建成功

步骤22.验证集群功能  

``` 
 kubectl run nginx-deploy --image=nginx:1.14-alpine --port=80 --replicas=2
```  

ps:步骤1-7,11-13是在所有节点上执行，
   步骤8-10，14-19，21-22在master节点执行
