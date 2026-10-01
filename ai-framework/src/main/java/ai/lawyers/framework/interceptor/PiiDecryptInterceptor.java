package ai.lawyers.framework.interceptor;

import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;

/**
 * PII 读取解密拦截器（P3-G1-b2 V2.56）：MyBatis 结果集返回后自动解密 PII 字段，
 * 域对象在应用层始终保持明文语义，业务代码（列表/详情/导出/内部服务）无需逐点处理，
 * 避免新增读取路径漏解密。
 *
 * <p>仅处理已知 PII 域对象；{@code PiiCryptoUtils.decrypt} 三态兼容
 * （encp: 解密 / 存量明文原样），解密失败（密文损坏/密钥不匹配）记录告警并保留原值，
 * 不阻断页面渲染。</p>
 *
 * @author ai-lawyers
 */
@Component
@Intercepts({
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})
})
public class PiiDecryptInterceptor implements Interceptor
{
    private static final Logger log = LoggerFactory.getLogger(PiiDecryptInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable
    {
        Object result = invocation.proceed();
        if (result instanceof List)
        {
            for (Object row : (List<?>) result)
            {
                decryptRow(row);
            }
        }
        return result;
    }

    /** 按域对象类型解密 PII 字段 */
    private void decryptRow(Object row)
    {
        if (row instanceof AiCallRecord)
        {
            AiCallRecord record = (AiCallRecord) row;
            record.setCallerNumber(safeDecrypt("ai_call_record.caller_number",
                    record.getCallerNumber()));
        }
        else if (row instanceof AiCallLedger)
        {
            AiCallLedger ledger = (AiCallLedger) row;
            ledger.setCallerPhone(safeDecrypt("ai_call_ledger.caller_phone",
                    ledger.getCallerPhone()));
            ledger.setCallerIdCard(safeDecrypt("ai_call_ledger.caller_id_card",
                    ledger.getCallerIdCard()));
        }
        else if (row instanceof AiCallTicket)
        {
            AiCallTicket ticket = (AiCallTicket) row;
            // 工单号码来自 join ai_call_record（工单表本身无此列）
            ticket.setCallerNumber(safeDecrypt("ai_call_ticket.caller_number",
                    ticket.getCallerNumber()));
        }
        else if (row instanceof AiCallerProfile)
        {
            AiCallerProfile profile = (AiCallerProfile) row;
            profile.setCallerNumber(safeDecrypt("ai_caller_profile.caller_number",
                    profile.getCallerNumber()));
            profile.setCallerIdCard(safeDecrypt("ai_caller_profile.caller_id_card",
                    profile.getCallerIdCard()));
        }
    }

    /** 解密容错：失败不阻断，告警并保留原值（密文损坏/密钥问题由运维排查） */
    private String safeDecrypt(String field, String stored)
    {
        if (stored == null || !PiiCryptoUtils.isEncrypted(stored))
        {
            return stored;
        }
        try
        {
            return PiiCryptoUtils.decrypt(stored);
        }
        catch (Exception e)
        {
            log.warn("[PII解密失败] {} 保留原值: {}", field, e.getMessage());
            return stored;
        }
    }

    @Override
    public Object plugin(Object target)
    {
        return target instanceof ResultSetHandler ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties)
    {
        // 无外部配置项
    }
}
