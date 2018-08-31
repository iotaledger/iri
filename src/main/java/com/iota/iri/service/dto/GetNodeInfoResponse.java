package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;

public class GetNodeInfoResponse extends AbstractResponse {

	private String appName;
	private String appVersion;
	private int jreAvailableProcessors;
	private long jreFreeMemory;
	private String jreVersion;

    private long jreMaxMemory;
    private long jreTotalMemory;
    private String latestMilestone;
    private int latestMilestoneIndex;

    private String latestSolidSubtangleMilestone;
    private int latestSolidSubtangleMilestoneIndex;

    private int milestoneStartIndex;

    private int neighbors;
    private int packetsQueueSize;
    private long time;
    private int tips;
    private int transactionsToRequest;

	public static AbstractResponse create(String appName, String appVersion, int jreAvailableProcessors, long jreFreeMemory,
	        String jreVersion, long maxMemory, long totalMemory, Hash latestMilestone, int latestMilestoneIndex,
	        Hash latestSolidSubtangleMilestone, int latestSolidSubtangleMilestoneIndex, int milestoneStartIndex,
	        int neighbors, int packetsQueueSize,
	        long currentTimeMillis, int tips, int numberOfTransactionsToRequest) {
		final GetNodeInfoResponse res = new GetNodeInfoResponse();
		res.appName = appName;
		res.appVersion = appVersion;
		res.jreAvailableProcessors = jreAvailableProcessors;
		res.jreFreeMemory = jreFreeMemory;
		res.jreVersion = jreVersion;

		res.jreMaxMemory = maxMemory;
		res.jreTotalMemory = totalMemory;
		res.latestMilestone = latestMilestone.toString();
		res.latestMilestoneIndex = latestMilestoneIndex;

		res.latestSolidSubtangleMilestone = latestSolidSubtangleMilestone.toString();
		res.latestSolidSubtangleMilestoneIndex = latestSolidSubtangleMilestoneIndex;

		res.milestoneStartIndex = milestoneStartIndex;

		res.neighbors = neighbors;
		res.packetsQueueSize = packetsQueueSize;
		res.time = currentTimeMillis;
		res.tips = tips;
		res.transactionsToRequest = numberOfTransactionsToRequest;
		return res;
	}

    /**
     * Name of the IOTA software you're currently using (IRI stands for IOTA Reference Implementation)
     *
     * @return The app name.
     */
	public String getAppName() {
		return appName;
	}
    
    /**
     * The version of the IOTA software you're currently running.
     * 
     * @return The app version.
     */
	public String getAppVersion() {
		return appVersion;
	}

    /**
     * Available cores on your machine for JRE.
     *
     * @return The available processors of the JRE.
     */
	public int getJreAvailableProcessors() {
		return jreAvailableProcessors;
	}

    /**
     * The amount of free memory in the Java Virtual Machine.
     *
     * @return The free memory of the JRE.
     */
	public long getJreFreeMemory() {
		return jreFreeMemory;
	}

    /**
     * The maximum amount of memory that the Java virtual machine will attempt to use.
     *
     * @return The maximum memory of the JRE.
     */
	public long getJreMaxMemory() {
		return jreMaxMemory;
	}

    /**
     * The total amount of memory in the Java virtual machine.
     *
     * @return The total memory of the JRE.
     */
	public long getJreTotalMemory() {
		return jreTotalMemory;
	}

    /**
     * The JRE version this node runs on
     *
     * @return The JRE version.
     */
	public String getJreVersion() {
		return jreVersion;
	}

    /**
     * The hash of the latest transaction that was signed off by the coordinator.
     *
     * @return The latest milestone.
     */
	public String getLatestMilestone() {
		return latestMilestone;
	}

    /**
     * Index of the latest milestone.
     *
     * @return The latest milestone index.
     */
	public int getLatestMilestoneIndex() {
		return latestMilestoneIndex;
	}

    /**
     * The hash of the latest transaction which is solid and is used for sending transactions. 
     * For a milestone to become solid your local node must basically approve the subtangle of coordinator-approved transactions, 
     *  and have a consistent view of all referenced transactions.
     *
     * @return The latest subtangle milestone hash.
     */
	public String getLatestSolidSubtangleMilestone() {
		return latestSolidSubtangleMilestone;
	}

    /**
     * Index of the latest solid subtangle.
     * 
     * @return The latest subtangle milestone index.
     */
	public int getLatestSolidSubtangleMilestoneIndex() {
		return latestSolidSubtangleMilestoneIndex;
	}

    /**
     * Gets the start milestone index
     *
     * @return The start milestone index.
     */
	public int getMilestoneStartIndex() {
		return milestoneStartIndex;
	}

    /**
     * Number of neighbors you are directly connected with.
     *
     * @return The neighbors.
     */
	public int getNeighbors() {
		return neighbors;
	}

    /**
     * Packets which are currently queued up.
     *
     * @return The size of the packets queue.
     */
	public int getPacketsQueueSize() {
		return packetsQueueSize;
	}

    /**
     * Current UNIX timestamp.
     *
     * @return The time.
     */
	public long getTime() {
		return time;
	}

    /**
     * Number of tips in the network.
     *
     * @return The tips.
     */
	public int getTips() {
		return tips;
	}

    /**
     * When a node receives a transaction from one of its neighbors, this transaction is referencing two other transactions t1 and t2 (trunk and branch transaction). 
     * If either t1 or t2 (or both) is not in the node's local database, then the transaction hash of t1 (or t2 or both) is added to the queue of the "transactions to request".
     * At some point, the node will process this queue and ask for details about transactions in the "transaction to request" queue from one of its neighbors. 
     * By this means, nodes solidify their view of the tangle (i.e. filling in the unknown parts).
     *
     * @return The transactions to tequest.
     */
	public int getTransactionsToRequest() {
		return transactionsToRequest;
	}

}
