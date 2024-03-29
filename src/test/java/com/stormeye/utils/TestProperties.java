package com.stormeye.utils;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author ian@meywood.com
 */
@Getter
public class TestProperties {

    private final Logger logger = LoggerFactory.getLogger(TestProperties.class);
    private final String chainName;
    private final String hostname;
    private final String dockerName;
    private final int rcpPort;
    private final int restPort;
    private final int ssePort;
    private final int spxPort;

    public TestProperties() {
        this.hostname = getProperty("cspr.hostname", "localhost");
        this.dockerName = getProperty("cspr.docker.name", "cspr-cctl");
        this.rcpPort = getIntProperty("cspr.port.rcp", 11101);
        this.restPort = getIntProperty("cspr.port.rest", 14101);
        this.ssePort = getIntProperty("cspr.port.sse", 18101);
        this.spxPort = getIntProperty("cspr.port.spx", 25101);
        this.chainName = getProperty("cspr.chain.name", "cspr-dev-cctl");
    }

    private String getProperty(final String name, final String defaultValue) {
        final String property = System.getProperty(name, defaultValue);
        logger.info("{} = {}", name, property);
        return property;
    }

    private int getIntProperty(final String name, final int defaultValue) {
        return Integer.parseInt(getProperty(name, Integer.toString(defaultValue)));
    }
}
