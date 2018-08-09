package com.iota.iri.service.dto;

/**
 * This class represents the core API request 'removeNeighbors'.
 * Removes a list of neighbors to your node. 
 * This is only temporary, and if you have your neighbors added via the command line, they will be retained after you restart your node.
 * The URI (Unique Resource Identification) for removing neighbors is:
 * <b>udp://IPADDRESS:PORT</b>
 **/
public class RemoveNeighborsResponse extends AbstractResponse {
	
	private int removedNeighbors;

	public static AbstractResponse create(int numberOfRemovedNeighbors) {
		RemoveNeighborsResponse res = new RemoveNeighborsResponse();
		res.removedNeighbors = numberOfRemovedNeighbors;
		return res;
	}
	
    /**
     * Gets the number of removed neighbors.
     *
     * @return The number of removed neighbors.
     */
	public int getRemovedNeighbors() {
		return removedNeighbors;
	}

}
