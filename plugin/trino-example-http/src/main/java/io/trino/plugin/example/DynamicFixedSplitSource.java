package io.trino.plugin.example;

import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedSplitSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.trino.spi.connector.Constraint;

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

        System.out.println("constraint.predicate()" + constraint.predicate());
        System.out.println("constraint.getSummary()" + constraint.getSummary());
        System.out.println("constraint.getExpression()" + constraint.getExpression());
        System.out.println("constraint.getExpression().get()" + constraint.predicate().get());
        System.out.println("constraint.getPredicateColumns()" + constraint.getPredicateColumns());
        System.out.println("constraint.getAssignments()" + constraint.getAssignments());

        if (dynamicFilter.isAwaitable()) {
            try {
                System.out.println(dynamicFilter.isBlocked().get(3, TimeUnit.SECONDS));
                System.out.println("dynamicFilter.getCurrentPredicate()" + dynamicFilter.getCurrentPredicate());
                System.out.println("dynamicFilter.getCurrentPredicate().getDomains()" + dynamicFilter.getCurrentPredicate().getDomains());
                System.out.println("dynamicFilter.isComplete()" + dynamicFilter.isComplete());
                System.out.println(splits);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
        return source.getNextBatch(maxSize);
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
