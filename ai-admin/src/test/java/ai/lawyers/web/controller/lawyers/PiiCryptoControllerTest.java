package ai.lawyers.web.controller.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.system.service.lawyers.IPiiCryptoMigrationService;

/**
 * PII 字段级加密运维接口单元测试
 *
 * @author ai-lawyers
 */
@ExtendWith(MockitoExtension.class)
class PiiCryptoControllerTest
{
    @Mock
    private IPiiCryptoMigrationService piiCryptoMigrationService;

    @InjectMocks
    private PiiCryptoController controller;

    @Test
    void 三张表迁移接口透传结果()
    {
        Map<String, Object> callerProfile = new HashMap<String, Object>();
        callerProfile.put("migrated", 10);
        Map<String, Object> callRecord = new HashMap<String, Object>();
        callRecord.put("migrated", 20);
        Map<String, Object> callLedger = new HashMap<String, Object>();
        callLedger.put("migrated", 30);
        when(piiCryptoMigrationService.migrateCallerProfiles()).thenReturn(callerProfile);
        when(piiCryptoMigrationService.migrateCallRecords()).thenReturn(callRecord);
        when(piiCryptoMigrationService.migrateCallLedgers()).thenReturn(callLedger);

        assertSame(callerProfile, controller.migrateCallerProfile().get(AjaxResult.DATA_TAG));
        assertSame(callRecord, controller.migrateCallRecord().get(AjaxResult.DATA_TAG));
        assertSame(callLedger, controller.migrateCallLedger().get(AjaxResult.DATA_TAG));
    }

    @Test
    void 密钥轮换接口透传结果()
    {
        Map<String, Object> rotate = new HashMap<String, Object>();
        rotate.put("rotatedRows", 99);
        when(piiCryptoMigrationService.rotateKey()).thenReturn(rotate);

        AjaxResult result = controller.rotateKey();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        assertSame(rotate, result.get(AjaxResult.DATA_TAG));
        verify(piiCryptoMigrationService).rotateKey();
    }
}
