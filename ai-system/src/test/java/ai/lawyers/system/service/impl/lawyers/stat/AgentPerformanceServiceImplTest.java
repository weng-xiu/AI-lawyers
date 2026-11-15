package ai.lawyers.system.service.impl.lawyers.stat;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import ai.lawyers.system.domain.lawyers.stat.AgentPerformanceVO;
import ai.lawyers.system.mapper.lawyers.stat.AgentPerformanceMapper;

/**
 * 坐席效能报表聚合服务单测。
 *
 * <p>覆盖按坐席名称与时间范围查询的委托。</p>
 *
 * @author ai-lawyers
 */
class AgentPerformanceServiceImplTest
{
    private AgentPerformanceServiceImpl service;

    @Mock private AgentPerformanceMapper agentPerformanceMapper;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        service = new AgentPerformanceServiceImpl();
        ReflectionTestUtils.setField(service, "agentPerformanceMapper", agentPerformanceMapper);
    }

    @Test
    void selectAgentPerformanceList_delegates()
    {
        Date begin = new Date(1000L);
        Date end = new Date(2000L);
        List<AgentPerformanceVO> list = Collections.singletonList(new AgentPerformanceVO());
        when(agentPerformanceMapper.selectAgentPerformance("张三", begin, end)).thenReturn(list);

        assertSame(list, service.selectAgentPerformanceList("张三", begin, end));
    }
}
