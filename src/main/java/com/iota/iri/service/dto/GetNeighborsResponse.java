package com.iota.iri.service.dto;

import java.util.List;

public class GetNeighborsResponse extends AbstractResponse {

	private Neighbor[] neighbors;

    public Neighbor[] getNeighbors() {
        return neighbors;
    }

    static class Neighbor {

    	private String address;
    	public int numberOfAllTransactions, numberOfNewTransactions, numberOfInvalidTransactions;

        public String getAddress() {
            return address;
        }
        public int getNumberOfAllTransactions() {
            return numberOfAllTransactions;
        }
        public int getNumberOfNewTransactions() {
            return numberOfNewTransactions;
        }
        public int getNumberOfInvalidTransactions() {
			return numberOfInvalidTransactions;
		}

        public static Neighbor createFrom(com.iota.iri.Neighbor n) {
        	Neighbor ne = new Neighbor();
        	ne.address = n.getAddress().getHostString() + ":" + n.getAddress().getPort();
        	ne.numberOfAllTransactions = n.getNumberOfAllTransactions();
        	ne.numberOfInvalidTransactions = n.getNumberOfInvalidTransactions();
        	ne.numberOfNewTransactions = n.getNumberOfNewTransactions();
        	return ne;
        }
    }

	public static AbstractResponse create(final List<com.iota.iri.Neighbor> elements) {
		GetNeighborsResponse res = new GetNeighborsResponse();
		res.neighbors = new Neighbor[elements.size()]; int i = 0;
		for (com.iota.iri.Neighbor n : elements) {
			res.neighbors[i++] = Neighbor.createFrom(n);
		}
		return res;
	}

}
