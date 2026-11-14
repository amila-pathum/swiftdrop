package com.swiftdrop.courier.service;

import com.swiftdrop.courier.client.CourierIntegrations.IdentityClient;
import com.swiftdrop.courier.dto.CourierDtos.*;
import com.swiftdrop.courier.entity.*;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.CourierProfileRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;

@Service
public class CourierProfileService {
    private final CourierProfileRepository profiles;
    private final IdentityClient identity;
    public CourierProfileService(CourierProfileRepository profiles,IdentityClient identity) { this.profiles=profiles;this.identity=identity; }
    @Transactional(readOnly=true)
    public CourierProfile own(Long userId) { return profiles.findByUserId(userId).orElseThrow(()->CourierException.missing("Courier profile not found.")); }
    public CourierProfile operational(Long userId) {
        CourierProfile p=own(userId);
        if(!p.active) throw CourierException.forbidden("Courier profile is inactive.");
        return p;
    }
    public record Provisioned(Profile profile, boolean created) {}
    @Transactional
    public Provisioned provision(ProvisionInput input,String token) {
        var account=identity.account(input.userId(),token);
        if(!input.userId().equals(account.id()) || !"COURIER".equals(account.role()))
            throw CourierException.invalid("The identity account must have the COURIER role.");
        var existing=profiles.findByUserId(input.userId());
        if(existing.isPresent()) return new Provisioned(Profile.from(existing.get()),false);
        CourierProfile p=new CourierProfile();p.userId=input.userId();apply(p,input.profile());
        p.active="ACTIVE".equals(account.status());
        profiles.saveAndFlush(p);
        return new Provisioned(Profile.from(p),true);
    }
    @Transactional
    public Profile update(Long userId,ProfileInput input) { var p=operational(userId);apply(p,input);return Profile.from(profiles.saveAndFlush(p)); }
    @Transactional
    public Profile availability(Long userId,Availability availability) { var p=operational(userId);p.availability=availability;return Profile.from(profiles.saveAndFlush(p)); }
    @Transactional
    public Profile active(Long id,boolean active,String token) {
        var p=profiles.findById(id).orElseThrow(()->CourierException.missing("Courier profile not found."));
        if(active) {
            var account=identity.account(p.userId,token);
            if(!p.userId.equals(account.id()) || !"COURIER".equals(account.role()) || !"ACTIVE".equals(account.status()))
                throw CourierException.conflict("Activate the COURIER identity account before activating its profile.");
        }
        p.active=active;if(!active)p.availability=Availability.OFFLINE;
        return Profile.from(profiles.saveAndFlush(p));
    }
    @Transactional(readOnly=true)
    public PageResult<Profile> list(Availability availability,String area,boolean active,int page,int size) {
        Specification<CourierProfile> filter=(root,query,cb)->{
            var conditions=new ArrayList<Predicate>();conditions.add(cb.equal(root.get("active"),active));
            if(availability!=null)conditions.add(cb.equal(root.get("availability"),availability));
            if(area!=null&&!area.isBlank())conditions.add(cb.equal(cb.lower(root.get("area")),area.trim().toLowerCase(java.util.Locale.ROOT)));
            return cb.and(conditions.toArray(Predicate[]::new));
        };
        var result=profiles.findAll(filter,PageRequest.of(page,size,Sort.by("id")));
        return new PageResult<>(result.getContent().stream().map(Profile::from).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    private void apply(CourierProfile p,ProfileInput i) {p.name=i.name().trim();p.phone=i.phone().trim();p.vehicleType=i.vehicleType();p.vehicleNumber=i.vehicleNumber().trim();p.area=i.area().trim();}
}
