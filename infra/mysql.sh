#!/bin/bash

docker kill `docker ps -q`
docker rm $(docker ps -a -q)
docker run -p 3306:3306 --name foundation -e MYSQL_DATABASE=foundation -e MYSQL_ROOT_PASSWORD=password007 -d mysql:latest
