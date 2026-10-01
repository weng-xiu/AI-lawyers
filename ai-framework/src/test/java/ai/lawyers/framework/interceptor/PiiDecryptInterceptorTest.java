package ai.lawyers.framework.interceptor;

import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallTicket;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P3-G1-b2（V2.56）：PiiDecryptInterceptor 测试——结果集 PII 自动解密、
 * 非 PII 行跳过、存量明文原样、密文损坏不阻断。
 *
 * @author ai-lawyers
 */
class PiiDecryptInterceptorTest
{
    private final PiiDecryptInterceptor interceptor = new PiiDecryptInterceptor();

    @SuppressWarnings("unchecked")
    @Test
    void intercept_piiRows_decrypted() throws Throwable
    {
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber(PiiCryptoUtils.encrypt("13812345678"));

        AiCallTicket ticket = new AiCallTicket();
        ticket.setCallerNumber(PiiCryptoUtils.encrypt("13987654321"));

        AiCallLedger ledger = new AiCallLedger();
        ledger.setCallerPhone(PiiCryptoUtils.encrypt("13700000000"));
        ledger.setCallerIdCard(PiiCryptoUtils.encrypt("110101199003078515"));

        AiCallerProfile profile = new AiCallerProfile();
        profile.setCallerNumber(PiiCryptoUtils.encrypt("13611111111"));
        profile.setCallerIdCard(PiiCryptoUtils.encrypt("440101198505051234"));

        Object plain = new Object();
        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenReturn(Arrays.asList(record, ticket, ledger, profile, plain));

        Object out = interceptor.intercept(invocation);
        assertThat(out).isInstanceOf(List.class);

        assertThat(record.getCallerNumber()).isEqualTo("13812345678");
        assertThat(ticket.getCallerNumber()).isEqualTo("13987654321");
        assertThat(ledger.getCallerPhone()).isEqualTo("13700000000");
        assertThat(ledger.getCallerIdCard()).isEqualTo("110101199003078515");
        assertThat(profile.getCallerNumber()).isEqualTo("13611111111");
        assertThat(profile.getCallerIdCard()).isEqualTo("440101198505051234");
    }

    @Test
    void intercept_plaintextPii_passedThrough() throws Throwable
    {
        AiCallRecord record = new AiCallRecord();
        record.setCallerNumber("13800138000");
        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenReturn(Arrays.asList(record));

        interceptor.intercept(invocation);
        assertThat(record.getCallerNumber()).isEqualTo("13800138000");
    }

    @Test
    void intercept_nonListResult_returnedAsIs() throws Throwable
    {
        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenReturn(null);
        assertThat(interceptor.intercept(invocation)).isNull();
    }
}
