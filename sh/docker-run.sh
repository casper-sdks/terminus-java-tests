#!/usr/bin/env bash
docker run --rm -it --name cspr-nctl -d -p 25101:25101 -p 11101:11101 -p 14101:14101 -p 18101:18101 casper-nctl:v1413