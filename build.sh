#!/usr/bin/env bash
mvn -Dskip.tests=true -Dcheckstyle.skip package &

(
kafka-topics --zookeeper localhost:2181 --delete --topic LowercasedTextLinesTopic &
kafka-topics --zookeeper localhost:2181 --delete --topic UppercasedTextLinesTopic &
kafka-topics --zookeeper localhost:2181 --delete --topic TextLinesTopic &
wait
) && (
kafka-topics --create --topic LowercasedTextLinesTopic --zookeeper localhost:2181 --partitions 1 --replication-factor 1 &
kafka-topics --create --topic UppercasedTextLinesTopic --zookeeper localhost:2181 --partitions 1 --replication-factor 1 &
kafka-topics --create --topic TextLinesTopic --zookeeper localhost:2181 --partitions 1 --replication-factor 1 &
wait
)

wait
