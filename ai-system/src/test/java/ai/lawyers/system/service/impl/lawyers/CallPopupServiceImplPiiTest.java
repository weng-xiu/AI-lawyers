package ai.lawyers.system.service.impl.lawyers;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-G1-b（V2.55）：来电弹屏 PII 加密路径测试——盲索引查询、读后解密、
 * 写前加密与无档案默认分析。
 */
class CallPopupServiceImplPiiTest
{
    private static final String PHONE = "13812345678";

    private AiCallerProfileMapper callerProfileMapper;
    private CallPopupServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        service = new CallPopupServiceImpl();
        callerProfileMapper = mock(AiCallerProfileMapper.class);
        setField("aiCallerProfileMapper", callerProfileMapper);
        // 以下两个 mapper 在本测试路径不触达 PII 逻辑，注入空 mock 防止 NPE
        setField("aiCallRecordMapper", mock(ai.lawyers.system.mapper.lawyers.AiCallRecordMapper.class));
        setField("aiCallTicketMapper", mock(ai.lawyers.system.mapper.lawyers.AiCallTicketMapper.class));
    }

    @Test
    void getPopupProfile_queriesByBlindIndexAndDecrypts()
    {
        AiCallerProfile stored = new AiCallerProfile();
        stored.setProfileId(1L);
        stored.setCallerNumber(PiiCryptoUtils.encrypt(PHONE));
        stored.setCallerIdCard(PiiCryptoUtils.encrypt("110101199003078515"));
        stored.setIntentPrediction("劳动纠纷");
        when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex(any())).thenReturn(stored);

        Map<String, Object> result = service.getPopupProfile(PHONE);

        // 查询必须走盲索引而非明文/密文列
        ArgumentCaptor<String> indexCaptor = ArgumentCaptor.forClass(String.class);
        verify(callerProfileMapper).selectAiCallerProfileByCallerNumberIndex(indexCaptor.capture());
        assertThat(indexCaptor.getValue()).isEqualTo(PiiCryptoUtils.blindIndex(PHONE));

        // 域对象读后解密回明文（展示层脱敏逻辑不变）
        @SuppressWarnings("unchecked")
        AiCallerProfile profile = (AiCallerProfile) result.get("profile");
        assertThat(profile.getCallerNumber()).isEqualTo(PHONE);
        assertThat(profile.getCallerIdCard()).isEqualTo("110101199003078515");
        assertThat(profile.getIntentPrediction()).isEqualTo("劳动纠纷");
    }

    @Test
    void getPopupProfile_withoutProfile_returnsDefaults()
    {
        when(callerProfileMapper.selectAiCallerProfileByCallerNumberIndex(any())).thenReturn(null);

        Map<String, Object> result = service.getPopupProfile(PHONE);

        assertThat(result.get("profile")).isNull();
        assertThat(result.get("intentPrediction")).isEqualTo("未知");
        assertThat(result.get("riskLevel")).isEqualTo("0");
        assertThat(result.get("emotionStatus")).isEqualTo("平稳");
    }

    @Test
    void updateAiCallerProfile_encryptsPiiAndFillsBlindIndex()
    {
        AiCallerProfile input = new AiCallerProfile();
        input.setProfileId(1L);
        input.setCallerNumber(PHONE);
        input.setCallerIdCard("110101199003078515");

        service.updateAiCallerProfile(input);

        ArgumentCaptor<AiCallerProfile> captor = ArgumentCaptor.forClass(AiCallerProfile.class);
        verify(callerProfileMapper).updateAiCallerProfile(captor.capture());
        AiCallerProfile saved = captor.getValue();
        // 落库为密文 + 盲索引，密文可解回原明文
        assertThat(PiiCryptoUtils.isEncrypted(saved.getCallerNumber())).isTrue();
        assertThat(PiiCryptoUtils.decrypt(saved.getCallerNumber())).isEqualTo(PHONE);
        assertThat(saved.getCallerNumberIndex()).isEqualTo(PiiCryptoUtils.blindIndex(PHONE));
        assertThat(PiiCryptoUtils.isEncrypted(saved.getCallerIdCard())).isTrue();
        assertThat(saved.getCallerIdCardIndex()).isEqualTo(PiiCryptoUtils.blindIndex("110101199003078515"));
    }

    @Test
    void updateAiCallerProfile_resubmittedCipher_keepsConsistentIndex()
    {
        // 重提交旧密文（前端原样回传）也应产出一致盲索引，不破坏唯一防重
        String cipher = PiiCryptoUtils.encrypt(PHONE);
        AiCallerProfile input = new AiCallerProfile();
        input.setProfileId(1L);
        input.setCallerNumber(cipher);
        input.setCallerNumberIndex("stale-index");

        service.updateAiCallerProfile(input);

        ArgumentCaptor<AiCallerProfile> captor = ArgumentCaptor.forClass(AiCallerProfile.class);
        verify(callerProfileMapper).updateAiCallerProfile(captor.capture());
        AiCallerProfile saved = captor.getValue();
        assertThat(saved.getCallerNumberIndex()).isEqualTo(PiiCryptoUtils.blindIndex(PHONE));
        assertThat(PiiCryptoUtils.decrypt(saved.getCallerNumber())).isEqualTo(PHONE);
    }

    private void setField(String name, Object value) throws Exception
    {
        Field field = CallPopupServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
