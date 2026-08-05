package ai.lawyers.system.service.impl.lawyers;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ai.lawyers.system.domain.lawyers.AiNotice;
import ai.lawyers.system.mapper.lawyers.AiNoticeMapper;
import ai.lawyers.system.service.lawyers.IAiNoticeService;

@Service
public class AiNoticeServiceImpl implements IAiNoticeService
{
    @Autowired
    private AiNoticeMapper aiNoticeMapper;

    @Override
    public AiNotice selectAiNoticeByNoticeId(Long noticeId)
    {
        return aiNoticeMapper.selectAiNoticeByNoticeId(noticeId);
    }

    @Override
    public List<AiNotice> selectAiNoticeList(AiNotice aiNotice)
    {
        return aiNoticeMapper.selectAiNoticeList(aiNotice);
    }

    @Override
    public List<AiNotice> selectPublishedNotices(Integer limit)
    {
        return aiNoticeMapper.selectPublishedNotices(limit);
    }

    @Override
    public int insertAiNotice(AiNotice aiNotice)
    {
        if (aiNotice.getStatus() == null || aiNotice.getStatus().isEmpty()) {
            aiNotice.setStatus("0");
        }
        if (aiNotice.getIsTop() == null || aiNotice.getIsTop().isEmpty()) {
            aiNotice.setIsTop("0");
        }
        if ("1".equals(aiNotice.getStatus()) && aiNotice.getPublishTime() == null) {
            aiNotice.setPublishTime(new Date());
        }
        return aiNoticeMapper.insertAiNotice(aiNotice);
    }

    @Override
    public int updateAiNotice(AiNotice aiNotice)
    {
        if ("1".equals(aiNotice.getStatus()) && aiNotice.getPublishTime() == null) {
            AiNotice exist = aiNoticeMapper.selectAiNoticeByNoticeId(aiNotice.getNoticeId());
            if (exist == null || exist.getPublishTime() == null) {
                aiNotice.setPublishTime(new Date());
            }
        }
        return aiNoticeMapper.updateAiNotice(aiNotice);
    }

    @Override
    public int deleteAiNoticeByNoticeId(Long noticeId)
    {
        return aiNoticeMapper.deleteAiNoticeByNoticeId(noticeId);
    }

    @Override
    public int deleteAiNoticeByNoticeIds(Long[] noticeIds)
    {
        return aiNoticeMapper.deleteAiNoticeByNoticeIds(noticeIds);
    }
}
