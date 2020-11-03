#!/usr/bin/env bash

CONFIG_FILE="./node.conf"
LOGBACK_FILE="./logback.xml"
ASSEMBLY_JAR="../target/scala-2.13/PublicEvents-assembly-0.0.1.jar"
JAVA_OPTS="-server -XX:+UseNUMA -XX:+UseCondCardMark -XX:-UseBiasedLocking -Xms128M -Xmx128M -Xss1M -XX:+UseParallelGC -Dfile.encoding=UTF-8"
COMMAND="$JAVA_OPTS -Dconfig.file=$CONFIG_FILE -Dlogback.configurationFile=$LOGBACK_FILE -jar $ASSEMBLY_JAR $1 $2 $3"
echo "Starting the MSPEngine using:\n[\njava $COMMAND]\n]\n"
java $COMMAND