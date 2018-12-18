# IOTA私网环境搭建说明

## 步骤0. 前置步骤
在前置步骤中，需要生成第一个节点的账户初始分配的配置文件，用来初始化所有的地址和余额，所有地址的余额总数应等于2779530283277761。

这里是使用了第三方的项目来完成的，这里是[项目地址](https://github.com/schierlm/private-iota-testnet)，按照README中的Step by step instructions章节，一步步来编译代码生成jar包，利用jar包来生成配置文件Snapshot.txt。

另外，后续还需要使用jar包来创建the first milestone，是在第一个节点启动之后来创建的。

## 步骤1. 启动第一个节点
从[github](https://github.com/iotaledger/iri/releases)中寻找Mainnet v1.5.4，下载文件名为iri-1.5.4.jar的jar包，这是区块链各节点的主程序。

使用如下命令来启动第一个节点：
```
# java -jar iri-1.5.4.jar --testnet --testnet-no-coo-validation --snapshot=Snapshot.txt -p 14700 --max-peers 21 --remote
```
参数中-p为API端口，是强制的，--max-peers为最多可以连接的节点数量，默认为0，--remote为允许远程访问。

注意，这里参数没有指定udp连接端口，默认是14600，连接地址即udp://第一个节点ip:14600，后续会用到。

详细参数说明可以参考[这个链接](https://github.com/iotaledger/iri)的Command Line Options部分。

注意，第一个节点启动后，记得用前置步骤生成的jar包来创建the first milestone。

启动成功后，可以按照[这个链接](https://www.mobilefish.com/developer/iota/iota_quickguide_build_install_testnet_wallet.html)的说明下载编译钱包来查看相应账户。

## 步骤2. 使用docker启动剩余节点
使用步骤N制作好的docker启动剩余节点即可，命令如下：

```
docker run -d --net=host --name iota-node1 -e API_PORT=14701 -e UDP_PORT=14601 -e TCP_PORT=14601 -v /root/iota/docker/data1:/iri/data -v /root/iota/docker/neighbors:/iri/conf/neighbors iota-node:test_v1 /docker-entrypoint.sh
```
/root/iota/docker/data1是宿主机目录，创建后可为空，用来映射到docker中，保存节点运行数据。
/root/iota/docker/neighbors是记录连接节点的文件，内容如下
```
udp://第一个节点ip:14600
```

## 步骤N. docker镜像制作

使用Dockerfile来创建镜像，Dockerfile内容如下：
``` 
FROM bluedigits/iota-node
RUN rm /docker-entrypoint.sh \
    && rm /iri/target/iri-1.5.5.jar
COPY ./docker-entrypoint.sh /
COPY ./iri-1.5.4.jar /iri/target
RUN chmod +x /docker-entrypoint.sh
```
用到的iri-1.5.4.jar需要自行下载。
用到的docker-entrypoint.sh内容如下
```
#!/bin/bash

$neighbors
for buddy in $(cat /iri/conf/neighbors); do
  neighbors="$buddy $neighbors"
done
neighbors=${neighbors::-1}

exec java \
  $JAVA_OPTIONS \
  -Xms$MIN_MEMORY -Xmx$MAX_MEMORY \
  -Djava.net.preferIPv4Stack=true \
  -Dlogback.configurationFile=/iri/conf/logback.xml \
  -jar /iri/target/iri*.jar \
  --config /iri/conf/iri.ini \
  --port $API_PORT \
  --udp-receiver-port $UDP_PORT \
  --tcp-receiver-port $TCP_PORT \
  --remote --remote-limit-api "$REMOTE_API_LIMIT" \
  --neighbors "$neighbors" \
  --testnet \
  --testnet-no-coo-validation \
  --max-peers 21
  "$@"
```

