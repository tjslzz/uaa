package org.cloudfoundry.identity.uaa.zone;

import org.cloudfoundry.identity.uaa.provider.IdentityProviderProvisioning;
import org.cloudfoundry.identity.uaa.saml.SamlKey;
import org.cloudfoundry.identity.uaa.scim.ScimGroup;
import org.cloudfoundry.identity.uaa.scim.ScimGroupProvisioning;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.validation.BindingResult;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IdentityZoneEndpointsTests {

    IdentityZoneEndpoints endpoints;
    private IdentityZone zone;
    private IdentityZoneProvisioning zoneDao = mock(IdentityZoneProvisioning.class);
    private ScimGroupProvisioning groupProvisioning = mock(ScimGroupProvisioning.class);

    @Before
    public void setup() {
        endpoints = new IdentityZoneEndpoints(
                zoneDao,
                mock(IdentityProviderProvisioning.class),
                mock(IdentityZoneEndpointClientRegistrationService.class),
                groupProvisioning,
                (config, mode) -> config,
                null);
        when(zoneDao.create(any())).then(invocation -> invocation.getArguments()[0]);
        IdentityZoneHolder.clear();
    }

    @Test
    public void create_zone() {
        zone = createZone();
        endpoints.createIdentityZone(zone, mock(BindingResult.class));
        verify(zoneDao, times(1)).create(same(zone));
    }

    @Test
    public void groups_are_created() {
        zone = createZone();
        endpoints.createUserGroups(zone);
        ArgumentCaptor<ScimGroup> captor = ArgumentCaptor.forClass(ScimGroup.class);
        List<String> defaultGroups = zone.getConfig().getUserConfig().getDefaultGroups();
        verify(groupProvisioning, times(defaultGroups.size())).createOrGet(captor.capture(), eq(zone.getId()));
        assertEquals(defaultGroups.size(), captor.getAllValues().size());
        assertThat(defaultGroups,
                containsInAnyOrder(
                        captor.getAllValues().stream().map(
                                ScimGroup::getDisplayName
                        ).toArray(String[]::new)
                )
        );
    }

    @Test
    public void group_creation_called_on_create() {
        IdentityZoneEndpoints spy = Mockito.spy(endpoints);
        zone = createZone();
        spy.createIdentityZone(zone, mock(BindingResult.class));
        verify(spy, times(1)).createUserGroups(same(zone));
    }

    @Test
    public void group_creation_called_on_update() {
        IdentityZoneEndpoints spy = Mockito.spy(endpoints);
        zone = createZone();
        when(zoneDao.retrieveIgnoreActiveFlag(zone.getId())).thenReturn(zone);
        when(zoneDao.update(same(zone))).thenReturn(zone);
        spy.updateIdentityZone(zone, zone.getId());
        verify(spy, times(1)).createUserGroups(same(zone));
    }

    @Test
    public void remove_keys_from_map() {
        zone = createZone();

        endpoints.removeKeys(zone);

        assertNull(zone.getConfig().getSamlConfig().getPrivateKey());
        assertNull(zone.getConfig().getSamlConfig().getPrivateKeyPassword());
        zone.getConfig().getSamlConfig().getKeys().forEach((key, value) -> {
            assertNull(value.getKey());
            assertNull(value.getPassphrase());
        });
    }

    @Test
    public void restore_keys() {
        remove_keys_from_map();
        IdentityZone original = createZone();
        endpoints.restoreSecretProperties(original, zone);


        assertNotNull(zone.getConfig().getSamlConfig().getPrivateKey());
        assertNotNull(zone.getConfig().getSamlConfig().getPrivateKeyPassword());
        zone.getConfig().getSamlConfig().getKeys().forEach((key, value) -> {
            assertNotNull(value.getKey());
            assertNotNull(value.getPassphrase());
        });

    }

    private static IdentityZone createZone() {
        IdentityZone zone = MultitenancyFixture.identityZone("id", "subdomain");
        IdentityZoneConfiguration config = zone.getConfig();
        assertNotNull(config);
        zone.getConfig().getSamlConfig().setPrivateKey("private");
        zone.getConfig().getSamlConfig().setPrivateKeyPassword("passphrase");
        zone.getConfig().getSamlConfig().setCertificate("certificate");
        zone.getConfig().getSamlConfig().addAndActivateKey("active", new SamlKey("private1", "passphrase1", "certificate1"));

        assertNotNull(zone.getConfig().getSamlConfig().getPrivateKey());
        assertNotNull(zone.getConfig().getSamlConfig().getPrivateKeyPassword());
        zone.getConfig().getSamlConfig().getKeys().forEach((key, value) -> {
            assertNotNull(value.getKey());
            assertNotNull(value.getPassphrase());
        });
        return zone;
    }
}
