package ai.lawyers.system.service.impl.lawyers;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiPiiSearchToken;
import ai.lawyers.system.mapper.lawyers.AiPiiSearchTokenMapper;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * P3-G1-b2（V2.56）：token 服务重建测试——先删后写、token 数量/取值、空号码仅清理。
 *
 * @author ai-lawyers
 */
class PiiSearchTokenServiceImplTest
{
    private static final String PHONE = "13812345678";

    private AiPiiSearchTokenMapper mapper;

    private PiiSearchTokenServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        service = new PiiSearchTokenServiceImpl();
        mapper = mock(AiPiiSearchTokenMapper.class);
        setField(service, "tokenMapper", mapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuild_phone_deleteThenInsertAllPositions()
    {
        service.rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 1001L, PHONE);

        verify(mapper).deleteByOwner(IPiiSearchTokenService.OWNER_CALL_RECORD, 1001L);
        ArgumentCaptor<List<AiPiiSearchToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchInsertToken(captor.capture());
        List<AiPiiSearchToken> rows = captor.getValue();
        assertThat(rows).hasSize(11);
        assertThat(rows).extracting(AiPiiSearchToken::getTokenValue)
                .containsExactlyElementsOf(PiiCryptoUtils.phoneTokens(PHONE));
        assertThat(rows).allSatisfy(t ->
        {
            assertThat(t.getOwnerType()).isEqualTo(IPiiSearchTokenService.OWNER_CALL_RECORD);
            assertThat(t.getOwnerId()).isEqualTo(1001L);
        });
    }

    @Test
    void rebuild_emptyPhone_deleteOnly()
    {
        service.rebuild(IPiiSearchTokenService.OWNER_CALL_LEDGER, 2002L, "");
        verify(mapper).deleteByOwner(IPiiSearchTokenService.OWNER_CALL_LEDGER, 2002L);
        verify(mapper, never()).batchInsertToken(org.mockito.ArgumentMatchers.anyList());
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
