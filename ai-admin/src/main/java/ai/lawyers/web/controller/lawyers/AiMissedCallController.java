package ai.lawyers.web.controller.lawyers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.page.TableDataInfo;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiMissedCall;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiMissedCallService;

/**
 * 未接来电Controller
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/call/missed")
public class AiMissedCallController extends BaseController
{
    @Autowired
    private IAiMissedCallService aiMissedCallService;

    @Autowired
    private IAiCallTicketService aiCallTicketService;

    /** 语音留言录音文件基础路径，默认复用 FreeSWITCH 录音目录 */
    @Value("${call.recording.base-path:C:/Program Files/FreeSWITCH/recordings}")
    private String recordingBasePath;

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiMissedCall aiMissedCall)
    {
        startPage();
        List<AiMissedCall> list = aiMissedCallService.selectAiMissedCallList(aiMissedCall);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:export')")
    @Log(title = "未接来电", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiMissedCall aiMissedCall)
    {
        List<AiMissedCall> list = aiMissedCallService.selectAiMissedCallList(aiMissedCall);
        ExcelUtil<AiMissedCall> util = new ExcelUtil<AiMissedCall>(AiMissedCall.class);
        util.exportExcel(response, list, "未接来电数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:query')")
    @GetMapping(value = "/{missedCallId}")
    public AjaxResult getInfo(@PathVariable("missedCallId") Long missedCallId)
    {
        return success(aiMissedCallService.selectAiMissedCallByMissedCallId(missedCallId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:add')")
    @Log(title = "未接来电", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiMissedCall aiMissedCall)
    {
        aiMissedCall.setCreateBy(getUsername());
        return toAjax(aiMissedCallService.insertAiMissedCall(aiMissedCall));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:edit')")
    @Log(title = "未接来电", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiMissedCall aiMissedCall)
    {
        aiMissedCall.setUpdateBy(getUsername());
        return toAjax(aiMissedCallService.updateAiMissedCall(aiMissedCall));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:remove')")
    @Log(title = "未接来电", businessType = BusinessType.DELETE)
    @DeleteMapping("/{missedCallIds}")
    public AjaxResult remove(@PathVariable Long[] missedCallIds)
    {
        return toAjax(aiMissedCallService.deleteAiMissedCallByMissedCallIds(missedCallIds));
    }

    /** 未接来电统计：今日未接、本周未接、已回拨、回拨率 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:list')")
    @GetMapping("/stats")
    public AjaxResult getStats()
    {
        return success(aiMissedCallService.selectMissedCallStats());
    }

    /** 回拨标记 */
    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:callback')")
    @Log(title = "未接来电回拨", businessType = BusinessType.UPDATE)
    @PutMapping("/callback/{missedCallId}")
    public AjaxResult callback(@PathVariable("missedCallId") Long missedCallId)
    {
        AiMissedCall update = new AiMissedCall();
        update.setMissedCallId(missedCallId);
        update.setStatus("1");
        update.setCallbackBy(getUsername());
        update.setCallbackTime(new java.util.Date());
        update.setUpdateBy(getUsername());
        return toAjax(aiMissedCallService.updateAiMissedCall(update));
    }

    // ---------------------------------------------------------------- 语音留言播放 / 转工单

    /**
     * 在线播放语音留言（支持 HTTP Range，支持音频拖动进度条）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:query')")
    @GetMapping("/{missedCallId}/voice/play")
    public ResponseEntity<Resource> playVoice(@PathVariable("missedCallId") Long missedCallId,
                                              HttpServletRequest request) throws IOException
    {
        File file = resolveVoiceFile(missedCallId);
        if (file == null || !file.exists() || !file.isFile())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        long fileLength = resource.contentLength();
        String contentType = resolveContentType(file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (StringUtils.isNotEmpty(rangeHeader))
        {
            try
            {
                long[] range = parseRange(rangeHeader, fileLength);
                if (range != null)
                {
                    long start = range[0];
                    long end = range[1];
                    long rangeLength = end - start + 1;

                    InputStream inputStream = resource.getInputStream();
                    long skipped = 0;
                    while (skipped < start)
                    {
                        long s = inputStream.skip(start - skipped);
                        if (s <= 0) break;
                        skipped += s;
                    }
                    org.springframework.core.io.InputStreamResource partialResource =
                            new org.springframework.core.io.InputStreamResource(inputStream);

                    headers.add("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                    headers.setContentLength(rangeLength);
                    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                            .headers(headers)
                            .body(partialResource);
                }
            }
            catch (IllegalArgumentException ex)
            {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                        .build();
            }
        }

        headers.setContentLength(fileLength);
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    /**
     * 语音留言一键转工单。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:missed:callback')")
    @Log(title = "语音留言转工单", businessType = BusinessType.INSERT)
    @PostMapping("/{missedCallId}/transfer")
    public AjaxResult transferVoiceToTicket(@PathVariable("missedCallId") Long missedCallId)
    {
        AiMissedCall missedCall = aiMissedCallService.selectAiMissedCallByMissedCallId(missedCallId);
        if (missedCall == null)
        {
            return error("未接来电记录不存在");
        }
        if (StringUtils.isEmpty(missedCall.getVoiceContent()) && StringUtils.isEmpty(missedCall.getVoiceFileUrl()))
        {
            return error("该记录无语音留言，无需转工单");
        }

        AiCallTicket ticket = new AiCallTicket();
        ticket.setTitle("语音留言转办-" + (StringUtils.isNotEmpty(missedCall.getCallerName())
                ? missedCall.getCallerName() : missedCall.getCallerNumber()));
        StringBuilder content = new StringBuilder();
        content.append("来电号码：").append(missedCall.getCallerNumber()).append("\n");
        if (StringUtils.isNotEmpty(missedCall.getCallerName()))
        {
            content.append("来电人：").append(missedCall.getCallerName()).append("\n");
        }
        if (missedCall.getCallTime() != null)
        {
            content.append("来电时间：").append(ai.lawyers.common.utils.DateUtils.parseDateToStr("yyyy-MM-dd HH:mm:ss", missedCall.getCallTime())).append("\n");
        }
        if (missedCall.getVoiceDuration() != null)
        {
            content.append("留言时长：").append(missedCall.getVoiceDuration()).append("秒\n");
        }
        if (StringUtils.isNotEmpty(missedCall.getVoiceContent()))
        {
            content.append("留言内容：").append(missedCall.getVoiceContent());
        }
        ticket.setContent(content.toString());
        ticket.setCallerNumber(missedCall.getCallerNumber());
        ticket.setCallerName(missedCall.getCallerName());
        ticket.setRecordId(missedCall.getRecordId());
        ticket.setPriority("2"); // 普通
        ticket.setCreateBy(getUsername());
        aiCallTicketService.insertAiCallTicket(ticket);

        // 回写工单号到未接来电备注，便于追溯
        AiMissedCall update = new AiMissedCall();
        update.setMissedCallId(missedCallId);
        update.setRemark("已转工单：" + ticket.getTicketNo());
        update.setUpdateBy(getUsername());
        aiMissedCallService.updateAiMissedCall(update);

        return success(ticket.getTicketNo());
    }

    /**
     * 解析语音留言文件：voice_file_url 可能是绝对路径，也可能是相对于
     * recordings_dir 的文件名，这里统一拼接成真实路径。
     */
    private File resolveVoiceFile(Long missedCallId)
    {
        AiMissedCall missedCall = aiMissedCallService.selectAiMissedCallByMissedCallId(missedCallId);
        if (missedCall == null || StringUtils.isEmpty(missedCall.getVoiceFileUrl()))
        {
            return null;
        }
        String path = missedCall.getVoiceFileUrl().trim();
        File file = new File(path);
        if (file.isAbsolute())
        {
            return file;
        }
        // FreeSWITCH 路径里 $${recordings_dir} 可能被透传，这里做一次替换
        if (path.startsWith("$${recordings_dir}") || path.startsWith("${recordings_dir}"))
        {
            path = path.substring(path.indexOf('}') + 1);
            if (path.startsWith("/") || path.startsWith("\\"))
            {
                path = path.substring(1);
            }
        }
        File base = new File(recordingBasePath);
        return new File(base, path);
    }

    private String resolveContentType(File file)
    {
        try
        {
            String probe = Files.probeContentType(file.toPath());
            if (StringUtils.isNotEmpty(probe))
            {
                return probe;
            }
        }
        catch (IOException ignored)
        {
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".webm")) return "audio/webm";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /**
     * 解析单个 HTTP Range 请求头（如 {@code bytes=0-1023}、{@code bytes=1024-}、
     * {@code bytes=-512}）。多段 Range（逗号分隔）只取第一段。
     *
     * @return long[]{start, end}；头为空或无法解析时返回 null
     * @throws IllegalArgumentException 范围越界
     */
    private long[] parseRange(String rangeHeader, long fileLength)
    {
        if (rangeHeader == null) return null;
        String value = rangeHeader.trim();
        if (!value.startsWith("bytes=")) return null;
        value = value.substring("bytes=".length()).trim();
        if (value.isEmpty()) return null;
        // 只支持单段范围，多段取第一段
        int comma = value.indexOf(',');
        if (comma >= 0) value = value.substring(0, comma).trim();
        int dash = value.indexOf('-');
        if (dash < 0) return null;

        String startStr = value.substring(0, dash).trim();
        String endStr = value.substring(dash + 1).trim();

        long start;
        long end;
        if (startStr.isEmpty())
        {
            // 后缀范围：bytes=-512 表示最后 512 字节
            if (endStr.isEmpty()) return null;
            long suffix = Long.parseLong(endStr);
            if (suffix <= 0) throw new IllegalArgumentException("Invalid suffix range: " + rangeHeader);
            start = Math.max(0, fileLength - suffix);
            end = fileLength - 1;
        }
        else
        {
            start = Long.parseLong(startStr);
            end = endStr.isEmpty() ? fileLength - 1 : Long.parseLong(endStr);
        }

        if (start < 0 || end < start || start >= fileLength)
        {
            throw new IllegalArgumentException("Range out of bounds: " + rangeHeader);
        }
        if (end >= fileLength) end = fileLength - 1;
        return new long[]{start, end};
    }
}
