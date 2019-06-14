FROM mysql:5.7


ENV MYSQL_ALLOW_EMPTY_PASSWORD yes

COPY setup.sh /mysql/setup.sh
COPY oauth-resource/src/main/resources/db/trias_cli-init.sql /mysql/trias_cli-init.sql
COPY oauth-resource/src/main/resources/db/trias_cli_user_ddl.sql /mysql/trias_cli_user_ddl.sql
COPY oauth-server/src/main/resources/db/trias_server-init.sql /mysql/trias_server-init.sql
COPY oauth-server/src/main/resources/db/trias-server_ddl.sql /mysql/trias-server_ddl.sql
COPY create_write.sql /mysql/create_write.sql

CMD ["sh", "/mysql/setup.sh"]
