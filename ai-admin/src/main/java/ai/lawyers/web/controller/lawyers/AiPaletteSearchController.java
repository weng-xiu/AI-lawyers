package ai.lawyers.web.controller.lawyers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.framework.web.service.PermissionService;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiLegalKnowledgeService;
import ai.lawyers.system.utils.trunk.NumberTransformUtils;

/**
 * U3 命令面板聚合搜索（Ctrl+K 直达）。
 *
 * <p>按当前登录用户权限逐域检索，无对应权限的域自动跳过（面板对所有登录用户可用，
 * 结果随权限收敛）：</p>
 * <ul>
 *   <li>工单：ticketNo like；</li>
 *   <li>知识：title like；</li>
 *   <li>话单：来电号码——走 ai_pii_search_token 盲 token 检索（G1-b2，参见
 *       AiCallRecordServiceImpl.selectAiCallRecordList），返回号码统一脱敏展示。</li>
 * </ul>
 *
 * <p>菜单直达由前端基于本地路由树搜索，不经过本接口。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/search")
public class AiPaletteSearchController extends BaseController
{
    /** 各域单次返回上限（防面板结果过长） */
    private static final int MAX_LIMIT = 10;

    @Autowired
    private IAiCallTicketService ticketService;

    @Autowired
    private IAiLegalKnowledgeService knowledgeService;

    @Autowired
    private IAiCallRecordService callRecordService;

    /** 编程式权限判断：面板接口本身不设权限，按域裁剪结果 */
    @Autowired
    private PermissionService permissionService;

    /**
     * 聚合搜索：keyword 匹配工单号 / 知识标题 / 来电号码。
     *
     * @param keyword 关键词
     * @param limit   各域返回条数上限（1-10，默认 5）
     */
    @GetMapping("/palette")
    public AjaxResult palette(@RequestParam("keyword") String keyword,
                              @RequestParam(value = "limit", defaultValue = "5") int limit)
    {
        String kw = keyword == null ? "" : keyword.trim();
        Map<String, Object> data = new HashMap<>();
        if (StringUtils.isEmpty(kw))
        {
            return success(data);
        }
        int top = Math.max(1, Math.min(MAX_LIMIT, limit));

        // 工单域：ticketNo like
        if (permissionService.hasPermi("lawyers:call:ticket:list"))
        {
            AiCallTicket query = new AiCallTicket();
            query.setTicketNo(kw);
            PageHelper.startPage(1, top, false);
            List<AiCallTicket> tickets = ticketService.selectAiCallTicketList(query);
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiCallTicket t : tickets)
            {
                Map<String, Object> item = new HashMap<>();
                item.put("id", t.getTicketId());
                item.put("title", t.getTicketNo());
                item.put("sub", t.getTitle());
                items.add(item);
            }
            data.put("tickets", items);
        }

        // 知识域：title like
        if (permissionService.hasPermi("lawyers:knowledge:list"))
        {
            AiLegalKnowledge query = new AiLegalKnowledge();
            query.setTitle(kw);
            PageHelper.startPage(1, top, false);
            List<AiLegalKnowledge> knowledges = knowledgeService.selectAiLegalKnowledgeList(query);
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiLegalKnowledge k : knowledges)
            {
                Map<String, Object> item = new HashMap<>();
                item.put("id", k.getKnowledgeId());
                item.put("title", k.getTitle());
                item.put("sub", k.getKeywords());
                items.add(item);
            }
            data.put("knowledges", items);
        }

        // 话单域：来电号码（PII 盲 token 检索），号码脱敏返回
        if (permissionService.hasPermi("lawyers:call:record:list"))
        {
            AiCallRecord query = new AiCallRecord();
            query.setCallerNumber(kw);
            PageHelper.startPage(1, top, false);
            List<AiCallRecord> records = callRecordService.selectAiCallRecordList(query);
            List<Map<String, Object>> items = new ArrayList<>();
            for (AiCallRecord r : records)
            {
                Map<String, Object> item = new HashMap<>();
                item.put("id", r.getRecordId());
                item.put("title", NumberTransformUtils.mask(r.getCallerNumber()));
                item.put("sub", r.getCallTime() != null
                        ? ai.lawyers.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd HH:mm", r.getCallTime())
                        : "");
                items.add(item);
            }
            data.put("records", items);
        }

        return success(data);
    }
}
