package ai.lawyers.web.controller.lawyers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.poi.ExcelUtil;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.service.lawyers.IAiCallRecordService;

@RestController
@RequestMapping("/lawyers/call/record")
public class AiCallRecordController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(AiCallRecordController.class);

    @Autowired
    private IAiCallRecordService aiCallRecordService;

    /** 录音文件基础路径，对应 FreeSWITCH recordings_dir */
    @Value("${call.recording.base-path:C:/Program Files/FreeSWITCH/recordings}")
    private String recordingBasePath;

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallRecord aiCallRecord)
    {
        startPage();
        List<AiCallRecord> list = aiCallRecordService.selectAiCallRecordList(aiCallRecord);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:export')")
    @Log(title = "来电记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCallRecord aiCallRecord)
    {
        List<AiCallRecord> list = aiCallRecordService.selectAiCallRecordList(aiCallRecord);
        ExcelUtil<AiCallRecord> util = new ExcelUtil<AiCallRecord>(AiCallRecord.class);
        util.exportExcel(response, list, "来电记录数据");
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:query')")
    @GetMapping(value = "/{recordId}")
    public AjaxResult getInfo(@PathVariable("recordId") Long recordId)
    {
        return success(aiCallRecordService.selectAiCallRecordByRecordId(recordId));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:add')")
    @Log(title = "来电记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCallRecord aiCallRecord)
    {
        aiCallRecord.setCreateBy(getUsername());
        return toAjax(aiCallRecordService.insertAiCallRecord(aiCallRecord));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:edit')")
    @Log(title = "来电记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCallRecord aiCallRecord)
    {
        aiCallRecord.setUpdateBy(getUsername());
        return toAjax(aiCallRecordService.updateAiCallRecord(aiCallRecord));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:remove')")
    @Log(title = "来电记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        return toAjax(aiCallRecordService.deleteAiCallRecordByRecordIds(recordIds));
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:list')")
    @GetMapping("/agent/{agentId}")
    public AjaxResult getRecordsByAgentId(@PathVariable("agentId") Long agentId)
    {
        List<AiCallRecord> list = aiCallRecordService.selectAiCallRecordByAgentId(agentId);
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics")
    public AjaxResult getStatistics()
    {
        java.util.Map<String, Object> statistics = aiCallRecordService.getCallStatistics();
        return success(statistics);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics/agent")
    public AjaxResult getStatisticsByAgent()
    {
        List<java.util.Map<String, Object>> data = aiCallRecordService.getCallStatisticsByAgent();
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics/category")
    public AjaxResult getStatisticsByCategory()
    {
        List<java.util.Map<String, Object>> data = aiCallRecordService.getCallStatisticsByCategory();
        return success(data);
    }

    @PreAuthorize("@ss.hasPermi('lawyers:call:record:statistics')")
    @GetMapping("/statistics/date")
    public AjaxResult getStatisticsByDate(Integer days)
    {
        List<java.util.Map<String, Object>> data = aiCallRecordService.getCallStatisticsByDate(days);
        return success(data);
    }

    /** 工作台首页汇总 */
    @GetMapping("/workbench")
    public AjaxResult getWorkbenchSummary()
    {
        return success(aiCallRecordService.getWorkbenchSummary());
    }

    // ---------------------------------------------------------------- 录音播放 / 下载

    /**
     * 在线播放录音（支持 HTTP Range，支持音频拖动进度条）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:record:query')")
    @GetMapping("/{recordId}/play")
    public ResponseEntity<Resource> play(@PathVariable("recordId") Long recordId,
                                         HttpServletRequest request) throws IOException
    {
        File file = resolveRecordingFile(recordId);
        if (file == null || !file.exists() || !file.isFile())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        long fileLength = resource.contentLength();
        String contentType = resolveContentType(file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        // 允许浏览器内联播放；Accept-Ranges 声明支持字节范围
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
                log.warn("非法 Range 头: {} recordId={}", rangeHeader, recordId);
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                        .build();
            }
        }

        headers.setContentLength(fileLength);
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    /**
     * 下载录音文件。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:record:query')")
    @GetMapping("/{recordId}/download")
    public ResponseEntity<Resource> download(@PathVariable("recordId") Long recordId) throws IOException
    {
        File file = resolveRecordingFile(recordId);
        if (file == null || !file.exists() || !file.isFile())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        String fileName = file.getName();
        // 中文文件名需要 URL 编码，避免 Content-Disposition 乱码
        String encoded = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(resolveContentType(file)));
        headers.setContentLength(resource.contentLength());
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        return ResponseEntity.ok().headers(headers).body(resource);
    }

    /**
     * 解析录音文件：数据库 record_file 可能是绝对路径，也可能是相对于
     * recordings_dir 的文件名，这里统一拼接成真实路径。
     */
    private File resolveRecordingFile(Long recordId)
    {
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(recordId);
        if (record == null || StringUtils.isEmpty(record.getRecordFile()))
        {
            return null;
        }
        String recordPath = record.getRecordFile().trim();
        File file = new File(recordPath);
        if (file.isAbsolute())
        {
            return file;
        }
        // FreeSWITCH 路径里 $${recordings_dir} 可能被透传，这里做一次替换
        String path = recordPath;
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
