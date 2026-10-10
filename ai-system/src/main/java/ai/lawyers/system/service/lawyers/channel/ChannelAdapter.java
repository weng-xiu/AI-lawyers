package ai.lawyers.system.service.lawyers.channel;

/**
 * P3-#16（全渠道 G9）：渠道适配器统一接口。
 *
 * <p>职责链：回调验签（{@link #verifySignature}）→ 密文解密（{@link #decrypt}）→
 * 报文归一（{@link #normalize}）→ 下游按 {@link ChannelMessage} 统一路由，
 * 复用现有咨询/工单/ACD 体系；回信经 {@link #reply} 组装（真实通道发送由
 * 各实现按渠道凭证完成，Mock 通道仅组装留痕）。</p>
 *
 * <p>渠道字典对齐 F6：PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB；
 * 电话渠道由 PBX/语音链路承载，不适用本接口。</p>
 *
 * @author ai-lawyers
 */
public interface ChannelAdapter
{
    /** 渠道编码（对齐 F6 渠道字典，如 WECHAT_MP/WECHAT_MINI） */
    String code();

    /**
     * 回调验签。
     *
     * @param signature 回调携带的签名（微信约定 sha1(sort(token,timestamp,nonce,encrypt))）
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param encrypt   密文体（参与签名计算）
     * @return true 验签通过
     */
    boolean verifySignature(String signature, String timestamp, String nonce, String encrypt);

    /**
     * 解密回调密文体为明文报文（验签通过后调用；失败抛异常由上层收敛）。
     */
    String decrypt(String encrypt);

    /**
     * 明文报文归一为统一消息（解析失败的必要字段缺失时抛异常由上层收敛）。
     */
    ChannelMessage normalize(String plaintext);

    /**
     * 组装回复报文（Mock 通道仅返回报文并留痕；真实通道在此发送并返回回执）。
     *
     * @param incoming  触发回复的入站消息
     * @param replyText 回复文本
     * @return 渠道侧回执标识或报文
     */
    String reply(ChannelMessage incoming, String replyText);
}
