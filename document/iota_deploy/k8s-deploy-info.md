安装k8s步骤

环境：
操作系统:Ubuntu 16.04
三台机器
192.168.50.128 master
192.168.50.129 node
192.168.50.130 node


1.安装docker
添加docker官方源
curl -fsSL https://download.docker.com/linux/ubuntu/gpg |apt-key add -
2.增加docker官方源仓库
  add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
3.apt-get update
4.查看aplache docker官方的稳定版本
  apt-cache madison docker-ce
5.安装docker 17.03版本
  apt-get install docker-ce=17.03.2~ce-0~ubuntu-xenial
6.查看docker 版本及服务
  docker version
  systemctl   status docker
7.docker run hello-world --显示结果
8.添加公钥文件
  curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
  添加公钥的需要连接google官方网站，许翻墙
  这里通过下载kube_apt_key.gpg源文件来安装
9.上传到服务器上，然后执行
  cat kube_apt_key.gpg | sudo apt-key add -
10 添加k8s源，安装kubelet,kubeadm,kubectl
 echo "deb [arch=amd64] https://mirrors.ustc.edu.cn/kubernetes/apt kubernetes-xenial main" | sudo tee -a /etc/apt/sources.list
 apt-get update
 apt-get install  kubelet kubeadm kubectl 
systemctl status kubelet

11.核心组件镜像下载
master节点
docker pull mirrorgooglecontainers/kube-apiserver:v1.13.2
docker pull mirrorgooglecontainers/kube-controller-manager:v1.13.2
docker pull mirrorgooglecontainers/kube-scheduler:v1.13.2
docker pull mirrorgooglecontainers/kube-proxy:v1.13.2
docker pull mirrorgooglecontainers/pause:3.1
docker pull mirrorgooglecontainers/etcd:3.2.24
docker pull coredns/coredns:1.2.6
12.修改标签为gcr.io
docker tag docker.io/mirrorgooglecontainers/kube-apiserver:v1.13.2 k8s.gcr.io/kube-apiserver:v1.13.2
docker tag docker.io/mirrorgooglecontainers/kube-controller-manager:v1.13.2 k8s.gcr.io/kube-controller-manager:v1.13.2
docker tag docker.io/mirrorgooglecontainers/kube-scheduler:v1.13.2 k8s.gcr.io/kube-scheduler:v1.13.2
docker tag docker.io/mirrorgooglecontainers/kube-proxy:v1.13.2 k8s.gcr.io/kube-proxy:v1.13.2
docker tag docker.io/mirrorgooglecontainers/pause:3.1  k8s.gcr.io/pause:3.1
docker tag docker.io/mirrorgooglecontainers/etcd:3.2.24  k8s.gcr.io/etcd:3.2.24
docker tag docker.io/coredns/coredns:1.2.6  k8s.gcr.io/coredns:1.2.6
13.删除多余镜像
docker rmi mirrorgooglecontainers/kube-apiserver:v1.13.2
docker rmi mirrorgooglecontainers/kube-controller-manager:v1.13.2
docker rmi mirrorgooglecontainers/kube-scheduler:v1.13.2
docker rmi mirrorgooglecontainers/kube-proxy:v1.13.2
docker rmi mirrorgooglecontainers/pause:3.1
docker rmi mirrorgooglecontainers/etcd:3.2.24
docker rmi coredns/coredns:1.2.6
14.初始化master节点
  引导和初始化master
（1）网络插件 选用weave net --可选用flannel网络(连接google 翻墙）
  kubeadm init --pod-network-cidr=10.244.0.0/16 --apiserver-advertise-address=192.168.50.128
  执行命令会报swap错误
  解决办法为：swapoff -a && sed -i '/swap/d' /etc/fstab，然后再重新执行
  初始化成功最后几行会生成下面的信息，通过该信息添加node节点
  kubeadm join 192.168.50.128:6443 --token rnrw5t.nedeq3364h7m0t6e --discovery-token-ca-cert-hash sha256:0e1090ceed078b2e9e97c5db325c9fe2233ab40cff6452b349eca3814f8f78e9
15.添加环境变量
export KUBECONFIG=/etc/kubernetes/admin.conf
16.查看运行结果
kubectl get pods -n kube-system -o wide
17.安装pod网络附加组件
  kubectl create -f https://raw.githubusercontent.com/coreos/flannel/v0.9.1/Documentation/kube-flannel.yml
  查看运行结果
  kubectl get pods -n kube-system -o wide
  coredns-86c58d9df4-bqvrn ：CrashLoopBackOff
  查看报错日志kubectl --namespace kube-system logs coredns-86c58d9df4-bqvrn
18 如果报错信息如下及解决办法
linux/amd64, go1.11.2, 756749c
 [INFO] plugin/reload: Running configuration MD5 = f65c4821c8a9b7b5eb30fa4fbc167769
 [FATAL] plugin/loop: Forwarding loop detected in "." zone. Exiting. See https://coredns.io/plugins/loop#troubleshooting. Probe query: "HINFO 6729575338545302492.8747661760561787218.".
kubectl -n kube-system edit configmap coredns	
查看dns：nmcli dev show |grep DNS
注释掉loop那一行，然后保存
然后再执行：
kubectl get pods -n kube-system -o wide
19.查看当前集群节点
kubectl get nodes
20.在node节点操作
 swapoff -a
 执行
 kubeadm join 192.168.50.128:6443 --token rnrw5t.nedeq3364h7m0t6e --discovery-token-ca-cert-hash sha256:0e1090ceed078b2e9e97c5db325c9fe2233ab40cff6452b349eca3814f8f78e9
21.kubectl get nodes
 查看node节点加入情况
 集群创建成功
22.验证集群功能
 kubectl run nginx-deploy --image=nginx:1.14-alpine --port=80 --replicas=2


