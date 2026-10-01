package ai.lawyers.system.service.impl.lawyers;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiPiiSearchToken;
import ai.lawyers.system.mapper.lawyers.AiPiiSearchTokenMapper;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;

/**
 * PII 模糊检索 token 服务实现（P3-G1-b2 V2.56）。
 *
 * @author ai-lawyers
 */
@Service
public class PiiSearchTokenServiceImpl implements IPiiSearchTokenService
{
    @Autowired
    private AiPiiSearchTokenMapper tokenMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuild(String ownerType, Long ownerId, String plain)
    {
        // 先删后写，保证号码变更/重复执行后 token 与当前号码严格一致
        tokenMapper.deleteByOwner(ownerType, ownerId);
        List<String> values = PiiCryptoUtils.phoneTokens(plain);
        if (StringUtils.isNotEmpty(plain) && !values.isEmpty())
        {
            List<AiPiiSearchToken> list = new ArrayList<>(values.size());
            for (String value : values)
            {
                AiPiiSearchToken token = new AiPiiSearchToken();
                token.setOwnerType(ownerType);
                token.setOwnerId(ownerId);
                token.setTokenValue(value);
                list.add(token);
            }
            tokenMapper.batchInsertToken(list);
        }
    }
}
