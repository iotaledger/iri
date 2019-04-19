FROM python:2.7
WORKDIR /code
RUN apt-get update  \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*
COPY ./ /code
RUN chmod +x requirements.txt
RUN pip install -r requirements.txt
VOLUME ["/code"]
ENV ENABLE_BATCHING false
ENV HOST_IP false
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
WORKDIR /
ENTRYPOINT [ "/docker-entrypoint.sh" ]
