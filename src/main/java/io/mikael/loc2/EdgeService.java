package io.mikael.loc2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Map;

@Service
@CacheConfig(cacheNames = "ips")
public class EdgeService {

    private static final Logger log = LoggerFactory.getLogger(EdgeService.class);

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
    public Map<String, String> fetchDataForIp(final String ip) {
        log.debug("Fetching data for ip {}", ip);
        final int number = nextNumber();
        byte[] bytes = new byte[2048];
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.clear();
        EdgeCodec.request(buffer, number, ip);
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
            return EdgeCodec.response(buffer, number, ip);
        } catch (PortUnreachableException e) {
            log.error("could not reach the server to make the initial request, make sure it's available in the address you have configured", e);
        } catch (SocketTimeoutException e) {
            log.error("UDP receive from server timed out", e);
        } catch (Exception e) {
            log.error("probably a socket error, check your configuration values", e);
        }
        return null;
    }

}
