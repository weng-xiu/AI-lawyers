package ai.lawyers.system.service.lawyers.impl;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.ArrayList;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.core.text.Convert;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.exception.ServiceException;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.system.domain.lawyers.AiUnifiedSession;
import ai.lawyers.system.domain.lawyers.AiUserConsultation;
import ai.lawyers.system.domain.lawyers.AiUserEvaluation;
import ai.lawyers.system.mapper.lawyers.AiUserConsultationMapper;
import ai.lawyers.system.mapper.lawyers.AiUserEvaluationMapper;
import ai.lawyers.system.service.lawyers.IAiUserConsultationService;
import ai.lawyers.system.service.lawyers.IAiUnifiedSessionService;
import ai.lawyers.system.service.lawyers.session.OccupyResult;

/**
 * 用户咨询Service业务层处理
 * 
 * @author AI律师
 * @date 2023-11-19
 */
@Service
public class AiUserConsultationServiceImpl implements IAiUserConsultationService 
{
    /** F10：补充材料单次最多 3 个文件，单个不超过 10MB */
    private static final int MAX_SUPPLEMENT_FILES = 3;

    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    /** 附件路径列长度上限（ai_user_consultation.attachments varchar(1000)） */
    private static final int MAX_ATTACHMENTS_LENGTH = 1000;

    /** 备注列长度上限（ai_user_consultation.remark varchar(500)） */
    private static final int MAX_REMARK_LENGTH = 500;

    /** 材料扩展名白名单（与前端上传限制一致） */
    private static final List<String> ALLOWED_EXTENSIONS =
            java.util.Arrays.asList(".jpg", ".jpeg", ".png", ".pdf", ".doc", ".docx");

    @Autowired
    private AiUserConsultationMapper aiUserConsultationMapper;
    
    @Autowired
    private AiUserEvaluationMapper aiUserEvaluationMapper;

    /** F8 公众端文本内容安全过滤 */
    @Autowired
    private ContentSafetyService contentSafetyService;

    /** 方案B：跨渠道统一会话占用 */
    @Autowired
    private IAiUnifiedSessionService unifiedSessionService;

    @Value("${file.path}")
    private String filePath;

    /** 方案B：图文咨询同渠道互斥总开关（默认开启；关闭后仅写时间线不拦截） */
    @Value("${session.occupy.enabled:true}")
    private boolean occupyEnabled;

    /** 方案B：MESSAGE 重复发起策略 REENTER 幂等恢复（默认）/ REJECT 直接拒绝 */
    @Value("${session.occupy.message-mode:REENTER}")
    private String messageOccupyMode;

    /** 方案B：MESSAGE 占用兜底 TTL（秒），默认 24h（业务状态完成即提前释放） */
    @Value("${session.occupy.ttl-message:86400}")
    private int messageOccupyTtlSeconds;

    /** 方案B：图文咨询统一会话渠道标识 */
    private static final String CHANNEL_H5 = "H5";
    private static final String BIZ_MESSAGE = "MESSAGE";

    /**
     * 查询用户咨询
     * 
     * @param id 用户咨询主键
     * @return 用户咨询
     */
    @Override
    public AiUserConsultation selectAiUserConsultationById(Long id)
    {
        return aiUserConsultationMapper.selectAiUserConsultationById(id);
    }

    /**
     * 查询用户咨询列表
     * 
     * @param aiUserConsultation 用户咨询
     * @return 用户咨询
     */
    @Override
    public List<AiUserConsultation> selectAiUserConsultationList(AiUserConsultation aiUserConsultation)
    {
        return aiUserConsultationMapper.selectAiUserConsultationList(aiUserConsultation);
    }

    /**
     * 新增用户咨询
     * 
     * @param aiUserConsultation 用户咨询
     * @return 结果
     */
    @Override
    public int insertAiUserConsultation(AiUserConsultation aiUserConsultation)
    {
        aiUserConsultation.setCreateTime(DateUtils.getNowDate());
        return aiUserConsultationMapper.insertAiUserConsultation(aiUserConsultation);
    }

