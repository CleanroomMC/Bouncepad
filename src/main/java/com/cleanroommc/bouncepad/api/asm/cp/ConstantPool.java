package com.cleanroommc.bouncepad.api.asm.cp;

// TODO: implement reading of different types
// TODO: replacement/redirecting method helpers
public class ConstantPool implements ConstantPoolTags {

    private static final int MAGIC_NUMBER = 0xCAFEBABE;
    private static final int CP_ITEM_START = 8;
    private static final int CP_START = 10;

    protected final byte[] classData;
    protected final int items;
    protected final int[] offsets;
    
    public ConstantPool(byte[] classData) {
        if (classData == null || classData.length < 4) {
            throw new IllegalArgumentException("classData is null, empty or incomplete!");
        }
        if (this.readInt(0) != MAGIC_NUMBER) {
            throw new IllegalArgumentException("classData does not represent a valid class file!");
        }
        this.classData = classData;
        this.items = this.readUnsignedShort(CP_ITEM_START);
        this.offsets = new int[this.items];
        this.read();
    }

    private void read() {
        int pointer = CP_START;
        for (int i = 1; i < this.offsets.length; i++) {
            int tag = this.readByte(pointer++);
            this.offsets[i] = tag;
            switch (tag) {
                case CLASS, METHOD_TYPE, MODULE, STRING, PACKAGE -> pointer += 2;
                case METHOD_HANDLE -> pointer += 3;
                case CONSTANT_DYNAMIC, FIELD_REF, FLOAT, INTEGER, INTERFACE_METHOD_REF, INVOKE_DYNAMIC, METHOD_REF, NAME_AND_TYPE -> pointer += 4;
                case LONG, DOUBLE -> { // Longs and Doubles take 2 "slots"
                    pointer += 8;
                    i++;
                }
                case UTF8 -> {
                    int length = this.readUnsignedShort(pointer);
                    pointer += (2 + length);
                }
                default -> throw new IllegalStateException("Bad tag (" + tag + ") @ index (" + i + ") @ position (" + pointer + ")");
            }
        }
    }

    public int readByte(int pointer) {
        return this.classData[pointer] & 0xFF;
    }

    public int readUnsignedShort(int pointer) {
        return ((this.classData[pointer] & 0xFF) << 8) | (this.classData[pointer + 1] & 0xFF);
    }

    public int readSignedShort(int pointer) {
        return (short) ((this.classData[pointer] & 0xFF) << 8) | (this.classData[pointer + 1] & 0xFF);
    }

    public int readInt(int pointer) {
        return ((this.classData[pointer] & 0xFF) << 24) |
                ((this.classData[pointer + 1] & 0xFF) << 16) |
                ((this.classData[pointer + 2] & 0xFF) << 8) |
                (this.classData[pointer + 3] & 0xFF);
    }

    public long readLong(int pointer) {
        return ((long) this.readInt(pointer) << 32) | (this.readInt(pointer + 4) & 0xFFFFFFFFL);
    }

    public float readFloat(int pointer) {
        return Float.intBitsToFloat(this.readInt(pointer));
    }

    public double readDouble(int pointer) {
        return Double.longBitsToDouble(this.readLong(pointer));
    }

}
