## IRI MessageQ

This is a **work in progress**. Things will change.

MessageQ is a small wrapper for ZeroMQ inside IRI to allow streaming 
of topics from within a running full node. The goal of this is to allow
for targeted event streams from subscribing clients to the node process.

A client may want to be notified of a change in status of a transaction,
or may want to see incoming transactions, or any number of data points.
These can be filtered by topic, and the aim is for machine-readability 
over human readability.

For instance, a light wallet connected to a remote node may want to know
when a transaction is confirmed. It would, perhaps, after querying the API,
subscribe to a topic which publishes on the update of a state.

#### Topics

A client interested in tip selection metrics may subscribe to `mctn`, short for
"monte carlo transaction number", a metric that indicates how many transactions
were traversed in a random walk simulation. It may subscribe to `rts`, for
"reason to stop", to see information about walk terminations.

Other topics currently found in the latest code are 
* `dns` for information related to neighbors
* `hmr` for the hit to miss ratio
* `antn` for added non-tethered neighbors ( testnet only )
* `rntn` for refused non-tethered neighbors
* `rtl` for transactions randomly removed from the request list
* `lmi` for the latest milestone index
* `lmsi` for the latest solid milestone index
* `lmhs` for the latest solid milestone hash
* `sn` for newly confirmed transactions ( by solid milestone children measurement )
* `tx` for newly seen transactions

* `<Address>` to watch for an address to be confirmed

All topic must be lowercase (to not clash with `<Address>` containing the topic title - like `TXCR9...` & `TX`)
All of these topics are subject to change, and more may be added; this is experimental code.
