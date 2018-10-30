# Python Regression Testing

A testing suite for performance and dynamic testing of IRI

## Aloe testing

This suite uses aloe for performing tests. Once this repository is cloned, make sure you are in the PythonRegression directory, and install the necessary packages using: 
```
pip install -e . --user
```

Once the packages are installed, you can run the tests locally with the command: 
```
bash RunLocalTests.sh
```

This will walk you through a configuration of the test you would like to run. In order to run a test locally you will need a minimum of one node that you will be using as an endpoint (depending on the test, you may need at least two different nodes to test properly). 

**Note: The test will only pass if you use the appropriate db on your node, or if you adjust the appropriate input values for the tests. These values can be found referenced in each `*.feature` file.

### Extra Features
If you would prefer to run the tests manually, then you will need to do three things:
1. Update/Create the `output.yml` file in the appropriate `tests/features/machine[x]` directory in the following format
```
nodes:
  [node name]:
    host: [host address, eg localhost]
    podip: [same as the host]
    clusterip: [same as the host]
    clusterip_ports: 
      api: [api receiver port]
      gossip-udp: [udp receiver port]
    ports:
      api: [api receiver port]
      gossip-udp: [udp receiver port]
  
  [node2 name]:
    host: ...
    ...
    ...
    
```

2. Ensure that your static values and input node names etc. in the appropriate machine `*.feature` file are set to reflect values that you know are present in your configuration. (Or download the refenced DB for each machines test. These can be found in the `DB_References.txt` file)

3. Run the following command with the appropriate names:
```
aloe [name of the *.feature file] --where [/path/to/machine/dir]
```
**Note: Additionally you can add the `--nologcapture` and `--verbose` tags to the command to increase the verbosity of the logging and see the inner workings of the test as they happen. 

Example: 
```
aloe 1_api_tests.feature --where tests/features/machine1 --nologcapture --verbose 
```

