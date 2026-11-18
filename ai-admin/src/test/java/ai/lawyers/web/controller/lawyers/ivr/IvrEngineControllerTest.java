package ai.lawyers.web.controller.lawyers.ivr;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.domain.lawyers.ivr.IntentionMatchResult;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteRequest;
import ai.lawyers.system.domain.lawyers.ivr.IvrExecuteResult;
import ai.lawyers.system.service.lawyers.ivr.engine.IIvrEngineService;
import ai.lawyers.system.service.lawyers.ivr.engine.IntentionRecognitionService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * IVR 引擎接口测试：流程模拟执行与意图识别的参数透传。
 *
 * @author ai-lawyers
 */
class IvrEngineControllerTest
{
    private IvrEngineController controller;

    private IIvrEngineService ivrEngineService;

    private IntentionRecognitionService intentionRecognitionService;

    @BeforeEach
    void setUp()
    {
        ivrEngineService = mock(IIvrEngineService.class);
        intentionRecognitionService = mock(IntentionRecognitionService.class);
        controller = new IvrEngineController();
        ReflectionTestUtils.setField(controller, "ivrEngineService", ivrEngineService);
        ReflectionTestUtils.setField(controller, "intentionRecognitionService", intentionRecognitionService);
    }

    @Test
    void 模拟执行返回引擎结果()
    {
        IvrExecuteRequest request = new IvrExecuteRequest();
        IvrExecuteResult result = new IvrExecuteResult();
        result.setSuccess(true);
        when(ivrEngineService.executeFlow(request)).thenReturn(result);

        AjaxResult response = controller.execute(request);

        assertTrue(response.isSuccess());
        assertSame(result, response.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 意图识别透传文本()
    {
        IntentionMatchResult match = IntentionMatchResult.none("未匹配");
        when(intentionRecognitionService.recognize("我要咨询离婚")).thenReturn(match);
        Map<String, String> param = new HashMap<>();
        param.put("text", "我要咨询离婚");

        AjaxResult response = controller.intention(param);

        assertSame(match, response.get(AjaxResult.DATA_TAG));
    }

    @Test
    void 意图识别参数为空时传入空文本()
    {
        IntentionMatchResult match = IntentionMatchResult.none("文本为空");
        when(intentionRecognitionService.recognize(null)).thenReturn(match);

        AjaxResult response = controller.intention(null);

        assertSame(match, response.get(AjaxResult.DATA_TAG));
    }
}
