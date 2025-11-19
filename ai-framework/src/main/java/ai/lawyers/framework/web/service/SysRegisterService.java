package ai.lawyers.framework.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ai.lawyers.common.constant.CacheConstants;
import ai.lawyers.common.constant.Constants;
import ai.lawyers.common.constant.UserConstants;
import ai.lawyers.common.core.domain.entity.SysUser;
import ai.lawyers.common.core.domain.model.RegisterBody;
import ai.lawyers.common.core.redis.RedisCache;
import ai.lawyers.common.exception.user.CaptchaException;
import ai.lawyers.common.exception.user.CaptchaExpireException;
import ai.lawyers.common.utils.DateUtils;
import ai.lawyers.common.utils.MessageUtils;
import ai.lawyers.common.utils.SecurityUtils;
import ai.lawyers.common.utils.StringUtils;
import ai.lawyers.framework.manager.AsyncManager;
import ai.lawyers.framework.manager.factory.AsyncFactory;
import ai.lawyers.system.service.ISysConfigService;
import ai.lawyers.system.service.ISysUserService;

/**
 * 注册校验方法
 * 
 * @author ruoyi
 */
@Component
public class SysRegisterService
{
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private RedisCache redisCache;

    /**
     * 注册
     */
    public String register(RegisterBody registerBody)
    {
        String msg = "", username = registerBody.getUsername(), password = registerBody.getPassword();
        SysUser sysUser = new SysUser();
        sysUser.setUserName(username);

        // 验证码开关
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }

        if (StringUtils.isEmpty(username))
        {
            msg = "用户名不能为空";
        }
        else if (StringUtils.isEmpty(password))
        {
            msg = "用户密码不能为空";
        }
        else if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            msg = "账户长度必须在2到20个字符之间";
        }
        else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            msg = "密码长度必须在5到20个字符之间";
        }
        else if (!userService.checkUserNameUnique(sysUser))
        {
            msg = "保存用户'" + username + "'失败，注册账号已存在";
        }
        else
        {
            // 设置用户信息
            sysUser.setNickName(StringUtils.isNotEmpty(registerBody.getNickName()) ? registerBody.getNickName() : username);
            sysUser.setEmail(registerBody.getEmail());
            sysUser.setPhonenumber(registerBody.getPhonenumber());
            sysUser.setSex(registerBody.getSex());
            // 设置用户类型，默认为普通用户'01'
            sysUser.setUserType(StringUtils.isNotEmpty(registerBody.getUserType()) ? registerBody.getUserType() : "01");
            sysUser.setPwdUpdateDate(DateUtils.getNowDate());
            sysUser.setPassword(SecurityUtils.encryptPassword(password));
            
            boolean regFlag = userService.registerUser(sysUser);
            if (!regFlag)
            {
                msg = "注册失败,请联系系统管理人员";
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success")));
            }
        }
        return msg;
    }

    /**
     * 校验验证码
     * 
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public void validateCaptcha(String username, String code, String uuid)
    {
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
        String captcha = redisCache.getCacheObject(verifyKey);
        redisCache.deleteObject(verifyKey);
        if (captcha == null)
        {
            throw new CaptchaExpireException();
        }
        if (!code.equalsIgnoreCase(captcha))
        {
            throw new CaptchaException();
        }
    }
}
