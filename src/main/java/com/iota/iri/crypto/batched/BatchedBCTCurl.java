package com.iota.iri.crypto.batched;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * BatchedBCTCurl takes care of batching up hashing requests and starts processing them through a BCTCurl once either
 * all available slots are filled or no request is submitted within a given timeout.
 */
public class BatchedBCTCurl implements BatchedHasher {

    private static final Logger log = LoggerFactory.getLogger(BatchedBCTCurl.class);

    // we have max 64 bits/slots available for requests to fill up
    private final static int MAX_BATCH_SIZE = 64;

    private ArrayBlockingQueue<HashRequest> reqQueue;
    private int hashLength;
    private int numberOfRounds;
    private int batchTimeoutMilliSec;

    /**
     * Creates a new {@link BatchedBCTCurl} with the given hash length, number of rounds and default batch timeout.
     *
     * @param hashLength     the desired hash length
     * @param numberOfRounds the number of hashing rounds to apply
     */
    public BatchedBCTCurl(int hashLength, int numberOfRounds) {
        this.hashLength = hashLength;
        this.numberOfRounds = numberOfRounds;
        this.batchTimeoutMilliSec = BatchedHasher.DEFAULT_BATCH_TIMEOUT_MILLISECONDS;
        this.reqQueue = new ArrayBlockingQueue<>(MAX_BATCH_SIZE * 2);
    }

    /**
     * Creates a new {@link BatchedBCTCurl} with the given hash length, number of rounds and batch timeout.
     *
     * @param hashLength          the desired hash length
     * @param numberOfRounds      the number of hashing rounds to apply
     * @param timeoutMilliseconds the timeout to wait for new incoming hashing requests before starting the process
     */
    public BatchedBCTCurl(int hashLength, int numberOfRounds, int timeoutMilliseconds) {
        this.hashLength = hashLength;
        this.numberOfRounds = numberOfRounds;
        this.batchTimeoutMilliSec = timeoutMilliseconds;
        this.reqQueue = new ArrayBlockingQueue<>(MAX_BATCH_SIZE * 2);
    }

    @Override
    public void submitHashingRequest(HashRequest req) {
        try {
            reqQueue.put(req);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        List<HashRequest> reqs = new ArrayList<>();
        long last = System.currentTimeMillis();
        long processed = 0, cycles = 0, cyclesTimeSum = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long start = System.currentTimeMillis();
                // await the first request
                reqs.add(reqQueue.take());

                // batch up requests until we hit the timeout once
                while (true) {
                    HashRequest newReq = reqQueue.poll(batchTimeoutMilliSec, TimeUnit.MILLISECONDS);

                    // didn't get any request within the timeout, lets thereby
                    // start processing batched up requests.
                    if (newReq == null) {
                        break;
                    }
                    reqs.add(newReq);
                    if (reqs.size() == MAX_BATCH_SIZE) {
                        break;
                    }
                }
                processed += reqs.size();
                process(reqs);
                reqs.clear();

                // remember some stats
                long now = System.currentTimeMillis();
                cycles++;
                cyclesTimeSum += now - start;

                // print some stats every now and then
                if (now - last >= 20000L) {
                    long maxReqsPossibleToBeProcessed = cycles * MAX_BATCH_SIZE;
                    double ratio = Math.floor(((double) processed / (double) maxReqsPossibleToBeProcessed) * 100);
                    double avgCycleTime = cyclesTimeSum / cycles;
                    log.info(
                            "batching saturation ratio {}% (processed {} / max possible {}), cycles {}, avg. cycle time {}ms",
                            ratio, processed, maxReqsPossibleToBeProcessed, cycles, avgCycleTime);
                    last = now;
                    processed = 0;
                    cycles = 0;
                    cyclesTimeSum = 0;
                }
            } catch (InterruptedException e) {
                log.info("shutdown signal received");
                Thread.currentThread().interrupt();
            }
        }
        log.info("BatchedBCTCurl shutdown");
    }

    /**
     * Processes the list of the given requests and executes the callbacks provided with each request after completion.
     *
     * @param reqs The requests to process.
     */
    private void process(List<HashRequest> reqs) {
        // multiplex input data
        ArrayList<byte[]> inputs = reqs.stream().map(HashRequest::getInput)
                .collect(Collectors.toCollection(ArrayList::new));
        BCTernaryMultiplexer multiplexer = new BCTernaryMultiplexer(inputs);
        BCTrinary multiplexedData = multiplexer.extract();

        // hash
        BCTCurl bctCurl = new BCTCurl(hashLength, numberOfRounds);
        bctCurl.reset();
        bctCurl.absorb(multiplexedData);

        // demultiplex and fire callbacks
        BCTrinary result = bctCurl.squeeze(hashLength);
        BCTernaryDemultiplexer demultiplexer = new BCTernaryDemultiplexer(result);
        for (int i = 0; i < reqs.size(); i++) {
            reqs.get(i).getCallback().process(demultiplexer.get(i));
        }
    }
}