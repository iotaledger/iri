package com.iota.iri.service.dto;

import com.iota.iri.conf.IotaConfig;

/**
 * Contains information about the result of a successful {@link com.iota.iri.service.API#getNodeAPIConfigurationStatement()} API call.
 * See {@link com.iota.iri.service.API#getNodeAPIConfigurationStatement()} for how this response is created.
 */
public class GetNodeAPIConfigurationResponse extends AbstractResponse {
    /** {@link IotaConfig#getMaxFindTransactions()} */
    private int maxFindTransactions;
    /** {@link IotaConfig#getMaxRequestsList()} */
    private int maxRequestsList;
    /** {@link IotaConfig#getMaxGetTrytes()} */
    private int maxGetTrytes;
    /** {@link IotaConfig#getMaxBodyLength()} */
    private int maxBodyLength;
    /** {@link IotaConfig#isTestnet()} */
    private boolean testNet;
    /** {@link IotaConfig#getMilestoneStartIndex()} */
    private int milestoneStartIndex;

    /**
     * Use factory method {@link GetNodeAPIConfigurationResponse#create(IotaConfig) to create response.}
     */
    private GetNodeAPIConfigurationResponse() {
    }

    /**
     * Creates a new {@link GetNodeAPIConfigurationResponse} with configuration options that should be returned.
     * <b>Make sure that you do not return secret informations (e.g. passwords, secrets...).</b>
     *
     * @param configuration {@link IotaConfig} used to create response.
     * @return an {@link GetNodeAPIConfigurationResponse} filled with actual config options.
     */
    public static AbstractResponse create(IotaConfig configuration) {
        if(configuration == null) {
            throw new IllegalStateException("configuration must not be null!");
        }

        final GetNodeAPIConfigurationResponse res = new GetNodeAPIConfigurationResponse();

        res.maxFindTransactions = configuration.getMaxFindTransactions();
        res.maxRequestsList = configuration.getMaxRequestsList();
        res.maxGetTrytes = configuration.getMaxGetTrytes();
        res.maxBodyLength = configuration.getMaxBodyLength();
        res.testNet = configuration.isTestnet();
        res.milestoneStartIndex = configuration.getMilestoneStartIndex();

        return res;
    }

    public int getMaxFindTransactions() {
        return maxFindTransactions;
    }

    public int getMaxRequestsList() {
        return maxRequestsList;
    }

    public int getMaxGetTrytes() {
        return maxGetTrytes;
    }

    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    public boolean isTestNet() {
        return testNet;
    }

    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }
}
