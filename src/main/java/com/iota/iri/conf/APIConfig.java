package com.iota.iri.conf;

import java.util.List;

public interface APIConfig extends Config {


    /**
     * @return {@value Descriptions#PORT}
     */
    int getPort();

    String getApiHost();

    List<String> getRemoteLimitApi();

    int getMaxFindTransactions();

    int getMaxRequestList();

    int getMaxGetTrytes();

    int getMaxBodyLength();

    String getRemoteAuth();

    interface Descriptions {
        String PORT = "The port that will be used by the API.";
        String API_HOST = "The host on which the API will listen to. Set to 0.0.0.0 to accept any host.";
        String REMOTE_LIMIT_API = "Commands that should be ignored by API.";
        String REMOTE_AUTH = "A string in the form of <user>:<password>. Used to access the API";
        String MAX_FIND_TRANSACTIONS = "The maximal number of transactions that may be returned by the \"findTransactions\" API call. If the number of transactions found exceeds this number an error will be returned.";
        String MAX_REQUESTS_LIST = "The maximal number of parameters one can place in an API call. If the number parameters exceeds this number an error will be returned";
        String MAX_GET_TRYTES = "The maximal number of trytes that may be returned by the \"getTrytes\" API call. If the number of transactions found exceeds this number an error will be returned.";
        String MAX_BODY_LENGTH = "The maximal number of characters the body of an API call may hold. If a request body length exeeds this number an error will be returned.";
    }
}
