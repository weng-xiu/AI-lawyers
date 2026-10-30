package ai.lawyers.admin.controller.lawyers;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ai.lawyers.system.domain.lawyers.AiLegalKnowledge;
import ai.lawyers.system.domain.lawyers.AiMessage;
import ai.lawyers.system.mapper.lawyers.AiCallerProfileMapper;
import ai.lawyers.system.mapper.lawyers.AiLegalKnowledgeMapper;
import ai.lawyers.system.service.lawyers.IAiCallTicketService;
import ai.lawyers.system.service.lawyers.IAiChannelIdentityService;
import ai.lawyers.system.service.lawyers.IAiExternalOrgService;
import ai.lawyers.system.service.lawyers.IAiMessageService;
import ai.lawyers.web.support.AbstractPermMvcTest;

/**
 * F10 门户接口行为回归：常见问题字段裁剪/条数收敛，消息中心强制按当前登录用户过滤
 * （防止通过 receiverUserId 越权读取他人站内信）。
 *
 * <p>权限 401/200 由 {@link AiUserPortalControllerPermTest} 覆盖，本类只断言业务语义。</p>
 */
public class AiUserPortalF10ControllerTest extends AbstractPermMvcTest
{
    private static final String BASE = "/lawyers/portal";

    /** 与基类 {@link #loginAs} 构造的用户ID一致 */
    private static final Long CURRENT_USER_ID = 1001L;

    @Mock
    private IAiCallTicketService callTicketService;

    @Mock
    private IAiExternalOrgService externalOrgService;

    @Mock
    private AiCallerProfileMapper callerProfileMapper;

    @Mock
    private IAiChannelIdentityService channelIdentityService;

    @Mock
    private AiLegalKnowledgeMapper legalKnowledgeMapper;

    @Mock
    private IAiMessageService messageService;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        when(callTicketService.selectAiCallTicketList(any())).thenReturn(new ArrayList<>());
        when(messageService.selectAiMessageList(any())).thenReturn(new ArrayList<>());
        startMvc(AiUserPortalController.class);
    }

    @Override
    protected void registerMocks()
    {
        registerMock("callTicketService", callTicketService);
        registerMock("externalOrgService", externalOrgService);
        registerMock("callerProfileMapper", callerProfileMapper);
        registerMock("channelIdentityService", channelIdentityService);
        registerMock("legalKnowledgeMapper", legalKnowledgeMapper);
        registerMock("messageService", messageService);
    }

    // ---------------- FAQ ----------------

    @Test
    void faq_defaultLimit8_andProjectsOnlyPublicFields() throws Exception
    {
        AiLegalKnowledge row = new AiLegalKnowledge();
        row.setKnowledgeId(1L);
        row.setTitle("劳动合同到期不续签是否有补偿？");
        row.setCategoryId(2L);
        row.setViewCount(128L);
        // 超过 160 字的内容应被摘要截断，且不返回 content 全文
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 40; i++)
        {
            longText.append("经济补偿按劳动者在本单位工作的年限计算。");
        }
        row.setContent("  " + longText + "  ");
        when(legalKnowledgeMapper.selectFaqKnowledge(isNull(), eq(8)))
                .thenReturn(Collections.singletonList(row));

        mockMvc.perform(get(BASE + "/faq").with(loginAs()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].knowledgeId").value(1))
                .andExpect(jsonPath("$.data[0].title").value("劳动合同到期不续签是否有补偿？"))
                .andExpect(jsonPath("$.data[0].categoryId").value(2))
                .andExpect(jsonPath("$.data[0].viewCount").value(128))
                .andExpect(jsonPath("$.data[0].summary", endsWith("…")))
                .andExpect(content().string(not(containsString(longText.toString()))));
    }

    @Test
    void faq_limitClampedTo20() throws Exception
    {
        when(legalKnowledgeMapper.selectFaqKnowledge(isNull(), eq(20))).thenReturn(new ArrayList<>());

        mockMvc.perform(get(BASE + "/faq").param("limit", "100").with(loginAs()))
                .andExpect(status().isOk());

        verify(legalKnowledgeMapper).selectFaqKnowledge(isNull(), eq(20));
    }

    @Test
    void faq_limitClampedTo1_whenNonPositive() throws Exception
    {
        when(legalKnowledgeMapper.selectFaqKnowledge(isNull(), eq(1))).thenReturn(new ArrayList<>());

        mockMvc.perform(get(BASE + "/faq").param("limit", "-5").with(loginAs()))
                .andExpect(status().isOk());

        verify(legalKnowledgeMapper).selectFaqKnowledge(isNull(), eq(1));
    }

    // ---------------- 消息中心 ----------------

    @Test
    void messageList_forcesCurrentReceiver_ignoresRequestReceiver() throws Exception
    {
        mockMvc.perform(get(BASE + "/message/list")
                        .param("receiverUserId", "999")
                        .param("msgType", "5")
                        .param("isRead", "0")
                        .with(loginAs()))
                .andExpect(status().isOk());

        ArgumentCaptor<AiMessage> captor = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageService).selectAiMessageList(captor.capture());
        AiMessage filter = captor.getValue();
        assertNotNull(filter);
        assertEquals(CURRENT_USER_ID, filter.getReceiverUserId(), "接收人必须强制为当前登录用户");
        assertEquals("5", filter.getMsgType());
        assertEquals("0", filter.getIsRead());
    }

    @Test
    void unreadCount_usesCurrentUser() throws Exception
    {
        when(messageService.countUnread(CURRENT_USER_ID)).thenReturn(3);

        mockMvc.perform(get(BASE + "/message/unreadCount").with(loginAs()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));

        verify(messageService).countUnread(CURRENT_USER_ID);
    }

    @Test
    void readMessage_marksOwnMessage() throws Exception
    {
        when(messageService.markRead(7L, CURRENT_USER_ID)).thenReturn(1);

        mockMvc.perform(put(BASE + "/message/read/7").with(loginAs()))
                .andExpect(status().isOk());

        verify(messageService).markRead(7L, CURRENT_USER_ID);
    }

    @Test
    void readAll_marksOwnMessages() throws Exception
    {
        when(messageService.markAllRead(CURRENT_USER_ID)).thenReturn(2);

        mockMvc.perform(put(BASE + "/message/readAll").with(loginAs()))
                .andExpect(status().isOk());

        verify(messageService).markAllRead(CURRENT_USER_ID);
    }
}
