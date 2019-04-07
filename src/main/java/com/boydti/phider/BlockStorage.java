package com.boydti.phider;

import org.apache.commons.lang.mutable.MutableInt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class BlockStorage {
    private static final byte AIR = 0x00;
    private final LinkedList<Byte> states;
    private final byte[] light;
    private int bitsPerEntry;
    private FlexibleStorage storage;

    BlockStorage(byte[] in, boolean sky) {
        MutableInt mut = new MutableInt();
        this.bitsPerEntry = readUnsigned(in, mut);

        this.states = new LinkedList<>();

        int stateCount = readVarInt(in, mut);
        int start = mut.toInteger();
        for (int i = start; i < start + stateCount; i++) {
            readVarInt(in, mut);
            byte state = in[i];
            this.states.add(state);
        }
        int expected = readVarInt(in, mut);
        this.storage = new FlexibleStorage(this.bitsPerEntry, readLongs(in, mut, expected));
        this.light = Arrays.copyOfRange(in, mut.intValue(), in.length);
    }

    private static int index(int x, int y, int z) {
        return y << 8 | z << 4 | (x);
    }

    void write(ByteArrayOutputStream out) throws IOException {
        long[] data = this.storage.getData();
        out.write(this.bitsPerEntry);
        writeVarInt(out, this.states.size());
        for (byte state : this.states) {
            writeBlockState(out, state);
        }
        writeVarInt(out, 1);
        writeVarInt(out, data.length);
        writeLongs(out, data);
        out.write(this.light);
    }

    byte get(int x, int y, int z) {
        byte id = this.storage.get(index(x, y, z));
        return this.bitsPerEntry <= 8 ?
            (id >= 0 && id < this.states.size() ? this.states.get(id) : AIR) :
            id;
    }

    void set(int x, int y, int z, byte state) {
        int id = this.bitsPerEntry <= 8 ? (state == 0 ? 0 : this.states.indexOf(state)) : state;
        if (id == -1) {
            this.states.add(state);
            if (this.states.size() > 1 << this.bitsPerEntry) {
                this.bitsPerEntry++;

                List<Byte> oldStates = this.states;
                if (this.bitsPerEntry > 8) {
                    oldStates = new ArrayList<>(this.states);
                    this.states.clear();
                    this.bitsPerEntry = 14;
                }

                FlexibleStorage oldStorage = this.storage;
                this.storage = new FlexibleStorage(this.bitsPerEntry, this.storage.getSize());
                for (int index = 0; index < this.storage.getSize(); index++) {
                    this.storage.set(index,
                        this.bitsPerEntry <= 8 ? oldStorage.get(index) : oldStates.get(index));
                }
            }

            id = this.bitsPerEntry <= 8 ? this.states.indexOf(state) : state;
        }
        this.storage.set(index(x, y, z), id);
    }

    public boolean equals(Object o) {
        return ((o instanceof BlockStorage)) && (this.bitsPerEntry
            == ((BlockStorage) o).bitsPerEntry) && (this.states.equals(((BlockStorage) o).states))
            && (this.storage.equals(((BlockStorage) o).storage));
    }

    public int hashCode() {
        int result = this.bitsPerEntry;
        result = 31 * result + this.states.hashCode();
        result = 31 * result + this.storage.hashCode();
        return result;
    }

    private byte read(byte[] bytes, MutableInt index) {
        byte value = bytes[index.intValue()];
        index.increment();
        return value;
    }

    private int readUnsigned(byte[] bytes, MutableInt index) {
        byte value = bytes[index.intValue()];
        index.increment();
        return value & 0xFF;
    }

    private long readLong(byte[] bytes, MutableInt index) {
        long value = 0;
        for (int i = 0, j = 56; i < 8; i++, j -= 8) {
            value += (bytes[i + index.intValue()] & 0xffL) << (j);
        }
        index.add(8);
        return value;
    }

    private int readVarInt(byte[] bytes, MutableInt index) {
        int value = 0;
        int size = 0;
        int b;
        while (((b = read(bytes, index)) & 0x80) == 0x80) {
            value |= (b & 0x7F) << (size++ * 7);
            if (size > 5) {
                return 1 / 0;
            }
        }

        return (value | ((b & 0x7F) << (size * 7)));
    }

    private long[] readLongs(byte[] bytes, MutableInt mut, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Array cannot have length less than 0.");
        }

        long l[] = new long[length];
        for (int index = 0; index < length; index++) {
            l[index] = readLong(bytes, mut);
        }

        return l;
    }

    private byte readBlockState(byte[] bytes, MutableInt index) {
        int rawId = readVarInt(bytes, index);
        return (byte) rawId;
    }

    private void writeVarInt(ByteArrayOutputStream out, int i) {
        while ((i & 0xFFFFFF80) != 0) {
            out.write(i & 0x7F | 0x80);
            i >>>= 7;
        }

        out.write(i);
    }

    private void writeBlockState(ByteArrayOutputStream out, byte blockState) {
        //writeVarInt(out, blockState);
        out.write(blockState);
    }

    private void writeLongs(ByteArrayOutputStream out, long[] l) {
        for (long aL : l) {
            writeLong(out, aL);
        }
    }

    private void writeLong(ByteArrayOutputStream out, long l) {
        out.write((byte) (int) (l >>> 56));
        out.write((byte) (int) (l >>> 48));
        out.write((byte) (int) (l >>> 40));
        out.write((byte) (int) (l >>> 32));
        out.write((byte) (int) (l >>> 24));
        out.write((byte) (int) (l >>> 16));
        out.write((byte) (int) (l >>> 8));
        out.write((byte) (int) (l));
    }
}
