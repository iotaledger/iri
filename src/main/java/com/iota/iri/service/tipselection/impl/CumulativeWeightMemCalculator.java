package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.utils.collections.impl.TransformingBoundedHashSet;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.utils.collections.impl.TransformingMap;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of <tt>RatingCalculator</tt> that gives the cumulative for each transaction referencing entryPoint.
 * Used to create a weighted random walks.
 *
 * @see <a href="cumulative.md">https://github.com/alongalky/iota-docs/blob/master/cumulative.md</a>
 */
public class CumulativeWeightMemCalculator implements RatingCalculator{

    private static final Logger log = LoggerFactory.getLogger(CumulativeWeightMemCalculator.class);
    public static final int MAX_FUTURE_SET_SIZE = 5000;

    public final Tangle tangle;

    public CumulativeWeightMemCalculator(Tangle tangle) {
        this.tangle = tangle;
    }

    @Override
    public UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
        log.debug("Start calculating cw starting with tx hash {}", entryPoint);

        UnIterableMap<HashId, Integer> ret = new TransformingMap<>(HashPrefix::createPrefix, null);

        Set<Hash> visited = new HashSet<Hash> ();
        LinkedList<Hash> queue = new LinkedList<>();
        queue.add(entryPoint);
        Hash h;
        while (!queue.isEmpty()) {
            h = queue.pop();
            for (Hash e : tangle.getChild(h)) {
                if (tangle.contains(e) && !visited.contains(e)) {
                    queue.add(e);
                    visited.add(e);
                }
            }
            ret.put(h, (tangle.getScore(h).intValue()));
        }

        return ret;
    }
}
