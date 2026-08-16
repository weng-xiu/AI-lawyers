package ai.lawyers.web.controller.lawyers.sms;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.sms.AiSmsLog;
import ai.lawyers.system.domain.lawyers.sms.SmsResult;
import ai.lawyers.system.mapper.lawyers.sms.AiSmsLogMapper;
import ai.lawyers.system.service.lawyers.sms.ISmsService;

/**
 * 短信发送记录Controller（含调试发送）
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/sms/log")
public class AiSmsLogController extends BaseController
{
    @Autowired
    private AiSmsLogMapper aiSmsLogMapper;

    @Autowired
    private ISmsService smsService;

    /**
     * 短信发送记录列表
     */
    @PreAuthorize("@ss.hasPermi('lawyers:smsLog:view')")
    @GetMapping("/list")
    public TableDataInfo list(AiSmsLog query)
    {
        startPage();
        return getDataTable(aiSmsLogMapper.selectAiSmsLogList(query));
    }

    /**
     * 调试发送（不经过 IVR 流程，直接调用短信服务）
     */
    @PostMapping("/send")
    public AjaxResult send(@RequestBody Map<String, Object> body)
    {
        String phone = body.get("phone") == null ? null : String.valueOf(body.get("phone"));
        Object templateIdObj = body.get("templateId");
        Long templateId = templateIdObj instanceof Number ? ((Number) templateIdObj).longValue() : null;
        String sessionId = body.get("sessionId") == null ? null : String.valueOf(body.get("sessionId"));
        Object recordIdObj = body.get("recordId");
        Long recordId = recordIdObj instanceof Number ? ((Number) recordIdObj).longValue() : null;

        Map<String, String> params = new HashMap<>();
        Object paramsObj = body.get("params");
        if (paramsObj instanceof Map)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) paramsObj).entrySet())
            {
                params.put(String.valueOf(entry.getKey()),
                        entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }

        SmsResult result = smsService.send(phone, templateId, params, sessionId, recordId);
        return result.isSuccess() ? success(result) : AjaxResult.error(result.getMessage(), result);
    }

    /**
     * 删除短信记录
     */
    @DeleteMapping("/{logIds}")
    public AjaxResult remove(@PathVariable Long[] logIds)
    {
        return toAjax(aiSmsLogMapper.deleteAiSmsLogByLogIds(logIds));
    }
}
