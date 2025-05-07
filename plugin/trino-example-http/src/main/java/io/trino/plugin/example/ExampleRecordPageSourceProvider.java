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

import com.google.common.io.Resources;
import io.trino.parquet.ParquetDataSource;
import io.trino.parquet.ParquetReaderOptions;
import io.trino.parquet.metadata.ParquetMetadata;
import io.trino.parquet.reader.MetadataReader;
import io.trino.parquet.reader.ParquetReader;
import io.trino.spi.connector.*;
import io.trino.spi.type.Type;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static io.trino.memory.context.AggregatedMemoryContext.newSimpleAggregatedMemoryContext;
import static io.trino.plugin.example.ParquetUtils.createParquetReader;
import static java.util.Objects.requireNonNull;

public class ExampleRecordPageSourceProvider
        implements ConnectorPageSourceProvider {
    private final ConnectorRecordSetProvider recordSetProvider;

    public ExampleRecordPageSourceProvider(ConnectorRecordSetProvider recordSetProvider) {
        this.recordSetProvider = requireNonNull(recordSetProvider, "recordSetProvider is null");
    }

    @Override
    public ConnectorPageSource createPageSource(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorSplit split,
            ConnectorTableHandle table,
            List<ColumnHandle> columns,
            DynamicFilter dynamicFilter) {


        // TODO: Create ParquetPageSource if is parquet file,
        if (split.getSplitInfo().containsKey("1")) {

            List<String> columnNames = columns.stream().map((columnHandle) -> {
                ExampleColumnHandle c = (ExampleColumnHandle) columnHandle;
                return c.getColumnName();
            }).toList();

            List<Type> types = columns.stream().map((columnHandle) -> {
                ExampleColumnHandle c = (ExampleColumnHandle) columnHandle;
                return c.getColumnType();
            }).toList();

            return getParquetPageSource(types, columnNames);
        }

        //
        return new RecordPageSource(recordSetProvider.getRecordSet(transaction, session, split, table, columns));
    }

    private static ParquetPageSource getParquetPageSource(List<Type> types, List<String> columnNames) {
        try {
            ParquetDataSource dataSource = new LocalFileParquetDataSource(
                    new File(Resources.getResource("int96_timestamps_nanos_outside_day_range.parquet").toURI()),
                    new ParquetReaderOptions());

            ParquetMetadata parquetMetadata = MetadataReader.readFooter(dataSource, Optional.empty());
            ParquetReader reader = createParquetReader(dataSource, parquetMetadata, newSimpleAggregatedMemoryContext(), types, columnNames);
            return new ParquetPageSource(reader);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
