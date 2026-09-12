package ai.lawyers.common.utils;

/**
 * 脱敏工具类
 *
 * @author ruoyi
 */
public class DesensitizedUtil
{
    /**
     * 密码的全部字符都用*代替，比如：******
     *
     * @param password 密码
     * @return 脱敏后的密码
     */
    public static String password(String password)
    {
        if (StringUtils.isBlank(password))
        {
            return StringUtils.EMPTY;
        }
        return StringUtils.repeat('*', password.length());
    }

    /**
     * 车牌中间用*代替，如果是错误的车牌，不处理
     *
     * @param carLicense 完整的车牌号
     * @return 脱敏后的车牌
     */
    public static String carLicense(String carLicense)
    {
        if (StringUtils.isBlank(carLicense))
        {
            return StringUtils.EMPTY;
        }
        // 普通车牌
        if (carLicense.length() == 7)
        {
            carLicense = StringUtils.hide(carLicense, 3, 6);
        }
        else if (carLicense.length() == 8)
        {
            // 新能源车牌
            carLicense = StringUtils.hide(carLicense, 3, 7);
        }
        return carLicense;
    }

    /**
     * 手机号脱敏：保留前 3 位与后 4 位，中间以 * 代替，如 138****8000。
     *
     * <p>N10/G1：日志中禁止明文打印手机号（PII）。长度不足 7 位时整体掩码，避免短号泄露；
     * 长度超过 11 位（含区号/异常字符）时同样保留前 3 后 4。</p>
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号；入参为空返回空串
     */
    public static String mobilePhone(String phone)
    {
        if (StringUtils.isBlank(phone))
        {
            return StringUtils.EMPTY;
        }
        if (phone.length() < 7)
        {
            return StringUtils.repeat('*', phone.length());
        }
        return StringUtils.hide(phone, 3, phone.length() - 4);
    }
}
