## Python Regression Testing
A testing suite for performance and dynamic testing of IRI. These tests currently only work in the `python2.7` 
environment.

### Aloe testing
This suite uses aloe for performing tests. Once this repository is cloned, make sure you are in the PythonRegression 
directory. Here you can create a virtual environment to run the tests on and install the necessary packages using:

_Optional:_
```
python -m venv venv
source venv/bin/activate
```
_**Note:** Don't forget to `deactivate` once you've finished your tests_


```
pip install -e .
```

### Available Tests
Machine 1 - Local Snapshotting Tests: This machine uses 4 nodes. The first node contains the snapshot `meta` and `state`
files, the `spent-addresses-db` and `testnetdb` of a synced node. The second only contains the database, and the third 
only contains the snapshot files. All three of these nodes are tested to ensure that they solidify to the same point, 
and that the proper information is contained in the snapshot files and databases. The fourth node has a larger database 
that contains more milestones than the `local-snapshots-pruning-depth`. This node is checked to make sure that after 
starting, transactions that should be pruned from the database have been pruned correctly. This machine also includes 
tests for spent addresses including a test for exporting and merging spent addresses using IXI 
modules.   

Machine 2 - Blowball Tests: This machine uses 6 nodes by default, but can be customized to be performed on as many/few 
as desired. 1000 `getTransactionsToApprove` calls are made across these nodes, and the responses checked to make sure 
that less than 5% of the results are milestone transactions. If the responses are over this threshold, then that means 
blowballs are occurring.  


Machine 3 - Transaction Tests: This machine uses 2 nodes. Several zero value transactions are sent to the first node, 
as well as a milestone transaction. Then node two is checked to make sure the transactions are all confirmed. After 
these transactions are resolved, the same approach is used to ensure that value transactions are also being confirmed 
correctly.  

Machine 4 - API Tests: This machine uses 2 nodes and tests each of the api calls, and ensures that the responses are 
the expected values 

Machine 5 - Stitching Tests: This machine uses 1 node. The node is loaded with a db containing a large side tangle. A 
stitching transaction is issued, and another transaction referencing that one is issued. After these transactions are 
issued, making `getTransactionsToApprove` calls should not crash the node.  

Machine 6 - Milestone Validation Tests: This machine uses 2 nodes. Both nodes are loaded with the same db. The db 
contains several signed milestone transactions, and several unsigned transactions. One node is set to validate the 
testnet coordinator signature, while the other is not. The one that requires validation should solidify to one point, 
while the other should solidify further. 

__*Note:*__ _The db's used for these tests have been documented below for reference_

### Running Tests Locally

To run the tests using a local/custom node, the first step will be to define the nodes that you would like to run the 
tests on. Each `aloe` test will run off of a given feature file, stored in the `./tests/features/machine[x]` directory.
The tests rely on an `output.yml` configuration file, which contains the host and port information of the nodes that 
the test will be run on. The nodes you intend to test will need to be configured manually using the arguments present in
the `config.yml` file. This file needs to be generated/filled out manually for local tests. The `config.yml` and 
`output.yml` files are found in the machine directory for the test. 

Setting up the node you intend to test will require you to enter the arguments given for that node in the 
`config.yml` file. For example, for `machine1` the `config.yml` file contains the following arguments: 
```
default_args: &args
  ['--testnet-coordinator',
   'EFPNKGPCBXXXLIBYFGIGYBYTFFPIOQVNNVVWTTIYZO9NFREQGVGDQQHUUQ9CLWAEMXVDFSSMOTGAHVIBH',
   '--mwm',
   '1',
   '--milestone-start',
   '0',
   '--testnet-no-coo-validation',
   'true',
   '--testnet',
   'true',
   '--snapshot',
   './snapshot.txt',
   '--local-snapshots-pruning-enabled',
   'true',
   '--local-snapshots-pruning-delay',
   '10000'
  ]
```

So for example, when starting a node for these `machine1` tests, you would download and unpackage the db referenced 
in the provided db url in the `config.yml`. Once ready, your iri start command would look like: 
```
java -jar iri-CURRENT-VERSION.jar -p 14265 -t 15600 
--testnet-coordinator EFPNKGPCBXXXLIBYFGIGYBYTFFPIOQVNNVVWTTIYZO9NFREQGVGDQQHUUQ9CLWAEMXVDFSSMOTGAHVIBH --testnet true 
--mwm 1 --milestone-start 0 --testnet-no-coo-validation true --snapshot ./snapshot.txt --local-snapshots-pruning-enabled 
true --local-snapshots-pruning-delay 10000
```

Once the node is running, it's time to run the tests. The `output.yml` needs to be present in the same machine folder as
the `config.yml` file and should contain the host and port details for your nodes in a structure as follows: 

