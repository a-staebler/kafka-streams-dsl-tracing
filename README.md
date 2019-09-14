# Prerequisites

```
brew install ossp-uuid redis kafka
brew services start redis
brew services start kafka
```

# Build

```
mvn -Dskip.tests=true package assembly:single
```

# Setup

```
kafka-topics --create --topic streams-plaintext-input --zookeeper localhost:2181 --partitions 1 --replication-factor 1
kafka-topics --create --topic streams-plaintext-output --zookeeper localhost:2181 --partitions 1 --replication-factor 1
kafka-topics --create --topic UppercasedTextLinesTopic --zookeeper localhost:2181 --partitions 1 --replication-factor 1
kafka-topics --create --topic TextLinesTopic --zookeeper localhost:2181 --partitions 1 --replication-factor 1
```

# Run

Start a console consumer for the final stream to see what's going on:

```
kafka-console-consumer --topic streams-wordcount-output --from-beginning --bootstrap-server localhost:9092 --property print.key=true --property value.deserializer=org.apache.kafka.common.serialization.LongDeserializer
```

Start a console producer for the first:

```
kafka-console-producer --broker-list localhost:9092 --topic TextLinesTopic
```

Feed the producer:

```
pr -t -m <(uuid -n 40) <(shuf -n 40 /usr/share/dict/words) | kafka-console-producer --broker-list localhost:9092 --topic TextLinesTopic
```
