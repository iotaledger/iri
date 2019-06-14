#!/bin/bash
set -e
echo '1.start mysql....'
service mysql start
sleep 3
echo 'create database and write data'
mysql < /mysql/create_write.sql
echo '2.create and write done....'
sleep 3

tail -f /dev/null
