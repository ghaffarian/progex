#!/bin/bash

# abort on first error
set -e

# change directory
cd src/progex/java/parser

# clean existing files
rm *.java *.tokens

# defile vars
export CLASSPATH=".:/usr/local/lib/antlr/antlr-complete.jar:$CLASSPATH"
antlr4='java -Xmx3G  org.antlr.v4.Tool'

# build parser
$antlr4 -visitor -package progex.java.parser Java.g4

# change back directory
cd ../../../..

