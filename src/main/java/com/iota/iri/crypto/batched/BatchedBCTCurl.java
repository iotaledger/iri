package com.iota.iri.crypto.batched;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * BatchedBCTCurl takes care of batching up hashing requests and starts
 * processing them through a BCTCurl once either all available slots
 * are filled or no request is submitted within a given timeout.
 */
public class BatchedBCTCurl implements BatchedHasher {

    // we have max 64 bits/slots available for requests to fill up
    private final static int MAX_BATCH_SIZE = 64;

    private ArrayBlockingQueue<HashRequest> reqQueue;
    private int hashLength;
    private int numberOfRounds;
    private int batchTimeoutMilliSec;

    private AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a new {@link BatchedBCTCurl} with the given
     * hash length, number of rounds and default batch timeout.
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
     * Creates a new {@link BatchedBCTCurl} with the given
     * hash length, number of rounds and batch timeout.
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
    public void shutdown() {
        running.set(false);
    }

    @Override
    public void run() {
        running.set(true);
        List<HashRequest> reqs = new ArrayList<>();
        exit:
        while (running.get()) {

            // await the first request
            HashRequest firstReq = null;
            try {
                firstReq = reqQueue.take();
            } catch (InterruptedException e) {
                break;
            }
            reqs.add(firstReq);

            // batch up requests until we hit the timeout once
            while (true) {
                HashRequest newReq;
                try {
                    newReq = reqQueue.poll(batchTimeoutMilliSec, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    break exit;
                }
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
            process(reqs);
            reqs.clear();
        }
        running.set(false);
    }

    /**
     * Processes the list of the given requests and executes the callbacks
     * provided with each request after completion.
     *
     * @param reqs The requests to process.
     */
    private void process(List<HashRequest> reqs) {
        // multiplex input data
        ArrayList<byte[]> inputs = reqs.stream().map(HashRequest::getInput).collect(Collectors.toCollection(ArrayList::new));
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