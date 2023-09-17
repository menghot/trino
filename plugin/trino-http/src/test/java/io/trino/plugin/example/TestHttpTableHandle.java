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

import io.airlift.json.JsonCodec;
import io.airlift.testing.EquivalenceTester;
import org.testng.annotations.Test;

import static io.airlift.json.JsonCodec.jsonCodec;
import static org.testng.Assert.assertEquals;

public class TestHttpTableHandle {
    private final HttpTableHandle tableHandle = new HttpTableHandle("schemaName", "tableName");

    @Test
    public void testJsonRoundTrip() {
        JsonCodec<HttpTableHandle> codec = jsonCodec(HttpTableHandle.class);
        String json = codec.toJson(tableHandle);
        HttpTableHandle copy = codec.fromJson(json);
        assertEquals(copy, tableHandle);
    }

    @Test
    public void testEquivalence() {
        EquivalenceTester.equivalenceTester()
                .addEquivalentGroup(new HttpTableHandle("schema", "table"), new HttpTableHandle("schema", "table"))
                .addEquivalentGroup(new HttpTableHandle("schemaX", "table"), new HttpTableHandle("schemaX", "table"))
                .addEquivalentGroup(new HttpTableHandle("schema", "tableX"), new HttpTableHandle("schema", "tableX"))
                .check();
    }
}
