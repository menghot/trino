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

import io.airlift.testing.EquivalenceTester;
import org.testng.annotations.Test;

import static io.trino.plugin.example.MetadataUtil.COLUMN_CODEC;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.VarcharType.createUnboundedVarcharType;
import static org.testng.Assert.assertEquals;

public class TestHttpColumnHandle {
    private final HttpColumnHandle columnHandle = new HttpColumnHandle("columnName", createUnboundedVarcharType(), 0);

    @Test
    public void testJsonRoundTrip() {
        String json = COLUMN_CODEC.toJson(columnHandle);
        HttpColumnHandle copy = COLUMN_CODEC.fromJson(json);
        assertEquals(copy, columnHandle);
    }

    @Test
    public void testEquivalence() {
        EquivalenceTester.equivalenceTester()
                .addEquivalentGroup(
                        new HttpColumnHandle("columnName", createUnboundedVarcharType(), 0),
                        new HttpColumnHandle("columnName", BIGINT, 0),
                        new HttpColumnHandle("columnName", createUnboundedVarcharType(), 1))
                .addEquivalentGroup(
                        new HttpColumnHandle("columnNameX", createUnboundedVarcharType(), 0),
                        new HttpColumnHandle("columnNameX", BIGINT, 0),
                        new HttpColumnHandle("columnNameX", createUnboundedVarcharType(), 1))
                .check();
    }
}
