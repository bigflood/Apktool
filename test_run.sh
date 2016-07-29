#!/bin/bash

JARFILE=brut.apktool/apktool-cli/build/libs/apktool-cli.jar
ls -al $JARFILE
java -jar $JARFILE d temp/test.apk -o temp/output/ -f
