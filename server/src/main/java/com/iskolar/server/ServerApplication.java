package com.iskolar.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * iskolarPath server — central scholarship catalog + auth API.
 *
 * <p>Phase 2 of the centralized-database plan: the desktop app will pull the
 * catalog snapshot from this service and use it for offline matching.</p>
 */
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}
