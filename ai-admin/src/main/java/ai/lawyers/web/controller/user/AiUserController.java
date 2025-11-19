package ai.lawyers.web.controller.user;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ai.lawyers.common.annotation.Anonymous;
import ai.lawyers.common.core.controller.BaseController;
import ai.lawyers.common.core.domain.AjaxResult;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.RegisterBody;
import ai.lawyers.common.enums.UserStatus;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.framework.web.service.SysRegisterService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 用户端用户控制器
 * 
 * @author ai-lawyers
 */
@RestController
@RequestMapping("/user")
public class AiUserController extends BaseController
{
    @Autowired
    private ISysUserService userService;
    
    @Autowired
    private SysRegisterService registerService;

    /**
     * 获取用户类型
     */
    @GetMapping("/type")
    public AjaxResult getUserType()
    {
        return AjaxResult.success("user");
    }
    
    /**
     * 用户注册
     */
    @Anonymous
    @PostMapping("/register")
    public AjaxResult register(@RequestBody RegisterBody user)
    {
        if (!("true".equals(configService.selectConfigByKey("sys.account.registerUser"))))
        {
            return error("当前系统没有开启注册功能");
        }
        
        String msg = registerService.register(user);
        return StringUtils.isEmpty(msg) ? success() : error(msg);
    }
}