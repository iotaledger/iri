FROM python:2.7
WORKDIR /code
RUN apt-get update  \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*
COPY ./ /code
ADD https://dist.ipfs.io/go-ipfs/v0.4.10/go-ipfs_v0.4.10_linux-386.tar.gz  .
RUN tar xvfz go-ipfs_v0.4.10_linux-386.tar.gz
RUN mv go-ipfs/ipfs  /usr/local/bin/ipfs
RUN ipfs init
RUN pip install ipfsapi==0.4.3
RUN pip install flask==1.0.2
RUN pip install pyOpenSSL==19.0.0
RUN pip install pyota==2.0.7
RUN pip install docker==3.7.0
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
WORKDIR /
ENTRYPOINT [ "/docker-entrypoint.sh" ]
