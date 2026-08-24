package ai.lawyers.framework.security.handle;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import com.alibaba.fastjson2.JSON;
import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.model.LoginUser;
import ai.lawyers.common.utils.MessageUtils;
import ai.lawyers.common.utils.ServletUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.framework.manager.AsyncManager;
import ai.lawyers.framework.manager.factory.AsyncFactory;
import ai.lawyers.framework.web.service.TokenService;
import ai.lawyers.system.service.lawyers.IAiCallAgentStatusService;

/**
 * 自定义退出处理类 返回成功
 * 
 * @author ruoyi
 */
@Configuration
public class LogoutSuccessHandlerImpl implements LogoutSuccessHandler
{
    @Autowired
    private TokenService tokenService;

    /**
     * 坐席状态服务：用户主动退出系统时，同步将其名下坐席签出，
     * 避免账号已登出但坐席仍处于"在线"状态占用技能组席位。
     * 使用 required=false 以兼容未启用呼叫中心模块的部署场景。
     */
    @Autowired(required = false)
    private IAiCallAgentStatusService aiCallAgentStatusService;

    /**
     * 退出处理
     * 
     * @return
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (StringUtils.isNotNull(loginUser))
        {
            String userName = loginUser.getUsername();
            // 退出系统前同步签出该账号名下的坐席，防止坐席状态残留为"在线"
            try
            {
                if (aiCallAgentStatusService != null && loginUser.getUser() != null
                        && loginUser.getUser().getUserId() != null)
                {
                    aiCallAgentStatusService.agentLogout(null, loginUser.getUser().getUserId());
                }
            }
            catch (Exception e)
            {
                // 坐席签出失败不影响用户正常退出
            }
            // 删除用户缓存记录
            tokenService.delLoginUser(loginUser.getToken());
            // 记录用户退出日志
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(userName, Constants.LOGOUT, MessageUtils.message("user.logout.success")));
        }
        ServletUtils.renderString(response, JSON.toJSONString(AjaxResult.success(MessageUtils.message("user.logout.success"))));
    }
}
