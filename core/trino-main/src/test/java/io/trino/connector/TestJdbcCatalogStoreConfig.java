/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.connector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

public class TestJdbcCatalogStoreConfig
{
    protected PostgreSQLContainer<?> container;

    @BeforeClass
    public void setup()
    {
        container = new PostgreSQLContainer<>("postgres");
        container.start();
    }

    @AfterClass(alwaysRun = true)
    public void teardown()
    {
        if (container != null) {
            container.close();
            container = null;
        }
    }

    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(JdbcCatalogStoreConfig.class)
                .setDisabledCatalogs((String) null)
                .setCatalogConfigDbUrl((String) null)
                .setCatalogConfigDbUser((String) null)
                .setCatalogConfigDbPassword((String) null)
                .setReadOnly(false));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("catalog.config-db-url", container.getJdbcUrl())
                .put("catalog.config-db-user", container.getUsername())
                .put("catalog.config-db-password", container.getPassword())
                .put("catalog.disabled-catalogs", "abc,xyz")
                .put("catalog.read-only", "true")
                .buildOrThrow();

        JdbcCatalogStoreConfig expected = new JdbcCatalogStoreConfig()
                .setCatalogConfigDbUrl(container.getJdbcUrl())
                .setCatalogConfigDbUser(container.getUsername())
                .setCatalogConfigDbPassword(container.getPassword())
                .setDisabledCatalogs(ImmutableList.of("abc", "xyz"))
                .setReadOnly(true);

        assertFullMapping(properties, expected);
    }
}
