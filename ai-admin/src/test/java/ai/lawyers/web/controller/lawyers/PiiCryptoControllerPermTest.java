package ai.lawyers.web.controller.lawyers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.service.lawyers.IPiiCryptoMigrationService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link PiiCryptoController} 权限校验——4 个运维端点共用 lawyers:pii:crypt，
 * 逐端点验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class PiiCryptoControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IPiiCryptoMigrationService piiCryptoMigrationService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(piiCryptoMigrationService.migrateCallerProfiles()).thenReturn(new HashMap<>());
        when(piiCryptoMigrationService.migrateCallRecords()).thenReturn(new HashMap<>());
        when(piiCryptoMigrationService.migrateCallLedgers()).thenReturn(new HashMap<>());
        when(piiCryptoMigrationService.rotateKey()).thenReturn(new HashMap<>());
        startMvc(PiiCryptoController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("piiCryptoMigrationService", piiCryptoMigrationService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/pii";
        endpoints.add(new Endpoint("lawyers:pii:crypt", post(base + "/migrateCallerProfile")));
        endpoints.add(new Endpoint("lawyers:pii:crypt", post(base + "/migrateCallRecord")));
        endpoints.add(new Endpoint("lawyers:pii:crypt", post(base + "/migrateCallLedger")));
        endpoints.add(new Endpoint("lawyers:pii:crypt", post(base + "/rotateKey")));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void allEndpoints_wrongPermission_403() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs("lawyers:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allEndpoints_withCorrectPermission_200() throws Exception
    {
        for (Endpoint endpoint : endpoints)
        {
            mockMvc.perform(endpoint.builder.with(loginAs(endpoint.permission)))
                    .andExpect(status().isOk());
        }
    }

    private static class Endpoint
    {
        private final String permission;

        private final MockHttpServletRequestBuilder builder;

        Endpoint(String permission, MockHttpServletRequestBuilder builder)
        {
            this.permission = permission;
            this.builder = builder;
        }
    }
}
