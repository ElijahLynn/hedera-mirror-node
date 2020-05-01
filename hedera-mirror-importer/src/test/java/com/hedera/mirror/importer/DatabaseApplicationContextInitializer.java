package com.hedera.mirror.importer;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import javax.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * First try to use a Testcontainer. If Docker is not running or it fails to connect to the Testcontainer, fallback
 * to a database running on localhost.
 */
@Log4j2
@TestConfiguration
public class DatabaseApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static PostgreSQLContainer postgresql;

    static {
        System.setProperty("testcontainers.environmentprovider.timeout", "1");
        System.setProperty("testcontainers.npipesocketprovider.timeout", "1");
        System.setProperty("testcontainers.unixsocketprovider.timeout", "1");
        System.setProperty("testcontainers.windowsprovider.timeout", "1");
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            log.info("Starting PostgreSQL");
            postgresql = new PostgreSQLContainer<>("postgres:9.6-alpine");
            postgresql.start();

            TestPropertyValues
                    .of("hedera.mirror.importer.db.name=" + postgresql.getDatabaseName())
                    .and("hedera.mirror.importer.db.password=" + postgresql.getPassword())
                    .and("hedera.mirror.importer.db.username=" + postgresql.getUsername())
                    .and("spring.datasource.url=" + postgresql.getJdbcUrl())
                    .applyTo(applicationContext);
        } catch (Throwable ex) {
            log.warn(ex.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (postgresql != null && postgresql.isRunning()) {
            log.info("Stopping PostgreSQL");
            postgresql.stop();
        }
    }
}
