package org.neo4j.graphalgo.core.huge;

import org.neo4j.graphalgo.api.HugeWeightMapping;
import org.neo4j.graphalgo.core.loading.ReadHelper;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.internal.kernel.api.CursorFactory;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.RelationshipScanCursor;

import java.util.Arrays;

class HugeWeightMapBuilder {
    private final AllocationTracker tracker;

    private final int weightProperty;
    private final double defaultWeight;

    private HugeWeightMap.Page[] pages;
    private HugeWeightMap.Page page;

    HugeWeightMapBuilder(AllocationTracker tracker, int weightProperty, double defaultWeight) {
        this.tracker = tracker;
        this.weightProperty = weightProperty;
        this.defaultWeight = defaultWeight;
    }

    private HugeWeightMapBuilder(
            AllocationTracker tracker,
            int weightProperty,
            double defaultWeight,
            HugeWeightMap.Page page) {
        this.tracker = tracker;
        this.weightProperty = weightProperty;
        this.defaultWeight = defaultWeight;
        this.page = page;
    }

    void prepare(int numberOfPages) {
        pages = new HugeWeightMap.Page[numberOfPages];
    }

    HugeWeightMapBuilder threadLocalCopy(int threadIndex, int batchSize) {
        HugeWeightMap.Page page = new HugeWeightMap.Page(batchSize, tracker);
        pages[threadIndex] = page;
        return new HugeWeightMapBuilder(tracker, weightProperty, defaultWeight, page);
    }

    void finish(int numberOfPages) {
        if (numberOfPages < pages.length) {
            pages = Arrays.copyOf(pages, numberOfPages);
        }
    }

    HugeWeightMapping build() {
        return new HugeWeightMap(pages, defaultWeight, tracker);
    }

    void load(long source, long target, int localSource, CursorFactory cursors, Read read) {
        try (RelationshipScanCursor rsc = cursors.allocateRelationshipScanCursor();
             PropertyCursor pc = cursors.allocatePropertyCursor()) {
            read.singleRelationship(source, rsc);
            while (rsc.next()) {
                rsc.properties(pc);
                double weight = ReadHelper.readProperty(pc, weightProperty, defaultWeight);
                if (weight != defaultWeight) {
                    page.put(localSource, target, weight);
                }
            }
        }
    }

    static class NullBuilder extends HugeWeightMapBuilder {

        private final double defaultValue;

        NullBuilder(double defaultValue) {
            super(AllocationTracker.EMPTY, -1, defaultValue);
            this.defaultValue = defaultValue;
        }

        @Override
        void prepare(final int numberOfPages) {
        }


        @Override
        HugeWeightMapBuilder threadLocalCopy(final int threadIndex, final int batchSize) {
            return this;
        }

        @Override
        void finish(final int numberOfPages) {
        }

        @Override
        HugeWeightMapping build() {
            return new HugeNullWeightMap(defaultValue);
        }

        @Override
        void load(long source, long target, int localSource, CursorFactory cursors, Read read) {
        }
    }
}
