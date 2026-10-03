package ai.lawyers.web.controller.lawyers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;
import ai.lawyers.system.service.lawyers.summary.IAiCallSummaryService;

@RestController
@RequestMapping("/lawyers/call/record")
public class AiCallRecordController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(AiCallRecordController.class);

    @Autowired
    private IAiCallRecordService aiCallRecordService;

    @Autowired
    private IAiCallSummaryService aiCallSummaryService;

    /** 录音文件基础路径，对应 FreeSWITCH recordings_dir（默认相对工作目录，生产由 CALL_RECORDING_BASE_PATH 指定） */
    @Value("${call.recording.base-path:recordings}")
    private String recordingBasePath;

    /** F2：录音存储抽象（本地/对象存储） */
    @Autowired(required = false)
    private RecordingStorageService recordingStorageService;

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
     * P3-E3：生成（或重新生成）AI 通话小结。
     * 同步调用大模型（受模型舱壁与读超时保护，默认 60s）；失败不抛 500，
     * 返回业务错误并把失败原因落库（状态 3），前端可据此提示并允许重试。
     *
     * @param force true=已生成也重新生成
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:record:edit')")
    @Log(title = "AI通话小结", businessType = BusinessType.UPDATE)
    @PostMapping("/{recordId}/ai-summary")
    public AjaxResult generateAiSummary(@PathVariable("recordId") Long recordId,
                                        @org.springframework.web.bind.annotation.RequestParam(
                                                defaultValue = "false") boolean force)
    {
        AiCallRecord rec = aiCallSummaryService.generateSummary(recordId, force);
        if (rec == null)
        {
            return AjaxResult.error("话单不存在：" + recordId);
        }
        if ("3".equals(rec.getAiSummaryStatus()))
        {
            return AjaxResult.error(StringUtils.isNotEmpty(rec.getAiSummaryFailReason())
                    ? rec.getAiSummaryFailReason() : "AI 小结生成失败，请稍后重试");
        }
        if ("1".equals(rec.getAiSummaryStatus()))
        {
            return AjaxResult.error("小结正在生成中，请稍后刷新查看");
        }
        return AjaxResult.success(rec);
    }

    /**
     * 在线播放录音（支持 HTTP Range，支持音频拖动进度条）。
     * 对象存储模式且未加密：302 重定向到预签名 URL；
     * 加密模式 / 本地模式：后端流式解密返回。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:record:query')")
    @GetMapping("/{recordId}/play")
    public ResponseEntity<Resource> play(@PathVariable("recordId") Long recordId,
                                         HttpServletRequest request) throws IOException
    {
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(recordId);
        if (record == null || StringUtils.isEmpty(record.getRecordFile()))
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // F2：对象存储模式且未加密 → 302 重定向到预签名 URL
        if (recordingStorageService != null && recordingStorageService.isObjectStorage())
        {
            String playUrl = recordingStorageService.getPlayUrl(record.getRecordFile(), 3600);
            if (StringUtils.isNotEmpty(playUrl))
            {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, playUrl)
                        .build();
            }
        }

        // G1-b3：加密模式（getPlayUrl 返回 null）或本地模式 → 后端流式解密
        if (recordingStorageService != null)
        {
            byte[] audioBytes = loadRecordingBytes(record.getRecordFile());
            if (audioBytes != null)
            {
                return streamAudio(audioBytes, record.getRecordFile(), request, false);
            }
        }

        // 降级：本地文件直接流式（兼容存量明文）
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
     * 对象存储模式且未加密：302 重定向到预签名 URL；
     * 加密模式 / 本地模式：后端流式解密下载。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:call:record:query')")
    @GetMapping("/{recordId}/download")
    public ResponseEntity<Resource> download(@PathVariable("recordId") Long recordId) throws IOException
    {
        AiCallRecord record = aiCallRecordService.selectAiCallRecordByRecordId(recordId);
        if (record == null || StringUtils.isEmpty(record.getRecordFile()))
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // F2：对象存储模式且未加密 → 302 重定向到预签名 URL
        if (recordingStorageService != null && recordingStorageService.isObjectStorage())
        {
            String playUrl = recordingStorageService.getPlayUrl(record.getRecordFile(), 3600);
            if (StringUtils.isNotEmpty(playUrl))
            {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, playUrl)
                        .build();
            }
        }

        // G1-b3：加密模式或本地模式 → 后端流式解密下载
        if (recordingStorageService != null)
        {
            byte[] audioBytes = loadRecordingBytes(record.getRecordFile());
            if (audioBytes != null)
            {
                return streamAudio(audioBytes, record.getRecordFile(), null, true);
            }
        }

        // 降级：本地文件直接下载
        File file = resolveRecordingFile(recordId);
        if (file == null || !file.exists() || !file.isFile())
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        String fileName = file.getName();
        String encoded = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(resolveContentType(file)));
        headers.setContentLength(resource.contentLength());
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);

        return ResponseEntity.ok().headers(headers).body(resource);
    }

    /**
     * 通过 storageService 读取录音（自动解密）。返回明文字节；读取失败返回 null。
     */
    private byte[] loadRecordingBytes(String recordFile)
    {
        try (InputStream in = recordingStorageService.load(recordFile))
        {
            if (in == null)
            {
                return null;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
            {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
        catch (Exception e)
        {
            log.warn("录音读取/解密失败 file={}: {}", recordFile, e.getMessage());
            return null;
        }
    }

    /**
     * 将明文字节流化为 HTTP 响应（支持 Range / 内联播放 / 下载）。
     *
     * @param audioBytes  明文字节
     * @param recordFile  原始文件名（用于推断 MIME 与下载文件名）
     * @param request     HTTP 请求（解析 Range；下载时传 null）
     * @param asDownload  true=下载（Content-Disposition: attachment），false=内联播放
     */
    private ResponseEntity<Resource> streamAudio(byte[] audioBytes, String recordFile,
                                                 HttpServletRequest request, boolean asDownload)
    {
        String fileName = new File(recordFile).getName();
        String contentType = guessContentType(fileName);
        long fileLength = audioBytes.length;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (asDownload)
        {
            try
            {
                String encoded = java.net.URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
                headers.set(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
            }
            catch (java.io.UnsupportedEncodingException ignored)
            {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            }
        }

        // Range 请求（仅播放场景）
        if (request != null)
        {
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
                        byte[] partial = new byte[(int) rangeLength];
                        System.arraycopy(audioBytes, (int) start, partial, 0, (int) rangeLength);

                        headers.add("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                        headers.setContentLength(rangeLength);
                        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                .headers(headers)
                                .body(new org.springframework.core.io.ByteArrayResource(partial));
                    }
                }
                catch (IllegalArgumentException ex)
                {
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                            .build();
                }
            }
        }

        headers.setContentLength(fileLength);
        return ResponseEntity.ok().headers(headers)
                .body(new org.springframework.core.io.ByteArrayResource(audioBytes));
    }

    private String guessContentType(String fileName)
    {
        String name = fileName == null ? "" : fileName.toLowerCase();
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".webm")) return "audio/webm";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
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
