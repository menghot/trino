package io.trino.plugin.example;

import io.airlift.slice.Slice;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedSplitSource;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.trino.spi.connector.Constraint;
import io.trino.sql.ir.*;
import io.trino.sql.planner.LayoutConstraintEvaluator;

import static io.trino.sql.ir.Comparison.Operator.EQUAL;

public class ExampleSplitSource implements ConnectorSplitSource {

    private FixedSplitSource source;
    private final DynamicFilter dynamicFilter;
    private final Constraint constraint;
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
            DynamicFilter dynamicFilter,
            Constraint constraint) {

        this.table = table;
        this.dynamicFilter = dynamicFilter;
        this.constraint = constraint;
        this.splits = new ArrayList<>();
        this.properties = new HashMap<>();
    }

    @Override
    public synchronized CompletableFuture<ConnectorSplitBatch> getNextBatch(int maxSize) {
        if (source == null) {
            // Extract split info from constraint and dynamic filter
            extractSplitsFromConstraint();
            extractSplitsFromDynamicFilter();

            // No split generate from dynamic filter and constraint.
            // Loop "location" from table properties
            if (splits.isEmpty()) {
                extractSplitFromTableProperties();
            }

            Collections.shuffle(splits);
            source = new FixedSplitSource(splits);
        }
        return source.getNextBatch(maxSize);
    }


    private void extractSplitFromTableProperties() {
        for (URI uri : table.getSources()) {
            splits.add(new ExampleSplit(uri.toString(), properties));
        }
    }


    // Support dynamic filter for "$data_uri", only below example supported
    // 1. "$data_uri" in (select path * from x)
    // 2. "$data_uri" = (select path * from x )
    private void extractSplitsFromDynamicFilter() {
        if (dynamicFilter != null) {
            try {
                dynamicFilter.isBlocked().get();
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

            } catch (InterruptedException e) {
                throw new RuntimeException("DYNAMIC FILTER NOT-SUPPORTED", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("DYNAMIC FILTER NOT-SUPPORTED e", e);
            }
        }
    }

    // Support constraint filter push down for internal columns
// $data_uri
// $params
// $http_header
// $http_body
    private void extractSplitsFromConstraint() {
        if (constraint != null && constraint.predicate().isPresent()) {
            Map<String, String> splitInfos = new HashMap<>();
            try {
                for (Field field : constraint.predicate().get().getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    LayoutConstraintEvaluator evaluator = (LayoutConstraintEvaluator) field.get(constraint.predicate().get());

                    evaluator.getArguments().iterator().forEachRemaining(arg -> {
                        if (arg instanceof ExampleColumnHandle columnHandle) {
                            if (internalColumns.contains(columnHandle.getColumnName())) {
                                Field ef = evaluator.getClass().getDeclaredFields()[3];
                                ef.setAccessible(true);
                                Expression expression = null;
                                try {
                                    expression = (Expression) ef.get(evaluator);
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                                if (expression instanceof Comparison comparison) {
                                    if (comparison.operator().equals(EQUAL)) {
                                        if (comparison.right() instanceof Constant constant) {
                                            if (constant.value() instanceof Slice s) {
                                                splitInfos.putIfAbsent(columnHandle.getColumnName(), s.toStringUtf8());
                                            } else {
                                                throw new RuntimeException("FOR INTERNAL COLUMNS, STRING SUPPORTED ONLY");
                                            }
                                        } else {
                                            throw new RuntimeException("FOR INTERNAL COLUMNS, Constant SUPPORTED ONLY");
                                        }
                                    } else {
                                        throw new RuntimeException("FOR INTERNAL COLUMNS, EQUAL SUPPORTED ONLY");
                                    }
                                } else {
                                    throw new RuntimeException("FOR INTERNAL COLUMNS, COMPARISON SUPPORTED ONLY");
                                }
                            } else if (ExampleInternalColumn.DATA_URI.getName().equals(columnHandle.getColumnName())) {
                                Field ef = evaluator.getClass().getDeclaredFields()[3];
                                ef.setAccessible(true);
                                Expression expression = null;
                                try {
                                    expression = (Expression) ef.get(evaluator);
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                                if (expression instanceof Comparison comparison) {
                                    if (comparison.operator().equals(EQUAL)) {
                                        if (comparison.right() instanceof Constant constant) {
                                            if (constant.value() instanceof Slice s) {
                                                splits.add(new ExampleSplit(s.toStringUtf8(), splitInfos));
                                            } else {
                                                throw new RuntimeException("1");
                                            }
                                        } else {
                                            throw new RuntimeException("2");
                                        }
                                    } else {
                                        throw new RuntimeException("3");
                                    }
                                } else if (expression instanceof In in) {
                                    for (Expression exp : in.valueList()) {
                                        if (exp instanceof Constant constant) {
                                            if (constant.value() instanceof Slice s) {
                                                splits.add(new ExampleSplit(s.toStringUtf8(), splitInfos));
                                            } else {
                                                throw new RuntimeException("4");
                                            }
                                        } else {
                                            throw new RuntimeException("5");
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
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


// 1. apply $params from dynamic filter or constraint
// 2. apply $location , http_header, http_body from dynamic filter or constraint, replace placeholders by $params
// 3. access location to generate splits.  check if need to get multiple split from location. generate splits.

// $location
// $params
// $http_header
// $http_body

// single value:   $http_header, $http_body, $params
// single or multiple values:  $location
