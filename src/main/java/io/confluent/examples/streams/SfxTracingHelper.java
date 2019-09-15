package io.confluent.examples.streams;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.zipkin.ZipkinV2Reporter;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import okhttp3.Request;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SfxTracingHelper {
    static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass());

    static Tracer createTracer(final String serviceName) {
        final String ingestUrl = System.getProperty("ingestUrl","http://192.168.64.8:8080/v1/trace");
        final String accessToken = System.getProperty("accessToken");

        System.out.println("ingestUrl: " + ingestUrl);
        System.out.println("accessToken: " + accessToken);

        final OkHttpSender.Builder senderBuilder = OkHttpSender.newBuilder()
                .compressionEnabled(true)
                .endpoint(ingestUrl);

        senderBuilder.clientBuilder().addInterceptor(chain -> {
            final Request request = chain.request().newBuilder()
                    .addHeader("X-SF-Token", accessToken)
                    .build();

            return chain.proceed(request);
        });

        final OkHttpSender sender = senderBuilder.build();

        final Tracer tracer = new Configuration(serviceName)
                .getTracerBuilder()
                .withSampler(new ConstSampler(true))
                .withReporter(new ZipkinV2Reporter(AsyncReporter.create(sender)))
                .build();

        GlobalTracer.register(tracer);

        return tracer;
    }

    static void reportConsume(final Tracer tracer, final String topic, final String value, final String op) {
        LOGGER.debug(String.format("reportConsume op: %s, topic: %s", op, topic));
        final String mapKey = value.trim().split("\\s+")[0];
        LOGGER.debug(String.format("mapKey: %s", mapKey));
        final Jedis cache = new Jedis("localhost");
        final Instant start = Instant.now();
        final Span span = createOrContinueSpan(tracer, op, cache, mapKey, start);
        span.setTag("cId", mapKey);
        Tags.SAMPLING_PRIORITY.set(span, 1);
        span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER);
        span.setTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), topic);
        span.finish(Instant.EPOCH.until(Instant.now(), ChronoUnit.MICROS));
    }

    static void reportProduce(final Tracer tracer, final String topic, final String value, final String op) {
        LOGGER.debug(String.format("reportConsume op: %s, topic: %s", op, topic));
        final String mapKey = value.trim().split("\\s+")[0];
        LOGGER.debug(String.format("mapKey: %s", mapKey));
        final Jedis cache = new Jedis("localhost");
        final Instant start = Instant.now();
        final Span span = createOrContinueSpan(tracer, op, cache, mapKey, start);
        Tags.SAMPLING_PRIORITY.set(span, 1);
        Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_PRODUCER);
        Tags.MESSAGE_BUS_DESTINATION.set(span, topic);
        span.finish(Instant.EPOCH.until(Instant.now(), ChronoUnit.MICROS));
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new JedisTextMapAdapter(cache, mapKey));
    }

    protected static SpanContext extractCtx(final Tracer tracer, final Jedis cache, final String mapKey) {
        final TextMap carrier = new JedisTextMapAdapter(cache, mapKey);
        return  tracer.extract(Format.Builtin.TEXT_MAP, carrier);
    }

    protected static Span createOrContinueSpan(final Tracer tracer, final String op, final Jedis cache, final String mapKey, final Instant start) {
        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(op);
        try {
            final SpanContext spanContext = extractCtx(tracer, cache, mapKey);
            if (spanContext != null) {
                LOGGER.debug(String.format("Parent: %s for mapKey: %s", spanContext, mapKey));
                spanBuilder
                        .addReference(References.FOLLOWS_FROM, spanContext)
                        // .asChildOf(spanContext)
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CONSUMER);
            } else {
                LOGGER.debug(String.format("No parent for: %s", mapKey));
                spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_PRODUCER);
            }
        } catch (final Exception e) {
            spanBuilder.withTag("Error", "extract from request fail, error msg:" + e.getMessage());
        }
        return spanBuilder
                .withStartTimestamp(Instant.EPOCH.until(start, ChronoUnit.MICROS))
                .start();
    }
}

