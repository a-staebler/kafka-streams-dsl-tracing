# What is this?

An OpenTracing example for Kafka Streams DSL.

# Prerequisites

```
brew install coreutils ossp-uuid redis kafka
brew services start redis
brew services start kafka
```

SignalFx Smart Gateway and access token required.

# Build

```
mvn -Dskip.tests=true -Dcheckstyle.skip package 
```

# Setup

```
kafka-topics --create --topic streams-plaintext-input --zookeeper localhost:2181 --partitions 1 --replication-factor 1
kafka-topics --create --topic streams-plaintext-output --zookeeper localhost:2181 --partitions 1 --replication-factor 1
kafka-topics --create --topic UppercasedTextLinesTopic --zookeeper localhost:2181 --partitions 1 --replication-factor 1
kafka-topics --create --topic TextLinesTopic --zookeeper localhost:2181 --partitions 1 --replication-factor 1
```

To delete the topics:

```
kafka-topics --zookeeper localhost:2181 --delete --topic streams-plaintext-input
kafka-topics --zookeeper localhost:2181 --delete --topic streams-plaintext-output
kafka-topics --zookeeper localhost:2181 --delete --topic UppercasedTextLinesTopic
kafka-topics --zookeeper localhost:2181 --delete --topic TextLinesTopic
```

# Run

Start a console consumer for the final stream to see what's going on:

```
kafka-console-consumer --topic streams-wordcount-output --from-beginning --bootstrap-server localhost:9092 --property print.key=true --property value.deserializer=org.apache.kafka.common.serialization.LongDeserializer
```

Start streams: 

```
java -DingestUrl=YOUR_INGEST -DaccessToken=YOUR_TOKEN -cp target/kafka-streams-examples-5.3.0-standalone.jar io.confluent.examples.streams.WordCountLambdaExample
java -DingestUrl=YOUR_INGEST -DaccessToken=YOUR_TOKEN -cp target/kafka-streams-examples-5.3.0-standalone.jar io.confluent.examples.streams.UpperCaseWordExample
```

Feed the producer:

```
pr -t -m <(uuid -n 40) <(shuf -n 40 /usr/share/dict/words) | kafka-console-producer --broker-list localhost:9092 --topic TextLinesTopic
```
