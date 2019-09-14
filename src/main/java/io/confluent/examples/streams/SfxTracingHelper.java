package io.confluent.examples.streams;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.zipkin.ZipkinV2Reporter;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import okhttp3.Request;
import redis.clients.jedis.Jedis;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class SfxTracingHelper {
    static Tracer createTracer(final String serviceName) {
        final String ingestUrl = System.getProperty("ingestUrl","http://192.168.64.8:8080");
        final String accessToken = System.getProperty("accessToken");

        final OkHttpSender.Builder senderBuilder = OkHttpSender.newBuilder()
                .compressionEnabled(true)
                .endpoint(ingestUrl + "/v1/trace");

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

    static void reportEnd(final String op, final String value) {
        final String mapKey = value.split(" ")[0];
        System.out.println("mapKey: " + mapKey);
        final Tracer tracer = GlobalTracer.get();
        final Jedis cache = new Jedis("localhost");
        final long tstamp = System.currentTimeMillis() * 1000;
        final Span span = extractTraceCtx(tracer, op, cache, mapKey, tstamp);
        Tags.SAMPLING_PRIORITY.set(span, 1);
        try {
            Thread.sleep(2000);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        span.finish(System.currentTimeMillis()* 1000);
        tracer.inject(span.context(), Format.Builtin.TEXT_MAP, new JedisTextMapAdapter(new Jedis("localhost"), mapKey));
    }

    protected static Span extractTraceCtx(final Tracer tracer, final String op, final Jedis jedis, final String mapKey, final long tstamp) {
        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(op);
        try {
            final TextMap carrier = new JedisTextMapAdapter(jedis, mapKey);
            final SpanContext spanContext = tracer.extract(Format.Builtin.TEXT_MAP, carrier);
            if (spanContext != null) {
                System.out.println("parent: " + spanContext.toString());
                spanBuilder.asChildOf(spanContext);
            } else {
                System.out.println("no parent!");
            }
        } catch (final Exception e) {
            spanBuilder.withTag("Error", "extract from request fail, error msg:" + e.getMessage());
        }
        return spanBuilder.withStartTimestamp(tstamp).start();
    }
}


