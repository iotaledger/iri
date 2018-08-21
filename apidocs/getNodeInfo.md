
---
### [getNodeInfo](https://github.com/iotaledger/iri/blob/dev/src/main/java/com/iota/iri/service/API.java#L727)
 [AbstractResponse](/javadoc/com/iota/iri/service/dto/abstractresponse/) getNodeInfoStatement()

Returns information about your node.

<Tabs> 

<Tab language="Python">

<Section type="request">
import urllib2
import json

command = {"command": "getNodeInfo"}

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
{"duration": "882", "appName": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "appVersion": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "jreAvailableProcessors": "414", "jreFreeMemory": "missing_data", "jreMaxMemory": "missing_data", "jreTotalMemory": "missing_data", "jreVersion": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestMilestone": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestMilestoneIndex": "248", "latestSolidSubtangleMilestone": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestSolidSubtangleMilestoneIndex": "793", "milestoneStartIndex": "58", "neighbors": "385", "packetsQueueSize": "140", "time": "missing_data", "tips": "116", "transactionsToRequest": "109"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>

<Tab language="NodeJS">

<Section type="request">
var request = require('request');

var command = {"command": "getNodeInfo"}

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
{"duration": "993", "appName": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "appVersion": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "jreAvailableProcessors": "64", "jreFreeMemory": "missing_data", "jreMaxMemory": "missing_data", "jreTotalMemory": "missing_data", "jreVersion": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestMilestone": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestMilestoneIndex": "157", "latestSolidSubtangleMilestone": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestSolidSubtangleMilestoneIndex": "840", "milestoneStartIndex": "416", "neighbors": "487", "packetsQueueSize": "327", "time": "missing_data", "tips": "5", "transactionsToRequest": "151"}
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
-d '{"command": "getNodeInfo"}'
</Section>

<Section type="response">
{"duration": "30", "appName": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "appVersion": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "jreAvailableProcessors": "57", "jreFreeMemory": "missing_data", "jreMaxMemory": "missing_data", "jreTotalMemory": "missing_data", "jreVersion": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestMilestone": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestMilestoneIndex": "91", "latestSolidSubtangleMilestone": "P9KFSJVGSPLXAEBJSHWFZLGP9GGJTIO9YITDEHATDTGAFLPLBZ9FOFWWTKMAZXZHFGQHUOXLXUALY9999", "latestSolidSubtangleMilestoneIndex": "962", "milestoneStartIndex": "601", "neighbors": "939", "packetsQueueSize": "949", "time": "missing_data", "tips": "831", "transactionsToRequest": "988"}
</Section>

<Section type="error">
{"error": "'command' parameter has not been specified"}
</Section>
</Tabs<





***

Returns [GetNodeInfoResponse](/javadoc/com/iota/iri/service/dto/getnodeinforesponse/)

|Return | Description |
|--|--|
| duration | The duration it took to process this command in milliseconds |
| appName | Gets the app name |
| appVersion | Name of the IOTA software you're currently using (IRI stands for Initial Reference Implementation) |
| jreAvailableProcessors | Available cores on your machine for JRE. |
| jreFreeMemory | The amount of free memory in the Java Virtual Machine. |
| jreMaxMemory | The maximum amount of memory that the Java virtual machine will attempt to use. |
| jreTotalMemory | The total amount of memory in the Java virtual machine. |
| jreVersion | The JRE version this node runs on |
| latestMilestone | The hash of the latest transaction that was signed off by the coordinator. |
| latestMilestoneIndex | Index of the latest milestone. |
| latestSolidSubtangleMilestone | The hash of the latest transaction which is solid and is used for sending transactions. For a milestone to become solid your local node must basically approve the subtangle of coordinator-approved transactions, and have a consistent view of all referenced transactions. |
| latestSolidSubtangleMilestoneIndex | Index of the latest solid subtangle. |
| milestoneStartIndex | Gets the start of the milestone index |
| neighbors | Number of neighbors you are directly connected with. |
| packetsQueueSize | Packets which are currently queued up. |
| time | Current UNIX timestamp. |
| tips | Number of tips in the network. |
| transactionsToRequest | When a node receives a transaction from one of its neighbors, this transaction is referencing two other transactions t1 and t2 (trunk and branch transaction). If either t1 or t2 (or both) is not in the node's local database, then the transaction hash of t1 (or t2 or both) is added to the queue of the "transactions to request". At some point, the node will process this queue and ask for details about transactions in the "transaction to request" queue from one of its neighbors. By this means, nodes solidify their view of the tangle (i.e. filling in the unknown parts). |
***