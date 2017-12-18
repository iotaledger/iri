## IOTA

This is the main branch of the main IRI repository, as this is a IOTA reference implementation that is utilized specifically for what we have setup. It is a complete [[IOTA]](http://iota.org/) Node with a JSON-REST HTTP interface.

It allows to connect easily using java directly to a local or a remote [[IOTA node]](http://learn.iota.org/).

* **Latest release:** 1.4.1 Release
* **License:** GPLv3

# How to get started

Obviously, because this is its own, independent network, you have to go through the same process as in the main network: **find neighbors**. You can find neighbors in the `#nodesharing` Slack channel[[Slack Invite]](http://slack.iota.org), or on our forum. Community members are usually very happy to help you out and get you connected. If you want to get tokens for your testcase, please just ask in one of the communication channels as well.

## Reporting Issues

If you notice any issues or irregularities in this release. Please make sure to submit an issue on github.


# Installing

You have two options, the preferred option is that you compile yourself. The second option is that you utilize the provided jar, which is released regularly (when new updates occur) here: [Github Releases](https://github.com/iotaledger/iri/releases).


### Compiling yourself  

Make sure to have Maven and Java 8 installed on your computer.

#### To compile & package
```
$ git clone https://github.com/iotaledger/iri
$ cd iri
$ mvn clean compile
$ mvn package
```

This will create a `target` directory in which you will find the executable jar file that you can use for the

### How to run IRI

#### Locally

Running IRI is pretty simple, and you don't even have to run it under admin rights. Below is a list of command line options. Here is an example script:

```
java -jar iri.jar -p 14265
```

### Docker

No need to create a config file, everything can be configured with environment variables.

```
$ docker run --net=host -d --name iota-node -v iota_data:/opt/iri/data -e "NEIGHBORS=udp://neighbor1:14600 tcp://neighbor1:15600 udp://neighbor3:14600" iotaledger/iri
```

To rescan the database or revalidate, you can do this:

```
$ docker run --net=host --rm --name iota-node -v iota_data:/opt/iri/data -e "NEIGHBORS=udp://neighbor1:14600 tcp://neighbor1:15600 udp://neighbor3:14600" iotaledger/iri --rescan
$ docker run --net=host --rm --name iota-node -v iota_data:/opt/iri/data -e "NEIGHBORS=udp://neighbor1:14600 tcp://neighbor1:15600 udp://neighbor3:14600" iotaledger/iri --revalidate
```

#### Environment variables

You can configure different things with environment variables:

| Name             | Description                                                                                         |
| ---------------- | --------------------------------------------------------------------------------------------------- |
| NEIGHBORS        | List of your neighbors (space delimited).                                                           |
| REMOTE_API_LIMIT | List of things that are forbidden via api (Default: attachToTangle, addNeighbors, removeNeighbors). |
| API_PORT         | Port for API listener (Default: 14265).                                                             |
| UDP_PORT         | Port for UDP listener (Default: 14600).                                                             |
| TCP_PORT         | Port for TCP listener (Default: 15600).                                                             |
| DB_PATH          | Path of database (Default: data/db [in the docker container]).                                      |
| IXI_DIR          | IXI directory (Default: data/ixi [in the docker container]).                                        |           
| DEBUG            | Debug mode (Default: false)                                                                         |
| TESTNET          | Run on testnet (Default: false)                                                                     |
| JAVA_OPTIONS     | Java options (Default: -XX:+DisableAttachMechanism -XX:+HeapDumpOnOutOfMemoryError -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap) |

#### Run with docker-compose

You can also run IRI with docker-compose. See this example:

```
version: '2'

services:
  iota-node:
    container_name: iota-node
    restart: always
    image: iotaledger/iri:latest
    mem_limit: 8G
    network_mode: host
    environment:
      - TZ=Europe/Berlin
      - NEIGHBORS=udp://neighbor1:14600 tcp://neighbor1:15600 udp://neighbor3:14600
    volumes:
      - /opt/iri-data:/opt/iri/data
```

### Command Line Options

Option | Shortened version | Description | Example Input
--- | --- | --- | ---
`--port` | `-p` | This is a *mandatory* option that defines the port to be used to send API commands to your node | `-p 14800`
`--neighbors` | `-n` | Neighbors that you are connected with will be added via this option. | `-n "udp://148.148.148.148:14265 udp://[2001:db8:a0b:12f0::1]:14265"`
`--config` | `-c` | Config INI file that can be used instead of CLI options. See more below | `-c iri.ini`
`--udp-receiver-port` | `-u` | UDP receiver port | `-u 14800`
`--tcp-receiver-port` | `-t` | TCP receiver port | `-t 14800`
`--testnet` | | Makes it possible to run IRI with the IOTA testnet | `--testnet`
`--remote` | | Remotely access your node and send API commands | `--remote`
`--remote-auth` | | Require authentication password for accessing remotely. Requires a correct `username:hashedpassword` combination | `--remote-auth iotatoken:LL9EZFNCHZCMLJLVUBCKJSWKFEXNYRHHMYS9XQLUZRDEKUUDOCMBMRBWJEMEDDXSDPHIGQULENCRVEYMO`
`--remote-limit-api` | | Exclude certain API calls from being able to be accessed remotely | `--remote-limit-api "attachToTangle, addNeighbors"`
`--send-limit`| | Limit the outbound bandwidth consumption. Limit is set to mbit/s | `--send-limit 1.0`
`--max-peers` | | Limit the number of max accepted peers. Default is set to 0 (mutual tethering) | `--max-peers 8`

### INI File

You can also provide an ini file to store all of your command line options and easily update (especially neighbors) if needed. You can enable it via the `--config` flag. Here is an example INI file:
```
[IRI]
PORT = 14700
UDP_RECEIVER_PORT = 14700
NEIGHBORS = udp://my.favorite.com:15600
IXI_DIR = ixi
HEADLESS = true
DEBUG = true
DB_PATH = db
```
