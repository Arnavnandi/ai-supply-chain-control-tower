package com.supplychain.controltower.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerConfigurationTest {

    @Test
    void testServerPortAndAddressConfiguration() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new FileSystemResource("src/main/resources/application.yml"));
        factoryBean.afterPropertiesSet();
        Properties props = factoryBean.getObject();

        assertNotNull(props, "Properties loaded from application.yml must not be null");
        assertEquals("${PORT:8080}", props.getProperty("server.port"),
                "server.port must bind to Render's injected ${PORT} with fallback to 8080");
        assertEquals("${SERVER_ADDRESS:0.0.0.0}", props.getProperty("server.address"),
                "server.address must bind to 0.0.0.0 to ensure HTTP listener is detected on all network interfaces");
    }
}
