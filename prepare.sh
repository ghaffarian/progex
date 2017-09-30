#!/bin/bash

# abort on first error
set -e

# rename JAR file
mv dist/Program_Graph_Extractor__PROGEX_.jar dist/PROGEX.jar

# copy jdk src package
cp lib/src.zip dist/lib/
