/*
 * Copyright Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.examples.streams;

import io.confluent.common.utils.TestUtils;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;

public class LowerCaseWord {
    static final String inputTopic = "UppercasedTextLinesTopic";
    static final String outputTopic = "LowercasedTextLinesTopic";
    static Tracer tracer = SfxTracingHelper.createTracer("word-count");

    public static void main(final String[] args) {
        final String bootstrapServers = args.length > 0 ? args[0] : "localhost:9092";
        final Properties streamsConfiguration = getStreamsConfiguration(bootstrapServers);
        final StreamsBuilder builder = new StreamsBuilder();
        createWordCountStream(builder);
        final KafkaStreams streams = new KafkaStreams(builder.build(), streamsConfiguration);

        streams.cleanUp();
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }

    static Properties getStreamsConfiguration(final String bootstrapServers) {
        final Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-lambda-example");
        streamsConfiguration.put(StreamsConfig.CLIENT_ID_CONFIG, "wordcount-lambda-example-client");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 10 * 1000);
        streamsConfiguration.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        streamsConfiguration.put(StreamsConfig.STATE_DIR_CONFIG, TestUtils.tempDirectory().getAbsolutePath());
        return streamsConfiguration;
    }

    static void createWordCountStream(final StreamsBuilder builder) {
        final Serde<String> stringSerde = Serdes.String();
        final Serde<byte[]> byteArraySerde = Serdes.ByteArray();
        final KStream<byte[], String> textLines = builder.stream(inputTopic, Consumed.with(byteArraySerde, stringSerde));
        final KStream<byte[], String> lowerCasedTextLines = textLines
                .peek((bytes, value) -> SfxTracingHelper.reportConsume(tracer, inputTopic, value, "consume"))
                .mapValues(v -> {
                    try (Scope scope = tracer.buildSpan("to-lower").startActive(true)) {
                        try {
                            Thread.sleep((int)(1000 + Math.random() * 2000));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        return v.toLowerCase();
                    }
                })
                /*
                .flatMapValues(value -> {
                    try (Scope scope = GlobalTracer.get().buildSpan("split-words").startActive(true)) {
                        return Arrays.asList(pattern.split(value.toLowerCase()));
                    }
                })
                */
                .peek((bytes, value) -> SfxTracingHelper.reportProduce(tracer, outputTopic, value, "produce"));

        lowerCasedTextLines.to(outputTopic);
    }

}
