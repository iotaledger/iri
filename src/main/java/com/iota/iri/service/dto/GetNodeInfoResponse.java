package com.iota.iri.service.dto;

import com.iota.iri.model.Hash;
import com.iota.iri.service.API;
import com.iota.iri.service.Feature;

/**
 * 
 * Contains information about the result of a successful {@code getNodeInfo} API call.
 * See {@link API#getNodeInfoStatement} for how this response is created.
 *
 */
public class GetNodeInfoResponse extends AbstractResponse {

    /**
     * Name of the IOTA software you're currently using. (IRI stands for IOTA Reference Implementation)
     */
	private String appName;
	
	/**
	 * The version of the IOTA software this node is running.
	 */
	private String appVersion;

    /**
     * Available cores for JRE on this node.
     */
	private int jreAvailableProcessors;
	
    /**
     * The amount of free memory in the Java Virtual Machine.
     */
    private long jreFreeMemory;
    
    /**
     * The JRE version this node runs on
     */
    private String jreVersion;

    /**
     * The maximum amount of memory that the Java virtual machine will attempt to use.
     */
    private long jreMaxMemory;
    
    /**
     * The total amount of memory in the Java virtual machine.
     */
    private long jreTotalMemory;
    
    /**
     * The hash of the latest transaction that was signed off by the coordinator.
     */
    private String latestMilestone;
    
    /**
     * Index of the {@link #latestMilestone}
     */
    private int latestMilestoneIndex;
    
    /**
     * The hash of the latest transaction which is solid and is used for sending transactions. 
     * For a milestone to become solid, your local node must approve the subtangle of coordinator-approved transactions, 
     *  and have a consistent view of all referenced transactions.
     */
    private String latestSolidSubtangleMilestone;
    
    /**
     * Index of the {@link #latestSolidSubtangleMilestone}
     */
    private int latestSolidSubtangleMilestoneIndex;
    
    /**
     * The start index of the milestones. 
     * This index is encoded in each milestone transaction by the coordinator
     */
    private int milestoneStartIndex;
    
    /**
     * Number of neighbors this node is directly connected with.
     */
    private int neighbors;
    
    /**
     * The amount of transaction packets which are currently waiting to be broadcast.
     */
    private int packetsQueueSize;
    
    /**
     * The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC
     */
    private long time;
    
    /**
     * Number of tips in the network.
     */
    private int tips;
    
    /**
     * When a node receives a transaction from one of its neighbors, 
     * this transaction is referencing two other transactions t1 and t2 (trunk and branch transaction). 
     * If either t1 or t2 (or both) is not in the node's local database, 
     * then the transaction hash of t1 (or t2 or both) is added to the queue of the "transactions to request".
     * At some point, the node will process this queue and ask for details about transactions in the
     *  "transaction to request" queue from one of its neighbors. 
     * This number represents the amount of "transaction to request"
     */
    private int transactionsToRequest;
    
    /**
     * Every node can have features enabled or disabled. 
     * This list will contain all the names of the features of a node as specified in {@link Feature}.
     */
    private String[] features;
    
    /**
     * The address of the Coordinator being followed by this node.
     */
    private String coordinatorAddress;

