## Python Regression Testing
A testing suite for performance and dynamic testing of IRI

### Aloe testing
This suite uses aloe for performing tests. Once this repository is cloned, make sure you are in the PythonRegression 
directory, and install the necessary packages using:
```
pip install -e .
```

The tests can be run in two ways: Using the `TIAB` (Tangle In A Box) using a specified docker image, or using a set of 
predefined nodes. The `TIAB` method requires the user to have an active `Kubernetes` configuration, as the nodes will be 
spun up in a `kubernetes cluster` before the testing begins. 

### Available Tests
Machine 1 - API Tests: This machine uses 2 nodes and tests each of the api calls, and ensures that the responses are 
the expected values 

Machine 2 - Transaction Tests: This machine uses 2 nodes. Several zero value transactions are sent to the first node, 
as well as a milestone transaction. Then node two is checked to make sure the transactions are all confirmed. After 
these transactions are resolved, the same approach is used to ensure that value transactions are also being confirmed 
correctly.  

Machine 3 - Blowball Tests: This machine uses 6 nodes by default, but can be customized to be performed on as many/few 
as desired. 1000 `getTransactionsToApprove` calls are made across these nodes, and the responses checked to make sure 
that less than 5% of the results are milestone transactions. If the responses are over this threshold, then that means 
blowballs are occurring.  

Machine 4 - Stitching Tests: This machine uses 1 node. The node is loaded with a db containing a large side tangle. A 
stitching transaction is issued, and another transaction referencing that one is issued. After these transactions are 
issued, making `getTransactionsToApprove` calls should not crash the node.  

Machine 5 - Milestone Validation Tests: This machine uses 2 nodes. Both nodes are loaded with the same db. The db 
contains several signed milestone transactions, and several unsigned transactions. One node is set to validate the 
testnet coordinator signature, while the other is not. The one that requires validation should solidify to one point, 
while the other should solidify further. 

Machine 6 - Local Snapshotting Tests: This machine uses 4 nodes. The first node contains the snapshot `meta` and `state`
files, the `spent-addresses-db` and `testnetdb` of a synced node. The second only contains the database, and the third 
only contains the snapshot files. All three of these nodes are tested to ensure that they solidify to the same point, 
and that the proper information is contained in the snapshot files and databases. The fourth node has a larger database 
that contains more milestones than the `local-snapshots-pruning-depth`. This node is checked to make sure that after 
starting, transactions that should be pruned form the database have been pruned correctly.   

### Running Tests on TIAB (Requires present kubernetes configuration)
These tests have been written to interact with a set of nodes with provided configurations, running on a `kubernetes` 
cluster. The `ciglue.sh` script will run all the available machines in the `./tests/features/` directory. The nodes will
be configured based off of the `./tests/features/machine[x]/config.yml` file, and will update the 
`./tests/features/machine[x]/output.yml` file with the newly created cluster host and port information. This script then
proceeds to run each of the feature files in the respective machine directories, and these tests are run on the nodes 
specified in the `output.yml` file. Once the tests have completed, the cluster is torn down. The default logging 
of these tests is verbose, for debugging purposes. To use this automated script, simply use the following command: 
```
bash ciglue.sh [insert iri docker image here]
```

To run on the latest IOTA build, run:
```
bash ciglue.sh iotacafe/iri-dev:latest
```

### Running Tests Locally

To run the tests using a local/custom node, the first step will be to define the nodes that you would like to run the 
tests on. Each `aloe` test will run off of a given feature file, stored in the `./tests/features/machine[x]` directory.
The tests rely on an `output.yml` configuration file, which contains the host and port information of the nodes that 
the test will be run on. This file is generated automatically by the `TIAB` approach, but needs to be generated/filled 
out manually for local tests. The `output.yml` files are found in the machine directory for the test, and should have a 
structure as follows: 

```
nodes:
  nodeA:
    host: localhost
    podip: localhost
    ports:
      api: 14265
      gossip-udp: 14600
      gossip-tcp: 15600
      zmq-feed: 5556
    clusterip_ports:
      api: 14265
      gossip-udp: 14600
      gossip-tcp: 15600
      zmq-feed: 5556

  nodeB:
    host: localhost
    podip: localhost
    ports:
      api: 15265
      gossip-udp: 14605
      gossip-tcp: 15605
      zmq-feed: 6556
    clusterip_ports:
      api: 15265
      gossip-udp: 14605
      gossip-tcp: 15605
      zmq-feed: 6556

``` 

Once the `output.yml` configuration is set, the tests can be run.

From the `python-regression` directory, a test can be run using the following command structure: 
```
aloe [Feature file name]  -w [Location of feature file]  -v [Optional: verbose test name logging] --nologcapture 
[Optional: release logging for individual tests for increased verbosity]
```

i.e. For the api tests:
```
aloe 1_api_tests.feature -w ./tests/features/machine1/ -v --nologcapture 
```





### Extra Features
If you would like to only run part of a machine test, flags can be input into the feature file above the `Scenario: ` 
lines. This flag can be whatever you would like, so long as it is preceded by an `@` symbol. For example: 
```
@getNodeInfo
Scenario: Test getNodeInfo API call 
```

When running the aloe command, you can add the `-a` flag to register that you would like to only run tests containing 
the given attribute. Inversely you can also run all tests that do not contain that flag by using `!`. This is shown 
below: 
```
aloe 1_api_tests.feature -w ./tests/features/machine1 -a getNodeInfo
```
or to not run the flagged tests:
```
aloe 1_api_tests.feature -w ./tests/features/machine1 -a '!getNodeInfo'
```
_Note: To negate running the tests using the flag requires the `!` and flag to be wrapped in parentheses as shown above_

The same flag can be used for several scenarios, and they will all either be included or negated by this flag. 
