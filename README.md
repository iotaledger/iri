[![Build Status](https://travis-ci.org/iotaledger/iri.svg?branch=dev)](https://travis-ci.org/iotaledger/iri)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/dba5b7ae42024718893991e767390135)](https://www.codacy.com/app/iotaledger/iri?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=iotaledger/iri&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/dba5b7ae42024718893991e767390135)](https://www.codacy.com/app/iotaledger/iri?utm_source=github.com&utm_medium=referral&utm_content=iotaledger/iri&utm_campaign=Badge_Coverage)
![GitHub release](https://img.shields.io/github/release/iotaledger/iri.svg)
![license](https://img.shields.io/github/license/iotaledger/iri.svg)

<p align="center"><img src="https://github.com/triasteam/StreamNet/blob/dev/document/pictures/StreamNet.png" alt="StreamNet Logo" title="StreamNet"/></p>

# Trias StreamNet

The trias-lab/iri repository is the main StreamNet reference implementation, 
for the design details, please see yellow paper [[StreamNet]](https://github.com/triasteam/iri/blob/dev/document/yellow\_paper/StreamNet/StreamNet.pdf). 
The original iri reference could be found at [[iri]](https://github.com/iotaledger/iri).

-* **License:** GPLv3


## 1. Installing

The preferred option is that you compile yourself.
The second option is that you utilize the provided jar, 
which is released whenever there is a new update here: [Github Releases](https://github.com/trias-lab/iri/releases).

### 1.1 Compiling yourself

Make sure to have Maven and Java 8 installed on your computer.

#### 1.1.1 To compile & package
```bash
git clone https://github.com/trias-lab/iri
cd iri
mvn clean compile
mvn package
```

This will create a `target` directory in which you will find the executable jar file that you can use.

#### 1.1.2 To compiple docker

```bash
docker build -t <name>:<tag> .
```

This will create a docker image for you to deploy

### 1.2 Running yourself

#### 1.2.1 How to run one node

##### Run one node in single transaction mode

```bash
cd scripts/examples/one_node/
./conflux_dag.sh
./start_cli.sh
./parallel_put_txn.sh
./get_balance.sh
```

##### Run one node in batch transaction mode

```bash
cd scripts/examples/one_node_batch
./conflux_dag.sh
./start_cli.sh
./parallel_put_txn.sh
./get_balance.sh
```

#### 1.2.2 How to run two nodes

##### Run two nodes in single transaction mode

```bash
cd scripts/examples/two_nodes
./conflux_dag_two_nodes.sh
./start_cli_two_nodes.sh
./parallel_put_txn_two_nodes.sh
./get_balance_two_nodes.sh
```

##### Run two nodes in multiple transaction mode

```bash
cd scripts/examples/two_nodes_batch
./conflux_dag_two_nodes.sh
./start_cli_two_nodes.sh
./parallel_put_txn_two_nodes.sh
./get_balance_two_nodes.sh
```

#### 1.2.3 How to run docker

```
$ docker run -d --net=host --name <name> -v <local_data_dir>:/iri/data -v <neighbor_file>:/iri/conf/neighbors <name>:<tag> /entrypoint.sh
```

## 2. MISC

### 2.1 Performance Tunning 

Please refere [[Performance tunning]](https://github.com/triasteam/iri/blob/dev/scripts/iota\_perf/README.md) for details of how to measure performance using Nginx + Jmeter. 

### 2.2 Cluster deployment 

Please refer [[Cluster deployment]](https://github.com/triasteam/iri/blob/dev/scripts/examples/cluster/README.md) for details of how to deploy multiple nodes.

### 2.3 Frontend deployment

Please refer [[Frontend deployment]](https://github.com/triasteam/StreamNet/blob/dev/scripts/front_end/README.md) for details of how to deploy the frontend

### 2.4 Portainer deployment 

Please refer [[Portainer deployment]](https://github.com/triasteam/StreamNet/blob/dev/document/iota_deploy/portainer-deploy-info.md) for details of how to leverage the portainer to manage containers

## 3  Team

[Zhaoming Yin](https://stplaydog.github.io/), [Junqing Wang](https://wunder3605.github.io/), [Yahui Wang](https://wangyh2016.github.io/wangyahui.github.io/), [Haifeng Li](https://lihaifeng111.github.io/lihaifeng.github.io/), [Huafeng Li](https://lhfbc.github.io/)