    /**
     * Creates a new {@link GetNodeInfoResponse}
     * 
     * @param appName {@link #appName}
     * @param appVersion {@link #appVersion}
     * @param jreAvailableProcessors {@link #jreAvailableProcessors}
     * @param jreFreeMemory {@link #jreFreeMemory}
     * @param jreVersion {@link #jreVersion}
     * @param maxMemory {@link #jreMaxMemory}
     * @param totalMemory {@link #jreTotalMemory}
     * @param latestMilestone {@link #latestMilestone}
     * @param latestMilestoneIndex {@link #latestMilestoneIndex}
     * @param latestSolidSubtangleMilestone {@link #latestSolidSubtangleMilestone}
     * @param latestSolidSubtangleMilestoneIndex {@link #latestSolidSubtangleMilestoneIndex}
     * @param milestoneStartIndex {@link #milestoneStartIndex}
     * @param neighbors {@link #neighbors}
     * @param packetsQueueSize {@link #packetsQueueSize}
     * @param currentTimeMillis {@link #time}
     * @param tips {@link #tips}
     * @param numberOfTransactionsToRequest {@link #transactionsToRequest}
     * @param features {@link #features}
     * @param coordinatorAddress {@link #coordinatorAddress}
     * @return a {@link GetNodeInfoResponse} filled with all the provided parameters
     */
	public static AbstractResponse create(String appName, String appVersion, int jreAvailableProcessors, long jreFreeMemory,
	        String jreVersion, long maxMemory, long totalMemory, Hash latestMilestone, int latestMilestoneIndex,
	        Hash latestSolidSubtangleMilestone, int latestSolidSubtangleMilestoneIndex, int milestoneStartIndex,
	        int neighbors, int packetsQueueSize, long currentTimeMillis, int tips, 
	        int numberOfTransactionsToRequest,  String[] features, String coordinatorAddress) {
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
		
		res.features = features;
		res.coordinatorAddress = coordinatorAddress;
		return res;
	}

    /**
     * 
     * @return {@link #appName}
     */
	public String getAppName() {
		return appName;
	}
    
    /**
     * 
     * @return {@link #appVersion}
     */
	public String getAppVersion() {
		return appVersion;
	}

    /**
     *
     * @return {@link #jreAvailableProcessors}
     */
	public int getJreAvailableProcessors() {
		return jreAvailableProcessors;
	}

    /**
     * 
     * @return {@link #jreFreeMemory}
     */
	public long getJreFreeMemory() {
		return jreFreeMemory;
	}

    /**
     *
     * @return {@link #jreMaxMemory}
     */
	public long getJreMaxMemory() {
		return jreMaxMemory;
	}

    /**
     *
     * @return {@link #jreTotalMemory}
     */
	public long getJreTotalMemory() {
		return jreTotalMemory;
	}

    /**
     *
     * @return {@link #jreVersion}
     */
	public String getJreVersion() {
		return jreVersion;
	}

    /**
     * 
     * @return {@link #latestMilestone}
     */
	public String getLatestMilestone() {
		return latestMilestone;
	}

    /**
     * 
     * @return {@link #latestMilestoneIndex}
     */
	public int getLatestMilestoneIndex() {
		return latestMilestoneIndex;
	}

    /**
     * 
     * @return {@link #latestSolidSubtangleMilestone}
     */
	public String getLatestSolidSubtangleMilestone() {
		return latestSolidSubtangleMilestone;
	}

    /**
     * 
     * @return {@link #latestSolidSubtangleMilestoneIndex}
     */
	public int getLatestSolidSubtangleMilestoneIndex() {
		return latestSolidSubtangleMilestoneIndex;
	}

    /**
     *
     * @return {@link #milestoneStartIndex}
     */
	public int getMilestoneStartIndex() {
		return milestoneStartIndex;
	}

    /**
     *
     * @return {@link #neighbors}
     */
	public int getNeighbors() {
		return neighbors;
	}

    /**
     *
     * @return {@link #packetsQueueSize}
     */
	public int getPacketsQueueSize() {
		return packetsQueueSize;
	}

    /**
     *
     * @return {@link #time}
     */
	public long getTime() {
		return time;
	}

    /**
     *
     * @return {@link #tips}
     */
	public int getTips() {
		return tips;
	}

    /**
     *
     * @return {@link #transactionsToRequest}
     */
	public int getTransactionsToRequest() {
		return transactionsToRequest;
	}
	
	/**
	 * 
	 * @return {@link #features}
	 */
	public String[] getFeatures() {
	    return features;
	}

	/**
	 * 
	 * @return {@link #coordinatorAddress}
	 */
	public String getCoordinatorAddress() {
		return coordinatorAddress;
	}
}
