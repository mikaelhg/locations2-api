package io.mikael.loc2;

import com.google.common.base.Splitter;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.UnsignedBytes;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class EdgeCodec {

    private static final Splitter.MapSplitter TO_MAP =
            Splitter.on('\000').omitEmptyStrings().trimResults().withKeyValueSeparator('=');

    private static final int HEADER_LENGTH = 8;

    public static void request(final ByteBuffer bb, final long number, final String ip) {
        bb.put(UnsignedBytes.checkedCast(3L)); // version
        bb.put(UnsignedBytes.checkedCast(0L)); // flags
        bb.put(Shorts.toByteArray(Shorts.checkedCast(number))); // number
        bb.put(Shorts.toByteArray(Shorts.checkedCast(bb.limit()))); // length
        bb.put(UnsignedBytes.checkedCast(0L)); // reserved1
        bb.put(UnsignedBytes.checkedCast(0L)); // reserved2
        bb.put(ip.getBytes(StandardCharsets.US_ASCII));
        bb.put(UnsignedBytes.checkedCast(0L)); // end of string null
    }

    public static Map<String, String> response(
            final ByteBuffer buffer, final int requestNumber, final int ipLength)
    {
        buffer.get(); // version
        buffer.get(); // flags
        final short number = buffer.getShort();
        if (number != requestNumber) {
            return null;
        }
        final short length = buffer.getShort();
        final byte error = buffer.get();
        if (error > 0) {
            return null;
        }
        buffer.get(); // reserved
        final byte[] data = new byte[length - HEADER_LENGTH];
        buffer.get(data);
        return TO_MAP.split(new String(data, ipLength, length - HEADER_LENGTH - ipLength, StandardCharsets.ISO_8859_1));
    }

}
