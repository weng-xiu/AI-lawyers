package ai.lawyers.system.mapper.lawyers;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import ai.lawyers.system.domain.lawyers.AiPiiSearchToken;

/**
 * PII 模糊检索 token Mapper（P3-G1-b2 V2.56）。
 *
 * @author ai-lawyers
 */
public interface AiPiiSearchTokenMapper
{
    /**
     * 批量写入 token（多值 insert）。
     */
    public int batchInsertToken(@Param("list") List<AiPiiSearchToken> list);

    /**
     * 删除某属主全部 token（号码变更重建/迁移回填前清理）。
     */
    public int deleteByOwner(@Param("ownerType") String ownerType, @Param("ownerId") Long ownerId);

    /**
     * 统计某属主已有 token 数（迁移幂等：判定 token 是否已回填）。
     */
    public int countByOwner(@Param("ownerType") String ownerType, @Param("ownerId") Long ownerId);
}
