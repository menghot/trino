package io.trino.plugin.example;

import io.airlift.slice.Slice;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedSplitSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.trino.spi.connector.Constraint;
import io.trino.sql.ir.*;
import io.trino.sql.planner.LayoutConstraintEvaluator;

import static io.trino.sql.ir.Comparison.Operator.EQUAL;

public class ExampleSplitSource implements ConnectorSplitSource {

    private final FixedSplitSource source;
    private final DynamicFilter dynamicFilter;
    private final Constraint constraint;
    private final List<ConnectorSplit> splits;

    public ExampleSplitSource(
            DynamicFilter dynamicFilter,
            List<ConnectorSplit> splits,
            Constraint constraint) {

        this.dynamicFilter = dynamicFilter;
        this.constraint = constraint;
        this.splits = splits;
        this.source = new FixedSplitSource(splits);

    }

    @Override
    public CompletableFuture<ConnectorSplitBatch> getNextBatch(int maxSize) {
        //
        applyConstraint();

        //
        applyDynamicFilter();
        return source.getNextBatch(maxSize);
    }

    private void applyDynamicFilter() {
        if (dynamicFilter != null && dynamicFilter.isAwaitable()) {
            try {
                dynamicFilter.isBlocked().get();
                System.out.println(splits);
                if (dynamicFilter.getCurrentPredicate().getDomains().isPresent()) {
                    dynamicFilter.getCurrentPredicate().getDomains().get().forEach((handle, domain) -> {
                        // Support dynamic filter push down.
                        // __file_path (support more splits)
                        // __http_params
                        // __http_header
                        // __http_body

                        // Support http_params dynamic filter
                        if (handle instanceof ExampleColumnHandle columnHandle
                                && columnHandle.getColumnName().equals("__http_params")) {

                            // Fill dynamic params to split
                            splits.iterator().forEachRemaining(split -> {
                                StringBuilder sb = new StringBuilder();
                                domain.getValues().getRanges().getOrderedRanges().iterator().forEachRemaining(r -> {
                                    if (r.isSingleValue()) {
                                        if (sb.isEmpty()) {
                                            sb.append(r.getSingleValue());
                                        } else {
                                            sb.append("&").append(r.getSingleValue());
                                        }
                                    } else {
                                        throw new RuntimeException("Doesn't support dynamic push-down for the column: " + columnHandle.getColumnName());
                                    }
                                });

                                //
                                split.getSplitInfo().putIfAbsent("http_params", sb.toString());
                            });
                        }
                    });
                }

            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("DYNAMIC FILTER NOT-SUPPORTED", e);
            }
        }
    }

    private void applyConstraint() {
        if (constraint != null && constraint.predicate().isPresent()) {
            // Support constraint filter push down
            // __file_path  (support more splits)
            // __http_params
            // __http_header
            // __http_body

            try {
                for (Field field : constraint.predicate().get().getClass().getDeclaredFields()) {
                    if ("arg$1".equals(field.getName())) {
                        field.setAccessible(true);
                        LayoutConstraintEvaluator evaluator = (LayoutConstraintEvaluator) field.get(constraint.predicate().get());

                        if (!evaluator.getArguments().stream().toList().isEmpty() &&
                                evaluator.getArguments().stream().toList().getFirst() instanceof ExampleColumnHandle columnHandle) {

                            System.out.println(columnHandle.getColumnName());

                            Field ef = evaluator.getClass().getDeclaredFields()[3];
                            ef.setAccessible(true);

                            if (evaluator.getArguments().stream().toList().isEmpty()) {
                                throw new RuntimeException("");
                            }

                            Expression expression = (Expression) ef.get(evaluator);
                            if (expression instanceof Comparison comparison) {
                                if (comparison.operator().equals(EQUAL)) {
                                    if (comparison.left() instanceof Reference reference) {
                                        System.out.println(reference.name());
                                    }

                                    if (comparison.right() instanceof Constant constant) {
                                        if (constant.value() instanceof Slice s) {
                                            System.out.println(s.toStringUtf8());
                                        }
                                    }
                                }
                            } else if (expression instanceof In in) {
                                for (Expression exp : in.valueList()) {
                                    if (exp instanceof Constant constant) {
                                        if (constant.value() instanceof Slice s) {
                                            System.out.println(s.toStringUtf8());
                                        }
                                    }
                                }
                            }
                        }
                    }
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
