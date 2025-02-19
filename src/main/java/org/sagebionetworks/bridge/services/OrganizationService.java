package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.OrganizationValidator.INSTANCE;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class OrganizationService {

    private OrganizationDao orgDao;
    private AccountService accountService;
    private SessionUpdateService sessionUpdateService;
    private AssessmentDao assessmentDao;
    private CacheProvider cacheProvider;
    
    @Autowired
    final void setOrganizationDao(OrganizationDao orgDao) {
        this.orgDao = orgDao;
    }
    @Autowired
    final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }
    @Autowired
    final void setSessionUpdateService(SessionUpdateService sessionUpdateService) {
        this.sessionUpdateService = sessionUpdateService;
    }
    @Autowired
    final void setAssessmentDao(AssessmentDao assessmentDao) {
        this.assessmentDao = assessmentDao;
    }
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    DateTime getCreatedOn() {
        return DateTime.now();
    }
    
    DateTime getModifiedOn() {
        return DateTime.now();
    }
    
    /**
     * Get a paged list of partially initialized organizations (containing name, description, 
     * and identifier).
     */
    public PagedResourceList<Organization> getOrganizations(String appId, Integer offsetBy, Integer pageSize) {
        checkArgument(isNotBlank(appId));
        
        if (offsetBy != null && offsetBy < 0) {
            throw new BadRequestException(NEGATIVE_OFFSET_ERROR);
        }
        if (pageSize != null && (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE)) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }
        
        if (!RequestContext.get().isInRole(Roles.ADMIN)) {
            List<Organization> list = ImmutableList.of();
            String orgId = RequestContext.get().getCallerOrgMembership();
            if (orgId != null) {
                Organization org = orgDao.getOrganization(appId, orgId)
                        .orElseThrow(() -> new EntityNotFoundException(Organization.class));        
                list = ImmutableList.of(org); 
            }
            return new PagedResourceList<>(list, list.size(), true)
                    .withRequestParam(OFFSET_BY, offsetBy)
                    .withRequestParam(PAGE_SIZE, pageSize);
        }
        return orgDao.getOrganizations(appId, offsetBy, pageSize)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize);
    }
    
    /**
     * Create an organization. The identifier of this organization must be unique within the context
     * of the app. 
     * @throws EntityAlreadyExistsException
     */
    public Organization createOrganization(Organization organization) {
        checkNotNull(organization);
        
        Validate.entityThrowingException(INSTANCE, organization);
        
        Optional<Organization> optional = orgDao.getOrganization(
                organization.getAppId(), organization.getIdentifier());
        
        if (optional.isPresent()) {
            throw new EntityAlreadyExistsException(Organization.class, 
                    ImmutableMap.of("appId", organization.getAppId(), 
                            "identifier", organization.getIdentifier()));
        }
        DateTime timestamp = getCreatedOn();
        organization.setCreatedOn(timestamp);
        organization.setModifiedOn(timestamp);
        organization.setVersion(null);
        return orgDao.createOrganization(organization);
    }
    
    /**
     * Update an existing organization.
     * @throws EntityNotFoundException
     */
    public Organization updateOrganization(Organization organization) {
        checkNotNull(organization);
        
        Validate.entityThrowingException(INSTANCE, organization);
        
        Organization existing = orgDao.getOrganization(organization.getAppId(), organization.getIdentifier())
                .orElseThrow(() -> new EntityNotFoundException(Organization.class));        
        
        organization.setModifiedOn(getModifiedOn());
        organization.setCreatedOn(existing.getCreatedOn());
        
        return orgDao.updateOrganization(organization);
    }
    
    /**
     * Get a fully initialized organization object.
     * @throws EntityNotFoundException
     */
    public Organization getOrganization(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        return orgDao.getOrganization(appId, identifier)
                .orElseThrow(() -> new EntityNotFoundException(Organization.class));        
    }
    
    /**
     * Get an optional that will contain an organization object if the supplied 
     * identifier is valid.
     */
    public Optional<Organization> getOrganizationOpt(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));

        return orgDao.getOrganization(appId, identifier);        
    }
    
    /**
     * Delete the organization with the given identifier.
     * @throws EntityNotFoundException
     */
    public void deleteOrganization(String appId, String identifier) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        
        Organization existing = orgDao.getOrganization(appId, identifier)
                .orElseThrow(() -> new EntityNotFoundException(Organization.class));        
        if (assessmentDao.hasAssessmentFromOrg(appId, identifier)) {
            throw new ConstraintViolationException.Builder().withMessage(
                    "Cannot delete organization (it still owns one or more assessments).")
                    .withEntityKey("appId", appId)
                    .withEntityKey("orgId", identifier).build();
        }

        orgDao.deleteOrganization(existing);
        
        CacheKey cacheKey = CacheKey.orgSponsoredStudies(appId, identifier);
        cacheProvider.removeObject(cacheKey);
    }
    
    public PagedResourceList<AccountSummary> getMembers(String appId, String identifier, AccountSummarySearch search) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        checkNotNull(search);
        
        AccountSummarySearch scopedSearch = search.toBuilder()
                // only needed for legacy APIs
                .withAdminOnly(null) 
                .withOrgMembership(identifier).build();
        
        return accountService.getPagedAccountSummaries(appId, scopedSearch);
    }
    
    public PagedResourceList<AccountSummary> getUnassignedAdmins(String appId, AccountSummarySearch search) {
        checkArgument(isNotBlank(appId));
        checkNotNull(search);

        AccountSummarySearch scopedSearch = search.toBuilder()
            .withAdminOnly(true)
            .withOrgMembership("<none>").build();

        return accountService.getPagedAccountSummaries(appId, scopedSearch);
    }
    
    /**
     * Once assigned, only admins can re-assign accounts.
     */
    public void addMember(String appId, String identifier, String userId) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        checkArgument(isNotBlank(userId));
        
        AccountId accountId = AccountId.forId(appId, userId);
        accountService.editAccount(accountId, (acct) -> {
            RequestContext context = RequestContext.get();
            if (!context.isInRole(ADMIN) && acct.getOrgMembership() != null) {
                throw new BadRequestException("Account already assigned to an organization.");
            }
            acct.setOrgMembership(identifier);
        });
        sessionUpdateService.updateOrgMembership(userId, identifier);
    }
    
    public void removeMember(String appId, String identifier, String userId) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(identifier));
        checkArgument(isNotBlank(userId));

        AccountId accountId = AccountId.forId(appId, userId);
        accountService.editAccount(accountId, (acct) -> {
            if (acct.getOrgMembership() == null || !acct.getOrgMembership().equals(identifier)) {
                throw new BadRequestException("Account is not a member of organization " + identifier);
            }
            acct.setOrgMembership(null);
        });
        sessionUpdateService.updateOrgMembership(userId, null);
    }

    public void deleteAllOrganizations(String appId) {
        checkNotNull(appId);

        orgDao.deleteAllOrganizations(appId);
    }
}
