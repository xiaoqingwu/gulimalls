package com.atguigu.gulimall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.to.es.ESSkuModel;
import com.atguigu.common.utils.Query;
import com.atguigu.gulimall.search.constant.ESConstant;
import com.atguigu.gulimall.search.service.GulimallSearchService;
import com.atguigu.gulimall.search.vo.SearchParam;
import com.atguigu.gulimall.search.vo.SearchResult;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class GulimallSearchServiceImpl implements GulimallSearchService {
    @Resource
    private RestHighLevelClient highLevelClient;
    @Override
    public SearchResult search(SearchParam searchParam) {
        SearchResult result = null;
        SearchRequest request = buildSearchRequest(searchParam);
        return null;
    }

    private SearchRequest buildSearchRequest(SearchParam searchParam) {
        // 构建 DSL 语句
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        /**
         * 查询：模糊匹配 过滤(按照属性，分类，品牌，价格区间， 库存)
         */
        // 1 构建 bool-query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle",searchParam.getKeyword()));
        }
        // 1.2 bool-filter
        if (!ObjectUtils.isEmpty(searchParam.getCatalog3Id())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("catalogId",searchParam.getCatalog3Id()));
        }
        if (!CollectionUtils.isEmpty(searchParam.getBrandId()) && searchParam.getBrandId().size()>0){
            boolQueryBuilder.filter(QueryBuilders.termQuery("brandId",searchParam.getBrandId()));
        }
        if (!CollectionUtils.isEmpty(searchParam.getAttrs()) && searchParam.getAttrs().size()>0){
            for (String attr: searchParam.getAttrs()){
                BoolQueryBuilder nestedBoolQuery = QueryBuilders.boolQuery();
                // attrs=1_5寸:6寸&attrs=2_16GB:8GB
                String []result = attr.split("_");
                String attrId = result[0];
                String []attrValues = result[1].split(":");
                nestedBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",attrId));
                nestedBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",attrValues));
                // 每一个必须都得生成一个 nested 查询
                NestedQueryBuilder nestedQueryBuilder = QueryBuilders.nestedQuery("attrs", nestedBoolQuery, ScoreMode.None);
                boolQueryBuilder.filter(nestedQueryBuilder);

            }
        }
        if (searchParam.getHasStock() !=null){
            boolQueryBuilder.filter(QueryBuilders.termQuery("hasStock",searchParam.getHasStock() == 1));
        }

        if (!StringUtils.isEmpty(searchParam.getSkuPrice())){
            RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("skuPrice");
            String [] skuPrice = searchParam.getSkuPrice().split("_");
            if (skuPrice.length ==2){
                rangeQueryBuilder.gte(skuPrice[0]).lte(skuPrice[1]);
            }else if (skuPrice.length == 1){
                if (searchParam.getSkuPrice().startsWith("_")){
                    rangeQueryBuilder.lte(skuPrice[0]);
                }
                if (searchParam.getSkuPrice().endsWith("_")){
                    rangeQueryBuilder.gte(skuPrice[0]);
                }
            }
            boolQueryBuilder.filter(rangeQueryBuilder);

        }
        // 拼装完成所有的查询条件
        sourceBuilder.query(boolQueryBuilder);

        /**
         * 排序、分页、高亮
         */
        if (!StringUtils.isEmpty(searchParam.getSort())){
            String sort = searchParam.getSort();
            String []result = sort.split("_");
            SortOrder order = result[1].equalsIgnoreCase("asc")? SortOrder.ASC:SortOrder.DESC;
            sourceBuilder.sort(result[1],order);
        }
        sourceBuilder.from((searchParam.getPageNum()-1)* ESConstant.PRODUCT_PAGE_SIZE);
        sourceBuilder.size(ESConstant.PRODUCT_PAGE_SIZE);

        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            HighlightBuilder builder = new HighlightBuilder();

            builder.field("skuTitle");
            builder.preTags("<b style='color: red'>");
            builder.postTags("</b>");

            sourceBuilder.highlighter(builder);

        }
        /**
         * 聚合分析
         */
        // 品牌聚合
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg").field("brandId").size(10);
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));
        sourceBuilder.aggregation(brand_agg);

        // 分类聚合
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg").field("catalogId").size(10);
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(1));
        sourceBuilder.aggregation(catalog_agg);
        // 属性聚合
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
        TermsAggregationBuilder attr_id_agg = AggregationBuilders.terms("attr_id_agg").field("attrs.attrId");
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(50));
        attr_agg.subAggregation(attr_id_agg);
        sourceBuilder.aggregation(attr_agg);

        SearchRequest request = new SearchRequest(new String[]{ESConstant.PRODUCT_INDEX}, sourceBuilder);
        return request;
    }
    /**
     * 构建结果数据
     *
     * @param response
     * @param searchParam
     * @return
     */
    private SearchResult buildSearchResult(SearchResponse response, SearchParam searchParam) {
        SearchResult result = new SearchResult();
        SearchHits hits = response.getHits();

        // 设置 products
        List<ESSkuModel> esSkuModels = new ArrayList<>();
        if (hits.getHits() != null && hits.getHits().length > 0) {
            for (SearchHit hit : hits.getHits()) {
                String sourceAsString = hit.getSourceAsString();
                ESSkuModel esSkuModel = JSON.parseObject(sourceAsString, ESSkuModel.class);
                if (!StringUtils.isEmpty(searchParam.getKeyword())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    esSkuModel.setSkuTitle(skuTitle.fragments()[0].string());
                }
                esSkuModels.add(esSkuModel);
            }
        }
        result.setProducts(esSkuModels);

        // 设置聚合信息

        // 设置属性聚合信息
        List<SearchResult.AttrVO> attrVOS = new ArrayList<>();
        ParsedNested attr_agg = response.getAggregations().get("attr_agg");
        ParsedLongTerms attr_id_agg = attr_agg.getAggregations().get("attr_id_agg");
        for (Terms.Bucket bucket : attr_id_agg.getBuckets()) {
            SearchResult.AttrVO attrVO = new SearchResult.AttrVO();
            attrVO.setAttrId(bucket.getKeyAsNumber().longValue());
            String attrName = ((ParsedStringTerms) bucket.getAggregations().get("attr_name_agg")).getBuckets().get(0).getKeyAsString();
            List<String> attrValues = ((ParsedStringTerms) bucket.getAggregations().get("attr_value_agg"))
                    .getBuckets().stream().map(MultiBucketsAggregation.Bucket::getKeyAsString).collect(Collectors.toList());
            attrVO.setAttrName(attrName);
            attrVO.setAttrValue(attrValues);
            attrVOS.add(attrVO);
        }
        result.setAttrs(attrVOS);

        // 设置品牌聚合信息
        List<SearchResult.BrandVO> brandVOS = new ArrayList<>();
        ParsedLongTerms brand_agg = response.getAggregations().get("brand_agg");
        for (Terms.Bucket bucket : brand_agg.getBuckets()) {
            SearchResult.BrandVO brandVO = new SearchResult.BrandVO();
            brandVO.setBrandId(bucket.getKeyAsNumber().longValue());
            String brand_name_agg = ((ParsedStringTerms) bucket.getAggregations().get("brand_name_agg")).getBuckets().get(0).getKeyAsString();
            brandVO.setBrandName(brand_name_agg);
            String brand_img_agg = ((ParsedStringTerms) bucket.getAggregations().get("brand_img_agg")).getBuckets().get(0).getKeyAsString();
            brandVO.setBrandImg(brand_img_agg);
            brandVOS.add(brandVO);
        }
        result.setBrands(brandVOS);

        // 设置分类聚合信息
        ParsedLongTerms catalog_agg = response.getAggregations().get("catalog_agg");
        List<SearchResult.CatalogVO> catalogVOS = new ArrayList<>();
        List<? extends Terms.Bucket> buckets = catalog_agg.getBuckets();
        for (Terms.Bucket bucket : buckets) {
            SearchResult.CatalogVO catalogVO = new SearchResult.CatalogVO();
            // 获取分类 id
            String catalogIdString = bucket.getKeyAsString();
            catalogVO.setCatalogId(Long.parseLong(catalogIdString));

            // 获取分类名
            ParsedStringTerms catalog_name_agg = bucket.getAggregations().get("catalog_name_agg");
            String catalogNameString = catalog_name_agg.getBuckets().get(0).getKeyAsString();
            catalogVO.setCatalogName(catalogNameString);
            catalogVOS.add(catalogVO);
        }
        result.setCatalogs(catalogVOS);

        // 设置分页信息
        result.setPageNum(searchParam.getPageNum());
        long totalRecords = hits.getTotalHits().value;
        result.setTotalRecords(totalRecords);
        int totalPages = totalRecords % ESConstant.PRODUCT_PAGE_SIZE == 0 ? (int) totalRecords / ESConstant.PRODUCT_PAGE_SIZE : (int) totalRecords / ESConstant.PRODUCT_PAGE_SIZE + 1;
        result.setTotalPages(totalPages);

        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNavs.add(i);
        }
        result.setPageNavs(pageNavs);

        return result;
    }
}
