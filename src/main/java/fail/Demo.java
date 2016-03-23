package fail;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;

public class Demo {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(Demo.class);

  private static final String ADDRESS = "my-address";

  public static void main(final String[] argv) throws Exception {

    if (argv.length == 0) {
      LOG.error("Missing argument, should be one of: publisher, consumer-1, consumer-2, consumer-3");
      return;
    }

    final VertxOptions options = new VertxOptions();

    final String mode = argv[0];
    LOG.info("Starting; mode='{}'", mode);

    if (mode.equals("publisher"))
      publisher(options);

    if (mode.equals("consumer-1"))
      consumer(options, "consumer-1");

    if (mode.equals("consumer-2"))
      consumer(options, "consumer-2");

    if (mode.equals("consumer-3"))
      consumer(options, "consumer-3");

  }

  /**
   * Run the one publisher process.
   */
  public static void publisher(final VertxOptions vertxOptions) throws Exception {

    Vertx.clusteredVertx(vertxOptions, (cluster) -> {

      final Vertx vertx = cluster.result();
      final EventBus eventBus = vertx.eventBus();

      /*
       * Collect the consumers that have responded to us. For this specific
       * demo, it's only relevant to track that we have at least once seen one
       * consumer.
       */
      final Set<String> observedConsumers = new HashSet<>();

      /*
       * Every second, send one ping.
       */
      vertx.setPeriodic(1000, (timeout) -> {

        LOG.info("Sending ping...");
        eventBus.<String> send(ADDRESS, null, new DeliveryOptions().setSendTimeout(500), (result) -> {

          /*
           * When we receive a reply, track the response (which should be the
           * name of the responding consumer).
           */
          if (result.succeeded()) {
            final String consumerName = result.result().body();
            observedConsumers.add(consumerName);
            LOG.info("Received response; consumerName='{}'", consumerName);
          }

          /*
           * When we receive no reply in time, ...
           */
          if (result.failed()) {

            /*
             * ...and we didn't observe any consumer yet, then that's fine.
             * We're still waiting for anyone to show up.
             */
            if (observedConsumers.size() == 0) {
              LOG.info("Waiting for consumers...");
            }

            /*
             * ...if we had already observed consumers, then this is a problem.
             * We don't expect _all_ consumers to go away, and as long as one
             * consumer is running, we should receive a response!
             */
            else {
              LOG.error("This should not have happened!");
            }

          }
        });
      });
    });
  }

  /**
   * Run a consumer process with the given name.
   */
  public static void consumer(final VertxOptions vertxOptions, final String consumerName) throws Exception {

    Vertx.clusteredVertx(vertxOptions, (cluster) -> {

      final Vertx vertx = cluster.result();
      final EventBus eventBus = vertx.eventBus();

      /*
       * Simply wait for incoming messages.
       */
      LOG.info("Waiting for messages...");
      eventBus.<Integer> consumer(ADDRESS, (message) -> {

        /*
         * When we receive something, respond with our consumerName.
         */
        LOG.info("Received ping, sending response; consumerName='{}'", consumerName);
        message.reply(consumerName);

      });
    });
  }

}
