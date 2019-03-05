[![Build Status](https://travis-ci.org/iotaledger/iri.svg?branch=dev)](https://travis-ci.org/iotaledger/iri)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/dba5b7ae42024718893991e767390135)](https://www.codacy.com/app/iotaledger/iri?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=iotaledger/iri&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/dba5b7ae42024718893991e767390135)](https://www.codacy.com/app/iotaledger/iri?utm_source=github.com&utm_medium=referral&utm_content=iotaledger/iri&utm_campaign=Badge_Coverage)
![GitHub release](https://img.shields.io/github/release/iotaledger/iri.svg)
![license](https://img.shields.io/github/license/iotaledger/iri.svg)

# Trias StreamNet

The trias-lab/iri repository is the main StreamNet reference implementation, 
for the design details, please see yellow paper [[StreamNet]](https://github.com/wunder3605/iri/blob/dev/document/yellow\_paper/StreamNet/StreamNet.pdf). 
The original iri reference could be found at [[iri]](https://github.com/iotaledger/iri).

-* **License:** GPLv3


# Installing

The preferred option is that you compile yourself.
The second option is that you utilize the provided jar, 
which is released whenever there is a new update here: [Github Releases](https://github.com/trias-lab/iri/releases).

## Compiling yourself

Make sure to have Maven and Java 8 installed on your computer.

### To compile & package
```
$ git clone https://github.com/trias-lab/iri
$ cd iri
$ mvn clean compile
$ mvn package
```

### To compiple docker

```
$ docker build -t <name>:<tag> .
```

This will create a `target` directory in which you will find the executable jar file that you can use.

## Running yourself

### How to run one node

```
$ cd scripts/examples/
$ ./conflux_dag.sh
$ ./start_cli.sh
$ ./parallel_put_txn.sh
$ ./get_balance.sh
```

### How to run two nodes

```
$ cd scripts/examples/
$ ./conflux_dag_two_nodes.sh
$ ./start_cli_two_nodes.sh
$ ./parallel_put_txn_two_nodes.sh
$ ./get_balance_two_nodes.sh
```

### How to run docker

```
$ docker run -d --net=host --name <name> -v <local_data_dir>:/iri/data -v <neighbor_file>:/iri/conf/neighbors <name>:<tag> /entrypoint.sh
```
