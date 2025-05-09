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
package io.trino.plugin.example;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.*;

public class TestHttpConfig {
    @Test
    public void testDefaults() {
        assertRecordedDefaults(recordDefaults(HttpConfig.class)
                .setMetadata(null));
    }

    @Test
    public void testExplicitPropertyMappings() {
        Map<String, String> properties = ImmutableMap.of("metadata-uri", "file://test.json");

        HttpConfig expected = new HttpConfig()
                .setMetadata(URI.create("file://test.json"));

        assertFullMapping(properties, expected);
    }
}
