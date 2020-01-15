package com.iota.iri.utils;


import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IotaIOUtils extends IOUtils {

    private static final Logger log = LoggerFactory.getLogger(IotaIOUtils.class);

    public static void closeQuietly(AutoCloseable... autoCloseables) {
        for (AutoCloseable it : autoCloseables) {
            try {
                if (it != null) {
                    it.close();
                }
            } catch (Exception ignored) {
                log.debug("Silent exception occured", ignored);
            }
        }
    }
    
    /**
     * Parses a java INI file with respect to sections, without relying on an external library.
     * 
     * From: https://stackoverflow.com/a/41084504/4512850 -ish
     * @param stream The input stream we will be reading the INI from
     * @return A map of sections with their properties. 
     *         If there was a section-less start, these will be put in "default".
     * @throws IOException
     */
    public static Map<String, Properties> parseINI(InputStream stream) throws IOException {
        Map<String, Properties> result = new HashMap<>();
        
        @SuppressWarnings("serial")
        Properties p = new Properties() {

            private Properties section;

            @Override
            public synchronized Object put(Object key, Object value) {
                String header = (((String) key) + " " + value).trim();
                if (header.startsWith("[") && header.endsWith("]")) {
                    return result.put(header.substring(1, header.length() - 1), 
                            section = new Properties());
                } else if (section != null){
                    return section.put(key, value);
                } else {
                    return super.put(key, value);
                }
            };

        };
        p.load(stream);
        result.put("default", p);
        return result;
    }
}
