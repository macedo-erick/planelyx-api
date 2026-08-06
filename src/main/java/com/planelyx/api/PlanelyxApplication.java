package com.planelyx.api;

import com.planelyx.api.config.KeycloakAdminProperties;
import com.planelyx.api.config.ProvisioningProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({KeycloakAdminProperties.class, ProvisioningProperties.class})
public class PlanelyxApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlanelyxApplication.class, args);
    }
}
