package io.mikael.loc2;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.Optional;

@SpringBootApplication
@EnableCaching
@RestController
public class Application implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    @Autowired
    private EdgeService es;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @RequestMapping("/address/current")
    public ResponseEntity<Map<String, String>> currentAddress(
            @RequestHeader("X-Forwarded-For") final String ip)
    {
        return es.fetchDataForIp(ip)
                .map(map -> new ResponseEntity<>(map, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @RequestMapping("/address/{ip:(?:[0-9]{1,3}\\.){3}[0-9]{1,3}}")
    public ResponseEntity<Map<String, String>> addressForIp(@PathVariable("ip") String ip) {
        return es.fetchDataForIp(ip)
                .map(map -> new ResponseEntity<>(map, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler
    public ResponseEntity<Map<String, String>> handleException(final WebRequest req, final Exception ex) {
        log.error("Handling exception", ex);
        final Map<String, String> ret = ImmutableMap.of(
                "status", "500",
                "message", ex != null ? ex.getMessage() : "",
                "path", req.getContextPath(),
                "timestamp", java.time.OffsetDateTime.now().toString());
        return new ResponseEntity<>(ret, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RequestMapping("/error")
    public ResponseEntity<Map<String, String>> error() {
        final Map<String, String> ret = ImmutableMap.of(
                "status", "404",
                "timestamp", java.time.OffsetDateTime.now().toString());
        return new ResponseEntity<>(ret, HttpStatus.NOT_FOUND);
    }

    @Override
    public String getErrorPath() {
        return "/error";
    }

}
