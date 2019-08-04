# Spent

Copy or clone source code into your `ixi` directory such that it can be found as `ixi/Spent/{index.js, package.json}`. 
Your node may be running at this time, and it will hot-load the script. 
After you've cloned it, and with a running iri node, run the following command to generate the spent addresses file:

```
curl http://localhost:14265 -X POST -H 'X-IOTA-API-Version: 1.4.1' -H 'Content-Type: application/json'   -d '{"command": "Spent.generateSpentAddressesFile"}'
```

Generated file will start with the timestamp of generation start.
Every next line is a human-readable line with the hash of a spent address.

-----

#### Troubleshooting:


