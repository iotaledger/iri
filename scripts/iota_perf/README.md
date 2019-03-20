## To generate data 

Firstly we need to generate data to simulate transactions, if you want to configure the data content, for example number of users, number of transactions etc.
You can change the code by yourself.

``` bash
python data_generate.py > data
```

## Deployment 
We need to configure two IRI machines, two APP servers, one Nginx server to conduct the performance test.

### Deploy locally

```bash
cd scripts/examples/
./conflux_dag_two_nodes.sh
./start_cli_two_nodes.sh
```

### Deploy multiple machines

### Deploy Nginx for distributing requests

Build the nginx docker, you need to configure the nginx.conf first. 

```bash
ifconfig |grep inet # (get the ip address), the change the nginx.conf.
docker build -t <name>:<tag> .
```

Start the docker
```bash
docker run -d -p 8080:8080 --name <name> <name>:<tag>
```

## Performance test by Jmeter  
Run jmeter to collect the performance stats(you need to have the jmeter installation)
```bash
jmeter -n -t PerformanceTestDAG2TM_TPS.jmx
```

make sure the result is correct
```bash
```
