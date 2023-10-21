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
package io.trino.plugin.http;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.mongo.dtp.metadata.api.Column;
import com.mongo.dtp.metadata.api.MetadataServiceClient;
import com.mongo.dtp.metadata.api.Table;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import io.airlift.json.JsonCodec;
import io.trino.spi.type.TypeManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Maps.transformValues;
import static com.google.common.collect.Maps.uniqueIndex;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ExampleClient
{

    private String catalogName;

    public String getCatalogName()
    {
        return catalogName;
    }

    private TypeManager typeManager;

    public TypeManager getTypeManager()
    {
        return typeManager;
    }

    public void setTypeManager(TypeManager typeManager)
    {
        this.typeManager = typeManager;
    }

    public void setCatalogName(String catalogName)
    {
        this.catalogName = catalogName;
    }

    /**
     * SchemaName -> (TableName -> TableMetadata)
     */
    private final Supplier<Map<String, Map<String, ExampleTable>>> schemas;

    private MetadataServiceClient metadataServiceClient;

    @Inject
    public ExampleClient(ExampleConfig config, JsonCodec<Map<String, List<ExampleTable>>> catalogCodec)
    {
        requireNonNull(catalogCodec, "catalogCodec is null");
        schemas = Suppliers.memoize(schemasSupplier(catalogCodec, config.getMetadata()));
        metadataServiceClient = Feign.builder().decoder(new JacksonDecoder()).target(MetadataServiceClient.class, "http://127.0.0.1:8081");
    }

    public Set<String> getSchemaNames()
    {
        return metadataServiceClient.getSchemas(catalogName).stream().map(schema -> schema.getName()).collect(Collectors.toSet());
    }

    public Set<String> getTableNames(String schema)
    {
        requireNonNull(schema, "schema is null");
        return metadataServiceClient.getTables(this.catalogName, schema).stream().map(t -> t.getName()).collect(Collectors.toSet());
    }

    public ExampleTable getTable(String schema, String tableName)
    {

        Table table = metadataServiceClient.getTable(catalogName, schema, tableName);

        List<ExampleColumn> columns = new ArrayList<>();
        for (Column column : table.getColumns()) {
            ExampleColumn exampleColumn = new ExampleColumn(column.getName(), typeManager.fromSqlType(column.getTypeName()));
            columns.add(exampleColumn);
        }

        List<URI> sources = new ArrayList();
        try {
            sources.add(new URI(table.getProperties().get("http.url")));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return new ExampleTable(tableName, columns, sources);
    }

    private static Supplier<Map<String, Map<String, ExampleTable>>> schemasSupplier(JsonCodec<Map<String, List<ExampleTable>>> catalogCodec, URI metadataUri)
    {
        return () -> {
            try {
                return lookupSchemas(metadataUri, catalogCodec);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * @param metadataUri
     * @param catalogCodec
     * @return key schema name
     * value Map: key: table name
     * value: ExampleTable
     * @throws IOException
     */
    private static Map<String, Map<String, ExampleTable>> lookupSchemas(URI metadataUri, JsonCodec<Map<String, List<ExampleTable>>> catalogCodec)
            throws IOException
    {

        java.util.function.Supplier<MetadataServiceClient> supplier = () -> Feign.builder().decoder(new JacksonDecoder()).target(MetadataServiceClient.class, "http://127.0.0.1:8081");

        supplier.get().getCatalogs().iterator().forEachRemaining(c -> System.out.println(c.getName()));

        URL result = metadataUri.toURL();
        String json = Resources.toString(result, UTF_8);
        Map<String, List<ExampleTable>> catalog = catalogCodec.fromJson(json);

        return ImmutableMap.copyOf(transformValues(catalog, resolveAndIndexTables(metadataUri)));
    }

    private static Function<List<ExampleTable>, Map<String, ExampleTable>> resolveAndIndexTables(URI metadataUri)
    {
        return tables -> {
            Iterable<ExampleTable> resolvedTables = transform(tables, tableUriResolver(metadataUri));
            return ImmutableMap.copyOf(uniqueIndex(resolvedTables, ExampleTable::getName));
        };
    }

    private static Function<ExampleTable, ExampleTable> tableUriResolver(URI baseUri)
    {
        return table -> {
            List<URI> sources = ImmutableList.copyOf(transform(table.getSources(), baseUri::resolve));
            return new ExampleTable(table.getName(), table.getColumns(), sources);
        };
    }
}
