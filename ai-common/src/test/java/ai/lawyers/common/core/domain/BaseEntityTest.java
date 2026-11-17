package ai.lawyers.common.core.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 实体基类测试：字段读写、查询参数懒初始化、搜索值忽略、空参数不序列化、
 * 时间格式化。
 *
 * @author ai-lawyers
 */
class BaseEntityTest
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void 字段读写()
    {
        BaseEntity entity = new BaseEntity();
        Date now = new Date();
        entity.setCreateBy("admin");
        entity.setCreateTime(now);
        entity.setUpdateBy("agent");
        entity.setUpdateTime(now);
        entity.setRemark("备注");
        entity.setSearchValue("关键字");

        assertEquals("admin", entity.getCreateBy());
        assertSame(now, entity.getCreateTime());
        assertEquals("agent", entity.getUpdateBy());
        assertSame(now, entity.getUpdateTime());
        assertEquals("备注", entity.getRemark());
        assertEquals("关键字", entity.getSearchValue());
    }

    @Test
    void 参数为空时懒初始化同一个Map()
    {
        BaseEntity entity = new BaseEntity();
        assertTrue(entity.getParams().isEmpty());

        Map<String, Object> params = entity.getParams();
        assertSame(params, entity.getParams());

        params.put("beginTime", "2026-01-01");
        assertEquals("2026-01-01", entity.getParams().get("beginTime"));
    }

    @Test
    void setParams直接替换()
    {
        BaseEntity entity = new BaseEntity();
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        entity.setParams(params);

        assertSame(params, entity.getParams());
    }

    @Test
    void 搜索值被忽略且时间按格式序列化() throws Exception
    {
        BaseEntity entity = new BaseEntity();
        entity.setSearchValue("不应输出");
        entity.setCreateBy("admin");
        entity.setCreateTime(new Date());

        String json = MAPPER.writeValueAsString(entity);

        assertFalse(json.contains("searchValue"));
        assertFalse(json.contains("不应输出"));
        assertTrue(json.contains("\"createBy\":\"admin\""));
        assertTrue(json.matches(".*\"createTime\":\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\".*"), json);
    }

    @Test
    void 空参数不参与序列化() throws Exception
    {
        String empty = MAPPER.writeValueAsString(new BaseEntity());
        assertFalse(empty.contains("params"));

        BaseEntity entity = new BaseEntity();
        entity.getParams().put("key", "value");
        assertTrue(MAPPER.writeValueAsString(entity).contains("\"params\":{\"key\":\"value\"}"));

        // 显式设置空 Map 同样不输出
        entity.setParams(new HashMap<>());
        assertFalse(MAPPER.writeValueAsString(entity).contains("params"));
    }

    @Test
    void 默认字段为空()
    {
        BaseEntity entity = new BaseEntity();

        assertNull(entity.getCreateBy());
        assertNull(entity.getCreateTime());
        assertNull(entity.getRemark());
        assertNull(entity.getSearchValue());
    }
}
