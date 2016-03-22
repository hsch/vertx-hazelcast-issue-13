# vertx-hazelcast-issue-13
Demonstrates how vert.x fails to deliver messages over its event bus when hazelcast nodes leave the cluster.

## Environment

- Windows 7
- Java HotSpot(TM) 64-Bit Server VM (build 25.60-b23, mixed mode)
- Vert.x 3.2.1

## Setup

The application can be run in 4 different modes, publisher, consumer-1, consumer-2, and consumer-3. The consumers essentially all behave the same, please excuse the redundant code. :)

First start the publisher:

    gradle run -Ppublisher
    
After some initialization it will start sending PING messages over the event bus, happily accepting the fact that nobody is listening yet:

    2016-03-22 19:03:27 [INFO ] vert.x-eventloop-thread-0: Sending ping...
    2016-03-22 19:03:27 [INFO ] vert.x-eventloop-thread-0: Waiting for consumers...
    ...

Now, in a separate shell, start the first consumer:

    gradle run -Pconsumer-1
    
You will see that the publisher starts receiving PONGs from that consumer. **Note that from now on the publisher will no longer accept it to receive no responses to its messages. It will log errors then.** Anyway, everything is fine for now:

    Sending ping...
    Received response; consumerName='consumer-1'
    ...

Now start the next consumers, number 2 and 3. Just to make things as reproducible as possible, please start them sequentially, i.e. wait for number 2 to successfully connect to the cluster and send replies before you start number 3.

    gradle run -Pconsumer-2
    gradle run -Pconsumer-3

The publisher should now receive responses from all 3 consumers in a round-robin fashion:

    Sending ping...
    Received response; consumerName='consumer-3'
    Sending ping...
    Received response; consumerName='consumer-1'
    Sending ping...
    Received response; consumerName='consumer-2'
    Sending ping...
    Received response; consumerName='consumer-3'
    Sending ping...
    Received response; consumerName='consumer-1'
    ...

So far, so good.

## The Issue

Now kill consumer numbers 2 and 3. Really kill the processes and try to make it as simultaneous as possible. From now on, no matter how long you give the cluster to recover (I've waited up to 15 minutes), it will fail to deliver the publisher's messages:

    Sending ping...
    Received response; consumerName='consumer-1'
    Sending ping...
    This should not have happened!
    Sending ping...
    This should not have happened!
    Sending ping...
    Received response; consumerName='consumer-1'
    Sending ping...
    This should not have happened!
    
It seems to be stuck in round-robin delivery to consumers 1 to 3, even though two of them are gone.

This looks like a bug to me. My expectation would have been that all messages would be routed to consumer 1, either immediately or at least after some time of cluster recovery.

Note that it doesn't even help to bring consumers 2 and 3 back into the game. You will be stuck with 2 failed attempts:

    Sending ping...
    Received response; consumerName='consumer-2'
    Sending ping...
    Received response; consumerName='consumer-3'
    Sending ping...
    Received response; consumerName='consumer-1'
    Sending ping...
    This should not have happened!
    Sending ping...
    This should not have happened!
    Sending ping...
    Received response; consumerName='consumer-2'
    ...
    
The cluster map looks okay and correctly recovered:

INFO: [10.55.1.130]:5701 [dev] [3.5.2]

    Members [4] {
      Member [10.55.1.130]:5701 this
      Member [10.55.1.130]:5702
      Member [10.55.1.130]:5703
      Member [10.55.1.130]:5704
    }
