package com.swiftdrop.merchant.merchantapi.controller;

import com.swiftdrop.merchant.config.SecurityConfig;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.merchantapi.exception.MerchantApiExceptionHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes={SecurityConfig.class,MerchantApiExceptionHandler.class,MerchantDeliveryControllerTest.Config.class})
@TestPropertySource(properties="swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
class MerchantDeliveryControllerTest {
    static HttpServer testServer;
    @AfterAll static void stopServer() { if (testServer != null) testServer.stop(0); }
    static final String TOKEN="test-internal-token-at-least-32-characters";
    @Autowired WebApplicationContext context;
    @Autowired MerchantRepository merchants;
    @Autowired HttpServer server;
    MockMvc mvc;
    String path;
    String receivedToken;
    @BeforeEach void setup() {
        reset(merchants);
        path=null; receivedToken=null;
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        server.createContext("/internal/webhooks", exchange -> {
            path=exchange.getRequestURI().getPath();
            receivedToken=exchange.getRequestHeaders().getFirst("X-SwiftDrop-Internal-Token");
            byte[] body="{\"items\":[],\"total\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            try(var output=exchange.getResponseBody()) { output.write(body); }
        });
    }
    @AfterEach void cleanup() { server.removeContext("/internal/webhooks"); }
    @Test void userIdIsResolvedToMerchantIdBeforeForwarding() throws Exception {
        Merchant merchant=mock(Merchant.class);
        when(merchant.getId()).thenReturn(7L);
        when(merchant.getStatus()).thenReturn(MerchantStatus.APPROVED);
        when(merchants.findByUserId(9001L)).thenReturn(Optional.of(merchant));
        mvc.perform(get("/api/merchants/me/webhook/deliveries").with(jwt().jwt(j -> j.subject("9001"))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT")))).andExpect(status().isOk());
        assertEquals("/internal/webhooks/merchants/7/deliveries",path);
        assertEquals(TOKEN,receivedToken);
    }
    @Test void customerAndAnonymousCannotCallManagement() throws Exception {
        mvc.perform(get("/api/merchants/me/webhook/deliveries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/merchants/me/webhook/deliveries").with(jwt().jwt(j -> j.subject("9001"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))).andExpect(status().isForbidden());
        verifyNoInteractions(merchants);
        assertNull(path);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean MerchantRepository merchants() { return mock(MerchantRepository.class); }
        @Bean(destroyMethod="") HttpServer server() throws Exception {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.start(); testServer=server; return server;
        }
        @Bean MerchantDeliveryController controller(MerchantRepository merchants, HttpServer server) {
            return new MerchantDeliveryController(merchants,"http://127.0.0.1:"+server.getAddress().getPort(),TOKEN);
        }
    }
}
