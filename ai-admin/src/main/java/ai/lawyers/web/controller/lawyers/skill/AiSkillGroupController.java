package ai.lawyers.web.controller.lawyers.skill;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroup;
import ai.lawyers.system.domain.lawyers.skill.AiSkillGroupMember;
import ai.lawyers.system.service.lawyers.skill.IAiSkillGroupService;

/**
 * 技能组Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/skill/group")
public class AiSkillGroupController extends BaseController
{
    @Autowired
    private IAiSkillGroupService aiSkillGroupService;

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:query')")
    @GetMapping("/list")
    public TableDataInfo list(AiSkillGroup aiSkillGroup)
    {
        startPage();
        List<AiSkillGroup> list = aiSkillGroupService.selectAiSkillGroupList(aiSkillGroup);
        return getDataTable(list);
    }

    /**
     * 下拉用：所有启用技能组（不分页）
     */
    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:query')")
    @GetMapping("/listEnabled")
    public AjaxResult listEnabled()
    {
        return success(aiSkillGroupService.selectEnabledGroups());
    }

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:export')")
    @Log(title = "技能组", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult export(AiSkillGroup aiSkillGroup)
    {
        List<AiSkillGroup> list = aiSkillGroupService.selectAiSkillGroupList(aiSkillGroup);
        ExcelUtil<AiSkillGroup> util = new ExcelUtil<>(AiSkillGroup.class);
        return util.exportExcel(list, "技能组数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:query')")
    @GetMapping(value = "/{groupId}")
    public AjaxResult getInfo(@PathVariable("groupId") Long groupId)
    {
        AiSkillGroup group = aiSkillGroupService.selectAiSkillGroupByGroupId(groupId);
        if (group != null)
        {
            group.setMembers(aiSkillGroupService.selectMembersByGroupId(groupId));
        }
        return success(group);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:add')")
    @Log(title = "技能组", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSkillGroup aiSkillGroup)
    {
        aiSkillGroup.setCreateBy(getUsername());
        return toAjax(aiSkillGroupService.insertAiSkillGroup(aiSkillGroup));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:edit')")
    @Log(title = "技能组", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSkillGroup aiSkillGroup)
    {
        aiSkillGroup.setUpdateBy(getUsername());
        return toAjax(aiSkillGroupService.updateAiSkillGroup(aiSkillGroup));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:remove')")
    @Log(title = "技能组", businessType = BusinessType.DELETE)
    @DeleteMapping("/{groupIds}")
    public AjaxResult remove(@PathVariable Long[] groupIds)
    {
        return toAjax(aiSkillGroupService.deleteAiSkillGroupByGroupIds(groupIds));
    }

    // ---- 成员管理 ----

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:query')")
    @GetMapping("/members/{groupId}")
    public AjaxResult members(@PathVariable Long groupId)
    {
        return success(aiSkillGroupService.selectMembersByGroupId(groupId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:edit')")
    @Log(title = "技能组成员", businessType = BusinessType.UPDATE)
    @PostMapping("/members/add")
    public AjaxResult addMembers(@RequestBody AiSkillGroupMember member)
    {
        return toAjax(aiSkillGroupService.addMembers(member.getGroupId(),
                member.getAgentIds(), member.getSkillLevel()));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:skillGroup:edit')")
    @Log(title = "技能组成员", businessType = BusinessType.UPDATE)
    @PostMapping("/members/remove")
    public AjaxResult removeMembers(@RequestBody AiSkillGroupMember member)
    {
        return toAjax(aiSkillGroupService.removeMembers(member.getGroupId(), member.getAgentIds()));
    }
}
