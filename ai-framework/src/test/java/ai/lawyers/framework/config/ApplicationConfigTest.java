package ai.lawyers.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 程序注解配置测试：Jackson 时区使用系统默认时区。
 *
 * @author ai-lawyers
 */
class ApplicationConfigTest
{
    @Test
    void jacksonObjectMapperCustomization_设置系统默认时区()
    {
        ApplicationConfig config = new ApplicationConfig();
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jacksonObjectMapperCustomization();

        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        customizer.customize(builder);
        ObjectMapper mapper = builder.build();

        assertEquals(TimeZone.getDefault(), mapper.getSerializationConfig().getTimeZone());
        assertEquals(TimeZone.getDefault(), mapper.getDeserializationConfig().getTimeZone());
    }
}
