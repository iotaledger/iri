package com.iota.iri.service.dto;

import com.iota.iri.conf.IotaConfig;
import com.sun.istack.internal.NotNull;

/**
 * Contains information about the result of a successful {@code getIotaConfigStatement} API call.
 * See {@link com.iota.iri.service.API#getIotaConfigStatement()} for how this response is created.
 */
public class GetIotaConfigResponse extends AbstractResponse {
    private int maxFindTransactions;
    private int maxRequestList;
    private int maxGetTrytes;
    private int maxBodyLength;
    private boolean testNet;
    private int milestoneStartIndex;

    /**
     * Use factory method {@link GetIotaConfigResponse#create(IotaConfig) to create response.}
     */
    private GetIotaConfigResponse() {
    }

    /**
     * Creates a new {@link GetIotaConfigResponse} with configuration options that should be returned.
     * <b>Make sure that you do not return secret informations (e.g. passwords, secrets...).</b>
     *
     * @param configuration {@link IotaConfig} used to create response.
     * @return an {@link GetIotaConfigResponse} filled with actual config options.
     */
    public static AbstractResponse create(@NotNull IotaConfig configuration) {
        if(configuration == null) {
            throw new IllegalStateException("configuration must not be null!");
        }

        final GetIotaConfigResponse res = new GetIotaConfigResponse();

        res.maxFindTransactions = configuration.getMaxFindTransactions();
        res.maxRequestList = configuration.getMaxRequestsList();
        res.maxGetTrytes = configuration.getMaxGetTrytes();
        res.maxBodyLength = configuration.getMaxBodyLength();
        res.testNet = configuration.isTestnet();
        res.milestoneStartIndex = configuration.getMilestoneStartIndex();

        return res;
    }

    /**
     * {@link IotaConfig#getMaxFindTransactions()}
     */
    public int getMaxFindTransactions() {
        return maxFindTransactions;
    }

    /**
     * {@link IotaConfig#getMaxRequestsList()}
     */
    public int getMaxRequestsList() {
        return maxRequestList;
    }

    /**
     * {link {@link IotaConfig#getMaxGetTrytes()}}
     */
    public int getMaxGetTrytes() {
        return maxGetTrytes;
    }

    /**
     * {@link IotaConfig#getMaxBodyLength()}
     */
    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    /**
     * {@link IotaConfig#isTestnet()}
     */
    public boolean isTestNet() {
        return testNet;
    }

    /**
     * {@link IotaConfig#getMilestoneStartIndex()}
     */
    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }
}
