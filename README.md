##Dev branch build status: 
[![Build Status](https://travis-ci.org/iotaledger/iri.svg?branch=dev)](https://travis-ci.org/iotaledger/iri)

##Introduction

The IRI is the IOTA reference implementation project. It is a complete [[IOTA]](http://www.iotatoken.com/) Node with a JSON-REST HTTP interface.

It allows to connect easily using java directly to a local or a remote [[IOTA node]](https://iota.readme.io/docs/syncing-to-the-network).

* **Latest release:** 1.1.1 Release
* **License:** GPLv3
* **Readme updated:** 2016-12-02 23:26:43 (UTC)

The current reference implementation is in refatoring phase. Any contribution is highly appreciated.

##Technologies & dependencies

The JOTA library has been designed to be used with Java8.

##Usage

You'll need maven installed. In order

* To compile:

`mvn clean compile`

* To create an executable build.

`mvn package`

This will create in the root directory of the project an executable jar package called IRI-${release-version}.jar . For instance "IRI-${version}.jar"

* To execute:

`java -jar IRI-${version}.jar  [{-p,--port} 14265] [{-r,--receiver-port} 14265] [{-c,--enabled-cors} *] [{-h}] [[--headless}] [{-d,--debug}] [{-n,--neighbors} '<list of neighbors>'] `

Where

`-p or --port define the API port (MANDATORY)`

`-n specify the list of neighbors (MANDATORY) Please note: neighbors must be defined between ''`

the following parameters are optional:

`-r or --receiver-port define the Transaction receiver port`

`-c or --enabled-cors enable the API cross origin filter: cors domain defined between ''`

`--headless disable the logo (logo still WIP)`

`-d or --debug prints on the standard output, more debug informations`

`-h prints the usage`
 
For instance

`java -jar target/iri-1.1.1.jar -p 14265 -n 'udp://1.1.1.1:14265 udp://2.2.2.2:14265' -d -c 'iotatoken.com'`


