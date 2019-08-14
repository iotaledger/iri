## Quickstart

Run the official iotaledger/iri container, passing the mandatory -p option:

```sh
docker run iotaledger/iri:vX.X.X-RELEASE -p 14265
```

This will get your a running IRI with its API listening on port 14265, no neighbours and an empty database. The IRI Docker container by default expects data at `/iri/data`. Use the `-v` option of the `docker run` command to mount volumes so to have persistent data. You can also pass more command line options to the docker run command and those will be passed to IRI.

If you want to use a iri.ini file with the docker container, supposing it's stored under /path/to/conf/iri.ini on your docker host, then pass `-v /path/to/conf:/iri/conf` and add -c /iri/conf/iri.ini as docker run arguments. So for example the `docker run` command above would become:

```sh
docker run -v /path/to/conf:/iri/conf -v /path/to/data:/iri/data iotaledger/iri:vX.X.X-RELEASE -p 14265 -c /iri/conf/iri.ini
```

Please refer to the IRI documentation for further command line options and iri.ini options.

## DOCKER and IRI in depth

The Dockerfile included in this repo builds a working IRI docker container whilst trying to stay the least opinionated as possible. This allows system administrators the option to deploy and configure IRI based on their own individual circumstances and needs.

When building IRI via the Dockerfile provided, Docker 17.05 minimum is required, due to the use of Docker build stages. During docker build, these are the stages invoked:
- java: installs Oracle Java on top of Ubuntu
- build: installs Maven on top of the java stage and compiles IRI
- final container: copies the IRI jar file using the java stage as base

The built container assumes the WORKDIR inside the container is /iri/data: this means that the database directory will be written inside that directory by default. If a system administrator wants to retain the database across restarts, it is his/her job to mount a docker volume in the right folder.

### Advanced Configuration

The docker container supports the env variables to configure advanced options. These variables can be set but are not required to run IRI.

`JAVA_OPTIONS` these are the java options to pass right after the java command. It must not contain -Xms nor -Xmx. Defaults to a safe value.

`JAVA_MIN_MEMORY` the value of -Xms option. Defaults to 2G

`JAVA_MAX_MEMORY` the value of -Xmx option. Defaults to 4G

`DOCKER_IRI_JAR_PATH` the directory where the IRI jar file is. Defaults to `/iri/target/` as pushed by the Dockerfile. This is useful if custom IRI binaries want to be executed and the default path needs to be overridden.

`DOCKER_IRI_JAR_FILE` the IRI jar file name to execute. Defaults to `iri*.jar`

`DOCKER_IRI_REMOTE_LIMIT_API` defaults to "interruptAttachToTangle, attachToTangle, addNeighbors, removeNeighbors, getNeighbors"

`DOCKER_IRI_MONITORING_API_PORT_ENABLE` defaults to 0. If set to 1, a socat on port 14266 directed to 127.0.0.1:DOCKER_IRI_MONITORING_API_PORT_DESTINATION  will be open in order to allow all API calls regardless of the DOCKER_IRI_REMOTE_LIMIT_API setting. This is useful to give access to restricted API calls to local tools and still denying access to restricted API calls to the internet. It is highly recommended to use this option together with docker networks (docker run --net).

`DOCKER_IRI_REMOTE` defaults to "true". This sets the boolean value for the "--remote" command line option. Setting to "false" ensures that the API port binds only to the localhost interface. The setting of "true" lets the API listen on all the interfaces (0.0.0.0). Set this value to "false" if you plan on running the container on the host network and want the API to bind to the localhost interface only. Set this value to false if you want to bind the --api-host (API_HOST) to a specific interface.

`DOCKER_JAVA_NET_PREFER_IPV4_STACK` defaults to "true". This sets the boolean value for the -Djava.net.preferIPv4Stack option. To be able to use IPv6 make sure to set this to false.

### Entrypoint
The container entry point is a shell script that performs few additional steps before launching IRI:
- verifies if `DOCKER_IRI_MONITORING_API_PORT_ENABLE` is set to 1
- launches IRI with all parameters passed as desired

It is important to note that other than --remote and --remote-limit-api "$DOCKER_IRI_REMOTE_LIMIT_API", neither the entrypoint nor the Dockerfile are aware of any IRI configuration option. This is to not tie the Dockerfile and its container to a specific set of IRI options. Instead, this contain still allows the use of an INI file or command line options. Please refer to the IRI documentation to learn what are the allowed options at command line and via the INI file.

**At the time of writing, IRI requires -p to be passed either via INI or via command line. The entrypoint of this docker container does not do that for you.**

### Systemd Unit
Here is a systemd unit example you can use with this Docker container. This is just an example and customisation is possible and recommended. In this example the paths `/mnt/iri/conf` and `/mnt/iri/data` are used on the docker host to serve respectively the neighbors file and the data directory. No INI files are used in this example, instead options are passed via command line options, such as `--testnet true` and `--zmq-enabled false`.

```
[Unit]
Description=IRI
After=docker.service
Requires=docker.service

[Service]
TimeoutStartSec=0
Restart=always
ExecStartPre=-/usr/bin/docker rm %n
ExecStart=/usr/bin/docker run \
  --name %n \
  --hostname iri \
  --user=1000 \
  --net=host \
  --cap-drop=ALL
  -v /mnt/iri/conf:/iri/conf \
  -v /mnt/iri/data:/iri/data \
  -e DOCKER_IRI_REMOTE=true \
  iotaledger/iri:vX.X.X-RELEASE \
  --port 14265 \
  --zmq-enabled false \
  --testnet false

ExecStop=/usr/bin/docker stop %n
ExecReload=/usr/bin/docker restart %n

[Install]
WantedBy=multi-user.target
```

## Security Considerations

It is highly recommended to run IRI with an unprivileged user and not as user `root`.

An unprivileged user can be created on the host and the UID passed to the docker command (e.g. `--user 1001`). Directories that are mounted to the container from the host should be owned by this user.

In addition the `--cap-drop=ALL` passed to docker restricts process capabilities and adheres to the principle of least privilege. See https://docs.docker.com/engine/reference/run/#runtime-privilege-and-linux-capabilities for more information.
