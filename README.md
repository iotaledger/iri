## IOTA Testnet IRI

This is a branch of the main IRI repository, as this is a IOTA reference implementation that is utilized specifically for the testnet we have setup. It is a complete [[IOTA]](http://www.iotatoken.com/) Node with a JSON-REST HTTP interface.

It allows to connect easily using java directly to a local or a remote [[IOTA node]](https://iota.readme.io/docs/syncing-to-the-network).

* **Latest release:** 1.1.1 Release
* **License:** GPLv3

# Purpose of this repository

Because the IOTA Testnet runs under different condtions than the main network, we have had to create a specific client just for the testnet. For one, there is no `snapshot` with the original holder's balances (see below for how to get tokens), and for another we have lowered the `minWeightMagnitude` minimum from 18 to `13`. This means that doing Proof of Work in this environment is executed much faster.

The IOTA Foundation will also utilize this testnet to thoroughly review and test more experimental features (automated snapshotting and IXI for example will be tested soon). As such, you shouldn't just treat the testnet as an environment where you can roam freely without having to worry, you should also see it as a testbed, for the core team to improve the protocols, libraries and tools.

# How to get started

Obviously, because this is its own, independent network, you have to go through the same process as in the main network: **find neighbors**. You can find neighbors in the `#testnet` Slack channel, or on our forum. Community members are usually very happy to help you out and get you connected. If you want to get tokens for your testcase, please just ask in one of the communication channels as well.

## Reporting Issues

If you notice any issues or irregularities in this release. Please make sure to submit an issue on github.

# Installing

You have two options, the preferred option is that you compile yourself. The second option is that you utilize the provided jar, which is released regularly (when new updates occur) here: [Github Releases](https://github.com/iotaledger/iri/releases). Make sure to utilize the `testnet` versions!

### Compiling yourself  

Make sure to have Maven and Java 8 installed on your computer. When cloning the project from github, also make sure to get the `testnet` branch.

#### To compile
```
$ git clone https://github.com/iotaledger/iri -b testnet
$ mvn clean compile
```

#### To create an executable build.

```
$ mvn package
```

This will create in the `target` directory of the project an executable jar package called IRI-${release-version}.jar . For instance `iri-1.1.1.jar`

#### To execute

`java -jar IRI-${version}.jar [port number] <list of neighbors>`

For example

`java -jar IRI-${version}.jar 14265 udp://1.1.1.1:14265 udp://2.2.2.2:14265`
