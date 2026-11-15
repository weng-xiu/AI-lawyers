package ai.lawyers.system.service.impl.lawyers.ivr.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IVR SpEL 模板渲染器单测：无模板直返、多表达式替换、缺失/非法表达式渲染为空。
 *
 * @author ai-lawyers
 */
class IvrTemplateRendererTest
{
    private IvrTemplateRenderer renderer;

    @BeforeEach
    void setUp()
    {
        renderer = new IvrTemplateRenderer();
    }

    @Test
    void returnsTemplateAsIsWhenNoExpression()
    {
        assertThat(renderer.render(null, new HashMap<>())).isNull();
        assertThat(renderer.render("", new HashMap<>())).isEmpty();
        assertThat(renderer.render("纯文本", new HashMap<>())).isEqualTo("纯文本");
        assertThat(renderer.render("未闭合${name", new HashMap<>())).isEqualTo("未闭合${name");
    }

    @Test
    void replacesMultipleExpressions()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "张三");
        vars.put("city", "北京");

        String text = renderer.render("您好，${name}！欢迎咨询${city}分所。", vars);

        assertThat(text).isEqualTo("您好，张三！欢迎咨询北京分所。");
    }

    @Test
    void supportsHashVariableAndPropertyAccess()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("count", 3);

        assertThat(renderer.render("共${count}条", vars)).isEqualTo("共3条");
        assertThat(renderer.render("共${#count}条", vars)).isEqualTo("共3条");
    }

    @Test
    void missingVariableRendersEmpty()
    {
        assertThat(renderer.render("值：${nothing}", new HashMap<>())).isEqualTo("值：");
    }

    @Test
    void invalidExpressionRendersEmpty()
    {
        assertThat(renderer.render("${1 +}", new HashMap<>())).isEmpty();
        assertThat(renderer.render("前${产}后", new HashMap<>())).isEqualTo("前后");
    }

    @Test
    void replacementValueWithDollarSignIsSafe()
    {
        Map<String, Object> vars = new HashMap<>();
        vars.put("amount", "$100");

        assertThat(renderer.render("金额${amount}", vars)).isEqualTo("金额$100");
    }
}
