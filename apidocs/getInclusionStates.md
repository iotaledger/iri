
---
### [getInclusionStates](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L747)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) getInclusionStatesStatement(java.util.List trans, java.util.List tps)

Get the inclusion states of a set of transactions. 
 This is for determining if a transaction was accepted and confirmed by the network or not. 
 You can search for multiple tips (and thus, milestones) to get past inclusion states of transactions.

 This API call simply returns a list of boolean values in the same order as the transaction list you submitted, thus you get a true/false whether a transaction is confirmed or not.
 Returns an {@link com.iota.iri.service.dto.ErrorResponse} if a tip is missing or the subtangle is not solid

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "getInclusionStates", "trans": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"], "tps": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}

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
{"duration": "439", "states": ["true", "true"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "getInclusionStates", "trans": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"], "tps": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}

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
{"duration": "902", "states": ["true", "true"]}
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
-d '{"command": "getInclusionStates", "trans": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"], "tps": ["P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999"]}'
</Section>

<Section type="response">
{"duration": "655", "states": ["true", "true"]}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<



***
	
|Parameters | Description |
|--|--|
| trans | List of transactions you want to get the inclusion state for. |
| tps | List of tips (including milestones) you want to search for the inclusion state. |

***

Returns [GetInclusionStatesResponse](/javadoc/com/iota/iri/service/dto/getinclusionstatesresponse/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
| states | List of boolean values in the same order as the transaction list you submitted, thus you get a true/false whether a transaction is confirmed or not. |
***