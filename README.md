## IOTA

The IRI repository is the main branch of the IOTA Reference Implementation and the embodiment
of the IOTA network specification. 

This is a full-featured [[IOTA]](http://iota.org/) node with a convenient JSON-REST HTTP interface.
It allows users to become part of the [[IOTA]](http://iota.org) network as both a transaction relay
and network information provider through the easy-to-use API.

It is specially designed for users seeking a fast, efficient and fully-compatible network setup.

Running an IRI node also allows light wallet users a node to directly connect to for their own wallet transactions.

* **Latest release:** 1.4.2.2 Release
* **License:** GPLv3

# How to get started

The IOTA network is an independent peer-to-peer network with a first-user, friend-to-friend, network structure:

- As a 'first-user' network, to access the data streams and APIs that other users provide, you must first exchange your IP and port configuration with a current user.

- As a 'friend-to-friend' network, you have the privilege of joining new users into the network through your node
by adding them to your approved neighbors list — ensuring that you both broadcast to them and also receive their broadcasts.

You can **find neighbors** quickly at both our [[Discord Community]](https://discord.gg/7Gu2mG5) and [[forum.iota.org]](https://forum.iota.org/).
 
Everyone will be welcoming and very happy to help you get connected.
If you want to get tokens for your testcase, please just ask in one of the communication channels.

## Reporting Issues

If you notice any bugs, problems or other irregularities with this release,
please submit an issue on github [[submit new issue]](https://github.com/iotaledger/iri/issues/new).

# Installing

The preferred option is that you compile yourself.
The second option is that you utilize the provided jar, 
which is released whenever there is a new update here: [Github Releases](https://github.com/iotaledger/iri/releases).

### Compiling yourself  

Make sure to have Maven and Java 8 installed on your computer.

#### To compile & package
```
$ git clone https://github.com/iotaledger/iri
$ cd iri
$ mvn clean compile
$ mvn package
```

This will create a `target` directory in which you will find the executable jar file that you can use.

### How to run IRI 

#### Locally

Running IRI is quick and easy, and you can usually run it without admin rights.
Below is a list of command line options.
At a minimum you must provide one of:

- the port — e.g., '`-p 14265`'
- the location of an ini config file — e.g., '`-c iota.ini`' 
- the default config ini file must exist **and also** contain the port setting — e.g.,'`PORT = 14265`'.

If the '`iota.ini`' file exists, it will be read even if it is not specified on the command line. The port and all the command line options below take precedence over values specified in the ini config file.

Here is an example script that specifies only the port, with all other setting to be read from the ini file **if it exists**:

```
java -jar iri.jar -p 14265
```

### Docker

Create an iota.ini file with all of your configuration variables set in it.
Any that you don't provide in here will be assumed to be default or taken from
command line arguments.

`docker run -d --net=host --name iota-node -v iota.ini:/iri/iota.ini iotaledger/iri:latest`

### Command Line Options 

Option | Shortened version | Description | Example Input
--- | --- | --- | --- 
`--port` | `-p` | This is a *mandatory* option that defines the port to be used to send API commands to your node | `-p 14265`
`--neighbors` | `-n` | Neighbors that you are connected with will be added via this option. | `-n "udp://148.148.148.148:14265 udp://[2001:db8:a0b:12f0::1]:14265"`
`--config` | `-c` | Config INI file that can be used instead of CLI options. See more below | `-c iri.ini`
`--udp-receiver-port` | `-u` | UDP receiver port | `-u 14600`
`--tcp-receiver-port` | `-t` | TCP receiver port | `-t 15600`
`--testnet` | | Makes it possible to run IRI with the IOTA testnet | `--testnet`
`--remote` | | Remotely access your node and send API commands | `--remote`
`--remote-auth` | | Require authentication password for accessing remotely. Requires a correct `username:hashedpassword` combination | `--remote-auth iotatoken:LL9EZFNCHZCMLJLVUBCKJSWKFEXNYRHHMYS9XQLUZRDEKUUDOCMBMRBWJEMEDDXSDPHIGQULENCRVEYMO`
`--remote-limit-api` | | Exclude certain API calls from being able to be accessed remotely | `--remote-limit-api "attachToTangle, addNeighbors"`
`--send-limit`| | Limit the outbound bandwidth consumption. Limit is set to mbit/s | `--send-limit 1.0`
`--max-peers` | | Limit the number of max accepted peers. Default is set to 0 (mutual tethering) | `--max-peers 8`
`--dns-resolution-false` | | Ignores DNS resolution refreshing  | `--dns-resolution-false`	
### INI File

You can also provide an ini file to store all of your command line options and easily update (especially neighbors) if needed. You can enable it via the `--config` flag. Here is an example INI file:
```
[IRI]
PORT = 14265
UDP_RECEIVER_PORT = 14600
NEIGHBORS = udp://my.favorite.com:14600
IXI_DIR = ixi
HEADLESS = true
DEBUG = false
DB_PATH = db
```