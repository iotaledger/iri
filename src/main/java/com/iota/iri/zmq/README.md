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

A client interested in tip selection metrics may subscribe to `MCTN`, short for
"Monte Carlo Transaction Number", a metric that indicates how many transactions
were traversed in a random walk simulation. It may subscribe to `RTS`, for 
"Reason To Stop", to see information about walk terminations.

Other topics currently found in the latest code are 
* `DNS` for information related to neighbors
* `HMR` for the hit to miss ratio
* `ANTN` for added non-tethered neighbors ( testnet only )
* `RNTN` for refused non-tethered neighbors
* `RTL` for transactions randomly removed from the request list
* `LMI` for the latest milestone index
* `LMSI` for the latest solid milestone index 
* `LMHS` for the latest solid milestone hash
* `SN` for newly confirmed transactions ( by solid milestone children measurement )
* `TX` for newly seen transactions

* `<Address>` to watch for an address to be confirmed

All of these topics are subject to change, and more may be added; this is experimental code.
