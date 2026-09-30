package ai.lawyers.web.controller.lawyers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.lawyers.common.annotation.Log;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.enums.BusinessType;
import ai.lawyers.system.service.lawyers.IPiiCryptoMigrationService;

/**
 * PII 字段级加密运维接口（P3-G1-b V2.55）。
 *
 * <p>上线顺序：执行 DDL（sql/ai_system_g1_pii_encrypt_20260930.sql）→ 部署新版本
 * → 由管理员调用迁移接口处理存量明文（幂等，可重复执行直至 migrated 覆盖全部存量行）。
 * 迁移窗口内存量明文经三态解密兼容继续可读。</p>
 *
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/lawyers/pii")
public class PiiCryptoController extends BaseController
{
    @Autowired
    private IPiiCryptoMigrationService piiCryptoMigrationService;

    /**
     * 迁移 ai_caller_profile 存量明文为 SM4-GCM 密文并回填盲索引（幂等）。
     */
    @PreAuthorize("@ss.hasPermi('lawyers:pii:crypt')")
    @Log(title = "PII存量加密迁移", businessType = BusinessType.UPDATE)
    @PostMapping("/migrateCallerProfile")
    public AjaxResult migrateCallerProfile()
    {
        Map<String, Object> result = piiCryptoMigrationService.migrateCallerProfiles();
        return success(result);
    }
}
