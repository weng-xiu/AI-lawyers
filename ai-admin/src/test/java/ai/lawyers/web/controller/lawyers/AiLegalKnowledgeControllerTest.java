package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.KnowledgeHitStat;
import ai.lawyers.system.domain.lawyers.KnowledgeUnmatchedQuestion;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.system.service.lawyers.KnowledgeDraftService;
import ai.lawyers.system.service.lawyers.KnowledgeOpsService;
import ai.lawyers.system.service.lawyers.rag.IRagIndexService;
import ai.lawyers.system.service.lawyers.rag.RagSearchService;
import ai.lawyers.system.service.lawyers.rag.VectorIndexRouter;

/**
 * 法律知识库接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiLegalKnowledgeControllerTest
{
    @Mock
    private IAiLegalKnowledgeService knowledgeService;

    @Mock
    private IRagIndexService ragIndexService;

    @Mock
    private VectorIndexRouter vectorIndexRouter;

    @Mock
    private RagSearchService ragSearchService;

    @Mock
    private KnowledgeDraftService knowledgeDraftService;

    @Mock
    private KnowledgeOpsService knowledgeOpsService;

    @InjectMocks
    private AiLegalKnowledgeController controller;

    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private LoginUser loginUser(String username)
    {
        SysUser user = new SysUser();
        user.setUserName(username);
        return new LoginUser(1L, 2L, user, null);
    }

    private AiLegalKnowledge knowledge()
    {
        AiLegalKnowledge knowledge = new AiLegalKnowledge();
        knowledge.setKnowledgeId(9L);
        knowledge.setTitle("劳动合同纠纷处理指引");
        return knowledge;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiLegalKnowledge query = new AiLegalKnowledge();
        List<AiLegalKnowledge> list = new ArrayList<AiLegalKnowledge>();
        list.add(knowledge());
        when(knowledgeService.selectAiLegalKnowledgeList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(knowledgeService.selectAiLegalKnowledgeList(any(AiLegalKnowledge.class)))
                .thenReturn(List.of(knowledge()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiLegalKnowledge());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情分类与关键词查询透传()
    {
        AiLegalKnowledge knowledge = knowledge();
        List<AiLegalKnowledge> list = List.of(knowledge);
        when(knowledgeService.selectAiLegalKnowledgeByKnowledgeId(9L)).thenReturn(knowledge);
        when(knowledgeService.selectAiLegalKnowledgeByCategoryId(3L)).thenReturn(list);
        when(knowledgeService.searchAiLegalKnowledge("劳动")).thenReturn(list);

        assertSame(knowledge, controller.getInfo(9L).get(AjaxResult.DATA_TAG));
        assertSame(list, controller.getKnowledgeByCategory(3L).get(AjaxResult.DATA_TAG));
        assertSame(list, controller.searchKnowledge("劳动").get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增写操作人()
    {
        AiLegalKnowledge knowledge = knowledge();
        when(knowledgeService.insertAiLegalKnowledge(knowledge)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("editor"));
            assertEquals(200, controller.add(knowledge).get(AjaxResult.CODE_TAG));
        }
        assertEquals("editor", knowledge.getCreateBy());
    }

    @Test
    void 修改写更新人()
    {
        AiLegalKnowledge knowledge = knowledge();
        when(knowledgeService.updateAiLegalKnowledge(knowledge)).thenReturn(0);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("editor"));
            assertEquals(500, controller.edit(knowledge).get(AjaxResult.CODE_TAG));
        }
        assertEquals("editor", knowledge.getUpdateBy());
    }

    @Test
    void 批量删除返回影响行数()
    {
        Long[] ids = new Long[] { 1L, 2L };
        when(knowledgeService.deleteAiLegalKnowledgeByKnowledgeIds(ids)).thenReturn(2);

        assertEquals(200, controller.remove(ids).get(AjaxResult.CODE_TAG));
        verify(knowledgeService).deleteAiLegalKnowledgeByKnowledgeIds(ids);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 批量导入返回导入结果() throws Exception
    {
        List<AiLegalKnowledge> imported = List.of(knowledge());
        MockMultipartFile file = new MockMultipartFile("file", "knowledge.xlsx", "application/vnd.ms-excel",
                new byte[] { 1 });
        when(knowledgeService.importKnowledge(imported, true, "editor")).thenReturn("导入成功 1 条");

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class);
                MockedConstruction<ExcelUtil> construction = mockConstruction(ExcelUtil.class,
                        (mock, context) -> when(mock.importExcel(any(InputStream.class))).thenReturn(imported)))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("editor"));
            assertEquals("导入成功 1 条", controller.importData(file, true).get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 导入模板写入Excel响应()
    {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.importTemplate(response);

        assertTrue(response.getContentType().contains("spreadsheetml"));
    }

    @Test
    void 审核写审核人与时间()
    {
        AiLegalKnowledge knowledge = knowledge();
        when(knowledgeService.auditAiLegalKnowledge(knowledge)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("auditor"));
            assertEquals(200, controller.audit(knowledge).get(AjaxResult.CODE_TAG));
        }
        assertEquals("auditor", knowledge.getAuditBy());
        assertTrue(knowledge.getAuditTime() != null);
    }

    @Test
    void RAG重建与索引信息()
    {
        Map<String, Object> rerankStatus = new HashMap<String, Object>();
        rerankStatus.put("enabled", Boolean.TRUE);
        when(ragIndexService.indexedVectorCount()).thenReturn(12);
        when(vectorIndexRouter.backend()).thenReturn("memory");
        when(ragSearchService.rerankStatus()).thenReturn(rerankStatus);

        assertEquals("RAG 索引重建任务已提交，正在后台执行",
                controller.rebuildRagIndex().get(AjaxResult.MSG_TAG));
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) controller.ragIndexInfo().get(AjaxResult.DATA_TAG);
        assertEquals(12, info.get("vectorCount"));
        assertEquals("memory", info.get("backend"));
        assertEquals(Boolean.TRUE, info.get("rerankEnabled"));
        verify(ragIndexService).rebuildAllAsync();
    }

    @Test
    void rerank联调透传结果()
    {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("ok", true);
        when(ragSearchService.testRerank()).thenReturn(result);

        assertSame(result, controller.ragRerankTest().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 通话生成草稿成功与参数错误()
    {
        when(knowledgeDraftService.draftFromCall(1L)).thenReturn(5L);
        when(knowledgeDraftService.draftFromCall(2L)).thenThrow(new IllegalArgumentException("该通话已沉淀知识"));
        when(knowledgeDraftService.draftFromTicket(3L)).thenReturn(6L);
        when(knowledgeDraftService.draftFromTicket(4L)).thenThrow(new IllegalArgumentException("工单不存在"));

        assertEquals(5L, controller.draftFromCall(1L).get(AjaxResult.DATA_TAG));
        assertEquals("该通话已沉淀知识", controller.draftFromCall(2L).get(AjaxResult.MSG_TAG));
        assertEquals(6L, controller.draftFromTicket(3L).get(AjaxResult.DATA_TAG));
        assertEquals("工单不存在", controller.draftFromTicket(4L).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 扫描优质通话生成草稿()
    {
        when(knowledgeDraftService.scanQualityCalls(7, new BigDecimal("4.5"), 20)).thenReturn(3);

        assertEquals("扫描完成，新生成待审知识草稿 3 条",
                controller.draftScan(7, new BigDecimal("4.5"), 20).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 标记法规效力传操作人()
    {
        when(knowledgeService.markValidStatus(9L, "3", "已废止", "editor")).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("editor"));
            assertEquals(200, controller.markValidStatus(9L, "3", "已废止").get(AjaxResult.CODE_TAG));
        }
        verify(knowledgeService).markValidStatus(9L, "3", "已废止", "editor");
    }

    @Test
    void 运营总览与高频知识透传()
    {
        Map<String, Object> overview = new HashMap<String, Object>();
        List<KnowledgeHitStat> top = new ArrayList<KnowledgeHitStat>();
        when(knowledgeOpsService.overview("2026-11-01", "2026-11-20")).thenReturn(overview);
        when(knowledgeOpsService.topKnowledge("2026-11-01", "2026-11-20", 10)).thenReturn(top);

        assertSame(overview, controller.opsOverview("2026-11-01", "2026-11-20").get(AjaxResult.DATA_TAG));
        assertSame(top, controller.opsTopKnowledge("2026-11-01", "2026-11-20", 10).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 未命中问句分页返回()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        List<KnowledgeUnmatchedQuestion> list = new ArrayList<KnowledgeUnmatchedQuestion>();
        when(knowledgeOpsService.listUnmatchedQuestions(null, null)).thenReturn(list);

        TableDataInfo table = controller.opsUnmatched(null, null);

        assertEquals(200, table.getCode());
        assertEquals(list, table.getRows());
    }

    @Test
    void 未命中问句转草稿成功与失败()
    {
        Map<String, String> body = new HashMap<String, String>();
        body.put("question", "试用期被辞退如何赔偿");
        body.put("queryHash", "hash-1");
        when(knowledgeOpsService.draftFromQuestion("试用期被辞退如何赔偿", "hash-1")).thenReturn(11L);
        when(knowledgeOpsService.draftFromQuestion("问题为空", null))
                .thenThrow(new IllegalArgumentException("问句不能为空"));
        when(knowledgeOpsService.draftFromQuestion(null, null))
                .thenThrow(new IllegalArgumentException("问句不能为空"));

        AjaxResult success = controller.opsDraft(body);
        assertEquals("已生成待审核知识草稿", success.get(AjaxResult.MSG_TAG));
        assertEquals(11L, success.get(AjaxResult.DATA_TAG));

        Map<String, String> badBody = new HashMap<String, String>();
        badBody.put("question", "问题为空");
        assertEquals("问句不能为空", controller.opsDraft(badBody).get(AjaxResult.MSG_TAG));
        assertEquals("问句不能为空", controller.opsDraft(null).get(AjaxResult.MSG_TAG));
    }
}
