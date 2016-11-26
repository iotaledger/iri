##Introduction

The IRI is the IOTA reference implementation project. It is a complete [[IOTA]](http://www.iotatoken.com/) Node with a JSON-REST HTTP interface.

It allows to connect easily using java directly to a local or a remote [[IOTA node]](https://iota.readme.io/docs/syncing-to-the-network).

* **Latest release:** 1.1.1 Release
* **License:** GPLv3
* **Readme updated:** 2016-11-01 10:23:43 (UTC)

The current reference implementation is in refatoring phase. Any contribution is highly appreciated.

##Technologies & dependencies

The JOTA library has been designed to be used with Java 6+ in order to allow Iot devices to support it.

##Usage

You'll need maven installed. In order

* To compile:

`mvn clean compile`

* To create an executable build.

`mvn package`

This will create in the root directory of the project an executable jar package called IRI-${release-version}.jar . For instance "IRI-1.1.0.jar"

* To execute

`java -jar IRI-1.1.0.jar [port number] <list of neighbors>`

For example

`java -jar IRI-1.1.0.jar 14265 udp://1.1.1.1:14265 udp://2.2.2.2:14265`
