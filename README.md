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
java -DPORT=14265 -jar iri.jar
```

### Docker

Create an application.conf file with all of your configuration variables set in it.
Any that you don't provide in here will be assumed to be default or taken from
environment variables. You can find some of the possible environment variables in the next section.

Example:

`docker run -d --net=host --name iota-node -e TESTNET=true -p 14265:14265 -p 14777:14777/udp -p 15777:15777 -v application.conf:/iri/conf/application.conf iotaledger/iri:latest`

### Command Line Options 

All configuration variables can be set by java property, as system environment variable or via configuration file.

Java system property | Description | Example Input
--- | --- | ---
`-DPORT` | This is a *mandatory* option that defines the port to be used to send API commands to your node | `-DPORT=14800`
`-DNEIGHBOURS` | Neighbors that you are connected with will be added via this option. | `-DNEIGHBOURS="udp://148.148.148.148:14265 udp://[2001:db8:a0b:12f0::1]:14265"`
`-Dconfig.file` | Config .conf file that can be used instead of CLI options. See more below | `-Dconfig.file=application.conf`
`-DUDP_RECEIVER_PORT` | UDP receiver port | `-DUDP_RECEIVER_PORT=14800`
`-DTCP_RECEIVER_PORT` | TCP receiver port | `-DTCP_RECEIVER_PORT=14800`
`-DTESTNET` | Makes it possible to run IRI with the IOTA testnet | `-DTESTNET=true`
`-DREMOTE` | Remotely access your node and send API commands | `-DREMOTE=true`
`-DREMOTE_AUTH` | Require authentication password for accessing remotely. Requires a correct `username:hashedpassword` combination | `-DREMOTE_AUTH="iotatoken:LL9EZFNCHZCMLJLVUBCKJSWKFEXNYRHHMYS9XQLUZRDEKUUDOCMBMRBWJEMEDDXSDPHIGQULENCRVEYMO"`
`-DREMOTE_LIMIT_API` | Exclude certain API calls from being able to be accessed remotely | `-DREMOTE_LIMIT_API="attachToTangle, addNeighbors"`
`-DSEND_LIMIT`| Limit the outbound bandwidth consumption. Limit is set to mbit/s | `-DSEND_LIMIT=1.0`
`-DMAX_PEERS` | Limit the number of max accepted peers. Default is set to 0 (mutual tethering) | `-DMAX_PEERS=8`

### CONF File

You can also provide a conf file to store all of your command line options and easily update (especially neighbors) if needed. You can enable it via the `-Dconfig.file=/path/to/config.conf` flag. Here is an example CONF file:
```
PORT = 14700
UDP_RECEIVER_PORT = 14700
NEIGHBORS = "udp://my.favorite.com:15600"
IXI_DIR = ixi
HEADLESS = true
DEBUG = true
DB_PATH = db
```

