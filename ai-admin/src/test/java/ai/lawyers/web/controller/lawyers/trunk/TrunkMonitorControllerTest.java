package ai.lawyers.web.controller.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.framework.websocket.CallWebSocketServer;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkAlarm;
import ai.lawyers.system.domain.lawyers.trunk.AiTrunkMetric;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkAlarmMapper;
import ai.lawyers.system.mapper.lawyers.trunk.AiTrunkMetricMapper;
import ai.lawyers.system.service.lawyers.cluster.RedisLeaderLock;
import ai.lawyers.system.service.lawyers.trunk.ICallDispatchService;
import ai.lawyers.system.service.lawyers.trunk.ITrunkMonitorService;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.AmiEventBridgeService;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip.PjsipConfigQueryService;
import ai.lawyers.system.service.lawyers.trunk.gateway.ami.pjsip.PjsipRegistrationService;
import ai.lawyers.system.service.lawyers.trunk.gateway.esl.EslEventBridgeService;

/**
 * 线路监控接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class TrunkMonitorControllerTest
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
    private AmiEventBridgeService amiEventBridgeService;

    @Mock
    private PjsipConfigQueryService pjsipConfigQueryService;

    @Mock
    private PjsipRegistrationService pjsipRegistrationService;

    @InjectMocks
    private TrunkMonitorController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiTrunkMetric metric()
    {
        AiTrunkMetric metric = new AiTrunkMetric();
        metric.setTrunkId(5L);
        return metric;
    }

    @Test
    void 监控总览汇总各集群指标()
    {
        Map<String, Object> today = new HashMap<String, Object>();
        today.put("callCount", 10);
        List<Map<String, Object>> locks = new ArrayList<Map<String, Object>>();
        when(trunkMonitorService.todayOverview()).thenReturn(today);
        when(callDispatchService.getQueueSize()).thenReturn(2);
        when(callDispatchService.getGlobalConcurrent()).thenReturn(3);
        when(redisLeaderLock.scanJobLocks()).thenReturn(locks);
        when(eslEventBridgeService.isEslLeader()).thenReturn(true);
        when(eslEventBridgeService.connectedCount()).thenReturn(1);
        when(amiEventBridgeService.isAmiLeader()).thenReturn(false);
        when(amiEventBridgeService.connectedCount()).thenReturn(4);

        try (MockedStatic<CallWebSocketServer> ws = mockStatic(CallWebSocketServer.class))
        {
            ws.when(CallWebSocketServer::globalOnlineCount).thenReturn(7);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) controller.overview().get(AjaxResult.DATA_TAG);

            assertEquals(10, data.get("callCount"));
            assertEquals(2, data.get("queueSize"));
            assertEquals(3, data.get("globalConcurrent"));
            assertEquals(7, data.get("wsOnlineCount"));
            assertSame(locks, data.get("scheduledLocks"));
            assertEquals(Boolean.TRUE, data.get("eslLeader"));
            assertEquals(1, data.get("eslConnected"));
            assertEquals(Boolean.FALSE, data.get("amiLeader"));
            assertEquals(4, data.get("amiConnected"));
        }
    }

    @Test
    void 监控总览AMI未启用时默认值()
    {
        when(trunkMonitorService.todayOverview()).thenReturn(new HashMap<String, Object>());
        when(callDispatchService.getQueueSize()).thenReturn(0);
        when(callDispatchService.getGlobalConcurrent()).thenReturn(0);
        when(redisLeaderLock.scanJobLocks()).thenReturn(new ArrayList<Map<String, Object>>());
        when(eslEventBridgeService.isEslLeader()).thenReturn(false);
        when(eslEventBridgeService.connectedCount()).thenReturn(0);
        ReflectionTestUtils.setField(controller, "amiEventBridgeService", null);

        try (MockedStatic<CallWebSocketServer> ws = mockStatic(CallWebSocketServer.class))
        {
            ws.when(CallWebSocketServer::globalOnlineCount).thenReturn(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) controller.overview().get(AjaxResult.DATA_TAG);

            assertEquals(Boolean.TRUE, data.get("amiLeader"));
            assertEquals(0, data.get("amiConnected"));
        }
    }

    @Test
    void 实时状态与告警透传服务结果()
    {
        List<Map<String, Object>> trunks = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> carriers = new ArrayList<Map<String, Object>>();
        List<AiTrunkAlarm> alarms = List.of(new AiTrunkAlarm());
        when(trunkMonitorService.realtimeTrunkStatus()).thenReturn(trunks);
        when(trunkMonitorService.realtimeCarrierStat()).thenReturn(carriers);
        when(trunkMonitorService.activeAlarms()).thenReturn(alarms);

        assertSame(trunks, controller.trunkStatus().get(AjaxResult.DATA_TAG));
        assertSame(carriers, controller.carrierStat().get(AjaxResult.DATA_TAG));
        assertSame(alarms, controller.activeAlarms().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 质量趋势分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiTrunkMetric query = new AiTrunkMetric();
        List<AiTrunkMetric> list = new ArrayList<AiTrunkMetric>();
        list.add(metric());
        when(trunkMetricMapper.selectAiTrunkMetricList(query)).thenReturn(list);

        TableDataInfo table = controller.metricList(query);

        assertEquals(200, table.getCode());
        assertEquals(list, table.getRows());
    }

    @Test
    void 单线路趋势默认维度与条数限幅()
    {
        List<AiTrunkMetric> defaults = List.of(metric());
        List<AiTrunkMetric> capped = List.of(metric());
        when(trunkMetricMapper.selectRecentByTrunk(5L, "1", 60)).thenReturn(defaults);
        when(trunkMetricMapper.selectRecentByTrunk(5L, "2", 500)).thenReturn(capped);

        assertSame(defaults, controller.trend(5L, null, 0).get(AjaxResult.DATA_TAG));
        assertSame(capped, controller.trend(5L, "2", 1000).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 告警列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiTrunkAlarm query = new AiTrunkAlarm();
        List<AiTrunkAlarm> list = new ArrayList<AiTrunkAlarm>();
        list.add(new AiTrunkAlarm());
        when(trunkAlarmMapper.selectAiTrunkAlarmList(query)).thenReturn(list);

        assertEquals(list, controller.alarmList(query).getRows());
    }

    @Test
    void 处理告警缺少编号被拒绝()
    {
        Map<String, Object> param = new HashMap<String, Object>();

        AjaxResult result = controller.handleAlarm(param);

        assertEquals(500, result.get(AjaxResult.CODE_TAG));
        assertEquals("告警ID不能为空", result.get(AjaxResult.MSG_TAG));
    }

    @Test
    void 处理告警默认状态与空备注()
    {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("alarmId", 9);
        when(trunkMonitorService.handleAlarm(9L, "1", "admin", "")).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            assertEquals(200, controller.handleAlarm(param).get(AjaxResult.CODE_TAG));
        }
    }

    @Test
    void 手动健康探测返回提示()
    {
        assertEquals("健康探测已执行", controller.healthCheck().get(AjaxResult.MSG_TAG));
    }

    @Test
    void PJSIP只读接口透传配置服务()
    {
        List<ai.lawyers.system.domain.lawyers.trunk.PjsipEndpoint> endpoints =
                new ArrayList<ai.lawyers.system.domain.lawyers.trunk.PjsipEndpoint>();
        List<ai.lawyers.system.domain.lawyers.trunk.PjsipQueue> queues =
                new ArrayList<ai.lawyers.system.domain.lawyers.trunk.PjsipQueue>();
        Map<String, Object> summary = new HashMap<String, Object>();
        Map<String, Object> registrations = new HashMap<String, Object>();
        when(pjsipConfigQueryService.listEndpoints()).thenReturn(endpoints);
        when(pjsipConfigQueryService.listQueues()).thenReturn(queues);
        when(pjsipConfigQueryService.summary()).thenReturn(summary);
        when(pjsipRegistrationService.listRegistrations()).thenReturn(registrations);

        assertSame(endpoints, controller.pjsipEndpoints().get(AjaxResult.DATA_TAG));
        assertSame(queues, controller.pjsipQueues().get(AjaxResult.DATA_TAG));
        assertSame(summary, controller.pjsipSummary().get(AjaxResult.DATA_TAG));
        assertSame(registrations, controller.pjsipRegistrations().get(AjaxResult.DATA_TAG));
    }
}
