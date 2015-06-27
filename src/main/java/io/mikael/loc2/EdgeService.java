package io.mikael.loc2;

import com.google.common.base.Splitter;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.UnsignedBytes;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.commons.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

@Service
@CacheConfig(cacheNames = "ips")
public class EdgeService {

    private static final Logger log = LoggerFactory.getLogger(EdgeService.class);

    private static final Splitter.MapSplitter TO_MAP =
            Splitter.on('\000').omitEmptyStrings().trimResults().withKeyValueSeparator('=');

    private static final int HEADER_LENGTH = 8;

    @Value("${app.edge.host:localhost}")
    private String edgeHost = "";

    @Value("${app.edge.port:2001}")
    private int edgePort;

    @Value("${app.edge.timeout:5000}")
    private int edgeTimeout;

    private volatile int counter = 0;

    private synchronized int nextNumber() {
        if (counter > 65000) {
            counter = 0;
        }
        return counter++;
    }

    @Cacheable
    public Optional<Map<String, String>> fetchDataForIp(final String ip) {
        log.debug("Fetching data for ip {}", ip);
        final int number = nextNumber();
        byte[] bytes = new byte[2048];
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.clear();
        request(buffer, number, ip);
        buffer.flip();

        try (final DatagramSocket sock = new DatagramSocket()) {
            final InetSocketAddress endpoint = new InetSocketAddress(edgeHost, edgePort);
            sock.connect(endpoint);
            sock.setSoTimeout(edgeTimeout);
            sock.setReuseAddress(true);
            sock.setTrafficClass(4);

            sock.send(new DatagramPacket(buffer.array(), buffer.limit(), endpoint));
            buffer.clear();
            final DatagramPacket responsePacket = new DatagramPacket(buffer.array(), buffer.limit(), endpoint);
            sock.receive(responsePacket);

            buffer.position(0);
            return response(buffer, number, ip);
        } catch (PortUnreachableException e) {
            log.error("could not reach the server to make the initial request, make sure it's available in the address you have configured", e);
        } catch (SocketTimeoutException e) {
            log.error("UDP receive from server timed out", e);
        } catch (Exception e) {
            log.error("probably a socket error, check your configuration values", e);
        }
        return null;
    }

    private static void request(final ByteBuffer bb, final long number, final String ip) {
        bb.put(UnsignedBytes.checkedCast(3L)); // version
        bb.put(UnsignedBytes.checkedCast(0L)); // flags
        bb.put(Shorts.toByteArray(Shorts.checkedCast(number))); // number
        bb.put(Shorts.toByteArray(Shorts.checkedCast(bb.limit()))); // length
        bb.put(UnsignedBytes.checkedCast(0L)); // reserved1
        bb.put(UnsignedBytes.checkedCast(0L)); // reserved2
        bb.put(ip.getBytes(StandardCharsets.US_ASCII));
        bb.put(UnsignedBytes.checkedCast(0L)); // end of string null
    }

    private static Optional<Map<String, String>> response(
            final ByteBuffer buffer, final int requestNumber, final String ip)
    {
        buffer.get(); // version
        buffer.get(); // flags
        final short number = buffer.getShort();
        if (number != requestNumber) {
            return Optional.empty();
        }
        final short length = buffer.getShort();
        final byte error = buffer.get();
        if (error > 0) {
            return Optional.empty();
        }
        buffer.get(); // reserved
        final byte[] data = new byte[length - HEADER_LENGTH];
        buffer.get(data);
        final int ipLength = ip.length();
        final String responseIp = new String(data, 0, ipLength, ISO_8859_1);
        if (!ip.equals(responseIp)) {
            return Optional.empty();
        }
        final int mapDataLength = length - HEADER_LENGTH - ipLength;
        return Optional.ofNullable(TO_MAP.split(new String(data, ipLength, mapDataLength, ISO_8859_1)));
    }

    public static class OptionalExternalizer implements AdvancedExternalizer<Optional> {

        @Override
        @SuppressWarnings("unchecked")
        public Set<Class<? extends Optional>> getTypeClasses() {
            return Util.<Class<? extends Optional>>asSet(Optional.class);
        }

        @Override
        public Integer getId() {
            return 1234;
        }

        @Override
        public void writeObject(final ObjectOutput output, final Optional object) throws IOException {
            output.writeBoolean(object.isPresent());
            if (object.isPresent()) {
                output.writeObject(object.get());
            }
        }

        @Override
        public Optional readObject(final ObjectInput input) throws IOException, ClassNotFoundException {
            if (input.readBoolean()) {
                return Optional.ofNullable(input.readObject());
            } else {
                return Optional.empty();
            }
        }
    }

}
