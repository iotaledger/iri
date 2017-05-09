# IOTA IRI


### master branch build status: [![Build Status](https://travis-ci.org/iotaledger/iri.svg?branch=master)](https://travis-ci.org/iotaledger/iri)


* **Latest release:** 1.1.2.4 Release
* **License:** GPLv3
* **Readme updated:** 2016-12-12 13:26:43 (UTC)


![Alt](https://files.readme.io/v2GmVOXhRw6tOxXMDSDg_Screen%20Shot%202016-07-03%20at%2014.14.56.png "Tangle")
### Introduction


This repository is **the** reference [IOTA](http://www.iotatoken.com/) implementation that functions as a complete node with a built in JSON-REST HTTP interface.

Communication with the node can be accomplished in one of two ways
1. direct local connection using java 
2. remote communication using HTTP POST requests and JSON responses

As IOTA IRI is currently being refactored and improved, contributions are welcome and appreciated!


### requirements
- Java 8  ([openjDK](http://openjdk.java.net/install/) | [oracle](http://www.oracle.com/technetwork/java/javase/overview/java8-2100321.html))  
- Maven ([apache maven](https://maven.apache.org/install.html)) 


### compile
```
git clone https://github.com/iotaledger/iri
cd iri
mvn clean compile
```


### build
```
mvn package
```

### execution
required
```
-p, --port             define the API port (MANDATORY)
```
optional
```
-n specify the list of neighbors 
       (please note: neighbors must be defined between '' or "" depending on terminal

-r or --receiver-port define the Transaction receiver port

-c or --enabled-cors enable the API cross origin filter: cors domain defined between ''

--headless disable the logo (logo still WIP)

-d or --debug prints on the standard output, more debug informations

-e or --experimental activates experimental features. 
       (current feature: Random Tip Selector Broadcaster)

-h prints the usage

```
### example
```
java -jar target/iri-1.1.2.4.jar -p 142655 
-n 'udp://1.1.1.1:14265 udp://2.2.2.2:14265' -d -c 'iotatoken.com'
```
### formal prototypical example
```
java -jar IRI-${version}.jar [{-p,--port} 14265] [{-r,--receiver-port} 14265]
[{-c,--enabled-cors} *] [{-h}] [[--headless}] [{-d,--debug}] 
[{-n,--neighbors} '<list of neighbors>'] [{-e,--experimental}]
```
