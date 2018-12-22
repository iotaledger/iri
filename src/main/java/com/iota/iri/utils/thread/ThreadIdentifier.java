package com.iota.iri.utils.thread;

/**
 * The instances of this class are used by the {@link ThreadUtils} to map the {@link Thread}s and make the corresponding
 * spawn and stop methods thread safe.
 */
public class ThreadIdentifier {
    /**
     * Holds the name of the {@link Thread}.
     */
    private final String name;

    /**
     * The constructor simply stores the passed in name in the private property of this instance.
     *
     * While the name is not required to identify the {@link Thread}, we still require to give one to be able to create
     * meaningful log messages.
     *
     * @param name name of the {@link Thread} that get referenced by this identifier.
     */
    public ThreadIdentifier(String name) {
        this.name = name;
    }

    /**
     * Getter of the name that was used to create this identifier.
     *
     * It simply returns the internal property.
     *
     * @return name of the {@link Thread} that this identifier references
     */
    public String getName() {
        return name;
    }
}
