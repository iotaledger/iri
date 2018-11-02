Python Regression Testing
A testing suite for performance and dynamic testing of IRI

Aloe testing
This suite uses aloe for performing tests. Once this repository is cloned, make sure you are in the PythonRegression 
directory, and install the necessary packages using:
```
pip install -e .
```
Once the packages are installed, you can run all the tests with the one line command:
```
aloe
```
Extra Features
If you would like a more verbose readout of the tests being run, you can add the -v flag:
```
aloe -v
````
If you would like to run any particular feature file, you may enter the path to the file following the aloe command 
(this can be done in conjunction with other flags as well):
```
aloe -v ./tests/machine1/features/machine_1_tests.feature
```
Certain features will have tags on them that can be used for inclusion or exclusion in testing. For example:
```
aloe -a utilTests
```
and:
```
aloe -a '!utilTests'
```
Will run all tests that include or don't include the utilTests tag respectively (run the second command if you would 
like to only run the main tests)