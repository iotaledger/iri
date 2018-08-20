
---
### [addNeighbors](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L1147)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) addNeighborsStatement(java.util.List uris)

Add a list of neighbors to your node. 
 It should be noted that this is only temporary, and the added neighbors will be removed from your set of neighbors after you relaunch IRI.

 The URI (Unique Resource Identification) for adding neighbors is:
 **udp://IPADDRESS:PORT**

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "addNeighbors", "uris": ["udp://8.8.8.8:14265", "udp://8.8.8.8:14265"]}

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
{"duration": "621", "addedNeighbors": "530"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "addNeighbors", "uris": ["udp://8.8.8.8:14265", "udp://8.8.8.8:14265"]}

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
{"duration": "779", "addedNeighbors": "754"}
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
-d '{"command": "addNeighbors", "uris": ["udp://8.8.8.8:14265", "udp://8.8.8.8:14265"]}'
</Section>

<Section type="response">
{"duration": "667", "addedNeighbors": "630"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<



***
	
|Parameters | Description |
|--|--|
| uris | list of neighbors to add |

***

Returns [AddedNeighborsResponse](/javadoc/com/iota/iri/service/dto/addedneighborsresponse/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
| addedNeighbors | Gets the number of added neighbors. |
***