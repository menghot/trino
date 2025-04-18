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

public class DynamicFixedSplitSource implements ConnectorSplitSource {

    private final FixedSplitSource source;
    private final DynamicFilter dynamicFilter;
    private final Constraint constraint;
    private List<ConnectorSplit> splits;

    public DynamicFixedSplitSource(
            FixedSplitSource source,
            DynamicFilter dynamicFilter,
            List<ConnectorSplit> splits,
            Constraint constraint) {

        this.source = source;
        this.dynamicFilter = dynamicFilter;
        this.splits = splits;
        this.constraint = constraint;
    }

    @Override
    public CompletableFuture<ConnectorSplitBatch> getNextBatch(int maxSize) {

        extracted();

        if (dynamicFilter != null && dynamicFilter.isAwaitable()) {
            try {
                System.out.println(dynamicFilter.isBlocked().get());
                System.out.println("dynamicFilter.getCurrentPredicate()" + dynamicFilter.getCurrentPredicate());
                System.out.println("dynamicFilter.getCurrentPredicate().getDomains()" + dynamicFilter.getCurrentPredicate().getDomains().get().values());
                System.out.println("dynamicFilter.isComplete()" + dynamicFilter.isComplete());
                System.out.println(splits);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("DYNAMIC FILTER NOT-SUPPORTED", e);
            }
        }
        return source.getNextBatch(maxSize);
    }

    private void extracted() {
        if (constraint != null && constraint.predicate().isPresent()) {
            try {
                for (Field f : constraint.predicate().get().getClass().getDeclaredFields()) {
                    if("arg$1".equals(f.getName())) {
                        f.setAccessible(true);
                        LayoutConstraintEvaluator evaluator = (LayoutConstraintEvaluator) f.get(constraint.predicate().get());
                        Field ef = evaluator.getClass().getDeclaredFields()[3];
                        ef.setAccessible(true);
                        Expression expression = (Expression) ef.get(evaluator);
                        if (expression instanceof Comparison comparison) {
                            System.out.println(comparison);
                            if(comparison.operator().equals(EQUAL)){
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
                            System.out.println(in);
                        }

                        System.out.println(expression);
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
