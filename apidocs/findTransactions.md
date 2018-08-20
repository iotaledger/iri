
---
### [findTransactions](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L854)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) findTransactionsStatement(java.util.Map request)

Find the transactions which match the specified input and return. 
 All input values are lists, for which a list of return values (transaction hashes), in the same order, is returned for all individual elements. 
 The input fields can either be `bundles`, `addresses`, `tags` or `approvees`. 
 **Using multiple of these input fields returns the intersection of the values.**
 
 Returns an {@link com.iota.iri.service.dto.ErrorResponse} if more then maxFindTxs was found

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "findTransactions", "request": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}

stringified = json.dumps(command)

headers = {
    'content-type': 'application/json',
    'X-IOTA-API-Version': '1'
}

request = urllib2.Request(url="http://localhost:14265", data=stringified, headers=headers)
returnData = urllib2.urlopen(request).read()

jsonData = json.loads(returnData)

print jsonData
</Section>

<Section type="response">
{"duration": "952", "hashes": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "findTransactions", "request": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}

var options = {
  url: 'http://localhost:14265',
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
		'X-IOTA-API-Version': '1',
    'Content-Length': Buffer.byteLength(JSON.stringify(command))
  },
  json: command
};

request(options, function (error, response, data) {
  if (!error && response.statusCode == 200) {
    console.log(data);
  }
});
</Section>

<Section type="response">
{"duration": "604", "hashes": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="cURL">

<Section type="request">
curl http://localhost:14265 
-X POST 
-H 'Content-Type: application/json' 
-H 'X-IOTA-API-Version: 1' 
-d '{"command": "findTransactions", "request": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}'
</Section>

<Section type="response">
{"duration": "903", "hashes": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<



***
	
|Parameters | Description |
|--|--|
| request | the map with input fields |

***

Returns [FindTransactionsResponse](/javadoc/com/iota/iri/service/dto/findtransactionsresponse/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
| hashes | The transaction hashes which are returned depend on your input. For each specified input value, the command will return the following: <code>bundles</code>: returns the list of transactions which contain the specified bundle hash. <code>addresses</code>: returns the list of transactions which have the specified address as an input/output field. <code>tags</code>: returns the list of transactions which contain the specified tag value. <code>approvees</code>: returns the list of transaction which reference (i.e. confirm) the specified transaction. |
***