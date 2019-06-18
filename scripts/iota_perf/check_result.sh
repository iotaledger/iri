#!/usr/bin/env bash
sh check | paste -s -d+ - | bc
