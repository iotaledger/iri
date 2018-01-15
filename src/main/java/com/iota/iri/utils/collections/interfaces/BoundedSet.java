package com.iota.iri.utils.collections.interfaces;

import java.util.Set;

/**
 * A set that can't hold more than {@link #getMaxSize()} elements
 *
 * @author galrogo on 08/02/18
 **/
public interface BoundedSet<E> extends BoundedCollection<E>, Set<E>{
}
