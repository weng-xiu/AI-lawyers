package ai.lawyers.web.controller.lawyers.trunk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkAlarmMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkMetricMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.EslEventBridgeService;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip.PjsipConfigQueryService;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip.PjsipRegistrationService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * {@link TrunkMonitorController} 权限校验——13 个端点逐端点验证未登录 401、
 * 权限码不符 403、持对应权限码放行 200。{@code /overview} 解引用今日总览 Map，
 * stub 为非空 HashMap；AMI 桥接为 {@code @Autowired(required=false)}，
 * 迷你容器不注册该 bean，覆盖「未启用 Asterisk」分支。
 */
public class TrunkMonitorControllerPermTest extends AbstractPermMvcTest
{
    @Mock
    private ITrunkMonitorService trunkMonitorService;

    @Mock
    private ICallDispatchService callDispatchService;

    @Mock
    private AiTrunkMetricMapper trunkMetricMapper;

    @Mock
    private AiTrunkAlarmMapper trunkAlarmMapper;

    @Mock
    private RedisLeaderLock redisLeaderLock;

    @Mock
    private EslEventBridgeService eslEventBridgeService;

    @Mock
    private PjsipConfigQueryService pjsipConfigQueryService;

    @Mock
    private PjsipRegistrationService pjsipRegistrationService;

    private final List<Endpoint> endpoints = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(trunkMonitorService.todayOverview()).thenReturn(new HashMap<>());
        when(redisLeaderLock.scanJobLocks()).thenReturn(new ArrayList<>());
        when(trunkMetricMapper.selectAiTrunkMetricList(any())).thenReturn(new ArrayList<>());
        when(trunkMetricMapper.selectRecentByTrunk(any(), any(), any())).thenReturn(new ArrayList<>());
        when(trunkAlarmMapper.selectAiTrunkAlarmList(any())).thenReturn(new ArrayList<>());
        startMvc(TrunkMonitorController.class);
        buildEndpoints();
    }

    @Override
    protected void registerMocks()
    {
        registerMock("trunkMonitorService", trunkMonitorService);
        registerMock("callDispatchService", callDispatchService);
        registerMock("trunkMetricMapper", trunkMetricMapper);
        registerMock("trunkAlarmMapper", trunkAlarmMapper);
        registerMock("redisLeaderLock", redisLeaderLock);
        registerMock("eslEventBridgeService", eslEventBridgeService);
        registerMock("pjsipConfigQueryService", pjsipConfigQueryService);
        registerMock("pjsipRegistrationService", pjsipRegistrationService);
    }

    private void buildEndpoints()
    {
        String base = "/lawyers/trunk/monitor";
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/overview")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/trunkStatus")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/carrierStat")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/metricList")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/trend")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/alarm/list")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/alarm/active")));
        endpoints.add(new Endpoint("lawyers:trunk:alarm",
                post(base + "/alarm/handle").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:test",
                post(base + "/healthCheck").contentType(MediaType.APPLICATION_JSON).content("{}")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/pjsip/endpoints")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/pjsip/queues")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/pjsip/summary")));
        endpoints.add(new Endpoint("lawyers:trunk:monitor", get(base + "/pjsip/registrations")));
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