    /**
     * 修改用户咨询
     * 
     * @param aiUserConsultation 用户咨询
     * @return 结果
     */
    @Override
    public int updateAiUserConsultation(AiUserConsultation aiUserConsultation)
    {
        aiUserConsultation.setUpdateTime(DateUtils.getNowDate());
        return aiUserConsultationMapper.updateAiUserConsultation(aiUserConsultation);
    }

    /**
     * 批量删除用户咨询
     * 
     * @param ids 需要删除的用户咨询主键
     * @return 结果
     */
    @Override
    public int deleteAiUserConsultationByIds(Long[] ids)
    {
        return aiUserConsultationMapper.deleteAiUserConsultationByIds(ids);
    }

    /**
     * 删除用户咨询信息
     * 
     * @param id 用户咨询主键
     * @return 结果
     */
    @Override
    public int deleteAiUserConsultationById(Long id)
    {
        return aiUserConsultationMapper.deleteAiUserConsultationById(id);
    }

    /**
     * 提交法律咨询
     * 
     * @param category 问题分类
     * @param content 问题内容
     * @param files 附件文件列表
     * @return 咨询记录
     */
    @Override
    public AiUserConsultation submitConsultation(String category, String content, List<MultipartFile> files)
    {
        return submitConsultation(category, content, files, null);
    }

    /**
     * 提交法律咨询（带渠道透传，#16）。
     *
     * @param channelType 提交渠道（PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB；空按 H5）
     */
    @Override
    public AiUserConsultation submitConsultation(String category, String content,
                                                 List<MultipartFile> files, String channelType)
    {
        // F8 安全基线：内容长度 + 本地敏感词校验（坐席端/公众端共用，命中即拒绝入库）
        contentSafetyService.validateConsultation(category, content);

        // 方案B（同渠道互斥）：该公众在 H5 图文渠道已有 PROCESSING 咨询时，按策略幂等恢复或拒绝
        Long currentUserId = SecurityUtils.getUserId();
        AiUnifiedSession activeSession = null;
        if (occupyEnabled)
        {
            List<AiUnifiedSession> actives = unifiedSessionService.listActive(null, currentPhone());
            AiUnifiedSession blocked = actives.stream()
                    .filter(s -> CHANNEL_H5.equals(s.getChannelType()) && BIZ_MESSAGE.equals(s.getBizType()))
                    .findFirst().orElse(null);
            if (blocked != null)
            {
                AiUserConsultation existing = findProcessingConsultation(currentUserId, blocked.getBizId());
                if (existing != null)
                {
                    // REENTER：返回既有处理中咨询（不新增、不重复触发 AI）；REJECT：直接拒绝
                    if ("REJECT".equalsIgnoreCase(messageOccupyMode))
                    {
                        throw new ServiceException("您有正在处理的咨询，请稍后查看结果后再提交");
                    }
                    return existing;
                }
                // DB 业务单已不存在（残留索引）：交由下方抢占，看门狗语义下不阻塞新提交
            }
        }

        // 创建咨询记录
        AiUserConsultation consultation = new AiUserConsultation();
        consultation.setUserId(currentUserId);
        consultation.setCategory(category);
        consultation.setContent(content);
        consultation.setStatus("PROCESSING"); // 处理中
        // #16 渠道透传：白名单校验，空/非法一律回退 H5（图文渠道）
        consultation.setChannelType(normalizeChannelType(channelType));
        consultation.setCreateTime(new Date());
        
        // 处理附件
        String attachmentPaths = saveAttachments(files);
        if (StringUtils.isNotEmpty(attachmentPaths)) {
            consultation.setAttachments(attachmentPaths);
        }
        
        // 保存咨询记录
        aiUserConsultationMapper.insertAiUserConsultation(consultation);

        // 方案B：登记 H5 图文渠道活跃占用（并发双击/多标签下抢占失败者回滚本次插入并恢复既有单）
        if (occupyEnabled)
        {
            AiUnifiedSession session = new AiUnifiedSession();
            session.setCallerNumber(currentPhone());
            session.setChannelType(CHANNEL_H5);
            session.setBizType(BIZ_MESSAGE);
            session.setBizId(String.valueOf(consultation.getConsultationId()));
            session.setBizTitle(categoryName(getCategoryLabel(category)));
            session.setStartTime(consultation.getCreateTime());
            OccupyResult result = unifiedSessionService.startActive(session, messageOccupyTtlSeconds);
            if (!result.isAcquired())
            {
                aiUserConsultationMapper.deleteAiUserConsultationById(consultation.getConsultationId());
                if ("REJECT".equalsIgnoreCase(messageOccupyMode))
                {
                    throw new ServiceException("您有正在处理的咨询，请稍后查看结果后再提交");
                }
                AiUserConsultation existing = findProcessingConsultation(currentUserId, result.getExistingBizId());
                if (existing != null)
                {
                    return existing;
                }
                // 极端情况：抢占失败但既有单查不到（对方刚结束），本次已回滚，提示重试一次
                throw new ServiceException("提交冲突，请重新发起咨询");
            }
            activeSession = result.getSession();
        }

        // AI 处理（当前为同步模拟）；无论完成/失败都释放本渠道占用
        try
        {
            processAIConsultation(consultation);
        }
        finally
        {
            if (activeSession != null)
            {
                unifiedSessionService.finishSession(BIZ_MESSAGE,
                        String.valueOf(consultation.getConsultationId()), new Date());
            }
        }
        
        return consultation;
    }

