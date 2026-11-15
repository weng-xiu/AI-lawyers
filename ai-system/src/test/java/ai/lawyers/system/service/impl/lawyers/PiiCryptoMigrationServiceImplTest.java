package ai.lawyers.system.service.impl.lawyers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.common.utils.sign.PiiCryptoUtils;
import ai.lawyers.system.domain.lawyers.AiCallLedger;
import ai.lawyers.system.domain.lawyers.AiCallRecord;
import ai.lawyers.system.domain.lawyers.AiCallerProfile;
import ai.lawyers.system.mapper.lawyers.AiCallLedgerMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordArchiveMapper;
import ai.lawyers.system.mapper.lawyers.AiCallRecordMapper;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.service.lawyers.IPiiSearchTokenService;
import ai.lawyers.system.service.lawyers.storage.RecordingStorageService;

/**
 * P3-G1 存量 PII 迁移与密钥轮换单测。
 *
 * <p>覆盖档案/话单/归档/台账四类数据的密文与盲索引补齐、幂等零写库、唯一键冲突与
 * 单行异常隔离、token 重建条件，以及轮换时的字段重加密与录音重写。</p>
 *
 * @author ai-lawyers
 */
class PiiCryptoMigrationServiceImplTest
{
    private static final String PHONE = "13800138000";

    private PiiCryptoMigrationServiceImpl service;

