package ai.lawyers.system.domain.lawyers;

/**
 * PII 模糊检索位置分片盲 token 实体（P3-G1-b2 V2.56，对应表 ai_pii_search_token）。
 *
 * <p>每个号码按字符位置逐位生成一个 token（{@code PiiCryptoUtils.searchToken}），
 * 查询侧 token 分组组内 AND 即保证位置连续，保持 {@code LIKE '%keyword%'} 等价语义。</p>
 *
 * @author ai-lawyers
 */
public class AiPiiSearchToken
{
    /** token ID */
    private Long tokenId;

    /** 属主类型（CALL_RECORD 话单 / CALL_LEDGER 台账 / CALLER_PROFILE 来电档案） */
    private String ownerType;

    /** 属主 ID（record_id / ledger_id / profile_id） */
    private Long ownerId;

    /** 位置分片盲 token（HMAC-SM3 截断 128 位 hex 32 字符） */
    private String tokenValue;

    public Long getTokenId()
    {
        return tokenId;
    }

    public void setTokenId(Long tokenId)
    {
        this.tokenId = tokenId;
    }

    public String getOwnerType()
    {
        return ownerType;
    }

    public void setOwnerType(String ownerType)
    {
        this.ownerType = ownerType;
    }

    public Long getOwnerId()
    {
        return ownerId;
    }

    public void setOwnerId(Long ownerId)
    {
        this.ownerId = ownerId;
    }

    public String getTokenValue()
    {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue)
    {
        this.tokenValue = tokenValue;
    }
}
