FROM python:2.7
WORKDIR /code
RUN apt-get update  \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*
COPY ./ /code
COPY examples/cluster/conf_info/ipinfo.txt /code/iota_api/ipinfo.txt
#ADD https://dist.ipfs.io/go-ipfs/v0.4.10/go-ipfs_v0.4.10_linux-386.tar.gz  .
#RUN tar xvfz go-ipfs_v0.4.10_linux-386.tar.gz
#RUN mv go-ipfs/ipfs  /usr/local/bin/ipfs
#RUN ipfs init
RUN chmod +x requirements.txt
RUN pip install -r requirements.txt
VOLUME ["/code"]
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
WORKDIR /
ENTRYPOINT [ "/docker-entrypoint.sh" ]
