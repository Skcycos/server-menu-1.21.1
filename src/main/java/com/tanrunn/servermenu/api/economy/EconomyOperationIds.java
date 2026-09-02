package com.tanrunn.servermenu.api.economy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 内部资金操作幂等键（operationId）生成器（纯逻辑，集中实现，可单测）。
 *
 * <p><b>为什么需要它</b>：不同业务域（BuildShop 扣款/退款、StockMarket 银行/证券操作）
 * 各自从<b>同一个玩家原始 requestId</b>派生资金操作时，如果直接复用原始 requestId，
 * 恶意客户端只要复用同一个 requestId，就能让一个业务的操作在另一个业务里被错误重放
 * （例如用商店扣款键免费完成证券入金）。</p>
 *
 * <p><b>规范</b>：
 * <ul>
 *   <li>指纹 = SHA-256( domain + provider + source + operationType + 完整原始 requestId + direction )；</li>
 *   <li>哈希使用 Base64 URL-safe 无填充（32 字节 → 43 字符）；</li>
 *   <li>前置短业务域前缀（域内自带业务归属），总长度 ≤ {@value #MAX_LENGTH}；</li>
 *   <li>原始 requestId 只进入哈希材料，<b>绝不</b>作为资金操作的幂等键直接外传；</li>
 *   <li>相同输入恒生成相同 opId（重放稳定）；不同域/业务/方向/完整 requestId 必不同。</li>
 * </ul></p>
 */
public final class EconomyOperationIds {

    /** 内部幂等键最大长度（满足各层 64 字符约束，且不占用外部请求长度预算）。 */
    public static final int MAX_LENGTH = 64;

    // 业务域（短前缀；域内已编码业务归属，避免跨业务复用同一个 id）
    /** BuildShop 扣款。 */
    public static final String BS_WITHDRAW = "bs:wd:";
    /** BuildShop 退款（冲正扣款）。 */
    public static final String BS_REFUND = "bs:rf:";
    /** StockMarket 银行扣款（入金方向，LC 侧）。 */
    public static final String SM_BANK_DEBIT = "sm:bd:";
    /** StockMarket 银行入账（出金方向，LC 侧）。 */
    public static final String SM_BANK_CREDIT = "sm:bc:";
    /** StockMarket 证券扣款（出金方向，证券侧）。 */
    public static final String SM_SECURITIES_DEBIT = "sm:sd:";
    /** StockMarket 证券入账（入金方向，证券侧）。 */
    public static final String SM_SECURITIES_CREDIT = "sm:sc:";
    /** StockMarket 补偿（任意方向的失败回滚）。 */
    public static final String SM_ROLLBACK = "sm:rb:";
    /** 飞行戒指手动充能（每次 Shift+右键按缺少耐久一次性补满）。 */
    public static final String FR_CHARGE = "fr:ch:";

    private EconomyOperationIds() {
    }

    /**
     * 生成固定长度的内部操作幂等键。
     *
     * @param domain        业务域前缀（如 {@link #BS_WITHDRAW}）
     * @param provider      provider id（如 server_menu:lc_bank_main）
     * @param source        业务来源/业务域
     * @param operationType 操作类型（如 withdraw / refund / rollback_bank_deposit）
     * @param requestId     玩家原始 requestId（完整，不截断；仅作为哈希材料）
     * @param direction     转账方向（如 DEPOSIT_TO_SECURITIES；无方向传 null)
     * @return 长度 ≤ {@value #MAX_LENGTH} 的稳定 opId
     */
    public static String generate(String domain, String provider, String source,
                                  String operationType, String requestId, String direction) {
        String safeDomain = domain == null ? "" : domain;
        String material = safeDomain + "\n"
                + (provider == null ? "" : provider) + "\n"
                + (source == null ? "" : source) + "\n"
                + (operationType == null ? "" : operationType) + "\n"
                + (requestId == null ? "" : requestId) + "\n"
                + (direction == null ? "" : direction);
        String hash = sha256Base64Url(material);
        String id = safeDomain + hash;
        if (id.length() > MAX_LENGTH) {
            int budget = MAX_LENGTH - safeDomain.length();
            if (budget <= 0) {
                // 防御：域前缀本身过长时退化为纯哈希（正常域前缀 ≤6 字符，不会触发）。
                return hash.substring(0, Math.min(MAX_LENGTH, hash.length()));
            }
            id = safeDomain + hash.substring(0, Math.min(budget, hash.length()));
        }
        return id;
    }

    private static String sha256Base64Url(String material) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
    }
}
