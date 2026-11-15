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
        {"name":"Western Metro","code":"WP","localities":["Colombo","Dehiwala"],"active":true}
        """;
    @Test @WithMockUser(roles = "CUSTOMER")
    void customerCannotWrite() throws Exception {
        mvc.perform(post("/api/zones").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(put("/api/zones/" + WESTERN_ID).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/zones/" + WESTERN_ID + "/disable")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/zones/" + WESTERN_ID + "/enable")).andExpect(status().isForbidden());
        mvc.perform(get("/api/zones?includeInactive=true")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanWrite() throws Exception {
        when(service.createZone(any())).thenReturn(western());
        when(service.updateZone(any(), any())).thenReturn(western());
        when(service.disableZone(any())).thenReturn(western());
        mvc.perform(post("/api/zones").contentType("application/json").content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("WP"))
            .andExpect(jsonPath("$.localities[1]").value("Dehiwala"));
        mvc.perform(put("/api/zones/" + WESTERN_ID).contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(patch("/api/zones/" + WESTERN_ID + "/disable")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles = "ADMIN")
    void validatesCodeAndLocalities() throws Exception {
        mvc.perform(post("/api/zones").contentType("application/json").content("""
            {"name":"X","code":"W P!","localities":[]}
            """)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.code").isString())
            .andExpect(jsonPath("$.fields.localities").isString());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "CUSTOMER")
    void authenticatedCustomerCanRead() throws Exception {
        when(service.getActiveZones()).thenReturn(List.of(western()));
        mvc.perform(get("/api/zones")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(WESTERN_ID.toString()))
            .andExpect(jsonPath("$[0].localities[0]").value("Colombo"));
    }
    @Test void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/zones")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanListInactiveAndEnable() throws Exception {
        var inactive = western(); inactive.setActive(false);
        when(service.getAllZones()).thenReturn(List.of(inactive));
        when(service.enableZone(any())).thenReturn(western());
        mvc.perform(get("/api/zones?includeInactive=true")).andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(patch("/api/zones/" + WESTERN_ID + "/enable")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }
}
