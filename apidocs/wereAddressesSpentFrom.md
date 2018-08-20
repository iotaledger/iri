
---
### [wereAddressesSpentFrom](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L377)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) wereAddressesSpentFromStatement(java.util.List addresses)

Check if a list of addresses was ever spent from, in the current epoch, or in previous epochs.

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "wereAddressesSpentFrom", "addresses": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}

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
{"duration": "861", "states": ["true", "true"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "wereAddressesSpentFrom", "addresses": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}

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
{"duration": "852", "states": ["true", "true"]}
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
-d '{"command": "wereAddressesSpentFrom", "addresses": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}'
</Section>

<Section type="response">
{"duration": "38", "states": ["true", "true"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<



***
	
|Parameters | Description |
|--|--|
| addresses | List of addresses to check if they were ever spent from. |

***

Returns [wereAddressesSpentFrom](/javadoc/com/iota/iri/service/dto/wereaddressesspentfrom/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
| states | States for the specified addresses |
***