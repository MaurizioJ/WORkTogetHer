#!/bin/bash

javac -cp .:./* *.java

java -cp .:./* MainServer
