package com.iota.iri.network.spam;

import com.iota.iri.network.Neighbor;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Simple spam prevention strategy that considers a percentage of the most active neighbors (the ones that send the
 * most new transactions) to be spamming. See issue #1293.
 */
public class DropFixedPercentage implements SpamPreventionStrategy {

    // only one instance but needs to be static (and lower case) according to IRI code style
    private static final Logger log = LoggerFactory.getLogger(DropFixedPercentage.class);

    private final int dropPercentage;
    private Map<Neighbor, Long> transactionCount;
    private Set<Neighbor> spammingNeighbors = Collections.emptySet();

    /**
     * Creates a new spam strategy that marks a fixed percentage of neighbors to be spamming.
     * @param dropPercentage The percentage of neighbors that are considered to be spamming. Percentage will be rounded
     *                       up. So 33% of 3 neighbors is one neighbors. 33% of 4 neighbors is two neighbors.
     * @param neighbors The list of neighbors that should be examined for finding spamming nodes.
     */
    public DropFixedPercentage(int dropPercentage, List<Neighbor> neighbors) {
        log.info("Created fixed percentage spam prevention strategy. Dropping transactions of {}% most active neighbors.", dropPercentage);
        this.dropPercentage = dropPercentage;
        transactionCount = toMap(neighbors);
    }

    /**
     * Finds out which neighbors are currently spamming.
     * @param neighbors The list of neighbors that should be included in the calculation.
     */
    public void calculateSpam(List<Neighbor> neighbors) {
        List<Pair<Long, Neighbor>> deltas = new ArrayList<>(neighbors.size());
        neighbors.forEach(neighbor -> {
            Long previous = transactionCount.getOrDefault(neighbor, neighbor.getNumberOfNewTransactions());
            long delta = neighbor.getNumberOfNewTransactions() - previous;
            if (delta > 0) { // only active neighbors count
                deltas.add(new Pair<>(delta, neighbor));
            }
        });

        int droppedNeighbors = calculateNumberOfSpammingNeighbors(deltas);
        spammingNeighbors = calculateSpammingNeighbors(deltas, droppedNeighbors);
        transactionCount = toMap(neighbors);
        debugLog(deltas, droppedNeighbors);

    }

    /**
     * @inheritDoc
     */
    public boolean broadcastTransactionFrom(Neighbor neighbor) {
        return !spammingNeighbors.contains(neighbor);
    }

    private Set<Neighbor> calculateSpammingNeighbors(List<Pair<Long, Neighbor>> deltas, int numberOfSpammingNeighbors) {
        Collections.shuffle(deltas); // ignore order of neighbors for similar deltas
        return deltas.stream()
                .sorted(Comparator.comparing(p -> -p.low))
                .limit(numberOfSpammingNeighbors)
                .map(p -> p.hi)
                .collect(Collectors.toSet());
    }

    private int calculateNumberOfSpammingNeighbors(List<Pair<Long, Neighbor>> deltas) {
        return deltas.size() == 1
                ? 0 // don't block single neighbor
                : (int) Math.ceil(deltas.size() * (dropPercentage / 100d)); // round up
    }

    private Map<Neighbor, Long> toMap(List<Neighbor> neighbors) {
        return neighbors.stream().collect(Collectors.toMap(Function.identity(), Neighbor::getNumberOfNewTransactions));
    }

    private void debugLog(List<Pair<Long, Neighbor>> deltas, int droppedNeighbors) {
        if (log.isDebugEnabled()) {
            log.debug("Deltas: {}", deltas.stream().map(p -> p.hi.getHostAddress() + "=" + p.low).collect(Collectors.joining(", ")));
            log.debug("{}% (rounded up) are [{}] neighbor(s).", dropPercentage, droppedNeighbors);
            log.debug("Spamming neighbor(s): {}", spammingNeighbors.stream().map(Neighbor::getHostAddress).collect(Collectors.joining(", ")));
        }
    }

}
