package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.config.SecurityConfig;
import com.swiftdrop.pricing.service.PricingRuleService;
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

@WebMvcTest(controllers = PricingRuleController.class,
    properties = "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Import(SecurityConfig.class)
class PricingRuleControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PricingRuleService service;
    private static final String BODY = """
        {"zoneId":"00000000-0000-0000-0000-000000000001","baseFee":200,"pricePerKg":150,"expressMultiplier":1.5}
        """;
    @Test @WithMockUser(roles = "CUSTOMER")
    void customerCannotWrite() throws Exception {
        mvc.perform(post("/api/pricing/rules").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(put("/api/pricing/rules/" + RULE_ID).contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/pricing/rules/" + RULE_ID + "/disable")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/pricing/rules/" + RULE_ID + "/enable")).andExpect(status().isForbidden());
        mvc.perform(get("/api/pricing/rules?includeInactive=true")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanWrite() throws Exception {
        when(service.createPricingRule(any(), any())).thenReturn(rule());
        when(service.updatePricingRule(any(), any())).thenReturn(rule());
        when(service.disablePricingRule(any())).thenReturn(rule());
        mvc.perform(post("/api/pricing/rules").contentType("application/json").content(BODY)).andExpect(status().isCreated());
        mvc.perform(put("/api/pricing/rules/" + RULE_ID).contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(patch("/api/pricing/rules/" + RULE_ID + "/disable")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles = "CUSTOMER")
    void authenticatedCustomerCanRead() throws Exception {
        when(service.getActivePricingRules()).thenReturn(List.of(rule()));
        mvc.perform(get("/api/pricing/rules")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(RULE_ID.toString()));
    }
    @Test void anonymousCannotRead() throws Exception {
        mvc.perform(get("/api/pricing/rules")).andExpect(status().isUnauthorized());
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCanListInactiveAndEnable() throws Exception {
        var inactive = rule(); inactive.setActive(false);
        when(service.getAllPricingRules()).thenReturn(List.of(inactive));
        when(service.enablePricingRule(any())).thenReturn(rule());
        mvc.perform(get("/api/pricing/rules?includeInactive=true")).andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(patch("/api/pricing/rules/" + RULE_ID + "/enable")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }
}
