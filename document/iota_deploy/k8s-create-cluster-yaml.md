# k8s启动集群步骤 #

## 启动单个集群 ##

通过yaml创建集群，新建deployment文件，iota-deploy.yaml内容如下:

```
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
name: trias-cli-deployment
  labels:
    app: trias-cli
spec:
  replicas: 1
  selector:
    matchLabels:
      app: trias-cli
  template:
    metadata:
      labels:
        app: trias-cli
    spec:
      restartPolicy: Always
      containers:
      - name: trias-cli
        image: 172.31.23.215:5000/trias-cli:StreamNet_v1.0.6 
```  

新建service文件，iota-service.yaml  

```
apiVersion: v1
kind: Service
metadata:
  name: trias-cli-service
spec:
    ports:
      - port: 4999
        targetPort: 4999
        name: "trias-cli"
        protocol: TCP
    selector:
        app: trias-cli
    externalIPs:
    - 172.31.28.12
```

创建集群 

```
sudo kubectl create -f iota-deploy.yaml  
sudo kubectl create -f iota-service.yaml
```

注意:  
spec.selector.matchLabels.app和spec.template.metadata.labels.app要一致  
service文件中的spec.selector.app 和deployment中labels要对应  

最后通过 clusterip 和port访问集群

## 启动服务相互调用的两个集群 ##

### 集群2访问集群1中的服务接口 ###

创建集群1

新建iota_node_rc.yaml文件

```
apiVersioe: v1
kind: ReplicationController
metadata:
  name: iota-node
spec:
  replicas: 1
  selector:
    app: iota-node
  template:
    metadata:
      labels:
        app: iota-node
    spec:
      containers:
      - name: iota-node
        image: stplaydog/iota-node:StreamNet_v1.0
        ports:
        - containerPort: 14700
        env:
        - name: API_PORT
          value: "14700"
        - name: UDP_PORT
          value: "13600"
        - name: TCP_PORT
          value: "13600"
```

新建iota_node_sc.yaml

```
apiVersion: v1

kind: Service
metadata:
  name: iota-node
spec:
  ports:
  - name: iota-node
    port: 14700
    targetPort: 14700
    nodePort: 31000
  selector:
    app: iota-node
  type: NodePort
```  
```
sudo kubectl create -f iota_cli_dp.yaml  
sudo kubectl create -f iota_cli_sc.yaml
```

创建集群2

新建iota_cli_dp.yaml文件

```
apiVersion: v1
kind: ReplicationController
metadata:
  name: iota-cli
spec:
  replicas: 1
  selector:
    app: iota-cli
  template:
    metadata:
      labels:
        app: iota-cli
    spec:
      containers:
        - name: iota-cli
          image: 172.31.23.215:5000/trias-cli:StreamNet_v1.0.6
          ports:
          - containerPort: 4999
          env:
          - name: IOTA_NODE_SERVICE_HOST
            value: '192.16.30.12'
          - name: IOTA_NODE_SERVICE_PORT
            value: '14700'
```

新建iota_cli_sc.yaml文件.

```
apiVersion: v1
kind: Service
metadata:
  name: iota-cli
spec:
  type: NodePort
  ports:
    - port: 4999
      nodePort: 31499
  selector:
    app: iota-cli
```

创建集群。

```
sudo kubectl create -f iota_cli_dp.yaml;
sudo kubectl create -f iota_cli_sc.yaml;
```

最后通过 clusterip 和port访问集群.
