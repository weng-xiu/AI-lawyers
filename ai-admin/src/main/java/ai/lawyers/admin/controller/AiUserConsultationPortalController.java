package ai.lawyers.admin.controller;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.annotation.RateLimiter;
import ai.lawyers.common.annotation.RepeatSubmit;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.enums.LimitType;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;

/**
 * 公众端法律咨询Controller（F8 公众端深化，2026-09-17）
 *
 * <p>与坐席端 {@code /lawyers/userConsultation} 分离：本控制器走 /aiuser/** 用户端安全过滤链
 * （{@code AiUserSecurityConfig}，公众用户 JWT），所有读写强制按登录态 userId 归属，
 * 杜绝自增 ID 遍历的 IDOR；提交接口挂限流（IP 维度）与防重复提交，文本经内容安全校验。</p>
 *
 * @author AI律师
 */
@RestController
@RequestMapping("/aiuser/consultation")
public class AiUserConsultationPortalController extends BaseController
{
    @Autowired
    private IAiUserConsultationService consultationService;

    /**
     * 提交法律咨询（multipart：category/content/files）
     * 限流：同 IP 60 秒最多 10 次；防重：相同内容 5 秒内拒绝重复提交。
     */
    @Log(title = "公众端用户咨询", businessType = BusinessType.INSERT)
    @RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
    @RepeatSubmit(interval = 5000)
    @PostMapping("/submit")
    public AjaxResult submit(HttpServletRequest request)
    {
        MultipartHttpServletRequest multipart = (MultipartHttpServletRequest) request;
        String category = multipart.getParameter("category");
        String content = multipart.getParameter("content");
        List<MultipartFile> files = multipart.getFiles("files");
        // 内容长度/敏感词校验在 service 内统一执行；userId 由服务端按登录态写入
        return AjaxResult.success("咨询提交成功", consultationService.submitConsultation(category, content, files));
    }

    /**
     * 获取本人咨询结果（对象级归属校验）
     */
    @GetMapping("/result/{id}")
    public AjaxResult result(@PathVariable("id") Long id)
    {
        return AjaxResult.success(consultationService.selectOwnConsultationById(id));
    }

    /**
     * 获取本人咨询详情（对象级归属校验）
     */
    @GetMapping("/info/{id}")
    public AjaxResult info(@PathVariable("id") Long id)
    {
        return AjaxResult.success(consultationService.selectOwnConsultationById(id));
    }

    /**
     * 本人咨询历史（强制按登录态 userId 过滤，忽略前端传入的 userId）
     */
    @GetMapping("/history")
    public TableDataInfo history(AiUserConsultation query)
    {
        query.setUserId(SecurityUtils.getUserId());
        startPage();
        List<AiUserConsultation> list = consultationService.selectAiUserConsultationList(query);
        return getDataTable(list);
    }

    /**
     * 提交咨询评价（总体评分 + 文字反馈；归属、重复评价、四维兜底在 service 内处理）
     * 限流：同 IP 60 秒最多 20 次；防重 5 秒。
     */
    @Log(title = "公众端咨询评价", businessType = BusinessType.INSERT)
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @RepeatSubmit(interval = 5000)
    @PostMapping("/evaluation")
    public AjaxResult evaluation(@RequestBody AiUserEvaluation evaluation)
    {
        consultationService.submitOwnEvaluation(evaluation);
        return AjaxResult.success("评价提交成功");
    }
}
