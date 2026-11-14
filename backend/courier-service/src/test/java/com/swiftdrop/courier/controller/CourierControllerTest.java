package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.config.SecurityConfig;
import com.swiftdrop.courier.exception.CourierExceptionHandler;
import com.swiftdrop.courier.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers={CourierController.class,CourierDeliveryController.class},properties="swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Import({SecurityConfig.class,CourierExceptionHandler.class})
class CourierControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CourierProfileService profiles;
    @MockitoBean CourierDeliveryService deliveries;
    @Test void unauthenticatedGets401AndCustomerCannotUseCourierEndpoints() throws Exception {
        mvc.perform(get("/api/couriers/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/couriers/me").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))).andExpect(status().isForbidden());
        verifyNoInteractions(profiles,deliveries);
    }
    @Test void couriersCannotListOthersOrProvisionProfiles() throws Exception {
        mvc.perform(get("/api/couriers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/couriers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COURIER"))).contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":42,\"profile\":{\"name\":\"Courier\",\"phone\":\"0771234567\",\"vehicleType\":\"VAN\",\"vehicleNumber\":\"ABC1234\",\"area\":\"Colombo\"}}"))
            .andExpect(status().isForbidden());verifyNoInteractions(profiles);
    }
    @Test void dispatcherCanListButInvalidPaginationIsRejected() throws Exception {
        mvc.perform(get("/api/couriers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")))).andExpect(status().isOk());
        mvc.perform(get("/api/couriers").param("size","1000").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")))).andExpect(status().isBadRequest());
        verify(profiles,times(1)).list(null,null,true,0,20);
    }
    @Test void invalidCoordinatesNeverReachService() throws Exception {
        mvc.perform(post("/api/couriers/me/deliveries/"+UUID.randomUUID()+"/status").with(jwt().jwt(j->j.subject("42")).authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"submissionId\":\""+UUID.randomUUID()+"\",\"status\":\"PICKED_UP\",\"latitude\":100,\"longitude\":80}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(deliveries);
    }
}
