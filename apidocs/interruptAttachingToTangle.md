
---
### [interruptAttachingToTangle](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L714)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) interruptAttachingToTangleStatement()

Interrupts and completely aborts the `attachToTangle` process.

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "interruptAttachingToTangle"}

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
{"duration": "759"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "interruptAttachingToTangle"}

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
{"duration": "615"}
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
-d '{"command": "interruptAttachingToTangle"}'
</Section>

<Section type="response">
{"duration": "267"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<





***

Returns [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
***