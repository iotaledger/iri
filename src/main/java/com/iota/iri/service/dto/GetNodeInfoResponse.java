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

    private int neighbors;
    private int packetsQueueSize;
    private long time;
    private int tips;
    private int transactionsToRequest;

	public static AbstractResponse create(String appName, String appVersion, int jreAvailableProcessors, long jreFreeMemory,
	        String jreVersion, long maxMemory, long totalMemory, Hash latestMilestone, int latestMilestoneIndex,
	        Hash latestSolidSubtangleMilestone, int latestSolidSubtangleMilestoneIndex,
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

		res.neighbors = neighbors;
		res.packetsQueueSize = packetsQueueSize;
		res.time = currentTimeMillis;
		res.tips = tips;
		res.transactionsToRequest = numberOfTransactionsToRequest;
		return res;
	}

	public String getAppName() {
		return appName;
	}

	public String getAppVersion() {
		return appVersion;
	}

	public int getJreAvailableProcessors() {
		return jreAvailableProcessors;
	}

	public long getJreFreeMemory() {
		return jreFreeMemory;
	}

	public long getJreMaxMemory() {
		return jreMaxMemory;
	}

	public long getJreTotalMemory() {
		return jreTotalMemory;
	}

	public String getJreVersion() {
		return jreVersion;
	}

	public String getLatestMilestone() {
		return latestMilestone;
	}

	public int getLatestMilestoneIndex() {
		return latestMilestoneIndex;
	}

	public String getLatestSolidSubtangleMilestone() {
		return latestSolidSubtangleMilestone;
	}

	public int getLatestSolidSubtangleMilestoneIndex() {
		return latestSolidSubtangleMilestoneIndex;
	}

	public int getNeighbors() {
		return neighbors;
	}

	public int getPacketsQueueSize() {
		return packetsQueueSize;
	}

	public long getTime() {
		return time;
	}

	public int getTips() {
		return tips;
	}

	public int getTransactionsToRequest() {
		return transactionsToRequest;
	}

}
