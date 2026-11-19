package ai.lawyers.web.controller.lawyers.trunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.trunk.AiCallTrunk;
import ai.lawyers.system.domain.lawyers.trunk.AiNumberSegment;
import ai.lawyers.system.domain.lawyers.trunk.DialResult;
import ai.lawyers.system.service.lawyers.trunk.IAiCallTrunkService;
import ai.lawyers.system.service.lawyers.trunk.ICarrierRouteService;
import ai.lawyers.system.service.lawyers.trunk.gateway.GatewayHealth;

/**
 * 运营商中继线路接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiCallTrunkControllerTest
{
    @Mock
    private IAiCallTrunkService aiCallTrunkService;

    @Mock
    private ICarrierRouteService carrierRouteService;

    @InjectMocks
    private AiCallTrunkController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private AiCallTrunk trunk()
    {
        AiCallTrunk trunk = new AiCallTrunk();
        trunk.setTrunkId(5L);
        return trunk;
    }

    @Test
    void 线路列表分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiCallTrunk query = new AiCallTrunk();
        List<AiCallTrunk> list = new ArrayList<AiCallTrunk>();
        list.add(trunk());
        when(aiCallTrunkService.selectAiCallTrunkList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertSame(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(aiCallTrunkService.selectAiCallTrunkList(any(AiCallTrunk.class))).thenReturn(List.of(trunk()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiCallTrunk());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情与新增修改删除()
    {
        AiCallTrunk trunk = trunk();
        Long[] ids = new Long[] { 5L };
        when(aiCallTrunkService.selectAiCallTrunkByTrunkId(5L)).thenReturn(trunk);
        when(aiCallTrunkService.insertAiCallTrunk(trunk)).thenReturn(1);
        when(aiCallTrunkService.updateAiCallTrunk(trunk)).thenReturn(0);
        when(aiCallTrunkService.deleteAiCallTrunkByTrunkIds(ids)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            assertSame(trunk, controller.getInfo(5L).get(AjaxResult.DATA_TAG));
            assertEquals(200, controller.add(trunk).get(AjaxResult.CODE_TAG));
            assertEquals(500, controller.edit(trunk).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", trunk.getCreateBy());
        assertEquals("admin", trunk.getUpdateBy());
        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 启停线路()
    {
        AiCallTrunk trunk = trunk();
        trunk.setEnableFlag("1");
        when(aiCallTrunkService.changeEnableFlag(5L, "1")).thenReturn(1);

        assertEquals(200, controller.changeStatus(trunk).get(AjaxResult.CODE_TAG));
        verify(aiCallTrunkService).changeEnableFlag(5L, "1");
    }

    @Test
    void 连通测试返回健康信息()
    {
        GatewayHealth up = GatewayHealth.up(20L);
        up.setRegisterState("OK");
        GatewayHealth down = GatewayHealth.down("超时");
        when(aiCallTrunkService.testTrunk(5L)).thenReturn(up);
        when(aiCallTrunkService.testTrunk(6L)).thenReturn(down);

        AjaxResult ok = controller.test(5L);
        assertEquals("线路连通正常", ok.get(AjaxResult.MSG_TAG));
        assertEquals(Boolean.TRUE, ok.get("reachable"));
        assertEquals(20L, ok.get("latencyMs"));
        assertEquals("OK", ok.get("registerState"));

        AjaxResult fail = controller.test(6L);
        assertEquals("线路不可达: 超时", fail.get(AjaxResult.MSG_TAG));
        assertEquals(Boolean.FALSE, fail.get("reachable"));
    }

    @Test
    void 拨测号码为空被拒绝()
    {
        assertEquals("拨测号码不能为空", controller.testCall(5L, "  ").get(AjaxResult.MSG_TAG));
        verify(aiCallTrunkService, org.mockito.Mockito.never()).testCall(any(), any());
    }

    @Test
    void 拨测成功与失败消息()
    {
        DialResult ok = DialResult.ok("uuid-1");
        DialResult fail = DialResult.fail(null, null);
        when(aiCallTrunkService.testCall(5L, "13800000000")).thenReturn(ok);
        when(aiCallTrunkService.testCall(6L, "13800000000")).thenReturn(fail);

        AjaxResult okResult = controller.testCall(5L, " 13800000000 ");
        assertEquals("拨测已下发", okResult.get(AjaxResult.MSG_TAG));
        assertEquals(Boolean.TRUE, okResult.get("success"));
        assertEquals("uuid-1", okResult.get("callUuid"));

        AjaxResult failResult = controller.testCall(6L, "13800000000");
        assertEquals("拨测失败", failResult.get(AjaxResult.MSG_TAG));
        assertEquals(Boolean.FALSE, failResult.get("success"));
    }

    @Test
    void 号段列表新增修改删除()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiNumberSegment query = new AiNumberSegment();
        AiNumberSegment segment = new AiNumberSegment();
        segment.setSegmentId(3L);
        List<AiNumberSegment> list = new ArrayList<AiNumberSegment>();
        list.add(segment);
        Long[] ids = new Long[] { 3L };
        when(aiCallTrunkService.selectAiNumberSegmentList(query)).thenReturn(list);
        when(aiCallTrunkService.insertAiNumberSegment(segment)).thenReturn(1);
        when(aiCallTrunkService.updateAiNumberSegment(segment)).thenReturn(1);
        when(aiCallTrunkService.deleteAiNumberSegmentBySegmentIds(ids)).thenReturn(1);

        assertSame(list, controller.segmentList(query).getRows());
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn("admin");
            assertEquals(200, controller.segmentAdd(segment).get(AjaxResult.CODE_TAG));
            assertEquals(200, controller.segmentEdit(segment).get(AjaxResult.CODE_TAG));
        }
        assertEquals("admin", segment.getCreateBy());
        assertEquals("admin", segment.getUpdateBy());
        assertEquals(200, controller.segmentRemove(ids).get(AjaxResult.CODE_TAG));
    }

    @Test
    void 号码识别与候选线路()
    {
        AiNumberSegment segment = new AiNumberSegment();
        List<AiCallTrunk> candidates = List.of(trunk());
        when(carrierRouteService.recognizeCarrier("13800000000")).thenReturn("CMCC");
        when(carrierRouteService.recognizeSegment("13800000000")).thenReturn(segment);
        when(carrierRouteService.selectCandidateTrunks("CMCC", null)).thenReturn(candidates);

        AjaxResult result = controller.recognize("13800000000");

        assertEquals("13800000000", result.get("number"));
        assertEquals("CMCC", result.get("carrier"));
        assertSame(segment, result.get("segment"));
        assertSame(candidates, result.get("candidateTrunks"));
    }

    @Test
    void 刷新号段缓存()
    {
        assertEquals("号段缓存已刷新", controller.refreshSegment().get(AjaxResult.MSG_TAG));
        verify(carrierRouteService).refreshSegmentCache();
    }
}
