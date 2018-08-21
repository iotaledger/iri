
---
### [getNeighbors](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L708)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) getNeighborsStatement()

Returns the set of neighbors you are connected with, as well as their activity statistics (or counters). 
 The activity counters are reset after restarting IRI.

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "getNeighbors"}

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
{"duration": "588", "neighbors": ["{ 
"address": "/8.8.8.8:14265", 
"numberOfAllTransactions": 397, 
"numberOfInvalidTransactions": 157, 
"numberOfNewTransactions": 192 
}", "{ 
"address": "/8.8.8.8:14265", 
"numberOfAllTransactions": 334, 
"numberOfInvalidTransactions": 331, 
"numberOfNewTransactions": 32 
}"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "getNeighbors"}

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
{"duration": "393", "neighbors": ["{ 
"address": "/8.8.8.8:14265", 
"numberOfAllTransactions": 915, 
"numberOfInvalidTransactions": 432, 
"numberOfNewTransactions": 285 
}", "{ 
"address": "/8.8.8.8:14265", 
"numberOfAllTransactions": 52, 
"numberOfInvalidTransactions": 223, 
"numberOfNewTransactions": 197 
}"]}
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
-d '{"command": "getNeighbors"}'
</Section>

<Section type="response">
{"duration": "963", "neighbors": ["{ 
"address": "/8.8.8.8:14265", 
"numberOfAllTransactions": 366, 
"numberOfInvalidTransactions": 682, 
"numberOfNewTransactions": 673 
}", "{ 
"address": "/8.8.8.8:14265", 
"numberOfAllTransactions": 748, 
"numberOfInvalidTransactions": 678, 
"numberOfNewTransactions": 722 
}"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<





***

Returns [GetNeighborsResponse](/javadoc/com/iota/iri/service/dto/getneighborsresponse/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
| neighbors | The list of neighbors, including the following stats: address, connectionType, numberOfAllTransactions, numberOfRandomTransactionRequests, numberOfNewTransactions, numberOfInvalidTransactions, numberOfSentTransactions |
***