```
nodes:
  nodeA:
    host: localhost
    podip: localhost
    ports:
      api: 14265
      gossip-tcp: 15600
      zmq-feed: 5556
    clusterip_ports:
      api: 14265
      gossip-tcp: 15600
      zmq-feed: 5556

  nodeB:
    host: localhost
    podip: localhost
    ports:
      api: 15265
      gossip-tcp: 15605
      zmq-feed: 6556
    clusterip_ports:
      api: 15265
      gossip-tcp: 15605
      zmq-feed: 6556

``` 

Once the `output.yml` configuration is set, the tests can be run.

_**Note:** Each `machine[X]` directory will need its own unique `output.yml` to run that machines tests._


The tests will be run from the `iri/python-regression` directory. The file structure for the test you wish to write 
should look as follows: 

```
iri
-/python-regression [Where the test is run]
--/tests
---/features
----/machine1 [Same structure for other machines]
-----/4_api_tests.feature
-----/config.yml
-----/output.yml
```

From the `iri/python-regression` directory, a test can be run using the following command structure:
 
`aloe ` _`Feature file name`_  `-w` _`Location of feature file`_  `[-v]` `[--nologcapture]` 
```
-v: Verbose test name logging 
--nologcapture: Release logging for individual tests for increased verbosity
```
i.e. For the api tests:
```
aloe 4_api_tests.feature -w ./tests/features/machine1/ -v --nologcapture 
```



### Extra Features
If you would like to only run part of a feature test, flags can be input into the feature file above the `Scenario: ` 
lines. This flag can be whatever you would like, so long as it is preceded by an `@` symbol. For example: 
```
@getNodeInfo
Scenario: Test getNodeInfo API call 
```

When running the aloe command, you can add the `-a` flag to register that you would like to only run tests containing 
the given attribute. Inversely you can also run all tests that do not contain that flag by using `!`. This is shown 
below: 
```
aloe 4_api_tests.feature -w ./tests/features/machine1 -a getNodeInfo
```
or to not run the flagged tests:
```
aloe 4_api_tests.feature -w ./tests/features/machine1 -a '!getNodeInfo'
```
_Note: To negate running the tests using the flag requires the `!` and flag to be wrapped in parentheses as shown above_

The same flag can be used for several scenarios, and they will all either be included or negated by this flag. 


### _*DB Descriptions:*_ 
##### _Machine 1_
https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/LS_Test_Db_With_LS_Db.tar.gz - Full LocalSnapshot test db 
synced to milestone 10321 and a local snapshot at 10220. Contains mostly 0 value transactions with a few spends early on 
to generate the localsnapshot-db as well as to provide transactions for pruning reference 

https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/LS_Test_DB_and_Snapshot.tar - Full LocalSnapshot test db 
synced to milestone 10321 without any localsnapshots-db. 

https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/LS_Test_LS_Db.tar.gz - No LocalSnapshot test db provided, 
instead the localsnapshot-db from index 10220 is provided instead. 

https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/PruningTestDB.tar - A full db synced to milestone 15000 
containing a mix of value and 0 value transactions that will be pruned once the node takes its snapshot. This is used 
to ensure that any transaction below the threshold is pruned correctly. 

https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/SpentAddressesTestDB.tar - A full db synced to milestone 
10100 with a mix of value and 0 value transactions that will be pruned once the node takes its snapshot. This is used 
to ensure that spent addresses are persisted in the local snapshot data after pruning occurs. 


##### _Machine 2_
https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/Blowball_Tests_db.tar - A full db synced to milestone 27. 
There are several tips surrounding the last milestone, and the test is used to ensure that new transactions aren't 
attaching en masse to the last milestone present. 


##### _Machine 3_
https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/Transactions_Tests_db.tar - A small db synced to milestone 
50 with a snapshot file containing a list of addresses preset for value spending. This DB is used to test value and non 
value transactions and their inclusion in the next milestone.


##### _Machine 4_
https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/testnet_files.tgz - A full db synced to milestone 8412 used 
for testing basic api commands.


##### _Machine 5_
https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/Stitching_tests_db.tar - A full db synced to milestone 37 
with a large subtangle that is building beside the main tangle. This db is used to test the success or failure of 
stitching this subtangle back into the original.


##### _Machine 6_
https://s3.eu-central-1.amazonaws.com/iotaledger-dbfiles/dev/Validation_tests_db.tar - A full db containing 2 separate 
synchronisation points. The first point is hit at milestone 37, where the last milestone issued with valid signatures is 
present. The db contains several more milestones that have been attached without a valid signature. The test will sync 
to 37 if a valid signature is required and 45 if not. 

