package org.sagebionetworks.bridge.hibernate;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeUtils.collectExternalIds;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableSet;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyClass;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.studies.Enrollment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/** MySQL implementation of accounts via Hibernate. */
@Entity
@Table(name = "Accounts")
@BridgeTypeName("Account")
public class HibernateAccount implements Account {
    private String id;
    private String appId;
    private String orgMembership; 
    private String email;
    private String synapseUserId;
    private Phone phone;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private Map<String, String> attributes;
    private Map<HibernateAccountConsentKey, HibernateAccountConsent> consents;
    private DateTime createdOn;
    private String healthCode;
    private DateTime modifiedOn;
    private String firstName;
    private String lastName;
    private PasswordAlgorithm passwordAlgorithm;
    private String passwordHash;
    private DateTime passwordModifiedOn;
    private String reauthToken;
    private Set<Roles> roles;
    private AccountStatus status;
    private int version;
    private JsonNode clientData;
    private DateTimeZone timeZone;
    private SharingScope sharingScope;
    private Boolean notifyByEmail;
    private Set<String> dataGroups;
    private List<String> languages;
    private int migrationVersion;
    private Set<Enrollment> enrollments;
    private String note;
    private String clientTimeZone;
    
    /**
     * Constructor to load information for the AccountRef object. This avoids loading any of the 
     * ancillary tables.
     */
    public HibernateAccount(String firstName, String lastName, String email, Phone phone, String synapseUserId,
            String orgMembership, String id) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.synapseUserId = synapseUserId;
        this.orgMembership = orgMembership;
        this.id = id;
    }
    
    /**
     * No args constructor, required and used by Hibernate for full object initialization.
     */
    public HibernateAccount() {}
    
    /**
     * Constructor to load information for the AccountSummary object. Could not find a way to 
     * construct this object with just the indicated fields using a select clause, without also 
     * specifying a constructor.
     */
    public HibernateAccount(DateTime createdOn, String appId, String orgId, String firstName, String lastName,
            String email, Phone phone, String id, AccountStatus status, String synapseUserId) {
        this.createdOn = createdOn;
        this.appId = appId;
        this.orgMembership = orgId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.id = id;
        this.status = status;
        this.synapseUserId = synapseUserId;
    }

    /**
     * Account ID, used as a unique identifier for the account that doesn't leak email address (which is personally
     * identifying info).
     */
    @Id
    public String getId() {
        return id;
    }

    /** @see #getId */
    public void setId(String id) {
        this.id = id;
    }

    /** App ID the account lives in. */
    @Column(name = "studyId")
    @JsonIgnore
    public String getAppId() {
        return appId;
    }

    /** @see #getAppId */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    /** Account is an administrative account that is a member of this organization. */
    public String getOrgMembership() {
        return orgMembership;
    }

    /** @see #getOrgMembership */
    public void setOrgMembership(String orgId) {
        this.orgMembership = orgId;
    }
    
    /** Account email address. */
    public String getEmail() {
        return email;
    }

    /** @see #getEmail */
    public void setEmail(String email) {
        this.email = email;
    }
    
    /** Synapse user ID (for administrative users). */ 
    public String getSynapseUserId() {
        return synapseUserId;
    }
    
    /** @see #getSynapseUserId */
    public void setSynapseUserId(String synapseUserId) {
        this.synapseUserId = synapseUserId;
    }
    
    /** Account phone number, as entered by the user. */
    @Embedded
    public Phone getPhone() {
        return phone;
    }

    /** @see #getPhone */
    public void setPhone(Phone phone) {
        this.phone = phone;
    }

    /** Has the email address been verified to be under the control of the account holder. */
    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    
    /** @see #getEmailVerified */
    public Boolean getEmailVerified() {
        return emailVerified;
    }
    
    /** Has the phone number been verified to be under the control of the account holder. */
    public void setPhoneVerified(Boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    /** @see #getPhoneVerified */
    public Boolean getPhoneVerified() {
        return phoneVerified;
    }

    /** Map of custom account attributes. Never returns null. */
    @CollectionTable(name = "AccountAttributes", joinColumns = @JoinColumn(name = "accountId",
            referencedColumnName = "id"))
    @Column(name = "attributeValue")
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "attributeKey")
    public Map<String, String> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    /** @see #getAttributes */
    public void setAttributes(Map<String, String> attributes) {
        // Note: Hibernate doesn't support copying this into a separate map.
        this.attributes = attributes;
    }

    /** Map of consents, keyed by a composite of subpopulation ID and signedOn. Never returns null. */
    @CollectionTable(name = "AccountConsents", joinColumns = @JoinColumn(name = "accountId",
            referencedColumnName = "id"))
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyClass(HibernateAccountConsentKey.class)
    @JsonIgnore
    public Map<HibernateAccountConsentKey, HibernateAccountConsent> getConsents() {
        if (consents == null) {
            consents = new HashMap<>();
        }
        return consents;
    }

    /** @see #getConsents */
    public void setConsents(Map<HibernateAccountConsentKey, HibernateAccountConsent> consents) {
        // Note: Hibernate doesn't support copying this into a separate map.
        this.consents = consents;
    }

    /** Epoch milliseconds when the account was created. */
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    public DateTime getCreatedOn() {
        return createdOn;
    }

    /** @see #getCreatedOn */
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    /** Account health code. */
    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** Epoch milliseconds when the account was last modified, including password but NOT 
     * reauthentication token changes. */
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    public DateTime getModifiedOn() {
        return modifiedOn;
    }

    /** @see #getModifiedOn */
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    /** User's first name (given name). */
    public String getFirstName() {
        return firstName;
    }

    /** @see #getFirstName */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /** User's last name (surname). */
    public String getLastName() {
        return lastName;
    }

    /** @see #getLastName */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * The algorithm used to hash the password.
     *
     * @see PasswordAlgorithm
     */
    @Enumerated(EnumType.STRING)
    @JsonIgnore
    public PasswordAlgorithm getPasswordAlgorithm() {
        return passwordAlgorithm;
    }

    /** @see #getPasswordAlgorithm */
    public void setPasswordAlgorithm(PasswordAlgorithm passwordAlgorithm) {
        this.passwordAlgorithm = passwordAlgorithm;
    }

    /** The full password hash, as used by {@link PasswordAlgorithm} to decode it. */
    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    /** @see #getPasswordHash */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Epoch milliseconds when the user last changed their password. */
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @JsonIgnore
    public DateTime getPasswordModifiedOn() {
        return passwordModifiedOn;
    }

    /** @see #getPasswordModifiedOn */
    public void setPasswordModifiedOn(DateTime passwordModifiedOn) {
        this.passwordModifiedOn = passwordModifiedOn;
    }

    /**
     * Set of user roles (admin, developer, researcher, etc). Never returns null.
     *
     * @see Roles
     */
    @CollectionTable(name = "AccountRoles", joinColumns = @JoinColumn(name = "accountId", referencedColumnName = "id"))
    @Column(name = "role")
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    public Set<Roles> getRoles() {
        if (roles == null) {
            roles = EnumSet.noneOf(Roles.class);
        }
        return roles;
    }

    /** @see #getRoles */
    public void setRoles(Set<Roles> roles) {
        // Note: Hibernate doesn't support copying this into a separate set.
        this.roles = roles;
    }

    /**
     * Account status (unverified, enabled, disabled).
     *
     * @see AccountStatus
     */
    @Enumerated(EnumType.STRING)
    public AccountStatus getStatus() {
        if (status == DISABLED) {
            return status;
        }
        // As long as there is a pathway to authenticate, the account is considered verified.
        // Can sign in with an external ID and password
        boolean verExtId = (passwordHash != null) && !collectExternalIds(this).isEmpty();
        // Can sign in via email
        boolean verEmail = (email != null && TRUE.equals(emailVerified));
        // Can sign in with phone number
        boolean verPhone = (phone != null && TRUE.equals(phoneVerified));
        // By setting the field, it's persisted on database updates
        this.status = (verEmail || verPhone || synapseUserId != null || verExtId) ? ENABLED : UNVERIFIED;
        return status;
    }

    /** @see #getStatus */
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    
    /** @see #getClientData */
    @Column(columnDefinition = "mediumtext", name = "clientData", nullable = true)
    @Convert(converter = JsonNodeAttributeConverter.class)
    public JsonNode getClientData() {
        return clientData;
    }
    
    /** The clientData JSON. */
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
    }
    
    @Version
    /** Version number, used by Hibernate to handle optimistic locking. */
    public int getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(int version) {
        this.version = version;
    }
    
    /** The time zone initially captured from this user's requests, used to correctly calculate 
     * schedules for the user. Should not be updated once set. Related to v3 scheduling API so
     * it is not always set or reliable. */
    @Convert(converter = DateTimeZoneAttributeConverter.class)
    @JsonIgnore
    public DateTimeZone getTimeZone() {
        return timeZone;
    }

    /** @see #getTimeZone */
    public void setTimeZone(DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /** The sharing scope set for data being generated by this study participant. */
    @Enumerated(EnumType.STRING)
    @JsonIgnore
    public SharingScope getSharingScope() {
        return (sharingScope == null) ? SharingScope.NO_SHARING : sharingScope;
    }

    /** @see #getSharingScope */
    public void setSharingScope(SharingScope sharingScope) {
        this.sharingScope = sharingScope;
    }

    /** Has this user consented to receive email from the app administrators? */
    @JsonIgnore
    public Boolean getNotifyByEmail() {
        return (notifyByEmail == null) ? TRUE : notifyByEmail;
    }

    /** @see #getNotifyByEmail */
    public void setNotifyByEmail(Boolean notifyByEmail) {
        this.notifyByEmail = notifyByEmail;
    }

    /** Data groups assigned to this account. */
    @CollectionTable(name = "AccountDataGroups", joinColumns = @JoinColumn(name = "accountId", referencedColumnName = "id"))
    @Column(name = "dataGroup")
    @ElementCollection(fetch = FetchType.EAGER)
    public Set<String> getDataGroups() {
        if (dataGroups == null) {
            dataGroups = new HashSet<>();
        }
        return dataGroups;
    }

    /** @see #getDataGroups */
    public void setDataGroups(Set<String> dataGroups) {
        this.dataGroups = dataGroups;
    }

    /** Languages captured from a request by this user's Accept-Language header. This should be an ordered 
     * list of unique ISO 639-1 language codes. */
    @CollectionTable(name = "AccountLanguages", joinColumns = @JoinColumn(name = "accountId", referencedColumnName = "id"))
    @Column(name = "language")
    @OrderColumn(name="order_index", insertable=true, updatable=true)
    @ElementCollection(fetch = FetchType.EAGER)
    public List<String> getLanguages() {
        if (languages == null) {
            languages = new ArrayList<>();
        }
        return languages;
    }

    /** @see #getLanguages */
    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    /** Used internally to track migration of data to/from this table. */
    @JsonIgnore
    public int getMigrationVersion() {
        return migrationVersion;
    }

    /** @see #getMigrationVersion */
    public void setMigrationVersion(int migrationVersion) {
        this.migrationVersion = migrationVersion;
    }
    
    @Transient // do not persist in Hibernate (the hash is persisted instead)
    @JsonIgnore
    public String getReauthToken() {
        return reauthToken;
    }

    public void setReauthToken(String reauthToken) {
        this.reauthToken = reauthToken; 
    }
    
    @OneToMany(mappedBy = "accountId", cascade = CascadeType.ALL, orphanRemoval = true, 
        fetch = FetchType.EAGER, targetEntity=HibernateEnrollment.class)
    @OnDelete(action=OnDeleteAction.CASCADE)
    @JsonIgnore
    @Override
    public Set<Enrollment> getEnrollments() {
        if (enrollments == null) {
            enrollments = new HashSet<>();
        }
        return enrollments;
    }

    @Override
    public void setEnrollments(Set<Enrollment> enrollments) {
        this.enrollments = enrollments;
    }
    
    @Transient
    @JsonIgnore
    public Set<Enrollment> getActiveEnrollments() {
        return getEnrollments().stream()
                .filter(en -> en.getWithdrawnOn() == null)
                .collect(toImmutableSet());
    }

    @Override
    public String getNote() {
        return note;
    }

    @Override
    public void setNote(String note) {
        this.note = note;
    }

    /** The client's time zone explicitly set through requests to be used as reference
     * for client side scheduling. Can be updated or deleted. Must be an IANA time zone name. */
    @Override
    public String getClientTimeZone() {
        return clientTimeZone;
    }

    /** @see #getClientTimeZone */
    @Override
    public void setClientTimeZone(String clientTimeZone) {
        this.clientTimeZone = clientTimeZone;
    }
}
