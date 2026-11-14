package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.config.SecurityConfig;
import com.swiftdrop.pricing.service.ZoneService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static com.swiftdrop.pricing.PricingFixtures.*;

@WebMvcTest(controllers = ZoneController.class,
    properties = "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Import(SecurityConfig.class)
class ZoneControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ZoneService service;
    private static final String BODY = """
        {"name":"Kandy","city":"Kandy","district":"Kandy"}
        """;
    @Test @WithMockUser(roles = "CUSTOMER")
    void customerCannotWrite() throws Exception {
        mvc.perform(post("/api/zones").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(put("/api/zones/" + ZONE_ID).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/zones/" + ZONE_ID + "/disable")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/zones/" + ZONE_ID + "/enable")).andExpect(status().isForbidden());
        mvc.perform(get("/api/zones?includeInactive=true")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanWrite() throws Exception {
        when(service.createZone(anyString(), anyString(), anyString())).thenReturn(zone());
        when(service.updateZone(any(), anyString(), anyString(), anyString())).thenReturn(zone());
        when(service.disableZone(any())).thenReturn(zone());
        mvc.perform(post("/api/zones").contentType("application/json").content(BODY)).andExpect(status().isCreated());
        mvc.perform(put("/api/zones/" + ZONE_ID).contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(patch("/api/zones/" + ZONE_ID + "/disable")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles = "CUSTOMER")
    void authenticatedCustomerCanRead() throws Exception {
        when(service.getActiveZones()).thenReturn(List.of(zone()));
        mvc.perform(get("/api/zones")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ZONE_ID.toString()));
    }
    @Test void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/zones")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanListInactiveAndEnable() throws Exception {
        var inactive = zone(); inactive.setActive(false);
        when(service.getAllZones()).thenReturn(List.of(inactive));
        when(service.enableZone(any())).thenReturn(zone());
        mvc.perform(get("/api/zones?includeInactive=true")).andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(patch("/api/zones/" + ZONE_ID + "/enable")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }
}
