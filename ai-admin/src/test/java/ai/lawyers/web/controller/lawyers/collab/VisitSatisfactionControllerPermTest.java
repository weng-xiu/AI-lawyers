package ai.lawyers.web.controller.lawyers.collab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.service.lawyers.stat.IVisitSatisfactionService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * P2-14：{@link VisitSatisfactionController} 权限校验——1 个端点、1 个权限边界，
 * 验证未登录 401、权限码不符 403、持对应权限码放行 200。
 */
public class VisitSatisfactionControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private IVisitSatisfactionService visitSatisfactionService;

    private final List<MockHttpServletRequestBuilder> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        startMvc(VisitSatisfactionController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("visitSatisfactionService", visitSatisfactionService);
    }

    private void buildEndpoints()
    {
        endpoints.add(get("/lawyers/visit/satisfaction/board"));
    }

    @Test
    void allEndpoints_unauthenticated_401() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void allEndpoints_wrongPermission_403() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint.with(loginAs("lawyers:nope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void allEndpoints_withCorrectPermission_200() throws Exception
    {
        for (MockHttpServletRequestBuilder endpoint : endpoints)
        {
            mockMvc.perform(endpoint.with(loginAs("lawyers:visitBoard:view")))
                    .andExpect(status().isOk());
        }
    }
}
