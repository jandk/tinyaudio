package be.twofold.tinyogg;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

final class ByteIO {
    private static final VarHandle VH_INT_LE = MethodHandles
        .byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN)
        .withInvokeExactBehavior();
    private static final VarHandle VH_LONG_LE = MethodHandles
        .byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN)
        .withInvokeExactBehavior();

    private ByteIO() {
    }

    static int getInt(byte[] array, int offset) {
        return (int) VH_INT_LE.get(array, offset);
    }

    static long getLong(byte[] array, int offset) {
        return (long) VH_LONG_LE.get(array, offset);
    }
}
