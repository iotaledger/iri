# Merge

Copy or clone source code into your `ixi` directory such that it can be found as `ixi/Merge/{index.js, package.json}`. 
Your node may be running at this time, and it will hot-load the script. 
After you've cloned it, and with a running iri node, run the following command to merge your spent-addresses-db with the supplied files:

```
curl http://localhost:14265 -X POST -H 'X-IOTA-API-Version: 1.4.1' -H 'Content-Type: application/json'   -d '{"command": "Merge.mergeSpentAddresses", "files": ["spentAddresses.txt"]}'
```

-----

#### Troubleshooting:

Make sure the file you passed to the node exists on the node. If you do not supply the files array, we automatically search for `spentAddresses.txt`

