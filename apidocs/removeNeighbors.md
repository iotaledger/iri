
---
### [removeNeighbors](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L576)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) removeNeighborsStatement(java.util.List uris)

Temporarily removes a list of neighbors from your node.
 The added neighbors will be added again after relaunching IRI. 
 Remove the neighbors from your config file or make sure you don't supply them in the -n command line option if you want to keep them removed after restart.

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
{"duration": "724", "removedNeighbors": "734"}
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
{"duration": "843", "removedNeighbors": "771"}
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
{"duration": "363", "removedNeighbors": "989"}
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