## IRI MessageQ

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

Topics currently found in the latest code are
* `mctn` transactions traversed during random walk
* `lmi` for the latest milestone index
* `lmsi` for the latest solid milestone index
* `lmhs` for the latest solid milestone hash
* `sn` for newly confirmed transactions ( by solid milestone children measurement )
* `sn_trytes` for raw trytes, transaction hash, and snapshot index of confirmed transactions.
* `tx` for newly seen transactions
* `trytes` trytes of newly seen transactions
* `<Address>` to watch activity on an address

All topic must be lowercase (to not clash with `<Address>` containing the topic title - like `TXCR9...` & `TX`)
