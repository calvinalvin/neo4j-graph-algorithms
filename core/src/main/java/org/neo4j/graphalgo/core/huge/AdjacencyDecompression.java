/**
 * Copyright (c) 2017 "Neo4j, Inc." <http://neo4j.com>
 *
 * This file is part of Neo4j Graph Algorithms <http://github.com/neo4j-contrib/neo4j-graph-algorithms>.
 *
 * Neo4j Graph Algorithms is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.core.huge;

import org.neo4j.graphalgo.core.huge.AdjacencyCompression.IntValue;

import java.util.Arrays;

import static org.neo4j.graphalgo.core.huge.AdjacencyCompression.CHUNK_SIZE;
import static org.neo4j.graphalgo.core.huge.VarLongDecoding.decodeDeltaVLongs;

final class AdjacencyDecompression {

    private final long[] block;
    private int pos;
    private byte[] array;
    private int offset;

    AdjacencyDecompression() {
        this.block = new long[CHUNK_SIZE];
    }

    void copyFrom(AdjacencyDecompression other) {
        System.arraycopy(other.block, 0, block, 0, CHUNK_SIZE);
        pos = other.pos;
        array = other.array;
        offset = other.offset;
    }

    int reset(byte[] array, int offset) {
        this.array = array;
        this.offset = 4 + offset;
        block[CHUNK_SIZE - 1] = 0L;
        pos = CHUNK_SIZE;
        return readInt(array, offset);
    }

    //@formatter:off
    private int readInt(byte[] array, int offset) {
        return   array[    offset] & 255        |
                (array[1 + offset] & 255) <<  8 |
                (array[2 + offset] & 255) << 16 |
                (array[3 + offset] & 255) << 24;
    }
    //@formatter:on

    long next(int remaining) {
        int pos = this.pos++;
        if (pos < CHUNK_SIZE) {
            return block[pos];
        }
        return readNextBlock(remaining);
    }

    private long readNextBlock(int remaining) {
        pos = 1;
        offset = decodeDeltaVLongs(block[CHUNK_SIZE - 1], array, offset, Math.min(remaining, CHUNK_SIZE), block);
        return block[0];
    }

    long skipUntil(long target, int remaining, IntValue consumed) {
        int pos = this.pos;
        long[] block = this.block;
        int available = remaining;

        while (CHUNK_SIZE - available < pos && block[CHUNK_SIZE - 1] <= target) {
            offset = decodeDeltaVLongs(block[CHUNK_SIZE - 1], array, offset, CHUNK_SIZE - pos, block);
            available -= (CHUNK_SIZE - pos);
            pos = 0;
        }

        // last block
        int targetPos = findPosStrictlyGreaterInBlock(target, pos, Math.min(pos + available, CHUNK_SIZE), block);
        available -= (1 + targetPos - pos);
        consumed.value = 1 + remaining - available;
        this.pos = 1 + targetPos;
        return block[targetPos];
    }

    long advance(long target, int remaining, IntValue consumed) {
        int pos = this.pos;
        long[] block = this.block;
        int available = remaining;

        while (CHUNK_SIZE - available < pos && block[CHUNK_SIZE - 1] < target) {
            offset = decodeDeltaVLongs(block[CHUNK_SIZE - 1], array, offset, CHUNK_SIZE - pos, block);
            available -= (CHUNK_SIZE - pos);
            pos = 0;
        }

        // last block
        int targetPos = findPosInBlock(target, pos, Math.min(pos + available, CHUNK_SIZE), block);
        available -= (1 + targetPos - pos);
        consumed.value = 1 + remaining - available;
        this.pos = 1 + targetPos;
        return block[targetPos];
    }

    private int findPosStrictlyGreaterInBlock(long target, int pos, int limit, long[] block) {
        return findPosInBlock(1L + target, pos, limit, block);
    }

    private int findPosInBlock(long target, int pos, int limit, long[] block) {
        int targetPos = Arrays.binarySearch(block, pos, limit, target);
        if (targetPos < 0) {
            targetPos = Math.min(-1 - targetPos, -1 + limit);
        }
        return targetPos;
    }
}