    /** #16 渠道白名单校验：PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB，空/非法一律回退 H5（图文渠道） */
    static String normalizeChannelType(String channelType)
    {
        if (StringUtils.isEmpty(channelType))
        {
            return CHANNEL_H5;
        }
        switch (channelType.trim().toUpperCase())
        {
            case "PHONE":
            case "WECHAT_MP":
            case "WECHAT_MINI":
            case "H5":
            case "WEB":
                return channelType.trim().toUpperCase();
            default:
                return CHANNEL_H5;
        }
    }

    /** 取当前登录公众用户手机号（公众账号必须绑定手机号） */
    private String currentPhone()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (user == null || StringUtils.isEmpty(user.getPhonenumber()))
        {
            throw new ServiceException("当前账号未绑定手机号，无法关联服务记录");
        }
        return user.getPhonenumber();
    }

    /** 按占用索引 bizId（咨询ID）取本人处理中咨询；非本人或非处理中返回 null */
    private AiUserConsultation findProcessingConsultation(Long userId, String bizId)
    {
        if (StringUtils.isEmpty(bizId) || !StringUtils.isNumeric(bizId))
        {
            return null;
        }
        AiUserConsultation c = aiUserConsultationMapper.selectAiUserConsultationById(Long.valueOf(bizId));
        if (c != null && userId.equals(c.getUserId()) && "PROCESSING".equals(c.getStatus()))
        {
            return c;
        }
        return null;
    }

    /** 入参 category 可能是 value 或中文名，统一转中文用于时间线摘要 */
    private String getCategoryLabel(String category)
    {
        if (StringUtils.isEmpty(category))
        {
            return "法律咨询";
        }
        return getCategoryName(category);
    }

    /** 兼容旧调用：分类名取值（与 getQuestionCategories 的 value 对齐） */
    private String categoryName(String name)
    {
        return StringUtils.isEmpty(name) ? "法律咨询" : name;
    }

    /**
     * 提交咨询评价
     * 
     * @param evaluation 评价信息
     * @return 结果
     */
    @Override
    public int submitEvaluation(AiUserEvaluation evaluation)
    {
        contentSafetyService.validateFeedback(evaluation.getFeedback());
        evaluation.setCreateTime(new Date());
        evaluation.setUserId(SecurityUtils.getUserId());
        return aiUserEvaluationMapper.insertAiUserEvaluation(evaluation);
    }

    /**
     * 公众端：对象级归属校验——仅允许访问本人咨询，防 IDOR 遍历
     */
    @Override
    public AiUserConsultation selectOwnConsultationById(Long consultationId)
    {
        AiUserConsultation consultation = aiUserConsultationMapper.selectAiUserConsultationById(consultationId);
        if (consultation == null)
        {
            throw new ServiceException("咨询记录不存在");
        }
        Long currentUserId = SecurityUtils.getUserId();
        if (!currentUserId.equals(consultation.getUserId()))
        {
            // 不暴露记录是否存在，统一按"不存在"返回
            throw new ServiceException("咨询记录不存在");
        }
        return consultation;
    }

    /**
     * 公众端：评价提交（咨询归属校验 + 一咨询一评价 + 四维评分兜底 + 文本安全）
     */
    @Override
    public int submitOwnEvaluation(AiUserEvaluation evaluation)
    {
        if (evaluation.getConsultationId() == null)
        {
            throw new ServiceException("缺少咨询ID");
        }
        if (evaluation.getOverallRating() == null
                || evaluation.getOverallRating() < 1 || evaluation.getOverallRating() > 5)
        {
            throw new ServiceException("请先选择总体评分（1-5分）");
        }
        contentSafetyService.validateFeedback(evaluation.getFeedback());

        AiUserConsultation consultation = selectOwnConsultationById(evaluation.getConsultationId());
        if (!"COMPLETED".equals(consultation.getStatus()))
        {
            throw new ServiceException("咨询尚未完成，暂不能评价");
        }

        AiUserEvaluation query = new AiUserEvaluation();
        query.setConsultationId(evaluation.getConsultationId());
        query.setUserId(SecurityUtils.getUserId());
        List<AiUserEvaluation> existed = aiUserEvaluationMapper.selectAiUserEvaluationList(query);
        if (existed != null && !existed.isEmpty())
        {
            throw new ServiceException("该咨询已评价，请勿重复提交");
        }

        // 四维评分缺省时以总体评分兜底（公众端仅采集总体评分 + 文字反馈）
        Integer overall = evaluation.getOverallRating();
        if (evaluation.getProfessionalismRating() == null)
        {
            evaluation.setProfessionalismRating(overall);
        }
        if (evaluation.getResponsivenessRating() == null)
        {
            evaluation.setResponsivenessRating(overall);
        }
        if (evaluation.getQualityRating() == null)
        {
            evaluation.setQualityRating(overall);
        }

        evaluation.setEvaluationId(null);
        evaluation.setUserId(SecurityUtils.getUserId());
        evaluation.setCreateTime(new Date());
        return aiUserEvaluationMapper.insertAiUserEvaluation(evaluation);
    }

    /**
     * F10 公众端：补充材料/说明（仅本人、仅已完成咨询；附件追加不覆盖原附件）
     */
    @Override
    public AiUserConsultation supplementOwnConsultation(Long consultationId, List<MultipartFile> files,
                                                        String note)
    {
        AiUserConsultation consultation = selectOwnConsultationById(consultationId);
        if ("FAILED".equals(consultation.getStatus()))
        {
            throw new ServiceException("咨询处理失败，暂不支持补充材料");
        }
        if (!"COMPLETED".equals(consultation.getStatus()))
        {
            throw new ServiceException("咨询正在处理中，请稍后再补充材料");
        }

        String safeNote = StringUtils.isEmpty(note) ? null : note.trim();
        // 复用评价文本校验：≤500 字 + 本地敏感词
        contentSafetyService.validateFeedback(safeNote);

        List<MultipartFile> uploads = new ArrayList<>();
        if (files != null)
        {
            for (MultipartFile file : files)
            {
                if (file != null && !file.isEmpty())
                {
                    uploads.add(file);
                }
            }
        }
        if (uploads.isEmpty() && StringUtils.isEmpty(safeNote))
        {
            throw new ServiceException("请上传材料或填写补充说明");
        }
        if (uploads.size() > MAX_SUPPLEMENT_FILES)
        {
            throw new ServiceException("单次最多补充" + MAX_SUPPLEMENT_FILES + "个材料");
        }

        String uploaded = saveAttachments(uploads);
        String mergedAttachments = mergeAttachments(consultation.getAttachments(), uploaded);
        String mergedRemark = mergeRemark(consultation.getRemark(), safeNote);

        AiUserConsultation update = new AiUserConsultation();
        update.setConsultationId(consultationId);
        if (StringUtils.isNotEmpty(uploaded))
        {
            update.setAttachments(mergedAttachments);
        }
        if (StringUtils.isNotEmpty(safeNote))
        {
            update.setRemark(mergedRemark);
        }
        update.setUpdateBy(SecurityUtils.getUsername());
        update.setUpdateTime(new Date());
        aiUserConsultationMapper.updateAiUserConsultation(update);
        return aiUserConsultationMapper.selectAiUserConsultationById(consultationId);
    }

    /**
     * F10 公众端：本人咨询是否已评价（归属校验后按 consultationId + userId 查询）
     */
    @Override
    public boolean isOwnConsultationEvaluated(Long consultationId)
    {
        selectOwnConsultationById(consultationId);
        AiUserEvaluation query = new AiUserEvaluation();
        query.setConsultationId(consultationId);
        query.setUserId(SecurityUtils.getUserId());
        List<AiUserEvaluation> existed = aiUserEvaluationMapper.selectAiUserEvaluationList(query);
        return existed != null && !existed.isEmpty();
    }

    /** 附件落盘：仅白名单扩展名、单个 ≤10MB；返回逗号分隔的相对路径 */
    private String saveAttachments(List<MultipartFile> files)
    {
        if (files == null || files.isEmpty())
        {
            return "";
        }
        StringBuilder paths = new StringBuilder();
        for (MultipartFile file : files)
        {
            if (file == null || file.isEmpty())
            {
                continue;
            }
            String originalFilename = file.getOriginalFilename();
            int dot = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
            if (dot < 0)
            {
                throw new ServiceException("文件名缺少扩展名，仅支持 jpg/jpeg/png/pdf/doc/docx");
            }
            String extension = originalFilename.substring(dot).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension))
            {
                throw new ServiceException("仅支持 jpg/jpeg/png/pdf/doc/docx 格式材料");
            }
            if (file.getSize() > MAX_ATTACHMENT_BYTES)
            {
                throw new ServiceException("单个附件不能超过10MB");
            }
            try
            {
                String newFilename = UUID.randomUUID().toString() + extension;
                // 创建文件目录（取绝对路径：MultipartFile.transferTo 对相对路径会按
                // Tomcat 临时目录解析，导致 FileNotFoundException，F8 联调修复）
                File dir = new File(filePath + "/consultation/").getAbsoluteFile();
                if (!dir.exists() && !dir.mkdirs())
                {
                    throw new ServiceException("上传目录创建失败: " + dir.getAbsolutePath());
                }
                File destFile = new File(dir, newFilename);
                file.transferTo(destFile.getAbsoluteFile());
                if (paths.length() > 0)
                {
                    paths.append(",");
                }
                paths.append("/consultation/").append(newFilename);
            }
            catch (IOException e)
            {
                throw new ServiceException("文件上传失败: " + e.getMessage());
            }
        }
        return paths.toString();
    }

    /** 追加附件：保留原附件，合并后受列长度约束 */
    private String mergeAttachments(String existed, String added)
    {
        if (StringUtils.isEmpty(added))
        {
            return existed;
        }
        String merged = StringUtils.isEmpty(existed) ? added : existed + "," + added;
        if (merged.length() > MAX_ATTACHMENTS_LENGTH)
        {
            throw new ServiceException("附件数量已达上限，请通过人工客服补充材料");
        }
        return merged;
    }

    /** 追加补充说明：保留原说明，合并后受列长度约束 */
    private String mergeRemark(String existed, String note)
    {
        if (StringUtils.isEmpty(note))
        {
            return existed;
        }
        String merged = StringUtils.isEmpty(existed) ? note : existed + "；" + note;
        if (merged.length() > MAX_REMARK_LENGTH)
        {
            throw new ServiceException("补充说明累计不能超过" + MAX_REMARK_LENGTH + "个字，请精简后重试");
        }
        return merged;
    }

    /**
     * 获取问题分类列表
     * 
     * @return 分类列表
     */
    @Override
    public List<Map<String, Object>> getQuestionCategories()
    {
        List<Map<String, Object>> categories = new ArrayList<>();
        
        Map<String, Object> category1 = new HashMap<>();
        category1.put("value", "marriage_family");
        category1.put("label", "婚姻家庭");
        categories.add(category1);
        
        Map<String, Object> category2 = new HashMap<>();
        category2.put("value", "labor_dispute");
        category2.put("label", "劳动纠纷");
        categories.add(category2);
        
        Map<String, Object> category3 = new HashMap<>();
        category3.put("value", "contract_dispute");
        category3.put("label", "合同纠纷");
        categories.add(category3);
        
        Map<String, Object> category4 = new HashMap<>();
        category4.put("value", "property_dispute");
        category4.put("label", "财产纠纷");
        categories.add(category4);
        
        Map<String, Object> category5 = new HashMap<>();
        category5.put("value", "criminal_case");
        category5.put("label", "刑事案件");
        categories.add(category5);
        
        Map<String, Object> category6 = new HashMap<>();
        category6.put("value", "intellectual_property");
        category6.put("label", "知识产权");
        categories.add(category6);
        
        Map<String, Object> category7 = new HashMap<>();
        category7.put("value", "consumer_rights");
        category7.put("label", "消费者权益");
        categories.add(category7);
        
        Map<String, Object> category8 = new HashMap<>();
        category8.put("value", "other");
        category8.put("label", "其他");
        categories.add(category8);
        
        return categories;
    }

    /**
     * 处理AI咨询请求
     * 
     * @param consultation 咨询信息
     * @return 处理后的咨询信息
     */
    @Override
    public AiUserConsultation processAIConsultation(AiUserConsultation consultation)
    {
        // 更新状态为处理中
        consultation.setStatus("PROCESSING");
        aiUserConsultationMapper.updateAiUserConsultation(consultation);

        // P3-⑨：当前未接入法规库/案例库与智能问答模型，禁止编造法条、案号与置信度。
        // 相关法条/案例留空、置信度置 null（前端已按「暂无/未知」渲染），回答明确告知未接入。
        consultation.setAiAnswer(buildFallbackAnswer(consultation.getCategory()));
        consultation.setRelatedLaws(null);
        consultation.setRelatedCases(null);
        consultation.setConfidence(null);
        consultation.setStatus("COMPLETED");
        consultation.setUpdateTime(new Date());

        aiUserConsultationMapper.updateAiUserConsultation(consultation);

        return consultation;
    }
    
    /**
     * 未接入智能问答时的兜底回答：明确告知能力边界，不输出任何“建议”或法条引用。
     */
    private String buildFallbackAnswer(String category)
    {
        return "当前系统尚未接入法规库与智能问答服务，无法针对您的" + getCategoryName(category) + "问题生成具体法律意见。\n\n"
                + "您可补充材料后转人工律师咨询，或拨打法律援助热线 12348。";
    }
    
    /**
     * 获取分类名称
     */
    private String getCategoryName(String category)
    {
        switch (category) {
            case "marriage_family": return "婚姻家庭";
            case "labor_dispute": return "劳动纠纷";
            case "contract_dispute": return "合同纠纷";
            case "property_dispute": return "财产纠纷";
            case "criminal_case": return "刑事案件";
            case "intellectual_property": return "知识产权";
            case "consumer_rights": return "消费者权益";
            default: return "其他";
        }
    }
}