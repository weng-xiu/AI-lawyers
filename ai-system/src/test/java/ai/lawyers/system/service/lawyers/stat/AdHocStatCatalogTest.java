package ai.lawyers.system.service.lawyers.stat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * AdHocStatCatalog 单测：指标/维度目录规模、白名单判定、
 * 维度指标与跨维度开关、只读性与元数据完整性。
 *
 * @author ai-lawyers
 */
class AdHocStatCatalogTest
{
    @Test
    void catalogSizes()
    {
        assertThat(AdHocStatCatalog.ALL_METRICS).hasSize(30);
        assertThat(AdHocStatCatalog.DIM_METRICS).hasSize(4);
        assertThat(AdHocStatCatalog.DIMENSIONS).hasSize(5);
    }

    @Test
    void knownMetricWhitelist()
    {
        assertThat(AdHocStatCatalog.isKnownMetric("call_total")).isTrue();
        assertThat(AdHocStatCatalog.isKnownMetric("transfer_total")).isTrue();
        assertThat(AdHocStatCatalog.isKnownMetric("not_exists")).isFalse();
        assertThat(AdHocStatCatalog.isKnownMetric(null)).isFalse();
        assertThat(AdHocStatCatalog.isKnownMetric("")).isFalse();
    }

    @Test
    void mapsAreUnmodifiable()
    {
        assertThatThrownBy(() -> AdHocStatCatalog.ALL_METRICS.put("x",
                new AdHocStatCatalog.MetricDef("x", "x", "x")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> AdHocStatCatalog.DIMENSIONS.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metricMetadataConsistent()
    {
        AdHocStatCatalog.ALL_METRICS.forEach((key, def) ->
        {
            assertThat(def.getKey()).isEqualTo(key);
            assertThat(def.getLabel()).isNotBlank();
            assertThat(def.getGroup()).isNotBlank();
        });
        AdHocStatCatalog.DIM_METRICS.forEach((key, def) ->
        {
            assertThat(def.getKey()).isEqualTo(key);
            assertThat(def.getLabel()).isNotBlank();
        });
    }

    @Test
    void dimensionMetricsAreAllKnown()
    {
        AdHocStatCatalog.DIMENSIONS.forEach((key, dim) ->
        {
            assertThat(dim.getKey()).isEqualTo(key);
            assertThat(dim.getLabel()).isNotBlank();
            assertThat(dim.getMetrics()).isNotEmpty();
            dim.getMetrics().forEach(metric -> assertThat(AdHocStatCatalog.isKnownMetric(metric))
                    .as("dimension %s metric %s", key, metric).isTrue());
        });
    }

    @Test
    void crossDimensionFlags()
    {
        assertThat(AdHocStatCatalog.DIMENSIONS.get("agent").isCrossDim()).isTrue();
        assertThat(AdHocStatCatalog.DIMENSIONS.get("category").isCrossDim()).isTrue();
        assertThat(AdHocStatCatalog.DIMENSIONS.get("lang").isCrossDim()).isTrue();
        assertThat(AdHocStatCatalog.DIMENSIONS.get("line").isCrossDim()).isFalse();
        assertThat(AdHocStatCatalog.DIMENSIONS.get("channel").isCrossDim()).isFalse();
    }

    @Test
    void crossDimMetricsAllKnown()
    {
        assertThat(AdHocStatCatalog.CROSS_DIM_METRICS).hasSize(5);
        AdHocStatCatalog.CROSS_DIM_METRICS
                .forEach(metric -> assertThat(AdHocStatCatalog.isKnownMetric(metric)).isTrue());
        assertThat(AdHocStatCatalog.CROSS_DIM_METRICS)
                .containsAll(AdHocStatCatalog.DIMENSIONS.get("agent").getMetrics());
    }
}
