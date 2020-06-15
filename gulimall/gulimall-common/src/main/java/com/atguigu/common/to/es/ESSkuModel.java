package com.atguigu.common.to.es;

import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

@ToString
@Data
public class ESSkuModel {
    private Long skuId;

    private Long spuId;

    private String skuTitle;

    private BigDecimal skuPrice;

    private String skuImg;

    private Long saleCount;

    private Boolean hasStock;

    private Long hotScore;

    private Long brandId;

    private String  brandName;

    private String brandImg;

    private Long catalogId;

    private String catalogName;

    private List<Attrs> attrs;

    public static class Attrs {

        private Long attrId;

        private String attrName;

        private String attrValue;

    }
}
