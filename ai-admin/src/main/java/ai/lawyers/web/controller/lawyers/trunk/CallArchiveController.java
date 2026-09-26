package ai.lawyers.web.controller.lawyers.trunk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.mapper.lawyers.AiCallRecordArchiveMapper;

/**
 * 话单归档查询 Controller（P3-F1 冷热分离，只读）。
 *
 * <p>查询对象为按月分区的归档表 {@code ai_call_record_archive}
 * （超过在线保留期的历史话单由 DataArchiveTask 迁移至此），应用侧不提供任何
 * 写操作；归档到期回收由 DBA 按合规保留期 DROP PARTITION 完成。前端页面随
 * F2 录音对象存储批次补充，当前通过权限点 lawyers:trunk:callArchive:* 提供
 * API 查询能力。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/trunk/callArchive")
public class CallArchiveController extends BaseController
{
    @Autowired
    private AiCallRecordArchiveMapper archiveMapper;

    /**
     * 归档话单分页列表（号码/姓名模糊、状态、坐席、来电时间区间）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:callArchive:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallRecord query)
    {
        startPage();
        return getDataTable(archiveMapper.selectArchiveList(query));
    }

    /**
     * 归档话单详情。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:callArchive:query')")
    @GetMapping("/{recordId}")
    public AjaxResult detail(@PathVariable("recordId") Long recordId)
    {
        return success(archiveMapper.selectArchiveByRecordId(recordId));
    }

    /**
     * 归档规模（冷数据总量观测）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:trunk:callArchive:list')")
    @GetMapping("/stat")
    public AjaxResult stat()
    {
        return success(archiveMapper.countArchive());
    }
}
