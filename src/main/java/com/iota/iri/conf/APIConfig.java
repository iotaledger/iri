package com.iota.iri.conf;

import java.util.List;

/**
 * Configurations for node API
 */
public interface APIConfig extends Config {


    /**
     * Default Value: {@value BaseIotaConfig.Defaults#API_PORT}
     *
     * @return {@value APIConfig.Descriptions#PORT}
     */
    int getPort();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#API_HOST}
     *
     * @return {@value APIConfig.Descriptions#API_HOST}
     */
    String getApiHost();


    /**
     * Default Value: {@link BaseIotaConfig.Defaults#REMOTE_LIMIT_API}
     *
     * @return {@value APIConfig.Descriptions#REMOTE_LIMIT_API}
     */
    List<String> getRemoteLimitApi();

    /**
     * Default Value: {@link BaseIotaConfig.Defaults#MAX_FIND_TRANSACTIONS}
     *
     * @return {@value APIConfig.Descriptions#MAX_FIND_TRANSACTIONS}
     */
    int getMaxFindTransactions();

    /**
     * Default Value: {@link BaseIotaConfig.Defaults#MAX_REQUESTS_LIST}
     *
     * @return {@value APIConfig.Descriptions#MAX_REQUESTS_LIST}
     */
    int getMaxRequestsList();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#MAX_GET_TRYTES}
     *
     * @return {@value APIConfig.Descriptions#MAX_GET_TRYTES}
     */
    int getMaxGetTrytes();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#MAX_BODY_LENGTH}
     *
     * @return {@value APIConfig.Descriptions#MAX_BODY_LENGTH}
     */
    int getMaxBodyLength();

    /**
     * Default Value: {@value BaseIotaConfig.Defaults#REMOTE_AUTH}
     *
     * @return {@value APIConfig.Descriptions#REMOTE_AUTH}
     */
    String getRemoteAuth();

    interface Descriptions {
        String PORT = "The port that will be used by the API.";
        String API_HOST = "The host on which the API will listen to. Set to 0.0.0.0 to accept any host.";
        String REMOTE_LIMIT_API = "Commands that should be ignored by API.";
        String REMOTE_AUTH = "A string in the form of <user>:<password>. Used to access the API";
        String MAX_FIND_TRANSACTIONS = "The maximal number of transactions that may be returned by the \"findTransactions\" API call. If the number of transactions found exceeds this number an error will be returned.";
        String MAX_REQUESTS_LIST = "The maximal number of parameters one can place in an API call. If the number parameters exceeds this number an error will be returned";
        String MAX_GET_TRYTES = "The maximal number of trytes that may be returned by the \"getTrytes\" API call. If the number of transactions found exceeds this number an error will be returned.";
        String MAX_BODY_LENGTH = "The maximal number of characters the body of an API call may hold. If a request body length exceeds this number an error will be returned.";
        String REMOTE = "Open the API interface to any host. Equivalent to \"--api-host 0.0.0.0\"";
    }
}
