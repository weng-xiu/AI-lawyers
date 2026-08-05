package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import ai.lawyers.system.domain.lawyers.AiNotice;

public interface AiNoticeMapper
{
    public AiNotice selectAiNoticeByNoticeId(Long noticeId);

    public List<AiNotice> selectAiNoticeList(AiNotice aiNotice);

    public List<AiNotice> selectPublishedNotices(Integer limit);

    public int insertAiNotice(AiNotice aiNotice);

    public int updateAiNotice(AiNotice aiNotice);

    public int deleteAiNoticeByNoticeId(Long noticeId);

    public int deleteAiNoticeByNoticeIds(Long[] noticeIds);
}
