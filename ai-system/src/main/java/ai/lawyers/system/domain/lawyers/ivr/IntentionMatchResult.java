package ai.lawyers.system.domain.lawyers.ivr;

import java.math.BigDecimal;

/**
 * 意图识别结果
 */
public class IntentionMatchResult
{
    private boolean matched;
    private Long intentionId;
    private String intentionCode;
    private String intentionName;
    private String category;
    private BigDecimal confidence;
    private String matchMethod;
    private Long categoryId;
    private String categoryName;
    private String message;

    public static IntentionMatchResult none(String message)
    {
        IntentionMatchResult r = new IntentionMatchResult();
        r.setMatched(false);
        r.setMessage(message);
        return r;
    }

    public boolean isMatched() { return matched; }
    public void setMatched(boolean matched) { this.matched = matched; }

    public Long getIntentionId() { return intentionId; }
    public void setIntentionId(Long intentionId) { this.intentionId = intentionId; }

    public String getIntentionCode() { return intentionCode; }
    public void setIntentionCode(String intentionCode) { this.intentionCode = intentionCode; }

    public String getIntentionName() { return intentionName; }
    public void setIntentionName(String intentionName) { this.intentionName = intentionName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getMatchMethod() { return matchMethod; }
    public void setMatchMethod(String matchMethod) { this.matchMethod = matchMethod; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
