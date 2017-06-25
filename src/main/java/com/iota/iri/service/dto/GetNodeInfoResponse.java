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

	@SuppressWarnings("unused") // used in the API
	public String getAppName() {
		return appName;
	}

	@SuppressWarnings("unused") // used in the API
	public String getAppVersion() {
		return appVersion;
	}

	@SuppressWarnings("unused") // used in the API
	public int getJreAvailableProcessors() {
		return jreAvailableProcessors;
	}

	@SuppressWarnings("unused") // used in the API
	public long getJreFreeMemory() {
		return jreFreeMemory;
	}

	@SuppressWarnings("unused") // used in the API
	public long getJreMaxMemory() {
		return jreMaxMemory;
	}

	@SuppressWarnings("unused") // used in the API
	public long getJreTotalMemory() {
		return jreTotalMemory;
	}

	@SuppressWarnings("unused") // used in the API
	public String getJreVersion() {
		return jreVersion;
	}

	@SuppressWarnings("unused") // used in the API
	public String getLatestMilestone() {
		return latestMilestone;
	}

	@SuppressWarnings("unused") // used in the API
	public int getLatestMilestoneIndex() {
		return latestMilestoneIndex;
	}

	@SuppressWarnings("unused") // used in the API
	public String getLatestSolidSubtangleMilestone() {
		return latestSolidSubtangleMilestone;
	}

	@SuppressWarnings("unused") // used in the API
	public int getLatestSolidSubtangleMilestoneIndex() {
		return latestSolidSubtangleMilestoneIndex;
	}

	@SuppressWarnings("unused") // used in the API
	public int getNeighbors() {
		return neighbors;
	}

	@SuppressWarnings("unused") // used in the API
	public int getPacketsQueueSize() {
		return packetsQueueSize;
	}

	@SuppressWarnings("unused") // used in the API
	public long getTime() {
		return time;
	}

	@SuppressWarnings("unused") // used in the API
	public int getTips() {
		return tips;
	}

	@SuppressWarnings("unused") // used in the API
	public int getTransactionsToRequest() {
		return transactionsToRequest;
	}

}
