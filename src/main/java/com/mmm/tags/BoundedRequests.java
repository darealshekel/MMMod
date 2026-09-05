package com.mmm.tags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

final class BoundedRequests
{
    private BoundedRequests() {}

    static <T, R> CompletableFuture<List<R>> map(List<T> input, int concurrency,
                                                Function<T, CompletableFuture<R>> request)
    {
        if (concurrency < 1)
        {
            throw new IllegalArgumentException("concurrency must be positive");
        }
        AtomicReferenceArray<R> results = new AtomicReferenceArray<>(input.size());
        List<CompletableFuture<Void>> lanes = new ArrayList<>();
        for (int lane = 0; lane < Math.min(concurrency, input.size()); lane++)
        {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int index = lane; index < input.size(); index += concurrency)
            {
                int position = index;
                chain = chain.thenCompose(ignored -> request.apply(input.get(position)))
                        .thenAccept(result -> results.set(position, result));
            }
            lanes.add(chain);
        }
        return CompletableFuture.allOf(lanes.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<R> ordered = new ArrayList<>(input.size());
            for (int index = 0; index < input.size(); index++)
            {
                ordered.add(results.get(index));
            }
            return ordered;
        });
    }
}
