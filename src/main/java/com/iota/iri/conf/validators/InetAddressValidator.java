package com.iota.iri.conf.validators;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import com.iota.iri.utils.IotaUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static com.iota.iri.conf.BaseIotaConfig.SPLIT_STRING_TO_LIST_REGEX;

/**
 * Used by JCommander to validate if the user entered a valid ipaddress or hostname from the command line.
 * Refer to {@link com.iota.iri.conf.APIConfig.Descriptions#REMOTE_TRUSTED_API_HOSTS} for more details.
 */
public class InetAddressValidator implements IParameterValidator {

    @Override
    public void validate(String name, String addressString) throws ParameterException {
        List<String> addresses = IotaUtils.splitStringToImmutableList(addressString, SPLIT_STRING_TO_LIST_REGEX);
        for (String address : addresses) {
            try {
                InetAddress.getByName(address);
            } catch (UnknownHostException e) {
                throw new ParameterException("Invalid value (" + address + ") for parameter: " + name);
            }
        }
    }
}
