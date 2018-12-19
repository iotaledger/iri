package com.iota.iri.conf.converters;

import com.beust.jcommander.IStringConverter;
import com.iota.iri.conf.APIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Used by JCommander to convert a string from command line into an InetAddress.
 * Refer to {@link APIConfig.Descriptions#REMOTE_TRUSTED_API_HOSTS} for more details.
 *
 * @author legacycode
 */
public class InetAddressConverter implements IStringConverter<InetAddress> {
    private static final Logger log = LoggerFactory.getLogger(InetAddressConverter.class);

    @Override
    public InetAddress convert(String address) {
        try {
            return InetAddress.getByName(address.trim());
           } catch (UnknownHostException e) {
            log.error("Invalid value ({}) for paramater: {}", address.trim(), e.getMessage());
        }
        return null;
    }
}
