package io.trino.plugin.example;

import io.airlift.slice.Slice;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedSplitSource;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ExampleSplitSource implements ConnectorSplitSource {

    private FixedSplitSource source;
    private final DynamicFilter dynamicFilter;
    private final List<ConnectorSplit> splits;

    //Split properties
    private final Map<String, String> properties;
    private final ExampleTable table;

    private final Set<String> internalColumns = Set.of(
            ExampleInternalColumn.HTTP_URL.getName(),
            ExampleInternalColumn.HTTP_HEADER.getName(),
            ExampleInternalColumn.HTTP_BODY.getName(),
            ExampleInternalColumn.PARAMS.getName()
    );

    public ExampleSplitSource(
            ExampleTable table,
            ExampleTableHandle exampleTableHandle,
            DynamicFilter dynamicFilter) {

        this.table = table;
        this.dynamicFilter = dynamicFilter;
        this.splits = new ArrayList<>();
        this.properties = new HashMap<>();
    }

    @Override
    public synchronized CompletableFuture<ConnectorSplitBatch> getNextBatch(int maxSize) {
        if (source == null) {

            extractSplitsFromDynamicFilter();

            // No split generate from dynamic filter and constraint.
            // Loop "location" from table properties
            if (splits.isEmpty()) {
                extractSplitFromDefaultTableProperties();
            }

            Collections.shuffle(splits);
            source = new FixedSplitSource(splits);
        }
        return source.getNextBatch(maxSize);
    }


    private void extractSplitFromDefaultTableProperties() {
        for (URI uri : table.getSources()) {
            splits.add(new ExampleSplit(uri.toString(), properties));
        }
    }


    // Support dynamic filter for "$data_uri", only below example supported
    // 1. "$data_uri" in (select path * from x)
    // 2. "$data_uri" = (select path * from x )
    private void extractSplitsFromDynamicFilter() {
        if (dynamicFilter == null) {
            return;
        }
        while (!dynamicFilter.isComplete()) {
            if (dynamicFilter.isAwaitable()) {
                try {
                    dynamicFilter.isBlocked().get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (dynamicFilter.getCurrentPredicate().getDomains().isPresent()) {
            dynamicFilter.getCurrentPredicate().getDomains().get().forEach((handle, domain) -> {
                if (handle instanceof ExampleColumnHandle columnHandle
                        && columnHandle.getColumnName().equals(ExampleInternalColumn.DATA_URI.getName())) {
                    domain.getValues().getRanges().getOrderedRanges().iterator().forEachRemaining(r -> {
                        if (r.isSingleValue() && r.getSingleValue() instanceof Slice s) {
                            splits.add(new ExampleSplit(s.toStringUtf8(), properties));
                        } else {
                            throw new RuntimeException("$data_uri is not a single value or string value");
                        }
                    });
                }
            });
        }
    }


    @Override
    public void close() {
        source.close();
    }

    @Override
    public boolean isFinished() {
        return source.isFinished();
    }

    @Override
    public Optional<List<Object>> getTableExecuteSplitsInfo() {
        return this.source.getTableExecuteSplitsInfo();
    }
}
