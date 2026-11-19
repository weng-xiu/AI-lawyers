package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.quality.AiQualityInspection;
import ai.lawyers.system.domain.lawyers.quality.AiQualityTemplate;
import ai.lawyers.system.mapper.lawyers.quality.AiQualityTemplateMapper;
import ai.lawyers.system.service.impl.lawyers.quality.QualityTemplateSupport;
import ai.lawyers.system.service.lawyers.quality.IAiQualityInspectionService;

/**
 * 智能质检接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class AiQualityInspectionControllerTest
{
    @Mock
    private IAiQualityInspectionService qualityInspectionService;

    @Mock
    private AiQualityTemplateMapper qualityTemplateMapper;

    @Mock
    private QualityTemplateSupport qualityTemplateSupport;

    @InjectMocks
    private AiQualityInspectionController controller;

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

    private AiQualityInspection inspection()
    {
        AiQualityInspection inspection = new AiQualityInspection();
        inspection.setInspectionId(7L);
        return inspection;
    }

    private AiQualityTemplate template()
    {
        AiQualityTemplate template = new AiQualityTemplate();
        template.setTemplateId(11L);
        template.setStatus("1");
        return template;
    }

    @Test
    void 列表返回分页数据()
    {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AiQualityInspection query = new AiQualityInspection();
        List<AiQualityInspection> list = new ArrayList<AiQualityInspection>();
        list.add(inspection());
        when(qualityInspectionService.selectInspectionList(query)).thenReturn(list);

        TableDataInfo table = controller.list(query);

        assertEquals(200, table.getCode());
        assertEquals(1, table.getTotal());
        assertEquals(list, table.getRows());
    }

    @Test
    void 导出写入Excel响应()
    {
        when(qualityInspectionService.selectInspectionList(any(AiQualityInspection.class)))
                .thenReturn(List.of(inspection()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.export(response, new AiQualityInspection());

        assertTrue(response.getContentType().contains("spreadsheetml"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void 详情透传服务结果()
    {
        AiQualityInspection inspection = inspection();
        when(qualityInspectionService.selectInspectionById(7L)).thenReturn(inspection);

        assertSame(inspection, controller.getInfo(7L).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 复核写复核人与复核时间()
    {
        AiQualityInspection inspection = inspection();
        when(qualityInspectionService.review(inspection)).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("leader"));
            assertEquals(200, controller.review(inspection).get(AjaxResult.CODE_TAG));
        }
        assertEquals(1L, inspection.getReviewerId());
        assertEquals("leader", inspection.getReviewerName());
    }

    @Test
    void 手动触发质检成功与异常()
    {
        doAnswer(invocation -> {
            if (Long.valueOf(8L).equals(invocation.getArgument(0)))
            {
                throw new RuntimeException("ASR 服务不可用");
            }
            return null;
        }).when(qualityInspectionService).inspect(any());

        assertEquals(200, controller.inspect(7L).get(AjaxResult.CODE_TAG));
        assertEquals("质检触发失败：ASR 服务不可用", controller.inspect(8L).get(AjaxResult.MSG_TAG));
    }

    @Test
    void 坐席申诉成功与参数错误()
    {
        when(qualityInspectionService.appeal(7L, "评分偏低", 1L)).thenReturn(1);
        when(qualityInspectionService.appeal(8L, "无理由", 1L))
                .thenThrow(new IllegalArgumentException("该质检记录不属于当前坐席"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("agent"));
            assertEquals(200, controller.appeal(7L, "评分偏低").get(AjaxResult.CODE_TAG));
            assertEquals("该质检记录不属于当前坐席", controller.appeal(8L, "无理由").get(AjaxResult.MSG_TAG));
        }
    }

    @Test
    void 申诉复核成功与参数错误()
    {
        AiQualityInspection inspection = inspection();
        when(qualityInspectionService.appealReview(inspection)).thenReturn(0);
        AiQualityInspection invalid = new AiQualityInspection();
        invalid.setInspectionId(8L);
        when(qualityInspectionService.appealReview(invalid))
                .thenThrow(new IllegalArgumentException("申诉状态不允许复核"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("leader"));
            assertEquals(500, controller.appealReview(inspection).get(AjaxResult.CODE_TAG));
            assertEquals("申诉状态不允许复核", controller.appealReview(invalid).get(AjaxResult.MSG_TAG));
        }
        assertEquals("leader", inspection.getReviewerName());
    }

    @Test
    void 模板列表透传Mappper结果()
    {
        AiQualityTemplate query = new AiQualityTemplate();
        List<AiQualityTemplate> list = List.of(template());
        when(qualityTemplateMapper.selectTemplateList(query)).thenReturn(list);

        assertSame(list, controller.templateList(query).get(AjaxResult.DATA_TAG));
    }

    @Test
    void 新增模板成功与校验失败()
    {
        AiQualityTemplate template = template();
        AiQualityTemplate invalid = new AiQualityTemplate();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == invalid)
            {
                throw new IllegalArgumentException("维度权重之和必须为100");
            }
            return null;
        }).when(qualityTemplateSupport).validate(any(AiQualityTemplate.class));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("leader"));
            assertEquals(11L, controller.addTemplate(template).get(AjaxResult.DATA_TAG));
            assertEquals("维度权重之和必须为100", controller.addTemplate(invalid).get(AjaxResult.MSG_TAG));
        }
        assertEquals("leader", template.getCreateBy());
        verify(qualityTemplateMapper).insertTemplate(template);
        verify(qualityTemplateMapper, never()).insertTemplate(invalid);
    }

    @Test
    void 修改模板参数校验与不存在()
    {
        AiQualityTemplate noId = new AiQualityTemplate();
        assertEquals("质检模板不存在", controller.editTemplate(noId).get(AjaxResult.MSG_TAG));

        AiQualityTemplate missing = new AiQualityTemplate();
        missing.setTemplateId(99L);
        when(qualityTemplateMapper.selectTemplateById(99L)).thenReturn(null);
        assertEquals("质检模板不存在", controller.editTemplate(missing).get(AjaxResult.MSG_TAG));
        verify(qualityTemplateMapper, never()).updateTemplate(any(AiQualityTemplate.class));
    }

    @Test
    void 修改模板成功与校验失败()
    {
        AiQualityTemplate template = template();
        when(qualityTemplateMapper.selectTemplateById(11L)).thenReturn(template);
        when(qualityTemplateMapper.updateTemplate(template)).thenReturn(1);
        AiQualityTemplate invalid = template();
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == invalid)
            {
                throw new IllegalArgumentException("模板名称不能为空");
            }
            return null;
        }).when(qualityTemplateSupport).validate(any(AiQualityTemplate.class));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("leader"));
            assertEquals(200, controller.editTemplate(template).get(AjaxResult.CODE_TAG));
            assertEquals("模板名称不能为空", controller.editTemplate(invalid).get(AjaxResult.MSG_TAG));
        }
        assertEquals("leader", template.getUpdateBy());
    }

    @Test
    void 设默认模板三种结果()
    {
        when(qualityTemplateMapper.selectTemplateById(11L)).thenReturn(template());
        AiQualityTemplate disabled = template();
        disabled.setTemplateId(12L);
        disabled.setStatus("0");
        when(qualityTemplateMapper.selectTemplateById(12L)).thenReturn(disabled);
        when(qualityTemplateMapper.selectTemplateById(13L)).thenReturn(null);
        when(qualityTemplateMapper.updateTemplate(any(AiQualityTemplate.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getLoginUser).thenReturn(loginUser("leader"));
            assertEquals(200, controller.setDefaultTemplate(11L).get(AjaxResult.CODE_TAG));
        }
        assertEquals("质检模板不存在", controller.setDefaultTemplate(13L).get(AjaxResult.MSG_TAG));
        assertEquals("停用状态的模板不能设为生效模板",
                controller.setDefaultTemplate(12L).get(AjaxResult.MSG_TAG));
        verify(qualityTemplateMapper).clearDefault();
    }
}
