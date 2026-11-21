package com.swiftdrop.courier.application;

import com.swiftdrop.courier.application.CourierApplicationDtos.ApplicationInput;
import com.swiftdrop.courier.application.CourierApplicationDtos.ApplicationResponse;
import com.swiftdrop.courier.entity.CourierProfile;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.CourierProfileRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Courier applications: a courier applies, an administrator approves or rejects.
 * Administrators never create courier accounts; approval is the only way a
 * courier becomes active and gets a courier profile.
 */
@Service
public class CourierApplicationService {
    private final CourierApplicationRepository applications;
    private final CourierProfileRepository profiles;
    private final CourierAccountActivation activation;

    public CourierApplicationService(CourierApplicationRepository applications,
            CourierProfileRepository profiles, CourierAccountActivation activation) {
        this.applications = applications;
        this.profiles = profiles;
        this.activation = activation;
    }

    @Transactional
    public ApplicationResponse submit(Long userId, ApplicationInput input) {
        if (applications.existsByUserId(userId)) {
            throw CourierException.conflict("You have already submitted a courier application.");
        }
        CourierApplication application = new CourierApplication();
        application.userId = userId;
        application.name = input.name().trim();
        application.phone = input.phone().trim();
        application.vehicleType = input.vehicleType();
        application.vehicleNumber = input.vehicleRegistrationNumber().trim();
        application.area = input.preferredArea().trim();
        return ApplicationResponse.from(applications.saveAndFlush(application));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse own(Long userId) {
        return applications.findByUserId(userId).map(ApplicationResponse::from)
            .orElseThrow(() -> CourierException.missing("Courier application not found."));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(ApplicationStatus status) {
        List<CourierApplication> found = status == null
            ? applications.findAllByOrderByCreatedAtDesc()
            : applications.findByStatusOrderByCreatedAtDesc(status);
        return found.stream().map(ApplicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long id) {
        return applications.findById(id).map(ApplicationResponse::from)
            .orElseThrow(CourierApplicationService::notFound);
    }

    /**
     * Approves a pending application: activates the login account, creates the
     * courier profile, then records the decision. Repeating it for an already
     * approved application is harmless and repairs a half-finished approval
     * (for example when the Identity response was lost).
     */
    @Transactional
    public ApplicationResponse approve(Long id, Long adminId, String adminToken) {
        CourierApplication application = applications.findForReviewById(id)
            .orElseThrow(CourierApplicationService::notFound);
        if (application.status == ApplicationStatus.REJECTED) throw notPending();

        activation.ensureActive(application.userId, adminToken);
        activateProfile(application);
        if (application.status == ApplicationStatus.APPROVED) return ApplicationResponse.from(application);

        application.status = ApplicationStatus.APPROVED;
        application.rejectionReason = null;
        application.reviewedBy = adminId;
        application.reviewedAt = Instant.now();
        return ApplicationResponse.from(applications.saveAndFlush(application));
    }

    /** Rejects a pending application. The login account stays pending so the applicant can read the reason. */
    @Transactional
    public ApplicationResponse reject(Long id, Long adminId, String reason) {
        CourierApplication application = applications.findForReviewById(id)
            .orElseThrow(CourierApplicationService::notFound);
        if (application.status != ApplicationStatus.PENDING) throw notPending();
        application.status = ApplicationStatus.REJECTED;
        application.rejectionReason = reason.trim();
        application.reviewedBy = adminId;
        application.reviewedAt = Instant.now();
        return ApplicationResponse.from(applications.saveAndFlush(application));
    }

    private void activateProfile(CourierApplication application) {
        CourierProfile profile = profiles.findByUserId(application.userId).orElse(null);
        if (profile == null) {
            profile = new CourierProfile();
            profile.userId = application.userId;
            profile.name = application.name;
            profile.phone = application.phone;
            profile.vehicleType = application.vehicleType;
            profile.vehicleNumber = application.vehicleNumber;
            profile.area = application.area;
        }
        profile.active = true;
        profiles.saveAndFlush(profile);
    }

    private static CourierException notFound() {
        return CourierException.missing("Courier application not found.");
    }

    private static CourierException notPending() {
        return CourierException.conflict("Only pending courier applications can be reviewed.");
    }
}
