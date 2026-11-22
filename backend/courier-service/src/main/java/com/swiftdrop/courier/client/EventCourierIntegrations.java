package com.swiftdrop.courier.client;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.repository.CourierDeliveryReadRepository;
import com.swiftdrop.courier.service.CourierWorkflowService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name="swiftdrop.deliveries.source",havingValue="rabbit",matchIfMissing=true)
public class EventCourierIntegrations {
    @Bean
    DispatchClient dispatchClient(CourierDeliveryReadRepository reads) {
        return (profileId,token)->reads.assignments(profileId);
    }
    @Bean
    ParcelClient parcelClient(CourierDeliveryReadRepository reads,CourierWorkflowService workflow) {
        return new ParcelClient() {
            public ParcelDetails parcel(UUID id,String token) { return reads.parcel(id); }
            public void update(UUID id,StatusUpdate update,String token) { workflow.changeStatus(id,update); }
        };
    }
}
