
---
### [removeNeighbors](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L573)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) removeNeighborsStatement(java.util.List uris)

Removes a list of neighbors to your node. 
 This is only temporary, and if you have your neighbors added via the command line, they will be retained after you restart your node.

 The URI (Unique Resource Identification) for removing neighbors is:
 **udp://IPADDRESS:PORT**
 
 Returns an {@link com.iota.iri.service.dto.ErrorResponse} if the URI scheme is wrong

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "removeNeighbors", "uris": ["udp://8.8.8.8:14265", "udp://8.8.8.8:14265"]}

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
{"duration": "23", "removedNeighbors": "48"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "removeNeighbors", "uris": ["udp://8.8.8.8:14265", "udp://8.8.8.8:14265"]}

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
{"duration": "877", "removedNeighbors": "187"}
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
-d '{"command": "removeNeighbors", "uris": ["udp://8.8.8.8:14265", "udp://8.8.8.8:14265"]}'
</Section>

<Section type="response">
{"duration": "3", "removedNeighbors": "137"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<



***
	
|Parameters | Description |
|--|--|
| uris | List of URI elements. |

***

Returns [RemoveNeighborsResponse](/javadoc/com/iota/iri/service/dto/removeneighborsresponse/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
| removedNeighbors | The number of removed neighbors. |
***