    @Mock private AiCallerProfileMapper callerProfileMapper;
    @Mock private AiCallRecordMapper callRecordMapper;
    @Mock private AiCallRecordArchiveMapper archiveMapper;
    @Mock private AiCallLedgerMapper callLedgerMapper;
    @Mock private IPiiSearchTokenService piiSearchTokenService;
    @Mock private RecordingStorageService recordingStorageService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new PiiCryptoMigrationServiceImpl();
        ReflectionTestUtils.setField(service, "callerProfileMapper", callerProfileMapper);
        ReflectionTestUtils.setField(service, "callRecordMapper", callRecordMapper);
        ReflectionTestUtils.setField(service, "archiveMapper", archiveMapper);
        ReflectionTestUtils.setField(service, "callLedgerMapper", callLedgerMapper);
        ReflectionTestUtils.setField(service, "piiSearchTokenService", piiSearchTokenService);
        ReflectionTestUtils.setField(service, "recordingStorageService", recordingStorageService);
    }

    // ---------------- 来电人档案 ----------------

    @Test
    void migrateCallerProfiles_empty_returnsZeros()
    {
        when(callerProfileMapper.selectRawForMigrate()).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.migrateCallerProfiles();

        assertEquals(0, result.get("total"));
        assertEquals(0, result.get("migrated"));
        assertEquals(0, result.get("conflictSkipped"));
        assertEquals(0, result.get("tokenRebuilt"));
    }

    @Test
    void migrateCallerProfiles_plainFields_encryptsAndBuildsIndexes()
    {
        when(callerProfileMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(
                profileRow(1L, PHONE, "110101199001011234")));
        when(callerProfileMapper.updateAiCallerProfile(any())).thenReturn(1);

        Map<String, Object> result = service.migrateCallerProfiles();

        assertEquals(1, result.get("migrated"));
        assertEquals(1, result.get("tokenRebuilt"));
        ArgumentCaptor<AiCallerProfile> captor = ArgumentCaptor.forClass(AiCallerProfile.class);
        verify(callerProfileMapper).updateAiCallerProfile(captor.capture());
        AiCallerProfile update = captor.getValue();
        assertEquals(1L, update.getProfileId());
        assertTrue(PiiCryptoUtils.isEncrypted(update.getCallerNumber()));
        assertEquals(PiiCryptoUtils.blindIndex(PHONE), update.getCallerNumberIndex());
        assertTrue(PiiCryptoUtils.isEncrypted(update.getCallerIdCard()));
        assertEquals(PiiCryptoUtils.blindIndex("110101199001011234"), update.getCallerIdCardIndex());
        verify(piiSearchTokenService).rebuild(
                IPiiSearchTokenService.OWNER_CALLER_PROFILE, 1L, PHONE);
    }

    @Test
    void migrateCallerProfiles_completeRow_isIdempotent()
    {
        Map<String, Object> row = profileRow(2L, PiiCryptoUtils.encrypt(PHONE), null);
        row.put("callerNumberIndex", PiiCryptoUtils.blindIndex(PHONE));
        when(callerProfileMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(row));
        when(piiSearchTokenService.countByOwner(IPiiSearchTokenService.OWNER_CALLER_PROFILE, 2L))
                .thenReturn(3);

        Map<String, Object> result = service.migrateCallerProfiles();

        assertEquals(0, result.get("migrated"));
        assertEquals(0, result.get("tokenRebuilt"));
        verify(callerProfileMapper, never()).updateAiCallerProfile(any());
        verify(piiSearchTokenService, never()).rebuild(anyString(), anyLong(), anyString());
    }

    @Test
    void migrateCallerProfiles_encryptedButMissingIndex_fillsIndexOnly()
    {
        when(callerProfileMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(
                profileRow(3L, PiiCryptoUtils.encrypt(PHONE), null)));
        when(callerProfileMapper.updateAiCallerProfile(any())).thenReturn(1);
        when(piiSearchTokenService.countByOwner(anyString(), anyLong())).thenReturn(0);

        Map<String, Object> result = service.migrateCallerProfiles();

        assertEquals(1, result.get("migrated"));
        assertEquals(1, result.get("tokenRebuilt"));
        ArgumentCaptor<AiCallerProfile> captor = ArgumentCaptor.forClass(AiCallerProfile.class);
        verify(callerProfileMapper).updateAiCallerProfile(captor.capture());
        assertNull(captor.getValue().getCallerNumber());
        assertEquals(PiiCryptoUtils.blindIndex(PHONE), captor.getValue().getCallerNumberIndex());
    }

    @Test
    void migrateCallerProfiles_duplicateKey_countsConflict()
    {
        when(callerProfileMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(
                profileRow(4L, PHONE, null)));
        when(callerProfileMapper.updateAiCallerProfile(any()))
                .thenThrow(new DuplicateKeyException("uk_caller_number"));

        Map<String, Object> result = service.migrateCallerProfiles();

        assertEquals(0, result.get("migrated"));
        assertEquals(1, result.get("conflictSkipped"));
        assertEquals(1, result.get("tokenRebuilt"));
    }

    @Test
    void migrateCallerProfiles_blankFields_skipsUpdate()
    {
        when(callerProfileMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(
                profileRow(5L, " ", null)));

        Map<String, Object> result = service.migrateCallerProfiles();

        assertEquals(0, result.get("migrated"));
        assertEquals(0, result.get("tokenRebuilt"));
        verify(callerProfileMapper, never()).updateAiCallerProfile(any());
    }

    // ---------------- 话单 ----------------

    @Test
    void migrateCallRecords_hotAndArchive_migrateBoth()
    {
        when(callRecordMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(
                recordRow(10L, PHONE, null)));
        when(archiveMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(
                recordRow(11L, PHONE, null)));
        when(callRecordMapper.updateAiCallRecord(any())).thenReturn(1);

        Map<String, Object> result = service.migrateCallRecords();

        assertEquals(1, result.get("hotTotal"));
        assertEquals(1, result.get("archiveTotal"));
        assertEquals(2, result.get("migrated"));
        assertEquals(2, result.get("tokenRebuilt"));
        assertEquals(0, result.get("failed"));
        verify(archiveMapper).updateArchiveEncryption(eq(11L), anyString(), anyString());
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 10L, PHONE);
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 11L, PHONE);
    }

    @Test
    void migrateCallRecords_badRow_isolatedAndContinues()
    {
        Map<String, Object> bad = new HashMap<>();
        bad.put("recordId", "not-a-number");
        bad.put("callerNumber", PHONE);
        when(callRecordMapper.selectRawForMigrate()).thenReturn(Arrays.asList(
                bad, recordRow(12L, PHONE, null)));
        when(callRecordMapper.updateAiCallRecord(any())).thenReturn(1);

        Map<String, Object> result = service.migrateCallRecords();

        assertEquals(1, result.get("failed"));
        assertEquals(1, result.get("migrated"));
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 12L, PHONE);
    }

    @Test
    void migrateCallRecords_completeRow_noWriteAndNoTokenRebuild()
    {
        Map<String, Object> row = recordRow(13L, PiiCryptoUtils.encrypt(PHONE),
                PiiCryptoUtils.blindIndex(PHONE));
        when(callRecordMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(row));
        when(archiveMapper.selectRawForMigrate()).thenReturn(Collections.emptyList());
        when(piiSearchTokenService.countByOwner(IPiiSearchTokenService.OWNER_CALL_RECORD, 13L))
                .thenReturn(1);

        Map<String, Object> result = service.migrateCallRecords();

        assertEquals(0, result.get("migrated"));
        assertEquals(0, result.get("tokenRebuilt"));
        verify(callRecordMapper, never()).updateAiCallRecord(any());
    }

    @Test
    void migrateCallRecords_noTokenService_stillMigrates()
    {
        ReflectionTestUtils.setField(service, "piiSearchTokenService", null);
        when(callRecordMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(
                recordRow(14L, PHONE, null)));
        when(archiveMapper.selectRawForMigrate()).thenReturn(Collections.emptyList());
        when(callRecordMapper.updateAiCallRecord(any())).thenReturn(1);

        Map<String, Object> result = service.migrateCallRecords();

        assertEquals(1, result.get("migrated"));
        assertEquals(0, result.get("tokenRebuilt"));
    }

    // ---------------- 台账 ----------------

    @Test
    void migrateCallLedgers_plainPhoneAndIdCard_encryptsBoth()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("ledgerId", 20L);
        row.put("callerPhone", PHONE);
        row.put("callerIdCard", "110101199001011234");
        when(callLedgerMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(row));
        when(callLedgerMapper.updateAiCallLedger(any())).thenReturn(1);

        Map<String, Object> result = service.migrateCallLedgers();

        assertEquals(1, result.get("migrated"));
        assertEquals(1, result.get("tokenRebuilt"));
        ArgumentCaptor<AiCallLedger> captor = ArgumentCaptor.forClass(AiCallLedger.class);
        verify(callLedgerMapper).updateAiCallLedger(captor.capture());
        assertTrue(PiiCryptoUtils.isEncrypted(captor.getValue().getCallerPhone()));
        assertTrue(PiiCryptoUtils.isEncrypted(captor.getValue().getCallerIdCard()));
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_LEDGER, 20L, PHONE);
    }

    @Test
    void migrateCallLedgers_completePhoneButMissingToken_rebuildsOnly()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("ledgerId", 21L);
        row.put("callerPhone", PiiCryptoUtils.encrypt(PHONE));
        when(callLedgerMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(row));
        when(piiSearchTokenService.countByOwner(IPiiSearchTokenService.OWNER_CALL_LEDGER, 21L))
                .thenReturn(0);

        Map<String, Object> result = service.migrateCallLedgers();

        assertEquals(0, result.get("migrated"));
        assertEquals(1, result.get("tokenRebuilt"));
        verify(callLedgerMapper, never()).updateAiCallLedger(any());
    }

    @Test
    void migrateCallLedgers_badRow_isolated()
    {
        Map<String, Object> bad = new HashMap<>();
        bad.put("ledgerId", "x");
        when(callLedgerMapper.selectRawForMigrate()).thenReturn(Collections.singletonList(bad));

        Map<String, Object> result = service.migrateCallLedgers();

        assertEquals(1, result.get("failed"));
        assertEquals(0, result.get("migrated"));
    }

    // ---------------- 密钥轮换 ----------------

    @Test
    void rotateKey_empty_returnsZeros()
    {
        givenEmptyRotateSources();

        Map<String, Object> result = service.rotateKey();

        assertEquals(0, result.get("profiles"));
        assertEquals(0, result.get("records"));
        assertEquals(0, result.get("archives"));
        assertEquals(0, result.get("ledgers"));
        assertEquals(0, result.get("recordingEncrypted"));
        assertEquals(0, result.get("failed"));
    }

    @Test
    void rotateKey_profiles_reEncryptAndRebuildToken()
    {
        givenEmptyRotateSources();
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(1L);
        profile.setCallerNumber(PHONE);
        profile.setCallerIdCard("110101199001011234");
        profile.setCallerNumberIndex("old-index");
        when(callerProfileMapper.selectAiCallerProfileList(any()))
                .thenReturn(Collections.singletonList(profile));
        when(callerProfileMapper.updateAiCallerProfile(any())).thenReturn(1);

        Map<String, Object> result = service.rotateKey();

        assertEquals(1, result.get("profiles"));
        ArgumentCaptor<AiCallerProfile> captor = ArgumentCaptor.forClass(AiCallerProfile.class);
        verify(callerProfileMapper).updateAiCallerProfile(captor.capture());
        assertTrue(PiiCryptoUtils.isEncrypted(captor.getValue().getCallerNumber()));
        assertTrue(PiiCryptoUtils.isEncrypted(captor.getValue().getCallerIdCard()));
        assertEquals(PiiCryptoUtils.blindIndex(PHONE), captor.getValue().getCallerNumberIndex());
        verify(piiSearchTokenService).rebuild(
                IPiiSearchTokenService.OWNER_CALLER_PROFILE, 1L, PHONE);
    }

    @Test
    void rotateKey_records_reEncryptPhoneAndRecording() throws Exception
    {
        givenEmptyRotateSources();
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(10L);
        record.setCallerNumber(PHONE);
        record.setRecordFile("rec.wav");
        when(callRecordMapper.selectAiCallRecordList(any()))
                .thenReturn(Collections.singletonList(record));
        when(callRecordMapper.updateAiCallRecord(any())).thenReturn(1);
        when(recordingStorageService.load("rec.wav"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        Map<String, Object> result = service.rotateKey();

        assertEquals(1, result.get("records"));
        assertEquals(1, result.get("recordingEncrypted"));
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 10L, PHONE);
        verify(recordingStorageService).save(eq("rec.wav"), any(InputStream.class), eq(3L),
                eq("application/octet-stream"));
    }

    @Test
    void rotateKey_recordWithoutPhone_stillRewritesRecording() throws Exception
    {
        givenEmptyRotateSources();
        AiCallRecord record = new AiCallRecord();
        record.setRecordId(11L);
        record.setRecordFile("rec2.wav");
        when(callRecordMapper.selectAiCallRecordList(any()))
                .thenReturn(Collections.singletonList(record));
        when(recordingStorageService.load("rec2.wav"))
                .thenReturn(new ByteArrayInputStream(new byte[]{9}));

        Map<String, Object> result = service.rotateKey();

        assertEquals(1, result.get("records"));
        verify(callRecordMapper, never()).updateAiCallRecord(any());
        verify(recordingStorageService).save(eq("rec2.wav"), any(InputStream.class), eq(1L),
                anyString());
    }

    @Test
    void rotateKey_archivesAndLedgers_reEncrypt()
    {
        givenEmptyRotateSources();
        AiCallRecord archive = new AiCallRecord();
        archive.setRecordId(30L);
        archive.setCallerNumber(PiiCryptoUtils.encrypt(PHONE));
        when(archiveMapper.selectArchiveList(any())).thenReturn(Collections.singletonList(archive));
        AiCallLedger ledger = new AiCallLedger();
        ledger.setLedgerId(40L);
        ledger.setCallerPhone(PHONE);
        ledger.setCallerIdCard(PiiCryptoUtils.encrypt("110101199001011234"));
        when(callLedgerMapper.selectAiCallLedgerList(any()))
                .thenReturn(Collections.singletonList(ledger));
        when(callLedgerMapper.updateAiCallLedger(any())).thenReturn(1);

        Map<String, Object> result = service.rotateKey();

        assertEquals(1, result.get("archives"));
        assertEquals(1, result.get("ledgers"));
        verify(archiveMapper).updateArchiveEncryption(eq(30L), anyString(), anyString());
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_RECORD, 30L, PHONE);
        verify(piiSearchTokenService).rebuild(IPiiSearchTokenService.OWNER_CALL_LEDGER, 40L, PHONE);
        ArgumentCaptor<AiCallLedger> captor = ArgumentCaptor.forClass(AiCallLedger.class);
        verify(callLedgerMapper).updateAiCallLedger(captor.capture());
        assertTrue(PiiCryptoUtils.isEncrypted(captor.getValue().getCallerIdCard()));
    }

    @Test
    void rotateKey_rowFailure_isolatedAndCounted()
    {
        givenEmptyRotateSources();
        AiCallerProfile profile = new AiCallerProfile();
        profile.setProfileId(2L);
        profile.setCallerNumber(PHONE);
        when(callerProfileMapper.selectAiCallerProfileList(any()))
                .thenReturn(Collections.singletonList(profile));
        when(callerProfileMapper.updateAiCallerProfile(any())).thenReturn(1);
        doThrow(new RuntimeException("token down"))
                .when(piiSearchTokenService).rebuild(anyString(), anyLong(), anyString());

        Map<String, Object> result = service.rotateKey();

        assertEquals(1, result.get("failed"));
        assertEquals(0, result.get("profiles"));
    }

    // ---------------- 辅助 ----------------

    private void givenEmptyRotateSources()
    {
        when(callerProfileMapper.selectAiCallerProfileList(any())).thenReturn(Collections.emptyList());
        when(callRecordMapper.selectAiCallRecordList(any())).thenReturn(Collections.emptyList());
        when(archiveMapper.selectArchiveList(any())).thenReturn(Collections.emptyList());
        when(callLedgerMapper.selectAiCallLedgerList(any())).thenReturn(Collections.emptyList());
    }

    private Map<String, Object> profileRow(Long profileId, String number, String idCard)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("profileId", profileId);
        row.put("callerNumber", number);
        row.put("callerIdCard", idCard);
        return row;
    }

    private Map<String, Object> recordRow(Long recordId, String number, String index)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("recordId", recordId);
        row.put("callerNumber", number);
        row.put("callerNumberIndex", index);
        return row;
    }
}
